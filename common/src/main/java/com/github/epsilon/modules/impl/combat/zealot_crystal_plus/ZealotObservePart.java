package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotDamage.DamageReductionData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotDamage.ResistantBlockCache;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.CrystalSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SelfSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SettingsSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.TargetSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.RangeMode;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModulePart;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OBSERVE 阶段：把世界状态固化为不可变快照。
 *
 * <p>本 Part 禁止产生任何外部副作用——不放置、不破坏、不切换物品栏、不写入最终旋转。它只读取
 * Minecraft 状态、维护过期时间戳，并把 {@link SnapshotData} 交给 worker 线程。
 */
final class ZealotObservePart extends ZealotPartBase implements ModulePart {

    private static final int EXPLOSION_SAMPLE_SIZE = 8;

    ZealotObservePart(ZealotCrystalPlus module) {
        super(module);
    }

    @Override
    public void declare(ModuleDeclaration declaration) {
        // 节点统一由 ZealotSettingsPart 声明，保证节点图集中可见。
    }

    void observeSnapshot(PlayerTickEvent.Pre event) {
        state().updateTimeouts();
        updateExplosionSamples();
        captureSnapshotIfNeeded();
    }

    /**
     * 网络包观察：水晶生成与爆炸音效只更新计时标记；真正的水晶动作交给 COMMIT 阶段。
     */
    void observePacket(PacketEvent.Receive event) {
        if (nullCheck() || !module.isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundAddEntityPacket addPacket && addPacket.getType() == EntityTypes.END_CRYSTAL) {
            module.commitPart.observeCrystalSpawn(addPacket);
        } else if (packet instanceof ClientboundSoundPacket soundPacket) {
            module.commitPart.observeExplosion(soundPacket);
        }
    }

    /**
     * 按 {@code Update Delay} 节流快照；跳过节流时复用上一份快照，避免 worker 处理重复输入。
     */
    private SnapshotData captureSnapshotIfNeeded() {
        if (!state().snapshotTimer.passedMillise(module.updateDelay.getValue()) && state().latestSnapshot != null) {
            return state().latestSnapshot;
        }

        state().snapshotTimer.reset();
        SnapshotData snapshot = captureSnapshot();
        state().latestSnapshot = snapshot;
        state().pendingSnapshot = snapshot;
        module.signalWorker();
        return snapshot;
    }

    private SnapshotData captureSnapshot() {
        Player player = mc().player;
        if (player == null || mc().level == null) {
            return SnapshotData.EMPTY;
        }

        SettingsSnapshot settings = module.captureSettings();
        SelfSnapshot self = captureSelfSnapshot(player, settings.armorMode());
        List<TargetSnapshot> targets = captureTargets(settings.armorMode());
        if (targets.isEmpty()) {
            return new SnapshotData(System.nanoTime(), settings, self, List.of(), List.of(), List.of(),
                    ResistantBlockCache.EMPTY, System.currentTimeMillis());
        }

        List<BlockPos> placePositions = capturePlacePositions();
        List<CrystalSnapshot> crystals = captureCrystals();
        ResistantBlockCache resistantBlocks = new ResistantBlockCache(mc().level, module.assumeInstantMine.getValue());

        return new SnapshotData(
                System.nanoTime(),
                settings,
                self,
                List.copyOf(targets),
                List.copyOf(placePositions),
                List.copyOf(crystals),
                resistantBlocks,
                System.currentTimeMillis()
        );
    }

    private SelfSnapshot captureSelfSnapshot(Player player, DamageUtils.ArmorEnchantmentMode armorMode) {
        Rot2f currentRotation = RotationManager.INSTANCE.getRotation();
        return new SelfSnapshot(
                player,
                player.position(),
                player.getEyePosition(),
                player.getBoundingBox(),
                getTotalHealth(player),
                player.hasEffect(MobEffects.WEAKNESS)
                        && (!player.hasEffect(MobEffects.STRENGTH) || player.getEffect(MobEffects.STRENGTH) == null || player.getEffect(MobEffects.STRENGTH).getAmplifier() <= 0),
                isToolLike(player.getMainHandItem()),
                player.getMainHandItem().is(ItemTags.SWORDS),
                DamageReductionData.fromEntity(player, armorMode),
                mc().level.getDifficulty(),
                currentRotation
        );
    }

    private List<TargetSnapshot> captureTargets(DamageUtils.ArmorEnchantmentMode armorMode) {
        if (mc().player == null || mc().level == null) return List.of();

        int ticks = module.motionPredict.getValue() ? module.predictTicks.getValue() : 0;
        List<LivingEntity> targets = TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                module.targetRange.getValue(),
                360.0f,
                module.players.getValue(),
                module.mobs.getValue(),
                module.animals.getValue(),
                false,
                false,
                false,
                false,
                true,
                living -> living.position().y > -64.0,
                module.maxTargets.getValue()
        ));

        if (targets.isEmpty()) {
            return List.of();
        }

        List<TargetSnapshot> list = new ArrayList<>(targets.size());
        for (LivingEntity living : targets) {
            list.add(buildTargetSnapshot(living, ticks, armorMode));
        }

        return List.copyOf(list);
    }

    /**
     * 用碰撞预测目标的落点：依次尝试完整位移、仅水平、仅垂直，全部失败则停止外推。
     */
    private TargetSnapshot buildTargetSnapshot(LivingEntity entity, int ticks, DamageUtils.ArmorEnchantmentMode armorMode) {
        double motionX = Mth.clamp(entity.getX() - entity.xo, -0.6, 0.6);
        double motionY = Mth.clamp(entity.getY() - entity.yo, -0.5, 0.5);
        double motionZ = Mth.clamp(entity.getZ() - entity.zo, -0.6, 0.6);

        AABB entityBox = entity.getBoundingBox();
        AABB targetBox = entityBox;
        for (int tick = 0; tick <= ticks; tick++) {
            AABB moved = canMove(entity, targetBox, motionX, motionY, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, motionX, 0.0, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, 0.0, motionY, 0.0);
            if (moved == null) break;
            targetBox = moved;
        }

        double offsetX = targetBox.minX - entityBox.minX;
        double offsetY = targetBox.minY - entityBox.minY;
        double offsetZ = targetBox.minZ - entityBox.minZ;
        Vec3 motion = new Vec3(offsetX, offsetY, offsetZ);
        Vec3 currentPos = entity.position();
        Vec3 predictedPos = currentPos.add(motion);

        return new TargetSnapshot(
                entity,
                predictedPos,
                targetBox,
                currentPos,
                motion,
                getTotalHealth(entity),
                entity instanceof Player,
                getRealSpeed(entity),
                getMinArmorRate(entity),
                DamageReductionData.fromEntity(entity, armorMode)
        );
    }

    private AABB canMove(Entity entity, AABB box, double motionX, double motionY, double motionZ) {
        AABB moved = box.move(motionX, motionY, motionZ);
        return mc().level.noCollision(entity, moved) ? moved : null;
    }

    /**
     * 筛选可放置位置：先按范围与视线得到候选，再剔除被实体占据的位置。
     */
    private List<BlockPos> capturePlacePositions() {
        List<BlockPos> rawPosList = getRawPosList();
        if (rawPosList.isEmpty()) return rawPosList;

        List<Entity> collidingEntities = getCollidingEntities();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : rawPosList) {
            if (!checkPlaceCollision(pos, collidingEntities)) continue;
            list.add(pos);
        }
        return list;
    }

    private List<CrystalSnapshot> captureCrystals() {
        if (mc().player == null || mc().level == null) return List.of();

        long current = System.currentTimeMillis();
        List<CrystalSnapshot> list = new ArrayList<>();
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;

            boolean breakable = !module.bbtt.getValue() || current - getSpawnTime(crystal) >= module.bbttFactor.getValue();
            breakable = breakable && checkBreakRange(crystal.position());

            list.add(new CrystalSnapshot(
                    crystal,
                    crystal.getId(),
                    crystal.position(),
                    crystal.getBoundingBox(),
                    breakable,
                    state().placedPosMap.containsKey(ZealotMath.toLong(crystal.getX(), crystal.getY() - 1.0, crystal.getZ()))
            ));
        }
        return List.copyOf(list);
    }

    /**
     * 放置候选列表带 60ms TTL：世界变化很快，过长缓存会让 COMMIT 使用过期位置。
     */
    private List<BlockPos> getRawPosList() {
        long now = System.currentTimeMillis();
        List<BlockPos> cached = state().cachedRawPosList;
        if (now < state().rawPosListExpireAt) return cached;
        cached = buildRawPosList();
        state().cachedRawPosList = cached;
        state().rawPosListExpireAt = now + 60L;
        return cached;
    }

    private List<BlockPos> buildRawPosList() {
        if (nullCheck()) return List.of();

        List<BlockPos> list = new ArrayList<>();
        double range = module.placeRange.getValue();
        double rangeSq = range * range;
        double wallRangeSq = module.wallRange.getValue() * module.wallRange.getValue();
        int floor = Mth.floor(range);
        int ceil = Mth.ceil(range);
        Vec3 feetPos = mc().player.position();
        Vec3 eyePos = mc().player.getEyePosition();

        int feetX = Mth.floor(feetPos.x);
        int feetY = Mth.floor(feetPos.y);
        int feetZ = Mth.floor(feetPos.z);

        for (int x = feetX - floor; x <= feetX + ceil; x++) {
            for (int z = feetZ - floor; z <= feetZ + ceil; z++) {
                for (int y = feetY - floor; y <= feetY + ceil; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc().level.getWorldBorder().isWithinBounds(pos)) continue;

                    double crystalX = x + 0.5;
                    double crystalY = y + 1.0;
                    double crystalZ = z + 0.5;
                    if (placeDistanceSq(mc().player, crystalX, crystalY, crystalZ) > rangeSq) continue;
                    if (!isPotentialPlacePosition(pos)) continue;

                    double feetDistSq = feetPos.distanceToSqr(crystalX, crystalY, crystalZ);
                    if (feetDistSq > wallRangeSq && !RaytraceUtils.canSeePointFrom(eyePos, new Vec3(crystalX, crystalY + 1.7, crystalZ))) {
                        continue;
                    }

                    list.add(pos);
                }
            }
        }

        list.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distToCenterSqr(feetX, feetY, feetZ)).reversed());
        return list;
    }

    private List<Entity> getCollidingEntities() {
        if (nullCheck()) return List.of();

        List<Entity> colliding = new ArrayList<>();
        double rangeSq = module.placeRange.getValue() * module.placeRange.getValue();
        int feetX = Mth.floor(mc().player.getX());
        int feetY = Mth.floor(mc().player.getY());
        int feetZ = Mth.floor(mc().player.getZ());
        boolean single = module.placeMode.getValue() == PlaceMode.Single;

        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;

            double adjustedRange = Mth.ceil(rangeSq) - Math.ceil((entity.getBbWidth() / 2.0f) * (entity.getBbWidth() / 2.0f) * 2.0f);
            double dist = entity.distanceToSqr(feetX + 0.5, feetY + 0.5, feetZ + 0.5);
            if (dist > adjustedRange) continue;

            if (!(entity instanceof EndCrystal crystal)) {
                colliding.add(entity);
            } else if (!single || !checkBreakRange(crystal.position())) {
                colliding.add(entity);
            }
        }

        return colliding;
    }

    private boolean checkPlaceCollision(BlockPos pos, List<Entity> collidingEntities) {
        double minX = pos.getX() + 0.001;
        double minY = pos.getY() + 1.0;
        double minZ = pos.getZ() + 0.001;
        double maxX = pos.getX() + 0.999;
        double maxY = pos.getY() + 3.0;
        double maxZ = pos.getZ() + 0.999;

        for (Entity entity : collidingEntities) {
            if (entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
        }
        return true;
    }

    boolean isPlaceable(BlockPos pos) {
        if (!isPotentialPlacePosition(pos)) return false;
        return mc().level.getEntities(null, ZealotMath.crystalPlaceBox(pos)).isEmpty();
    }

    private boolean isPotentialPlacePosition(BlockPos pos) {
        if (!isCrystalSupport(pos)) return false;

        BlockPos crystalPos = pos.above();
        if (!mc().level.getBlockState(crystalPos).canBeReplaced()) return false;
        return mc().level.getBlockState(crystalPos.above()).canBeReplaced();
    }

    boolean isCrystalSupport(BlockPos pos) {
        BlockState state = mc().level.getBlockState(pos);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    double placeDistanceSq(Entity entity, double x, double y, double z) {
        return module.placeRangeMode.getValue() == RangeMode.Feet
                ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    double breakDistanceSq(Entity entity, double x, double y, double z) {
        return module.breakRangeMode.getValue() == RangeMode.Feet
                ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    private double eyeDistanceSq(Entity entity, double x, double y, double z) {
        double dx = entity.getX() - x;
        double dy = entity.getY() + entity.getEyeHeight() - y;
        double dz = entity.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 判断水晶是否在破坏范围内；贴墙时要求眼睛到水晶上方可见，避免隔墙攻击。
     */
    boolean checkBreakRange(Vec3 crystalPos) {
        double rangeSq = module.breakRange.getValue() * module.breakRange.getValue();
        if (breakDistanceSq(mc().player, crystalPos.x, crystalPos.y, crystalPos.z) > rangeSq) return false;

        Vec3 eyePos = mc().player.getEyePosition();
        return eyePos.distanceToSqr(crystalPos) <= module.wallRange.getValue() * module.wallRange.getValue()
                || RaytraceUtils.canSeePointFrom(eyePos, new Vec3(crystalPos.x, crystalPos.y + 1.7, crystalPos.z));
    }

    int getMinArmorRate(LivingEntity entity) {
        int minDura = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (!armor.isDamageableItem()) continue;
            int maxDamage = armor.getMaxDamage();
            if (maxDamage <= 0) continue;
            int remaining = maxDamage - armor.getDamageValue();
            int percent = Math.clamp((int) ((remaining / (double) maxDamage) * 100.0), 0, 100);
            minDura = Math.min(minDura, percent);
        }
        return minDura;
    }

    double getRealSpeed(LivingEntity entity) {
        return Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) * 20.0;
    }

    float getTotalHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    /**
     * 水晶生成时间优先取网络包记录；缺失时用 {@code tickCount} 回推，避免 2B2T 模式误判。
     */
    long getSpawnTime(EndCrystal crystal) {
        return state().crystalSpawnMap.computeIfAbsent(crystal.getId(), ignored -> System.currentTimeMillis() - crystal.tickCount * 50L);
    }

    boolean isToolLike(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    boolean isHoldingTool() {
        return mc().player != null && isToolLike(mc().player.getMainHandItem());
    }

    boolean isEatingPaused() {
        if (!module.eatingPause.getValue() || mc().player == null) {
            return false;
        }

        return mc().player.isUsingItem();
    }

    /**
     * 每 250ms 归档一次爆炸计数，HUD 的爆炸速率取最近 8 个窗口的平均值。
     */
    private void updateExplosionSamples() {
        if (!state().explosionSampleTimer.passedMillise(250)) {
            return;
        }

        state().explosionSampleTimer.reset();
        state().explosionSamples.addLast(state().explosionsThisWindow);
        while (state().explosionSamples.size() > EXPLOSION_SAMPLE_SIZE) {
            state().explosionSamples.removeFirst();
        }
        state().explosionsThisWindow = 0;
    }
}
