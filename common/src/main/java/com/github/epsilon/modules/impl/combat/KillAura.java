package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.network.PacketUtils;
import com.github.epsilon.utils.render.esp.CaptureMarkESP;
import com.github.epsilon.utils.render.esp.CircleESP;
import com.github.epsilon.utils.render.esp.DeobfESP;
import com.github.epsilon.utils.render.esp.FireflyESP;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import love.slmpc.ClInitNative;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ClInitNative
public class KillAura extends Module {

    public static final KillAura INSTANCE = new KillAura();

    private KillAura() {
        super("Kill Aura", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class, event -> DeobfESP.render(
                event.getPoseStack(),
                deobfSize.getValue().floatValue(),
                deobfSpins.getValue().floatValue(),
                deobfWobble.getValue().floatValue(),
                deobfFlyHeight.getValue().floatValue()
        )));
    }

    private enum Mode {
        OnePointEight,
        OnePointNinePlus
    }

    private enum TargetMode {
        Single,
        Switch
    }

    private enum AutoBlockMode {
        None,
        Fake,
        Legit,
        Blink1
    }

    private enum PriorityMode {
        Health,
        Fov,
        Range,
        None
    }

    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly,
        Deobf
    }

    private final BoolSetting player = boolSetting("Player", true);
    private final BoolSetting mob = boolSetting("Mob", true);
    private final BoolSetting animal = boolSetting("Animal", true);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting ambient = boolSetting("Ambient", false);
    private final BoolSetting water = boolSetting("Water", false);
    private final BoolSetting others = boolSetting("Others", false);
    private final BoolSetting invisible = boolSetting("Invisible", true);

    private final BoolSetting throughWalls = boolSetting("Through Walls", false);
    private final BoolSetting hitSelect = boolSetting("Hit Select", true);
    private final BoolSetting swingHand = boolSetting("SwingHand", true);
    private final DoubleSetting attackRange = doubleSetting("Attack Range", 4.0, 3.0, 6.0, 0.01);
    public final DoubleSetting aimRange = doubleSetting("Aim Range", 3.0, 3.0, 6.0, 0.01);
    private final IntSetting fov = intSetting("FOV", 180, 1, 360, 1);
    private final DoubleSetting rotationSpeed = doubleSetting("Rotation Speed", 1.3, 1.0, 10.0, 0.5);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.OnePointEight, _ -> attacks = 0.0);
    private final IntSetting cps = intSetting("CPS", 12, 1, 20, 1, () -> mode.is(Mode.OnePointEight));

    private final EnumSetting<TargetMode> targetMode = enumSetting("Target Mode", TargetMode.Single);
    private final IntSetting switchDelay = intSetting("Switch Delay", 100, 0, 500, 1, () -> targetMode.is(TargetMode.Switch));

    private final EnumSetting<AutoBlockMode> abMode = enumSetting("Auto Block Mode", AutoBlockMode.None, () -> mode.is(Mode.OnePointEight));
    private final IntSetting blinkTicks = intSetting("Blink Ticks", 3, 1, 5, 1, () -> mode.is(Mode.OnePointEight) && abMode.is(AutoBlockMode.Blink1));

    private final EnumSetting<PriorityMode> priority = enumSetting("Priority", PriorityMode.Range);

    private final BoolSetting esp = boolSetting("ESP", true);
    private final EnumSetting<ESPMode> espMode = enumSetting("ESP Mode", ESPMode.Circle, esp::getValue);
    public final EnumSetting<DeobfESP.TextureMode> deobfMode = enumSetting("Deobf Mode", DeobfESP.TextureMode.Mengcha, () -> esp.getValue() && espMode.is(ESPMode.Deobf));
    private final DoubleSetting deobfSize = doubleSetting("Deobf Size", 0.75, 0.25, 2.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Deobf));
    private final DoubleSetting deobfSpins = doubleSetting("Deobf Spins", 3.0, 0.5, 8.0, 0.25, () -> esp.getValue() && espMode.is(ESPMode.Deobf));
    private final DoubleSetting deobfWobble = doubleSetting("Deobf Wobble", 1.0, 0.0, 2.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.Deobf));
    private final DoubleSetting deobfFlyHeight = doubleSetting("Deobf Fly Height", 5.0, 1.0, 12.0, 0.5, () -> esp.getValue() && espMode.is(ESPMode.Deobf));
    private final ColorSetting espColor1 = colorSetting("ESP Main", new Color(255, 183, 197), () -> esp.getValue() && espMode.is(ESPMode.CaptureMark));
    private final ColorSetting espColor2 = colorSetting("ESP Second", new Color(255, 133, 161), () -> esp.getValue() && espMode.is(ESPMode.CaptureMark));
    private final DoubleSetting espSize = doubleSetting("ESP Size", 1.2, 0.5, 3.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.CaptureMark));
    private final DoubleSetting espRotSpeed = doubleSetting("Rot Speed", 2.0, 0.5, 10.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.CaptureMark));
    private final DoubleSetting waveSpeed = doubleSetting("Wave Speed", 3.0, 0.5, 10.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.CaptureMark));
    private final ColorSetting sideColor = colorSetting("Side Color", Color.WHITE, false);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 233), () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final DoubleSetting circleRadius = doubleSetting("Circle Radius", 0.75, 0.1, 2.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final DoubleSetting circleAlphaFactor = doubleSetting("Circle Alpha Factor", 1.0, 0.0, 2.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final ColorSetting fireflyColor = colorSetting("Firefly Color", new Color(149, 149, 149, 255), false, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final EnumSetting<FireflyESP.ColorMode> fireflyColorMode = enumSetting("Firefly Color Mode", FireflyESP.ColorMode.Blend, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final ColorSetting fireflyColor2 = colorSetting("Firefly Color 2", new Color(255, 133, 161, 255), false, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyColorMix = doubleSetting("Firefly Color Mix", 0.65, 0.0, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyColorSpeed = doubleSetting("Firefly Color Speed", 1.2, 0.1, 6.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyRainbowSpeed = doubleSetting("Firefly Rainbow Speed", 1.0, 0.1, 6.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final DoubleSetting fireflyRainbowSaturation = doubleSetting("Firefly Rainbow Saturation", 0.85, 0.1, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final DoubleSetting fireflyRainbowBrightness = doubleSetting("Firefly Rainbow Brightness", 1.0, 0.1, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final IntSetting fireflyLength = intSetting("Firefly Length", 14, 8, 128, 1, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final IntSetting fireflyFactor = intSetting("Firefly Factor", 8, 1, 10, 1, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final DoubleSetting fireflyShaking = doubleSetting("Firefly Shaking", 1.8, 0.25, 10.0, 0.25, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final DoubleSetting fireflyAmplitude = doubleSetting("Firefly Amplitude", 3.0, 0.0, 10.0, 0.25, () -> esp.getValue() && espMode.is(ESPMode.Firefly));

    public static List<LivingEntity> targets;
    public static int targetNumber;
    public LivingEntity target;
    private double attacks;
    private boolean blinking;
    private final TimerUtils switchTimer = new TimerUtils();
    private final Queue<Packet<ServerGamePacketListener>> packets = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        attacks = 0.0;
        switchTimer.reset();
    }

    @Override
    public void onDisable() {
        DeobfESP.retainRisingEffects();
        targets = null;
        targetNumber = 0;
        target = null;
        attacks = 0.0;
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getInventory().getSelectedItem().is(ItemTags.SWORDS)) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
        blinking = false;
        flush();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        if (event.getPacket() instanceof ClientboundDisconnectPacket) {
            blinking = false;
            packets.clear();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();

        if (blinking) {
            if (packet instanceof ServerboundMovePlayerPacket) {
                int c03Counts = 0;
                for (Packet<ServerGamePacketListener> p : packets) {
                    if (p instanceof ServerboundMovePlayerPacket) {
                        c03Counts++;
                    }
                }
                if (c03Counts >= blinkTicks.getValue()) {
                    mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, Managers.ROTATION.getRotation().getYaw(), Managers.ROTATION.getRotation().getPitch()));
                    flush();
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                }
            }
            if (packet.type().flow() == PacketFlow.SERVERBOUND) {
                packets.add((Packet<ServerGamePacketListener>) packet);
                event.cancel();
            }
        } else {
            flush();
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (!esp.getValue() || !espMode.is(ESPMode.Deobf)) {
            DeobfESP.clear();
        }

        if (Scaffold.INSTANCE.isEnabled()) return;

        targets = new ArrayList<>(Managers.TARGET.acquireTargets(TargetRequest.of(
                aimRange.getValue(),
                fov.getValue().floatValue(),
                player.getValue(),
                mob.getValue(),
                animal.getValue(),
                villagers.getValue(),
                ambient.getValue(),
                water.getValue(),
                others.getValue(),
                invisible.getValue(),
                throughWalls.getValue(),
                64
        )));

        blinking = false;
        if (targets.isEmpty()) {
            target = null;
            attacks = 0.0;
            return;
        }

        if (mode.is(Mode.OnePointEight) && abMode.is(AutoBlockMode.Blink1) && mc.player.getInventory().getSelectedItem().is(ItemTags.SWORDS)) {
            blinking = true;
        }

        if (this.priority.is(PriorityMode.Range)) {
            targets.sort(Comparator.comparingDouble(o -> (double) o.distanceTo(mc.player)));
        } else if (this.priority.is(PriorityMode.Fov)) {
            targets.sort(
                    Comparator.comparingDouble(o -> (double) Math.abs(Mth.wrapDegrees(mc.player.getXRot() - RotationUtils.calculate(o).getYaw())))
            );
        } else if (this.priority.is(PriorityMode.Health)) {
            targets.sort(Comparator.comparingDouble(o -> o instanceof LivingEntity living ? (double) living.getHealth() : 0.0));
        }

        switch (targetMode.getValue()) {
            case Single -> targetNumber = 0;
            case Switch -> {
                if (switchTimer.passedMillise(switchDelay.getValue())) {
                    switchTimer.reset();
                    if (++targetNumber >= targets.size()) {
                        targetNumber = 0;
                    }
                }
            }
        }

        if (targetNumber < 0 || targetNumber >= targets.size()) {
            targetNumber = 0;
        }

        target = targets.get(targetNumber);

        if (throughWalls.getValue()) {
            Managers.ROTATION.setRotations(RotationUtils.calculate(target), rotationSpeed.getValue());
        } else {
            Managers.ROTATION.setRotations(
                    RotationUtils.calculate(target),
                    rotationSpeed.getValue(),
                    rotation -> RaytraceUtils.raytrace(rotation, attackRange.getValue()) instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() == target
            );
        }

        if (mode.is(Mode.OnePointEight)) {
            double attacksPerTick = cps.getValue() / 20.0;
            attacks = Math.min(1.0 + attacksPerTick, attacks + attacksPerTick);
        } else {
            attacks = mc.player.getAttackStrengthScale(0.5F) >= 1.0F ? 1.0 : 0.0;
        }

        if (mode.is(Mode.OnePointEight) && abMode.is(AutoBlockMode.Legit) && mc.player.getInventory().getSelectedItem().is(ItemTags.SWORDS) && targets != null && !targets.isEmpty()) {
            if (mc.player.hurtTime == 7) {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            } else if (mc.player.hurtTime == 1) {
                mc.gameMode.releaseUsingItem(mc.player);
                return;
            }
            if (mc.player.isUsingItem()) return;
        }

        if (!(mode.is(Mode.OnePointEight) && abMode.is(AutoBlockMode.Legit)) || mc.player.hurtTime != 1) {
            boolean hitSelectReady = true;
            if (hitSelect.getValue() && mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player player) {
                PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                int latencyTicks = playerInfo == null ? 0 : playerInfo.getLatency() / 50;
                hitSelectReady = player.hurtTime <= latencyTicks + 1 || mc.player.hurtTime >= 7;
            }

            if (attacks >= 1.0 && hitSelectReady) {
                attacks -= 1.0;
                if (mc.player.isUsingItem() && !(mode.is(Mode.OnePointEight) && abMode.is(AutoBlockMode.Blink1))) {
                    return;
                }

                if (throughWalls.getValue()) {
                    if (target.distanceTo(mc.player) <= attackRange.getValue()) {
                        doAttack();
                    }
                } else if (RaytraceUtils.raytrace(Managers.ROTATION.getRotation(), attackRange.getValue()) instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() == target) {
                    doAttack();
                }
            }
        }
    }

    private void doAttack() {
        mc.gameMode.attack(mc.player, target);
        if (esp.getValue() && espMode.is(ESPMode.Deobf)) {
            DeobfESP.markHit(target);
        }
        if (swingHand.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (targets != null && !targets.isEmpty()) {
            if (esp.getValue()) {
                LivingEntity target = targets.get(targetNumber);
                PoseStack stack = event.getPoseStack();
                switch (espMode.getValue()) {
                    case CaptureMark -> {
                        CaptureMarkESP.render(
                                stack,
                                target,
                                espSize.getValue(),
                                espRotSpeed.getValue(),
                                waveSpeed.getValue(),
                                espColor1.getValue(),
                                espColor2.getValue()
                        );
                    }
                    case Circle -> {
                        CircleESP.render(
                                stack,
                                target,
                                circleRadius.getValue().floatValue(),
                                sideColor.getValue(),
                                lineColor.getValue(),
                                circleAlphaFactor.getValue().floatValue()
                        );
                    }
                    case Firefly -> {
                        FireflyESP.render(
                                stack,
                                target,
                                fireflyLength.getValue(),
                                fireflyFactor.getValue(),
                                fireflyShaking.getValue(),
                                fireflyAmplitude.getValue(),
                                fireflyColor.getValue(),
                                fireflyColorMode.getValue(),
                                fireflyColor2.getValue(),
                                fireflyColorMix.getValue(),
                                fireflyColorSpeed.getValue(),
                                fireflyRainbowSpeed.getValue(),
                                fireflyRainbowSaturation.getValue(),
                                fireflyRainbowBrightness.getValue()
                        );
                    }
                }
            }
        }
    }

    public void flush() {
        while (!packets.isEmpty()) {
            packets.removeIf(packet -> {
                PacketUtils.sendSilently(packet);
                return true;
            });
        }
    }

    public boolean isBlocking() {
        return mode.is(Mode.OnePointEight) && !abMode.is(AutoBlockMode.None) && targets != null && !targets.isEmpty() && mc.player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS);
    }

    public LivingEntity getTarget() {
        return target;
    }

}
