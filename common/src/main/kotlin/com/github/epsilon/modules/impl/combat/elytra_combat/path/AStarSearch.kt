package com.github.epsilon.modules.impl.combat.elytra_combat.path

import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor
import net.minecraft.core.BlockPos
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 基础 26 方向 A*。
 *
 * 只在体素缓存上执行标准 open/closed、g 分数、欧氏启发式和父节点回溯，
 * 返回从起点到终点的原始方块路径。不运行滑翔轨迹预演、临时障碍重试、路径压缩或其他额外修正。
 */
internal class AStarSearch(
    private val grid: VoxelCollisionCache,
    private val profile: ElytraMotionPredictor.PlayerCollisionProfile,
    private val start: BlockPos,
    private val goal: BlockPos,
    private val searchRadius: Int,
    private val maxNodes: Int,
) {

    fun findPath(): List<BlockPos> {
        // 标准 A* 数据结构：open 按 f 排序，closed 防止重复扩展。
        val open = PriorityQueue<Node>(Node.COMPARATOR)
        val gScores = HashMap<BlockPos, Double>()
        val cameFrom = HashMap<BlockPos, BlockPos>()
        val closed = HashSet<BlockPos>()

        gScores[this.start] = 0.0
        open.add(Node(this.start, 0.0, heuristic(this.start)))

        var visited = 0
        // 目标格被遮挡或超出搜索限制时，用 bestPosition 返回离目标最近的已探索节点。
        var bestPosition = this.start
        var bestHeuristic = heuristic(this.start)
        while (open.isNotEmpty() && visited < this.maxNodes) {
            val current = open.poll() ?: continue
            if (!closed.add(current.position)) {
                continue
            }
            visited++

            if (current.position == this.goal) {
                return reconstructPath(cameFrom, current.position)
            }
            if (current.heuristic < bestHeuristic) {
                bestHeuristic = current.heuristic
                bestPosition = current.position
            }

            for (offset in NEIGHBORS) {
                val next = current.position.offset(offset[0], offset[1], offset[2])
                // 节点必须在体素窗口、搜索半径内，并且玩家碰撞箱可以通过。
                if (closed.contains(next) ||
                    !this.grid.isInWindow(next) ||
                    !isInsideSearchRadius(next) ||
                    !canMove(current.position, next, offset)
                ) {
                    continue
                }

                val tentativeG = current.gScore + cost(offset)
                val previousG = gScores[next]
                if (previousG != null && tentativeG >= previousG) {
                    continue
                }

                // 记录父节点并松弛 g 分数；启发式相同则优先靠近目标的节点。
                cameFrom[next] = current.position
                gScores[next] = tentativeG
                open.add(Node(next, tentativeG, heuristic(next)))
            }
        }

        return if (bestPosition == this.start) emptyList() else reconstructPath(cameFrom, bestPosition)
    }

    private fun canMove(from: BlockPos, to: BlockPos, offset: IntArray): Boolean {
        if (!canOccupy(to)) {
            return false
        }

        // 26 方向搜索的基本防切角规则：斜向移动要求对应的轴向邻居也可站立。
        if (offset[0] != 0 && !canOccupy(from.offset(offset[0], 0, 0))) {
            return false
        }
        if (offset[1] != 0 && !canOccupy(from.offset(0, offset[1], 0))) {
            return false
        }
        return offset[2] == 0 || canOccupy(from.offset(0, 0, offset[2]))
    }

    private fun canOccupy(pos: BlockPos): Boolean {
        // 节点占用的定义：以该方块为脚底时，完整玩家 AABB 覆盖的体素全部为 FREE。
        val minX = floor(bodyMinX(pos)).toInt()
        val minY = floor(bodyMinY(pos)).toInt()
        val minZ = floor(bodyMinZ(pos)).toInt()
        val maxX = ceil(bodyMaxX(pos)).toInt() - 1
        val maxY = ceil(bodyMaxY(pos)).toInt() - 1
        val maxZ = ceil(bodyMaxZ(pos)).toInt() - 1
        return this.grid.findBlockedInVolume(minX, minY, minZ, maxX, maxY, maxZ) ==
                VoxelCollisionCache.NO_BLOCK
    }

    private fun isInsideSearchRadius(pos: BlockPos): Boolean {
        // 使用欧氏距离而非切比雪夫距离，与球形 Search Radius 的语义一致。
        return this.start.distSqr(pos) <= this.searchRadius.toLong() * this.searchRadius
    }

    private fun heuristic(pos: BlockPos): Double {
        // 三维欧氏距离，和 26 方向移动代价一致，因此是可采纳启发式。
        return sqrt(this.goal.distSqr(pos))
    }

    private fun bodyMinX(pos: BlockPos): Double {
        return pos.x + 0.5 - this.profile.width * 0.5 + EPSILON
    }

    private fun bodyMinY(pos: BlockPos): Double {
        return pos.y + EPSILON
    }

    private fun bodyMinZ(pos: BlockPos): Double {
        return pos.z + 0.5 - this.profile.width * 0.5 + EPSILON
    }

    private fun bodyMaxX(pos: BlockPos): Double {
        return pos.x + 0.5 + this.profile.width * 0.5 - EPSILON
    }

    private fun bodyMaxY(pos: BlockPos): Double {
        return pos.y + this.profile.height - EPSILON
    }

    private fun bodyMaxZ(pos: BlockPos): Double {
        return pos.z + 0.5 + this.profile.width * 0.5 - EPSILON
    }

    private data class Node(val position: BlockPos, val gScore: Double, val heuristic: Double) {

        // f = g + h；同 f 时优先 h 更小（即更接近目标）的节点。
        fun fScore(): Double {
            return this.gScore + this.heuristic
        }

        companion object {

            val COMPARATOR: Comparator<Node> = Comparator
                .comparingDouble<Node> { node -> node.fScore() }
                .thenComparingDouble { node -> node.heuristic }

        }

    }

    companion object {

        /**
         * 缩小碰撞箱边界，避免正好落在方块边界时把相邻格误算进来。
         */
        private const val EPSILON = 1.0E-7

        /**
         * 预生成的 26 个方向，避免每次扩展节点重复创建偏移数组。
         */
        private val NEIGHBORS: Array<IntArray> = createNeighbors()

        private fun cost(offset: IntArray): Double {
            // 对角移动代价为实际欧氏长度，保证不会低估路径成本。
            return sqrt((offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]).toDouble())
        }

        private fun reconstructPath(cameFrom: Map<BlockPos, BlockPos>, end: BlockPos): List<BlockPos> {
            // 从终点沿 cameFrom 回溯到起点，再反转为起 → 终顺序。
            val path = ArrayList<BlockPos>()
            var current: BlockPos? = end
            while (current != null) {
                path.add(current)
                current = cameFrom[current]
            }
            path.reverse()
            // 与 Java 版 List.copyOf 对齐：返回真正不可变的列表。toList() 在长度 >1 时只做
            // 防御性拷贝（底层仍是可变 ArrayList），会破坏 PathPlan 可跨线程只读共享的契约。
            return java.util.List.copyOf(path)
        }

        private fun createNeighbors(): Array<IntArray> {
            val offsets = ArrayList<IntArray>(26)
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue
                        }
                        offsets.add(intArrayOf(x, y, z))
                    }
                }
            }
            return offsets.toTypedArray()
        }

    }

}
