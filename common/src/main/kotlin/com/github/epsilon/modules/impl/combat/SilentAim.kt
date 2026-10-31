package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.SwingHandEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.HitResult

object SilentAim : Module("Silent Aim", Category.COMBAT) {

    private val weaponOnly by boolSetting("Weapon Only", false)
    private val targetPlayer by boolSetting("Player", true)
    private val mob by boolSetting("Mob", false)
    private val animal by boolSetting("Animal", false)
    private val villagers by boolSetting("Villagers", false)
    private val invisible by boolSetting("Invisible", false)
    private val range by doubleSetting("Range", 3.0, 1.0..6.0, 0.1)
    private val fov by intSetting("FOV", 360, 10..360, 1)

    private var redirecting = false
    private var target: LivingEntity? = null

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (!redirecting) return@nonNullHandler

            val currentTarget = target
            if (currentTarget == null || !currentTarget.isAlive || currentTarget.isDeadOrDying ||
                RotationUtils.getEyeDistanceToEntity(currentTarget) > range
            ) {
                redirecting = false
                return@nonNullHandler
            }

            val rotations = RotationUtils.calculate(currentTarget.eyePosition)
            RotationManager.INSTANCE.setRotations(rotations, 180.0, Priority.High)

            val hitResult = RotationManager.INSTANCE.getHitResult()
            if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
                interaction.attack(player, currentTarget)
                SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)
                redirecting = false
            }
        }

        nonNullHandler<SwingHandEvent> { event ->
            if (redirecting) return@nonNullHandler

            if (weaponOnly && !player.mainHandItem.has(DataComponents.WEAPON)) {
                return@nonNullHandler
            }

            val hitResult = mc.hitResult
            if (hitResult == null || hitResult.type != HitResult.Type.MISS) {
                return@nonNullHandler
            }

            val newTarget = TargetManager.acquirePrimary(
                TargetRequest.of(
                    range,
                    fov.toFloat(),
                    targetPlayer,
                    mob,
                    animal,
                    villagers,
                    false,
                    false,
                    false,
                    invisible,
                    1
                )
            )

            if (newTarget == null) return@nonNullHandler

            target = newTarget

            event.cancel()

            redirecting = true
        }
    }

}
