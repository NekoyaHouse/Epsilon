package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor;
import net.minecraft.core.BlockPos;

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
 * 基础 26 方向 A*。
 *
 * <p>只在体素缓存上执行标准 open/closed、g 分数、欧氏启发式和父节点回溯，
 * 返回从起点到终点的原始方块路径。不运行滑翔轨迹预演、临时障碍重试、路径
 * 压缩或其他额外修正。</p>
 */
final class AStarSearch {

    private static final double EPSILON = 1.0E-7;
    private static final int[][] NEIGHBORS = createNeighbors();

    private final VoxelCollisionCache grid;
    private final ElytraMotionPredictor.PlayerCollisionProfile profile;
    private final BlockPos start;
    private final BlockPos goal;
    private final int searchRadius;
    private final int maxNodes;

    AStarSearch(
            VoxelCollisionCache grid,
            ElytraMotionPredictor.PlayerCollisionProfile profile,
            BlockPos start,
            BlockPos goal,
            int searchRadius,
            int maxNodes
    ) {
        this.grid = grid;
        this.profile = profile;
        this.start = start;
        this.goal = goal;
        this.searchRadius = searchRadius;
        this.maxNodes = maxNodes;
    }

    List<BlockPos> findPath() {
        PriorityQueue<Node> open = new PriorityQueue<>(Node.COMPARATOR);
        Map<BlockPos, Double> gScores = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScores.put(this.start, 0.0);
        open.add(new Node(this.start, 0.0, heuristic(this.start)));

        int visited = 0;
        while (!open.isEmpty() && visited < this.maxNodes) {
            Node current = open.poll();
            if (!closed.add(current.position())) {
                continue;
            }
            visited++;

            if (current.position().equals(this.goal)) {
                return reconstructPath(cameFrom, current.position());
            }

            for (int[] offset : NEIGHBORS) {
                BlockPos next = current.position().offset(offset[0], offset[1], offset[2]);
                if (closed.contains(next)
                        || !this.grid.isInWindow(next)
                        || !isInsideSearchRadius(next)
                        || !canMove(current.position(), next, offset)) {
                    continue;
                }

                double tentativeG = current.gScore() + cost(offset);
                Double previousG = gScores.get(next);
                if (previousG != null && tentativeG >= previousG) {
                    continue;
                }

                cameFrom.put(next, current.position());
                gScores.put(next, tentativeG);
                open.add(new Node(next, tentativeG, heuristic(next)));
            }
        }

        return List.of();
    }

    private boolean canMove(BlockPos from, BlockPos to, int[] offset) {
        if (!canOccupy(to)) {
            return false;
        }

        // 26 方向搜索的基本防切角规则：斜向移动要求对应的轴向邻居也可站立。
        if (offset[0] != 0 && !canOccupy(from.offset(offset[0], 0, 0))) {
            return false;
        }
        if (offset[1] != 0 && !canOccupy(from.offset(0, offset[1], 0))) {
            return false;
        }
        return offset[2] == 0 || canOccupy(from.offset(0, 0, offset[2]));
    }

    private boolean canOccupy(BlockPos pos) {
        int minX = (int) Math.floor(bodyMinX(pos));
        int minY = (int) Math.floor(bodyMinY(pos));
        int minZ = (int) Math.floor(bodyMinZ(pos));
        int maxX = (int) Math.ceil(bodyMaxX(pos)) - 1;
        int maxY = (int) Math.ceil(bodyMaxY(pos)) - 1;
        int maxZ = (int) Math.ceil(bodyMaxZ(pos)) - 1;
        return this.grid.findBlockedInVolume(minX, minY, minZ, maxX, maxY, maxZ)
                == VoxelCollisionCache.NO_BLOCK;
    }

    private boolean isInsideSearchRadius(BlockPos pos) {
        return this.start.distSqr(pos) <= (long) this.searchRadius * this.searchRadius;
    }

    private double heuristic(BlockPos pos) {
        return Math.sqrt(this.goal.distSqr(pos));
    }

    private static double cost(int[] offset) {
        return Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        ArrayList<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        while (current != null) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private double bodyMinX(BlockPos pos) {
        return pos.getX() + 0.5 - this.profile.width() * 0.5 + EPSILON;
    }

    private double bodyMinY(BlockPos pos) {
        return pos.getY() + EPSILON;
    }

    private double bodyMinZ(BlockPos pos) {
        return pos.getZ() + 0.5 - this.profile.width() * 0.5 + EPSILON;
    }

    private double bodyMaxX(BlockPos pos) {
        return pos.getX() + 0.5 + this.profile.width() * 0.5 - EPSILON;
    }

    private double bodyMaxY(BlockPos pos) {
        return pos.getY() + this.profile.height() - EPSILON;
    }

    private double bodyMaxZ(BlockPos pos) {
        return pos.getZ() + 0.5 + this.profile.width() * 0.5 - EPSILON;
    }

    private static int[][] createNeighbors() {
        ArrayList<int[]> offsets = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    offsets.add(new int[]{x, y, z});
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }

    private record Node(BlockPos position, double gScore, double heuristic) {

        private static final Comparator<Node> COMPARATOR = Comparator
                .comparingDouble(Node::fScore)
                .thenComparingDouble(Node::heuristic);

        private double fScore() {
            return this.gScore + this.heuristic;
        }
    }
}
