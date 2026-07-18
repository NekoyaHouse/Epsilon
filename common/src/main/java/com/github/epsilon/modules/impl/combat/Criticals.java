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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Criticals extends Module {

    public static final Criticals INSTANCE = new Criticals();

    private Criticals() {
        super("Criticals", Category.COMBAT);
    }

    private enum PacketMode {
        Pure,
        Legit
    }

    private final EnumSetting<PacketMode> packetMode = enumSetting("Packet Mode", PacketMode.Pure);
    private final BoolSetting groundOnly = boolSetting("GroundO nly", false, () -> packetMode.is(PacketMode.Pure));
    private final BoolSetting alwaysShowCritParticles = boolSetting("Show Crit Particles", true, () -> packetMode.is(PacketMode.Legit));

    private boolean shouldShowCritParticles = false;
    private Entity lastAttackedEntity = null;

    @Override
    public String getInfo() {
        return packetMode.getTranslatedValue();
    }

    @Override
    protected void onEnable() {
        shouldShowCritParticles = false;
        lastAttackedEntity = null;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        if (packetMode.is(PacketMode.Pure)) {
            if (!isCriticalHitAvailable() || (groundOnly.getValue() && !mc.player.onGround())) {
                return;
            }
            AABB box = mc.player.getBoundingBox().move(0.0, 0.0625, 0.0);
            if (!mc.level.getBlockCollisions(mc.player, box).iterator().hasNext()) {
                doPacketCriticals();
            }
        } else if (packetMode.is("Legit")) {
            if (alwaysShowCritParticles.getValue()) {
                lastAttackedEntity = event.getEntity();
                shouldShowCritParticles = true;
            }
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (packetMode.is(PacketMode.Legit)) {
            if (shouldShowCritParticles && lastAttackedEntity != null) {
                mc.player.crit(lastAttackedEntity);
                shouldShowCritParticles = false;
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (packetMode.is(PacketMode.Legit)) {
            KillAura killAura = KillAura.INSTANCE;
            if (killAura.isEnabled() && killAura.target != null) {
                if (mc.player.fallDistance > 0) {
                    double dist = mc.player.distanceTo(killAura.target);
                    if (dist <= killAura.aimRange.getValue().doubleValue() + 0.3) {
                        event.setSprint(false);
                        mc.player.setSprinting(false);
                        mc.options.keySprint.setDown(false);
                    }
                }
            }
        }
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
