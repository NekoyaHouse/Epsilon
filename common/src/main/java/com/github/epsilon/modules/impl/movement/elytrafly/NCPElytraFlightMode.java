package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.managers.TimerManager;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class NCPElytraFlightMode extends ElytraFlightMode {

    /** 起飞时的 tick 长度倍率，0.3 对应约 3.33 倍速。 */
    private static final float TIMER_BOOST = 0.3f;
    /** 悬停时的微小下降速度，乘以 DownFactor。 */
    private static final double HOVER_Y = -0.00000000000003;
    /** 预置计时器经过时间，使首次起飞无需等待。 */
    private static final long TIMER_ARMED_MS = 917813L;

    private boolean hasElytra;
    private boolean hasTouchedGround;
    private boolean shouldJump;
    /** 是否由本模式设置了临时变速，只有设置过才负责还原。 */
    private boolean timerBoosted;
    private final TimerUtils instantFlyTimer = new TimerUtils();
    private final TimerUtils strictTimer = new TimerUtils();

    public NCPElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    @Override
    public void onEnable() {
        hasElytra = false;
        hasTouchedGround = false;
        shouldJump = false;
        instantFlyTimer.setMs(TIMER_ARMED_MS);
        strictTimer.setMs(TIMER_ARMED_MS);
    }

    @Override
    public void onDisable() {
        hasElytra = false;
        hasTouchedGround = false;
        shouldJump = false;
        releaseTimerBoost();
    }

    @Override
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public void onPlayerTick() {
        if (mc.player.onGround()) {
            hasTouchedGround = true;
        }
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        hasElytra = canGlide(elytra.found());

                // 仅释放本模式设置的临时变速。
        if (strictTimer.passedMillise(1500L) && !strictTimer.passedMillise(2000L)
                || timerBoosted && mc.player.isFallFlying() && TimerManager.INSTANCE.get() == TIMER_BOOST) {
            releaseTimerBoost();
        }

        if (mc.player.isFallFlying()) return;

        if (hasTouchedGround && elytraFly.ncpTimer.getValue() && !mc.player.onGround()) {
            TimerManager.INSTANCE.set(TIMER_BOOST);
            timerBoosted = true;
        }

        if (elytraFly.armored.getValue()) {
            updateArmoredGlide(elytra);
            return;
        }

        if (mc.player.onGround()
                || !elytraFly.ncpInstantFly.getValue()
                || mc.player.getDeltaMovement().y >= 0.0) {
            return;
        }
        if (!instantFlyTimer.passedMillise((long) (1000.0 * elytraFly.ncpTimeout.getValue()))) return;
        instantFlyTimer.reset();

        if (canStartFallFlying()) {
            startFallFlying();
        }
        hasTouchedGround = false;
        strictTimer.reset();
    }

    /** 未滑翔时临时换上鞘翅触发滑翔，再恢复胸甲；已装备鞘翅时直接起飞。 */
    private void updateArmoredGlide(FindItemResult elytra) {
        if (!canGlide(elytra.found())) return;

        if (mc.player.onGround()) {
            shouldJump = true;
            return;
        }
        if (!canStartFallFlying()) return;

        if (!elytra.found()) {
            if (startFallFlying()) {
                shouldJump = true;
            }
            return;
        }
        // 容器界面下的槽位下标不属于 InventoryMenu，此时不做装备操作，避免把物品点进别的容器。
        if (mc.player.containerMenu != mc.player.inventoryMenu) return;

        int slot = toContainerSlot(elytra.slot());
        swapArmor(slot);
        if (startFallFlying()) {
            shouldJump = true;
        }
        swapArmor(slot);
    }

    private int toContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    @Override
    public void onTravel(TravelEvent event) {
        if (!hasElytra || !mc.player.isFallFlying()) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 lookVec = mc.player.calculateViewVector(-elytraFly.ncpUpPitch.getValue().floatValue(), mc.player.getYRot(partialTick));
        double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);

        Vec3 movement = mc.player.getDeltaMovement();
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        double motionDist = Math.sqrt(x * x + z * z);

        // 读取真实跳跃键，避免起飞时注入的 jump 影响移动。
        boolean jumping = mc.options.keyJump.isDown();

        if (mc.options.keyShift.isDown()) {
            y = -elytraFly.ncpDownSpeed.getValue();
        } else if (!jumping) {
            y = HOVER_Y * elytraFly.ncpDownFactor.getValue();
        }

        if (jumping) {
            if (motionDist > elytraFly.ncpUpFactor.getValue() / elytraFly.ncpUpFactor.getMax()) {
                double rawUpSpeed = motionDist * 0.01325;
                y += rawUpSpeed * 3.2;
                x -= lookVec.x * rawUpSpeed / lookDist;
                z -= lookVec.z * rawUpSpeed / lookDist;
            } else {
                double[] dir = directionSpeed(elytraFly.ncpSpeed.getValue());
                x = dir[0];
                z = dir[1];
            }
        }

        if (lookDist > 0.0) {
            x += (lookVec.x / lookDist * motionDist - x) * 0.1;
            z += (lookVec.z / lookDist * motionDist - z) * 0.1;
        }

        if (!jumping) {
            double[] dir = directionSpeed(elytraFly.ncpSpeed.getValue());
            x = dir[0];
            z = dir[1];
        }

        if (!elytraFly.ncpNoDrag.getValue()) {
            y *= 0.9900000095367432;
            x *= 0.9800000190734863;
            z *= 0.9900000095367432;
        }

        double finalDist = Math.sqrt(x * x + z * z);
        if (elytraFly.ncpSpeedLimit.getValue() && finalDist > elytraFly.ncpMaxSpeed.getValue()) {
            x = x * elytraFly.ncpMaxSpeed.getValue() / finalDist;
            z = z * elytraFly.ncpMaxSpeed.getValue() / finalDist;
        }

        mc.player.setDeltaMovement(x, y, z);
        event.cancel();
        mc.player.move(MoverType.SELF, mc.player.getDeltaMovement());
    }

    private void releaseTimerBoost() {
        if (!timerBoosted) return;
        timerBoosted = false;
        TimerManager.INSTANCE.reset();
    }

    /** 按真实移动按键与插值朝向计算水平速度。 */
    private double[] directionSpeed(double speed) {
        float forward = (mc.options.keyUp.isDown() ? 1.0f : 0.0f) - (mc.options.keyDown.isDown() ? 1.0f : 0.0f);
        float side = (mc.options.keyLeft.isDown() ? 1.0f : 0.0f) - (mc.options.keyRight.isDown() ? 1.0f : 0.0f);
        float yaw = mc.player.getYRot(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));

        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += forward > 0.0f ? -45.0f : 45.0f;
            } else if (side < 0.0f) {
                yaw += forward > 0.0f ? 45.0f : -45.0f;
            }
            side = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        return new double[]{
                forward * speed * cos + side * speed * sin,
                forward * speed * sin - side * speed * cos
        };
    }

}
