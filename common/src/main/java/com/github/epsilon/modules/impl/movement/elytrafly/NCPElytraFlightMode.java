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

/**
 * NullPoint 的 ElytraFly 移动逻辑移植版本。
 * <p>
 * 该模式完全接管滑翔中的位移：在 {@link TravelEvent} 中自行计算速度，取消原版
 * {@code Player#travel} 后手动调用 {@link net.minecraft.world.entity.Entity#move}，
 * 因此不再受原版鞘翅物理影响。方向由玩家自身朝向决定，不提交托管旋转请求。
 * <p>
 * 开启 Armored 时按本体的甲飞方式工作：穿甲飞行，靠「换上鞘翅触发滑翔再换回胸甲」维持，
 * 移动逻辑本身不变（见 {@link #updateArmoredGlide}）。
 */
public class NCPElytraFlightMode extends ElytraFlightMode {

    /** 起飞瞬间使用的游戏速度倍率，源码取 0.3（tick 长度 0.3，约 3.33 倍速）。 */
    private static final float TIMER_BOOST = 0.3f;
    /** 未按跳跃键且未潜行时的垂直速度，源码为 -0.00000000000003 * DownFactor。 */
    private static final double HOVER_Y = -0.00000000000003;
    /** 计时器初始值：直接视作已经过很久，避免启用后首次触发被等待时间挡住。 */
    private static final long TIMER_ARMED_MS = 917813L;

    private boolean hasElytra;
    private boolean hasTouchedGround;
    /** 需要在下一次按键输入时模拟一次「按空格」：甲飞起跳与换甲触发滑翔都靠它。 */
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

        /*
         * 起飞后严格计时窗口结束时收回临时变速；同一逻辑在已经滑翔且仍处于 0.3 时兜底。
         * 只有本模式设置过变速才还原，避免覆盖 Timer 模块自己的倍率。
         */
        if (strictTimer.passedMillise(1500L) && !strictTimer.passedMillise(2000L)
                || timerBoosted && mc.player.isFallFlying() && TimerManager.INSTANCE.get() == TIMER_BOOST) {
            releaseTimerBoost();
        }

        if (mc.player.isFallFlying()) return;

        if (hasTouchedGround && elytraFly.ncpTimer.getValue() && !mc.player.onGround()) {
            TimerManager.INSTANCE.set(TIMER_BOOST);
            timerBoosted = true;
        }

        // 甲飞走换甲路径，不再单独发 InstantFly 的起飞包（穿上胸甲时服务端不会认这个包）。
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

    /**
     * 甲飞：穿甲时鞘翅不在身上，先换上鞘翅触发一次滑翔，再把胸甲换回来；鞘翅本来就穿在身上时直接起飞。
     * <p>
     * 换甲时机与 Control 模式一致——只在未滑翔时补一次：{@code startFallFlying()} 会立刻置位客户端滑翔标记，
     * 服务端清掉标记后才会再次换甲，因此不会每个 tick 连续点击背包；换回胸甲后 {@code hasElytra} 仍为真，
     * {@link #onTravel} 的移动逻辑照常生效。
     */
    private void updateArmoredGlide(FindItemResult elytra) {
        if (!canGlide(elytra.found())) return;

        if (mc.player.onGround()) {
            shouldJump = true;
            return;
        }
        if (!canStartFallFlying()) return;

        if (!elytra.found()) {
            // 鞘翅已经穿在身上，不需要换甲，直接起飞。
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

        // 直接读按键状态：本模式为触发滑翔会注入一次 jump，不能让那次注入反过来改变当 tick 的移动。
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

    /**
     * 按移动输入与插值朝向计算水平速度。
     * <p>
     * 输入直接取按键状态：源码模块的 KeyboardInputEvent 只能取消，其
     * {@code input.movementForward/Sideways} 就是未归一化的 ±1 冲量，而 {@code getMoveVector()}
     * 对斜向做了归一化，纯横移时会相差一个 1/√2 系数。
     */
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
