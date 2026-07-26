package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.modules.impl.movement.Stuck;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoThrow extends Module {

    public static final AutoThrow INSTANCE = new AutoThrow();

    private AutoThrow() {
        super("Auto Throw", Category.COMBAT);
    }

    private final DoubleSetting minRange = doubleSetting("Min Range", 4.5, 0.0, 6.0, 0.25);
    private final DoubleSetting maxRange = doubleSetting("Max Range", 7.0, 5.0, 20.0, 0.25);
    private final IntSetting fov = intSetting("FOV", 360, 10, 360, 1);
    private final IntSetting minDelay = intSetting("Min Delay", 100, 0, 1000, 10);
    private final IntSetting maxDelay = intSetting("Max Delay", 300, 0, 1000, 10);
    private final IntSetting swapDelay = intSetting("Swap Back Delay", 200, 0, 500, 10);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 1, 180, 1);
    private final IntSetting rotationBackSpeed = intSetting("Rotation Back Speed", 180, 1, 180, 1);

    private ThrowInfo pendingPlan;
    private Rot2f pendingRotation;
    private boolean pendingSwapBack = false;

    private final TimerUtils timer = new TimerUtils();
    private final TimerUtils swapTimer = new TimerUtils();

    @Override
    protected void onEnable() {
        pendingPlan = null;
        pendingRotation = null;
        pendingSwapBack = false;
    }

    @Override
    protected void onDisable() {
        pendingPlan = null;
        pendingRotation = null;
        pendingSwapBack = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (pendingSwapBack && swapTimer.passedMillise(swapDelay.getValue())) {
            InvUtils.swapBack();
            pendingSwapBack = false;
        }

        if (Scaffold.INSTANCE.isEnabled() || Stuck.INSTANCE.isEnabled()) {
            pendingPlan = null;
            pendingRotation = null;
            return;
        }

        Rot2f rotation;

        if (pendingPlan != null) {
            if (pendingRotation != null && isFacing(pendingRotation)) {
                Rot2f backRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
                throwItem(pendingPlan, pendingRotation);
                pendingPlan = null;
                pendingRotation = null;
                Managers.ROTATION.setRotations(backRotation, rotationBackSpeed.getValue(), Priority.Highest);
            }
            return;
        }

        ThrowInfo plan = updateThrowInfo();
        if (plan == null) {
            return;
        }

        LivingEntity target = Managers.TARGET.acquirePrimary(TargetRequest.of(
                maxRange.getValue(),
                fov.getValue().floatValue(),
                true, false, false, false, false, false, false, true, false,
                targetEntity -> RotationUtils.getEyeDistanceToEntity(targetEntity) >= minRange.getValue(),
                1
        ));
        if (target == null) {
            return;
        }

        if (timer.passedMillise(MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue())) && canRotate(plan.hand)) {
            rotation = getRotation(target);
            Managers.ROTATION.setRotations(rotation, rotationSpeed.getValue(), Priority.Highest);
            pendingPlan = plan;
            pendingRotation = rotation;
            timer.reset();
        }
    }

    private void throwItem(ThrowInfo plan, Rot2f rotation) {
        float originalYaw = mc.player.getYRot();
        float originalPitch = mc.player.getXRot();
        if (rotation != null) {
            mc.player.setYRot(rotation.getYaw());
            mc.player.setXRot(rotation.getPitch());
        }
        if (plan.hand == InteractionHand.MAIN_HAND && mc.player.getInventory().getSelectedSlot() != plan.hotbarSlot) {
            InvUtils.swap(plan.hotbarSlot, true);
        }

        mc.gameMode.useItem(mc.player, plan.hand);
        mc.player.swing(plan.hand);

        if (rotation != null) {
            mc.player.setYRot(originalYaw);
            mc.player.setXRot(originalPitch);
        }

        pendingSwapBack = true;
        swapTimer.reset();
    }

    private ThrowInfo updateThrowInfo() {
        ItemStack offhand = mc.player.getOffhandItem();
        if (isThrowable(offhand)) {
            return new ThrowInfo(InteractionHand.OFF_HAND, -1);
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        ItemStack mainhand = mc.player.getInventory().getItem(selected);
        if (isThrowable(mainhand)) {
            return new ThrowInfo(InteractionHand.MAIN_HAND, selected);
        }

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            ItemStack stack = mc.player.getInventory().getItem(hotbar);
            if (isThrowable(stack)) {
                return new ThrowInfo(InteractionHand.MAIN_HAND, hotbar);
            }
        }
        return null;
    }

    private boolean canRotate(InteractionHand hand) {
        if (mc.player.isUsingItem()) {
            return false;
        }
        ItemStack stack = hand == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (stack.isEmpty()) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof EnderpearlItem) {
            return false;
        }
        if (item instanceof BowItem) {
            return false;
        }
        if (item instanceof PotionItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem) {
            return false;
        }
        return !stack.has(DataComponents.FOOD);
    }

    private boolean isFacing(Rot2f target) {
        Rot2f current = Managers.ROTATION.rotations;
        if (current == null) {
            current = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }
        float yawDiff = Math.abs(Mth.wrapDegrees(current.getYaw() - target.getYaw()));
        float pitchDiff = Math.abs(Mth.wrapDegrees(current.getPitch() - target.getPitch()));
        return yawDiff <= 2.0f && pitchDiff <= 2.0f;
    }

    private Rot2f getRotation(LivingEntity target) {
        Vec3 origin = mc.player.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 targetMovement = target.getKnownMovement();
        Vec3 playerMovement = mc.player.getKnownMovement();

        // RotationManager 平滑转向期间目标仍会移动，提前估算这段时间的位移。
        Rot2f current = Managers.ROTATION.getRotation();
        if (current == null) {
            current = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }
        float yawDifference = Math.abs(Mth.wrapDegrees(current.getYaw() - mc.player.getYRot()));
        float pitchDifference = Math.abs(current.getPitch() - mc.player.getXRot());
        int rotationLeadTicks = (int) Math.ceil(
                Math.max(yawDifference, pitchDifference) / Math.max(1.0, rotationSpeed.getValue())
        ) + 1;

        Vec3 leadTarget = targetCenter.add(targetMovement.scale(rotationLeadTicks));
        double leadDx = leadTarget.x - origin.x;
        double leadDz = leadTarget.z - origin.z;
        double horizontalDistance = Math.sqrt(leadDx * leadDx + leadDz * leadDz);
        if (horizontalDistance < 1.0E-5) {
            return new Rot2f(mc.player.getYRot(), -90.0F);
        }

        double bestScore = Double.POSITIVE_INFINITY;
        float bestYaw = mc.player.getYRot();
        float bestPitch = mc.player.getXRot();

        // 逐 tick、逐俯仰角模拟 ThrowableProjectile.tick() 的重力和惯性。
        for (int ticks = 1; ticks <= 20; ticks++) {
            Vec3 predictedTarget = targetCenter.add(targetMovement.scale(rotationLeadTicks + ticks));
            float yaw = (float) Math.toDegrees(Math.atan2(
                    -(predictedTarget.x - origin.x), predictedTarget.z - origin.z
            ));
            AABB targetBox = target.getBoundingBox()
                    .move(targetMovement.scale(rotationLeadTicks + ticks))
                    .inflate(0.125);

            for (float pitch = -89.0F; pitch <= 89.0F; pitch += 0.5F) {
                Vec3 projectilePosition = simulateProjectile(origin, yaw, pitch, ticks, playerMovement);
                double missDistance = distanceToBox(projectilePosition, targetBox);
                if (missDistance > 0.35 || hasBlockingCollision(origin, yaw, pitch, ticks, playerMovement)) {
                    continue;
                }

                // 优先选择低弧线和较短飞行时间，减少目标移动误差。
                double score = missDistance * 100.0 + ticks * 0.01 + Math.abs(pitch) * 0.0001;
                if (score < bestScore) {
                    bestScore = score;
                    bestYaw = yaw;
                    bestPitch = pitch;
                }
            }
        }

        if (Double.isFinite(bestScore)) {
            return new Rot2f(Mth.wrapDegrees(bestYaw), Mth.clamp(bestPitch, -90.0F, 90.0F));
        }

        // 没有可行弹道时退化为实体中心，避免返回无效旋转。
        float fallbackYaw = (float) Math.toDegrees(Math.atan2(-leadDx, leadDz));
        float fallbackPitch = (float) -Math.toDegrees(Math.atan2(leadTarget.y - origin.y, horizontalDistance));
        return new Rot2f(Mth.wrapDegrees(fallbackYaw), Mth.clamp(fallbackPitch, -90.0F, 90.0F));
    }

    private Vec3 simulateProjectile(Vec3 origin, float yaw, float pitch, int ticks, Vec3 playerMovement) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 direction = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians)
        );
        Vec3 movement = direction.scale(1.5).add(
                playerMovement.x,
                mc.player.onGround() ? 0.0 : playerMovement.y,
                playerMovement.z
        );
        Vec3 position = origin;
        for (int tick = 0; tick < ticks; tick++) {
            movement = movement.add(0.0, -0.03, 0.0).scale(0.99);
            position = position.add(movement);
        }
        return position;
    }

    private boolean hasBlockingCollision(Vec3 origin, float yaw, float pitch, int ticks, Vec3 playerMovement) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 direction = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians)
        );
        Vec3 movement = direction.scale(1.5).add(
                playerMovement.x,
                mc.player.onGround() ? 0.0 : playerMovement.y,
                playerMovement.z
        );
        Vec3 position = origin;
        for (int tick = 0; tick < ticks; tick++) {
            movement = movement.add(0.0, -0.03, 0.0).scale(0.99);
            Vec3 nextPosition = position.add(movement);
            HitResult result = mc.level.clip(new ClipContext(
                    position,
                    nextPosition,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (result.getType() != HitResult.Type.MISS) {
                return true;
            }
            position = nextPosition;
        }
        return false;
    }

    private double distanceToBox(Vec3 point, AABB box) {
        double dx = Math.max(box.minX - point.x, Math.max(0.0, point.x - box.maxX));
        double dy = Math.max(box.minY - point.y, Math.max(0.0, point.y - box.maxY));
        double dz = Math.max(box.minZ - point.z, Math.max(0.0, point.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean isThrowable(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == Items.EGG || stack.getItem() == Items.SNOWBALL);
    }

    private record ThrowInfo(InteractionHand hand, int hotbarSlot) {
    }

}
