package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ElytraCombat 的后台单层 A* 路径服务。
 *
 * <p>客户端线程只负责有界采样，碰撞层级、搜索、路径压缩和飞行校验均在专用工作线程执行。</p>
 */
public final class ElytraPathNavigator {

    private static final int DEFAULT_DATA_SIZE = 50;
    private static final int MIN_DATA_SIZE = 25;
    private static final int MAX_DATA_SIZE = 100;
    private static final int DATA_SIZE_ALIGNMENT = 5;
    private static final int MAX_SAMPLES_PER_TICK = 4096;
    private static final int MAX_QUEUED_SAMPLE_BATCHES = 64;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int LOCAL_SAMPLE_RADIUS = 7;
    private static final long SEARCH_INTERVAL_NANOS = 50_000_000L;
    private static final long RESULT_MAX_AGE_NANOS = 150_000_000L;
    private static final double RESULT_MAX_START_DISTANCE_SQR = 25.0;
    private static final double RESULT_MAX_TARGET_DISTANCE_SQR = 64.0;
    private static final double AVOIDANCE_CLEARANCE_WIDTH = 1.2;
    private static final double AVOIDANCE_CLEARANCE_HEIGHT = 0.8;
    private static final List<Vec3> AVOIDANCE_DIRECTIONS = createAvoidanceDirections();
    private final String workerThreadName;
    private final AtomicInteger requestedDataSize = new AtomicInteger(DEFAULT_DATA_SIZE);
    private final ConcurrentLinkedQueue<SampleBatch> sampleBatches = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedSampleBatches = new AtomicInteger();
    private final AtomicReference<SearchRequest> pendingRequest = new AtomicReference<>();
    private final AtomicReference<SearchResult> latestResult = new AtomicReference<>();
    private final Object workerMonitor = new Object();
    private final Sampler sampler = new Sampler();

    private volatile boolean running;
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
            return result.path();
        }

        return new PathPlan(player.position(), List.of(player.position()));
    }

    public void setDataSize(int size) {
        int normalized = normalizeDataSize(size);
        if (this.requestedDataSize.getAndSet(normalized) != normalized) {
            this.pendingRequest.set(null);
            this.latestResult.set(null);
            this.sampler.invalidate();
        }
    }

    public void stop() {
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
        int effectiveRadius = Math.max(6, Math.min(config.searchRadius(), window.size() / 2 - 1));
        double gravity = player.getGravity();
        if (player.getDeltaMovement().y <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }

        this.pendingRequest.set(new SearchRequest(
                window.epoch(),
                window.sequence(),
                window.size(),
                window.origin(),
                player.position(),
                targetPos,
                player.getDeltaMovement(),
                gravity,
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

        ElytraMotionPredictor.PlayerCollisionProfile profile =
                new ElytraMotionPredictor.PlayerCollisionProfile(request.playerWidth(), request.playerHeight());
        Set<Long> noExtraBlocked = Set.of();

        if (request.playerPos().distanceTo(request.targetPos()) <= request.stopDistance()) {
            PathPlan stopped = new PathPlan(request.playerPos(), List.of(request.playerPos()));
            return new SearchResult(
                    request.epoch(),
                    request.sequence(),
                    System.nanoTime(),
                    request.playerPos(),
                    request.targetPos(),
                    stopped
            );
        }

        Vec3 limitedTarget = limitTarget(request.playerPos(), request.targetPos(), request.searchRadius());
        BlockPos goal = grid.clampToWindow(BlockPos.containing(limitedTarget));
        if (!goal.equals(BlockPos.containing(limitedTarget))) {
            limitedTarget = Vec3.atBottomCenterOf(goal);
        }

        Attempt attempt = null;
        if (ElytraMotionPredictor.isSweepClear(
                grid,
                profile,
                request.playerPos(),
                limitedTarget,
                noExtraBlocked
        )) {
            PathPlan directPath = new PathPlan(
                    limitedTarget,
                    List.of(request.playerPos(), limitedTarget)
            );
            attempt = new Attempt(
                    directPath,
                    ElytraMotionPredictor.validatePath(
                            grid,
                            profile,
                            request.playerPos(),
                            request.velocity(),
                            directPath.points(),
                            request.gravity()
                    )
            );
            if (attempt.validation().safe()) {
                return result(request, directPath);
            }
        }

        AStarSearch search = new AStarSearch(
                grid,
                profile,
                start,
                goal,
                request.searchRadius(),
                request.maxNodes(),
                request.stopDistance(),
                noExtraBlocked
        );
        PathPlan searched = createPath(
                request.playerPos(),
                search.findPath(),
                grid,
                profile,
                noExtraBlocked
        );
        if (searched == null) {
            PathPlan directPrefix = attempt != null ? safePrefix(attempt) : null;
            if (directPrefix != null) {
                return result(request, directPrefix);
            }
            PathPlan avoidance = findAvoidancePath(request, grid, profile, limitedTarget);
            return result(request, avoidance != null ? avoidance : stopPath(request));
        }

        ElytraMotionPredictor.ValidationResult validation = ElytraMotionPredictor.validatePath(
                grid,
                profile,
                request.playerPos(),
                request.velocity(),
                searched.points(),
                request.gravity()
        );
        if (validation.safe()) {
            return result(request, searched);
        }

        Attempt firstAttempt = new Attempt(searched, validation);
        attempt = firstAttempt;

        long blockingBlock = validation.blockingBlock();
        if (blockingBlock != VoxelCollisionCache.NO_BLOCK
                && blockingBlock != VoxelCollisionCache.OUTSIDE_WINDOW) {
            Set<Long> extraBlocked = new HashSet<>();
            extraBlocked.add(blockingBlock);

            AStarSearch retrySearch = new AStarSearch(
                    grid,
                    profile,
                    start,
                    goal,
                    request.searchRadius(),
                    request.maxNodes(),
                    request.stopDistance(),
                    extraBlocked
            );
            PathPlan retryPath = createPath(
                    request.playerPos(),
                    retrySearch.findPath(),
                    grid,
                    profile,
                    extraBlocked
            );
            if (retryPath != null) {
                ElytraMotionPredictor.ValidationResult retryValidation =
                        ElytraMotionPredictor.validatePath(
                                grid,
                                profile,
                                request.playerPos(),
                                request.velocity(),
                                retryPath.points(),
                                request.gravity()
                        );
                if (retryValidation.safe()) {
                    return result(request, retryPath);
                }
                Attempt retryAttempt = new Attempt(retryPath, retryValidation);
                PathPlan retryPrefix = safePrefix(retryAttempt);
                if (retryPrefix != null) {
                    return result(request, retryPrefix);
                }
            }
        }

        PathPlan safePrefix = safePrefix(attempt);
        if (safePrefix != null) {
            return result(request, safePrefix);
        }
        PathPlan avoidance = findAvoidancePath(request, grid, profile, limitedTarget);
        return result(request, avoidance != null ? avoidance : stopPath(request));
    }

    private static PathPlan safePrefix(Attempt attempt) {
        int lastSafeIndex = attempt.validation().lastSafePathIndex();
        if (lastSafeIndex < 1 || lastSafeIndex >= attempt.path().points().size()) {
            return null;
        }

        List<Vec3> points = List.copyOf(attempt.path().points().subList(0, lastSafeIndex + 1));
        return new PathPlan(points.get(1), points);
    }

    private static PathPlan stopPath(SearchRequest request) {
        return new PathPlan(request.playerPos(), List.of(request.playerPos()));
    }

    private static PathPlan createPath(
            Vec3 playerPos,
            List<BlockPos> nodes,
            VoxelCollisionCache grid,
            ElytraMotionPredictor.PlayerCollisionProfile profile,
            Set<Long> extraBlocked
    ) {
        if (nodes.size() < 2) {
            return null;
        }

        ArrayList<Vec3> rawPoints = new ArrayList<>(nodes.size());
        rawPoints.add(playerPos);
        for (int i = 1; i < nodes.size(); i++) {
            rawPoints.add(Vec3.atBottomCenterOf(nodes.get(i)));
        }

        ArrayList<Vec3> points = new ArrayList<>();
        points.add(playerPos);
        ElytraMotionPredictor.PlayerCollisionProfile paddedProfile =
                new ElytraMotionPredictor.PlayerCollisionProfile(
                        profile.width() + AVOIDANCE_CLEARANCE_WIDTH,
                        profile.height() + AVOIDANCE_CLEARANCE_HEIGHT
                );
        int anchor = 0;
        while (anchor < rawPoints.size() - 1) {
            int next = findNextPathPoint(
                    rawPoints,
                    anchor,
                    grid,
                    paddedProfile,
                    extraBlocked
            );
            if (next == anchor) {
                next = findNextPathPoint(rawPoints, anchor, grid, profile, extraBlocked);
            }
            if (next == anchor) {
                return null;
            }
            points.add(rawPoints.get(next));
            anchor = next;
        }

        return new PathPlan(points.get(1), List.copyOf(points));
    }

    private static int findNextPathPoint(
            List<Vec3> points,
            int anchor,
            VoxelCollisionCache grid,
            ElytraMotionPredictor.PlayerCollisionProfile profile,
            Set<Long> extraBlocked
    ) {
        int next = points.size() - 1;
        while (next > anchor
                && !ElytraMotionPredictor.isSweepClear(
                grid,
                profile,
                points.get(anchor),
                points.get(next),
                extraBlocked
        )) {
            next--;
        }
        return next;
    }

    private static PathPlan findAvoidancePath(
            SearchRequest request,
            VoxelCollisionCache grid,
            ElytraMotionPredictor.PlayerCollisionProfile profile,
            Vec3 goalPoint
    ) {
        Vec3 playerPos = request.playerPos();
        Vec3 toGoal = goalPoint.subtract(playerPos);
        double goalDistance = toGoal.length();
        if (goalDistance < 0.001) {
            return null;
        }

        Vec3 goalDirection = toGoal.scale(1.0 / goalDistance);
        Vec3 velocityDirection = request.velocity().lengthSqr() > 0.000001
                ? request.velocity().normalize()
                : goalDirection;
        int primaryDistance = Math.clamp((int) Math.round(request.velocity().length() * 4.0 + 3.0), 3, 8);
        int[] distances = {primaryDistance, Math.max(2, primaryDistance / 2), 2};
        ElytraMotionPredictor.PlayerCollisionProfile paddedProfile =
                new ElytraMotionPredictor.PlayerCollisionProfile(
                        profile.width() + AVOIDANCE_CLEARANCE_WIDTH,
                        profile.height() + AVOIDANCE_CLEARANCE_HEIGHT
                );

        PathPlan bestPaddedPath = null;
        double bestPaddedScore = Double.NEGATIVE_INFINITY;
        PathPlan bestNormalPath = null;
        double bestNormalScore = Double.NEGATIVE_INFINITY;
        Set<Long> noExtraBlocked = Set.of();
        for (Vec3 direction : AVOIDANCE_DIRECTIONS) {
            double goalAlignment = direction.dot(goalDirection);
            double velocityAlignment = direction.dot(velocityDirection);
            for (int distance : distances) {
                if (distance > request.searchRadius()) {
                    continue;
                }

                Vec3 candidate = playerPos.add(direction.scale(distance));
                if (!grid.isInWindow(BlockPos.containing(candidate))
                        || !ElytraMotionPredictor.isSweepClear(
                        grid,
                        profile,
                        playerPos,
                        candidate,
                        noExtraBlocked
                )) {
                    continue;
                }

                PathPlan candidatePath = new PathPlan(
                        candidate,
                        List.of(playerPos, candidate)
                );
                if (!ElytraMotionPredictor.validatePath(
                        grid,
                        profile,
                        playerPos,
                        request.velocity(),
                        candidatePath.points(),
                        request.gravity()
                ).safe()) {
                    continue;
                }

                boolean paddedClear = ElytraMotionPredictor.isSweepClear(
                        grid,
                        paddedProfile,
                        playerPos,
                        candidate,
                        noExtraBlocked
                );
                double progress = goalDistance - candidate.distanceTo(goalPoint);
                double score = progress
                        + goalAlignment * 2.0
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

    private static List<Vec3> createAvoidanceDirections() {
        ArrayList<Vec3> directions = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    directions.add(new Vec3(x, y, z).normalize());
                }
            }
        }
        return List.copyOf(directions);
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
            Vec3 velocity,
            double gravity,
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

    private record Attempt(PathPlan path, ElytraMotionPredictor.ValidationResult validation) {
    }

    private final class Sampler {

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
            int size = normalizeDataSize(requestedSize);
            if (this.level != player.level() || this.dataSize != size || this.invalidated) {
                reset(player.level(), player.blockPosition(), size);
            }

            updateWindow(player.blockPosition(), size);
            enqueueInitialBatch(size);

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
            int origin = center - size / 2;
            return Math.floorDiv(origin, DATA_SIZE_ALIGNMENT) * DATA_SIZE_ALIGNMENT;
        }

        private record WindowSnapshot(long epoch, long sequence, int size, BlockPos origin) {
        }
    }

    private static final class LongQueue {

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
