package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.ArmRenderEvent
import com.github.epsilon.events.impl.HeldItemRenderEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.impl.EnumSetting
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.Items
import org.joml.Vector3f
import kotlin.math.PI

object HandView : Module("Hand View", Category.RENDER) {

    enum class SwingMode {
        Vanilla,
        Flux
    }

    private enum class BlockMode {
        V1_7,
        Pushdown,
        Scale,
        Leaked,
        Ninja,
        NewExhibition,
        OldExhibition
    }

    val disableSwapMain by boolSetting("Disable Swap Main", true)
    val disableSwapOff by boolSetting("Disable Swap Off", true)

    private val blockingAnimation by boolSetting("Blocking Animation", true)
    private val blockingMode: EnumSetting<BlockMode> = enumSetting(
        "Blocking Mode", BlockMode.NewExhibition, dependency = { blockingAnimation }
    )

    val swingMode: EnumSetting<SwingMode> = enumSetting("Swing Mode", SwingMode.Vanilla)
    val onlyWeapon by boolSetting(
        "Only Weapon", true, dependency = { swingMode.`is`(SwingMode.Flux) }
    )

    val modifySwingDuration by boolSetting("Modify Swing Duration", false)
    val swingDuration by intSetting(
        "Swing Duration", 6, 0, 20, 1, dependency = { modifySwingDuration }
    )

    val swingWhileUsing by boolSetting("Visual Swing On Use", true)
    val onlyOnBlock by boolSetting("Only On Block", true, dependency = { swingWhileUsing })

    private val mainHandGroup: SettingGroup = settingGroup("Main Hand")
    private val offHandGroup: SettingGroup = settingGroup("Off Hand")
    private val armGroup: SettingGroup = settingGroup("Arm")

    private val customTransform by boolSetting("Custom Transform", false)
    private val mainHandTransform: TransformSettings =
        TransformSettings("Main Hand", mainHandGroup, { customTransform })
    private val offHandTransform: TransformSettings =
        TransformSettings("Off Hand", offHandGroup, { customTransform })
    private val armTransform: TransformSettings =
        TransformSettings("Arm", armGroup, { customTransform })

    init {
        handler<HeldItemRenderEvent> {
            if (customTransform) {
                val transform = if (it.hand == InteractionHand.MAIN_HAND) mainHandTransform else offHandTransform
                transform.apply(it.poseStack)
            }
        }
        handler<ArmRenderEvent> {
            if (customTransform) armTransform.apply(it.poseStack)
        }
    }

    fun isBlocking(): Boolean {
        val player = mc.player ?: return false
        val usingMainHand = player.isUsingItem && player.usedItemHand == InteractionHand.MAIN_HAND
        return (KillAura.isEnabled && KillAura.target != null) ||
                (isBlockableWeapon(player.mainHandItem) && (mc.options.keyUse.isDown || usingMainHand))
    }

    fun shouldApplyBlockingAnimation(hand: InteractionHand, itemStack: ItemStack): Boolean =
        hand == InteractionHand.MAIN_HAND && canRenderMainHand(itemStack) && isBlocking()

    fun shouldApplyThirdPersonBlockingAnim(avatar: Avatar, arm: HumanoidArm): Boolean {
        val itemStack = avatar.getItemInHand(InteractionHand.MAIN_HAND)
        return avatar === mc.player && avatar.mainArm == arm && canRenderMainHand(itemStack) && isBlocking()
    }

    private fun canRenderMainHand(itemStack: ItemStack): Boolean =
        isEnabled && blockingAnimation && isBlockableWeapon(itemStack) && !isOffhandBlocking()

    private fun isBlockableWeapon(itemStack: ItemStack): Boolean =
        !itemStack.isEmpty && itemStack.`is`(ItemTags.WEAPON_ENCHANTABLE)

    private fun isOffhandBlocking(): Boolean {
        val player = mc.player ?: return false
        return !(!mc.options.keyUse.isDown || !player.offhandItem.`is`(Items.SHIELD)) && if (!player.isUsingItem) {
            player.mainHandItem.useAnimation == ItemUseAnimation.NONE
        } else {
            player.usedItemHand == InteractionHand.OFF_HAND
        }
    }

    fun applyBlockingTransform(poseStack: PoseStack, arm: HumanoidArm, attack: Float, inverseArmHeight: Float) {
        val side = if (arm == HumanoidArm.RIGHT) 1 else -1
        val progress = Mth.sin(Mth.sqrt(attack).toDouble() * PI)

        when (blockingMode.value) {
            BlockMode.V1_7 -> {
                poseStack.translate(-side * 0.1F, 0.1F, 0.0F)
                applyAttackTransform(poseStack, side, attack, 0.9F)
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F)
            }

            BlockMode.Pushdown -> {
                poseStack.translate(-side * 0.1F, 0.1F, 0.0F)
                poseStack.rotateDegrees(Axis.ZP, side * progress * 10.0F)
                poseStack.rotateDegrees(Axis.XP, progress * -35.0F)
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -102.25F, 13.365F, 78.05F)
            }

            BlockMode.Scale -> {
                poseStack.translate(-side * 0.16F, 0.12F, 0.16F)
                poseStack.scale(0.85F, 0.85F, 0.85F)
                applyAttackTransform(poseStack, side, attack, 0.35F)
                applyBlockPose(poseStack, side, 0.0F, 0.0F, 0.0F, -100.0F, 18.0F, 72.0F)
            }

            BlockMode.Leaked -> {
                applyAttackTransform(poseStack, side, attack, 0.6F)
                applyBlockPose(poseStack, side, -0.18F, 0.18F, 0.1F, -96.0F, 24.0F, 68.0F)
            }

            BlockMode.Ninja -> {
                applyAttackTransform(poseStack, side, attack, 0.25F)
                applyBlockPose(poseStack, side, -0.05F, 0.22F, 0.2F, -88.0F, 35.0F, 82.0F)
            }

            BlockMode.NewExhibition -> {
                applySourceBaseTransform(poseStack, side, -0.01F, attack)
                poseStack.rotateDegrees(normalizedAxis(-0.3F, 1.0F, 1.3F), side * progress * -40.0F)
                poseStack.rotateDegrees(normalizedAxis(progress / 2.0F, 0.0F, 4.0F), side * progress * 50.0F)
                poseStack.rotateDegrees(normalizedAxis(1.0F, progress / 2.0F, 0.0F), side * progress * 40.0F)
                poseStack.rotateDegrees(normalizedAxis(0.3F, 0.1F, 0.4F), side * progress * -20.7F)
                poseStack.scale(0.9F, 0.9F, 0.9F)
                applySourceBlockTransform(poseStack)
            }

            BlockMode.OldExhibition -> {
                applySourceBaseTransform(poseStack, side, inverseArmHeight * 0.6F - 0.07F, 1.0F)
                poseStack.rotateDegrees(normalizedAxis(progress / 2.0F, 0.0F, 4.0F), -progress * 50.0F / 2.0F)
                poseStack.rotateDegrees(normalizedAxis(1.0F, progress / 2.0F, 0.0F), -progress * 30.0F)
                applySourceBlockTransform(poseStack)
            }
        }
    }

    private fun normalizedAxis(x: Float, y: Float, z: Float): Axis =
        Axis.of(Vector3f(x, y, z).normalize())

    private fun applySourceBaseTransform(poseStack: PoseStack, side: Int, y: Float, swingProgress: Float) {
        poseStack.translate(side * 0.29F, y, -0.18F)
        poseStack.rotateDegrees(Axis.YP, 45.0F)
        applySourceSwingTransform(poseStack, side.toFloat(), swingProgress)
    }

    private fun applySourceSwingTransform(poseStack: PoseStack, side: Float, swingProgress: Float) {
        val ySwing = Mth.sin((swingProgress * swingProgress).toDouble() * PI)
        val xzSwing = Mth.sin(Mth.sqrt(swingProgress).toDouble() * PI)
        poseStack.rotateDegrees(Axis.YP, side * ySwing * -20.0F)
        poseStack.rotateDegrees(Axis.ZP, side * xzSwing * -20.0F)
        poseStack.rotateDegrees(Axis.XP, side * xzSwing * -80.0F)
    }

    private fun applySourceBlockTransform(poseStack: PoseStack) {
        poseStack.translate(-0.5F, 0.2F, 0.0F)
        poseStack.rotateDegrees(Axis.YP, 30.0F)
        poseStack.rotateDegrees(Axis.XP, -80.0F)
        poseStack.rotateDegrees(Axis.YP, 60.0F)
    }

    private fun applyAttackTransform(poseStack: PoseStack, side: Int, attack: Float, scale: Float) {
        val ySwingRotation = Mth.sin((attack * attack).toDouble() * PI)
        poseStack.rotateDegrees(Axis.YP, side * (45.0F + ySwingRotation * -20.0F * scale))
        val xzSwingRotation = Mth.sin(Mth.sqrt(attack).toDouble() * PI)
        poseStack.rotateDegrees(Axis.ZP, side * xzSwingRotation * -20.0F * scale)
        poseStack.rotateDegrees(Axis.XP, xzSwingRotation * -80.0F * scale)
        poseStack.rotateDegrees(Axis.YP, side * -45.0F)
    }

    private fun applyBlockPose(
        poseStack: PoseStack, side: Int, translateX: Float, translateY: Float, translateZ: Float,
        rotateX: Float, rotateY: Float, rotateZ: Float,
    ) {
        poseStack.translate(side * translateX, translateY, translateZ)
        poseStack.rotateDegrees(Axis.XP, rotateX)
        poseStack.rotateDegrees(Axis.YP, side * rotateY)
        poseStack.rotateDegrees(Axis.ZP, side * rotateZ)
    }

    private class TransformSettings(prefix: String, group: SettingGroup, enabled: Setting.Dependency) {

        private val scaleX by doubleSetting(prefix + " Scale X", 1.0, 0.0, 5.0, 0.1, dependency = enabled).apply { group(group) }
        private val scaleY by doubleSetting(prefix + " Scale Y", 1.0, 0.0, 5.0, 0.1, dependency = enabled).apply { group(group) }
        private val scaleZ by doubleSetting(prefix + " Scale Z", 1.0, 0.0, 5.0, 0.1, dependency = enabled).apply { group(group) }
        private val positionX by doubleSetting(prefix + " Position X", 0.0, -3.0, 3.0, 0.1, dependency = enabled).apply { group(group) }
        private val positionY by doubleSetting(prefix + " Position Y", 0.0, -3.0, 3.0, 0.1, dependency = enabled).apply { group(group) }
        private val positionZ by doubleSetting(prefix + " Position Z", 0.0, -3.0, 3.0, 0.1, dependency = enabled).apply { group(group) }
        private val rotationX by doubleSetting(prefix + " Rotation X", 0.0, -180.0, 180.0, 1.0, dependency = enabled).apply { group(group) }
        private val rotationY by doubleSetting(prefix + " Rotation Y", 0.0, -180.0, 180.0, 1.0, dependency = enabled).apply { group(group) }
        private val rotationZ by doubleSetting(prefix + " Rotation Z", 0.0, -180.0, 180.0, 1.0, dependency = enabled).apply { group(group) }

        fun apply(poseStack: PoseStack) {
            poseStack.rotateDegrees(Axis.XP, rotationX.toFloat())
            poseStack.rotateDegrees(Axis.YP, rotationY.toFloat())
            poseStack.rotateDegrees(Axis.ZP, rotationZ.toFloat())
            poseStack.scale(scaleX.toFloat(), scaleY.toFloat(), scaleZ.toFloat())
            poseStack.translate(positionX, positionY, positionZ)
        }

    }

}
