package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AStarFollowerNavigator implements FollowerNavigator {

    private static final double HORIZONTAL_CLEARANCE = 0.18;
    private static final double VERTICAL_CLEARANCE = 0.10;
    private static final double INITIAL_COLLISION_EPSILON = 1.0E-4;
    private static final double DETOUR_RETREAT_DISTANCE = 1.25;
    private static final double WALL_CLEARANCE_WIDTH = 1.2;
    private static final double WALL_CLEARANCE_HEIGHT = 0.8;
    private static final double WALL_CLEARANCE_PENALTY = 8.0;
    private static final int FLIGHT_PREDICTION_TICKS = 10;
    private static final double WAYPOINT_REACHED_SQR = 0.75 * 0.75;
    private static final List<Direction> NEIGHBORS = createNeighbors();

    @Override
    public FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config) {
        Vec3 limitedTarget = limitTarget(player.position(), targetPos, config.searchRadius());

        if (isSegmentClear(player, player.position(), limitedTarget)) {
            if (player.position().distanceTo(targetPos) <= config.stopDistance()) {
                return new FollowerPath(player.position(), List.of(player.position()));
            }
            FollowerPath direct = new FollowerPath(limitedTarget, List.of(player.position(), limitedTarget));
            if (isFlightPathSafe(player, direct.points())) {
                return direct;
            }
        }

        Vec3 escape = findLocalEscape(player, limitedTarget);
        if (escape != null) {
            FollowerPath escapePath = new FollowerPath(escape, List.of(player.position(), escape));
            if (isFlightPathSafe(player, escapePath.points())) {
                return escapePath;
            }
        }

        Vec3 detour = findVisibleDetour(player, limitedTarget, config.searchRadius());
        if (detour != null) {
            FollowerPath detourPath = new FollowerPath(
                    detour,
                    List.of(player.position(), detour, limitedTarget)
            );
            if (isFlightPathSafe(player, detourPath.points())) {
                return detourPath;
            }
        }

        BlockPos start = BlockPos.containing(player.position());
        BlockPos goal = BlockPos.containing(limitedTarget);
        List<BlockPos> path = findPath(player, start, goal, config);

        if (path.size() > 1) {
            FollowerPath created = createPath(player, path);
            if (created.points().size() > 1 && isFlightPathSafe(player, created.points())) {
                return created;
            }
        }

        FollowerPath avoidance = findSafeLocalAvoidance(player, limitedTarget, config.searchRadius());
        return avoidance != null ? avoidance : stopPath(player);
    }

    private List<BlockPos> findPath(LocalPlayer player, BlockPos start, BlockPos goal, FollowerConfig config) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, Boolean> occupancy = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, 0.0, heuristic(start, goal));
        open.add(startNode);
        gScore.put(start, 0.0);

        int visited = 0;
        while (!open.isEmpty() && visited < config.maxNodes()) {
            Node current = open.poll();
            if (!closed.add(current.pos())) continue;

            visited++;
            Vec3 currentPoint = Vec3.atBottomCenterOf(current.pos());
            boolean canStop = (current.pos().equals(goal)
                    || current.pos().distSqr(goal) <= config.stopDistance() * config.stopDistance())
                    && isSegmentClear(player, currentPoint, Vec3.atBottomCenterOf(goal));
            if (canStop) {
                return reconstructPath(cameFrom, current.pos());
            }

            for (Direction direction : NEIGHBORS) {
                BlockPos next = current.pos().offset(direction.x(), direction.y(), direction.z());
                if (closed.contains(next)) continue;
                if (start.distSqr(next) > config.searchRadius() * config.searchRadius()) continue;
                if (!occupancy.computeIfAbsent(next, pos -> canOccupy(player, pos))) continue;

                if (!isSegmentClear(player, currentPoint, Vec3.atBottomCenterOf(next))) {
                    continue;
                }

                double edgeCost = direction.cost();
                if (!isPaddedSegmentClear(player, currentPoint, Vec3.atBottomCenterOf(next))) {
                    edgeCost += WALL_CLEARANCE_PENALTY;
                }

                double tentativeG = current.gScore() + edgeCost;
                double previousG = gScore.getOrDefault(next, Double.MAX_VALUE);
                if (tentativeG >= previousG) continue;

                cameFrom.put(next, current.pos());
                gScore.put(next, tentativeG);
                double h = heuristic(next, goal);
                open.add(new Node(next, tentativeG, h));
            }
        }

        return List.of();
    }

    @SuppressWarnings("deprecation")
    private boolean canOccupy(LocalPlayer player, BlockPos pos) {
        if (!player.level().isInWorldBounds(pos) || !player.level().hasChunkAt(pos)) {
            return false;
        }

        return canOccupy(player, Vec3.atBottomCenterOf(pos));
    }

    private boolean canOccupy(LocalPlayer player, Vec3 feet) {
        return canOccupy(player, feet, 0.0, 0.0);
    }

    @SuppressWarnings("deprecation")
    private boolean canOccupy(LocalPlayer player, Vec3 feet, double extraWidth, double extraHeight) {
        BlockPos pos = BlockPos.containing(feet);
        if (!player.level().isInWorldBounds(pos) || !player.level().hasChunkAt(pos)) {
            return false;
        }

        AABB box = collisionBox(player, feet, extraWidth, extraHeight);
        return hasLoadedChunks(player, box)
                && player.level().noBlockCollision(player, box)
                && player.level().noBorderCollision(player, box);
    }

    private AABB collisionBox(LocalPlayer player, Vec3 feet) {
        return collisionBox(player, feet, 0.0, 0.0);
    }

    private AABB collisionBox(LocalPlayer player, Vec3 feet, double extraWidth, double extraHeight) {
        double halfWidth = player.getBbWidth() * 0.5 + HORIZONTAL_CLEARANCE + extraWidth * 0.5;
        return new AABB(
                feet.x - halfWidth,
                feet.y - VERTICAL_CLEARANCE - extraHeight * 0.5,
                feet.z - halfWidth,
                feet.x + halfWidth,
                feet.y + player.getBbHeight() + VERTICAL_CLEARANCE + extraHeight * 0.5,
                feet.z + halfWidth
        );
    }

    private boolean isSegmentClear(LocalPlayer player, Vec3 from, Vec3 to) {
        return isBoxSweepClear(player, collisionBox(player, from), to.subtract(from));
    }

    private boolean isPaddedSegmentClear(LocalPlayer player, Vec3 from, Vec3 to) {
        return isBoxSweepClear(
                player,
                collisionBox(player, from, WALL_CLEARANCE_WIDTH, WALL_CLEARANCE_HEIGHT),
                to.subtract(from)
        );
    }

    private boolean isInitialSegmentClear(LocalPlayer player, Vec3 to) {
        AABB box = player.getBoundingBox().deflate(INITIAL_COLLISION_EPSILON);
        return isBoxSweepClear(player, box, to.subtract(player.position()));
    }

    private boolean isInitialSegmentClearPadded(LocalPlayer player, Vec3 to) {
        AABB box = player.getBoundingBox()
                .deflate(INITIAL_COLLISION_EPSILON)
                .inflate(
                        WALL_CLEARANCE_WIDTH * 0.5,
                        WALL_CLEARANCE_HEIGHT * 0.5,
                        WALL_CLEARANCE_WIDTH * 0.5
                );
        return isBoxSweepClear(player, box, to.subtract(player.position()));
    }

    private boolean isBoxSweepClear(LocalPlayer player, AABB box, Vec3 delta) {
        if (delta.lengthSqr() < 0.000001) return true;

        AABB sweptBox = box.expandTowards(delta);
        if (!hasLoadedChunks(player, sweptBox)
                || !player.level().noBorderCollision(player, box.move(delta))) {
            return false;
        }

        for (var shape : player.level().getBlockCollisions(player, sweptBox)) {
            if (box.collidedAlongVector(delta, shape.toAabbs())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private boolean hasLoadedChunks(LocalPlayer player, AABB box) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        return player.level().hasChunksAt(min, max);
    }

    private Vec3 limitTarget(Vec3 from, Vec3 target, int searchRadius) {
        Vec3 delta = target.subtract(from);
        double distance = delta.length();
        if (distance <= searchRadius || distance < 0.001) {
            return target;
        }
        return from.add(delta.normalize().scale(searchRadius));
    }

    private Vec3 findVisibleDetour(LocalPlayer player, Vec3 target, int searchRadius) {
        Vec3 playerPos = player.position();
        Vec3 targetDelta = target.subtract(playerPos);
        Vec3 horizontal = new Vec3(targetDelta.x, 0.0, targetDelta.z);
        Vec3 retreat = targetDelta.normalize().scale(-DETOUR_RETREAT_DISTANCE);
        Vec3 lateral = horizontal.lengthSqr() < 0.000001
                ? new Vec3(1.0, 0.0, 0.0)
                : new Vec3(-horizontal.z, 0.0, horizontal.x).normalize();
        List<Vec3> directions = List.of(
                new Vec3(0.0, -1.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                lateral,
                lateral.scale(-1.0)
        );

        Vec3 best = null;
        double bestCost = Double.MAX_VALUE;
        for (Vec3 direction : directions) {
            for (int distance = 2; distance <= searchRadius; distance += 2) {
                Vec3 candidate = playerPos.add(retreat).add(direction.scale(distance));
                if (!canOccupy(player, candidate)) continue;
                if (!isInitialSegmentClear(player, candidate)) continue;
                if (!isSegmentClear(player, candidate, target)) continue;

                double cost = playerPos.distanceTo(candidate) + candidate.distanceTo(target);
                if (cost < bestCost) {
                    best = candidate;
                    bestCost = cost;
                }
                break;
            }
        }
        return best;
    }

    private Vec3 findLocalEscape(LocalPlayer player, Vec3 target) {
        Vec3 playerPos = player.position();
        if (canOccupy(player, playerPos)) return null;

        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : NEIGHBORS) {
            if (direction.x() == 0 && direction.z() == 0) continue;
            for (int distance = 1; distance <= 3; distance++) {
                Vec3 offset = new Vec3(direction.x(), direction.y(), direction.z())
                        .normalize()
                        .scale(distance);
                Vec3 candidate = playerPos.add(offset);
                if (!canOccupy(player, candidate)) continue;
                if (!isInitialSegmentClear(player, candidate)) continue;

                double score = distance * 1.5 + candidate.distanceTo(target);
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
                break;
            }
        }
        return best;
    }

    private double heuristic(BlockPos pos, BlockPos goal) {
        return Math.sqrt(pos.distSqr(goal));
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        ArrayList<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        path.add(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }

        Collections.reverse(path);
        return path;
    }

    private FollowerPath createPath(LocalPlayer player, List<BlockPos> nodes) {
        Vec3 playerPos = player.position();
        ArrayList<Vec3> rawPoints = new ArrayList<>();
        rawPoints.add(playerPos);
        for (int i = 1; i < nodes.size(); i++) {
            rawPoints.add(Vec3.atBottomCenterOf(nodes.get(i)));
        }

        ArrayList<Vec3> points = new ArrayList<>();
        points.add(playerPos);

        int anchor = 0;
        while (anchor < rawPoints.size() - 1) {
            int next = findNextPathPoint(player, rawPoints, anchor, true);
            if (next == anchor) {
                next = findNextPathPoint(player, rawPoints, anchor, false);
            }
            if (next == anchor) {
                return stopPath(player);
            }
            points.add(rawPoints.get(next));
            anchor = next;
        }

        return new FollowerPath(points.get(1), List.copyOf(points));
    }

    private int findNextPathPoint(LocalPlayer player, List<Vec3> points, int anchor, boolean padded) {
        int next = points.size() - 1;
        while (next > anchor && !isPathSegmentClear(player, points, anchor, next, padded)) {
            next--;
        }
        return next;
    }

    private boolean isPathSegmentClear(LocalPlayer player, List<Vec3> points, int anchor, int next, boolean padded) {
        if (anchor == 0) {
            return padded
                    ? isInitialSegmentClearPadded(player, points.get(next))
                    : isInitialSegmentClear(player, points.get(next));
        }
        return padded
                ? isPaddedSegmentClear(player, points.get(anchor), points.get(next))
                : isSegmentClear(player, points.get(anchor), points.get(next));
    }

    private boolean isFlightPathSafe(LocalPlayer player, List<Vec3> points) {
        if (points.size() < 2) {
            return true;
        }

        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        double gravity = effectiveGravity(player);
        int nextPoint = 1;

        for (int tick = 0; tick < FLIGHT_PREDICTION_TICKS; tick++) {
            while (nextPoint < points.size()
                    && position.distanceToSqr(points.get(nextPoint)) <= WAYPOINT_REACHED_SQR) {
                nextPoint++;
            }
            if (nextPoint >= points.size()) {
                return true;
            }

            Vec3 delta = points.get(nextPoint).subtract(position);
            Vec3 nextVelocity = FlightTrajectoryValidator.nextFallFlyingMovement(
                    velocity,
                    FlightTrajectoryValidator.yawTo(delta),
                    FlightTrajectoryValidator.pitchTo(delta),
                    gravity
            );
            Vec3 nextPosition = position.add(nextVelocity);
            boolean clear = tick == 0
                    ? isInitialSegmentClear(player, nextPosition)
                    : isSegmentClear(player, position, nextPosition);
            if (!clear) {
                return false;
            }

            position = nextPosition;
            velocity = nextVelocity;
        }
        return true;
    }

    private double effectiveGravity(LocalPlayer player) {
        if (player.getDeltaMovement().y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            return Math.min(player.getGravity(), 0.01);
        }
        return player.getGravity();
    }

    private FollowerPath findSafeLocalAvoidance(LocalPlayer player, Vec3 target, int searchRadius) {
        Vec3 playerPos = player.position();
        Vec3 toTarget = target.subtract(playerPos);
        double targetDistance = toTarget.length();
        if (targetDistance < 0.001) {
            return null;
        }

        Vec3 targetDirection = toTarget.scale(1.0 / targetDistance);
        Vec3 velocityDirection = player.getDeltaMovement().lengthSqr() > 0.000001
                ? player.getDeltaMovement().normalize()
                : targetDirection;
        int primaryDistance = Math.clamp(
                (int) Math.round(player.getDeltaMovement().length() * 4.0 + 3.0),
                3,
                Math.max(3, Math.min(8, searchRadius))
        );
        int[] distances = {primaryDistance, Math.max(2, primaryDistance / 2), 2};

        FollowerPath bestPaddedPath = null;
        double bestPaddedScore = Double.NEGATIVE_INFINITY;
        FollowerPath bestNormalPath = null;
        double bestNormalScore = Double.NEGATIVE_INFINITY;
        for (Direction direction : NEIGHBORS) {
            Vec3 unit = new Vec3(direction.x(), direction.y(), direction.z()).normalize();
            double targetAlignment = unit.dot(targetDirection);
            double velocityAlignment = unit.dot(velocityDirection);
            for (int distance : distances) {
                if (distance > searchRadius) {
                    continue;
                }

                Vec3 candidate = playerPos.add(unit.scale(distance));
                if (!canOccupy(player, candidate)) {
                    continue;
                }

                FollowerPath candidatePath = new FollowerPath(
                        candidate,
                        List.of(playerPos, candidate)
                );
                if (!isFlightPathSafe(player, candidatePath.points())) {
                    continue;
                }

                boolean paddedClear = canOccupy(
                        player,
                        candidate,
                        WALL_CLEARANCE_WIDTH,
                        WALL_CLEARANCE_HEIGHT
                ) && isInitialSegmentClearPadded(player, candidate);
                double progress = targetDistance - candidate.distanceTo(target);
                double score = progress
                        + targetAlignment * 2.0
                        + velocityAlignment * 0.5
                        + (paddedClear ? 3.0 : 0.0)
                        - distance * 0.05;
                if (paddedClear) {
                    if (score > bestPaddedScore) {
                        bestPaddedScore = score;
                        bestPaddedPath = candidatePath;
                    }
                } else if (score > bestNormalScore) {
                    bestNormalScore = score;
                    bestNormalPath = candidatePath;
                }
            }
        }
        return bestPaddedPath != null ? bestPaddedPath : bestNormalPath;
    }

    private FollowerPath stopPath(LocalPlayer player) {
        return new FollowerPath(player.position(), List.of(player.position()));
    }

    private static List<Direction> createNeighbors() {
        ArrayList<Direction> directions = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    double cost = Math.sqrt(x * x + z * z + y * y * 1.44);
                    directions.add(new Direction(x, y, z, cost));
                }
            }
        }
        return List.copyOf(directions);
    }

    private record Node(BlockPos pos, double gScore, double hScore) {
        private double fScore() {
            return gScore + hScore;
        }
    }

    private record Direction(int x, int y, int z, double cost) {
    }

}
