package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.interfaces.ClientboundEntityEventPacketAccessor;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.render.esp.CaptureMarkESP;
import com.github.epsilon.utils.render.esp.CircleESP;
import com.github.epsilon.utils.render.esp.FireflyESP;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.List;

/** 长矛自动蓄力与瞄准，kinetic 命中后延迟一 tick 尝试重锤补刀。 */
public class SpearAura extends Module {

    public static final SpearAura INSTANCE = new SpearAura();

    /** 与目标小于 3 格时不再转头/移动，避免贴脸转头。 */
    private static final double SPEAR_MIN_TRACK_DISTANCE = 3.0;

    /** 重锤补刀只在摔落高度大于 3 格时触发。 */
    private static final double MACE_MIN_FALL_DISTANCE = 3.0;

    /** 命中后停止瞄准/移动的 tick 数，覆盖目标无敌帧。 */
    private static final int HIT_PAUSE_TICKS = 10;

    /** kinetic 伤害所需的最小相对速度，单位米/秒。 */
    private static final double SPEAR_MIN_RELATIVE_SPEED = 4.6;

    /** 补发左键点击的最大等待 tick 数。 */
    private static final int ATTACK_CLICK_RETRY_TICKS = 10;

    private static final int MAX_TARGETS = 64;

    private SpearAura() {
        super("Spear Aura", Category.COMBAT);
    }

    private enum MaceSwapMode {
        Normal,
        Silent,
        InvSwitch
    }

    /** 目标 ESP 样式；DeobfESP 由 KillAura 全局驱动，不在此重复渲染。 */
    private enum ESPMode {
        CaptureMark,
        Circle,
        Firefly
    }

    private final DoubleSetting range = doubleSetting("Range", 4.0, 0.0, 6.0, 0.1);
    private final IntSetting fov = intSetting("FOV", 360, 10, 360, 1);
    private final BoolSetting autoCharge = boolSetting("Auto Charge", true);
    private final BoolSetting move = boolSetting("Move", true);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 10, 180, 10);
    private final EnumSetting<Priority> rotationPriority = enumSetting("Rotation Priority", Priority.High);

    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting mobs = boolSetting("Mobs", true);
    private final BoolSetting animals = boolSetting("Animals", true);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting invisible = boolSetting("Invisible", true);

    private final BoolSetting mace = boolSetting("Mace", true);
    private final EnumSetting<MaceSwapMode> maceSwapMode = enumSetting("Mace Swap Mode", MaceSwapMode.Silent, mace::getValue);

    private final BoolSetting esp = boolSetting("ESP", true);
    private final EnumSetting<ESPMode> espMode = enumSetting("ESP Mode", ESPMode.Circle, esp::getValue);
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

    /** 本 tick 生效的静默瞄准角度；非 null 表示正在瞄准，移动按它结算。 */
    private Rot2f aimRotations;
    /** 本 tick 是否允许在没有输入时模拟按 W 沿瞄准方向前进。 */
    private boolean forceForward;
    private boolean aimActive;

    /** 长矛 kinetic 命中包来自网络线程，先转成客户端 tick 消费的状态。 */
    private volatile boolean spearHitPending;
    private volatile int localPlayerId = -1;
    /** 长矛命中后延迟 1 tick 再补重锤，避免和 kinetic 命中同一 tick 处理。 */
    private int spearMaceDelay;
    /** 命中后暂停瞄准/移动，等待目标无敌帧结束。 */
    private int hitPauseTicks;

    private boolean autoChargeWanted;
    /** 本模块是否替玩家按住了右键；只有本字段为 true 时才允许松开按键。 */
    private boolean autoChargeKeyHeld;
    /** 蓄力期间被原版消耗的左键点击，使用状态结束后补发；0 表示无待补点击。 */
    private int pendingAttackClick;

    @Override
    public String getInfo() {
        return target == null ? null : target.getName().getString();
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        localPlayerId = mc.player.getId();
        forceForward = false;
        // 蓄力按键在低优先级监听器同步，晚于本 tick 的瞄准请求。
        autoChargeWanted = isHoldingSpear();

        targets = TargetManager.INSTANCE.acquireTargets(TargetRequest.of(
                range.getValue(),
                fov.getValue().floatValue(),
                players.getValue(),
                mobs.getValue(),
                animals.getValue(),
                villagers.getValue(),
                false,
                false,
                false,
                invisible.getValue(),
                MAX_TARGETS
        ));

        if (targets.isEmpty()) {
            target = null;
            aimRotations = null;
            aimActive = false;
            return;
        }

        target = targets.getFirst();
        aimActive = isHoldingSpear() || isUsingSpear();
        if (!aimActive) {
            aimRotations = null;
            return;
        }

        // 命中后的无敌帧窗口内只暂停，不再瞄准/移动；重锤补刀窗口例外。蓄力不受影响。
        if (hitPauseTicks > 0 && spearMaceDelay <= 0) {
            hitPauseTicks--;
            aimRotations = null;
            return;
        }

        if (RotationUtils.getEyeDistanceToEntity(target) <= SPEAR_MIN_TRACK_DISTANCE) {
            aimRotations = null;
            return;
        }

        if (getRelativeSpeedTo(target) <= SPEAR_MIN_RELATIVE_SPEED) {
            aimRotations = null;
            return;
        }

        forceForward = move.getValue();

        aimRotations = RotationUtils.calculate(target, true, range.getValue());
        if (RaytraceUtils.raytrace(aimRotations, range.getValue()).getType() == HitResult.Type.BLOCK) {
            aimRotations = null;
            return;
        }
        RotationManager.INSTANCE.setRotations(
                aimRotations,
                rotationSpeed.getValue(),
                rotation -> RaytraceUtils.raytrace(rotation, range.getValue()) instanceof EntityHitResult hitResult && hitResult.getEntity() == target,
                rotationPriority.getValue()
        );
    }

    /** 沿托管旋转方向移动，直接使用原始 WASD 输入，避免与 MovementFix 重复旋转。 */
    @EventHandler(priority = EventPriority.LOWEST)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (nullCheck()) return;
        if (!aimActive || !move.getValue() || aimRotations == null) return;

        float forward = (mc.options.keyUp.isDown() ? 1.0f : 0.0f) - (mc.options.keyDown.isDown() ? 1.0f : 0.0f);
        float strafe = (mc.options.keyLeft.isDown() ? 1.0f : 0.0f) - (mc.options.keyRight.isDown() ? 1.0f : 0.0f);

        if (forward == 0.0f && strafe == 0.0f) {
            if (!forceForward) return;
            forward = 1.0f;
        }

        event.setForward(forward);
        event.setStrafe(strafe);
        event.setSprint(true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || !esp.getValue() || target == null || !target.isAlive()) return;

        PoseStack stack = event.getPoseStack();

        switch (espMode.getValue()) {
            case CaptureMark -> CaptureMarkESP.render(
                    stack,
                    target,
                    espSize.getValue(),
                    espRotSpeed.getValue(),
                    waveSpeed.getValue(),
                    espColor1.getValue(),
                    espColor2.getValue()
            );
            case Circle -> CircleESP.render(
                    stack,
                    target,
                    circleRadius.getValue().floatValue(),
                    sideColor.getValue(),
                    lineColor.getValue(),
                    circleAlphaFactor.getValue().floatValue()
            );
            case Firefly -> FireflyESP.render(
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

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || !(event.getPacket() instanceof ClientboundEntityEventPacket packet)) {
            return;
        }
        if (packet.getEventId() != EntityEvent.KINETIC_HIT) return;
        if (packet instanceof ClientboundEntityEventPacketAccessor accessor
                && accessor.epsilon$getEntityId() == localPlayerId) {
            spearHitPending = true;
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) {
            resetState();
            return;
        }
        if (spearHitPending) {
            spearHitPending = false;
            if (isUsingSpear()) {
                mc.gameMode.releaseUsingItem(mc.player);
            }
            spearMaceDelay = 1;
            forceForward = false;
            // 命中包可能在 ClientTickEvent.Pre 之后才到达，这里再松一次按键，防止下一 tick 原版重新起手。
            stopAutoCharge();
            return;
        }

        if (spearMaceDelay > 0) {
            if (--spearMaceDelay == 0) {
                if (isUsingSpear()) {
                    mc.gameMode.releaseUsingItem(mc.player);
                }
                if (target != null && target.isAlive()) {
                    attackWithMace(target);
                }
                hitPauseTicks = HIT_PAUSE_TICKS;
                forceForward = false;
                stopAutoCharge();
            }
        }
    }

    /**
     * 通过按住右键维持蓄力，左键攻击时让位并补发点击。
     * 必须晚于 {@link #onClientTick} 执行，避免读取上一 tick 的状态。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    private void onClientTickAutoCharge(ClientTickEvent.Pre event) {
        if (nullCheck()) {
            pendingAttackClick = 0;
            stopAutoCharge();
            return;
        }

        boolean attackDown = mc.options.keyAttack.isDown();

        if (attackDown && autoChargeKeyHeld && mc.player.isUsingItem()) {
            pendingAttackClick = ATTACK_CLICK_RETRY_TICKS;
        }

        if (attackDown) {
            stopAutoCharge();
        }

        if (pendingAttackClick > 0) {
            // 等待使用状态结束期间保持右键释放。
            if (!mc.player.isUsingItem()) {
                KeyMapping.click(mc.options.keyAttack.key);
                pendingAttackClick = 0;
            } else {
                pendingAttackClick--;
            }
            return;
        }

        if (attackDown) return;

        if (!autoCharge.getValue() || !autoChargeWanted
                || spearHitPending || spearMaceDelay > 0 || !isHoldingSpear()) {
            stopAutoCharge();
            return;
        }

        if (!mc.options.keyUse.isDown()) {
            mc.options.keyUse.setDown(true);
            autoChargeKeyHeld = true;
        }
    }

    /**
     * 松开自动蓄力按住的右键；玩家自己按住的键不处理。
     */
    private void stopAutoCharge() {
        if (!autoChargeKeyHeld) return;
        autoChargeKeyHeld = false;
        // 配置恢复可能早于 Minecraft 构造完成，此时还没有 Options。
        if (mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
    }

    private void attackEntity(Entity entity) {
        mc.gameMode.attack(mc.player, entity);
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND);
    }

    /**
     * 长矛 kinetic 命中后立刻切换重锤再补一次攻击。
     * <p>
     * Normal 保留切换结果，Silent 只静默切换快捷栏，InvSwitch 通过容器交换从背包取物。
     */
    private void attackWithMace(Entity entity) {
        if (!mace.getValue() || mc.player.fallDistance <= MACE_MIN_FALL_DISTANCE || entity == null || !entity.isAlive()) return;

        FindItemResult maceResult = findMace();
        if (!maceResult.found()) return;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (maceResult.slot() == selectedSlot) {
            attackEntity(entity);
            return;
        }

        switch (maceSwapMode.getValue()) {
            case Normal, Silent -> mc.player.getInventory().setSelectedSlot(maceResult.slot());
            case InvSwitch -> InvUtils.invSwap(maceResult.slot());
        }

        attackEntity(entity);

        switch (maceSwapMode.getValue()) {
            case Normal -> {
            }
            case Silent -> mc.player.getInventory().setSelectedSlot(selectedSlot);
            case InvSwitch -> InvUtils.invSwapBack();
        }
    }

    private FindItemResult findMace() {
        // 攻击只结算主手，因此排除副手槽位 40；Silent 只能操作快捷栏，InvSwitch 才搜索整个主背包。
        if (maceSwapMode.is(MaceSwapMode.InvSwitch)) {
            return InvUtils.find(stack -> stack.is(Items.MACE), 0, 35);
        }
        return InvUtils.find(stack -> stack.is(Items.MACE), 0, 8);
    }

    private boolean isHoldingSpear() {
        return mc.player != null && mc.player.getMainHandItem().is(ItemTags.SPEARS);
    }

    private boolean isUsingSpear() {
        return mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem().is(ItemTags.SPEARS);
    }

    /** 沿眼部到目标方向投影双方速度差，单位米/秒。 */
    private double getRelativeSpeedTo(Entity entity) {
        Rot2f look = RotationUtils.getRotationsToEntity(entity);
        Vec3 direction = Vec3.directionFromRotation(look.getPitch(), look.getYaw());
        Vec3 relativeMotion = mc.player.getKnownSpeed().subtract(entity.getKnownSpeed()).scale(20.0);
        return Math.max(0.0, direction.dot(relativeMotion));
    }

    private void resetState() {
        target = null;
        targets = null;
        aimRotations = null;
        forceForward = false;
        aimActive = false;
        spearHitPending = false;
        spearMaceDelay = 0;
        hitPauseTicks = 0;
        autoChargeWanted = false;
        stopAutoCharge();
        pendingAttackClick = 0;
        localPlayerId = -1;
    }
}
