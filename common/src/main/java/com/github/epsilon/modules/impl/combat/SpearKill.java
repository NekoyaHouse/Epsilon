package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SpearKill extends Module {

    public static final SpearKill INSTANCE = new SpearKill();

    private SpearKill() {
        super("Spear Kill", Category.COMBAT);
    }

    private enum LungeMode {
        DirectionBased,
        Above
    }

    private final EnumSetting<LungeMode> lungeMode = enumSetting("Lunge Mode", LungeMode.DirectionBased);
    private final DoubleSetting maxRange = doubleSetting("Max Range", 6.0, 1.0, 16.0, 0.1);
    private final DoubleSetting lungeStrength = doubleSetting("Lunge Strength", 3.7, 0.5, 15.0, 0.1);
    private final DoubleSetting aboveHeight = doubleSetting("Above Height", 10.0, 3.0, 30.0, 0.1, () -> lungeMode.is(LungeMode.Above));
    private final DoubleSetting aboveHeightDistance = doubleSetting("Above Height Distance", 3.0, 1.0, 10.0, 0.1, () -> lungeMode.is(LungeMode.Above));
    private final DoubleSetting stopDistance = doubleSetting("Stop Distance", 0.6, 0.0, 10.0, 0.1);
    private final IntSetting chargeTimeModifier = intSetting("Charge Time", 100, 0, 100, 1);
    private final BoolSetting stopOnTarget = boolSetting("Stop On Target", true);
    private final BoolSetting autoSwitch = boolSetting("Auto Switch", true);
    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting mobs = boolSetting("Mobs", false);
    private final BoolSetting animals = boolSetting("Animals", false);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting invisible = boolSetting("Invisible", true);

    public LivingEntity killtarget;
    private boolean currentlyCharging;
    private Vec3 aboveTargetPos;
    private boolean firstPhase;

    @Override
    protected void onEnable() {
        killtarget = null;
        currentlyCharging = false;
        aboveTargetPos = null;
        firstPhase = false;
    }

    @Override
    protected void onDisable() {
        killtarget = null;
        currentlyCharging = false;
        aboveTargetPos = null;
        firstPhase = false;
    }

    @EventHandler
    public void onTick(PlayerTickEvent e) {
        updateTarget();

        currentlyCharging = isUsingSpear();

        if (currentlyCharging) {
            if (killtarget == null) {
                updateTarget();
            }
            if (killtarget != null && !killtarget.isAlive()) {
                if (stopOnTarget.getValue()) {
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    mc.player.setSprinting(false);
                }
                killtarget = null;
                firstPhase = false;
                aboveTargetPos = null;
            }
            if (killtarget != null) {
                lunge();
            }
        } else {
            killtarget = null;
            firstPhase = false;
            aboveTargetPos = null;
        }
    }

    private void updateTarget() {
        if (killtarget != null && !killtarget.isAlive()) {
            killtarget = null;
        }

        if (killtarget == null || autoSwitch.getValue()) {
            double rangeValue = maxRange.getValue();
            var candidates = Managers.TARGET.acquireTargets(
                    TargetRequest.of(
                            rangeValue,
                            360.0f,
                            players.getValue(),
                            mobs.getValue(),
                            animals.getValue(),
                            villagers.getValue(),
                            invisible.getValue(),
                            64
                    )
            );

            if (!candidates.isEmpty()) {
                killtarget = candidates.getFirst();
            } else {
                killtarget = null;
            }
        }
    }

    private boolean isUsingSpear() {
        if (mc.player == null) return false;
        Item item = mc.player.getUseItem().getItem();
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.COPPER_SPEAR
                || item == Items.IRON_SPEAR || item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR
                || item == Items.NETHERITE_SPEAR;
    }

    private int getReadyTicks(Item item) {
        int value;

        if (item == Items.WOODEN_SPEAR) value = 14;
        else if (item == Items.STONE_SPEAR || item == Items.GOLDEN_SPEAR) value = 13;
        else if (item == Items.COPPER_SPEAR) value = 12;
        else if (item == Items.IRON_SPEAR) value = 11;
        else if (item == Items.DIAMOND_SPEAR) value = 9;
        else if (item == Items.NETHERITE_SPEAR) value = 7;
        else value = 10;

        return Math.round(value * (chargeTimeModifier.getValue() / 100.0f));
    }

    private void lunge() {
        if (killtarget == null) return;

        int readyTicks = getReadyTicks(mc.player.getUseItem().getItem());

        if (rotate.getValue() && killtarget != null) {
            rotateToTarget(killtarget);
        }

        int useTime = mc.player.getTicksUsingItem();
        if (useTime > readyTicks) {
            AABB playerBox = mc.player.getBoundingBox().inflate(stopDistance.getValue());
            AABB targetBox = killtarget.getBoundingBox();
            boolean atTarget = playerBox.intersects(targetBox);

            if (atTarget) {
                if (stopOnTarget.getValue()) {
                    killtarget = null;
                    mc.player.setDeltaMovement(Vec3.ZERO);
                    mc.player.setSprinting(false);
                }
                firstPhase = false;
                aboveTargetPos = null;
                return;
            }

            double lungeSpeed = lungeStrength.getValue();
            Vec3 viewDir;

            switch (lungeMode.getValue()) {
                case DirectionBased -> {
                    Vec3 targetDir = killtarget.getBoundingBox().getCenter().subtract(mc.player.position()).normalize();
                    viewDir = targetDir;

                    mc.player.setSprinting(true);
                    mc.player.setDeltaMovement(viewDir.x() * lungeSpeed, viewDir.y() * lungeSpeed, viewDir.z() * lungeSpeed);
                }
                case Above -> {
                    if (!firstPhase || aboveTargetPos == null) {
                        Vec3 targetCenter = killtarget.getBoundingBox().getCenter();
                        aboveTargetPos = new Vec3(targetCenter.x(), targetCenter.y() + aboveHeight.getValue(), targetCenter.z());
                        firstPhase = true;
                    }

                    Vec3 playerPos = mc.player.position();
                    double distToAbove = playerPos.distanceTo(aboveTargetPos);

                    if (distToAbove < aboveHeightDistance.getValue()) {
                        Vec3 targetDir = killtarget.getBoundingBox().getCenter().subtract(playerPos).normalize();
                        viewDir = targetDir;
                        firstPhase = false;
                    } else {
                        Vec3 aboveDir = aboveTargetPos.subtract(playerPos).normalize();
                        viewDir = aboveDir;
                    }

                    mc.player.setSprinting(true);
                    mc.player.setDeltaMovement(viewDir.x() * lungeSpeed, viewDir.y() * lungeSpeed, viewDir.z() * lungeSpeed);
                }
            }
        }
    }

    private void rotateToTarget(LivingEntity target) {
        if (target == null) return;
        var rotations = RotationUtils.getRotationsToEntity(target);
        mc.player.setYRot(rotations.getYaw());
        mc.player.setYHeadRot(rotations.getYaw());
        mc.player.setXRot(rotations.getPitch());
    }

}
