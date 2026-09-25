package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.BreakMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwingHand;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwingMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwitchMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.BreakPlan;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.PlaceInfo;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.TargetSnapshot;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModulePart;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * COMMIT 阶段：唯一允许产生外部副作用的 Part。
 *
 * <p>放置与破坏都必须在执行前重新校验：目标仍然存在、位置仍在范围内、旋转已经就位、玩家仍持有水晶。
 * 每个决策最多执行一次，并在执行后重置对应计时器，避免同一 tick 重复提交。
 */
final class ZealotCommitPart extends ZealotPartBase implements ModulePart {

    ZealotCommitPart(ZealotCrystalPlus module) {
        super(module);
    }

    @Override
    public void declare(ModuleDeclaration declaration) {
        // 节点统一由 ZealotSettingsPart 声明，保证节点图集中可见。
    }

    /**
     * COMMIT 节点：优先破坏，其次放置；两者都未发生时按空闲时间回收渲染目标。
     */
    void commitAction(PlayerTickEvent.Pre event) {
        if (module.observePart.isEatingPaused()) return;

        BreakPlan actionBreak = module.decidePart.getActionBreakPlan();
        boolean acted = module.breakMode.getValue() != BreakMode.Off
                && state().breakTimer.passedMillise(module.breakDelay.getValue())
                && actionBreak != null
                && breakDirect(actionBreak);

        PlaceInfo actionPlace = module.decidePart.getActionPlaceInfo();
        if (!acted
                && module.placeMode.getValue() != PlaceMode.Off
                && state().placeTimer.passedMillise(module.placeDelay.getValue())
                && actionPlace != null
                && shouldAttemptPlace(actionPlace)) {
            acted = placeDirect(actionPlace, false);
        }

        if (!acted && state().lastActiveTime > 0 && System.currentTimeMillis() - state().lastActiveTime > 250L) {
            module.renderState.deactivate();
        }
    }

    /**
     * 执行一次放置：先提交旋转请求，旋转就位后再切换物品栏并调用 {@code useItemOn}。
     *
     * @return 是否消费了本次放置机会（返回 true 时调用方不应再尝试其他动作）
     */
    boolean placeDirect(PlaceInfo placeInfo, boolean ignoreTimer) {
        Player player = mc().player;
        if (player == null) return false;
        if (!ignoreTimer && !state().placeTimer.passedMillise(module.placeDelay.getValue())) return false;

        FindItemResult crystals = findCrystalItem();
        if (!crystals.found()) return false;

        InteractionHand hand = crystals.getHand();
        BlockHitResult hitResult = new BlockHitResult(placeInfo.hitVec(), placeInfo.side(), placeInfo.blockPos(), false);

        Rot2f rotation = placeInfo.rotation();
        RotationManager.INSTANCE.setRotations(rotation, module.decidePart.getRotationSpeed(), null, Priority.High);
        if (module.preRotation.getValue() && !module.decidePart.isRotationReady(rotation)) {
            return true;
        }

        if (hand == InteractionHand.MAIN_HAND && crystals.slot() != player.getInventory().getSelectedSlot() && crystals.slot() != 40) {
            switch (module.placeSwitchMode.getValue()) {
                case Off -> {
                    return false;
                }
                case Legit -> {
                    InvUtils.swap(crystals.slot(), false);
                    state().lastSwapTime = System.currentTimeMillis();
                }
                case Ghost -> {
                    InvUtils.swap(crystals.slot(), true);
                    state().lastSwapTime = System.currentTimeMillis();
                }
            }
        }

        InteractionResult result = mc().gameMode.useItemOn(mc().player, hand, hitResult);
        if (result.consumesAction()) {
            if (module.placeSwing.getValue()) {
                doSwing(resolveSwingHand(true));
            }
            state().placedPosMap.put(placeInfo.blockPos().asLong(), System.currentTimeMillis() + module.ownTimeout.getValue());
            state().placeTimer.reset();
            state().lastActiveTime = System.currentTimeMillis();
            state().target = placeInfo.target();
            module.renderState.updateTarget(placeInfo.blockPos(), placeInfo.targetDamage(), placeInfo.selfDamage());
        }
        InvUtils.swapBack();

        return true;
    }

    /**
     * 执行一次破坏：必要时先换到武器以绕过虚弱，再攻击水晶。
     *
     * @return 是否消费了本次破坏机会
     */
    boolean breakDirect(BreakPlan breakPlan) {
        if (breakPlan == null) return false;
        if (module.placeSwitchMode.getValue() != SwitchMode.Ghost
                && module.antiWeakness.getValue() != SwitchMode.Ghost
                && System.currentTimeMillis() - state().lastSwapTime < module.swapDelay.getValue() * 50L) {
            return false;
        }

        if (mc().player == null) return false;
        boolean needsAntiWeaknessSwap = mc().player.hasEffect(MobEffects.WEAKNESS) && !module.observePart.isHoldingTool();
        int weaponSlot = -1;
        if (needsAntiWeaknessSwap) {
            if (module.antiWeakness.getValue() == SwitchMode.Off) return false;
            weaponSlot = findWeaponSlot();
            if (weaponSlot == -1) return false;
        }

        Rot2f rotation = RotationUtils.calculate(breakPlan.pos());
        RotationManager.INSTANCE.setRotations(rotation, module.decidePart.getRotationSpeed(), null, Priority.High);
        if (module.preRotation.getValue() && !module.decidePart.isRotationReady(rotation)) {
            return true;
        }

        if (needsAntiWeaknessSwap) {
            InvUtils.swap(weaponSlot, module.antiWeakness.getValue() == SwitchMode.Ghost);
            state().lastSwapTime = System.currentTimeMillis();
        }

        // 旋转等待期间目标可能已经消失，必须重新解析实体。
        Entity current = mc().level.getEntity(breakPlan.entityId());
        if (!(current instanceof EndCrystal currentCrystal) || !currentCrystal.isAlive()) {
            InvUtils.swapBack();
            return true;
        }
        if (!module.observePart.checkBreakRange(currentCrystal.position())) {
            InvUtils.swapBack();
            return true;
        }

        mc().gameMode.attack(mc().player, currentCrystal);
        doSwing(resolveSwingHand(false));
        state().breakTimer.reset();
        state().lastActiveTime = System.currentTimeMillis();
        state().attackedCrystalMap.put(currentCrystal.getId(), System.currentTimeMillis() + 1000L);
        state().attackedPosMap.put(BlockPos.containing(currentCrystal.position()).asLong(), System.currentTimeMillis() + 1000L);
        module.renderState.updateTarget(currentCrystal.blockPosition().below(), breakPlan.targetDamage(), breakPlan.selfDamage());

        PlaceInfo placeInfo = module.decidePart.getActionPlaceInfo();
        if (module.packetPlace.getValue().onBreak && placeInfo != null && ZealotMath.crystalPlaceBoxIntersects(placeInfo.blockPos(), currentCrystal.getBoundingBox())) {
            placeDirect(placeInfo, true);
        }

        InvUtils.swapBack();

        return true;
    }

    /**
     * 观察水晶生成包：符合 {@code Packet Break} 条件时立即破坏一次。
     * <p>
     * 这里仍然属于 COMMIT 语义——它会攻击实体，因此必须先通过范围与旋转校验。
     */
    void observeCrystalSpawn(ClientboundAddEntityPacket packet) {
        state().crystalSpawnMap.put(packet.getId(), System.currentTimeMillis());
        SnapshotData snapshot = state().latestSnapshot;
        if (snapshot == null || snapshot.targets().isEmpty() || module.bbtt.getValue()) return;

        Vec3 crystalPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (!module.observePart.checkBreakRange(crystalPos) || !module.decidePart.checkCrystalRotation(crystalPos, module.breakRotationRange.getValue())) {
            return;
        }
        PlaceInfo placeInfo = module.decidePart.getActionPlaceInfo();
        if (placeInfo == null) {
            placeInfo = module.decidePart.getValidPlaceInfo(state().cachedRotationPlaceInfo, false);
        }
        if (placeInfo == null) return;

        boolean shouldBreak = switch (module.packetBreak.getValue()) {
            case Target -> ZealotMath.crystalPlaceBoxIntersects(placeInfo.blockPos(), ZealotMath.crystalBoundingBox(crystalPos));
            case Own -> ZealotMath.crystalPlaceBoxIntersects(placeInfo.blockPos(), ZealotMath.crystalBoundingBox(crystalPos))
                    || (state().placedPosMap.containsKey(ZealotMath.toLong(packet.getX(), packet.getY() - 1.0, packet.getZ())) && checkBreakDamage(snapshot, crystalPos));
            case Smart -> ZealotMath.crystalPlaceBoxIntersects(placeInfo.blockPos(), ZealotMath.crystalBoundingBox(crystalPos))
                    || checkBreakDamage(snapshot, crystalPos);
            case All -> true;
            case Off -> false;
        };

        if (!shouldBreak) return;

        BreakPlan immediate = new BreakPlan(null, packet.getId(), crystalPos,
                ZealotDamage.calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks()),
                snapshot.targets().isEmpty() ? 0.0f
                        : ZealotDamage.calcDamage(snapshot.targets().getFirst(), crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty()));
        breakDirect(immediate);
    }

    /**
     * 观察爆炸音效：命中玩家或放置点附近时清空计时标记，并按 {@code Packet Place} 立即补放。
     */
    void observeExplosion(ClientboundSoundPacket packet) {
        if (packet.getSound() != SoundEvents.GENERIC_EXPLODE) return;

        Vec3 soundPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (state().attackedPosMap.containsKey(BlockPos.containing(soundPos).asLong())) {
            state().explosionsThisWindow++;
        }

        PlaceInfo placeInfo = module.decidePart.getValidPlaceInfo(state().cachedPlaceInfo, true);
        if (placeInfo != null) {
            Vec3 placePos = ZealotMath.crystalPos(placeInfo.blockPos());
            if (placePos.distanceToSqr(soundPos) <= 144.0) {
                clearPlaceTracking();
                if (module.packetPlace.getValue().onRemove) {
                    placeDirect(placeInfo, true);
                }
                return;
            }
        }

        if (mc().player != null && mc().player.distanceToSqr(soundPos) <= 144.0) {
            clearPlaceTracking();
        }
    }

    private void clearPlaceTracking() {
        state().placedPosMap.clear();
        state().crystalSpawnMap.clear();
        state().attackedCrystalMap.clear();
        state().attackedPosMap.clear();
    }

    /**
     * 已放置的水晶是否值得立即破坏；用于 {@code Packet Break} 的 Smart/Own 判断。
     */
    private boolean checkBreakDamage(SnapshotData snapshot, Vec3 crystalPos) {
        float selfDamage = ZealotDamage.calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks());
        if (snapshot.self().totalHealth() - selfDamage <= snapshot.settings().noSuicide()) return false;

        for (TargetSnapshot targetInfo : snapshot.targets()) {
            float targetDamage = ZealotDamage.calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty());
            if (snapshot.settings().lethalOverride()
                    && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                    && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                return true;
            }

            if (selfDamage > snapshot.settings().breakMaxSelfDamage()) continue;
            boolean force = ZealotDamage.shouldForcePlace(snapshot.self(), targetInfo, snapshot.settings());
            float minDamage = force ? snapshot.settings().forcePlaceMinDamage() : snapshot.settings().breakMinDamage();
            float balance = force ? snapshot.settings().forcePlaceBalance() : snapshot.settings().breakBalance();
            if (targetDamage >= minDamage && targetDamage - selfDamage >= balance) {
                return true;
            }
        }

        return false;
    }

    /**
     * 放置前检查目标位置是否已被“尚未确认死亡”的水晶占据，避免重复放置。
     */
    boolean shouldAttemptPlace(PlaceInfo placeInfo) {
        if (module.spamPlace.getValue()) {
            return true;
        }
        if (nullCheck()) {
            return false;
        }

        net.minecraft.world.phys.AABB placeBox = ZealotMath.crystalPlaceBox(placeInfo.blockPos());
        for (Entity entity : mc().level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) continue;
            if (!placeBox.intersects(crystal.getBoundingBox())) continue;
            if (state().attackedCrystalMap.containsKey(crystal.getId())) continue;
            return false;
        }

        return true;
    }

    int findWeaponSlot() {
        if (mc().player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc().player.getInventory().getItem(i);
            if (module.observePart.isToolLike(stack)) return i;
        }
        return -1;
    }

    private FindItemResult findCrystalItem() {
        return InvUtils.findInHotbar(Items.END_CRYSTAL);
    }

    private InteractionHand resolveSwingHand(boolean placing) {
        if (mc().player == null) return InteractionHand.MAIN_HAND;
        return switch (module.swingHand.getValue()) {
            case OffHand -> InteractionHand.OFF_HAND;
            case MainHand -> InteractionHand.MAIN_HAND;
            case Auto ->
                    (placing && mc().player.getOffhandItem().is(Items.END_CRYSTAL)) || !mc().player.getOffhandItem().is(Items.GOLDEN_APPLE)
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        };
    }

    private void doSwing(InteractionHand hand) {
        switch (module.swingMode.getValue()) {
            case Client, Packet -> PlayerUtils.swingHand(hand);
            case None -> {
            }
        }
    }
}
