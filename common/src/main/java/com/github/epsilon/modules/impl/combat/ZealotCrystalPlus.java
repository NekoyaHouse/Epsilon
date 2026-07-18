package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.WorldToScreen;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ZealotCrystalPlus extends Module {

    public static final ZealotCrystalPlus INSTANCE = new ZealotCrystalPlus();

    private ZealotCrystalPlus() {
        super("Zealot Crystal+", Category.COMBAT);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgForcePlace = settingGroup("Force Place");
    private final SettingGroup sgCalculation = settingGroup("Calculation");
    private final SettingGroup sgPlace = settingGroup("Place");
    private final SettingGroup sgBreak = settingGroup("Break");
    private final SettingGroup sgRender = settingGroup("Render");

    // General
    private final BoolSetting players = boolSetting("Players", true).group(sgGeneral);
    private final BoolSetting mobs = boolSetting("Mobs", false).group(sgGeneral);
    private final BoolSetting animals = boolSetting("Animals", false).group(sgGeneral);
    private final IntSetting maxTargets = intSetting("Max Targets", 4, 1, 10, 1).group(sgGeneral);
    private final DoubleSetting targetRange = doubleSetting("Target Range", 16.0, 0.0, 32.0, 0.5).group(sgGeneral);
    private final DoubleSetting yawSpeed = doubleSetting("Yaw Speed", 45.0, 5.0, 180.0, 5.0).group(sgGeneral);
    private final DoubleSetting placeRotationRange = doubleSetting("Place Rotation Range", 0.0, 0.0, 180.0, 5.0).group(sgGeneral);
    private final DoubleSetting breakRotationRange = doubleSetting("Break Rotation Range", 90.0, 0.0, 180.0, 5.0).group(sgGeneral);
    private final BoolSetting eatingPause = boolSetting("Eating Pause", false).group(sgGeneral);
    private final IntSetting updateDelay = intSetting("Update Delay", 5, 0, 250, 1).group(sgGeneral);
    private final IntSetting globalDelay = intSetting("Global Delay", 1_000_000, 1_000, 10_000_000, 1_000).group(sgGeneral);

    // Force Place
    private final DoubleSetting forcePlaceHealth = doubleSetting("Force Place Health", 8.0, 0.0, 20.0, 0.5).group(sgForcePlace);
    private final IntSetting forcePlaceArmorRate = intSetting("Force Place Armor Rate", 3, 0, 25, 1).group(sgForcePlace);
    private final DoubleSetting forcePlaceMinDamage = doubleSetting("Force Place Min Damage", 1.5, 0.0, 10.0, 0.25).group(sgForcePlace);
    private final DoubleSetting forcePlaceMotion = doubleSetting("Force Place Motion", 4.0, 0.0, 10.0, 0.25).group(sgForcePlace);
    private final DoubleSetting forcePlaceBalance = doubleSetting("Force Place Balance", -1.0, -10.0, 10.0, 0.25).group(sgForcePlace);
    private final BoolSetting forcePlaceWhileSwording = boolSetting("Force Place While Swording", false).group(sgForcePlace);

    // Calculation
    private final BoolSetting assumeInstantMine = boolSetting("Assume Instant Mine", true).group(sgCalculation);
    private final DoubleSetting noSuicide = doubleSetting("No Suicide", 2.0, 0.0, 20.0, 0.25).group(sgCalculation);
    private final DoubleSetting wallRange = doubleSetting("Wall Range", 3.0, 0.0, 8.0, 0.1).group(sgCalculation);
    private final BoolSetting motionPredict = boolSetting("Motion Predict", true).group(sgCalculation);
    private final IntSetting predictTicks = intSetting("Predict Ticks", 8, 0, 20, 1, motionPredict::getValue).group(sgCalculation);
    private final EnumSetting<DamagePriority> damagePriority = enumSetting("Damage Priority", DamagePriority.Efficient).group(sgCalculation);
    private final EnumSetting<DamageUtils.ArmorEnchantmentMode> armorMode = enumSetting("Armor Mode", DamageUtils.ArmorEnchantmentMode.None).group(sgCalculation);
    private final BoolSetting lethalOverride = boolSetting("Lethal Override", true).group(sgCalculation);
    private final DoubleSetting lethalThresholdAddition = doubleSetting("Lethal Threshold Addition", 0.5, -5.0, 5.0, 0.1, lethalOverride::getValue).group(sgCalculation);
    private final DoubleSetting lethalMaxSelfDamage = doubleSetting("Lethal Max Self Damage", 16.0, 0.0, 20.0, 0.25, lethalOverride::getValue).group(sgCalculation);
    private final DoubleSetting safeMaxTargetDamageReduction = doubleSetting("Safe Max Target Damage Reduction", 1.0, 0.0, 10.0, 0.1).group(sgCalculation);
    private final DoubleSetting safeMinSelfDamageReduction = doubleSetting("Safe Min Self Damage Reduction", 2.0, 0.0, 10.0, 0.1).group(sgCalculation);
    private final DoubleSetting collidingCrystalExtraSelfDamageThreshold = doubleSetting("Colliding Crystal Extra Self Damage Threshold", 4.0, 0.0, 10.0, 0.1).group(sgCalculation);

    // Place
    private final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Single).group(sgPlace);
    private final EnumSetting<PacketPlaceMode> packetPlace = enumSetting("Packet Place", PacketPlaceMode.Weak).group(sgPlace);
    private final BoolSetting spamPlace = boolSetting("Spam Place", false).group(sgPlace);
    private final EnumSetting<SwitchMode> placeSwitchMode = enumSetting("Place Switch Mode", SwitchMode.Off).group(sgPlace);
    private final BoolSetting placeSwing = boolSetting("Place Swing", false).group(sgPlace);
    private final EnumSetting<PlaceBypass> placeSideBypass = enumSetting("Place Side Bypass", PlaceBypass.Up).group(sgPlace);
    private final DoubleSetting placeMinDamage = doubleSetting("Place Min Damage", 5.0, 0.0, 20.0, 0.25).group(sgPlace);
    private final DoubleSetting placeMaxSelfDamage = doubleSetting("Place Max Self Damage", 6.0, 0.0, 20.0, 0.25).group(sgPlace);
    private final DoubleSetting placeBalance = doubleSetting("Place Balance", -3.0, -10.0, 10.0, 0.25).group(sgPlace);
    private final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 500, 1).group(sgPlace);
    private final DoubleSetting placeRange = doubleSetting("Place Range", 5.0, 0.0, 8.0, 0.1).group(sgPlace);
    private final EnumSetting<RangeMode> placeRangeMode = enumSetting("Place Range Mode", RangeMode.Feet).group(sgPlace);

    // Break
    private final EnumSetting<BreakMode> breakMode = enumSetting("Break Mode", BreakMode.Smart).group(sgBreak);
    private final BoolSetting bbtt = boolSetting("2B2T", false).group(sgBreak);
    private final IntSetting bbttFactor = intSetting("2B2T Factor", 200, 0, 1000, 25, bbtt::getValue).group(sgBreak);
    private final EnumSetting<BreakMode> packetBreak = enumSetting("Packet Break", BreakMode.Target, () -> !bbtt.getValue()).group(sgBreak);
    private final IntSetting ownTimeout = intSetting("Own Timeout", 100, 0, 2000, 25,
            () -> breakMode.getValue() == BreakMode.Own || packetBreak.getValue() == BreakMode.Own).group(sgBreak);
    private final EnumSetting<SwitchMode> antiWeakness = enumSetting("Anti Weakness", SwitchMode.Off).group(sgBreak);
    private final IntSetting swapDelay = intSetting("Swap Delay", 0, 0, 20, 1).group(sgBreak);
    private final DoubleSetting breakMinDamage = doubleSetting("Break Min Damage", 4.0, 0.0, 20.0, 0.25).group(sgBreak);
    private final DoubleSetting breakMaxSelfDamage = doubleSetting("Break Max Self Damage", 8.0, 0.0, 20.0, 0.25).group(sgBreak);
    private final DoubleSetting breakBalance = doubleSetting("Break Balance", -4.0, -10.0, 10.0, 0.25).group(sgBreak);
    private final IntSetting breakDelay = intSetting("Break Delay", 100, 0, 500, 1).group(sgBreak);
    private final DoubleSetting breakRange = doubleSetting("Break Range", 5.0, 0.0, 8.0, 0.1).group(sgBreak);
    private final EnumSetting<RangeMode> breakRangeMode = enumSetting("Break Range Mode", RangeMode.Feet).group(sgBreak);

    // Render
    private final EnumSetting<SwingMode> swingMode = enumSetting("Swing Mode", SwingMode.Client).group(sgRender);
    private final EnumSetting<SwingHand> swingHand = enumSetting("Swing Hand", SwingHand.Auto).group(sgRender);
    private final EnumSetting<RenderPredictMode> renderPredict = enumSetting("Render Predict", RenderPredictMode.Off).group(sgRender);
    private final EnumSetting<HudInfo> hudInfo = enumSetting("Hud Info", HudInfo.Speed).group(sgRender);
    private final IntSetting filledAlpha = intSetting("Filled Alpha", 63, 0, 255, 1).group(sgRender);
    private final IntSetting outlineAlpha = intSetting("Outline Alpha", 200, 0, 255, 1).group(sgRender);
    private final BoolSetting renderTargetDamage = boolSetting("Target Damage", true).group(sgRender);
    private final BoolSetting renderSelfDamage = boolSetting("Self Damage", true).group(sgRender);
    private final ColorSetting renderColor = colorSetting("Render Color", new Color(255, 150, 120, 255), true).group(sgRender);
    private final DoubleSetting outlineWidth = doubleSetting("Outline Width", 3.0, 1.0, 10.0, 0.5).group(sgRender);
    private final IntSetting movingLength = intSetting("Moving Length", 400, 0, 1000, 50).group(sgRender);
    private final IntSetting fadeLength = intSetting("Fade Length", 200, 0, 1000, 50).group(sgRender);

    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils breakTimer = new TimerUtils();
    private final TimerUtils snapshotTimer = new TimerUtils();
    private final TimerUtils explosionSampleTimer = new TimerUtils();

    private final Map<Long, Long> placedPosMap = new HashMap<>();
    private final Map<Integer, Long> crystalSpawnMap = new HashMap<>();
    private final Map<Integer, Long> attackedCrystalMap = new HashMap<>();
    private final Map<Long, Long> attackedPosMap = new HashMap<>();
    private long lastSwapTime;
    private long lastActiveTime;
    private LivingEntity target;

    private final Object workerSignal = new Object();
    private final Thread workerThread = new Thread(this::workerLoop, "Zealot+");
    private volatile SnapshotData pendingSnapshot;
    private volatile SnapshotData latestSnapshot;
    private volatile AsyncResult asyncResult = AsyncResult.EMPTY;
    private volatile PlaceInfo cachedRotationPlaceInfo;
    private volatile PlaceInfo cachedPlaceInfo;
    private volatile BreakPlan cachedRotationBreakPlan;
    private volatile BreakPlan cachedBreakPlan;
    private volatile List<BlockPos> cachedRawPosList = List.of();
    private volatile long rawPosListExpireAt;

    private BlockPos renderBlockPos;
    private Vec3 renderPrevPos;
    private Vec3 renderCurrentPos;
    private Vec3 renderLastRenderedPos;
    private long renderMoveStartTime;
    private long renderFadeStartTime;
    private float renderScale;
    private float renderDamage;
    private float renderSelfDamageValue;
    private boolean renderHasTarget;

    private final Supplier<TextRenderer> textRenderer = Suppliers.memoize(() -> TextRenderer.create(128 * 1024));
    private final Deque<Integer> explosionSamples = new ArrayDeque<>();
    private int explosionsThisWindow;

    private static final int EXPLOSION_SAMPLE_SIZE = 8;

    @Override
    protected void onEnable() {
        placeTimer.reset();
        breakTimer.reset();
        snapshotTimer.setMs(updateDelay.getValue().longValue());
        explosionSampleTimer.reset();
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        explosionSamples.clear();
        explosionsThisWindow = 0;
        lastSwapTime = 0L;
        lastActiveTime = 0L;
        target = null;
        pendingSnapshot = null;
        latestSnapshot = null;
        asyncResult = AsyncResult.EMPTY;
        cachedRotationPlaceInfo = null;
        cachedPlaceInfo = null;
        cachedRotationBreakPlan = null;
        cachedBreakPlan = null;
        resetRenderState();
        signalWorker();
    }

    @Override
    protected void onDisable() {
        placedPosMap.clear();
        crystalSpawnMap.clear();
        attackedCrystalMap.clear();
        attackedPosMap.clear();
        pendingSnapshot = null;
        latestSnapshot = null;
        asyncResult = AsyncResult.EMPTY;
        cachedRotationPlaceInfo = null;
        cachedPlaceInfo = null;
        cachedRotationBreakPlan = null;
        cachedBreakPlan = null;
        target = null;
        explosionSamples.clear();
        explosionsThisWindow = 0;
        resetRenderState();
        signalWorker();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        updateTimeouts();
        updateExplosionSamples();
        if (isEatingPaused()) return;

        SnapshotData snapshot = captureSnapshotIfNeeded();
        AsyncResult result = asyncResult;
        BreakPlan preBreak = getValidBreakPlan(cachedRotationBreakPlan);
        PlaceInfo prePlace = getValidPlaceInfo(cachedRotationPlaceInfo, false);
        target = resolveCurrentTarget(result, prePlace);

        boolean acted = false;
        BreakPlan actionBreak = getActionBreakPlan();
        if (breakMode.getValue() != BreakMode.Off && breakTimer.passedMillise(breakDelay.getValue()) && actionBreak != null) {
            acted = breakDirect(actionBreak);
        }

        PlaceInfo actionPlace = getActionPlaceInfo();
        if (!acted && placeMode.getValue() != PlaceMode.Off && placeTimer.passedMillise(placeDelay.getValue()) && actionPlace != null && shouldAttemptPlace(actionPlace)) {
            acted = placeDirect(actionPlace, false);
        }

        if (!acted && (snapshot == null || (preBreak == null && prePlace == null && actionBreak == null && actionPlace == null))) {
            if (System.currentTimeMillis() - lastActiveTime > 250L) {
                deactivateRenderTarget();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || !isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundAddEntityPacket addPacket && addPacket.getType() == EntityType.END_CRYSTAL) {
            handleSpawnPacket(addPacket);
        } else if (packet instanceof ClientboundSoundPacket soundPacket) {
            handleExplosionPacket(soundPacket);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        renderTargetPredictions(event);

        if (renderPrevPos == null || renderCurrentPos == null) return;

        float moveDelta = toDelta(renderMoveStartTime, movingLength.getValue());
        float moveMultiplier = easeOutQuart(moveDelta);
        Vec3 renderPos = renderPrevPos.add(renderCurrentPos.subtract(renderPrevPos).scale(moveMultiplier));

        float fadeDelta = toDelta(renderFadeStartTime, fadeLength.getValue());
        renderScale = renderHasTarget ? easeOutCubic(fadeDelta) : 1.0f - easeInCubic(fadeDelta);
        if (renderScale <= 0.01f) return;

        double halfSize = 0.5 * renderScale;
        AABB box = new AABB(
                renderPos.x - halfSize, renderPos.y - halfSize, renderPos.z - halfSize,
                renderPos.x + halfSize, renderPos.y + halfSize, renderPos.z + halfSize
        );

        Color base = renderColor.getValue();
        Color filled = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (filledAlpha.getValue() * renderScale), 0, 255));
        Color outline = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (outlineAlpha.getValue() * renderScale), 0, 255));

        if (filledAlpha.getValue() > 0) {
            Render3DScheduler.INSTANCE.addFilledBox(box, filled);
        }
        if (outlineAlpha.getValue() > 0) {
            Render3DScheduler.INSTANCE.addOutlineBox(box, outline.getRGB(), outlineWidth.getValue().floatValue());
        }

        renderLastRenderedPos = renderPos;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || renderPrevPos == null || renderCurrentPos == null) return;
        if (!renderTargetDamage.getValue() && !renderSelfDamage.getValue()) return;

        float moveDelta = toDelta(renderMoveStartTime, movingLength.getValue());
        float moveMultiplier = easeOutQuart(moveDelta);
        Vec3 renderPos = renderPrevPos.add(renderCurrentPos.subtract(renderPrevPos).scale(moveMultiplier));
        Vector2f screenPos = projectToScreen(renderPos);
        if (screenPos == null) return;

        StringBuilder text = new StringBuilder();
        if (renderTargetDamage.getValue()) {
            text.append(String.format(Locale.ROOT, "%.1f", renderDamage));
        }
        if (renderSelfDamage.getValue()) {
            if (!text.isEmpty()) text.append('/');
            text.append(String.format(Locale.ROOT, "%.1f", renderSelfDamageValue));
        }
        if (text.isEmpty()) return;

        TextRenderer renderer = textRenderer.get();
        float scale = 1.0f;
        float width = renderer.getWidth(text.toString(), scale);
        float height = renderer.getHeight(scale);
        Color color = new Color(255, 255, 255, Math.clamp((int) (220 * renderScale), 0, 255));
        renderer.addText(text.toString(), screenPos.x - width / 2.0f, screenPos.y - height / 2.0f, scale, color);
        renderer.drawAndClear();
    }


    @Override
    public String getInfo() {
        return switch (hudInfo.getValue()) {
            case Off -> "";
            case Speed -> String.format(Locale.ROOT, "%.1f", getExplosionSpeed());
            case Target -> {
                LivingEntity currentTarget = target;
                if (currentTarget == null && asyncResult.primaryTarget() != null) {
                    currentTarget = asyncResult.primaryTarget().entity();
                }
                yield currentTarget != null ? currentTarget.getName().getString() : "None";
            }
            case Damage -> {
                PlaceInfo info = getValidPlaceInfo(cachedPlaceInfo, false);
                if (info == null) {
                    info = getValidPlaceInfo(cachedRotationPlaceInfo, false);
                }
                yield info != null
                        ? String.format(Locale.ROOT, "%.1f/%.1f", info.targetDamage(), info.selfDamage())
                        : "0.0/0.0";
            }
            case CalculationTime -> String.format(Locale.ROOT, "%.2f ms", asyncResult.calculationNanos() / 1_000_000.0);
        };
    }

    private void workerLoop() {
        long lastProcessedId = Long.MIN_VALUE;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!isEnabled()) {
                    waitForSignal(100L, 0);
                    continue;
                }

                SnapshotData snapshot = pendingSnapshot;
                if (snapshot == null) {
                    waitForSignal(50L, 0);
                    continue;
                }

                if (snapshot.id() == lastProcessedId) {
                    waitForSignal(0L, Math.max(1, snapshot.settings().globalDelayNanos()));
                    continue;
                }

                lastProcessedId = snapshot.id();
                long start = System.nanoTime();
                asyncResult = evaluateSnapshot(snapshot, start);
                cachedRotationPlaceInfo = asyncResult.rotationPlaceInfo();
                cachedPlaceInfo = asyncResult.placeInfo();
                cachedRotationBreakPlan = asyncResult.rotationBreakPlan();
                cachedBreakPlan = asyncResult.breakPlan();
                waitForSignal(0L, Math.max(1, snapshot.settings().globalDelayNanos()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                Constants.LOGGER.warn("Error in ZealotCrystalPlus worker loop", t);
                try {
                    waitForSignal(100L, 0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void waitForSignal(long millis, int nanos) throws InterruptedException {
        long normalizedMillis = Math.max(0L, millis);
        int normalizedNanos = Math.max(0, nanos);
        if (normalizedNanos >= 1_000_000) {
            normalizedMillis += normalizedNanos / 1_000_000L;
            normalizedNanos %= 1_000_000;
        }

        synchronized (workerSignal) {
            if (normalizedMillis > 0 || normalizedNanos > 0) {
                workerSignal.wait(normalizedMillis, normalizedNanos);
            } else {
                workerSignal.wait(1L);
            }
        }
    }

    private void signalWorker() {
        synchronized (workerSignal) {
            workerSignal.notifyAll();
        }
    }

    private SnapshotData captureSnapshotIfNeeded() {
        if (!snapshotTimer.passedMillise(updateDelay.getValue()) && latestSnapshot != null) {
            return latestSnapshot;
        }

        snapshotTimer.reset();
        SnapshotData snapshot = captureSnapshot();
        latestSnapshot = snapshot;
        pendingSnapshot = snapshot;
        signalWorker();
        return snapshot;
    }

    private SnapshotData captureSnapshot() {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return SnapshotData.EMPTY;
        }

        SettingsSnapshot settings = new SettingsSnapshot(
                globalDelay.getValue(),
                noSuicide.getValue().floatValue(),
                placeMaxSelfDamage.getValue().floatValue(),
                breakMaxSelfDamage.getValue().floatValue(),
                placeMinDamage.getValue().floatValue(),
                breakMinDamage.getValue().floatValue(),
                placeBalance.getValue().floatValue(),
                breakBalance.getValue().floatValue(),
                forcePlaceMinDamage.getValue().floatValue(),
                forcePlaceBalance.getValue().floatValue(),
                lethalOverride.getValue(),
                lethalThresholdAddition.getValue().floatValue(),
                lethalMaxSelfDamage.getValue().floatValue(),
                safeMaxTargetDamageReduction.getValue().floatValue(),
                safeMinSelfDamageReduction.getValue().floatValue(),
                collidingCrystalExtraSelfDamageThreshold.getValue().floatValue(),
                damagePriority.getValue(),
                armorMode.getValue(),
                placeSideBypass.getValue(),
                packetPlace.getValue(),
                breakMode.getValue(),
                packetBreak.getValue()
        );

        SelfSnapshot self = captureSelfSnapshot(player, settings.armorMode());
        List<TargetSnapshot> targets = captureTargets(settings.armorMode());
        if (targets.isEmpty()) {
            return new SnapshotData(System.nanoTime(), settings, self, List.of(), List.of(), List.of(), ResistantBlockCache.EMPTY, System.currentTimeMillis());
        }

        List<BlockPos> placePositions = capturePlacePositions();
        List<CrystalSnapshot> crystals = captureCrystals();
        ResistantBlockCache resistantBlocks = new ResistantBlockCache(mc.level, assumeInstantMine.getValue());

        return new SnapshotData(
                System.nanoTime(),
                settings,
                self,
                List.copyOf(targets),
                List.copyOf(placePositions),
                List.copyOf(crystals),
                resistantBlocks,
                System.currentTimeMillis()
        );
    }

    private SelfSnapshot captureSelfSnapshot(Player player, DamageUtils.ArmorEnchantmentMode armorMode) {
        Rot2f currentRotation = Managers.ROTATION.getRotation();
        return new SelfSnapshot(
                player,
                player.position(),
                player.getEyePosition(),
                player.getBoundingBox(),
                getTotalHealth(player),
                player.hasEffect(MobEffects.WEAKNESS)
                        && (!player.hasEffect(MobEffects.STRENGTH) || player.getEffect(MobEffects.STRENGTH) == null || player.getEffect(MobEffects.STRENGTH).getAmplifier() <= 0),
                isToolLike(player.getMainHandItem()),
                player.getMainHandItem().is(ItemTags.SWORDS),
                DamageReductionData.fromEntity(player, armorMode),
                mc.level.getDifficulty(),
                currentRotation
        );
    }

    private List<TargetSnapshot> captureTargets(DamageUtils.ArmorEnchantmentMode armorMode) {
        if (mc.player == null || mc.level == null) return List.of();

        int ticks = motionPredict.getValue() ? predictTicks.getValue() : 0;
        List<LivingEntity> targets = Managers.TARGET.acquireTargets(TargetRequest.of(
                targetRange.getValue(),
                360.0f,
                players.getValue(),
                mobs.getValue(),
                animals.getValue(),
                false,
                true,
                living -> living.position().y > -64.0,
                maxTargets.getValue()
        ));

        if (targets.isEmpty()) {
            return List.of();
        }

        List<TargetSnapshot> list = new ArrayList<>(targets.size());
        for (LivingEntity living : targets) {
            list.add(buildTargetSnapshot(living, ticks, armorMode));
        }

        return List.copyOf(list);
    }

    private TargetSnapshot buildTargetSnapshot(LivingEntity entity, int ticks, DamageUtils.ArmorEnchantmentMode armorMode) {
        double motionX = Mth.clamp(entity.getX() - entity.xo, -0.6, 0.6);
        double motionY = Mth.clamp(entity.getY() - entity.yo, -0.5, 0.5);
        double motionZ = Mth.clamp(entity.getZ() - entity.zo, -0.6, 0.6);

        AABB entityBox = entity.getBoundingBox();
        AABB targetBox = entityBox;
        for (int tick = 0; tick <= ticks; tick++) {
            AABB moved = canMove(entity, targetBox, motionX, motionY, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, motionX, 0.0, motionZ);
            if (moved == null) moved = canMove(entity, targetBox, 0.0, motionY, 0.0);
            if (moved == null) break;
            targetBox = moved;
        }

        double offsetX = targetBox.minX - entityBox.minX;
        double offsetY = targetBox.minY - entityBox.minY;
        double offsetZ = targetBox.minZ - entityBox.minZ;
        Vec3 motion = new Vec3(offsetX, offsetY, offsetZ);
        Vec3 currentPos = entity.position();
        Vec3 predictedPos = currentPos.add(motion);

        return new TargetSnapshot(
                entity,
                predictedPos,
                targetBox,
                currentPos,
                motion,
                getTotalHealth(entity),
                entity instanceof Player,
                getRealSpeed(entity),
                getMinArmorRate(entity),
                DamageReductionData.fromEntity(entity, armorMode)
        );
    }

    private List<BlockPos> capturePlacePositions() {
        List<BlockPos> rawPosList = getRawPosList();
        if (rawPosList.isEmpty()) return rawPosList;

        List<Entity> collidingEntities = getCollidingEntities();
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : rawPosList) {
            if (!checkPlaceCollision(pos, collidingEntities)) continue;
            list.add(pos);
        }
        return list;
    }

    private List<CrystalSnapshot> captureCrystals() {
        if (mc.player == null || mc.level == null) return List.of();

        long current = System.currentTimeMillis();
        List<CrystalSnapshot> list = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;

            boolean breakable = !bbtt.getValue() || current - getSpawnTime(crystal) >= bbttFactor.getValue();
            breakable = breakable && checkBreakRange(crystal.position());

            list.add(new CrystalSnapshot(
                    crystal,
                    crystal.getId(),
                    crystal.position(),
                    crystal.getBoundingBox(),
                    breakable,
                    placedPosMap.containsKey(toLong(crystal.getX(), crystal.getY() - 1.0, crystal.getZ()))
            ));
        }
        return List.copyOf(list);
    }

    private Set<Long> captureResistantBlocks(List<TargetSnapshot> targets, List<BlockPos> placePositions, List<CrystalSnapshot> crystals, BlockPos playerPos) {
        int minX = playerPos.getX();
        int minY = playerPos.getY();
        int minZ = playerPos.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;

        for (TargetSnapshot target : targets) {
            minX = Math.min(minX, Mth.floor(target.pos().x));
            minY = Math.min(minY, Mth.floor(target.pos().y));
            minZ = Math.min(minZ, Mth.floor(target.pos().z));
            maxX = Math.max(maxX, Mth.ceil(target.pos().x));
            maxY = Math.max(maxY, Mth.ceil(target.pos().y + target.box().getYsize()));
            maxZ = Math.max(maxZ, Mth.ceil(target.pos().z));
        }

        for (BlockPos pos : placePositions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY() + 2);
            maxZ = Math.max(maxZ, pos.getZ());
        }

        for (CrystalSnapshot crystal : crystals) {
            minX = Math.min(minX, Mth.floor(crystal.pos().x));
            minY = Math.min(minY, Mth.floor(crystal.pos().y));
            minZ = Math.min(minZ, Mth.floor(crystal.pos().z));
            maxX = Math.max(maxX, Mth.ceil(crystal.pos().x));
            maxY = Math.max(maxY, Mth.ceil(crystal.pos().y + 2.0));
            maxZ = Math.max(maxZ, Mth.ceil(crystal.pos().z));
        }

        int expand = 12;
        minX -= expand;
        minY -= expand;
        minZ -= expand;
        maxX += expand;
        maxY += expand;
        maxZ += expand;

        Set<Long> resistant = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (isResistantState(state) && (!assumeInstantMine.getValue() || !PacketMine.INSTANCE.isInstantMining(pos))) {
                        resistant.add(pos.asLong());
                    }
                }
            }
        }
        return resistant;
    }

    private AsyncResult evaluateSnapshot(SnapshotData snapshot, long startTime) {
        if (snapshot.targets().isEmpty()) {
            return new AsyncResult(snapshot.id(), null, null, null, null, null, System.nanoTime() - startTime);
        }

        PlaceInfo rotationPlaceInfo = evaluatePlace(snapshot, false);
        PlaceInfo placeInfo = evaluatePlace(snapshot, true);
        BreakPlan rotationBreakPlan = evaluateBreak(snapshot, rotationPlaceInfo, false);
        BreakPlan breakPlan = evaluateBreak(snapshot, placeInfo, true);
        TargetSnapshot primary = placeInfo != null
                ? snapshot.targets().stream().filter(targetInfo -> targetInfo.entity() == placeInfo.target()).findFirst().orElse(snapshot.targets().getFirst())
                : (rotationPlaceInfo != null
                   ? snapshot.targets().stream().filter(targetInfo -> targetInfo.entity() == rotationPlaceInfo.target()).findFirst().orElse(snapshot.targets().getFirst())
                   : snapshot.targets().getFirst());

        return new AsyncResult(snapshot.id(), rotationPlaceInfo, placeInfo, rotationBreakPlan, breakPlan, primary, System.nanoTime() - startTime);
    }

    private PlaceInfo evaluatePlace(SnapshotData snapshot, boolean requireRotation) {
        if (snapshot.placePositions().isEmpty()) return null;

        PlaceChoice max = new PlaceChoice();
        PlaceChoice safe = new PlaceChoice();
        PlaceChoice lethal = new PlaceChoice();

        for (BlockPos pos : snapshot.placePositions()) {
            if (requireRotation && !checkPlaceRotation(pos, snapshot.self().currentRotation())) continue;

            AABB placeBox = getCrystalPlaceBox(pos);
            Vec3 crystalPos = getCrystalPos(pos);

            float selfDamage = calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks());
            float collidingDamage = calcCollidingCrystalDamage(snapshot, placeBox);
            float adjustedDamage = Math.max(selfDamage, collidingDamage - snapshot.settings().collidingCrystalExtraSelfDamageThreshold());

            if (snapshot.self().totalHealth() - adjustedDamage <= snapshot.settings().noSuicide()) continue;
            if (snapshot.self().totalHealth() - collidingDamage <= snapshot.settings().noSuicide()) continue;
            if (!snapshot.settings().lethalOverride() && adjustedDamage > snapshot.settings().placeMaxSelfDamage())
                continue;

            for (TargetSnapshot targetInfo : snapshot.targets()) {
                if (targetInfo.box().intersects(placeBox)) continue;
                float targetDamage = calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty());

                if (snapshot.settings().lethalOverride()
                        && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                        && selfDamage < lethal.selfDamage
                        && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                    lethal.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                }

                if (adjustedDamage > snapshot.settings().placeMaxSelfDamage()) continue;

                float minDamage = shouldForcePlace(snapshot.self(), targetInfo)
                        ? snapshot.settings().forcePlaceMinDamage()
                        : snapshot.settings().placeMinDamage();
                float balance = shouldForcePlace(snapshot.self(), targetInfo)
                        ? snapshot.settings().forcePlaceBalance()
                        : snapshot.settings().placeBalance();

                if (targetDamage < minDamage || targetDamage - adjustedDamage < balance) continue;

                float score = snapshot.settings().damagePriority().score(adjustedDamage, targetDamage);
                float maxScore = snapshot.settings().damagePriority().score(max.selfDamage, max.targetDamage);
                if (score > maxScore) {
                    max.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                } else if (max.targetDamage - targetDamage <= snapshot.settings().safeMaxTargetDamageReduction()
                        && max.selfDamage - adjustedDamage >= snapshot.settings().safeMinSelfDamageReduction()) {
                    safe.update(targetInfo.entity(), pos, adjustedDamage, targetDamage);
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > snapshot.settings().safeMaxTargetDamageReduction()
                || max.selfDamage - safe.selfDamage <= snapshot.settings().safeMinSelfDamageReduction()) {
            safe.clear();
        }

        PlaceChoice choice = lethal.takeValid();
        if (choice == null) choice = safe.takeValid();
        if (choice == null) choice = max.takeValid();
        if (choice == null) return null;

        return buildPlaceInfo(choice, snapshot.settings().placeSideBypass(), snapshot.self().eyePos());
    }

    private BreakPlan evaluateBreak(SnapshotData snapshot, PlaceInfo placeInfo, boolean requireRotation) {
        if (snapshot.crystals().isEmpty()) return null;

        List<CrystalSnapshot> crystalList = snapshot.crystals().stream()
                .filter(CrystalSnapshot::breakable)
                .filter(crystal -> !requireRotation || checkCrystalRotation(crystal.pos(), breakRotationRange.getValue(), snapshot.self().currentRotation()))
                .toList();
        if (crystalList.isEmpty()) return null;

        CrystalSnapshot crystal = switch (snapshot.settings().breakMode()) {
            case Own -> {
                CrystalSnapshot targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : evaluateBestBreak(snapshot, crystalList.stream().filter(CrystalSnapshot::ownPlaced).toList());
            }
            case Target -> getTargetCrystal(placeInfo, crystalList);
            case Smart -> {
                CrystalSnapshot targetCrystal = getTargetCrystal(placeInfo, crystalList);
                yield targetCrystal != null ? targetCrystal : evaluateBestBreak(snapshot, crystalList);
            }
            case All -> {
                Entity ref = placeInfo != null ? placeInfo.target() : snapshot.self().player();
                yield crystalList.stream().min(Comparator.comparingDouble(info -> ref.distanceToSqr(info.entity()))).orElse(null);
            }
            case Off -> null;
        };

        if (crystal == null) return null;
        float selfDamage = calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
        float targetDamage = 0.0f;
        if (!snapshot.targets().isEmpty()) {
            targetDamage = calcDamage(snapshot.targets().getFirst(), crystal.pos(), snapshot.resistantBlocks(), snapshot.self().difficulty());
        }
        return new BreakPlan(crystal.entity(), crystal.id(), crystal.pos(), selfDamage, targetDamage);
    }

    private CrystalSnapshot evaluateBestBreak(SnapshotData snapshot, List<CrystalSnapshot> crystalList) {
        if (crystalList.isEmpty()) return null;

        BreakChoice max = new BreakChoice();
        BreakChoice safe = new BreakChoice();
        BreakChoice lethal = new BreakChoice();

        for (CrystalSnapshot crystal : crystalList) {
            float selfDamage = calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
            if (snapshot.self().totalHealth() - selfDamage <= snapshot.settings().noSuicide()) continue;
            if (!snapshot.settings().lethalOverride() && selfDamage > snapshot.settings().breakMaxSelfDamage())
                continue;

            for (TargetSnapshot targetInfo : snapshot.targets()) {
                float targetDamage = calcDamage(targetInfo, crystal.pos(), snapshot.resistantBlocks(), snapshot.self().difficulty());
                if (snapshot.settings().lethalOverride()
                        && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                        && selfDamage < lethal.selfDamage
                        && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                    lethal.update(crystal, selfDamage, targetDamage);
                }

                if (selfDamage > snapshot.settings().breakMaxSelfDamage()) continue;

                float minDamage = shouldForcePlace(snapshot.self(), targetInfo)
                        ? snapshot.settings().forcePlaceMinDamage()
                        : snapshot.settings().breakMinDamage();
                float balance = shouldForcePlace(snapshot.self(), targetInfo)
                        ? snapshot.settings().forcePlaceBalance()
                        : snapshot.settings().breakBalance();
                if (targetDamage < minDamage || targetDamage - selfDamage < balance) continue;

                float score = snapshot.settings().damagePriority().score(selfDamage, targetDamage);
                float maxScore = snapshot.settings().damagePriority().score(max.selfDamage, max.targetDamage);
                if (score > maxScore) {
                    max.update(crystal, selfDamage, targetDamage);
                } else if (max.targetDamage - targetDamage <= snapshot.settings().safeMaxTargetDamageReduction()
                        && max.selfDamage - selfDamage >= snapshot.settings().safeMinSelfDamageReduction()) {
                    safe.update(crystal, selfDamage, targetDamage);
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > snapshot.settings().safeMaxTargetDamageReduction()
                || max.selfDamage - safe.selfDamage <= snapshot.settings().safeMinSelfDamageReduction()) {
            safe.clear();
        }

        BreakChoice choice = lethal.takeValid();
        if (choice == null) choice = safe.takeValid();
        if (choice == null) choice = max.takeValid();
        return choice != null ? choice.crystal : null;
    }

    private float calcCollidingCrystalDamage(SnapshotData snapshot, AABB placeBox) {
        float max = 0.0f;
        for (CrystalSnapshot crystal : snapshot.crystals()) {
            if (!placeBox.intersects(crystal.box())) continue;
            float damage = calcDamage(snapshot.self(), crystal.pos(), snapshot.resistantBlocks());
            if (damage > max) {
                max = damage;
            }
        }
        return max;
    }

    private PlaceInfo buildPlaceInfo(PlaceChoice choice, PlaceBypass bypass, Vec3 eyePos) {
        Direction side;
        Vec3 hitVec;

        switch (bypass) {
            case Up -> {
                side = Direction.UP;
                hitVec = new Vec3(choice.blockPos.getX() + 0.5, choice.blockPos.getY() + 1.0, choice.blockPos.getZ() + 0.5);
            }
            case Down -> {
                side = Direction.DOWN;
                hitVec = new Vec3(choice.blockPos.getX() + 0.5, choice.blockPos.getY(), choice.blockPos.getZ() + 0.5);
            }
            case Closest -> {
                side = calcDirection(eyePos, Vec3.atCenterOf(choice.blockPos));
                hitVec = new Vec3(
                        choice.blockPos.getX() + 0.5 + side.getStepX() * 0.5,
                        choice.blockPos.getY() + 0.5 + side.getStepY() * 0.5,
                        choice.blockPos.getZ() + 0.5 + side.getStepZ() * 0.5
                );
            }
            default -> throw new IllegalStateException("Unexpected value: " + bypass);
        }

        return new PlaceInfo(choice.target, choice.blockPos, choice.selfDamage, choice.targetDamage, side, hitVec, RotationUtils.calculate(hitVec));
    }

    private LivingEntity resolveCurrentTarget(AsyncResult result, PlaceInfo prePlace) {
        if (prePlace != null) {
            return prePlace.target();
        }
        return result.primaryTarget() != null ? result.primaryTarget().entity() : null;
    }

    private PlaceInfo getValidPlaceInfo(PlaceInfo placeInfo) {
        return getValidPlaceInfo(placeInfo, true);
    }

    private PlaceInfo getValidPlaceInfo(PlaceInfo placeInfo, boolean requirePlaceable) {
        if (placeInfo == null || nullCheck() || mc.player == null || mc.level == null) return null;
        if (placeDistanceSq(mc.player, placeInfo.hitVec().x, placeInfo.hitVec().y, placeInfo.hitVec().z) > placeRange.getValue() * placeRange.getValue()) {
            return null;
        }
        if (!isCrystalSupport(placeInfo.blockPos())) {
            return null;
        }
        return !requirePlaceable || isPlaceable(placeInfo.blockPos()) ? placeInfo : null;
    }

    private BreakPlan getValidBreakPlan(BreakPlan breakPlan) {
        if (breakPlan == null || nullCheck() || mc.level == null) return null;
        Entity entity = mc.level.getEntity(breakPlan.entityId());
        if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) {
            return null;
        }
        return checkBreakRange(crystal.position()) ? new BreakPlan(crystal, crystal.getId(), crystal.position(), breakPlan.selfDamage(), breakPlan.targetDamage()) : null;
    }

    private PlaceInfo getActionPlaceInfo() {
        PlaceInfo action = getValidPlaceInfo(cachedPlaceInfo, true);
        if (action != null) return action;

        PlaceInfo fallback = getValidPlaceInfo(cachedRotationPlaceInfo, true);
        if (fallback != null && checkPlaceRotation(fallback.blockPos())) {
            return fallback;
        }

        return null;
    }

    private BreakPlan getActionBreakPlan() {
        BreakPlan action = getValidBreakPlan(cachedBreakPlan);
        if (action != null) return action;

        BreakPlan fallback = getValidBreakPlan(cachedRotationBreakPlan);
        if (fallback != null && checkCrystalRotation(fallback.pos(), breakRotationRange.getValue())) {
            return fallback;
        }

        return null;
    }

    private boolean placeDirect(PlaceInfo placeInfo, boolean ignoreTimer) {
        Player player = mc.player;
        if (player == null) return false;
        if (!ignoreTimer && !placeTimer.passedMillise(placeDelay.getValue())) return false;

        FindItemResult crystals = findCrystalItem();
        if (!crystals.found()) return false;

        InteractionHand hand = crystals.getHand();
        if (hand == InteractionHand.MAIN_HAND && crystals.slot() != player.getInventory().getSelectedSlot() && crystals.slot() != 40) {
            switch (placeSwitchMode.getValue()) {
                case Off -> {
                    return false;
                }
                case Legit -> {
                    InvUtils.swap(crystals.slot(), false);
                    lastSwapTime = System.currentTimeMillis();
                }
                case Ghost -> {
                    InvUtils.swap(crystals.slot(), true);
                    lastSwapTime = System.currentTimeMillis();
                }
            }
        }

        InteractionHand finalHand = hand;
        BlockHitResult hitResult = new BlockHitResult(placeInfo.hitVec(), placeInfo.side(), placeInfo.blockPos(), false);

        Managers.ROTATION.setRotations(placeInfo.rotation(), getRotationSpeed(), null, Priority.High);

        InteractionResult result = mc.gameMode.useItemOn(mc.player, finalHand, hitResult);
        if (result.consumesAction()) {
            if (placeSwing.getValue()) {
                doSwing(resolveSwingHand(true));
            }
            placedPosMap.put(placeInfo.blockPos().asLong(), System.currentTimeMillis() + ownTimeout.getValue());
            placeTimer.reset();
            lastActiveTime = System.currentTimeMillis();
            target = placeInfo.target();
            updateRenderTarget(placeInfo.blockPos(), placeInfo.targetDamage(), placeInfo.selfDamage());
        }
        InvUtils.swapBack();

        return true;
    }

    private boolean breakDirect(BreakPlan breakPlan) {
        if (breakPlan == null) return false;
        if (placeSwitchMode.getValue() != SwitchMode.Ghost
                && antiWeakness.getValue() != SwitchMode.Ghost
                && System.currentTimeMillis() - lastSwapTime < swapDelay.getValue() * 50L) {
            return false;
        }

        if (mc.player == null) return false;
        if (mc.player.hasEffect(MobEffects.WEAKNESS) && !isHoldingTool()) {
            switch (antiWeakness.getValue()) {
                case Off -> {
                    return false;
                }
                case Legit, Ghost -> {
                    int weaponSlot = findWeaponSlot();
                    if (weaponSlot == -1) return false;
                    InvUtils.swap(weaponSlot, antiWeakness.getValue() == SwitchMode.Ghost);
                    lastSwapTime = System.currentTimeMillis();
                }
            }
        }

        Managers.ROTATION.setRotations(RotationUtils.calculate(breakPlan.pos()), getRotationSpeed(), null, Priority.High);

        Entity current = mc.level.getEntity(breakPlan.entityId());
        if (!(current instanceof EndCrystal currentCrystal) || !currentCrystal.isAlive()) {
            InvUtils.swapBack();
            return true;
        }
        if (!checkBreakRange(currentCrystal.position())) {
            InvUtils.swapBack();
            return true;
        }

        mc.gameMode.attack(mc.player, currentCrystal);
        doSwing(resolveSwingHand(false));
        breakTimer.reset();
        lastActiveTime = System.currentTimeMillis();
        attackedCrystalMap.put(currentCrystal.getId(), System.currentTimeMillis() + 1000L);
        attackedPosMap.put(BlockPos.containing(currentCrystal.position()).asLong(), System.currentTimeMillis() + 1000L);
        updateRenderTarget(currentCrystal.blockPosition().below(), breakPlan.targetDamage(), breakPlan.selfDamage());

        PlaceInfo placeInfo = getActionPlaceInfo();
        if (packetPlace.getValue().onBreak && placeInfo != null && crystalPlaceBoxIntersects(placeInfo.blockPos(), currentCrystal.getBoundingBox())) {
            placeDirect(placeInfo, true);
        }

        InvUtils.swapBack();

        return true;
    }

    private void handleSpawnPacket(ClientboundAddEntityPacket packet) {
        crystalSpawnMap.put(packet.getId(), System.currentTimeMillis());
        SnapshotData snapshot = latestSnapshot;
        if (snapshot == null || snapshot.targets().isEmpty() || bbtt.getValue()) return;

        Vec3 crystalPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (!checkBreakRange(crystalPos) || !checkCrystalRotation(crystalPos, breakRotationRange.getValue())) return;
        PlaceInfo placeInfo = getActionPlaceInfo();
        if (placeInfo == null) {
            placeInfo = getValidPlaceInfo(cachedRotationPlaceInfo, false);
        }
        if (placeInfo == null) return;

        boolean shouldBreak = switch (packetBreak.getValue()) {
            case Target -> crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos));
            case Own -> crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos))
                    || (placedPosMap.containsKey(toLong(packet.getX(), packet.getY() - 1.0, packet.getZ())) && checkBreakDamage(snapshot, crystalPos));
            case Smart ->
                    crystalPlaceBoxIntersects(placeInfo.blockPos(), getCrystalBoundingBox(crystalPos)) || checkBreakDamage(snapshot, crystalPos);
            case All -> true;
            case Off -> false;
        };

        if (!shouldBreak) return;

        BreakPlan immediate = new BreakPlan(null, packet.getId(), crystalPos, calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks()),
                snapshot.targets().isEmpty() ? 0.0f : calcDamage(snapshot.targets().getFirst(), crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty()));
        breakDirect(immediate);
    }

    private void handleExplosionPacket(ClientboundSoundPacket packet) {
        if (packet.getSound() != SoundEvents.GENERIC_EXPLODE) return;

        Vec3 soundPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (attackedPosMap.containsKey(BlockPos.containing(soundPos).asLong())) {
            explosionsThisWindow++;
        }

        PlaceInfo placeInfo = getValidPlaceInfo(cachedPlaceInfo);
        if (placeInfo != null) {
            Vec3 placePos = getCrystalPos(placeInfo.blockPos());
            if (placePos.distanceToSqr(soundPos) <= 144.0) {
                placedPosMap.clear();
                crystalSpawnMap.clear();
                attackedCrystalMap.clear();
                attackedPosMap.clear();
                if (packetPlace.getValue().onRemove) {
                    placeDirect(placeInfo, true);
                }
                return;
            }
        }

        if (mc.player != null && mc.player.distanceToSqr(soundPos) <= 144.0) {
            placedPosMap.clear();
            crystalSpawnMap.clear();
            attackedCrystalMap.clear();
            attackedPosMap.clear();
        }
    }

    private boolean checkBreakDamage(SnapshotData snapshot, Vec3 crystalPos) {
        float selfDamage = calcDamage(snapshot.self(), crystalPos, snapshot.resistantBlocks());
        if (snapshot.self().totalHealth() - selfDamage <= snapshot.settings().noSuicide()) return false;

        for (TargetSnapshot targetInfo : snapshot.targets()) {
            float targetDamage = calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks(), snapshot.self().difficulty());
            if (snapshot.settings().lethalOverride()
                    && targetDamage - targetInfo.totalHealth() > snapshot.settings().lethalThresholdAddition()
                    && selfDamage <= snapshot.settings().lethalMaxSelfDamage()) {
                return true;
            }

            if (selfDamage > snapshot.settings().breakMaxSelfDamage()) continue;
            float minDamage = shouldForcePlace(snapshot.self(), targetInfo)
                    ? snapshot.settings().forcePlaceMinDamage()
                    : snapshot.settings().breakMinDamage();
            float balance = shouldForcePlace(snapshot.self(), targetInfo)
                    ? snapshot.settings().forcePlaceBalance()
                    : snapshot.settings().breakBalance();
            if (targetDamage >= minDamage && targetDamage - selfDamage >= balance) {
                return true;
            }
        }

        return false;
    }

    private CrystalSnapshot getTargetCrystal(PlaceInfo placeInfo, List<CrystalSnapshot> crystalList) {
        if (placeInfo == null) return null;
        for (CrystalSnapshot crystal : crystalList) {
            if (crystalPlaceBoxIntersects(placeInfo.blockPos(), crystal.box())) {
                return crystal;
            }
        }
        return null;
    }

    private float calcDamage(SelfSnapshot self, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        return calcDamage(self.pos(), self.box(), self.reduction(), true, self.difficulty(), crystalPos, resistantBlocks);
    }

    private float calcDamage(TargetSnapshot target, Vec3 crystalPos, ResistantBlockCache resistantBlocks, Difficulty difficulty) {
        return calcDamage(target.pos(), target.box(), target.reduction(), target.player(), difficulty, crystalPos, resistantBlocks);
    }

    private float calcDamage(Vec3 entityPos, AABB entityBox, DamageReductionData reduction, boolean playerEntity, Difficulty difficulty, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        if (playerEntity && difficulty == Difficulty.PEACEFUL) {
            return 0.0f;
        }

        float damage;
        BlockPos supportPos = BlockPos.containing(crystalPos.x, crystalPos.y - 1.0, crystalPos.z);
        if (playerEntity && crystalPos.y - entityPos.y > 1.5652173822904127 && resistantBlocks.isResistant(supportPos.asLong())) {
            damage = 1.0f;
        } else {
            damage = calcRawDamage(entityPos, entityBox, crystalPos, resistantBlocks);
        }

        if (playerEntity) {
            damage = applyDifficultyDamage(difficulty, damage);
        }
        return reduction.apply(damage);
    }

    private float calcRawDamage(Vec3 entityPos, AABB entityBox, Vec3 crystalPos, ResistantBlockCache resistantBlocks) {
        float scaledDist = (float) (entityPos.distanceTo(crystalPos) / DOUBLE_SIZE);
        if (scaledDist > 1.0f) return 0.0f;

        float factor = (1.0f - scaledDist) * getExposureAmount(entityBox, crystalPos, resistantBlocks);
        return ((factor * factor + factor) * DAMAGE_FACTOR + 1.0f);
    }

    private float getExposureAmount(AABB entityBox, Vec3 explosionPos, ResistantBlockCache resistantBlocks) {
        double width = entityBox.maxX - entityBox.minX;
        double height = entityBox.maxY - entityBox.minY;
        double gridMultiplierXZ = 1.0 / (width * 2.0 + 1.0);
        double gridMultiplierY = 1.0 / (height * 2.0 + 1.0);
        double gridXZ = width * gridMultiplierXZ;
        double gridY = height * gridMultiplierY;
        int sizeXZ = Mth.floor(1.0 / gridMultiplierXZ);
        int sizeY = Mth.floor(1.0 / gridMultiplierY);
        double xzOffset = (1.0 - gridMultiplierXZ * sizeXZ) / 2.0;

        int total = 0;
        int count = 0;
        for (int yIndex = 0; yIndex <= sizeY; yIndex++) {
            for (int xIndex = 0; xIndex <= sizeXZ; xIndex++) {
                for (int zIndex = 0; zIndex <= sizeXZ; zIndex++) {
                    double x = gridXZ * xIndex + xzOffset + entityBox.minX;
                    double y = gridY * yIndex + entityBox.minY;
                    double z = gridXZ * zIndex + xzOffset + entityBox.minZ;
                    total++;
                    if (!rayTraceResistant(new Vec3(x, y, z), explosionPos, resistantBlocks)) {
                        count++;
                    }
                }
            }
        }
        return total == 0 ? 0.0f : count / (float) total;
    }

    private boolean rayTraceResistant(Vec3 from, Vec3 to, ResistantBlockCache resistantBlocks) {
        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);
        int endX = Mth.floor(to.x);
        int endY = Mth.floor(to.y);
        int endZ = Mth.floor(to.z);

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

        double tMaxX = intBound(from.x, dx);
        double tMaxY = intBound(from.y, dy);
        double tMaxZ = intBound(from.z, dz);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        for (int i = 0; i < 200; i++) {
            if (resistantBlocks.isResistant(BlockPos.asLong(x, y, z))) {
                return true;
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return false;
    }

    private double intBound(double s, double ds) {
        if (ds > 0.0) {
            return (Math.floor(s + 1.0) - s) / ds;
        }
        if (ds < 0.0) {
            return (s - Math.floor(s)) / -ds;
        }
        return Double.POSITIVE_INFINITY;
    }

    private float applyDifficultyDamage(Difficulty difficulty, float damage) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0f;
            case EASY -> Math.min(damage * 0.5f + 1.0f, damage);
            case HARD -> damage * 1.5f;
            default -> damage;
        };
    }

    private boolean shouldForcePlace(SelfSnapshot self, TargetSnapshot targetInfo) {
        return (!forcePlaceWhileSwording.getValue() || !self.swording())
                && (targetInfo.totalHealth() <= forcePlaceHealth.getValue()
                || targetInfo.speed() >= forcePlaceMotion.getValue()
                || targetInfo.minArmorRate() <= forcePlaceArmorRate.getValue());
    }

    private List<BlockPos> getRawPosList() {
        long now = System.currentTimeMillis();
        List<BlockPos> cached = cachedRawPosList;
        if (now < rawPosListExpireAt) return cached;
        cached = buildRawPosList();
        cachedRawPosList = cached;
        rawPosListExpireAt = now + 60L;
        return cached;
    }

    private List<BlockPos> buildRawPosList() {
        if (nullCheck()) return List.of();

        List<BlockPos> list = new ArrayList<>();
        double range = placeRange.getValue();
        double rangeSq = range * range;
        double wallRangeSq = wallRange.getValue() * wallRange.getValue();
        int floor = Mth.floor(range);
        int ceil = Mth.ceil(range);
        Vec3 feetPos = mc.player.position();
        Vec3 eyePos = mc.player.getEyePosition();

        int feetX = Mth.floor(feetPos.x);
        int feetY = Mth.floor(feetPos.y);
        int feetZ = Mth.floor(feetPos.z);

        for (int x = feetX - floor; x <= feetX + ceil; x++) {
            for (int z = feetZ - floor; z <= feetZ + ceil; z++) {
                for (int y = feetY - floor; y <= feetY + ceil; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.level.getWorldBorder().isWithinBounds(pos)) continue;

                    double crystalX = x + 0.5;
                    double crystalY = y + 1.0;
                    double crystalZ = z + 0.5;
                    if (placeDistanceSq(mc.player, crystalX, crystalY, crystalZ) > rangeSq) continue;
                    if (!isPotentialPlacePosition(pos)) continue;

                    double feetDistSq = feetPos.distanceToSqr(crystalX, crystalY, crystalZ);
                    if (feetDistSq > wallRangeSq && !RaytraceUtils.canSeePointFrom(eyePos, new Vec3(crystalX, crystalY + 1.7, crystalZ))) {
                        continue;
                    }

                    list.add(pos);
                }
            }
        }

        list.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distToCenterSqr(feetX, feetY, feetZ)).reversed());
        return list;
    }

    private List<Entity> getCollidingEntities() {
        if (nullCheck()) return List.of();

        List<Entity> colliding = new ArrayList<>();
        double rangeSq = placeRange.getValue() * placeRange.getValue();
        int feetX = Mth.floor(mc.player.getX());
        int feetY = Mth.floor(mc.player.getY());
        int feetZ = Mth.floor(mc.player.getZ());
        boolean single = placeMode.getValue() == PlaceMode.Single;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;

            double adjustedRange = Mth.ceil(rangeSq) - Math.ceil((entity.getBbWidth() / 2.0f) * (entity.getBbWidth() / 2.0f) * 2.0f);
            double dist = entity.distanceToSqr(feetX + 0.5, feetY + 0.5, feetZ + 0.5);
            if (dist > adjustedRange) continue;

            if (!(entity instanceof EndCrystal crystal)) {
                colliding.add(entity);
            } else if (!single || !checkBreakRange(crystal.position())) {
                colliding.add(entity);
            }
        }

        return colliding;
    }

    private boolean checkPlaceCollision(BlockPos pos, List<Entity> collidingEntities) {
        double minX = pos.getX() + 0.001;
        double minY = pos.getY() + 1.0;
        double minZ = pos.getZ() + 0.001;
        double maxX = pos.getX() + 0.999;
        double maxY = pos.getY() + 3.0;
        double maxZ = pos.getZ() + 0.999;

        for (Entity entity : collidingEntities) {
            if (entity.getBoundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkPlaceRotation(BlockPos pos) {
        if (placeRotationRange.getValue() <= 0.0) return true;
        return checkPlaceRotation(pos, Managers.ROTATION.getRotation());
    }

    private boolean checkPlaceRotation(BlockPos pos, Rot2f currentRotation) {
        if (placeRotationRange.getValue() <= 0.0) return true;
        return getRotationDelta(currentRotation, RotationUtils.calculate(getCrystalPos(pos))) <= placeRotationRange.getValue();
    }

    private boolean checkCrystalRotation(Vec3 crystalPos, double range) {
        if (range <= 0.0) return true;
        return checkCrystalRotation(crystalPos, range, Managers.ROTATION.getRotation());
    }

    private boolean checkCrystalRotation(Vec3 crystalPos, double range, Rot2f currentRotation) {
        if (range <= 0.0) return true;
        return getRotationDelta(currentRotation, RotationUtils.calculate(crystalPos)) <= range;
    }

    private boolean checkBreakRange(Vec3 crystalPos) {
        double rangeSq = breakRange.getValue() * breakRange.getValue();
        if (breakDistanceSq(mc.player, crystalPos.x, crystalPos.y, crystalPos.z) > rangeSq) return false;

        Vec3 eyePos = mc.player.getEyePosition();
        return eyePos.distanceToSqr(crystalPos) <= wallRange.getValue() * wallRange.getValue()
                || RaytraceUtils.canSeePointFrom(eyePos, new Vec3(crystalPos.x, crystalPos.y + 1.7, crystalPos.z));
    }

    private AABB canMove(Entity entity, AABB box, double motionX, double motionY, double motionZ) {
        AABB moved = box.move(motionX, motionY, motionZ);
        return mc.level.noCollision(entity, moved) ? moved : null;
    }

    private boolean isPlaceable(BlockPos pos) {
        if (!isPotentialPlacePosition(pos)) return false;
        return mc.level.getEntities(null, getCrystalPlaceBox(pos)).isEmpty();
    }

    private boolean isPotentialPlacePosition(BlockPos pos) {
        if (!isCrystalSupport(pos)) return false;

        BlockPos crystalPos = pos.above();
        if (!mc.level.getBlockState(crystalPos).canBeReplaced()) return false;
        return mc.level.getBlockState(crystalPos.above()).canBeReplaced();
    }

    private boolean isCrystalSupport(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private double placeDistanceSq(Entity entity, double x, double y, double z) {
        return placeRangeMode.getValue() == RangeMode.Feet ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    private double breakDistanceSq(Entity entity, double x, double y, double z) {
        return breakRangeMode.getValue() == RangeMode.Feet ? entity.distanceToSqr(x, y, z) : eyeDistanceSq(entity, x, y, z);
    }

    private double eyeDistanceSq(Entity entity, double x, double y, double z) {
        double dx = entity.getX() - x;
        double dy = entity.getY() + entity.getEyeHeight() - y;
        double dz = entity.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private int getMinArmorRate(LivingEntity entity) {
        int minDura = 100;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = entity.getItemBySlot(slot);
            if (!armor.isDamageableItem()) continue;
            int maxDamage = armor.getMaxDamage();
            if (maxDamage <= 0) continue;
            int remaining = maxDamage - armor.getDamageValue();
            int percent = Math.clamp((int) ((remaining / (double) maxDamage) * 100.0), 0, 100);
            minDura = Math.min(minDura, percent);
        }
        return minDura;
    }

    private double getRealSpeed(LivingEntity entity) {
        return Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) * 20.0;
    }

    private float getTotalHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private long getSpawnTime(EndCrystal crystal) {
        return crystalSpawnMap.computeIfAbsent(crystal.getId(), ignored -> System.currentTimeMillis() - crystal.tickCount * 50L);
    }

    private boolean isHoldingTool() {
        return mc.player != null && isToolLike(mc.player.getMainHandItem());
    }

    private boolean isToolLike(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    private int findWeaponSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isToolLike(stack)) return i;
        }
        return -1;
    }

    private FindItemResult findCrystalItem() {
        return InvUtils.findInHotbar(Items.END_CRYSTAL);
    }

    private InteractionHand resolveSwingHand(boolean placing) {
        if (mc.player == null) return InteractionHand.MAIN_HAND;
        return switch (swingHand.getValue()) {
            case OffHand -> InteractionHand.OFF_HAND;
            case MainHand -> InteractionHand.MAIN_HAND;
            case Auto ->
                    (placing && mc.player.getOffhandItem().is(Items.END_CRYSTAL)) || !mc.player.getOffhandItem().is(Items.GOLDEN_APPLE)
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        };
    }

    private void doSwing(InteractionHand hand) {
        switch (swingMode.getValue()) {
            case Client -> mc.player.swing(hand);
            case Packet -> mc.getConnection().send(new ServerboundSwingPacket(hand));
            case None -> {
            }
        }
    }

    private double getRotationSpeed() {
        return Math.max(1.8, yawSpeed.getValue());
    }

    private float getRotationDelta(Rot2f from, Rot2f to) {
        float yawDiff = Math.abs(Mth.wrapDegrees(to.getYaw() - from.getYaw()));
        float pitchDiff = Math.abs(to.getPitch() - from.getPitch());
        return (float) Math.hypot(yawDiff, pitchDiff);
    }

    private Vec3 getCrystalPos(BlockPos supportPos) {
        return new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
    }

    private AABB getCrystalPlaceBox(BlockPos supportPos) {
        return new AABB(
                supportPos.getX(), supportPos.getY() + 1.0, supportPos.getZ(),
                supportPos.getX() + 1.0, supportPos.getY() + 3.0, supportPos.getZ() + 1.0
        );
    }

    private AABB getCrystalBoundingBox(Vec3 crystalPos) {
        return new AABB(
                crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0,
                crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0
        );
    }

    private boolean crystalPlaceBoxIntersects(BlockPos supportPos, AABB crystalBox) {
        return getCrystalPlaceBox(supportPos).intersects(crystalBox);
    }

    private long toLong(double x, double y, double z) {
        return BlockPos.containing(x, y, z).asLong();
    }

    private void updateTimeouts() {
        long current = System.currentTimeMillis();
        placedPosMap.values().removeIf(time -> time < current);
        crystalSpawnMap.values().removeIf(time -> time + 5000L < current);
        attackedCrystalMap.values().removeIf(time -> time < current);
        attackedPosMap.values().removeIf(time -> time < current);
    }

    private boolean isEatingPaused() {
        if (!eatingPause.getValue() || mc.player == null) {
            return false;
        }

        return mc.player.isUsingItem();
    }

    private void updateExplosionSamples() {
        if (!explosionSampleTimer.passedMillise(250)) {
            return;
        }

        explosionSampleTimer.reset();
        explosionSamples.addLast(explosionsThisWindow);
        while (explosionSamples.size() > EXPLOSION_SAMPLE_SIZE) {
            explosionSamples.removeFirst();
        }
        explosionsThisWindow = 0;
    }

    private double getExplosionSpeed() {
        if (explosionSamples.isEmpty()) {
            return 0.0;
        }

        int total = 0;
        for (int value : explosionSamples) {
            total += value;
        }
        return total / (double) explosionSamples.size() * 4.0;
    }

    private boolean shouldAttemptPlace(PlaceInfo placeInfo) {
        if (spamPlace.getValue()) {
            return true;
        }
        if (nullCheck()) {
            return false;
        }

        AABB placeBox = getCrystalPlaceBox(placeInfo.blockPos());
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) continue;
            if (!placeBox.intersects(crystal.getBoundingBox())) continue;
            if (attackedCrystalMap.containsKey(crystal.getId())) continue;
            return false;
        }

        return true;
    }

    private void renderTargetPredictions(Render3DEvent event) {
        if (renderPredict.getValue() == RenderPredictMode.Off) return;

        SnapshotData snapshot = latestSnapshot;
        if (snapshot == null || snapshot.targets().isEmpty()) return;

        LivingEntity focus = target;
        if (focus == null && asyncResult.primaryTarget() != null) {
            focus = asyncResult.primaryTarget().entity();
        }

        Color outline = new Color(100, 255, 100, 150);
        Color filled = new Color(100, 255, 100, 28);

        for (TargetSnapshot targetInfo : snapshot.targets()) {
            if (renderPredict.getValue() == RenderPredictMode.Single && targetInfo.entity() != focus) {
                continue;
            }

            Render3DScheduler.INSTANCE.addFilledBox(targetInfo.box(), filled);
            Render3DScheduler.INSTANCE.addOutlineBox(targetInfo.box(), outline.getRGB(), 1.5f);
        }
    }

    private void updateRenderTarget(BlockPos pos, float damage, float selfDamage) {
        long now = System.currentTimeMillis();
        if (!pos.equals(renderBlockPos)) {
            renderCurrentPos = Vec3.atCenterOf(pos);
            renderPrevPos = renderLastRenderedPos != null ? renderLastRenderedPos : renderCurrentPos;
            renderMoveStartTime = now;
            if (renderBlockPos == null) {
                renderFadeStartTime = now;
            }
            renderBlockPos = pos;
        }
        if (!renderHasTarget) {
            renderFadeStartTime = now;
        }
        renderHasTarget = true;
        renderDamage = damage;
        renderSelfDamageValue = selfDamage;
    }

    private void deactivateRenderTarget() {
        if (renderHasTarget) {
            renderHasTarget = false;
            renderFadeStartTime = System.currentTimeMillis();
        }
    }

    private void resetRenderState() {
        renderBlockPos = null;
        renderPrevPos = null;
        renderCurrentPos = null;
        renderLastRenderedPos = null;
        renderMoveStartTime = 0L;
        renderFadeStartTime = 0L;
        renderScale = 0.0f;
        renderDamage = 0.0f;
        renderSelfDamageValue = 0.0f;
        renderHasTarget = false;
    }

    private Vector2f projectToScreen(Vec3 pos) {
        Vector3f projected = WorldToScreen.getWorldPositionToScreen(pos);
        float guiScale = (float) LuminRenderSystem.getGuiScale();
        if (projected.z < 0.0f || projected.z > 1.0f) return null;

        float centerX = projected.x / guiScale;
        float centerY = projected.y / guiScale;
        if (centerX < 0.0f || centerY < 0.0f
                || centerX > LuminRenderSystem.getScaledWidth()
                || centerY > LuminRenderSystem.getScaledHeight()) {
            return null;
        }
        return new Vector2f(centerX, centerY);
    }

    private Direction calcDirection(Vec3 eyePos, Vec3 hitVec) {
        double x = eyePos.x - hitVec.x;
        double y = eyePos.y - hitVec.y;
        double z = eyePos.z - hitVec.z;

        Direction best = Direction.NORTH;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            Vec3 vec = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            double dot = x * vec.x + y * vec.y + z * vec.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }

    private boolean isResistantState(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.ENCHANTING_TABLE)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.NETHERITE_BLOCK);
    }

    private static float easeOutQuart(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float toDelta(long startTime, int lengthMs) {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.clamp((float) elapsed / Math.max(1, lengthMs), 0.0f, 1.0f);
    }

    private enum DamagePriority {
        Efficient {
            @Override
            float score(float selfDamage, float targetDamage) {
                return targetDamage - selfDamage;
            }
        },
        Aggressive {
            @Override
            float score(float selfDamage, float targetDamage) {
                return targetDamage;
            }
        };

        abstract float score(float selfDamage, float targetDamage);
    }

    private enum SwingHand {
        Auto,
        OffHand,
        MainHand
    }

    private enum SwitchMode {
        Off,
        Legit,
        Ghost
    }

    private enum PlaceMode {
        Off,
        Single,
        Multi
    }

    private enum PacketPlaceMode {
        Off(false, false),
        Weak(true, false),
        Strong(true, true);

        private final boolean onRemove;
        private final boolean onBreak;

        PacketPlaceMode(boolean onRemove, boolean onBreak) {
            this.onRemove = onRemove;
            this.onBreak = onBreak;
        }
    }

    private enum PlaceBypass {
        Up,
        Down,
        Closest
    }

    private enum BreakMode {
        Off,
        Target,
        Own,
        Smart,
        All
    }

    private enum RangeMode {
        Feet,
        Eyes
    }

    private enum SwingMode {
        None,
        Client,
        Packet
    }

    private enum RenderPredictMode {
        Off,
        Single,
        Multi
    }

    private enum HudInfo {
        Off,
        Speed,
        Target,
        Damage,
        CalculationTime
    }

    private static final float DOUBLE_SIZE = 12.0f;
    private static final float DAMAGE_FACTOR = 42.0f;

    private static final class ResistantBlockCache {
        private static final ResistantBlockCache EMPTY = new ResistantBlockCache(null, false);

        private final Level level;
        private final boolean assumeInstantMine;
        private final Map<Long, Boolean> cache = new HashMap<>();

        private ResistantBlockCache(Level level, boolean assumeInstantMine) {
            this.level = level;
            this.assumeInstantMine = assumeInstantMine;
        }

        private boolean isResistant(long posLong) {
            Boolean cached = cache.get(posLong);
            if (cached != null) return cached;
            boolean resistant = false;
            if (level != null) {
                BlockPos pos = BlockPos.of(posLong);
                BlockState state = level.getBlockState(pos);
                resistant = isResistantStateStatic(state) && (!assumeInstantMine || !PacketMine.INSTANCE.isInstantMining(pos));
            }
            cache.put(posLong, resistant);
            return resistant;
        }
    }

    private static boolean isResistantStateStatic(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.ENCHANTING_TABLE)
                || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL)
                || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.NETHERITE_BLOCK);
    }

    private record SettingsSnapshot(
            int globalDelayNanos,
            float noSuicide,
            float placeMaxSelfDamage,
            float breakMaxSelfDamage,
            float placeMinDamage,
            float breakMinDamage,
            float placeBalance,
            float breakBalance,
            float forcePlaceMinDamage,
            float forcePlaceBalance,
            boolean lethalOverride,
            float lethalThresholdAddition,
            float lethalMaxSelfDamage,
            float safeMaxTargetDamageReduction,
            float safeMinSelfDamageReduction,
            float collidingCrystalExtraSelfDamageThreshold,
            DamagePriority damagePriority,
            DamageUtils.ArmorEnchantmentMode armorMode,
            PlaceBypass placeSideBypass,
            PacketPlaceMode packetPlaceMode,
            BreakMode breakMode,
            BreakMode packetBreakMode
    ) {
    }

    private record SelfSnapshot(
            Player player,
            Vec3 pos,
            Vec3 eyePos,
            AABB box,
            float totalHealth,
            boolean weaknessActive,
            boolean holdingTool,
            boolean swording,
            DamageReductionData reduction,
            Difficulty difficulty,
            Rot2f currentRotation
    ) {
    }

    private record TargetSnapshot(
            LivingEntity entity,
            Vec3 pos,
            AABB box,
            Vec3 currentPos,
            Vec3 predictMotion,
            float totalHealth,
            boolean player,
            double speed,
            int minArmorRate,
            DamageReductionData reduction
    ) {
    }

    private record CrystalSnapshot(
            EndCrystal entity,
            int id,
            Vec3 pos,
            AABB box,
            boolean breakable,
            boolean ownPlaced
    ) {
    }

    private record SnapshotData(
            long id,
            SettingsSnapshot settings,
            SelfSnapshot self,
            List<TargetSnapshot> targets,
            List<BlockPos> placePositions,
            List<CrystalSnapshot> crystals,
            ResistantBlockCache resistantBlocks,
            long capturedAt
    ) {
        private static final SnapshotData EMPTY = new SnapshotData(-1L, null, null, List.of(), List.of(), List.of(), ResistantBlockCache.EMPTY, 0L);
    }

    private record AsyncResult(
            long snapshotId,
            PlaceInfo rotationPlaceInfo,
            PlaceInfo placeInfo,
            BreakPlan rotationBreakPlan,
            BreakPlan breakPlan,
            TargetSnapshot primaryTarget,
            long calculationNanos
    ) {
        private static final AsyncResult EMPTY = new AsyncResult(-1L, null, null, null, null, null, 0L);
    }

    private record PlaceInfo(
            LivingEntity target,
            BlockPos blockPos,
            float selfDamage,
            float targetDamage,
            Direction side,
            Vec3 hitVec,
            Rot2f rotation
    ) {
    }

    private record BreakPlan(
            EndCrystal crystal,
            int entityId,
            Vec3 pos,
            float selfDamage,
            float targetDamage
    ) {
    }

    private static final class DamageReductionData {
        private final float armorValue;
        private final float toughness;
        private final float resistanceMultiplier;
        private final float blastReduction;

        private DamageReductionData(float armorValue, float toughness, float resistanceMultiplier, float blastReduction) {
            this.armorValue = armorValue;
            this.toughness = toughness;
            this.resistanceMultiplier = resistanceMultiplier;
            this.blastReduction = blastReduction;
        }

        private static DamageReductionData fromEntity(LivingEntity entity, DamageUtils.ArmorEnchantmentMode armorMode) {
            float armorValue = (float) entity.getAttributeValue(Attributes.ARMOR);
            float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float resistanceMultiplier = 1.0f;
            if (entity.hasEffect(MobEffects.RESISTANCE) && entity.getEffect(MobEffects.RESISTANCE) != null) {
                resistanceMultiplier = Math.max(1.0f - (entity.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 0.2f, 0.0f);
            }

            int epf = switch (armorMode) {
                case PPPP -> 16;
                case PPBP -> 20;
                case None -> {
                    int actualEpf = 0;
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        ItemStack stack = entity.getItemBySlot(slot);
                        actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION);
                        actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * 2;
                    }
                    yield actualEpf;
                }
            };

            float blastReduction = 1.0f - Math.min(epf, 20) / 25.0f;
            return new DamageReductionData(armorValue, toughness, resistanceMultiplier, blastReduction);
        }

        private float apply(float damage) {
            float toughnessFactor = 2.0f + toughness / 4.0f;
            float effectiveArmor = Mth.clamp(armorValue - damage / toughnessFactor, armorValue * 0.2f, 20.0f);
            float afterArmor = damage * (1.0f - effectiveArmor / 25.0f);
            return afterArmor * resistanceMultiplier * blastReduction;
        }
    }

    private static final class PlaceChoice {
        private LivingEntity target;
        private BlockPos blockPos;
        private float selfDamage = Float.MAX_VALUE;
        private float targetDamage = Float.NEGATIVE_INFINITY;

        private void update(LivingEntity target, BlockPos blockPos, float selfDamage, float targetDamage) {
            this.target = target;
            this.blockPos = blockPos;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
        }

        private void clear() {
            target = null;
            blockPos = null;
            selfDamage = Float.MAX_VALUE;
            targetDamage = Float.NEGATIVE_INFINITY;
        }

        private PlaceChoice takeValid() {
            return target != null && blockPos != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY ? this : null;
        }
    }

    private static final class BreakChoice {
        private CrystalSnapshot crystal;
        private float selfDamage = Float.MAX_VALUE;
        private float targetDamage = Float.NEGATIVE_INFINITY;

        private void update(CrystalSnapshot crystal, float selfDamage, float targetDamage) {
            this.crystal = crystal;
            this.selfDamage = selfDamage;
            this.targetDamage = targetDamage;
        }

        private void clear() {
            crystal = null;
            selfDamage = Float.MAX_VALUE;
            targetDamage = Float.NEGATIVE_INFINITY;
        }

        private BreakChoice takeValid() {
            return crystal != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY ? this : null;
        }
    }
}
