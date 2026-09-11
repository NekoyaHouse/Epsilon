package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AutoThrow extends Module {

    public static final AutoThrow INSTANCE = new AutoThrow();

    private AutoThrow() {
        super("Auto Throw", Category.COMBAT);
    }

    private final DoubleSetting minRange = doubleSetting("Min Range", 3.0, 0.0, 10.0, 0.1);
    private final DoubleSetting maxRange = doubleSetting("Max Range", 8.0, 2.0, 16.0, 0.1);
    private final IntSetting fov = intSetting("Fov", 90, 1, 360, 1);
    private final IntSetting delay = intSetting("Delay", 500, 0, 2000, 10);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 1, 180, 10);
    private final EnumSetting<Priority> rotationPriority = enumSetting("Rotation Priority", Priority.Lowest);

    private final TimerUtils timer = new TimerUtils();
    private boolean shouldThrow;
    private int lastSlot;

    private LivingEntity currentTarget;

    @Override
    public String getInfo() {
        return currentTarget == null ? null : currentTarget.getName().getString();
    }

    @Override
    protected void onEnable() {
        timer.reset();
        shouldThrow = false;
        lastSlot = -1;
        currentTarget = null;
    }

    @Override
    protected void onDisable() {
        shouldThrow = false;
        currentTarget = null;
        if (!nullCheck() && lastSlot != -1) {
            mc.player.getInventory().setSelectedSlot(lastSlot);
            lastSlot = -1;
        }
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;
        int slot = getThrowSlot();
        if (mc.player.getMainHandItem().is(Items.SNOWBALL)
                || mc.player.getMainHandItem().is(Items.EGG)
                || mc.player.getOffhandItem().is(Items.SNOWBALL)
                || mc.player.getOffhandItem().is(Items.EGG)
                || slot != -1
        ) {
            // 搜索并准备投掷
            if (timer.passedMillise(delay.getValue()) && canWork()) {
                double maxRange = this.maxRange.getValue();
                double maxRangeSq = maxRange * maxRange;

                List<LivingEntity> candidates = new ArrayList<>(TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                        maxRange,
                        fov.getValue().floatValue(),
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        64
                )));

                LivingEntity best = null;
                double minDstSq = Double.MAX_VALUE;
                for (LivingEntity entity : candidates) {
                    double distSq = mc.player.distanceToSqr(entity);
                    if (distSq > maxRangeSq) continue;

                    double dx = entity.getX() - mc.player.getX();
                    double dz = entity.getZ() - mc.player.getZ();
                    if (Math.sqrt(dx * dx + dz * dz) < minRange.getValue()) continue;

                    if (distSq < minDstSq) {
                        minDstSq = distSq;
                        best = entity;
                    }
                }

                currentTarget = best;
                if (currentTarget == null) return;

                Rot2f rotation = RotationUtils.calculate(getAimVec(currentTarget), false);

                RotationManager.INSTANCE.setRotations(rotation, rotationSpeed.getValue(), rotationPriority.getValue());

                HitResult hit = RaytraceUtils.raytrace(rotation, maxRange);
                if (hit.getType() != HitResult.Type.ENTITY) return;

                // 准备槽位与投掷状态
                ItemStack off = mc.player.getOffhandItem();
                ItemStack main = mc.player.getMainHandItem();
                if (!isThrowable(off) && !isThrowable(main)) {
                    lastSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(slot);
                }

                shouldThrow = true;
                timer.reset();
            } else {
                currentTarget = null;
            }
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (shouldThrow && canWork()) {
            boolean used = false;
            ItemStack off = mc.player.getOffhandItem();
            ItemStack main = mc.player.getMainHandItem();
            if (isThrowable(off)) {
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                used = true;
            } else if (isThrowable(main)) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                used = true;
            }
            if (used) {
                if (lastSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(lastSlot);
                }
                resetThrowState();
            }
        } else {
            if (lastSlot != -1) {
                mc.player.getInventory().setSelectedSlot(lastSlot);
                lastSlot = -1;
            }
            shouldThrow = false;
        }
    }

    private void resetThrowState() {
        shouldThrow = false;
        currentTarget = null;
        timer.reset();
    }

    private boolean isThrowable(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.SNOWBALL) || stack.is(Items.EGG));
    }

    private int getThrowSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getNonEquipmentItems().get(i);
            if (isThrowable(stack)) return i;
        }
        return -1;
    }

    private boolean canWork() {
        if (mc.player.isUsingItem()) {
            return false;
        }

        if (mc.gui.screen() != null) {
            return false;
        }

        if (Scaffold.INSTANCE.isEnabled() && !mc.player.onGround()) {
            return false;
        }

        KillAura killAura = KillAura.INSTANCE;
        if (killAura.isEnabled() && ((killAura.target != null && RotationUtils.getEyeDistanceToEntity(killAura.target) > killAura.aimRange.getValue()))) {
            return false;
        }

        return mc.options.keyUse.isDown() || !mc.options.keyAttack.isDown();
    }

    // 通过离散模拟计算可行的命中方向，移植自现有实现并适配 Vec3
    private Vec3 getAimVec(LivingEntity livingEntity) {
        if (mc.player == null) return livingEntity.getEyePosition();

        Vec3 shooterPosNow = mc.player.getEyePosition();
        Vec3 shooterVelNow = mc.player.getDeltaMovement();

        Vec3 targetPosNow = livingEntity.getBoundingBox().getCenter();
        Vec3 targetVelNow = livingEntity.getDeltaMovement();

        // 目标 AABB 半尺寸（包含 ~0.15 的投掷物半径冗余）
        var bb = livingEntity.getBoundingBox();
        double hx = (bb.maxX - bb.minX) * 0.5 + 0.15;
        double hy = (bb.maxY - bb.minY) * 0.5 + 0.15;
        double hz = (bb.maxZ - bb.minZ) * 0.5 + 0.15;

        // 抛物线参数（雪球/鸡蛋）
        double s = 1.5;   // 初速
        double g = 0.03;  // 重力
        double k = 0.99;  // 阻力

        boolean hasInHand = isThrowable(mc.player.getMainHandItem()) || isThrowable(mc.player.getOffhandItem());
        int fireDelay = hasInHand ? 2 : 3;

        // 只在 XZ 上外推到开火时刻；Y 维持当前高度
        Vec3 shooterPosFire = new Vec3(
                shooterPosNow.x + shooterVelNow.x * fireDelay,
                shooterPosNow.y,
                shooterPosNow.z + shooterVelNow.z * fireDelay
        );
        Vec3 targetPosFire = new Vec3(
                targetPosNow.x + targetVelNow.x * fireDelay,
                targetPosNow.y,
                targetPosNow.z + targetVelNow.z * fireDelay
        );

        Vec3 r = targetPosFire.subtract(shooterPosFire);
        Vec3 targetVelXZ = new Vec3(targetVelNow.x, 0.0, targetVelNow.z);

        Vec3 bestDir = null;
        double bestScore = Double.POSITIVE_INFINITY;

        int nMax = 80; // 搜索最大飞行 tick
        for (int n = 2; n <= nMax; n++) {
            Vec3 Rh = new Vec3(r.x + targetVelXZ.x * n, 0.0, r.z + targetVelXZ.z * n);
            double RhLen = Math.sqrt(Rh.x * Rh.x + Rh.z * Rh.z);
            if (RhLen < 1e-6) continue;

            double dirHx = Rh.x / RhLen;
            double dirHz = Rh.z / RhLen;

            double sn = geomSum(k, n);
            double tn = triGeomSum(k, n);

            double cosTheta = (RhLen / sn) / s;
            double sinTheta = (r.y + g * tn) / (s * sn);

            if (!Double.isFinite(cosTheta) || !Double.isFinite(sinTheta)) continue;
            if (cosTheta < -1 || cosTheta > 1 || sinTheta < -1 || sinTheta > 1) continue;
            double norm2 = cosTheta * cosTheta + sinTheta * sinTheta;
            if (norm2 > 1.0005) continue;

            Vec3 dir = new Vec3(dirHx * cosTheta, sinTheta, dirHz * cosTheta);
            double l = Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
            if (l < 1e-6) continue;
            dir = new Vec3(dir.x / l, dir.y / l, dir.z / l);

            double err = simulatePathAABBError(
                    shooterPosFire,
                    Vec3.ZERO,
                    dir,
                    targetPosFire,
                    targetVelXZ,
                    n, s, g, k,
                    hx, hy, hz,
                    false
            );

            double score = err + n * 0.002;
            if (score < bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            return shooterPosNow.add(bestDir);
        } else {
            double d = shooterPosNow.distanceTo(targetPosNow);
            double t = Math.max(d / Math.max(s, 1e-3), 0.05);
            Vec3 lead = targetPosNow.add(targetVelXZ.scale(t));
            return new Vec3(lead.x, lead.y + 0.5 * g * t * t, lead.z);
        }
    }

    private static double simulatePathAABBError(Vec3 shooterPos, Vec3 shooterVel, Vec3 dirUnit, Vec3 targetPos0, Vec3 targetVel0, int n, double speed, double gProj, double kProj, double hx, double hy, double hz, boolean dampedY) {
        Vec3 p = shooterPos;
        Vec3 v = new Vec3(
                dirUnit.x * speed + shooterVel.x,
                dirUnit.y * speed + shooterVel.y,
                dirUnit.z * speed + shooterVel.z
        );

        Vec3 t = targetPos0;
        double tvx = targetVel0.x, tvy = targetVel0.y, tvz = targetVel0.z;

        double minDist = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            p = p.add(v);
            v = new Vec3(v.x * kProj, v.y * kProj - gProj, v.z * kProj);

            if (dampedY) {
                tvy = tvy * 0.85 - 0.08;
            }
            t = new Vec3(t.x + tvx, t.y + tvy, t.z + tvz);

            double d = distancePointToAABB(p, t, hx, hy, hz);
            if (d < minDist) minDist = d;
        }

        return minDist;
    }

    private static double distancePointToAABB(Vec3 point, Vec3 center, double hx, double hy, double hz) {
        double dx = Math.max(0.0, Math.abs(point.x - center.x) - hx);
        double dy = Math.max(0.0, Math.abs(point.y - center.y) - hy);
        double dz = Math.max(0.0, Math.abs(point.z - center.z) - hz);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double geomSum(double k, int n) {
        if (n <= 0) return 0.0;
        if (Math.abs(1.0 - k) < 1e-9) return n;
        return (1.0 - Math.pow(k, n)) / (1.0 - k);
    }

    private static double triGeomSum(double k, int n) {
        if (n <= 1) return 0.0;
        if (Math.abs(1.0 - k) < 1e-9) return (n - 1) * (n) / 2.0;
        double oneMinusK = (1.0 - k);
        return ((n - 1) / oneMinusK) - (k * (1.0 - Math.pow(k, n - 1)) / (oneMinusK * oneMinusK));
    }

}
