package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    private enum Mode {
        Cancel,
        Legit,
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel);
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));

    private final SettingGroup sgExclusions = settingGroup("Exclusions");

    private final BoolSetting excludeSpearLunge = boolSetting("Exclude Spear Lunge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);
    private final BoolSetting excludeWindCharge = boolSetting("Exclude Wind Charge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);

    private final TimerUtils windChargeTimer = new TimerUtils();

    private boolean jump;

    @Override
    protected void onEnable() {
        jump = false;
        windChargeTimer.reset();
    }

    @Override
    protected void onDisable() {
        jump = false;
        windChargeTimer.reset();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (excludeWindCharge.getValue() && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            ItemStack stack = mc.player.getItemInHand(packet.getHand());
            if (stack.getItem() instanceof WindChargeItem) {
                windChargeTimer.reset();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (nullCheck()) return;

                if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    if (!shouldExcludeMotion(packet)) {
                        event.cancel();
                    }
                    return;
                }

                if (
                        explosion.getValue() && event.getPacket() instanceof ClientboundExplodePacket packet
                                && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())
                ) {
                    if (shouldExcludeExplosion(packet)) {
                        return;
                    }
                    event.setPacket(new ClientboundExplodePacket(
                            packet.center(),
                            packet.radius(),
                            packet.blockCount(),
                            Optional.empty(),
                            packet.explosionParticle(),
                            packet.explosionSound(),
                            packet.blockParticles()
                    ));
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    jump = true;
                }
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (jump) {
            if (mc.player.onGround() && mc.player.isMoving()) {
                mc.player.input.makeJump();
            }
            jump = false;
        }
    }

    private boolean shouldExcludeMotion(ClientboundSetEntityMotionPacket packet) {
        return excludeSpearLunge.getValue() && isSpearLungeMotion(packet);
    }

    private boolean shouldExcludeExplosion(ClientboundExplodePacket packet) {
        return excludeWindCharge.getValue() && isWindChargeExplosion(packet);
    }

    private boolean isSpearLungeMotion(ClientboundSetEntityMotionPacket packet) {
        if (!isSpearWithLunge(mc.player.getMainHandItem())) return false;
        if (!mc.options.keyAttack.isDown()) return false;

        Vec3 vel = packet.movement();
        double horiz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (horiz < 0.15) return false;

        Vec3 look = mc.player.getLookAngle();
        double dot = vel.x * look.x + vel.z * look.z;
        return dot > 0;
    }

    private boolean isWindChargeExplosion(ClientboundExplodePacket packet) {
        if (windChargeTimer.passedMillise(3000)) return false;

        double dist = packet.center().distanceTo(mc.player.position());
        if (dist > 12.0) return false;

        if (packet.radius() > 3.0f) return false;

        return packet.playerKnockback().isPresent();
    }

    private boolean isSpearWithLunge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.PIERCING_WEAPON)
                && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.LUNGE) > 0;
    }

}
