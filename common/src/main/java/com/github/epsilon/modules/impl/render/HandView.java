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
import org.joml.Vector3f;

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
        NewExhibition,
        OldExhibition
    }

    public final BoolSetting disableSwapMain = boolSetting("Disable Swap Main", true);
    public final BoolSetting disableSwapOff = boolSetting("Disable Swap Off", true);

    private final BoolSetting blockingAnimation = boolSetting("Blocking Animation", true);
    private final EnumSetting<BlockMode> blockingMode = enumSetting("Blocking Mode", BlockMode.NewExhibition, blockingAnimation::getValue);

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
        boolean usingMainHand = mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        return (killAura.isEnabled() && killAura.target != null) || (isBlockableWeapon(mc.player.getMainHandItem()) && (mc.options.keyUse.isDown() || usingMainHand));
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

    public void applyBlockingTransform(PoseStack poseStack, HumanoidArm arm, float attack, float inverseArmHeight) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float progress = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);

        switch (blockingMode.getValue()) {
            case V1_7 -> {
                poseStack.translate(-side * 0.1F, 0.1F, 0.0F);
                applyAttackTransform(poseStack, side, attack, 0.9F);
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
            }
            case Pushdown -> {
                poseStack.translate(-side * 0.1F, 0.1F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * progress * 10.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(progress * -35.0F));
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F);
            }
            case Scale -> {
                poseStack.translate(-side * 0.16F, 0.12F, 0.16F);
                poseStack.scale(0.85F, 0.85F, 0.85F);
                applyAttackTransform(poseStack, side, attack, 0.35F);
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -100.0F, 18.0F, 72.0F);
            }
            case Leaked -> {
                applyAttackTransform(poseStack, side, attack, 0.6F);
                applyBlockPose(poseStack, side, -0.18F, 0.18F, 0.1F, -96.0F, 24.0F, 68.0F);
            }
            case Ninja -> {
                applyAttackTransform(poseStack, side, attack, 0.25F);
                applyBlockPose(poseStack, side, -0.05F, 0.22F, 0.2F, -88.0F, 35.0F, 82.0F);
            }
            case NewExhibition -> {
                applySourceBaseTransform(poseStack, side, -0.01F, attack);
                poseStack.mulPose(Axis.of(new Vector3f(-0.3F, 1.0F, 1.3F)).rotationDegrees(side * progress * -40.0F));
                poseStack.mulPose(Axis.of(new Vector3f(progress / 2.0F, 0.0F, 4.0F)).rotationDegrees(side * progress * 50.0F));
                poseStack.mulPose(Axis.of(new Vector3f(1.0F, progress / 2.0F, 0.0F)).rotationDegrees(side * progress * 40.0F));
                poseStack.mulPose(Axis.of(new Vector3f(0.3F, 0.1F, 0.4F)).rotationDegrees(side * progress * -20.7F));
                poseStack.scale(0.9F, 0.9F, 0.9F);
                applySourceBlockTransform(poseStack);
            }
            case OldExhibition -> {
                applySourceBaseTransform(poseStack, side, inverseArmHeight * 0.6F - 0.07F, 1.0F);
                poseStack.mulPose(Axis.of(new Vector3f(progress / 2.0F, 0.0F, 4.0F)).rotationDegrees(-progress * 50.0F / 2.0F));
                poseStack.mulPose(Axis.of(new Vector3f(1.0F, progress / 2.0F, 0.0F)).rotationDegrees(-progress * 30.0F));
                applySourceBlockTransform(poseStack);
            }
        }
    }

    private void applySourceBaseTransform(PoseStack poseStack, int side, float y, float swingProgress) {
        poseStack.translate(side * 0.29F, y, -0.18F);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        applySourceSwingTransform(poseStack, side, swingProgress);
    }

    private void applySourceSwingTransform(PoseStack poseStack, float side, float swingProgress) {
        float ySwing = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float xzSwing = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * ySwing * -20.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * xzSwing * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(side * xzSwing * -80.0F));
    }

    private void applySourceBlockTransform(PoseStack poseStack) {
        poseStack.translate(-0.5F, 0.2F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(60.0F));
    }

    private void applyAttackTransform(PoseStack poseStack, int side, float attack, float scale) {
        float ySwingRotation = Mth.sin(attack * attack * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + ySwingRotation * -20.0F * scale)));
        float xzSwingRotation = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * xzSwingRotation * -20.0F * scale));
        poseStack.mulPose(Axis.XP.rotationDegrees(xzSwingRotation * -80.0F * scale));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
    }

    private void applyBlockPose(PoseStack poseStack, int side, float translateX, float translateY, float translateZ, float rotateX, float rotateY, float rotateZ) {
        poseStack.translate(side * translateX, translateY, translateZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotateX));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * rotateY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * rotateZ));
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
