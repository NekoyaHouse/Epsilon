package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ElytraCombat 的后台单层 A* 路径服务。
 *
 * <p>客户端线程只负责有界采样；专用工作线程运行基础 A* 并输出原始方块路径。</p>
 */
public final class ElytraPathNavigator {

    // 体素窗口与每 tick 采样预算；Data Size 必须是 5 的倍数。
    private static final int DEFAULT_DATA_SIZE = 50;
    private static final int MIN_DATA_SIZE = 25;
    private static final int MAX_DATA_SIZE = 100;
    private static final int DATA_SIZE_ALIGNMENT = 5;
    private static final int MAX_SAMPLES_PER_TICK = 4096;
    private static final int MAX_QUEUED_SAMPLE_BATCHES = 64;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int LOCAL_SAMPLE_RADIUS = 7;
    // 异步搜索节流与结果有效性检查：避免主线程等待，也避免复用过期路径。
    private static final long SEARCH_INTERVAL_NANOS = 50_000_000L;
    private static final long RESULT_MAX_AGE_NANOS = 150_000_000L;
    private static final double RESULT_MAX_START_DISTANCE_SQR = 25.0;
    private static final double RESULT_MAX_TARGET_DISTANCE_SQR = 64.0;
    private static final double PATH_LOOKAHEAD_DISTANCE = 3.0;
    private final String workerThreadName;
    /** 主线程写入请求，工作线程只保留最新一份；采样批次则按序号顺序消费。 */
    private final AtomicInteger requestedDataSize = new AtomicInteger(DEFAULT_DATA_SIZE);
    private final ConcurrentLinkedQueue<SampleBatch> sampleBatches = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedSampleBatches = new AtomicInteger();
    private final AtomicReference<SearchRequest> pendingRequest = new AtomicReference<>();
    private final AtomicReference<SearchResult> latestResult = new AtomicReference<>();
    private final Object workerMonitor = new Object();
    private final Sampler sampler = new Sampler();

    private volatile boolean running;
    /** 每次启停递增的代际号，旧线程退出后不会再处理新请求。 */
    private volatile long workerGeneration;
    private volatile Thread workerThread;
    private volatile VoxelCollisionCache workerGrid;
    private volatile long workerEpoch = Long.MIN_VALUE;

    public ElytraPathNavigator() {
        this.workerThreadName = "Epsilon-ElytraCombat-AStar";
    }

    static int normalizeDataSize(int size) {
        int clamped = Math.clamp(size, MIN_DATA_SIZE, MAX_DATA_SIZE);
        return Math.round((float) clamped / DATA_SIZE_ALIGNMENT) * DATA_SIZE_ALIGNMENT;
    }

    public PathPlan getPath(LocalPlayer player, Vec3 targetPos, PathConfig config) {
        // 客户端线程只做有界采样和提交请求；路径计算全部在工作线程完成。
        startWorker();

        int dataSize = normalizeDataSize(this.requestedDataSize.get());
        SearchResult previousResult = this.latestResult.get();
        Vec3 pathAhead = previousResult != null && previousResult.path() != null
                && previousResult.path().points().size() > 1
                ? previousResult.path().points().get(1)
                : null;

        Sampler.WindowSnapshot window = this.sampler.prepare(player, dataSize, targetPos, pathAhead);
        submitSearch(player, targetPos, config, window);

        SearchResult result = this.latestResult.get();
        if (isResultUsable(result, player.position(), targetPos, window)) {
            // 消费结果时按玩家当前投影位置推进路径，避免朝身后节点飞。
            return advancePath(result.path(), player.position());
        }

        return new PathPlan(player.position(), List.of(player.position()));
    }

    /**
     * 返回原始 A* 路径中玩家所在航段之后的节点，避免复用旧结果时朝身后的节点飞。
     */
    private static PathPlan advancePath(PathPlan path, Vec3 playerPos) {
        List<Vec3> points = path.points();
        if (points.size() < 2) {
            return new PathPlan(playerPos, List.of(playerPos));
        }

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            Vec3 segment = to.subtract(from);
            double segmentLengthSqr = segment.lengthSqr();
            double projection = segmentLengthSqr < 1.0E-8
                    ? 0.0
                    : playerPos.subtract(from).dot(segment) / segmentLengthSqr;
            if (projection < 1.0) {
                // 找到玩家当前所在航段，再从该段向前取固定前视距离的航点。
                int next = i + 1;
                Vec3 projected = from.add(segment.scale(Math.clamp(projection, 0.0, 1.0)));
                double remaining = projected.distanceTo(points.get(next));
                while (next < points.size() - 1) {
                    double step = points.get(next).distanceTo(points.get(next + 1));
                    if (remaining + step > PATH_LOOKAHEAD_DISTANCE) {
                        break;
                    }
                    remaining += step;
                    next++;
                }
                return new PathPlan(points.get(next), points);
            }
        }
        return new PathPlan(playerPos, List.of(playerPos));
    }

    public void setDataSize(int size) {
        // 窗口尺寸变化会使旧体素和旧路径失效，全部清空等待重新采样。
        int normalized = normalizeDataSize(size);
        if (this.requestedDataSize.getAndSet(normalized) != normalized) {
            this.pendingRequest.set(null);
            this.latestResult.set(null);
            this.sampler.invalidate();
        }
    }

    public void stop() {
        // 关闭运行标志并递增代际，随后清空队列、缓存和最新结果。
        this.running = false;
        this.workerGeneration++;
        this.pendingRequest.set(null);
        this.latestResult.set(null);
        this.sampleBatches.clear();
        this.queuedSampleBatches.set(0);
        this.workerGrid = null;
        this.workerEpoch = Long.MIN_VALUE;
        this.sampler.invalidate();

        Thread thread = this.workerThread;
        if (thread != null) {
            thread.interrupt();
        }
        synchronized (this.workerMonitor) {
            this.workerMonitor.notifyAll();
        }
    }

    private void startWorker() {
        // 同一时刻只允许一个 daemon 工作线程；重复调用直接复用。
        if (this.running && this.workerThread != null && this.workerThread.isAlive()) {
            return;
        }

        this.running = true;
        long generation = ++this.workerGeneration;
        Thread thread = new Thread(() -> workerLoop(generation), this.workerThreadName);
        thread.setDaemon(true);
        this.workerThread = thread;
        thread.start();
    }

    private void submitSearch(
            LocalPlayer player,
            Vec3 targetPos,
            PathConfig config,
            Sampler.WindowSnapshot window
    ) {
        // 有效半径必须留在体素窗口内，否则 A* 查询会大量落到 OUTSIDE_WINDOW。
        int effectiveRadius = Math.max(6, Math.min(config.searchRadius(), window.size() / 2 - 1));
        this.pendingRequest.set(new SearchRequest(
                window.epoch(),
                window.sequence(),
                window.size(),
                window.origin(),
                player.position(),
                targetPos,
                player.getBbWidth(),
                player.getBbHeight(),
                config.stopDistance(),
                effectiveRadius,
                config.maxNodes()
        ));
    }

    private boolean isResultUsable(
            SearchResult result,
            Vec3 playerPos,
            Vec3 targetPos,
            Sampler.WindowSnapshot window
    ) {
        // 结果必须来自当前窗口代际，且在时间/起点/目标偏移容差内才可使用。
        if (result == null || result.path() == null || result.epoch() != window.epoch()) {
            return false;
        }

        long age = System.nanoTime() - result.createdNanos();
        if (age < 0L || age > RESULT_MAX_AGE_NANOS) {
            return false;
        }

        return result.startPos().distanceToSqr(playerPos) <= RESULT_MAX_START_DISTANCE_SQR
                && result.targetPos().distanceToSqr(targetPos) <= RESULT_MAX_TARGET_DISTANCE_SQR;
    }

    private void workerLoop(long generation) {
        long lastSearchNanos = 0L;

        while (isWorkerRunning(generation)) {
            try {
                drainSampleBatches();

                // 只处理最新请求：旧请求会被后续客户端 tick 覆盖，不排队等待。
                SearchRequest request = this.pendingRequest.getAndSet(null);
                if (request == null) {
                    waitForWork(10L);
                    continue;
                }

                long now = System.nanoTime();
                long waitNanos = SEARCH_INTERVAL_NANOS - (now - lastSearchNanos);
                if (waitNanos > 0L) {
                    waitForWork(Math.max(1L, waitNanos / 1_000_000L));
                    if (!isWorkerRunning(generation)) {
                        return;
                    }
                    drainSampleBatches();
                    SearchRequest newest = this.pendingRequest.getAndSet(null);
                    if (newest != null) {
                        request = newest;
                    }
                }

                if (request.epoch() != this.workerEpoch || this.workerGrid == null) {
                    continue;
                }
                if (!isWorkerRunning(generation)) {
                    return;
                }

                SearchResult result = evaluate(request, this.workerGrid);
                if (result != null) {
                    this.latestResult.set(result);
                }
                lastSearchNanos = System.nanoTime();
            } catch (InterruptedException interrupted) {
                return;
            } catch (Throwable throwable) {
                Constants.LOGGER.warn("Error in ElytraCombat path worker loop", throwable);
                try {
                    waitForWork(100L);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    private boolean isWorkerRunning(long generation) {
        return this.running
                && this.workerGeneration == generation
                && !Thread.currentThread().isInterrupted();
    }

    private void waitForWork(long millis) throws InterruptedException {
        synchronized (this.workerMonitor) {
            this.workerMonitor.wait(millis);
        }
    }

    private void drainSampleBatches() {
        SampleBatch batch;
        while ((batch = this.sampleBatches.poll()) != null) {
            this.queuedSampleBatches.updateAndGet(value -> Math.max(0, value - 1));
            if (batch.epoch() < this.workerEpoch) {
                continue;
            }

            // 窗口尺寸或代际变化时直接重建缓存，而不是逐格搬运。
            if (this.workerGrid == null
                    || batch.epoch() > this.workerEpoch
                    || batch.size() != this.workerGrid.size()) {
                this.workerGrid = new VoxelCollisionCache(batch.size());
                this.workerEpoch = batch.epoch();
                this.latestResult.set(null);
            }

            this.workerGrid.setWindowOrigin(batch.origin(), batch.sequence());
            long[] positions = batch.positions();
            byte[] states = batch.states();
            for (int i = 0; i < positions.length; i++) {
                this.workerGrid.applySample(positions[i], states[i], batch.sequence());
            }
        }
    }

    private SearchResult evaluate(SearchRequest request, VoxelCollisionCache grid) {
        BlockPos start = BlockPos.containing(request.playerPos());
        if (!grid.isInWindow(start)) {
            return null;
        }

        if (request.playerPos().distanceTo(request.targetPos()) <= request.stopDistance()) {
            // 已进入停止距离时返回单点路径，消费端会把它视为悬停。
            return result(request, stopPath(request));
        }

        Vec3 limitedTarget = limitTarget(request.playerPos(), request.targetPos(), request.searchRadius());
        // A* 只能搜索当前体素窗口；超出窗口的目标先裁剪到窗口边界。
        BlockPos goal = grid.clampToWindow(BlockPos.containing(limitedTarget));
        ElytraMotionPredictor.PlayerCollisionProfile profile =
                new ElytraMotionPredictor.PlayerCollisionProfile(request.playerWidth(), request.playerHeight());
        AStarSearch search = new AStarSearch(
                grid,
                profile,
                start,
                goal,
                request.searchRadius(),
                request.maxNodes()
        );
        List<BlockPos> nodes = search.findPath();
        if (nodes.size() < 2) {
            // 搜索没有任何可走节点时才返回停止路径。
            return result(request, stopPath(request));
        }
        return result(request, toPathPlan(request.playerPos(), nodes));
    }

    private static PathPlan toPathPlan(Vec3 playerPos, List<BlockPos> nodes) {
        // 第一点用真实玩家位置，后续点使用 A* 节点中心，保持跨线程不可变。
        ArrayList<Vec3> points = new ArrayList<>(nodes.size());
        points.add(playerPos);
        for (int i = 1; i < nodes.size(); i++) {
            points.add(Vec3.atBottomCenterOf(nodes.get(i)));
        }
        return new PathPlan(points.get(1), List.copyOf(points));
    }

    private static PathPlan stopPath(SearchRequest request) {
        return new PathPlan(request.playerPos(), List.of(request.playerPos()));
    }

    private static Vec3 limitTarget(Vec3 from, Vec3 target, int searchRadius) {
        Vec3 delta = target.subtract(from);
        double distance = delta.length();
        if (distance <= searchRadius || distance < 0.001) {
            return target;
        }
        return from.add(delta.normalize().scale(searchRadius));
    }

    private static SearchResult result(SearchRequest request, PathPlan path) {
        return new SearchResult(
                request.epoch(),
                request.sequence(),
                System.nanoTime(),
                request.playerPos(),
                request.targetPos(),
                path
        );
    }

    private record SampleBatch(
            long epoch,
            long sequence,
            int size,
            BlockPos origin,
            long[] positions,
            byte[] states
    ) {
    }

    private record SearchRequest(
            long epoch,
            long sequence,
            int size,
            BlockPos origin,
            Vec3 playerPos,
            Vec3 targetPos,
            float playerWidth,
            float playerHeight,
            double stopDistance,
            int searchRadius,
            int maxNodes
    ) {
    }

    private record SearchResult(
            long epoch,
            long sequence,
            long createdNanos,
            Vec3 startPos,
            Vec3 targetPos,
            PathPlan path
    ) {
    }

    private final class Sampler {

        /** 采样器只允许在客户端线程访问；epoch 变化代表窗口或维度整体重建。 */
        private Level level;
        private int dataSize;
        private long epoch;
        private int originX;
        private int originY;
        private int originZ;
        private long sampleSequence;
        private int refreshTicks;
        private boolean invalidated = true;
        private boolean initialFillPending;
        private int initialFillCursor;
        private LongQueue queue = new LongQueue(16);
        private long[] scheduledPositions = new long[0];

        private void invalidate() {
            this.invalidated = true;
        }

        private WindowSnapshot prepare(LocalPlayer player, int requestedSize, Vec3 targetPos, Vec3 pathAhead) {
            // 维度、尺寸或外部 invalidate 变化时整窗重建。
            int size = normalizeDataSize(requestedSize);
            if (this.level != player.level() || this.dataSize != size || this.invalidated) {
                reset(player.level(), player.blockPosition(), size);
            }

            updateWindow(player.blockPosition(), size);
            enqueueInitialBatch(size);

            // 周期性刷新玩家、目标和当前路径前方的局部区域，优先保证近处体素新鲜。
            if (this.refreshTicks <= 0) {
                enqueueNeighborhood(player.blockPosition(), LOCAL_SAMPLE_RADIUS);
                enqueueNeighborhood(BlockPos.containing(targetPos), LOCAL_SAMPLE_RADIUS);
                if (pathAhead != null) {
                    enqueueNeighborhood(BlockPos.containing(pathAhead), LOCAL_SAMPLE_RADIUS);
                }
                this.refreshTicks = REFRESH_INTERVAL_TICKS;
            } else {
                this.refreshTicks--;
            }

            drain(player);
            return new WindowSnapshot(
                    this.epoch,
                    this.sampleSequence,
                    size,
                    new BlockPos(this.originX, this.originY, this.originZ)
            );
        }

        private void reset(Level level, BlockPos playerPos, int size) {
            // epoch 自增后，工作线程会丢弃所有旧窗口样本和旧路径结果。
            this.level = level;
            this.dataSize = size;
            this.epoch++;
            this.sampleSequence = 0L;
            this.refreshTicks = 0;
            this.invalidated = false;
            this.initialFillPending = true;
            this.initialFillCursor = 0;
            this.queue = new LongQueue(Math.min(16_384, Math.max(64, size * size)));
            this.scheduledPositions = new long[size * size * size];
            java.util.Arrays.fill(this.scheduledPositions, Long.MIN_VALUE);

            int desiredX = alignedWindowOrigin(playerPos.getX(), size);
            int desiredY = alignedWindowOrigin(playerPos.getY(), size);
            int desiredZ = alignedWindowOrigin(playerPos.getZ(), size);
            this.originX = desiredX;
            this.originY = desiredY;
            this.originZ = desiredZ;

            enqueueNeighborhood(playerPos, LOCAL_SAMPLE_RADIUS);
        }

        private void updateWindow(BlockPos playerPos, int size) {
            int newX = alignedWindowOrigin(playerPos.getX(), size);
            int newY = alignedWindowOrigin(playerPos.getY(), size);
            int newZ = alignedWindowOrigin(playerPos.getZ(), size);
            if (newX == this.originX && newY == this.originY && newZ == this.originZ) {
                return;
            }

            int oldOriginX = this.originX;
            int oldOriginY = this.originY;
            int oldOriginZ = this.originZ;
            int oldMaxX = this.originX + size - 1;
            int oldMaxY = this.originY + size - 1;
            int oldMaxZ = this.originZ + size - 1;
            int newMaxX = newX + size - 1;
            int newMaxY = newY + size - 1;
            int newMaxZ = newZ + size - 1;

            int deltaX = Math.abs(newX - this.originX);
            int deltaY = Math.abs(newY - this.originY);
            int deltaZ = Math.abs(newZ - this.originZ);
            int exposedUpperBound = size * size * (deltaX + deltaY + deltaZ);
            // 小范围滚动只给新增切片排样；位移过大或初始填充时直接重建队列。
            boolean rescheduleWindow = this.initialFillPending
                    || deltaX > size
                    || deltaY > size
                    || deltaZ > size
                    || exposedUpperBound > MAX_SAMPLES_PER_TICK * 4;
            if (rescheduleWindow) {
                this.queue = new LongQueue(Math.min(16_384, Math.max(64, size * size)));
                this.scheduledPositions = new long[size * size * size];
                java.util.Arrays.fill(this.scheduledPositions, Long.MIN_VALUE);
                this.originX = newX;
                this.originY = newY;
                this.originZ = newZ;
                this.initialFillPending = true;
                this.initialFillCursor = 0;
                enqueueNeighborhood(playerPos, LOCAL_SAMPLE_RADIUS);
            } else {
                this.originX = newX;
                this.originY = newY;
                this.originZ = newZ;
                if (newX > oldOriginX) {
                    enqueueRegion(oldMaxX + 1, this.originY, this.originZ, newMaxX, newMaxY, newMaxZ);
                } else if (newX < oldOriginX) {
                    enqueueRegion(newX, this.originY, this.originZ, oldOriginX - 1, newMaxY, newMaxZ);
                }
                if (newY > oldOriginY) {
                    enqueueRegion(this.originX, oldMaxY + 1, this.originZ, newMaxX, newMaxY, newMaxZ);
                } else if (newY < oldOriginY) {
                    enqueueRegion(this.originX, newY, this.originZ, newMaxX, oldOriginY - 1, newMaxZ);
                }
                if (newZ > oldOriginZ) {
                    enqueueRegion(this.originX, this.originY, oldMaxZ + 1, newMaxX, newMaxY, newMaxZ);
                } else if (newZ < oldOriginZ) {
                    enqueueRegion(this.originX, this.originY, newZ, newMaxX, newMaxY, oldOriginZ - 1);
                }
            }
        }

        private void enqueueInitialBatch(int size) {
            if (!this.initialFillPending) {
                return;
            }

            int volume = size * size * size;
            int plane = size * size;
            // 每 tick 最多排入 4096 个初始体素，避免开启模块时卡顿。
            int added = 0;
            while (this.initialFillCursor < volume
                    && added < MAX_SAMPLES_PER_TICK
                    && this.queue.size() < MAX_SAMPLES_PER_TICK * 2) {
                int cursor = this.initialFillCursor++;
                int x = cursor / plane;
                int remainder = cursor % plane;
                int y = remainder / size;
                int z = remainder % size;
                enqueue(this.originX + x, this.originY + y, this.originZ + z);
                added++;
            }

            if (this.initialFillCursor >= volume) {
                this.initialFillPending = false;
            }
        }

        private void enqueueNeighborhood(BlockPos center, int radius) {
            int minX = Math.max(this.originX, center.getX() - radius);
            int minY = Math.max(this.originY, center.getY() - radius);
            int minZ = Math.max(this.originZ, center.getZ() - radius);
            int maxX = Math.min(this.originX + this.dataSize - 1, center.getX() + radius);
            int maxY = Math.min(this.originY + this.dataSize - 1, center.getY() + radius);
            int maxZ = Math.min(this.originZ + this.dataSize - 1, center.getZ() + radius);
            enqueueRegion(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private void enqueueRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        enqueue(x, y, z);
                    }
                }
            }
        }

        private void enqueue(int x, int y, int z) {
            if (!isInWindow(x, y, z)) {
                return;
            }

            long packedPos = BlockPos.asLong(x, y, z);
            int index = index(x, y, z);
            if (this.scheduledPositions[index] == packedPos) {
                return;
            }

            this.scheduledPositions[index] = packedPos;
            this.queue.enqueue(packedPos);
        }

        private void drain(LocalPlayer player) {
            if (queuedSampleBatches.get() >= MAX_QUEUED_SAMPLE_BATCHES) {
                return;
            }

            long[] positions = new long[MAX_SAMPLES_PER_TICK];
            byte[] states = new byte[MAX_SAMPLES_PER_TICK];
            // CollisionContext 让方块碰撞形状按当前玩家状态计算（例如潜行/飞行姿态）。
            CollisionContext context = CollisionContext.of(player);
            int count = 0;

            while (count < MAX_SAMPLES_PER_TICK && !this.queue.isEmpty()) {
                long packedPos = this.queue.dequeue();
                int x = BlockPos.getX(packedPos);
                int y = BlockPos.getY(packedPos);
                int z = BlockPos.getZ(packedPos);
                int index = index(x, y, z);
                if (this.scheduledPositions[index] == packedPos) {
                    this.scheduledPositions[index] = Long.MIN_VALUE;
                }

                if (!isInWindow(x, y, z)) {
                    continue;
                }

                positions[count] = packedPos;
                states[count] = sampleState(x, y, z, context);
                count++;
            }

            if (count == 0) {
                return;
            }

            long sequence = ++this.sampleSequence;
            sampleBatches.offer(new SampleBatch(
                    this.epoch,
                    sequence,
                    this.dataSize,
                    new BlockPos(this.originX, this.originY, this.originZ),
                    java.util.Arrays.copyOf(positions, count),
                    java.util.Arrays.copyOf(states, count)
            ));
            queuedSampleBatches.incrementAndGet();
        }

        @SuppressWarnings("deprecation")
        private byte sampleState(int x, int y, int z, CollisionContext context) {
            // 未加载区块记 UNKNOWN，A* 会把它当作不可通行；世界边界外直接记 BLOCKED。
            BlockPos pos = new BlockPos(x, y, z);
            if (!this.level.isInWorldBounds(pos)) {
                return VoxelCollisionCache.BLOCKED;
            }
            if (!this.level.hasChunkAt(pos)) {
                return VoxelCollisionCache.UNKNOWN;
            }

            BlockState state = this.level.getBlockState(pos);
            return state.getCollisionShape(this.level, pos, context).isEmpty()
                    ? VoxelCollisionCache.FREE
                    : VoxelCollisionCache.BLOCKED;
        }

        private boolean isInWindow(int x, int y, int z) {
            return x >= this.originX && x < this.originX + this.dataSize
                    && y >= this.originY && y < this.originY + this.dataSize
                    && z >= this.originZ && z < this.originZ + this.dataSize;
        }

        private int index(int x, int y, int z) {
            int wrappedX = Math.floorMod(x, this.dataSize);
            int wrappedY = Math.floorMod(y, this.dataSize);
            int wrappedZ = Math.floorMod(z, this.dataSize);
            return (wrappedX * this.dataSize + wrappedY) * this.dataSize + wrappedZ;
        }

        private static int alignedWindowOrigin(int center, int size) {
            // 窗口原点对齐到 5 的倍数，让 5³ 粗粒度统计保持稳定。
            int origin = center - size / 2;
            return Math.floorDiv(origin, DATA_SIZE_ALIGNMENT) * DATA_SIZE_ALIGNMENT;
        }

        private record WindowSnapshot(long epoch, long sequence, int size, BlockPos origin) {
        }
    }

    private static final class LongQueue {

        /** 为方块采样设计的原始 long 环形队列，避免装箱和递归队列开销。 */
        private long[] values;
        private int head;
        private int size;

        private LongQueue(int initialCapacity) {
            this.values = new long[Math.max(16, initialCapacity)];
        }

        private void enqueue(long value) {
            if (this.size == this.values.length) {
                grow();
            }
            int index = (this.head + this.size) % this.values.length;
            this.values[index] = value;
            this.size++;
        }

        private long dequeue() {
            if (this.size == 0) {
                throw new IllegalStateException("Queue is empty");
            }
            long value = this.values[this.head];
            this.head = (this.head + 1) % this.values.length;
            this.size--;
            return value;
        }

        private boolean isEmpty() {
            return this.size == 0;
        }

        private int size() {
            return this.size;
        }

        private void grow() {
            long[] grown = new long[this.values.length * 2];
            for (int i = 0; i < this.size; i++) {
                grown[i] = this.values[(this.head + i) % this.values.length];
            }
            this.values = grown;
            this.head = 0;
        }
    }
}
