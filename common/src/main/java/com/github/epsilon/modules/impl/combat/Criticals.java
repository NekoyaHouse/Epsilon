package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Criticals extends Module {


    public static final Criticals INSTANCE = new Criticals();

    private Criticals() {
        super("Criticals", Category.COMBAT);
    }

    private enum Mode {
        Pure,
        Legit,
        New
    }

    private final EnumSetting<Mode> mode = enumSetting("Packet Mode", Mode.Legit);
    private final BoolSetting groundOnly = boolSetting("GroundOnly", false, () -> mode.is(Mode.Pure));
    private final BoolSetting alwaysShowCritParticles = boolSetting("Show Crit Particles", true, () -> mode.is(Mode.Legit));
    private final BoolSetting debugDamage = boolSetting("Debug Damage", false, () -> mode.is(Mode.New));

    private static final int NEW_SEQUENCE_TIMEOUT_TICKS = 20;

    private enum NewSequenceStage {
        IDLE,
        OVERWRITE,
        WAIT_FOR_HURT_RESET
    }

    private boolean shouldShowCritParticles = false;
    private Entity lastAttackedEntity = null;
    private NewSequenceStage newSequenceStage = NewSequenceStage.IDLE;
    private LivingEntity newSequenceTarget;
    private int newSequenceTicks;

    @Override
    public String getInfo() {
        return mode.getTranslatedValue();
    }

    @Override
    protected void onEnable() {
        shouldShowCritParticles = false;
        lastAttackedEntity = null;
        resetNewSequence();
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        if (mode.is(Mode.Pure)) {
            if (!isCriticalHitAvailable() || (groundOnly.getValue() && !mc.player.onGround())) {
                return;
            }
            AABB box = mc.player.getBoundingBox().move(0.0, 0.0625, 0.0);
            if (!mc.level.getBlockCollisions(mc.player, box).iterator().hasNext()) {
                doPacketCriticals();
            }
        } else if (mode.is(Mode.Legit)) {
            if (alwaysShowCritParticles.getValue()) {
                lastAttackedEntity = event.getEntity();
                shouldShowCritParticles = true;
            }
        } else if (mode.is(Mode.New)) {
            armNewSequence(event.getEntity());
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mode.is(Mode.Legit)) {
            if (shouldShowCritParticles && lastAttackedEntity != null) {
                mc.player.crit(lastAttackedEntity);
                shouldShowCritParticles = false;
            }
        } else if (mode.is(Mode.New)) {
            processNewSequence();
        } else {
            resetNewSequence();
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.New) && newSequenceStage != NewSequenceStage.IDLE) {
            event.setSprint(false);
            stopSprintingForNewSequence();
        } else if (mode.is(Mode.Legit)) {
            KillAura killAura = KillAura.INSTANCE;
            if (killAura.isEnabled() && killAura.target != null && mc.player.fallDistance > 0) {
                double dist = mc.player.distanceTo(killAura.target);
                if (dist <= killAura.aimRange.getValue() + 0.3) {
                    event.setSprint(false);
                    mc.player.setSprinting(false);
                    mc.options.keySprint.setDown(false);
                }
            }
        }
    }

    private void armNewSequence(Entity entity) {
        if (nullCheck()
                || newSequenceStage != NewSequenceStage.IDLE
                || !(entity instanceof LivingEntity target)
                || !target.isAlive()
                || target.hurtTime != 0) {
            return;
        }


        KillAura killAura = KillAura.INSTANCE;
        if (!killAura.isEnabled()
                || killAura.target != target
                || !mc.player.isSprinting()
                || !isNewAirborneEnvironment(target)) {
            return;
        }

        newSequenceTarget = target;
        newSequenceStage = NewSequenceStage.OVERWRITE;
        newSequenceTicks = 0;
    }

    private void processNewSequence() {
        if (nullCheck() || newSequenceStage == NewSequenceStage.IDLE) {
            return;
        }

        if (++newSequenceTicks > NEW_SEQUENCE_TIMEOUT_TICKS
                || newSequenceTarget == null
                || !newSequenceTarget.isAlive()
                || KillAura.INSTANCE.target != newSequenceTarget) {
            resetNewSequence();
            return;
        }

        if (newSequenceStage == NewSequenceStage.OVERWRITE) {
            stopSprintingForNewSequence();

            if (newSequenceTarget.hurtTime <= 0 && newSequenceTicks > 3) {
                resetNewSequence();
                return;
            }

            if (!isNewCriticalEnvironment(newSequenceTarget)) {
                return;
            }

            sendNewAttack(newSequenceTarget);
            newSequenceStage = NewSequenceStage.WAIT_FOR_HURT_RESET;
            newSequenceTicks = 0;
            return;
        }


        if (newSequenceTarget.hurtTime <= 0
                && isNewCriticalEnvironment(newSequenceTarget)) {
            sendNewAttack(newSequenceTarget);
            resetNewSequence();
        }
    }

    private boolean isNewAirborneEnvironment(LivingEntity target) {
        return mc.player.fallDistance > 0.0F
                && !mc.player.onGround()
                && !mc.player.onClimbable()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isPassenger()
                && target.isAlive();
    }


    private boolean isNewCriticalEnvironment(LivingEntity target) {
        return isNewAirborneEnvironment(target) && !mc.player.isSprinting();
    }

    private void stopSprintingForNewSequence() {
        mc.options.keySprint.setDown(false);
        mc.player.setSprinting(false);
    }

    private void sendNewAttack(LivingEntity target) {
        stopSprintingForNewSequence();
        mc.getConnection().send(new ServerboundPlayerCommandPacket(
                mc.player,
                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING
        ));
        mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    public boolean isDamageDebugEnabled() {
        return mode.is(Mode.New) && debugDamage.getValue();
    }

    private void resetNewSequence() {
        newSequenceStage = NewSequenceStage.IDLE;
        newSequenceTarget = null;
        newSequenceTicks = 0;
    }

    private void doPacketCriticals() {
        Vec3 pos = mc.player.position();
        boolean ground = mc.player.onGround();

        mc.player.setPos(pos.add(0.0, 0.0625, 0.0));
        mc.player.setOnGround(false);
        mc.player.sendPosition();

        mc.player.setPos(pos.add(0.0, 0.00125, 0.0));
        mc.player.setOnGround(false);
        mc.player.sendPosition();

        mc.player.setPos(pos);
        mc.player.setOnGround(ground);
    }

    private boolean isCriticalHitAvailable() {
        return mc.player.onGround() &&
                !mc.player.isInWater() &&
                !mc.player.isInLava() &&
                !mc.player.onClimbable() &&
                !mc.player.hasEffect(MobEffects.BLINDNESS) &&
                !mc.player.isPassenger();
    }

}
