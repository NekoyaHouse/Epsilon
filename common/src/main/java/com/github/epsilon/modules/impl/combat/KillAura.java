package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.NoSlowdown;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.modules.impl.movement.Velocity;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.render.esp.CaptureMarkESP;
import com.github.epsilon.utils.render.esp.CircleESP;
import com.github.epsilon.utils.render.esp.DeobfESP;
import com.github.epsilon.utils.render.esp.FireflyESP;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {

    public static final KillAura INSTANCE = new KillAura();

    private KillAura() {
        super("Kill Aura", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class, event -> {
            DeobfESP.render(
                    event.getPoseStack(),
                    deobfSize.getValue().floatValue(),
                    deobfSpins.getValue().floatValue(),
                    deobfWobble.getValue().floatValue(),
                    deobfFlyHeight.getValue().floatValue()
            );
        }));
    }

    private enum Mode {
        OnePointEight,
        OnePointNinePlus
    }

    private enum TargetMode {
        Single,
        Switch
    }

    private enum PriorityMode {
        None,
        Health,
        Fov,
        Range
    }

    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly,
        Deobf
    }

    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true);
    private final BoolSetting pauseOnScaffold = boolSetting("Pause On Scaffold", true);
    private final BoolSetting hitSelect = boolSetting("Hit Select", true);
    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.OnePointEight);
    private final EnumSetting<TargetMode> targetMode = enumSetting("Target Mode", TargetMode.Single);
    private final IntSetting switchDelay = intSetting("Switch Delay", 100, 0, 500, 1, () -> targetMode.is(TargetMode.Switch));
    private final EnumSetting<PriorityMode> priorityMode = enumSetting("Priority Mode", PriorityMode.None);
    public final DoubleSetting searchRange = doubleSetting("Search Range", 4.0, 1.0, 6.0, 0.1);
    public final DoubleSetting aimRange = doubleSetting("Aim Range", 3.0, 1.0, 6.0, 0.1);
    private final IntSetting fov = intSetting("FOV", 360, 10, 360, 1);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 10, 180, 10);
    private final EnumSetting<Priority> rotationPriority = enumSetting("Rotation Priority", Priority.High);
    private final IntSetting cps = intSetting("CPS", 12, 1, 20, 1, () -> mode.is(Mode.OnePointEight));

    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting mobs = boolSetting("Mobs", true);
    private final BoolSetting animals = boolSetting("Animals", true);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting ambient = boolSetting("Ambient", false);
    private final BoolSetting water = boolSetting("Water", false);
    private final BoolSetting others = boolSetting("Others", false);
    private final BoolSetting invisible = boolSetting("Invisible", true);

    private final BoolSetting swingHand = boolSetting("SwingHand", true);
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
    private final ColorSetting sideColor = colorSetting("Side Color", Color.WHITE, false, () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 233), () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final DoubleSetting circleRadius = doubleSetting("Circle Radius", 0.75, 0.1, 2.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final DoubleSetting circleAlphaFactor = doubleSetting("Circle Alpha Factor", 1.0, 0.0, 2.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Circle));
    private final EnumSetting<FireflyESP.ColorMode> fireflyColorMode = enumSetting("Firefly Color Mode", FireflyESP.ColorMode.Blend, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final ColorSetting fireflyColor = colorSetting("Firefly Color", new Color(149, 149, 149, 255), () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final ColorSetting fireflyColor2 = colorSetting("Firefly Color 2", new Color(255, 133, 161, 255), () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyColorMix = doubleSetting("Firefly Color Mix", 0.65, 0.0, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyColorSpeed = doubleSetting("Firefly Color Speed", 1.2, 0.1, 6.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Blend));
    private final DoubleSetting fireflyRainbowSpeed = doubleSetting("Firefly Rainbow Speed", 1.0, 0.1, 6.0, 0.1, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final DoubleSetting fireflyRainbowSaturation = doubleSetting("Firefly Rainbow Saturation", 0.85, 0.1, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final DoubleSetting fireflyRainbowBrightness = doubleSetting("Firefly Rainbow Brightness", 1.0, 0.1, 1.0, 0.05, () -> esp.getValue() && espMode.is(ESPMode.Firefly) && fireflyColorMode.is(FireflyESP.ColorMode.Rainbow));
    private final IntSetting fireflyLength = intSetting("Firefly Length", 14, 8, 128, 1, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final IntSetting fireflyFactor = intSetting("Firefly Factor", 8, 1, 10, 1, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final DoubleSetting fireflyShaking = doubleSetting("Firefly Shaking", 1.8, 0.25, 10.0, 0.25, () -> esp.getValue() && espMode.is(ESPMode.Firefly));
    private final DoubleSetting fireflyAmplitude = doubleSetting("Firefly Amplitude", 3.0, 0.0, 10.0, 0.25, () -> esp.getValue() && espMode.is(ESPMode.Firefly));

    public LivingEntity target;
    private List<LivingEntity> targets;
    private int targetIndex;

    private int attacks;
    private long lastAttackTime;

    private final TimerUtils switchTimer = new TimerUtils();

    @Override
    public String getInfo() {
        return target == null ? null : target.getName().getString();
    }

    @Override
    protected void onDisable() {
        resetState();
        DeobfESP.retainRisingEffects();
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        if (!esp.getValue() || !espMode.is(ESPMode.Deobf)) {
            DeobfESP.clear();
        }

        if (pauseOnScaffold.getValue() && Scaffold.INSTANCE.isEnabled()) {
            resetState();
            return;
        }

        targets = new ArrayList<>(TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                searchRange.getValue(),
                fov.getValue().floatValue(),
                players.getValue(),
                mobs.getValue(),
                animals.getValue(),
                villagers.getValue(),
                ambient.getValue(),
                water.getValue(),
                others.getValue(),
                invisible.getValue(),
                64
        )));

        Velocity velocity = Velocity.INSTANCE;

        if (velocity.delay) {
            targets.sort(
                    Comparator.comparingDouble(o -> (double) Math.abs(velocity.yaw - RotationUtils.calculate(o).getYaw()))
            );
        }

        switch (targetMode.getValue()) {
            case Single -> targetIndex = 0;
            case Switch -> {
                if (switchTimer.passedMillise(switchDelay.getValue())) {
                    switchTimer.reset();
                    if (++targetIndex >= targets.size()) {
                        targetIndex = 0;
                    }
                }
            }
        }

        if (targetIndex >= targets.size()) {
            targetIndex = 0;
        }

        if (targets.isEmpty()) {
            target = null;
            return;
        }

        switch (priorityMode.getValue()) {
            case Range -> targets.sort(Comparator.comparingDouble(o -> (double) o.distanceTo(mc.player)));
            case Fov -> {
                targets.sort(Comparator.comparingDouble(o -> (double) Math.abs(Mth.wrapDegrees(mc.player.getXRot() - RotationUtils.calculate(o).getYaw()))));
            }
            case Health -> {
                targets.sort(Comparator.comparingDouble(o -> o instanceof LivingEntity living ? (double) living.getHealth() : 0.0));
            }
        }

        target = targets.get(targetIndex);

        Rot2f calculate = RotationUtils.calculate(target, true, aimRange.getValue());
        if (RaytraceUtils.raytrace(calculate, aimRange.getValue()).getType() == HitResult.Type.BLOCK) return;
        RotationManager.INSTANCE.setRotations(calculate, rotationSpeed.getValue(), rotation -> RaytraceUtils.raytrace(rotation, 3.0f) instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() == target, rotationPriority.getValue());

        HitResult hitResult = RotationManager.INSTANCE.getHitResult();
        if (hitSelect.getValue() && hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player player && !AntiBot.INSTANCE.isBot(player) && !TargetManager.INSTANCE.isSameTeam(player) && velocity.attackQueue <= 0) {
            ClientPacketListener connection = mc.getConnection();
            PlayerInfo localPlayerInfo = connection == null ? null : connection.getPlayerInfo(mc.player.getUUID());
            int latencyTicks = localPlayerInfo == null ? 0 : localPlayerInfo.getLatency() / 50;
            if (player.hurtTime <= latencyTicks + 1 || (mc.player.hurtTime >= 6 && !Velocity.INSTANCE.isEnabled()) || Criticals.INSTANCE.fallTicks == 2) {
                switch (mode.getValue()) {
                    case OnePointNinePlus -> {
                        if (attacks == 0 && mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                            attacks++;
                        }
                    }
                    case OnePointEight -> {
                        long time = System.currentTimeMillis();
                        if (time - lastAttackTime >= (long) (1000.0 / cps.getValue())) {
                            attacks++;
                            lastAttackTime = time;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        HitResult hitResult = RotationManager.INSTANCE.getHitResult();
        while (attacks > 0) {
            attacks--;
            if (pauseOnEat.getValue() && PlayerUtils.isEating() || NoSlowdown.INSTANCE.isWorking()) return;
            if (hitResult instanceof EntityHitResult entityHitResult) {
                Entity entity = entityHitResult.getEntity();
                if (!entity.isAlive()) return;

                mc.gameMode.attack(mc.player, entity);

                if (espMode.is(ESPMode.Deobf)) DeobfESP.markHit(entity);

                if (swingHand.getValue()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else {
                    mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (target != null && Velocity.INSTANCE.attackQueue <= 0) {
            HitResult hitResult = RotationManager.INSTANCE.getHitResult();
            if (!hitSelect.getValue() || !(hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player)) {
                switch (mode.getValue()) {
                    case OnePointNinePlus -> {
                        if (attacks == 0 && mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                            attacks++;
                        }
                    }
                    case OnePointEight -> {
                        long time = System.currentTimeMillis();
                        if (time - lastAttackTime >= (long) (1000.0 / cps.getValue())) {
                            attacks++;
                            lastAttackTime = time;
                        }
                    }
                }
            }
        }

        if (!esp.getValue() || espMode.is(ESPMode.Deobf) || target == null) return;

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

    private void resetState() {
        targets = null;
        target = null;
        attacks = 0;
        lastAttackTime = 0L;
    }

}
