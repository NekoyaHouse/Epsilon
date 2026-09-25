package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.BreakMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.DamagePriority;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.HudInfo;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PacketPlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceBypass;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.PlaceMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.RangeMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.RenderPredictMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwingHand;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwingMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.SwitchMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.AsyncResult;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SettingsSnapshot;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.modules.orchestration.ModuleDispatchMode;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.awt.*;
import java.util.Locale;

/**
 * Zealot Crystal+：自动水晶光环。
 *
 * <p>本模块按 Module Orchestrator 的声明式节点图组织：
 * <ul>
 *     <li>{@code observe.*} 只建立本帧事实快照，不产生任何外部副作用；</li>
 *     <li>{@code decide.plan} 只读取快照并生成放置/破坏/旋转计划；</li>
 *     <li>{@code commit.action} 重新校验目标后执行一次水晶动作；</li>
 *     <li>{@code render.*} 只向渲染调度器提交命令。</li>
 * </ul>
 *
 * <p>伤害与放置候选的估算量较大，因此由 {@code Zealot+} worker 线程在不可变
 * {@link SnapshotData} 上完成；主线程通过 {@link AsyncResult} 取回结果，任何世界读写都留在主线程。
 */
public class ZealotCrystalPlus extends Module {

    public static final ZealotCrystalPlus INSTANCE = new ZealotCrystalPlus();

    private ZealotCrystalPlus() {
        super("Zealot Crystal+", Category.COMBAT);
        setDispatchMode(ModuleDispatchMode.MANAGED);

        observePart = new ZealotObservePart(this);
        decidePart = new ZealotDecidePart(this);
        commitPart = new ZealotCommitPart(this);
        renderPart = new ZealotRenderPart(this);

        part(new ZealotSettingsPart(this));
        part(observePart);
        part(decidePart);
        part(commitPart);
        part(renderPart);

        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ------------------------------------------------------------------
    // Setting
    // ------------------------------------------------------------------

    final SettingGroup sgGeneral = settingGroup("General");
    final SettingGroup sgForcePlace = settingGroup("Force Place");
    final SettingGroup sgCalculation = settingGroup("Calculation");
    final SettingGroup sgPlace = settingGroup("Place");
    final SettingGroup sgBreak = settingGroup("Break");
    final SettingGroup sgRender = settingGroup("Render");

    // General
    final BoolSetting players = boolSetting("Players", true).group(sgGeneral);
    final BoolSetting mobs = boolSetting("Mobs", false).group(sgGeneral);
    final BoolSetting animals = boolSetting("Animals", false).group(sgGeneral);
    final IntSetting maxTargets = intSetting("Max Targets", 4, 1, 10, 1).group(sgGeneral);
    final DoubleSetting targetRange = doubleSetting("Target Range", 16.0, 0.0, 32.0, 0.5).group(sgGeneral);
    final DoubleSetting yawSpeed = doubleSetting("Yaw Speed", 45.0, 5.0, 180.0, 5.0).group(sgGeneral);
    final BoolSetting preRotation = boolSetting("Pre Rotation", true).group(sgGeneral);
    final DoubleSetting placeRotationRange = doubleSetting("Place Rotation Range", 0.0, 0.0, 180.0, 5.0).group(sgGeneral);
    final DoubleSetting breakRotationRange = doubleSetting("Break Rotation Range", 90.0, 0.0, 180.0, 5.0).group(sgGeneral);
    final BoolSetting eatingPause = boolSetting("Eating Pause", false).group(sgGeneral);
    final IntSetting updateDelay = intSetting("Update Delay", 5, 0, 250, 1).group(sgGeneral);
    final IntSetting globalDelay = intSetting("Global Delay", 1_000_000, 1_000, 10_000_000, 1_000).group(sgGeneral);

    // Force Place
    final DoubleSetting forcePlaceHealth = doubleSetting("Force Place Health", 8.0, 0.0, 20.0, 0.5).group(sgForcePlace);
    final IntSetting forcePlaceArmorRate = intSetting("Force Place Armor Rate", 3, 0, 25, 1).group(sgForcePlace);
    final DoubleSetting forcePlaceMinDamage = doubleSetting("Force Place Min Damage", 1.5, 0.0, 10.0, 0.25).group(sgForcePlace);
    final DoubleSetting forcePlaceMotion = doubleSetting("Force Place Motion", 4.0, 0.0, 10.0, 0.25).group(sgForcePlace);
    final DoubleSetting forcePlaceBalance = doubleSetting("Force Place Balance", -1.0, -10.0, 10.0, 0.25).group(sgForcePlace);
    final BoolSetting forcePlaceWhileSwording = boolSetting("Force Place While Swording", false).group(sgForcePlace);

    // Calculation
    final BoolSetting assumeInstantMine = boolSetting("Assume Instant Mine", true).group(sgCalculation);
    final DoubleSetting noSuicide = doubleSetting("No Suicide", 2.0, 0.0, 20.0, 0.25).group(sgCalculation);
    final DoubleSetting wallRange = doubleSetting("Wall Range", 3.0, 0.0, 8.0, 0.1).group(sgCalculation);
    final BoolSetting motionPredict = boolSetting("Motion Predict", true).group(sgCalculation);
    final IntSetting predictTicks = intSetting("Predict Ticks", 8, 0, 20, 1, motionPredict::getValue).group(sgCalculation);
    final EnumSetting<DamagePriority> damagePriority = enumSetting("Damage Priority", DamagePriority.Efficient).group(sgCalculation);
    final EnumSetting<DamageUtils.ArmorEnchantmentMode> armorMode = enumSetting("Armor Mode", DamageUtils.ArmorEnchantmentMode.None).group(sgCalculation);
    final BoolSetting lethalOverride = boolSetting("Lethal Override", true).group(sgCalculation);
    final DoubleSetting lethalThresholdAddition = doubleSetting("Lethal Threshold Addition", 0.5, -5.0, 5.0, 0.1, lethalOverride::getValue).group(sgCalculation);
    final DoubleSetting lethalMaxSelfDamage = doubleSetting("Lethal Max Self Damage", 16.0, 0.0, 20.0, 0.25, lethalOverride::getValue).group(sgCalculation);
    final DoubleSetting safeMaxTargetDamageReduction = doubleSetting("Safe Max Target Damage Reduction", 1.0, 0.0, 10.0, 0.1).group(sgCalculation);
    final DoubleSetting safeMinSelfDamageReduction = doubleSetting("Safe Min Self Damage Reduction", 2.0, 0.0, 10.0, 0.1).group(sgCalculation);
    final DoubleSetting collidingCrystalExtraSelfDamageThreshold = doubleSetting("Colliding Crystal Extra Self Damage Threshold", 4.0, 0.0, 10.0, 0.1).group(sgCalculation);

    // Place
    final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Single).group(sgPlace);
    final EnumSetting<PacketPlaceMode> packetPlace = enumSetting("Packet Place", PacketPlaceMode.Weak).group(sgPlace);
    final BoolSetting spamPlace = boolSetting("Spam Place", false).group(sgPlace);
    final EnumSetting<SwitchMode> placeSwitchMode = enumSetting("Place Switch Mode", SwitchMode.Off).group(sgPlace);
    final BoolSetting placeSwing = boolSetting("Place Swing", false).group(sgPlace);
    final EnumSetting<PlaceBypass> placeSideBypass = enumSetting("Place Side Bypass", PlaceBypass.Up).group(sgPlace);
    final DoubleSetting placeMinDamage = doubleSetting("Place Min Damage", 5.0, 0.0, 20.0, 0.25).group(sgPlace);
    final DoubleSetting placeMaxSelfDamage = doubleSetting("Place Max Self Damage", 6.0, 0.0, 20.0, 0.25).group(sgPlace);
    final DoubleSetting placeBalance = doubleSetting("Place Balance", -3.0, -10.0, 10.0, 0.25).group(sgPlace);
    final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 500, 1).group(sgPlace);
    final DoubleSetting placeRange = doubleSetting("Place Range", 5.0, 0.0, 8.0, 0.1).group(sgPlace);
    final EnumSetting<RangeMode> placeRangeMode = enumSetting("Place Range Mode", RangeMode.Feet).group(sgPlace);

    // Break
    final EnumSetting<BreakMode> breakMode = enumSetting("Break Mode", BreakMode.Smart).group(sgBreak);
    final BoolSetting bbtt = boolSetting("2B2T", false).group(sgBreak);
    final IntSetting bbttFactor = intSetting("2B2T Factor", 200, 0, 1000, 25, bbtt::getValue).group(sgBreak);
    final EnumSetting<BreakMode> packetBreak = enumSetting("Packet Break", BreakMode.Target, () -> !bbtt.getValue()).group(sgBreak);
    final IntSetting ownTimeout = intSetting("Own Timeout", 100, 0, 2000, 25,
            () -> breakMode.getValue() == BreakMode.Own || packetBreak.getValue() == BreakMode.Own).group(sgBreak);
    final EnumSetting<SwitchMode> antiWeakness = enumSetting("Anti Weakness", SwitchMode.Off).group(sgBreak);
    final IntSetting swapDelay = intSetting("Swap Delay", 0, 0, 20, 1).group(sgBreak);
    final DoubleSetting breakMinDamage = doubleSetting("Break Min Damage", 4.0, 0.0, 20.0, 0.25).group(sgBreak);
    final DoubleSetting breakMaxSelfDamage = doubleSetting("Break Max Self Damage", 8.0, 0.0, 20.0, 0.25).group(sgBreak);
    final DoubleSetting breakBalance = doubleSetting("Break Balance", -4.0, -10.0, 10.0, 0.25).group(sgBreak);
    final IntSetting breakDelay = intSetting("Break Delay", 100, 0, 500, 1).group(sgBreak);
    final DoubleSetting breakRange = doubleSetting("Break Range", 5.0, 0.0, 8.0, 0.1).group(sgBreak);
    final EnumSetting<RangeMode> breakRangeMode = enumSetting("Break Range Mode", RangeMode.Feet).group(sgBreak);

    // Render
    final EnumSetting<SwingMode> swingMode = enumSetting("Swing Mode", SwingMode.Client).group(sgRender);
    final EnumSetting<SwingHand> swingHand = enumSetting("Swing Hand", SwingHand.Auto).group(sgRender);
    final EnumSetting<RenderPredictMode> renderPredict = enumSetting("Render Predict", RenderPredictMode.Off).group(sgRender);
    final EnumSetting<HudInfo> hudInfo = enumSetting("Hud Info", HudInfo.Speed).group(sgRender);
    final IntSetting filledAlpha = intSetting("Filled Alpha", 63, 0, 255, 1).group(sgRender);
    final IntSetting outlineAlpha = intSetting("Outline Alpha", 200, 0, 255, 1).group(sgRender);
    final BoolSetting renderTargetDamage = boolSetting("Target Damage", true).group(sgRender);
    final BoolSetting renderSelfDamage = boolSetting("Self Damage", true).group(sgRender);
    final ColorSetting renderColor = colorSetting("Render Color", new Color(255, 150, 120, 255), true).group(sgRender);
    final DoubleSetting outlineWidth = doubleSetting("Outline Width", 3.0, 1.0, 10.0, 0.5).group(sgRender);
    final IntSetting movingLength = intSetting("Moving Length", 400, 0, 1000, 50).group(sgRender);
    final IntSetting fadeLength = intSetting("Fade Length", 200, 0, 1000, 50).group(sgRender);

    // ------------------------------------------------------------------
    // 状态与 Part
    // ------------------------------------------------------------------

    final ZealotState state = new ZealotState();
    final ZealotRenderState renderState = new ZealotRenderState();

    final ZealotObservePart observePart;
    final ZealotDecidePart decidePart;
    final ZealotCommitPart commitPart;
    final ZealotRenderPart renderPart;

    /**
     * 暴露父类的受保护 MC 实例给同包的 Part；Part 不重复持有 Minecraft 引用。
     */
    Minecraft minecraft() {
        return mc;
    }

    /**
     * 供同包 Part 声明节点的桥接方法。
     * <p>
     * {@code Module#node} 与 {@code Module#nullCheck} 是 protected，跨包引用实例时不可见，
     * 因此由父 Module 提供包内可见的入口，节点仍然归属于同一个 Module。
     */
    <E> com.github.epsilon.modules.orchestration.NodeBuilder<E> declareNode(Class<E> eventType, com.github.epsilon.modules.orchestration.NodeKey key) {
        return node(eventType, key);
    }

    private final Object workerSignal = new Object();
    private final Thread workerThread = new Thread(this::workerLoop, "Zealot+");

    /**
     * 把 worker 线程需要读取的 Setting 固化为不可变快照。
     * <p>
     * worker 不得直接读取 Setting：用户在 GUI 修改配置时会写主线程状态。
     */
    SettingsSnapshot captureSettings() {
        return new SettingsSnapshot(
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
                forcePlaceHealth.getValue().floatValue(),
                forcePlaceMotion.getValue().floatValue(),
                forcePlaceArmorRate.getValue(),
                forcePlaceWhileSwording.getValue(),
                lethalOverride.getValue(),
                lethalThresholdAddition.getValue().floatValue(),
                lethalMaxSelfDamage.getValue().floatValue(),
                safeMaxTargetDamageReduction.getValue().floatValue(),
                safeMinSelfDamageReduction.getValue().floatValue(),
                collidingCrystalExtraSelfDamageThreshold.getValue().floatValue(),
                placeRotationRange.getValue().floatValue(),
                breakRotationRange.getValue().floatValue(),
                damagePriority.getValue(),
                armorMode.getValue(),
                placeSideBypass.getValue(),
                packetPlace.getValue(),
                breakMode.getValue(),
                packetBreak.getValue()
        );
    }

    @Override
    protected void onEnable() {
        state.placeTimer.reset();
        state.breakTimer.reset();
        state.snapshotTimer.setMs(updateDelay.getValue().longValue());
        state.explosionSampleTimer.reset();
        state.resetForEnable();
        renderState.reset();
        signalWorker();
    }

    @Override
    protected void onDisable() {
        state.resetForDisable();
        renderState.reset();
        signalWorker();
    }

    @Override
    public String getInfo() {
        return switch (hudInfo.getValue()) {
            case Off -> "";
            case Speed -> String.format(Locale.ROOT, "%.1f", state.explosionSpeed());
            case Target -> {
                LivingEntity currentTarget = state.target;
                if (currentTarget == null && state.asyncResult.primaryTarget() != null) {
                    currentTarget = state.asyncResult.primaryTarget().entity();
                }
                yield currentTarget != null ? currentTarget.getName().getString() : "None";
            }
            case Damage -> {
                ZealotSnapshot.PlaceInfo info = decidePart.getValidPlaceInfo(state.cachedPlaceInfo, false);
                if (info == null) {
                    info = decidePart.getValidPlaceInfo(state.cachedRotationPlaceInfo, false);
                }
                yield info != null
                        ? String.format(Locale.ROOT, "%.1f/%.1f", info.targetDamage(), info.selfDamage())
                        : "0.0/0.0";
            }
            case CalculationTime -> String.format(Locale.ROOT, "%.2f ms", state.asyncResult.calculationNanos() / 1_000_000.0);
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

                SnapshotData snapshot = state.pendingSnapshot;
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
                AsyncResult result = decidePart.evaluateSnapshot(snapshot, start);
                state.asyncResult = result;
                state.cachedRotationPlaceInfo = result.rotationPlaceInfo();
                state.cachedPlaceInfo = result.placeInfo();
                state.cachedRotationBreakPlan = result.rotationBreakPlan();
                state.cachedBreakPlan = result.breakPlan();
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

    /**
     * 唤醒 worker：启用状态、快照或禁用状态变化后调用。
     */
    void signalWorker() {
        synchronized (workerSignal) {
            workerSignal.notifyAll();
        }
    }
}
