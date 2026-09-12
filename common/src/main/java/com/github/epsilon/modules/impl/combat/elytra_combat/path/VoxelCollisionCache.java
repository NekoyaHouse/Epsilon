package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import net.minecraft.core.BlockPos;

/**
 * 工作线程独占的分层体素缓存。
 *
 * <p>细体素使用世界坐标取模寻址；槽位同时保存坐标标签，因此滚动窗口复用槽位时，
 * 旧坐标不会被当成新坐标读取。</p>
 */
public final class VoxelCollisionCache {

    /** UNKNOWN 与 BLOCKED 都视为不可通行，只有明确采样的 FREE 允许通过。 */
    public static final byte UNKNOWN = 0;
    public static final byte FREE = 1;
    public static final byte BLOCKED = 2;

    /** 查询结果哨兵值，避免额外分配可选对象。 */
    public static final long NO_BLOCK = Long.MIN_VALUE;
    public static final long OUTSIDE_WINDOW = Long.MAX_VALUE;

    private static final int COARSE_SIZE = 5;
    private static final int COARSE_VOLUME = COARSE_SIZE * COARSE_SIZE * COARSE_SIZE;
    private final int size;
    private final int coarseSide;
    private final int mediumSide;
    private final int volume;

    /** 细体素按世界坐标取模映射；positions 保存坐标标签，sampleVersions 防止旧样本覆盖新样本。 */
    private final byte[] states;
    private final long[] positions;
    private final long[] sampleVersions;

    /** 5³ 粗粒度统计，用于快速判断大块区域是否全 FREE。 */
    private final short[] coarseValid;
    private final short[] coarseBlocked;
    private final long[] coarsePositions;

    /** 每轴 2+2+1 的中间粒度统计，用于细粒度回退查询。 */
    private final short[] mediumValid;
    private final short[] mediumBlocked;
    private final long[] mediumPositions;

    private int originX;
    private int originY;
    private int originZ;
    private long windowSequence = Long.MIN_VALUE;

    public VoxelCollisionCache(int size) {
        if (size < COARSE_SIZE || size % COARSE_SIZE != 0) {
            throw new IllegalArgumentException("Voxel grid size must be a positive multiple of 5");
        }

        this.size = size;
        this.coarseSide = size / COARSE_SIZE;
        this.mediumSide = this.coarseSide * 3;
        this.volume = size * size * size;

        this.states = new byte[this.volume];
        this.positions = new long[this.volume];
        this.sampleVersions = new long[this.volume];

        int coarseVolume = this.coarseSide * this.coarseSide * this.coarseSide;
        this.coarseValid = new short[coarseVolume];
        this.coarseBlocked = new short[coarseVolume];
        this.coarsePositions = new long[coarseVolume];

        int mediumVolume = this.mediumSide * this.mediumSide * this.mediumSide;
        this.mediumValid = new short[mediumVolume];
        this.mediumBlocked = new short[mediumVolume];
        this.mediumPositions = new long[mediumVolume];
    }

    public int size() {
        return this.size;
    }

    public void setWindowOrigin(BlockPos origin, long sequence) {
        // 只接受更新的窗口序号，防止迟到的旧批次把 origin 回退。
        if (sequence < this.windowSequence) {
            return;
        }

        this.originX = origin.getX();
        this.originY = origin.getY();
        this.originZ = origin.getZ();
        this.windowSequence = sequence;
    }

    public boolean isInWindow(BlockPos pos) {
        return isInWindow(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isInWindow(int x, int y, int z) {
        return x >= this.originX && x < this.originX + this.size
                && y >= this.originY && y < this.originY + this.size
                && z >= this.originZ && z < this.originZ + this.size;
    }

    public BlockPos clampToWindow(BlockPos pos) {
        int x = Math.clamp(pos.getX(), this.originX, this.originX + this.size - 1);
        int y = Math.clamp(pos.getY(), this.originY, this.originY + this.size - 1);
        int z = Math.clamp(pos.getZ(), this.originZ, this.originZ + this.size - 1);
        return x == pos.getX() && y == pos.getY() && z == pos.getZ()
                ? pos
                : new BlockPos(x, y, z);
    }

    public void applySample(long packedPos, byte state, long sequence) {
        int x = BlockPos.getX(packedPos);
        int y = BlockPos.getY(packedPos);
        int z = BlockPos.getZ(packedPos);
        if (!isInWindow(x, y, z)) {
            return;
        }

        int index = index(x, y, z, this.size);
        // 同一个环形槽可能被新窗口复用，低序号样本不能覆盖高序号样本。
        if (sequence < this.sampleVersions[index]) {
            return;
        }

        long previousPos = this.positions[index];
        byte previousState = this.states[index];
        if (previousPos == packedPos && previousState == state) {
            this.sampleVersions[index] = sequence;
            return;
        }

        if (previousState != UNKNOWN) {
            // 状态变化时先撤销旧值对粗/中粒度计数的贡献。
            updateAggregates(previousPos, previousState, -1);
        }

        this.positions[index] = packedPos;
        this.states[index] = state;
        this.sampleVersions[index] = sequence;

        if (state != UNKNOWN) {
            updateAggregates(packedPos, state, 1);
        }
    }

    /**
     * 返回查询体积内第一个非 FREE 体素；全部可通过时返回 {@link #NO_BLOCK}。
     */
    public long findBlockedInVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return NO_BLOCK;
        }

        if (minX < this.originX || minY < this.originY || minZ < this.originZ
                || maxX > this.originX + this.size - 1
                || maxY > this.originY + this.size - 1
                || maxZ > this.originZ + this.size - 1) {
            return OUTSIDE_WINDOW;
        }

        if (isCoarseVolumeFree(minX, minY, minZ, maxX, maxY, maxZ)) {
            // 整块 5³ 已完整采样且无阻塞时可直接判定通过。
            return NO_BLOCK;
        }
        if (isMediumVolumeFree(minX, minY, minZ, maxX, maxY, maxZ)) {
            return NO_BLOCK;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isFree(x, y, z)) {
                        return BlockPos.asLong(x, y, z);
                    }
                }
            }
        }
        return NO_BLOCK;
    }

    private boolean isCoarseVolumeFree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int minCellX = Math.floorDiv(minX, COARSE_SIZE);
        int minCellY = Math.floorDiv(minY, COARSE_SIZE);
        int minCellZ = Math.floorDiv(minZ, COARSE_SIZE);
        int maxCellX = Math.floorDiv(maxX, COARSE_SIZE);
        int maxCellY = Math.floorDiv(maxY, COARSE_SIZE);
        int maxCellZ = Math.floorDiv(maxZ, COARSE_SIZE);

        for (int x = minCellX; x <= maxCellX; x++) {
            for (int y = minCellY; y <= maxCellY; y++) {
                for (int z = minCellZ; z <= maxCellZ; z++) {
                    int index = index(x, y, z, this.coarseSide);
                    long packedCell = BlockPos.asLong(x, y, z);
                    if (this.coarsePositions[index] != packedCell
                            || this.coarseValid[index] != COARSE_VOLUME
                            || this.coarseBlocked[index] != 0) {
                        // 坐标标签不匹配、未采满或存在 BLOCKED，都不能走快速路径。
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isMediumVolumeFree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int minCellX = mediumCoordinate(minX);
        int minCellY = mediumCoordinate(minY);
        int minCellZ = mediumCoordinate(minZ);
        int maxCellX = mediumCoordinate(maxX);
        int maxCellY = mediumCoordinate(maxY);
        int maxCellZ = mediumCoordinate(maxZ);

        for (int x = minCellX; x <= maxCellX; x++) {
            for (int y = minCellY; y <= maxCellY; y++) {
                for (int z = minCellZ; z <= maxCellZ; z++) {
                    int index = index(x, y, z, this.mediumSide);
                    long packedCell = BlockPos.asLong(x, y, z);
                    if (this.mediumPositions[index] != packedCell
                            || this.mediumValid[index] != mediumCellVolume(x, y, z)
                            || this.mediumBlocked[index] != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isFree(int x, int y, int z) {
        int index = index(x, y, z, this.size);
        return this.positions[index] == BlockPos.asLong(x, y, z)
                && this.states[index] == FREE;
    }

    private void updateAggregates(long packedPos, byte state, int delta) {
        int x = BlockPos.getX(packedPos);
        int y = BlockPos.getY(packedPos);
        int z = BlockPos.getZ(packedPos);
        // 粗粒度记录阻塞数，中粒度用于缩小回退范围。
        int blockedDelta = state == BLOCKED ? delta : 0;

        updateCoarseAggregate(x, y, z, delta, blockedDelta);
        updateMediumAggregate(x, y, z, delta, blockedDelta);
    }

    private void updateCoarseAggregate(int x, int y, int z, int delta, int blockedDelta) {
        int coarseX = Math.floorDiv(x, COARSE_SIZE);
        int coarseY = Math.floorDiv(y, COARSE_SIZE);
        int coarseZ = Math.floorDiv(z, COARSE_SIZE);
        int coarseIndex = index(coarseX, coarseY, coarseZ, this.coarseSide);
        long packedCoarse = BlockPos.asLong(coarseX, coarseY, coarseZ);
        if (this.coarsePositions[coarseIndex] != packedCoarse) {
            // 旧窗口的桶可能已被同模的新桶替换，此时不能继续递减旧计数。
            if (delta < 0) {
                return;
            }
            this.coarsePositions[coarseIndex] = packedCoarse;
            this.coarseValid[coarseIndex] = 0;
            this.coarseBlocked[coarseIndex] = 0;
        }
        this.coarseValid[coarseIndex] = (short) (this.coarseValid[coarseIndex] + delta);
        this.coarseBlocked[coarseIndex] = (short) (this.coarseBlocked[coarseIndex] + blockedDelta);
    }

    private void updateMediumAggregate(int x, int y, int z, int delta, int blockedDelta) {
        int mediumX = mediumCoordinate(x);
        int mediumY = mediumCoordinate(y);
        int mediumZ = mediumCoordinate(z);
        int mediumIndex = index(mediumX, mediumY, mediumZ, this.mediumSide);
        long packedMedium = BlockPos.asLong(mediumX, mediumY, mediumZ);
        if (this.mediumPositions[mediumIndex] != packedMedium) {
            if (delta < 0) {
                return;
            }
            this.mediumPositions[mediumIndex] = packedMedium;
            this.mediumValid[mediumIndex] = 0;
            this.mediumBlocked[mediumIndex] = 0;
        }
        this.mediumValid[mediumIndex] = (short) (this.mediumValid[mediumIndex] + delta);
        this.mediumBlocked[mediumIndex] = (short) (this.mediumBlocked[mediumIndex] + blockedDelta);
    }

    private static int mediumCoordinate(int coordinate) {
        // 5 格固定拆成 2/2/1 三段，避免 5 不能被 2 整除的歧义。
        int local = Math.floorMod(coordinate, COARSE_SIZE);
        int child = local < 2 ? 0 : local < 4 ? 1 : 2;
        return Math.floorDiv(coordinate, COARSE_SIZE) * 3 + child;
    }

    private static int mediumCellVolume(int x, int y, int z) {
        return mediumChildSize(x) * mediumChildSize(y) * mediumChildSize(z);
    }

    private static int mediumChildSize(int coordinate) {
        return Math.floorMod(coordinate, 3) < 2 ? 2 : 1;
    }

    private static int index(int x, int y, int z, int side) {
        int wrappedX = Math.floorMod(x, side);
        int wrappedY = Math.floorMod(y, side);
        int wrappedZ = Math.floorMod(z, side);
        return (wrappedX * side + wrappedY) * side + wrappedZ;
    }
}
