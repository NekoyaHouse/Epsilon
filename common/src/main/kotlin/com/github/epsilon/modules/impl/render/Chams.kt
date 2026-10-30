package com.github.epsilon.modules.impl.render

import com.github.epsilon.managers.FriendManager
import com.github.epsilon.settings.getValue
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import java.util.function.Function

object Chams : Module("Chams", Category.RENDER) {

    private val players by boolSetting("Players", true)
    private val self by boolSetting("Self", true, dependency = { players })
    private val friends by boolSetting("Friends", true)
    private val crystals by boolSetting("Crystals", true)
    val chests by boolSetting("Chests", true)
    private val creatures by boolSetting("Creatures", false)
    private val monsters by boolSetting("Monsters", false)
    private val ambients by boolSetting("Ambients", false)
    private val others by boolSetting("Others", false)

    private val submittingPlayer: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    private var hasAlwaysOnTopSubmits = false

    private val PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation("pipeline/epsilon_entity_chams")
        .withColorTargetState(ColorTargetState.DEFAULT)
        .withShaderDefine("ALPHA_CUTOUT", 0.1f)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withCull(false)
        .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
        .build()

    private val TYPE: Function<Identifier, RenderType> = Util.memoize { texture ->
        RenderType.create(
            "epsilon_entity_chams", RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        )
    }

    fun getRenderType(texture: Identifier): RenderType = TYPE.apply(texture)

    fun isChamsRenderType(renderType: RenderType): Boolean = renderType.pipeline() === PIPELINE

    fun isSubmittingPlayer(): Boolean = submittingPlayer.get()

    fun setSubmittingPlayer(submittingPlayer: Boolean) {
        this.submittingPlayer.set(submittingPlayer)
    }

    fun markAlwaysOnTopSubmit() {
        this.hasAlwaysOnTopSubmits = true
    }

    fun hasAlwaysOnTopSubmits(): Boolean = this.hasAlwaysOnTopSubmits

    fun resetAlwaysOnTopSubmits() {
        this.hasAlwaysOnTopSubmits = false
    }

    fun isValidEntity(entity: Entity?): Boolean {
        if (entity == null) {
            return false
        }

        if (entity is Player) {
            if (entity === mc.player && !self) {
                return false
            }
            if (FriendManager.isFriend(entity) || TargetManager.isSameTeam(entity)) {
                return friends
            }
            return players
        }

        if (entity is EndCrystal) {
            return crystals
        }

        return when (entity.type.category) {
            MobCategory.CREATURE, MobCategory.WATER_CREATURE -> creatures
            MobCategory.MONSTER -> monsters
            MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> ambients
            else -> others
        }
    }

}
