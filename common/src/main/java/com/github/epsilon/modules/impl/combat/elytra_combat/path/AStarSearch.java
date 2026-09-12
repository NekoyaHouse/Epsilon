package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 单层 1x1x1 节点 A*。层级搜索已移除，粗粒度缓存仅用于加速碰撞查询。
 */
final class AStarSearch {

    private static final int STEP_SIZE = 1;
    private static final List<StepDirection> DIRECTIONS = createDirections();
    private static final double WALL_CLEARANCE_WIDTH = 1.2;
    private static final double WALL_CLEARANCE_HEIGHT = 0.8;
    private static final double WALL_CLEARANCE_PENALTY = 8.0;

    private final VoxelCollisionCache grid;
    private final ElytraMotionPredictor.PlayerCollisionProfile profile;
    private final BlockPos start;
    private final BlockPos goal;
    private final Vec3 goalPoint;
    private final int searchRadius;
    private final int maxNodes;
    private final double stopDistance;
    private final Set<Long> extraBlocked;
    private final ElytraMotionPredictor.PlayerCollisionProfile paddedProfile;

    AStarSearch(
            VoxelCollisionCache grid,
            ElytraMotionPredictor.PlayerCollisionProfile profile,
            BlockPos start,
            BlockPos goal,
            int searchRadius,
            int maxNodes,
            double stopDistance,
            Set<Long> extraBlocked
    ) {
        this.grid = grid;
        this.profile = profile;
        this.start = start;
        this.goal = goal;
        this.goalPoint = Vec3.atBottomCenterOf(goal);
        this.searchRadius = searchRadius;
        this.maxNodes = maxNodes;
        this.stopDistance = stopDistance;
        this.extraBlocked = extraBlocked;
        this.paddedProfile = new ElytraMotionPredictor.PlayerCollisionProfile(
                profile.width() + WALL_CLEARANCE_WIDTH,
                profile.height() + WALL_CLEARANCE_HEIGHT
        );
    }

    List<BlockPos> findPath() {
        long startKey = this.start.asLong();
        PriorityQueue<Node> open = new PriorityQueue<>(Node.COMPARATOR);
        Map<Long, Double> gScores = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        double startHeuristic = heuristic(this.start);
        Node startNode = new Node(startKey, 0.0, startHeuristic, 1);
        open.add(startNode);
        gScores.put(startKey, 0.0);

        int visited = 0;
        while (!open.isEmpty() && visited < this.maxNodes) {
            Node current = open.poll();
            if (!closed.add(current.position())) {
                continue;
            }

            visited++;

            BlockPos currentPos = BlockPos.of(current.position());
            if (canStopAt(currentPos)) {
                return reconstructPath(cameFrom, current.position());
            }

            for (StepDirection direction : DIRECTIONS) {
                int stepSize = STEP_SIZE;
                BlockPos next = direction.offset(currentPos, stepSize);
                if (next.equals(currentPos)) {
                    continue;
                }

                long nextKey = next.asLong();
                if (closed.contains(nextKey)
                        || this.start.distSqr(next) > (long) this.searchRadius * this.searchRadius
                        || !this.grid.isInWindow(next)) {
                    continue;
                }

                Vec3 currentPoint = Vec3.atBottomCenterOf(currentPos);
                Vec3 nextPoint = Vec3.atBottomCenterOf(next);
                if (!ElytraMotionPredictor.isSweepClear(
                        this.grid,
                        this.profile,
                        currentPoint,
                        nextPoint,
                        this.extraBlocked
                )) {
                    continue;
                }

                double edgeCost = direction.cost(stepSize);
                if (!ElytraMotionPredictor.isSweepClear(
                        this.grid,
                        this.paddedProfile,
                        currentPoint,
                        nextPoint,
                        this.extraBlocked
                )) {
                    edgeCost += WALL_CLEARANCE_PENALTY;
                }

                double tentativeG = current.gScore() + edgeCost;
                double previousG = gScores.getOrDefault(nextKey, Double.MAX_VALUE);
                if (tentativeG >= previousG) {
                    continue;
                }

                cameFrom.put(nextKey, current.position());
                gScores.put(nextKey, tentativeG);
                open.add(new Node(nextKey, tentativeG, heuristic(next), stepSize));
            }
        }

        return List.of();
    }

    private boolean canStopAt(BlockPos current) {
        if (!current.equals(this.goal) && current.distSqr(this.goal) > this.stopDistance * this.stopDistance) {
            return false;
        }

        return ElytraMotionPredictor.isSweepClear(
                this.grid,
                this.profile,
                Vec3.atBottomCenterOf(current),
                this.goalPoint,
                this.extraBlocked
        );
    }

    private double heuristic(BlockPos position) {
        return Vec3.atBottomCenterOf(position).distanceTo(this.goalPoint);
    }

    private static List<BlockPos> reconstructPath(Map<Long, Long> cameFrom, long end) {
        ArrayList<BlockPos> path = new ArrayList<>();
        long current = end;
        path.add(BlockPos.of(current));

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(BlockPos.of(current));
        }

        Collections.reverse(path);
        return path;
    }

    private static List<StepDirection> createDirections() {
        ArrayList<StepDirection> directions = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    directions.add(new StepDirection(x, y, z));
                }
            }
        }
        return List.copyOf(directions);
    }

    private record Node(long position, double gScore, double heuristic, int lastStep) {

        private static final Comparator<Node> COMPARATOR = Comparator
                .comparingDouble(Node::fScore)
                .thenComparing(Comparator.comparingInt(Node::lastStep).reversed())
                .thenComparingDouble(Node::heuristic);

        private double fScore() {
            return this.gScore + this.heuristic;
        }
    }

    private record StepDirection(int x, int y, int z, double normalizedX, double normalizedY, double normalizedZ) {

        private StepDirection(int x, int y, int z) {
            this(
                    x,
                    y,
                    z,
                    x / Math.sqrt(x * x + y * y + z * z),
                    y / Math.sqrt(x * x + y * y + z * z),
                    z / Math.sqrt(x * x + y * y + z * z)
            );
        }

        private BlockPos offset(BlockPos origin, int stepSize) {
            int offsetX = roundedOffset(this.normalizedX, stepSize);
            int offsetY = roundedOffset(this.normalizedY, stepSize);
            int offsetZ = roundedOffset(this.normalizedZ, stepSize);
            return origin.offset(offsetX, offsetY, offsetZ);
        }

        private double cost(int stepSize) {
            int offsetX = roundedOffset(this.normalizedX, stepSize);
            int offsetY = roundedOffset(this.normalizedY, stepSize);
            int offsetZ = roundedOffset(this.normalizedZ, stepSize);
            return Math.sqrt(offsetX * offsetX + offsetZ * offsetZ + offsetY * offsetY * 1.44);
        }

        private static int roundedOffset(double normalized, int stepSize) {
            if (normalized == 0.0) {
                return 0;
            }
            return (int) Math.round(normalized * stepSize);
        }
    }
}
