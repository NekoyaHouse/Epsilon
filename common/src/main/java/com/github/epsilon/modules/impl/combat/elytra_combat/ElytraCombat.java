package com.github.epsilon.modules.impl.combat.elytra_combat;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.FallFlyingMovementEvent;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.ElytraCombatBehavior;
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.FollowBehavior;
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.MaceBehavior;
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.SpearBehavior;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDirectionSolver;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.PredictorMode;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetMotionTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFlightModes;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * 追人、重锤和长矛合并后的 ElytraCombat 模块。
 *
 * <p>模块层只负责目标生命周期、模式调度和事件边界；具体行为由 behavior 状态机生成,
 * 全部飞行结果统一收敛为 {@link FlightIntent}。</p>
 */
public class ElytraCombat extends Module {

    public static final ElytraCombat INSTANCE = new ElytraCombat();

    public enum ControlMode {
        /** 通过 yaw/pitch 和 WASD 输入控制，兼容原版滑翔物理。 */
        Input,
        /** 在 FallFlyingMovementEvent 中直接覆盖当 tick 速度，作为实验模式。 */
        DirectVelocity
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgTarget = settingGroup("Target");
    private final SettingGroup sgFollow = settingGroup("Follow");
    private final SettingGroup sgMace = settingGroup("Mace");
    private final SettingGroup sgSpear = settingGroup("Spear");
    private final SettingGroup sgFlight = settingGroup("Flight");
    private final SettingGroup sgRender = settingGroup("Render");

    public final EnumSetting<ElytraCombatMode> mode =
            enumSetting("Mode", ElytraCombatMode.Follow, this::onModeChanged).group(sgGeneral);
    private final KeybindSetting switchModeKey =
            keybindSetting("Switch Mode Key", -1).group(sgGeneral);
    public final IntSetting targetRange = intSetting("Target Range", 80, 8, 256, 1).group(sgGeneral);
    public final BoolSetting dynamicTarget = boolSetting("Dynamic Target", false).group(sgGeneral);
    private final BoolSetting onlyWhenNoWASD = boolSetting("Only When No WASD", false).group(sgGeneral);
    private final BoolSetting autoControlElytra = boolSetting("Auto Control Elytra", true).group(sgGeneral);

    private final BoolSetting players = boolSetting("Players", true).group(sgTarget);
    private final BoolSetting mobs = boolSetting("Mobs", false).group(sgTarget);
    private final BoolSetting animals = boolSetting("Animals", false).group(sgTarget);
    private final BoolSetting villagers = boolSetting("Villagers", false).group(sgTarget);
    private final BoolSetting ambient = boolSetting("Ambient", false).group(sgTarget);
    private final BoolSetting water = boolSetting("Water", false).group(sgTarget);
    private final BoolSetting others = boolSetting("Others", false).group(sgTarget);
    private final BoolSetting invisible = boolSetting("Invisible", false).group(sgTarget);
    private final BoolSetting followFriend = boolSetting("Follow Friend", false).group(sgTarget);
    public final EnumSetting<PredictorMode> predictorMode =
            enumSetting("Predictor", PredictorMode.NV).group(sgTarget);
    public final IntSetting predictTicks = intSetting("Predict Ticks", 2, 0, 20, 1).group(sgTarget);
    public final IntSetting predictionHistory = intSetting("Prediction History", 5, 2, 20, 1).group(sgTarget);

    public final DoubleSetting stopDistance = doubleSetting("Stop Distance", 6.0, 1.0, 32.0, 0.5).group(sgFollow);
    public final DoubleSetting followGroundHeight =
            doubleSetting("Follow Ground Height", 2.0, 0.0, 12.0, 0.5).group(sgFollow);
    private final BoolSetting pathfinding = boolSetting("Pathfinding", true).group(sgFollow);
    private final IntSetting searchRadius = intSetting("Search Radius", 24, 6, 64, 1, pathfinding::getValue).group(sgFollow);
    private final IntSetting maxNodes = intSetting("Max Nodes", 1200, 100, 6000, 100, pathfinding::getValue).group(sgFollow);
    private final IntSetting dataSize = intSetting(
            "Data Size", 50, 25, 100, 5, pathfinding::getValue, this::setPathDataSize
    ).applyWhenRelease().group(sgFollow);

    public final DoubleSetting maceEngageRange =
            doubleSetting("Mace Range", 10.0, 2.0, 16.0, 0.5).group(sgMace);
    public final DoubleSetting maceHeight =
            doubleSetting("Mace Height", 10.0, 2.0, 32.0, 0.5).group(sgMace);
    public final DoubleSetting maceGroundHeight =
            doubleSetting("Mace Ground Height", 10.0, 2.0, 32.0, 0.5).group(sgMace);
    public final DoubleSetting maceFollowMinHeight =
            doubleSetting("Mace Min Follow Height", 1.5, 0.0, 12.0, 0.5).group(sgMace);
    public final DoubleSetting maceYBias =
            doubleSetting("Mace Y Bias", 3.0, -10.0, 10.0, 0.5).group(sgMace);
    public final IntSetting macePullUpTicks =
            intSetting("Mace Pull Up Max Ticks", 20, 1, 100, 1).group(sgMace);
    public final BoolSetting maceUsePredictor =
            boolSetting("Mace Use Predictor", true).group(sgMace);
    public final DoubleSetting maceAttackThreshold =
            doubleSetting("Mace Attack Threshold", 0.75, 0.1, 1.0, 0.05).group(sgMace);
    public final BoolSetting maceSwingHand =
            boolSetting("Mace Swing Hand", true).group(sgMace);
    public final BoolSetting maceAntiShield =
            boolSetting("Mace Anti Shield", true).group(sgMace);
    public final BoolSetting maceSmoothFlight =
            boolSetting("Mace Smooth Flight", true).group(sgMace);
    public final BoolSetting maceAngleOptimize =
            boolSetting("Mace Angle Optimize", false).group(sgMace);
    public final DoubleSetting maceDownAngle =
            doubleSetting("Mace Down Angle", 30.5, 5.0, 80.0, 0.5, maceAngleOptimize::getValue).group(sgMace);

    public final DoubleSetting spearEngageRange =
            doubleSetting("Spear Range", 8.0, 2.0, 24.0, 0.5).group(sgSpear);
    public final DoubleSetting spearMinRange =
            doubleSetting("Spear Min Range", 1.0, 0.0, 8.0, 0.5).group(sgSpear);
    public final DoubleSetting spearLungeStrength =
            doubleSetting("Spear Lunge Strength", 3.7, 0.5, 15.0, 0.1).group(sgSpear);
    public final IntSetting spearPullOverTicks =
            intSetting("Spear Pull Over Ticks", 6, 1, 40, 1).group(sgSpear);
    public final BoolSetting spearUsePredictor =
            boolSetting("Spear Use Predictor", true).group(sgSpear);
    public final BoolSetting spearAntiSpear =
            boolSetting("Spear Anti Spear", true).group(sgSpear);
    public final DoubleSetting spearAntiSpearExtra =
            doubleSetting("Spear Anti Extra Distance", 1.0, 0.0, 8.0, 0.5, spearAntiSpear::getValue).group(sgSpear);
    private final BoolSetting logSpearHit =
            boolSetting("Log Spear Hit", false).group(sgSpear);

    public final EnumSetting<ControlMode> controlMode =
            enumSetting("Control Mode", ControlMode.Input).group(sgFlight);
    public final DoubleSetting maxFlightSpeed =
            doubleSetting("Max Flight Speed", 2.0, 0.5, 10.0, 0.1).group(sgFlight);
    private final BoolSetting allowFirework =
            boolSetting("Allow Firework", true).group(sgFlight);

    private final BoolSetting render =
            boolSetting("Render Vector", true).group(sgRender);
    private final ColorSetting renderColor =
            colorSetting("Render Color", new Color(255, 133, 161, 210), render::getValue).group(sgRender);
    private final DoubleSetting renderWidth =
            doubleSetting("Render Width", 2.0, 0.5, 8.0, 0.5, render::getValue).group(sgRender);

    /** 模式到状态机的固定映射；切换模式只替换引用，不重建行为对象。 */
    private final Map<ElytraCombatMode, ElytraCombatBehavior> behaviors = new EnumMap<>(ElytraCombatMode.class);
    /** 目标轨迹预测、命中包解析和飞行规划三个纯数据组件。 */
    private final TargetMotionTracker motionTracker = new TargetMotionTracker();
    private final CombatHitTracker hitTracker = new CombatHitTracker();
    private final FlightIntentPlanner flightPlanner = new FlightIntentPlanner();

    private ElytraCombatBehavior currentBehavior;
    private LivingEntity target;
    private ElytraCombatInput controlInput;
    private FlightIntent latestIntent = FlightIntent.idle(Vec3.ZERO);
    /** 记录模块启用前的 ElytraFly 状态，关闭时只恢复被本模块改动过的部分。 */
    private boolean capturedElytraEnabled;
    private ElytraFlightModes capturedElytraMode;
    private boolean changedElytraControl;

    public double lastFallDistance;

    private ElytraCombat() {
        super("Elytra Combat", Category.COMBAT);
        this.behaviors.put(ElytraCombatMode.Follow, new FollowBehavior());
        this.behaviors.put(ElytraCombatMode.Mace, new MaceBehavior());
        this.behaviors.put(ElytraCombatMode.Spear, new SpearBehavior());
        this.currentBehavior = this.behaviors.get(ElytraCombatMode.Follow);
    }

    @Override
    protected void onEnable() {
        // 自动接管时先保存原 ElytraFly 状态，避免关闭模块后把用户设置覆盖掉。
        this.currentBehavior = this.behaviors.get(this.mode.getValue());
        this.capturedElytraEnabled = ElytraFly.INSTANCE.isEnabled();
        this.capturedElytraMode = ElytraFly.INSTANCE.mode.getValue();
        this.changedElytraControl = false;
        if (this.autoControlElytra.getValue()) {
            if (!ElytraFly.INSTANCE.isEnabled()) {
                ElytraFly.INSTANCE.setEnabled(true);
                this.changedElytraControl = true;
            }
            if (!ElytraFly.INSTANCE.mode.is(ElytraFlightModes.Control)) {
                ElytraFly.INSTANCE.mode.setMode(ElytraFlightModes.Control);
                this.changedElytraControl = true;
            }
        }
        this.flightPlanner.setDataSize(this.dataSize.getValue());
        resetState();
    }

    @Override
    protected void onDisable() {
        // 释放长矛蓄力、停止后台寻路线程，并恢复接管前的 ElytraFly 状态。
        CombatWeaponController.stopSpearUse();
        this.flightPlanner.stop();
        this.controlInput = null;
        this.target = null;
        this.latestIntent = FlightIntent.idle(Vec3.ZERO);
        if (this.changedElytraControl) {
            if (!ElytraFly.INSTANCE.mode.is(this.capturedElytraMode)) {
                ElytraFly.INSTANCE.mode.setMode(this.capturedElytraMode);
            }
            if (!this.capturedElytraEnabled && ElytraFly.INSTANCE.isEnabled()) {
                ElytraFly.INSTANCE.setEnabled(false);
            }
        }
    }

    public ElytraCombatInput getControlInput() {
        return this.controlInput;
    }

    public boolean isControllingCombat() {
        return isEnabled() && this.currentBehavior != null;
    }

    public boolean shouldUseFirework() {
        return this.allowFirework.getValue() && this.latestIntent.useFirework();
    }

    public net.minecraft.client.player.LocalPlayer player() {
        return this.mc.player;
    }

    public Vec3 playerLook() {
        return this.mc.player == null ? Vec3.ZERO : this.mc.player.getLookAngle();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        // tick 顺序：刷新目标 → 更新预测快照 → 消费命中 → 行为状态机 → 限速 → 转控制输入。
        if (nullCheck() || !canControlFlight()) {
            clearControl();
            return;
        }
        if (this.onlyWhenNoWASD.getValue() && hasMovementControl()) {
            clearControl();
            return;
        }

        refreshTarget();
        if (this.target == null) {
            clearControl();
            return;
        }

        TargetSnapshot snapshot = this.motionTracker.update(
                this.target,
                this.mc.player.tickCount,
                this.predictorMode.getValue(),
                this.predictTicks.getValue(),
                this.predictionHistory.getValue()
        );
        this.hitTracker.setContext(this.mc.player, this.target);
        processHits();

        FlightPlanConfig planConfig = new FlightPlanConfig(
                this.stopDistance.getValue(),
                this.searchRadius.getValue(),
                this.maxNodes.getValue(),
                this.pathfinding.getValue()
        );
        FlightIntent intent = this.currentBehavior.tick(
                this,
                snapshot,
                this.flightPlanner,
                planConfig
        );
        this.latestIntent = clampIntent(intent);
        this.controlInput = toInput(this.mc.player, this.latestIntent);
        this.lastFallDistance = this.mc.player.fallDistance;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKeyboardInput(KeyboardInputEvent event) {
        // 行为规划出的输入覆盖真实键盘，DirectVelocity 模式仍保留同一套 WASD 反馈。
        if (this.controlInput == null || !canControlFlight()) return;
        event.setForward(this.controlInput.forwardImpulse());
        event.setStrafe(this.controlInput.strafeImpulse());
        event.setJump(this.controlInput.jump());
        event.setSneak(this.controlInput.sneak());
        event.setSprint(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onFallFlyingMovement(FallFlyingMovementEvent event) {
        // 只有规划速度本身安全时才覆盖原版滑翔结果，否则保留 solveSafe 的旋转控制。
        if (!isEnabled() || this.controlInput == null || !this.controlInput.hasDirectVelocity()) {
            return;
        }

        Vec3 directVelocity = this.controlInput.directVelocity();
        if (this.mc.player == null || !LocalFlightAvoidance.isSegmentClear(
                this.mc.player,
                this.mc.player.position(),
                this.mc.player.position().add(directVelocity)
        )) {
            // 直接速度不安全时保留原版滑翔结果，由 solveSafe 选择的旋转接管本 tick。
            return;
        }
        event.setMovement(directVelocity);
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        // 只记录本地玩家对当前目标发起的攻击，行为状态机据此进入等待/脱战阶段。
        if (!isEnabled() || event.getPlayer() != this.mc.player || this.target == null) {
            return;
        }
        if (event.getEntity() == this.target) {
            this.hitTracker.markAttack(this.target, this.mc.player.tickCount);
            this.currentBehavior.onAttack(this.target);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // 伤害包用于重锤确认，实体事件用于 kinetic 命中；传送包只清除旧轨迹。
        if (!isEnabled()) return;
        if (event.getPacket() instanceof ClientboundDamageEventPacket damage) {
            this.hitTracker.onDamagePacket(damage);
        } else if (event.getPacket() instanceof ClientboundEntityEventPacket entityEvent) {
            this.hitTracker.onEntityStatusPacket(entityEvent);
        } else if (event.getPacket() instanceof ClientboundTeleportEntityPacket teleport) {
            this.motionTracker.noteTeleport(teleport.id());
        } else if (event.getPacket() instanceof ClientboundEntityPositionSyncPacket sync) {
            this.motionTracker.noteTeleport(sync.id());
        }
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (!isEnabled() || event.getAction() != InputConstants.PRESS) return;
        if (this.switchModeKey.getValue() == event.getKey()) {
            cycleMode();
        }
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (!isEnabled() || event.getAction() != InputConstants.PRESS) return;
        if (this.switchModeKey.getValue() == KeybindUtils.encodeMouseButton(event.getButton())) {
            cycleMode();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        // 渲染 4 格期望方向，便于区分直飞、A* 航点和避障意图。
        if (!this.render.getValue() || this.controlInput == null || this.mc.player == null) return;
        Vec3 velocity = this.latestIntent.desiredVelocity();
        if (velocity.lengthSqr() < 1.0E-8) return;

        Vec3 start = this.mc.player.position().add(0.0, this.mc.player.getBbHeight() * 0.5, 0.0);
        Render3DScheduler.INSTANCE.addLine(
                start,
                start.add(velocity.normalize().scale(4.0)),
                this.renderColor.getValue(),
                this.renderWidth.getValue().floatValue()
        );
    }

    @Override
    public String getInfo() {
        String state = this.currentBehavior == null ? "Idle" : this.currentBehavior.stateName();
        return this.target == null ? this.mode.getValue() + " " + state : this.target.getName().getString() + " " + state;
    }

    private void refreshTarget() {
        // 目标死亡、换维度或超出追击范围时立即失效；动态目标模式每 tick 重新选择。
        if (this.target != null
                && (!this.target.isAlive()
                || this.target.level() != this.mc.level
                || this.mc.player.distanceTo(this.target) > this.targetRange.getValue())) {
            this.target = null;
        }
        if (this.target == null || this.dynamicTarget.getValue()) {
            this.target = TargetManager.INSTANCE.acquirePrimary(TargetRequest.of(
                    this.targetRange.getValue(),
                    360.0f,
                    this.players.getValue(),
                    this.mobs.getValue(),
                    this.animals.getValue(),
                    this.villagers.getValue(),
                    this.ambient.getValue(),
                    this.water.getValue(),
                    this.others.getValue(),
                    this.invisible.getValue(),
                    this.followFriend.getValue(),
                    living -> living != this.mc.player,
                    1
            ));
        }
    }

    private FlightIntent clampIntent(FlightIntent intent) {
        // 行为层可以返回任意长度向量，统一限制到 Max Flight Speed 后再交给飞控。
        Vec3 velocity = intent.desiredVelocity();
        double length = velocity.length();
        if (length < 1.0E-8) {
            return new FlightIntent(Vec3.ZERO, intent.lookDirection(), intent.directVelocity(), false);
        }
        Vec3 clamped = length > this.maxFlightSpeed.getValue()
                ? velocity.scale(this.maxFlightSpeed.getValue() / length)
                : velocity;
        return new FlightIntent(clamped, clamped.normalize(), intent.directVelocity(), intent.useFirework());
    }

    private ElytraCombatInput toInput(net.minecraft.client.player.LocalPlayer player, FlightIntent intent) {
        // 输入模式先反解下一 tick 速度对齐的旋转，再按 yaw 偏差映射到 8 个 WASD 扇区。
        Vec3 velocity = intent.desiredVelocity();
        if (velocity.lengthSqr() < 1.0E-8) {
            return null;
        }

        Rot2f rotations = ElytraDirectionSolver.solveSafe(player, velocity);
        DirectionInput direction = directionInput(Mth.wrapDegrees(rotations.getYaw() - player.getYRot()));
        Vec3 direct = this.controlMode.is(ControlMode.DirectVelocity) ? velocity : null;
        return new ElytraCombatInput(
                direction.forward(),
                direction.back(),
                direction.left(),
                direction.right(),
                velocity.y > 0.0,
                velocity.y < 0.0,
                rotations.getYaw(),
                rotations.getPitch(),
                direct
        );
    }

    private DirectionInput directionInput(float yawDelta) {
        // 每个扇区覆盖 45 度，命中边界时向相邻方向同时按键以平滑转向。
        int sector = Math.floorMod(Math.round(yawDelta / 45.0f), 8);
        return switch (sector) {
            case 0 -> new DirectionInput(true, false, false, false);
            case 1 -> new DirectionInput(true, false, false, true);
            case 2 -> new DirectionInput(false, false, false, true);
            case 3 -> new DirectionInput(false, true, false, true);
            case 4 -> new DirectionInput(false, true, false, false);
            case 5 -> new DirectionInput(false, true, true, false);
            case 6 -> new DirectionInput(false, false, true, false);
            default -> new DirectionInput(true, false, true, false);
        };
    }

    private void processHits() {
        // 网络线程只入队，命中反馈在客户端 tick 中按顺序消费。
        CombatHitTracker.HitType hit;
        while ((hit = this.hitTracker.poll()) != null) {
            this.currentBehavior.onHit(hit);
            if (hit == CombatHitTracker.HitType.SPEAR && this.logSpearHit.getValue()) {
                Constants.LOGGER.info("ElytraCombat spear kinetic hit registered");
            }
        }
    }

    private boolean canControlFlight() {
        // ElytraCombat 只接管 Control 模式；其他 ElytraFly 模式保持用户手动控制。
        return ElytraFly.INSTANCE.isEnabled() && ElytraFly.INSTANCE.mode.is(ElytraFlightModes.Control);
    }

    private boolean hasMovementControl() {
        return this.mc.options.keyUp.isDown()
                || this.mc.options.keyDown.isDown()
                || this.mc.options.keyLeft.isDown()
                || this.mc.options.keyRight.isDown()
                || this.mc.options.keyJump.isDown()
                || this.mc.options.keyShift.isDown();
    }

    private void cycleMode() {
        // 切换模式时立即重置行为状态和路径请求，避免沿用上个模式的速度/状态。
        ElytraCombatMode[] modes = this.mode.getModes();
        int next = Math.floorMod(this.mode.getModeIndex() + 1, modes.length);
        this.mode.setMode(modes[next]);
        resetState();
    }

    private void onModeChanged(ElytraCombatMode newMode) {
        this.currentBehavior = this.behaviors.get(newMode);
        resetState();
    }

    private void resetState() {
        // 模式切换或状态重置时同时清空轨迹、命中队列、寻路结果和当前输入。
        this.motionTracker.reset();
        this.hitTracker.clear();
        this.flightPlanner.reset();
        this.controlInput = null;
        this.latestIntent = FlightIntent.idle(Vec3.ZERO);
        if (this.currentBehavior != null) {
            this.currentBehavior.reset();
        }
    }

    private void clearControl() {
        // 失去目标或无法接管飞行时回到无输入状态，不保留过期路径。
        this.controlInput = null;
        this.latestIntent = FlightIntent.idle(Vec3.ZERO);
        this.target = null;
        this.flightPlanner.reset();
        this.currentBehavior.reset();
    }

    private void setPathDataSize(int size) {
        // Data Size 修改后由导航器重建体素窗口；需要 applyWhenRelease 防止拖动时反复重建。
        this.flightPlanner.setDataSize(size);
    }

    private record DirectionInput(boolean forward, boolean back, boolean left, boolean right) {
    }
}
