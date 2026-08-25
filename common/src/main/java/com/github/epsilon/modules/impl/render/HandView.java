package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ArmRenderEvent;
import com.github.epsilon.events.impl.HeldItemRenderEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

public class HandView extends Module {

    public static final HandView INSTANCE = new HandView();

    private HandView() {
        super("Hand View", Category.RENDER);
    }

    public enum SwingMode {
        Vanilla,
        Flux
    }

    private enum BlockMode {
        V1_7,
        Pushdown,
        Scale,
        Leaked,
        Ninja,
        Down
    }

    public final BoolSetting disableSwapMain = boolSetting("Disable Swap Main", true);
    public final BoolSetting disableSwapOff = boolSetting("Disable Swap Off", true);

    private final BoolSetting blockingAnimation = boolSetting("Blocking Animation", true);
    private final EnumSetting<BlockMode> blockingMode = enumSetting("Blocking Mode", BlockMode.V1_7, blockingAnimation::getValue);

    public final EnumSetting<SwingMode> swingMode = enumSetting("Swing Mode", SwingMode.Vanilla);
    public final BoolSetting onlyWeapon = boolSetting("Only Weapon", true, () -> swingMode.is(SwingMode.Flux));

    public final BoolSetting modifySwingDuration = boolSetting("Modify Swing Duration", false);
    public final IntSetting swingDuration = intSetting("Swing Duration", 6, 0, 20, 1, modifySwingDuration::getValue);

    public final BoolSetting swingWhileUsing = boolSetting("Visual Swing On Use", true);
    public final BoolSetting onlyOnBlock = boolSetting("Only On Block", true, swingWhileUsing::getValue);

    private final SettingGroup mainHandGroup = settingGroup("Main Hand");
    private final SettingGroup offHandGroup = settingGroup("Off Hand");
    private final SettingGroup armGroup = settingGroup("Arm");

    private final BoolSetting customTransform = boolSetting("Custom Transform", false);
    private final TransformSettings mainHandTransform = new TransformSettings("Main Hand", mainHandGroup);
    private final TransformSettings offHandTransform = new TransformSettings("Off Hand", offHandGroup);
    private final TransformSettings armTransform = new TransformSettings("Arm", armGroup);

    @EventHandler
    private void onHeldItemRender(HeldItemRenderEvent event) {
        if (customTransform.getValue()) {
            TransformSettings transform = event.hand() == InteractionHand.MAIN_HAND ? mainHandTransform : offHandTransform;
            transform.apply(event.poseStack());
        }
    }

    @EventHandler
    private void onArmRender(ArmRenderEvent event) {
        if (customTransform.getValue()) armTransform.apply(event.poseStack());
    }

    public boolean isBlocking() {
        KillAura killAura = KillAura.INSTANCE;
        return (killAura.isEnabled() && killAura.target != null) || (isBlockableWeapon(mc.player.getMainHandItem()) && mc.options.keyUse.isDown());
    }

    public boolean shouldApplyBlockingAnimation(InteractionHand hand, ItemStack itemStack) {
        return hand == InteractionHand.MAIN_HAND && canRenderMainHand(itemStack) && isBlocking();
    }

    public boolean shouldApplyThirdPersonBlockingAnim(Avatar avatar, HumanoidArm arm) {
        ItemStack itemStack = avatar.getItemInHand(InteractionHand.MAIN_HAND);
        return avatar == mc.player && avatar.getMainArm() == arm && canRenderMainHand(itemStack) && isBlocking();
    }

    private boolean canRenderMainHand(ItemStack itemStack) {
        return isEnabled() && blockingAnimation.getValue() && isBlockableWeapon(itemStack) && !isOffhandBlocking();
    }

    private boolean isBlockableWeapon(ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.is(ItemTags.WEAPON_ENCHANTABLE);
    }

    private boolean isOffhandBlocking() {
        if (!mc.options.keyUse.isDown() || !mc.player.getOffhandItem().is(Items.SHIELD)) {
            return false;
        }
        return !mc.player.isUsingItem() ? mc.player.getMainHandItem().getUseAnimation() == ItemUseAnimation.NONE : mc.player.getUsedItemHand() == InteractionHand.OFF_HAND;
    }

    public void applyBlockingTransform(PoseStack poseStack, HumanoidArm arm, float attack) {
        switch (blockingMode.getValue()) {
            case V1_7 -> {
                poseStack.translate((arm == HumanoidArm.RIGHT ? -0.1F : 0.1F), 0.1F, 0.0F);
                applyAttackTransform(poseStack, arm, attack, 0.9F);
                applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
            }
            case Pushdown -> {
                poseStack.translate((arm == HumanoidArm.RIGHT ? -0.1F : 0.1F), 0.1F, 0.0F);
                float swing = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
                poseStack.mulPose(Axis.ZP.rotationDegrees((arm == HumanoidArm.RIGHT ? 1 : -1) * swing * 10.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(swing * -35.0F));
                applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
            }
            case Scale -> {
                poseStack.translate((arm == HumanoidArm.RIGHT ? -0.16F : 0.16F), 0.12F, 0.16F);
                poseStack.scale(0.85F, 0.85F, 0.85F);
                applyAttackTransform(poseStack, arm, attack, 0.35F);
                applyBlockPose(poseStack, arm, 0.0F, 0.0F, 0.0F, -100.0F, 18.0F, 72.0F);
            }
            case Leaked -> {
                applyAttackTransform(poseStack, arm, attack, 0.6F);
                applyBlockPose(poseStack, arm, -0.18F, 0.18F, 0.1F, -96.0F, 24.0F, 68.0F);
            }
            case Ninja -> {
                applyAttackTransform(poseStack, arm, attack, 0.25F);
                applyBlockPose(poseStack, arm, -0.05F, 0.22F, 0.2F, -88.0F, 35.0F, 82.0F);
            }
            case Down -> {
                applyAttackTransform(poseStack, arm, attack, 0.2F);
                applyBlockPose(poseStack, arm, -0.25F, -0.02F, 0.05F, -120.0F, 8.0F, 72.0F);
            }
        }
    }

    private void applyAttackTransform(PoseStack poseStack, HumanoidArm arm, float attack, float scale) {
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        float ySwingRotation = Mth.sin(attack * attack * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * (45.0F + ySwingRotation * -20.0F * scale)));
        float xzSwingRotation = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * xzSwingRotation * -20.0F * scale));
        poseStack.mulPose(Axis.XP.rotationDegrees(xzSwingRotation * -80.0F * scale));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * -45.0F));
    }

    private void applyBlockPose(PoseStack poseStack, HumanoidArm arm, float translateX, float translateY, float translateZ, float rotateX, float rotateY, float rotateZ) {
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(invert * translateX, translateY, translateZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotateX));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * rotateY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * rotateZ));
    }

    private class TransformSettings {
        private final DoubleSetting scaleX;
        private final DoubleSetting scaleY;
        private final DoubleSetting scaleZ;
        private final DoubleSetting positionX;
        private final DoubleSetting positionY;
        private final DoubleSetting positionZ;
        private final DoubleSetting rotationX;
        private final DoubleSetting rotationY;
        private final DoubleSetting rotationZ;

        private TransformSettings(String prefix, SettingGroup group) {
            Setting.Dependency enabled = customTransform::getValue;
            scaleX = doubleSetting(prefix + " Scale X", 1.0, 0.0, 5.0, 0.1, enabled).group(group);
            scaleY = doubleSetting(prefix + " Scale Y", 1.0, 0.0, 5.0, 0.1, enabled).group(group);
            scaleZ = doubleSetting(prefix + " Scale Z", 1.0, 0.0, 5.0, 0.1, enabled).group(group);
            positionX = doubleSetting(prefix + " Position X", 0.0, -3.0, 3.0, 0.1, enabled).group(group);
            positionY = doubleSetting(prefix + " Position Y", 0.0, -3.0, 3.0, 0.1, enabled).group(group);
            positionZ = doubleSetting(prefix + " Position Z", 0.0, -3.0, 3.0, 0.1, enabled).group(group);
            rotationX = doubleSetting(prefix + " Rotation X", 0.0, -180.0, 180.0, 1.0, enabled).group(group);
            rotationY = doubleSetting(prefix + " Rotation Y", 0.0, -180.0, 180.0, 1.0, enabled).group(group);
            rotationZ = doubleSetting(prefix + " Rotation Z", 0.0, -180.0, 180.0, 1.0, enabled).group(group);
        }

        private void apply(PoseStack poseStack) {
            poseStack.mulPose(Axis.XP.rotationDegrees(rotationX.getValue().floatValue()));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationY.getValue().floatValue()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotationZ.getValue().floatValue()));
            poseStack.scale(scaleX.getValue().floatValue(), scaleY.getValue().floatValue(), scaleZ.getValue().floatValue());
            poseStack.translate(positionX.getValue(), positionY.getValue(), positionZ.getValue());
        }
    }

}
