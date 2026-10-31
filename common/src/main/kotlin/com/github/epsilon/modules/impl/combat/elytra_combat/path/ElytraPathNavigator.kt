package com.github.epsilon.modules.impl.combat.elytra_combat.path

import com.github.epsilon.Constants
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor
import kotlin.math.abs
import kotlin.math.roundToInt
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * ElytraCombat 的后台单层 A* 路径服务。
 *
 * 客户端线程只负责有界采样；专用工作线程运行基础 A* 并输出原始方块路径。
 */
private const val DATA_SIZE_ALIGNMENT = 5

class ElytraPathNavigator {

    private val workerThreadName: String = "Epsilon-ElytraCombat-AStar"

    /**
     * 主线程写入请求，工作线程只保留最新一份；采样批次则按序号顺序消费。
     */
    private val requestedDataSize = AtomicInteger(DEFAULT_DATA_SIZE)
    private val sampleBatches = ConcurrentLinkedQueue<SampleBatch>()
    private val queuedSampleBatches = AtomicInteger()
    private val pendingRequest = AtomicReference<SearchRequest?>()
    private val latestResult = AtomicReference<SearchResult?>()
    private val workerMonitor = ReentrantLock()
    private val workerCondition = workerMonitor.newCondition()
    private val sampler = Sampler()

    @Volatile
    private var running: Boolean = false

    /**
     * 每次启停递增的代际号，旧线程退出后不会再处理新请求。
     */
    @Volatile
    private var workerGeneration: Long = 0L

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var workerGrid: VoxelCollisionCache? = null

    @Volatile
    private var workerEpoch: Long = Long.MIN_VALUE

    fun getPath(player: LocalPlayer, targetPos: Vec3, config: PathConfig): PathPlan {
        // 客户端线程只做有界采样和提交请求；路径计算全部在工作线程完成。
        startWorker()

        val dataSize = normalizeDataSize(this.requestedDataSize.get())
        val previousResult = this.latestResult.get()
        val pathAhead = previousResult?.path?.let { path -> if (path.points.size > 1) path.points[1] else null }

        val window = this.sampler.prepare(player, dataSize, targetPos, pathAhead)
        submitSearch(player, targetPos, config, window)

        val result = this.latestResult.get()
        val resultPath = result?.path
        if (resultPath != null && isResultUsable(result, player.position(), targetPos, window)) {
            // 消费结果时按玩家当前投影位置推进路径，避免朝身后节点飞。
            return advancePath(resultPath, player.position())
        }

        return PathPlan(player.position(), listOf(player.position()))
    }

    fun setDataSize(size: Int) {
        // 窗口尺寸变化会使旧体素和旧路径失效，全部清空等待重新采样。
        val normalized = normalizeDataSize(size)
        if (this.requestedDataSize.getAndSet(normalized) != normalized) {
            this.pendingRequest.set(null)
            this.latestResult.set(null)
            this.sampler.invalidate()
        }
    }

    fun stop() {
        // 关闭运行标志并递增代际，随后清空队列、缓存和最新结果。
        this.running = false
        this.workerGeneration++
        this.pendingRequest.set(null)
        this.latestResult.set(null)
        this.sampleBatches.clear()
        this.queuedSampleBatches.set(0)
        this.workerGrid = null
        this.workerEpoch = Long.MIN_VALUE
        this.sampler.invalidate()

        val thread = this.workerThread
        if (thread != null) {
            thread.interrupt()
        }
        this.workerMonitor.lock()
        try {
            this.workerCondition.signalAll()
        } finally {
            this.workerMonitor.unlock()
        }
    }

    private fun startWorker() {
        // 同一时刻只允许一个 daemon 工作线程；重复调用直接复用。
        val existing = this.workerThread
        if (this.running && existing != null && existing.isAlive) {
            return
        }

        this.running = true
        val generation = ++this.workerGeneration
        val thread = Thread({ workerLoop(generation) }, this.workerThreadName)
        thread.isDaemon = true
        this.workerThread = thread
        thread.start()
    }

    private fun submitSearch(
        player: LocalPlayer,
        targetPos: Vec3,
        config: PathConfig,
        window: WindowSnapshot,
    ) {
        // 有效半径必须留在体素窗口内，否则 A* 查询会大量落到 OUTSIDE_WINDOW。
        val effectiveRadius = maxOf(6, minOf(config.searchRadius, window.size / 2 - 1))
        this.pendingRequest.set(
            SearchRequest(
                window.epoch,
                window.sequence,
                window.size,
                window.origin,
                player.position(),
                targetPos,
                player.bbWidth,
                player.bbHeight,
                config.stopDistance,
                effectiveRadius,
                config.maxNodes,
            )
        )
    }

    private fun isResultUsable(
        result: SearchResult?,
        playerPos: Vec3,
        targetPos: Vec3,
        window: WindowSnapshot,
    ): Boolean {
        // 结果必须来自当前窗口代际，且在时间/起点/目标偏移容差内才可使用。
        if (result == null || result.path == null || result.epoch != window.epoch) {
            return false
        }

        val age = System.nanoTime() - result.createdNanos
        if (age < 0L || age > RESULT_MAX_AGE_NANOS) {
            return false
        }

        return result.startPos.distanceToSqr(playerPos) <= RESULT_MAX_START_DISTANCE_SQR &&
                result.targetPos.distanceToSqr(targetPos) <= RESULT_MAX_TARGET_DISTANCE_SQR
    }

    private fun workerLoop(generation: Long) {
        var lastSearchNanos = 0L

        while (isWorkerRunning(generation)) {
            try {
                drainSampleBatches()

                // 只处理最新请求：旧请求会被后续客户端 tick 覆盖，不排队等待。
                val initial = this.pendingRequest.getAndSet(null)
                if (initial == null) {
                    waitForWork(10L)
                    continue
                }
                var request: SearchRequest = initial

                val now = System.nanoTime()
                val waitNanos = SEARCH_INTERVAL_NANOS - (now - lastSearchNanos)
                if (waitNanos > 0L) {
                    waitForWork(maxOf(1L, waitNanos / 1_000_000L))
                    if (!isWorkerRunning(generation)) {
                        return
                    }
                    drainSampleBatches()
                    val newest = this.pendingRequest.getAndSet(null)
                    if (newest != null) {
                        request = newest
                    }
                }

                val grid = this.workerGrid
                if (request.epoch != this.workerEpoch || grid == null) {
                    continue
                }
                if (!isWorkerRunning(generation)) {
                    return
                }

                val result = evaluate(request, grid)
                if (result != null) {
                    this.latestResult.set(result)
                }
                lastSearchNanos = System.nanoTime()
            } catch (interrupted: InterruptedException) {
                return
            } catch (throwable: Throwable) {
                Constants.LOGGER.warn("Error in ElytraCombat path worker loop", throwable)
                try {
                    waitForWork(100L)
                } catch (interrupted: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun isWorkerRunning(generation: Long): Boolean {
        return this.running &&
                this.workerGeneration == generation &&
                !Thread.currentThread().isInterrupted
    }

    private fun waitForWork(millis: Long) {
        this.workerMonitor.lock()
        try {
            this.workerCondition.awaitNanos(millis * 1_000_000L)
        } finally {
            this.workerMonitor.unlock()
        }
    }

    private fun drainSampleBatches() {
        var batch = this.sampleBatches.poll()
        while (batch != null) {
            this.queuedSampleBatches.updateAndGet { value -> maxOf(0, value - 1) }
            if (batch.epoch < this.workerEpoch) {
                batch = this.sampleBatches.poll()
                continue
            }

            // 窗口尺寸或代际变化时直接重建缓存，而不是逐格搬运。
            val currentGrid = this.workerGrid
            if (currentGrid == null ||
                batch.epoch > this.workerEpoch ||
                batch.size != currentGrid.size()
            ) {
                this.workerGrid = VoxelCollisionCache(batch.size)
                this.workerEpoch = batch.epoch
                this.latestResult.set(null)
            }

            val grid = this.workerGrid ?: run {
                batch = this.sampleBatches.poll()
                continue
            }
            grid.setWindowOrigin(batch.origin, batch.sequence)
            val positions = batch.positions
            val states = batch.states
            for (i in positions.indices) {
                grid.applySample(positions[i], states[i], batch.sequence)
            }

            batch = this.sampleBatches.poll()
        }
    }

    private fun evaluate(request: SearchRequest, grid: VoxelCollisionCache): SearchResult? {
        val start = BlockPos.containing(request.playerPos)
        if (!grid.isInWindow(start)) {
            return null
        }

        if (request.playerPos.distanceTo(request.targetPos) <= request.stopDistance) {
            // 已进入停止距离时返回单点路径，消费端会把它视为悬停。
            return result(request, stopPath(request))
        }

        val limitedTarget = limitTarget(request.playerPos, request.targetPos, request.searchRadius)
        // A* 只能搜索当前体素窗口；超出窗口的目标先裁剪到窗口边界。
        val goal = grid.clampToWindow(BlockPos.containing(limitedTarget))
        val profile = ElytraMotionPredictor.PlayerCollisionProfile(request.playerWidth.toDouble(), request.playerHeight.toDouble())
        val search = AStarSearch(
            grid,
            profile,
            start,
            goal,
            request.searchRadius,
            request.maxNodes,
        )
        val nodes = search.findPath()
        if (nodes.size < 2) {
            // 搜索没有任何可走节点时才返回停止路径。
            return result(request, stopPath(request))
        }
        return result(request, toPathPlan(request.playerPos, nodes))
    }

    private inner class Sampler {

        /**
         * 采样器只允许在客户端线程访问；epoch 变化代表窗口或维度整体重建。
         */
        private var level: Level? = null
        private var dataSize: Int = 0
        private var epoch: Long = 0L
        private var originX: Int = 0
        private var originY: Int = 0
        private var originZ: Int = 0
        private var sampleSequence: Long = 0L
        private var refreshTicks: Int = 0
        private var invalidated: Boolean = true
        private var initialFillPending: Boolean = false
        private var initialFillCursor: Int = 0
        private var queue: LongQueue = LongQueue(16)
        private var scheduledPositions: LongArray = LongArray(0)

        fun invalidate() {
            this.invalidated = true
        }

        fun prepare(player: LocalPlayer, requestedSize: Int, targetPos: Vec3, pathAhead: Vec3?): WindowSnapshot {
            // 维度、尺寸或外部 invalidate 变化时整窗重建。
            val size = normalizeDataSize(requestedSize)
            if (this.level !== player.level() || this.dataSize != size || this.invalidated) {
                reset(player.level(), player.blockPosition(), size)
            }

            updateWindow(player.blockPosition(), size)
            enqueueInitialBatch(size)

            // 周期性刷新玩家、目标和当前路径前方的局部区域，优先保证近处体素新鲜。
            if (this.refreshTicks <= 0) {
                enqueueNeighborhood(player.blockPosition(), LOCAL_SAMPLE_RADIUS)
                enqueueNeighborhood(BlockPos.containing(targetPos), LOCAL_SAMPLE_RADIUS)
                if (pathAhead != null) {
                    enqueueNeighborhood(BlockPos.containing(pathAhead), LOCAL_SAMPLE_RADIUS)
                }
                this.refreshTicks = REFRESH_INTERVAL_TICKS
            } else {
                this.refreshTicks--
            }

            drain(player)
            return WindowSnapshot(
                this.epoch,
                this.sampleSequence,
                size,
                BlockPos(this.originX, this.originY, this.originZ),
            )
        }

        private fun reset(level: Level, playerPos: BlockPos, size: Int) {
            // epoch 自增后，工作线程会丢弃所有旧窗口样本和旧路径结果。
            this.level = level
            this.dataSize = size
            this.epoch++
            this.sampleSequence = 0L
            this.refreshTicks = 0
            this.invalidated = false
            this.initialFillPending = true
            this.initialFillCursor = 0
            this.queue = LongQueue(minOf(16_384, maxOf(64, size * size)))
            this.scheduledPositions = LongArray(size * size * size)
            java.util.Arrays.fill(this.scheduledPositions, Long.MIN_VALUE)

            val desiredX = alignedWindowOrigin(playerPos.x, size)
            val desiredY = alignedWindowOrigin(playerPos.y, size)
            val desiredZ = alignedWindowOrigin(playerPos.z, size)
            this.originX = desiredX
            this.originY = desiredY
            this.originZ = desiredZ

            enqueueNeighborhood(playerPos, LOCAL_SAMPLE_RADIUS)
        }

        private fun updateWindow(playerPos: BlockPos, size: Int) {
            val newX = alignedWindowOrigin(playerPos.x, size)
            val newY = alignedWindowOrigin(playerPos.y, size)
            val newZ = alignedWindowOrigin(playerPos.z, size)
            if (newX == this.originX && newY == this.originY && newZ == this.originZ) {
                return
            }

            val oldOriginX = this.originX
            val oldOriginY = this.originY
            val oldOriginZ = this.originZ
            val oldMaxX = this.originX + size - 1
            val oldMaxY = this.originY + size - 1
            val oldMaxZ = this.originZ + size - 1
            val newMaxX = newX + size - 1
            val newMaxY = newY + size - 1
            val newMaxZ = newZ + size - 1

            val deltaX = abs(newX - this.originX)
            val deltaY = abs(newY - this.originY)
            val deltaZ = abs(newZ - this.originZ)
            val exposedUpperBound = size * size * (deltaX + deltaY + deltaZ)
            // 小范围滚动只给新增切片排样；位移过大或初始填充时直接重建队列。
            val rescheduleWindow = this.initialFillPending ||
                    deltaX > size ||
                    deltaY > size ||
                    deltaZ > size ||
                    exposedUpperBound > MAX_SAMPLES_PER_TICK * 4
            if (rescheduleWindow) {
                this.queue = LongQueue(minOf(16_384, maxOf(64, size * size)))
                this.scheduledPositions = LongArray(size * size * size)
                java.util.Arrays.fill(this.scheduledPositions, Long.MIN_VALUE)
                this.originX = newX
                this.originY = newY
                this.originZ = newZ
                this.initialFillPending = true
                this.initialFillCursor = 0
                enqueueNeighborhood(playerPos, LOCAL_SAMPLE_RADIUS)
            } else {
                this.originX = newX
                this.originY = newY
                this.originZ = newZ
                if (newX > oldOriginX) {
                    enqueueRegion(oldMaxX + 1, this.originY, this.originZ, newMaxX, newMaxY, newMaxZ)
                } else if (newX < oldOriginX) {
                    enqueueRegion(newX, this.originY, this.originZ, oldOriginX - 1, newMaxY, newMaxZ)
                }
                if (newY > oldOriginY) {
                    enqueueRegion(this.originX, oldMaxY + 1, this.originZ, newMaxX, newMaxY, newMaxZ)
                } else if (newY < oldOriginY) {
                    enqueueRegion(this.originX, newY, this.originZ, newMaxX, oldOriginY - 1, newMaxZ)
                }
                if (newZ > oldOriginZ) {
                    enqueueRegion(this.originX, this.originY, oldMaxZ + 1, newMaxX, newMaxY, newMaxZ)
                } else if (newZ < oldOriginZ) {
                    enqueueRegion(this.originX, this.originY, newZ, newMaxX, newMaxY, oldOriginZ - 1)
                }
            }
        }

        private fun enqueueInitialBatch(size: Int) {
            if (!this.initialFillPending) {
                return
            }

            val volume = size * size * size
            val plane = size * size
            // 每 tick 最多排入 4096 个初始体素，避免开启模块时卡顿。
            var added = 0
            while (this.initialFillCursor < volume &&
                added < MAX_SAMPLES_PER_TICK &&
                this.queue.size() < MAX_SAMPLES_PER_TICK * 2
            ) {
                val cursor = this.initialFillCursor++
                val x = cursor / plane
                val remainder = cursor % plane
                val y = remainder / size
                val z = remainder % size
                enqueue(this.originX + x, this.originY + y, this.originZ + z)
                added++
            }

            if (this.initialFillCursor >= volume) {
                this.initialFillPending = false
            }
        }

        private fun enqueueNeighborhood(center: BlockPos, radius: Int) {
            val minX = maxOf(this.originX, center.x - radius)
            val minY = maxOf(this.originY, center.y - radius)
            val minZ = maxOf(this.originZ, center.z - radius)
            val maxX = minOf(this.originX + this.dataSize - 1, center.x + radius)
            val maxY = minOf(this.originY + this.dataSize - 1, center.y + radius)
            val maxZ = minOf(this.originZ + this.dataSize - 1, center.z + radius)
            enqueueRegion(minX, minY, minZ, maxX, maxY, maxZ)
        }

        private fun enqueueRegion(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) {
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    for (z in minZ..maxZ) {
                        enqueue(x, y, z)
                    }
                }
            }
        }

        private fun enqueue(x: Int, y: Int, z: Int) {
            if (!isInWindow(x, y, z)) {
                return
            }

            val packedPos = BlockPos.asLong(x, y, z)
            val index = index(x, y, z)
            if (this.scheduledPositions[index] == packedPos) {
                return
            }

            this.scheduledPositions[index] = packedPos
            this.queue.enqueue(packedPos)
        }

        private fun drain(player: LocalPlayer) {
            if (queuedSampleBatches.get() >= MAX_QUEUED_SAMPLE_BATCHES) {
                return
            }

            val positions = LongArray(MAX_SAMPLES_PER_TICK)
            val states = ByteArray(MAX_SAMPLES_PER_TICK)
            // CollisionContext 让方块碰撞形状按当前玩家状态计算（例如潜行/飞行姿态）。
            val context = CollisionContext.of(player)
            var count = 0

            while (count < MAX_SAMPLES_PER_TICK && !this.queue.isEmpty()) {
                val packedPos = this.queue.dequeue()
                val x = BlockPos.getX(packedPos)
                val y = BlockPos.getY(packedPos)
                val z = BlockPos.getZ(packedPos)
                val index = index(x, y, z)
                if (this.scheduledPositions[index] == packedPos) {
                    this.scheduledPositions[index] = Long.MIN_VALUE
                }

                if (!isInWindow(x, y, z)) {
                    continue
                }

                positions[count] = packedPos
                states[count] = sampleState(x, y, z, context)
                count++
            }

            if (count == 0) {
                return
            }

            val sequence = ++this.sampleSequence
            sampleBatches.offer(
                SampleBatch(
                    this.epoch,
                    sequence,
                    this.dataSize,
                    BlockPos(this.originX, this.originY, this.originZ),
                    java.util.Arrays.copyOf(positions, count),
                    java.util.Arrays.copyOf(states, count),
                )
            )
            queuedSampleBatches.incrementAndGet()
        }

        private fun sampleState(x: Int, y: Int, z: Int, context: CollisionContext): Byte {
            // 未加载区块记 UNKNOWN，A* 会把它当作不可通行；世界边界外直接记 BLOCKED。
            val pos = BlockPos(x, y, z)
            val world = this.level ?: return VoxelCollisionCache.UNKNOWN
            if (!world.isInWorldBounds(pos)) {
                return VoxelCollisionCache.BLOCKED
            }
            if (!world.hasChunkAt(pos)) {
                return VoxelCollisionCache.UNKNOWN
            }

            val state: BlockState = world.getBlockState(pos)
            return if (state.getCollisionShape(world, pos, context).isEmpty()) {
                VoxelCollisionCache.FREE
            } else {
                VoxelCollisionCache.BLOCKED
            }
        }

        private fun isInWindow(x: Int, y: Int, z: Int): Boolean {
            return x >= this.originX && x < this.originX + this.dataSize &&
                    y >= this.originY && y < this.originY + this.dataSize &&
                    z >= this.originZ && z < this.originZ + this.dataSize
        }

        private fun index(x: Int, y: Int, z: Int): Int {
            val wrappedX = (x).mod(this.dataSize)
            val wrappedY = (y).mod(this.dataSize)
            val wrappedZ = (z).mod(this.dataSize)
            return (wrappedX * this.dataSize + wrappedY) * this.dataSize + wrappedZ
        }


    }

    private class LongQueue(initialCapacity: Int) {

        /**
         * 为方块采样设计的原始 long 环形队列，避免装箱和递归队列开销。
         */
        private var values: LongArray = LongArray(maxOf(16, initialCapacity))
        private var head: Int = 0
        private var count: Int = 0

        fun enqueue(value: Long) {
            if (this.count == this.values.size) {
                grow()
            }
            val index = (this.head + this.count) % this.values.size
            this.values[index] = value
            this.count++
        }

        fun dequeue(): Long {
            if (this.count == 0) {
                throw IllegalStateException("Queue is empty")
            }
            val value = this.values[this.head]
            this.head = (this.head + 1) % this.values.size
            this.count--
            return value
        }

        fun isEmpty(): Boolean {
            return this.count == 0
        }

        fun size(): Int {
            return this.count
        }

        private fun grow() {
            val grown = LongArray(this.values.size * 2)
            for (i in 0 until this.count) {
                grown[i] = this.values[(this.head + i) % this.values.size]
            }
            this.values = grown
            this.head = 0
        }

    }

    private data class WindowSnapshot(val epoch: Long, val sequence: Long, val size: Int, val origin: BlockPos)

    private data class SampleBatch(
        val epoch: Long,
        val sequence: Long,
        val size: Int,
        val origin: BlockPos,
        val positions: LongArray,
        val states: ByteArray,
    )

    private data class SearchRequest(
        val epoch: Long,
        val sequence: Long,
        val size: Int,
        val origin: BlockPos,
        val playerPos: Vec3,
        val targetPos: Vec3,
        val playerWidth: Float,
        val playerHeight: Float,
        val stopDistance: Double,
        val searchRadius: Int,
        val maxNodes: Int,
    )

    private data class SearchResult(
        val epoch: Long,
        val sequence: Long,
        val createdNanos: Long,
        val startPos: Vec3,
        val targetPos: Vec3,
        val path: PathPlan?,
    )

    companion object {

        // 体素窗口与每 tick 采样预算；Data Size 必须是 5 的倍数。
        private const val DEFAULT_DATA_SIZE = 50
        private const val MIN_DATA_SIZE = 25
        private const val MAX_DATA_SIZE = 100
        private const val MAX_SAMPLES_PER_TICK = 4096
        private const val MAX_QUEUED_SAMPLE_BATCHES = 64
        private const val REFRESH_INTERVAL_TICKS = 20
        private const val LOCAL_SAMPLE_RADIUS = 7

        // 异步搜索节流与结果有效性检查：避免主线程等待，也避免复用过期路径。
        private const val SEARCH_INTERVAL_NANOS = 50_000_000L
        private const val RESULT_MAX_AGE_NANOS = 150_000_000L
        private const val RESULT_MAX_START_DISTANCE_SQR = 25.0
        private const val RESULT_MAX_TARGET_DISTANCE_SQR = 64.0
        private const val PATH_LOOKAHEAD_DISTANCE = 3.0

        internal fun normalizeDataSize(size: Int): Int {
            val clamped = size.coerceIn(MIN_DATA_SIZE, MAX_DATA_SIZE)
            return (clamped.toFloat() / DATA_SIZE_ALIGNMENT).roundToInt() * DATA_SIZE_ALIGNMENT
        }

        /**
         * 返回原始 A* 路径中玩家所在航段之后的节点，避免复用旧结果时朝身后的节点飞。
         */
        private fun advancePath(path: PathPlan, playerPos: Vec3): PathPlan {
            val points = path.points
            if (points.size < 2) {
                return PathPlan(playerPos, listOf(playerPos))
            }

            for (i in 0 until points.size - 1) {
                val from = points[i]
                val to = points[i + 1]
                val segment = to.subtract(from)
                val segmentLengthSqr = segment.lengthSqr()
                val projection = if (segmentLengthSqr < 1.0E-8) {
                    0.0
                } else {
                    playerPos.subtract(from).dot(segment) / segmentLengthSqr
                }
                if (projection < 1.0) {
                    // 找到玩家当前所在航段，再从该段向前取固定前视距离的航点。
                    var next = i + 1
                    val projected = from.add(segment.scale((projection).coerceIn(0.0, 1.0)))
                    var remaining = projected.distanceTo(points[next])
                    while (next < points.size - 1) {
                        val step = points[next].distanceTo(points[next + 1])
                        if (remaining + step > PATH_LOOKAHEAD_DISTANCE) {
                            break
                        }
                        remaining += step
                        next++
                    }
                    return PathPlan(points[next], points)
                }
            }
            return PathPlan(playerPos, listOf(playerPos))
        }

        private fun toPathPlan(playerPos: Vec3, nodes: List<BlockPos>): PathPlan {
            // 第一点用真实玩家位置，后续点使用 A* 节点中心，保持跨线程不可变。
            val points = ArrayList<Vec3>(nodes.size)
            points.add(playerPos)
            for (i in 1 until nodes.size) {
                points.add(Vec3.atBottomCenterOf(nodes[i]))
            }
            return PathPlan(points[1], points.toList())
        }

        private fun stopPath(request: SearchRequest): PathPlan {
            return PathPlan(request.playerPos, listOf(request.playerPos))
        }

        private fun limitTarget(from: Vec3, target: Vec3, searchRadius: Int): Vec3 {
            val delta = target.subtract(from)
            val distance = delta.length()
            if (distance <= searchRadius || distance < 0.001) {
                return target
            }
            return from.add(delta.normalize().scale(searchRadius.toDouble()))
        }

        private fun result(request: SearchRequest, path: PathPlan): SearchResult {
            return SearchResult(
                request.epoch,
                request.sequence,
                System.nanoTime(),
                request.playerPos,
                request.targetPos,
                path,
            )
        }

    }

}

/**
 * 窗口原点对齐到 5 的倍数，让 5³ 粗粒度统计保持稳定。
 */
private fun alignedWindowOrigin(center: Int, size: Int): Int {
    val origin = center - size / 2
    return (origin).floorDiv(DATA_SIZE_ALIGNMENT) * DATA_SIZE_ALIGNMENT
}
