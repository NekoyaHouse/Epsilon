package com.github.epsilon.modules.impl.combat

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.SwingHandEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RotationUtils
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
    private val range by doubleSetting("Range", 3.0, 1.0, 6.0, 0.1)
    private val fov by intSetting("FOV", 360, 10, 360, 1)

    private var redirecting = false
    private var target: LivingEntity? = null

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (!redirecting) return@nonNullListener

            val currentTarget = target
            if (currentTarget == null || !currentTarget.isAlive || currentTarget.isDeadOrDying ||
                RotationUtils.getEyeDistanceToEntity(currentTarget) > range
            ) {
                redirecting = false
                return@nonNullListener
            }

            val rotations = RotationUtils.calculate(currentTarget.eyePosition)
            RotationManager.INSTANCE.setRotations(rotations, 180.0, Priority.High)

            val hitResult = RotationManager.INSTANCE.getHitResult()
            if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
                interaction.attack(player, currentTarget)
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                redirecting = false
            }
        }

        nonNullListener<SwingHandEvent> { event ->
            if (redirecting) return@nonNullListener

            if (weaponOnly && !player.mainHandItem.has(DataComponents.WEAPON)) {
                return@nonNullListener
            }

            val hitResult = mc.hitResult
            if (hitResult == null || hitResult.type != HitResult.Type.MISS) {
                return@nonNullListener
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

            if (newTarget == null) return@nonNullListener

            target = newTarget

            event.cancel()

            redirecting = true
        }
    }

}
