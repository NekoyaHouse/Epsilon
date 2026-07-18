package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AStarFollowerNavigator implements FollowerNavigator {

    private static final double HORIZONTAL_CLEARANCE = 0.18;
    private static final double VERTICAL_CLEARANCE = 0.10;
    private static final double INITIAL_COLLISION_EPSILON = 1.0E-4;
    private static final double DETOUR_RETREAT_DISTANCE = 1.25;
    private static final List<Direction> NEIGHBORS = createNeighbors();

    @Override
    public FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config) {
        Vec3 limitedTarget = limitTarget(player.position(), targetPos, config.searchRadius());

        if (isSegmentClear(player, player.position(), limitedTarget)) {
            if (player.position().distanceTo(targetPos) <= config.stopDistance()) {
                return new FollowerPath(player.position(), List.of(player.position()));
            }
            return new FollowerPath(limitedTarget, List.of(player.position(), limitedTarget));
        }

        Vec3 escape = findLocalEscape(player, limitedTarget);
        if (escape != null) {
            return new FollowerPath(escape, List.of(player.position(), escape));
        }

        Vec3 detour = findVisibleDetour(player, limitedTarget, config.searchRadius());
        if (detour != null) {
            return new FollowerPath(detour, List.of(player.position(), detour, limitedTarget));
        }

        BlockPos start = BlockPos.containing(player.position());
        BlockPos goal = BlockPos.containing(limitedTarget);
        List<BlockPos> path = findPath(player, start, goal, config);

        if (path.size() > 1) {
            return createPath(player, path);
        }
        if (path.size() == 1) {
            return new FollowerPath(player.position(), List.of(player.position()));
        }
        return new FollowerPath(player.position(), List.of(player.position()));
    }

    private List<BlockPos> findPath(LocalPlayer player, BlockPos start, BlockPos goal, FollowerConfig config) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, Boolean> occupancy = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, 0.0, heuristic(start, goal));
        Node bestNode = startNode;
        open.add(startNode);
        gScore.put(start, 0.0);

        int visited = 0;
        while (!open.isEmpty() && visited < config.maxNodes()) {
            Node current = open.poll();
            if (!closed.add(current.pos())) continue;

            visited++;
            if (current.hScore() < bestNode.hScore()) {
                bestNode = current;
            }

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

                double tentativeG = current.gScore() + direction.cost();
                double previousG = gScore.getOrDefault(next, Double.MAX_VALUE);
                if (tentativeG >= previousG) continue;
                if (!isSegmentClear(player, Vec3.atBottomCenterOf(current.pos()), Vec3.atBottomCenterOf(next)))
                    continue;

                cameFrom.put(next, current.pos());
                gScore.put(next, tentativeG);
                double h = heuristic(next, goal);
                open.add(new Node(next, tentativeG, h));
            }
        }

        if (!bestNode.pos().equals(start)) {
            return reconstructPath(cameFrom, bestNode.pos());
        }
        return List.of();
    }

    private boolean canOccupy(LocalPlayer player, BlockPos pos) {
        if (!player.level().isInWorldBounds(pos) || !player.level().hasChunkAt(pos)) {
            return false;
        }

        return canOccupy(player, Vec3.atBottomCenterOf(pos));
    }

    private boolean canOccupy(LocalPlayer player, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        if (!player.level().isInWorldBounds(pos) || !player.level().hasChunkAt(pos)) {
            return false;
        }

        AABB box = collisionBox(player, feet);
        return hasLoadedChunks(player, box)
                && player.level().noBlockCollision(player, box)
                && player.level().noBorderCollision(player, box);
    }

    private AABB collisionBox(LocalPlayer player, Vec3 feet) {
        double halfWidth = player.getBbWidth() * 0.5 + HORIZONTAL_CLEARANCE;
        return new AABB(
                feet.x - halfWidth,
                feet.y - VERTICAL_CLEARANCE,
                feet.z - halfWidth,
                feet.x + halfWidth,
                feet.y + player.getBbHeight() + VERTICAL_CLEARANCE,
                feet.z + halfWidth
        );
    }

    private boolean isSegmentClear(LocalPlayer player, Vec3 from, Vec3 to) {
        return isBoxSweepClear(player, collisionBox(player, from), to.subtract(from));
    }

    private boolean isInitialSegmentClear(LocalPlayer player, Vec3 to) {
        AABB box = player.getBoundingBox().deflate(INITIAL_COLLISION_EPSILON);
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
            int next = rawPoints.size() - 1;
            while (next > anchor && !isPathSegmentClear(player, rawPoints, anchor, next)) {
                next--;
            }
            if (next == anchor) {
                return new FollowerPath(playerPos, List.of(playerPos));
            }
            points.add(rawPoints.get(next));
            anchor = next;
        }

        return new FollowerPath(points.get(1), List.copyOf(points));
    }

    private boolean isPathSegmentClear(LocalPlayer player, List<Vec3> points, int anchor, int next) {
        if (anchor == 0) {
            return isInitialSegmentClear(player, points.get(next));
        }
        return isSegmentClear(player, points.get(anchor), points.get(next));
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
