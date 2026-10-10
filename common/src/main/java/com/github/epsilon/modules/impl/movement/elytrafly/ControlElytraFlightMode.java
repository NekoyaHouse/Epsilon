package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.Constants;
import com.github.epsilon.events.impl.FallFlyingEvent;
import com.github.epsilon.events.impl.FireworkRotationEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombatInput;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class ControlElytraFlightMode extends ElytraFlightMode {

    private static final double CEILING_PROBE_DISTANCE = 0.75;
    private static final double CEILING_PROBE_EPSILON = 1.0E-4;
    private static final float CEILING_ESCAPE_PITCH = 5.0f;
    private static final int EAT_GLIDE_START_TICK = 30;
    private static final int CHEST_MENU_SLOT = 6;
    private static final int EAT_COMPLETE_REMAINING_TICKS = 3;
    private static final int EAT_CLIMB_TICK = 20;
    private static final int EAT_CLIMB_TICKS = 5;
    private static final float EAT_CLIMB_PITCH = -45f;
    /**
     * 总速度低于该值（格/tick）视为动量不足，缩短补发等待。
     *
     * <p>必须用**总速度**而不是水平分量：垂直爬升时水平速度本来就接近 0，只看水平分量会把
     * "正在上升"误判成"失速"并疯狂补发烟花。正常滑翔的总速度在 0.5~2 格/tick，被打停后接近 0。</p>
     */
    private static final double LOW_MOMENTUM_SPEED = 0.4;
    /** 动量不足时的最短补发间隔（tick）：完全绕过节流会变成每 tick 连发。 */
    private static final int LOW_MOMENTUM_BOOST_INTERVAL_TICKS = 4;

    private boolean hasFirstFirework;
    private boolean shouldJump;
    private boolean pendingFirework;
    private int eatGlideElytraSlot = -1;
    private boolean eatCompleted;
    private boolean eatClimbed;
    private int climbTicks;
    private final TimerUtils timer = new TimerUtils();

    public ControlElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    @Override
    public void onEnable() {
        hasFirstFirework = false;
        shouldJump = false;
        pendingFirework = false;
        timer.setMs(917813L);
        eatCompleted = false;
        eatClimbed = false;
        climbTicks = 0;
    }

    @Override
    public void onDisable() {
        shouldJump = false;
        pendingFirework = false;
        eatCompleted = false;
        eatClimbed = false;
        climbTicks = 0;
        releaseEatGlide();
    }

    @Override
    public void handleUnbreaking() {
        if (eatGlideElytraSlot >= 0) return;
        super.handleUnbreaking();
    }

    @Override
    public void onPlayerTick() {
        updateEatClimb();
        redirectRotation();
        updateEatKeyRelease();
        updateControl();
    }

    @Override
    public void onRightClick() {
        releaseUseKeyAfterEating();
    }

    @Override
    public void onTravel(TravelEvent event) {
        boolean avoidCeilingLift = shouldAvoidCeilingLift();

        if (avoidCeilingLift && mc.player.getDeltaMovement().y > 0.0) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }

        if (!avoidCeilingLift && !hasMoveInput() && (!elytraFly.useFireworks.getValue() || hasFirstFirework)) {
            mc.player.setDeltaMovement(0, 0.02, 0);
        }
    }

    @Override
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (elytraFly.noSprint.getValue()) {
            event.setSprint(false);
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public void onFallFlying(FallFlyingEvent event) {
        event.setYaw(calcYaw());
        event.setPitch(calcPitch());
    }

    @Override
    public void onFireworkUpdate(FireworkRotationEvent event) {
        event.setYaw(calcYaw());
        event.setPitch(calcPitch());
    }

    private void updateControl() {
        // 疾跑只该影响起飞/换甲这类控制动作，不该连带把烟花一起停掉：被击退后角色常常正好处于
        // 疾跑状态，烟花一停动量就再也回不来（滑翔的水平对齐项在速度归零后恒为 0）。
        if (elytraFly.noSprint.getValue() && mc.player.isSprinting()) {
            if (!elytraFly.armored.getValue()) {
                useTimedFirework();
            }
            return;
        }

        boolean eating = isEating();

        if (pendingFirework && !eating) {
            useTimedFirework();
        }

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);

        if (shouldEatGlide(eating)) {
            handleEatGlide(elytra);
            return;
        }
        releaseEatGlide();

        if (!canGlide(elytra.found()) || mc.player.onGround()) {
            shouldJump = true;
            hasFirstFirework = false;
            pendingFirework = false;
            return;
        }

        if (elytraFly.armored.getValue()) {
            if (canStartFallFlying()) {
                jiaFei(elytra.slot());
            }
        } else {
            if (canStartFallFlying() && startFallFlying()) {
                shouldJump = true;
            }
            useTimedFirework();
        }
    }

    private void useTimedFirework() {
        // 只有 ElytraCombat 真的在驾驶（有目标并产出控制输入）时才按它的意图拦截烟花。
        // 模块开着但待机（刚被打断、目标丢失）时 latestIntent 是 idle、useFirework 为 false，
        // 旧写法会在这里把烟花整个卡死——而那正是最需要推进保命的时候。
        if (ElytraCombat.INSTANCE.isDrivingFlight() && !ElytraCombat.INSTANCE.shouldUseFirework()) {
            ElytraDebug.log(ElytraDebug.SLOT_FIREWORK, "firework", "blocked by ElytraCombat");
            return;
        }
        if (!elytraFly.useFireworks.getValue()) return;
        if (isEating()) {
            if (timer.hasDelayed(elytraFly.boostDelay.getValue())) {
                pendingFirework = true;
            }
            return;
        }
        // 动量不足（被击退、撞到障碍、刚起飞）时把等待从 Boost Delay 缩短到最短间隔：
        // 按 Boost Delay 等 20 tick 动量早就掉光了，而完全绕过节流又会变成每 tick 连发烟花。
        boolean lowMomentum = isLowMomentum();
        int waitTicks = lowMomentum
                ? Math.min(LOW_MOMENTUM_BOOST_INTERVAL_TICKS, elytraFly.boostDelay.getValue())
                : elytraFly.boostDelay.getValue();
        if (!pendingFirework && !timer.hasDelayed(waitTicks)) {
            return;
        }
        pendingFirework = false;
        if (useFirework()) {
            hasFirstFirework = true;
            timer.reset();
            ElytraDebug.log(ElytraDebug.SLOT_FIREWORK, "firework",
                    lowMomentum ? "boost (low momentum)" : "boost");
        } else {
            ElytraDebug.log(ElytraDebug.SLOT_FIREWORK, "firework", "use failed");
        }
    }

    /**
     * 动量是否低到需要缩短补发等待。
     *
     * <p>判据是**总速度**（含 y 分量）：垂直爬升时水平速度本来就接近 0，只看水平分量会把
     * "正在上升"误判成"失速"。真正被打停时三个分量都会接近 0，而滑翔方程的水平对齐项按
     * {@code moveHorLength}（来自当前速度）缩放，速度为 0 时恒为 0、无法自恢复，只能靠烟花。</p>
     */
    private boolean isLowMomentum() {
        if (!mc.player.isFallFlying()) {
            return false;
        }
        return mc.player.getDeltaMovement().length() < LOW_MOMENTUM_SPEED;
    }

    private boolean isEating() {
        return mc.player.isUsingItem() && mc.player.getUseItem().has(DataComponents.FOOD);
    }

    private void redirectRotation() {
        if (ElytraDebug.enabled) {
            ElytraDebug.log(ElytraDebug.SLOT_ROTATION, "rotation.request",
                    "yaw=" + ElytraDebug.fmt(calcYaw()) + " pitch=" + ElytraDebug.fmt(calcPitch()));
        }
        RotationManager.INSTANCE.setRotations(new Rot2f(calcYaw(), calcPitch()), 360, Priority.Highest);
    }

    private float calcYaw() {
        ElytraCombatInput combatInput = ElytraCombat.INSTANCE.getControlInput();
        if (combatInput != null) {
            return combatInput.yaw();
        }

        float yaw = mc.player.getYRot();

        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();

        if (forward && !back) {
            if (left && !right) {
                yaw -= 45f;
            } else if (right && !left) {
                yaw += 45f;
            }
        } else if (back && !forward) {
            yaw += 180f;
            if (left && !right) {
                yaw += 45f;
            } else if (right && !left) {
                yaw -= 45f;
            }
        } else if (left && !right) {
            yaw -= 90f;
        } else if (right && !left) {
            yaw += 90f;
        }
        return Mth.wrapDegrees(yaw);
    }

    private float calcPitch() {
        ElytraCombatInput combatInput = ElytraCombat.INSTANCE.getControlInput();
        if (combatInput != null) {
            return applyCeilingPitchGuard(combatInput.pitch());
        }

        float pitch = mc.player.getXRot();

        boolean climb = isClimbing();
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();
        boolean moving = mc.player.isMoving();

        if (climb) {
            pitch = EAT_CLIMB_PITCH;
        } else if (sneak && jump) {
            pitch = -3f;
        } else if (jump) {
            pitch = moving ? -45f : -90f;
        } else if (sneak) {
            pitch = moving ? 45f : 90f;
        } else if (moving) {
            pitch = -1.9f;
        }
        return applyCeilingPitchGuard(pitch);
    }

    private float applyCeilingPitchGuard(float pitch) {
        if (shouldAvoidCeilingLift()) {
            return Math.max(pitch, CEILING_ESCAPE_PITCH);
        }
        return Mth.clamp(pitch, -90f, 90f);
    }

    private boolean shouldAvoidCeilingLift() {
        if (!mc.player.isFallFlying()) return false;

        AABB box = mc.player.getBoundingBox();
        // ElytraCombat 接管时用「意图抬升量」做探测距离：用当前 vy 会形成
        // 「拉升→探测变长→强制低头→上升变慢→探测变短→再拉升」的每 tick 振荡。
        double climb = ElytraCombat.INSTANCE.getControlInput() != null
                ? ElytraCombat.INSTANCE.getCombatIntendedClimb()
                : mc.player.getDeltaMovement().y;
        double probeDistance = CEILING_PROBE_DISTANCE + Math.max(0.0, climb);
        AABB ceilingProbe = new AABB(
                box.minX + CEILING_PROBE_EPSILON,
                box.maxY - CEILING_PROBE_EPSILON,
                box.minZ + CEILING_PROBE_EPSILON,
                box.maxX - CEILING_PROBE_EPSILON,
                box.maxY + probeDistance,
                box.maxZ - CEILING_PROBE_EPSILON
        );
        return !mc.level.noBlockCollision(mc.player, ceilingProbe);
    }

    private boolean hasMoveInput() {
        ElytraCombatInput combatInput = ElytraCombat.INSTANCE.getControlInput();
        if (combatInput != null) {
            return combatInput.hasMoveInput();
        }

        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown()
                || isClimbing();
    }

    private boolean shouldEatGlide(boolean eating) {
        if (elytraFly.noEat.getValue() || !elytraFly.armored.getValue()) return false;
        if (mc.player.onGround() || !eating) return false;
        if (mc.player.getTicksUsingItem() < EAT_GLIDE_START_TICK) return false;
        // 容器界面下的槽位下标不属于 InventoryMenu，此时不做任何装备操作。
        return mc.player.containerMenu == mc.player.inventoryMenu;
    }

    private void updateEatKeyRelease() {
        if (elytraFly.noEat.getValue()) {
            eatCompleted = false;
            return;
        }

        if (isEating() && mc.player.getUseItemRemainingTicks() <= EAT_COMPLETE_REMAINING_TICKS) {
            eatCompleted = true;
        }

        releaseUseKeyAfterEating();
    }

    private void releaseUseKeyAfterEating() {
        if (!eatCompleted || mc.player.isUsingItem()) return;

        mc.options.keyUse.setDown(false);
        eatCompleted = false;
    }

    private void updateEatClimb() {
        if (climbTicks > 0) {
            climbTicks--;
        }

        if (!isEating()) {
            eatClimbed = false;
            return;
        }
        if (elytraFly.noEat.getValue() || !elytraFly.armored.getValue()) return;
        if (eatClimbed || mc.player.onGround()) return;
        if (mc.player.getTicksUsingItem() < EAT_CLIMB_TICK) return;

        eatClimbed = true;
        climbTicks = EAT_CLIMB_TICKS;
    }

    private boolean isClimbing() {
        return climbTicks > 0;
    }

    private void handleEatGlide(FindItemResult elytra) {
        if (eatGlideElytraSlot < 0) {
            if (!elytra.found()) return;
            eatGlideElytraSlot = toContainerSlot(elytra.slot());
            swapArmor(eatGlideElytraSlot);
        }

        if (canStartFallFlying() && startFallFlying()) {
            shouldJump = true;
        }
    }

    private void releaseEatGlide() {
        int slot = eatGlideElytraSlot;
        if (slot < 0) return;

        if (mc.player == null || mc.level == null) {
            eatGlideElytraSlot = -1;
            return;
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) return;

        ItemStack chestStack = mc.player.containerMenu.getSlot(CHEST_MENU_SLOT).getItem();
        if (!LivingEntity.canGlideUsing(chestStack, EquipmentSlot.CHEST) || !isChestEquippable(mc.player.containerMenu.getSlot(slot).getItem())) {
            eatGlideElytraSlot = -1;
            Constants.LOGGER.warn("Elytra Fly: 胸甲或鞘翅槽位已被改动，跳过装备还原（记录槽位 {}，当前胸甲槽物品 {}）", slot, chestStack);
            return;
        }

        swapArmor(slot);
        eatGlideElytraSlot = -1;
    }

    private boolean isChestEquippable(ItemStack stack) {
        if (stack.isEmpty()) return true;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private int toContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private void jiaFei(int elytraSlot) {
        int elytra = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;

        swapArmor(elytra);
        if (startFallFlying()) {
            shouldJump = true;
        }
        useTimedFirework();
        swapArmor(elytra);
    }

}
