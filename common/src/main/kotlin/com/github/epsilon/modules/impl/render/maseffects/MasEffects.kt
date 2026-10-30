package com.github.epsilon.modules.impl.render.maseffects

import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.utils.player
import com.github.epsilon.utils.world
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.enchantment.ItemEnchantments
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.phys.Vec3
import java.awt.Color

object MasEffects : Module("Mas Effects", Category.RENDER) {

    private val GLIDE_ICON: Identifier =
        ResourceLocationUtils.getIdentifier("textures/maseffects/hud/elytra_icon.png")

    private val generalGroup: SettingGroup = settingGroup("General")
    private val maceGroup: SettingGroup = settingGroup("Mace")
    private val hitboxGroup: SettingGroup = settingGroup("Hitboxes")

    private val windParticles by boolSetting("Wind Particles", true).group(generalGroup)
    private val customTotemEffect by boolSetting("Custom Totem Effect", true).group(generalGroup)
    private val totemEffectOpacity by doubleSetting(
        "Totem Effect Opacity", 1.0, 0.0, 1.0, 0.05, Setting.Dependency { customTotemEffect }
    ).group(generalGroup)
    private val playerDeathEffect by boolSetting("Player Death Effect", true).group(generalGroup)
    private val pearlTrailParticles by boolSetting("Pearl Trail Particles", true).group(generalGroup)
    private val pearlTrailOpacity by doubleSetting(
        "Pearl Trail Opacity", 0.5, 0.0, 1.0, 0.05, Setting.Dependency { pearlTrailParticles }
    ).group(generalGroup)
    private val glideIcon by boolSetting("Glide Icon", true).group(generalGroup)

    private val maceShockwave by boolSetting("Mace Shockwave", true).group(maceGroup)
    private val maceShockwaveSize by doubleSetting(
        "Mace Shockwave Size", 1.0, 0.1, 3.0, 0.05, Setting.Dependency { maceShockwave }
    ).group(maceGroup)
    private val maceShockwaveOpacity by doubleSetting(
        "Mace Shockwave Opacity", 1.0, 0.0, 1.0, 0.05
    ).group(maceGroup)
    private val maceSpark by boolSetting("Mace Spark", true).group(maceGroup)
    private val maceFlash by boolSetting("Mace Flash", true).group(maceGroup)
    private val shieldEffect by boolSetting("Shield Effect", true).group(maceGroup)
    private val armorParticles by boolSetting("Armor Particles", true).group(maceGroup)
    private val legacyMaceShockwave by boolSetting(
        "Legacy Mace Shockwave", false, Setting.Dependency { maceShockwave }
    ).group(maceGroup)

    private val customHitbox by boolSetting("Custom Hitbox", true).group(hitboxGroup)
    private val pearlHitboxColors by boolSetting(
        "Pearl Hitbox Colors", true, Setting.Dependency { customHitbox }
    ).group(hitboxGroup)
    private val pearlAllies by stringListSetting(
        "Pearl Allies", listOf(), Setting.Dependency { customHitbox && pearlHitboxColors }
    ).group(hitboxGroup)
    private val selfPearlColor by colorSetting(
        "Self Pearl Color", Color(255, 255, 0, 179), true,
        Setting.Dependency { customHitbox && pearlHitboxColors }
    ).group(hitboxGroup)
    private val allyPearlColor by colorSetting(
        "Ally Pearl Color", Color(0, 0, 255, 179), true,
        Setting.Dependency { customHitbox && pearlHitboxColors }
    ).group(hitboxGroup)
    private val otherPearlColor by colorSetting(
        "Other Pearl Color", Color(255, 0, 0, 179), true,
        Setting.Dependency { customHitbox && pearlHitboxColors }
    ).group(hitboxGroup)
    private val mobHitboxOpacity by doubleSetting(
        "Mob Hitbox Opacity", 0.3, 0.0, 1.0, 0.05, Setting.Dependency { customHitbox }
    ).group(hitboxGroup)
    private val playerHitboxOpacity by doubleSetting(
        "Player Hitbox Opacity", 1.0, 0.0, 1.0, 0.05, Setting.Dependency { customHitbox }
    ).group(hitboxGroup)
    private val hitboxFadeDistance by doubleSetting(
        "Hitbox Fade Distance", 15.0, 0.0, 64.0, 0.5, Setting.Dependency { customHitbox }
    ).group(hitboxGroup)
    private val projectileFadeDistance by doubleSetting(
        "Projectile Fade Distance", 5.0, 0.0, 64.0, 0.5, Setting.Dependency { customHitbox }
    ).group(hitboxGroup)

    private val particleRenderer = MasEffectsParticleRenderer()

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            particleRenderer.tick()

            if (playerDeathEffect) {
                for (target in world.players()) {
                    if (target.deathTime == 1) {
                        particleRenderer.spawnDeath(target.position())
                    }
                }
            }

            if (pearlTrailParticles) {
                for (entity in world.entitiesForRendering()) {
                    if (entity is ThrownEnderpearl) {
                        particleRenderer.spawnPearlTrail(entity)
                    }
                }
            }
        }

        nonNullListener<AttackEntityEvent> {
            if (it.player !== player || !it.player.mainHandItem.`is`(Items.MACE)) return@nonNullListener

            val victim = it.entity
            val shielding = victim is Player &&
                    it.player.fallDistance >= 1.5F &&
                    isShielding(it.player, victim)

            if (shielding && shieldEffect) {
                particleRenderer.spawnShieldWave(victim as Player)
            } else if (victim is LivingEntity &&
                armorParticles &&
                it.player.fallDistance > 1.5F &&
                it.player.mainHandItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                    .keySet().stream().anyMatch { holder -> holder.`is`(Enchantments.BREACH) }
            ) {
                particleRenderer.spawnArmorParticles(victim)
            }
        }

        handler<PacketEvent.Receive> { event ->
            val packet = event.packet
            if (packet !is ClientboundEntityEventPacket ||
                packet.eventId != EntityEvent.PROTECTED_FROM_DEATH ||
                !customTotemEffect
            ) {
                return@handler
            }

            mc.execute {
                if (!isEnabled || !customTotemEffect) return@execute
                val level = mc.level ?: return@execute
                val entity = packet.getEntity(level)
                if (entity != null) {
                    particleRenderer.spawnTotem(entity)
                }
            }
        }

        handler<Render3DEvent> {
            particleRenderer.render(it.poseStack)
        }

        handler<Render2DEvent.HUD> { event ->
            val player = mc.player
            if (player == null || !glideIcon || !player.isFallFlying) return@handler

            event.guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                GLIDE_ICON,
                event.guiGraphics.guiWidth() / 2 - 5,
                event.guiGraphics.guiHeight() / 2 + 7,
                0.0F,
                0.0F,
                9,
                9,
                9,
                9
            )
        }

        handler<LevelUpdateEvent> {
            particleRenderer.clear()
        }
    }

    override fun onDisable() {
        particleRenderer.clear()
    }

    fun onLevelEvent(type: Int, position: Vec3) {
        if (isEnabled && type == LevelEvent.PARTICLES_SMASH_ATTACK) {
            particleRenderer.spawnSlam(position, 5.0)
        }
    }

    fun onWindSeed(x: Double, y: Double, z: Double) {
        if (isEnabled && windParticles) {
            particleRenderer.spawnWindWave(Vec3(x, y, z), 0.3F, 1.0F, 5.0F)
        }
    }

    fun renderCustomHitbox(entity: Entity, partialTicks: Float): Boolean {
        val player = mc.player ?: return false
        if (!isEnabled || !customHitbox) return false

        if (entity is Player) {
            val fade = distanceFade(entity, hitboxFadeDistance.toFloat())
            val color = ARGB.colorFromFloat((1.0F - fade) * playerHitboxOpacity.toFloat(), 1.0F, 1.0F, 1.0F)
            drawHitbox(entity, partialTicks, color)
            return true
        } else if (entity is ThrownEnderpearl) {
            val fade = distanceFade(entity, projectileFadeDistance.toFloat())
            val selected = if (pearlHitboxColors) pearlColor(entity) else Color(255, 255, 0)
            val alpha = Math.round(fade * selected.alpha)
            drawHitbox(entity, partialTicks, ARGB.color(alpha, selected.red, selected.green, selected.blue))
            return true
        } else if (entity is WindCharge) {
            val fade = distanceFade(entity, projectileFadeDistance.toFloat())
            drawHitbox(entity, partialTicks, ARGB.colorFromFloat(fade, 0.9F, 0.9F, 1.0F))
            return true
        } else if (entity is LivingEntity) {
            val fade = distanceFade(entity, hitboxFadeDistance.toFloat())
            val color = ARGB.colorFromFloat((1.0F - fade) * mobHitboxOpacity.toFloat(), 1.0F, 1.0F, 1.0F)
            drawHitbox(entity, partialTicks, color)
            return true
        }

        return false
    }

    fun shouldHideVanillaTotemParticles(): Boolean = isEnabled && customTotemEffect

    fun getMaceShockwaveOpacity(): Float = maceShockwaveOpacity.toFloat()

    fun getTotemEffectOpacity(): Float = totemEffectOpacity.toFloat()

    fun getPearlTrailOpacity(): Float = pearlTrailOpacity.toFloat()

    fun isMaceShockwaveEnabled(): Boolean = maceShockwave

    fun getMaceShockwaveSize(): Float = maceShockwaveSize.toFloat()

    fun isLegacyMaceShockwave(): Boolean = legacyMaceShockwave

    fun isMaceSparkEnabled(): Boolean = maceSpark

    fun isMaceFlashEnabled(): Boolean = maceFlash

    private fun isShielding(attacker: Player, victim: Player): Boolean {
        if (!victim.isBlocking) return false

        val directionToAttacker = attacker.position().subtract(victim.position()).let {
            Vec3(it.x, 0.0, it.z).normalize()
        }
        val shieldDirection = Vec3.directionFromRotation(0.0F, victim.yHeadRot)
        return directionToAttacker.dot(shieldDirection) > 0.0
    }

    private fun distanceFade(entity: Entity, startDistance: Float): Float {
        val player = mc.player ?: return 0.0f
        return Mth.clamp((player.distanceTo(entity) - startDistance) / 20.0F, 0.0F, 1.0F)
    }

    private fun pearlColor(pearl: ThrownEnderpearl): Color {
        val owner = pearl.owner
        val player = mc.player
        if (owner != null && player != null && owner.uuid == player.uuid) {
            return selfPearlColor
        }
        if (owner != null && pearlAllies.contains(owner.scoreboardName)) {
            return allyPearlColor
        }
        return otherPearlColor
    }

    private fun drawHitbox(entity: Entity, partialTicks: Float, color: Int) {
        val offset = entity.getPosition(partialTicks).subtract(entity.position())
        Gizmos.cuboid(entity.boundingBox.move(offset), GizmoStyle.stroke(color))
    }

}
