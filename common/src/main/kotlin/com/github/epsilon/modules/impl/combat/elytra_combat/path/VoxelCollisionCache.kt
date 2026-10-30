package com.github.epsilon.modules.impl.combat.elytra_combat.path

import net.minecraft.core.BlockPos

/**
 * 工作线程独占的分层体素缓存。
 *
 * 细体素使用世界坐标取模寻址；槽位同时保存坐标标签，因此滚动窗口复用槽位时，
 * 旧坐标不会被当成新坐标读取。
 */
class VoxelCollisionCache(private val gridSize: Int) {

    init {
        require(gridSize >= COARSE_SIZE && gridSize % COARSE_SIZE == 0) {
            "Voxel grid size must be a positive multiple of 5"
        }
    }

    private val coarseSide: Int = gridSize / COARSE_SIZE
    private val mediumSide: Int = coarseSide * 3
    private val volume: Int = gridSize * gridSize * gridSize

    /**
     * 细体素按世界坐标取模映射；positions 保存坐标标签，sampleVersions 防止旧样本覆盖新样本。
     */
    private val states: ByteArray = ByteArray(volume)
    private val positions: LongArray = LongArray(volume)
    private val sampleVersions: LongArray = LongArray(volume)

    /**
     * 5³ 粗粒度统计，用于快速判断大块区域是否全 FREE。
     */
    private val coarseValid: ShortArray = ShortArray(coarseSide * coarseSide * coarseSide)
    private val coarseBlocked: ShortArray = ShortArray(coarseSide * coarseSide * coarseSide)
    private val coarsePositions: LongArray = LongArray(coarseSide * coarseSide * coarseSide)

    /**
     * 每轴 2+2+1 的中间粒度统计，用于细粒度回退查询。
     */
    private val mediumValid: ShortArray = ShortArray(mediumSide * mediumSide * mediumSide)
    private val mediumBlocked: ShortArray = ShortArray(mediumSide * mediumSide * mediumSide)
    private val mediumPositions: LongArray = LongArray(mediumSide * mediumSide * mediumSide)

    private var originX: Int = 0
    private var originY: Int = 0
    private var originZ: Int = 0
    private var windowSequence: Long = Long.MIN_VALUE

    fun size(): Int {
        return this.gridSize
    }

    fun setWindowOrigin(origin: BlockPos, sequence: Long) {
        // 只接受更新的窗口序号，防止迟到的旧批次把 origin 回退。
        if (sequence < this.windowSequence) {
            return
        }

        this.originX = origin.x
        this.originY = origin.y
        this.originZ = origin.z
        this.windowSequence = sequence
    }

    fun isInWindow(pos: BlockPos): Boolean {
        return isInWindow(pos.x, pos.y, pos.z)
    }

    fun isInWindow(x: Int, y: Int, z: Int): Boolean {
        return x >= this.originX && x < this.originX + this.gridSize &&
                y >= this.originY && y < this.originY + this.gridSize &&
                z >= this.originZ && z < this.originZ + this.gridSize
    }

    fun clampToWindow(pos: BlockPos): BlockPos {
        val x = pos.x.coerceIn(this.originX, this.originX + this.gridSize - 1)
        val y = pos.y.coerceIn(this.originY, this.originY + this.gridSize - 1)
        val z = pos.z.coerceIn(this.originZ, this.originZ + this.gridSize - 1)
        return if (x == pos.x && y == pos.y && z == pos.z) pos else BlockPos(x, y, z)
    }

    fun applySample(packedPos: Long, state: Byte, sequence: Long) {
        val x = BlockPos.getX(packedPos)
        val y = BlockPos.getY(packedPos)
        val z = BlockPos.getZ(packedPos)
        if (!isInWindow(x, y, z)) {
            return
        }

        val index = index(x, y, z, this.gridSize)
        // 同一个环形槽可能被新窗口复用，低序号样本不能覆盖高序号样本。
        if (sequence < this.sampleVersions[index]) {
            return
        }

        val previousPos = this.positions[index]
        val previousState = this.states[index]
        if (previousPos == packedPos && previousState == state) {
            this.sampleVersions[index] = sequence
            return
        }

        if (previousState != UNKNOWN) {
            // 状态变化时先撤销旧值对粗/中粒度计数的贡献。
            updateAggregates(previousPos, previousState, -1)
        }

        this.positions[index] = packedPos
        this.states[index] = state
        this.sampleVersions[index] = sequence

        if (state != UNKNOWN) {
            updateAggregates(packedPos, state, 1)
        }
    }

    /**
     * 返回查询体积内第一个非 FREE 体素；全部可通过时返回 [NO_BLOCK]。
     */
    fun findBlockedInVolume(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Long {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return NO_BLOCK
        }

        if (minX < this.originX || minY < this.originY || minZ < this.originZ ||
            maxX > this.originX + this.gridSize - 1 ||
            maxY > this.originY + this.gridSize - 1 ||
            maxZ > this.originZ + this.gridSize - 1
        ) {
            return OUTSIDE_WINDOW
        }

        if (isCoarseVolumeFree(minX, minY, minZ, maxX, maxY, maxZ)) {
            // 整块 5³ 已完整采样且无阻塞时可直接判定通过。
            return NO_BLOCK
        }
        if (isMediumVolumeFree(minX, minY, minZ, maxX, maxY, maxZ)) {
            return NO_BLOCK
        }

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    if (!isFree(x, y, z)) {
                        return BlockPos.asLong(x, y, z)
                    }
                }
            }
        }
        return NO_BLOCK
    }

    private fun isCoarseVolumeFree(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean {
        val minCellX = Math.floorDiv(minX, COARSE_SIZE)
        val minCellY = Math.floorDiv(minY, COARSE_SIZE)
        val minCellZ = Math.floorDiv(minZ, COARSE_SIZE)
        val maxCellX = Math.floorDiv(maxX, COARSE_SIZE)
        val maxCellY = Math.floorDiv(maxY, COARSE_SIZE)
        val maxCellZ = Math.floorDiv(maxZ, COARSE_SIZE)

        for (x in minCellX..maxCellX) {
            for (y in minCellY..maxCellY) {
                for (z in minCellZ..maxCellZ) {
                    val index = index(x, y, z, this.coarseSide)
                    val packedCell = BlockPos.asLong(x, y, z)
                    if (this.coarsePositions[index] != packedCell ||
                        this.coarseValid[index].toInt() != COARSE_VOLUME ||
                        this.coarseBlocked[index].toInt() != 0
                    ) {
                        // 坐标标签不匹配、未采满或存在 BLOCKED，都不能走快速路径。
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isMediumVolumeFree(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean {
        val minCellX = mediumCoordinate(minX)
        val minCellY = mediumCoordinate(minY)
        val minCellZ = mediumCoordinate(minZ)
        val maxCellX = mediumCoordinate(maxX)
        val maxCellY = mediumCoordinate(maxY)
        val maxCellZ = mediumCoordinate(maxZ)

        for (x in minCellX..maxCellX) {
            for (y in minCellY..maxCellY) {
                for (z in minCellZ..maxCellZ) {
                    val index = index(x, y, z, this.mediumSide)
                    val packedCell = BlockPos.asLong(x, y, z)
                    if (this.mediumPositions[index] != packedCell ||
                        this.mediumValid[index].toInt() != mediumCellVolume(x, y, z) ||
                        this.mediumBlocked[index].toInt() != 0
                    ) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isFree(x: Int, y: Int, z: Int): Boolean {
        val index = index(x, y, z, this.gridSize)
        return this.positions[index] == BlockPos.asLong(x, y, z) && this.states[index] == FREE
    }

    private fun updateAggregates(packedPos: Long, state: Byte, delta: Int) {
        val x = BlockPos.getX(packedPos)
        val y = BlockPos.getY(packedPos)
        val z = BlockPos.getZ(packedPos)
        // 粗粒度记录阻塞数，中粒度用于缩小回退范围。
        val blockedDelta = if (state == BLOCKED) delta else 0

        updateCoarseAggregate(x, y, z, delta, blockedDelta)
        updateMediumAggregate(x, y, z, delta, blockedDelta)
    }

    private fun updateCoarseAggregate(x: Int, y: Int, z: Int, delta: Int, blockedDelta: Int) {
        val coarseX = Math.floorDiv(x, COARSE_SIZE)
        val coarseY = Math.floorDiv(y, COARSE_SIZE)
        val coarseZ = Math.floorDiv(z, COARSE_SIZE)
        val coarseIndex = index(coarseX, coarseY, coarseZ, this.coarseSide)
        val packedCoarse = BlockPos.asLong(coarseX, coarseY, coarseZ)
        if (this.coarsePositions[coarseIndex] != packedCoarse) {
            // 旧窗口的桶可能已被同模的新桶替换，此时不能继续递减旧计数。
            if (delta < 0) {
                return
            }
            this.coarsePositions[coarseIndex] = packedCoarse
            this.coarseValid[coarseIndex] = 0
            this.coarseBlocked[coarseIndex] = 0
        }
        this.coarseValid[coarseIndex] = (this.coarseValid[coarseIndex] + delta).toShort()
        this.coarseBlocked[coarseIndex] = (this.coarseBlocked[coarseIndex] + blockedDelta).toShort()
    }

    private fun updateMediumAggregate(x: Int, y: Int, z: Int, delta: Int, blockedDelta: Int) {
        val mediumX = mediumCoordinate(x)
        val mediumY = mediumCoordinate(y)
        val mediumZ = mediumCoordinate(z)
        val mediumIndex = index(mediumX, mediumY, mediumZ, this.mediumSide)
        val packedMedium = BlockPos.asLong(mediumX, mediumY, mediumZ)
        if (this.mediumPositions[mediumIndex] != packedMedium) {
            if (delta < 0) {
                return
            }
            this.mediumPositions[mediumIndex] = packedMedium
            this.mediumValid[mediumIndex] = 0
            this.mediumBlocked[mediumIndex] = 0
        }
        this.mediumValid[mediumIndex] = (this.mediumValid[mediumIndex] + delta).toShort()
        this.mediumBlocked[mediumIndex] = (this.mediumBlocked[mediumIndex] + blockedDelta).toShort()
    }

    companion object {

        /**
         * UNKNOWN 与 BLOCKED 都视为不可通行，只有明确采样的 FREE 允许通过。
         */
        const val UNKNOWN: Byte = 0
        const val FREE: Byte = 1
        const val BLOCKED: Byte = 2

        /**
         * 查询结果哨兵值，避免额外分配可选对象。
         */
        const val NO_BLOCK: Long = Long.MIN_VALUE
        const val OUTSIDE_WINDOW: Long = Long.MAX_VALUE

        private const val COARSE_SIZE = 5
        private const val COARSE_VOLUME = COARSE_SIZE * COARSE_SIZE * COARSE_SIZE

        private fun mediumCoordinate(coordinate: Int): Int {
            // 5 格固定拆成 2/2/1 三段，避免 5 不能被 2 整除的歧义。
            val local = Math.floorMod(coordinate, COARSE_SIZE)
            val child = if (local < 2) 0 else if (local < 4) 1 else 2
            return Math.floorDiv(coordinate, COARSE_SIZE) * 3 + child
        }

        private fun mediumCellVolume(x: Int, y: Int, z: Int): Int {
            return mediumChildSize(x) * mediumChildSize(y) * mediumChildSize(z)
        }

        private fun mediumChildSize(coordinate: Int): Int {
            return if (Math.floorMod(coordinate, 3) < 2) 2 else 1
        }

        private fun index(x: Int, y: Int, z: Int, side: Int): Int {
            val wrappedX = Math.floorMod(x, side)
            val wrappedY = Math.floorMod(y, side)
            val wrappedZ = Math.floorMod(z, side)
            return (wrappedX * side + wrappedY) * side + wrappedZ
        }

    }

}
