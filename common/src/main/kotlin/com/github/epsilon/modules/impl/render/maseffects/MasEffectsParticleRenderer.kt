package com.github.epsilon.modules.impl.render.maseffects

import com.github.epsilon.Constants
import com.github.epsilon.assets.resources.ResourceLocationUtils
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.Camera
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.tags.ItemTags
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.ArrayList
import java.util.EnumMap
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class MasEffectsParticleRenderer {

    private val random: RandomSource = RandomSource.create()
    private val particles: MutableList<EffectParticle> = ArrayList()
    private val pendingParticles: ConcurrentLinkedQueue<EffectParticle> = ConcurrentLinkedQueue()

    fun tick() {
        drainPending()
        particles.removeAll { particle -> particle.tick() }
    }

    fun render(poseStack: PoseStack) {
        drainPending()
        if (particles.isEmpty()) return

        val partialTick = Constants.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
        val batches = LinkedHashMap<Identifier, MutableList<EffectParticle>>()
        for (particle in particles) {
            if (!particle.shouldRender()) continue
            batches.computeIfAbsent(particle.texture()) { ArrayList() }.add(particle)
        }

        for (entry in batches.entries) {
            val renderer = LuminImmediateRenderer.beginPosTexColorQuads(PARTICLE_PIPELINE, entry.key)
            for (particle in entry.value) {
                particle.render(poseStack, renderer, partialTick)
            }
            renderer.end()
        }
    }

    fun clear() {
        particles.clear()
        pendingParticles.clear()
    }

    fun spawnSlam(position: Vec3, size: Double) {
        val clampedSize = Mth.clamp(size.toFloat(), 1.0f, 10.0f)
        if (MasEffects.isMaceShockwaveEnabled()) {
            add(ShockwaveParticle(position, SHOCKWAVE, 0.8f, 0.8f, 1.5f * MasEffects.getMaceShockwaveSize() * clampedSize, false))
            add(ShockwaveParticle(position, SHOCKWAVE, 0.4f, 1.0f, 0.35f * MasEffects.getMaceShockwaveSize() * clampedSize, false))
            if (!MasEffects.isLegacyMaceShockwave()) {
                spawnWindWave(position, 0.8f, 0.8f, 1.75f * MasEffects.getMaceShockwaveSize() * clampedSize)
            }
        }

        val offset = Vec3(random(-0.6f, 0.6f).toDouble(), random(-1.2f, 1.2f).toDouble(), random(-0.6f, 0.6f).toDouble())
        if (MasEffects.isMaceSparkEnabled()) {
            add(FlickParticle(position.add(offset.x, offset.y + 0.9f, offset.z), 0.5f))
            add(FlickParticle(position.add(offset.x, offset.y + 0.9f, offset.z), 0.2f))
        }
        if (MasEffects.isMaceFlashEnabled()) {
            add(FlashParticle(position))
        }
    }

    fun spawnWindWave(position: Vec3, scaler: Float, opacity: Float, size: Float) {
        add(ShockwaveParticle(position, WINDWAVE, scaler, opacity, size, true))
    }

    fun spawnShieldWave(shielder: Player) {
        val height = Vec3(0.0, (shielder.getDimensions(shielder.pose).height() * 0.6f).toDouble(), 0.0)
        val position = shielder.position().add(height).add(shielder.lookAngle.scale(0.5))
        add(ShieldWaveParticle(position))
    }

    fun spawnArmorParticles(entity: LivingEntity) {
        val armor = EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot::class.java)
        armor[EquipmentSlot.HEAD] = entity.getItemBySlot(EquipmentSlot.HEAD)
        armor[EquipmentSlot.CHEST] = entity.getItemBySlot(EquipmentSlot.CHEST)
        armor[EquipmentSlot.LEGS] = entity.getItemBySlot(EquipmentSlot.LEGS)
        armor[EquipmentSlot.FEET] = entity.getItemBySlot(EquipmentSlot.FEET)

        val size = Vec3(
            entity.getDimensions(entity.pose).width().toDouble(),
            (entity.getDimensions(entity.pose).height() / 4.0f).toDouble(),
            entity.getDimensions(entity.pose).width().toDouble()
        )

        for (entry in armor.entries) {
            val stack = entry.value
            val texture = armorTexture(stack) ?: continue

            val yOffset = when (entry.key) {
                EquipmentSlot.HEAD, EquipmentSlot.LEGS -> size.y * 3.0
                EquipmentSlot.CHEST -> size.y * 2.0
                else -> 0.0
            }
            spawnScraps(entity.position().add(0.0, yOffset, 0.0), size, texture, random.nextInt(3))
        }
    }

    fun spawnTotem(entity: Entity) {
        val position = entity.position().add(0.0, (entity.getDimensions(entity.pose).height() / 2.0f).toDouble(), 0.0)
        for (i in 1 until 18) {
            add(ReviveParticle(position, entity.id, 2.5f))
        }
        for (i in 1 until 100) {
            add(ReviveSparkParticle(position, entity.id))
        }
    }

    fun spawnDeath(position: Vec3) {
        add(DeathSkullParticle(position))
        for (i in 0 until 30) {
            add(DeathSparkParticle(position))
        }
    }

    fun spawnPearlTrail(pearl: ThrownEnderpearl) {
        val motion = pearl.knownMovement.normalize().scale(0.05)
        val position = pearl.position()
        for (i in 0 until 3) {
            add(PearlTrailParticle(position, motion))
        }
    }

    private fun spawnScraps(position: Vec3, size: Vec3, texture: Identifier, count: Int) {
        for (i in 0..count) {
            val offset = Vec3(
                random((-size.x / 2.0).toFloat(), (size.x / 2.0).toFloat()).toDouble(),
                random(0.0f, size.y.toFloat()).toDouble(),
                random((-size.x / 2.0).toFloat(), (size.x / 2.0).toFloat()).toDouble()
            )
            val motion = Vec3(
                random(-0.08f, 0.08f).toDouble(),
                random(0.04f, 0.15f).toDouble(),
                random(-0.08f, 0.08f).toDouble()
            )
            add(ScrapParticle(position.add(offset), motion, texture))
        }
    }

    private fun armorTexture(stack: ItemStack): Identifier? {
        if (!(stack.`is`(ItemTags.HEAD_ARMOR) ||
                    stack.`is`(ItemTags.CHEST_ARMOR) ||
                    stack.`is`(ItemTags.LEG_ARMOR) ||
                    stack.`is`(ItemTags.FOOT_ARMOR))
        ) {
            return null
        }
        if (DIAMOND_ARMOR.contains(stack.item)) return DIAMOND_SCRAP
        if (NETHERITE_ARMOR.contains(stack.item)) return NETHERITE_SCRAP
        return null
    }

    private fun add(particle: EffectParticle) {
        pendingParticles.add(particle)
    }

    private fun drainPending() {
        while (true) {
            val particle = pendingParticles.poll() ?: break
            if (particles.size >= MAX_PARTICLES) {
                particles.removeAt(0)
            }
            particles.add(particle)
        }
    }

    private fun random(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)

    private fun randomInclusive(min: Int, max: Int): Int = min + random.nextInt(max - min + 1)

    private abstract inner class EffectParticle internal constructor(
        position: Vec3,
        protected val lifetime: Int,
        private val frames: Array<out Identifier>,
    ) {

        protected var xo: Double = position.x
        protected var yo: Double = position.y
        protected var zo: Double = position.z
        protected var x: Double = position.x
        protected var y: Double = position.y
        protected var z: Double = position.z
        protected var xd: Double = 0.0
        protected var yd: Double = 0.0
        protected var zd: Double = 0.0
        protected var age: Int = 0
        protected var gravity: Float = 0.0f
        protected var friction: Float = 0.98f
        protected var size: Float = 0.1f
        protected var alpha: Float = 1.0f
        protected var red: Float = 1.0f
        protected var green: Float = 1.0f
        protected var blue: Float = 1.0f
        protected var physics: Boolean = false
        protected var removed: Boolean = false
        private var stoppedByCollision = false

        internal fun tick(): Boolean {
            xo = x
            yo = y
            zo = z
            if (age++ >= lifetime) return true

            yd -= 0.04 * gravity
            move()
            xd *= friction
            yd *= friction
            zd *= friction
            afterTick()
            return removed
        }

        protected open fun afterTick() {
        }

        internal fun shouldRender(): Boolean = alpha > 0.0f && size > 0.0f

        internal fun texture(): Identifier {
            if (frames.size == 1) return frames[0]
            val max = Math.max(1, frameDuration())
            val index = Mth.clamp(frameAge(), 0, max) * (frames.size - 1) / max
            return frames[index]
        }

        protected open fun frameAge(): Int = age

        protected open fun frameDuration(): Int = lifetime

        internal open fun render(
            poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads, partialTick: Float,
        ) {
            renderPlane(poseStack, renderer, partialTick, Constants.mc.gameRenderer.mainCamera().rotation())
        }

        protected fun renderPlane(
            poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads,
            partialTick: Float, rotation: Quaternionf,
        ) {
            val camera: Camera = Constants.mc.gameRenderer.mainCamera()
            val renderX = Mth.lerp(partialTick.toDouble(), xo, x) - camera.position().x
            val renderY = Mth.lerp(partialTick.toDouble(), yo, y) - camera.position().y
            val renderZ = Mth.lerp(partialTick.toDouble(), zo, z) - camera.position().z

            poseStack.pushPose()
            poseStack.translate(renderX, renderY, renderZ)
            poseStack.rotate(rotation)

            val matrix: Matrix4f = poseStack.last().pose()
            val halfSize = size
            val color = ARGB.colorFromFloat(
                Mth.clamp(alpha, 0.0f, 1.0f),
                Mth.clamp(red, 0.0f, 1.0f),
                Mth.clamp(green, 0.0f, 1.0f),
                Mth.clamp(blue, 0.0f, 1.0f)
            )

            renderer.vertex(matrix, -halfSize, -halfSize, 0.0f, 0.0f, 1.0f, color)
            renderer.vertex(matrix, halfSize, -halfSize, 0.0f, 1.0f, 1.0f, color)
            renderer.vertex(matrix, halfSize, halfSize, 0.0f, 1.0f, 0.0f, color)
            renderer.vertex(matrix, -halfSize, halfSize, 0.0f, 0.0f, 0.0f, color)

            poseStack.popPose()
        }

        protected fun target(entityId: Int): Entity? {
            val level = Constants.mc.level ?: return null
            return level.getEntity(entityId)
        }

        private fun move() {
            if (stoppedByCollision) return

            val movement = Vec3(xd, yd, zd)
            val level = Constants.mc.level
            if (!physics || level == null || movement.lengthSqr() >= 10000.0) {
                x += xd
                y += yd
                z += zd
                return
            }

            val box = AABB(x - 0.1, y, z - 0.1, x + 0.1, y + 0.2, z + 0.1)
            val adjusted = Entity.collideBoundingBox(Constants.mc.player!!, movement, box, level, emptyList())
            x += adjusted.x
            y += adjusted.y
            z += adjusted.z

            if (Math.abs(movement.y) >= 1.0E-5f && Math.abs(adjusted.y) < 1.0E-5f) {
                stoppedByCollision = true
            }
            if (movement.x != adjusted.x) xd = 0.0
            if (movement.z != adjusted.z) zd = 0.0
        }
    }

    private inner class ShockwaveParticle(
        position: Vec3, frames: Array<out Identifier>, private var scaler: Float, opacity: Float,
        size: Float, private val crossed: Boolean,
    ) : EffectParticle(position, 40, frames) {

        private val sizer = size * 0.1f
        private var alphaControl = opacity

        init {
            this.alpha = Mth.clamp(opacity * MasEffects.getMaceShockwaveOpacity(), 0.0f, 1.0f)
            this.size = 0.5f
            this.friction = 0.0f
        }

        override fun afterTick() {
            size += scaler * sizer
            scaler = Mth.clamp(scaler - 1.0f / lifetime, 0.0f, 1.0f)
            alphaControl -= 1.0f / lifetime
            alpha = Mth.clamp(alphaControl * MasEffects.getMaceShockwaveOpacity(), 0.0f, 1.0f)
        }

        override fun frameAge(): Int = Math.min(age, lifetime / 2)

        override fun frameDuration(): Int = lifetime / 2

        override fun render(poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads, partialTick: Float) {
            renderPlane(poseStack, renderer, partialTick, Quaternionf().rotateX((Math.PI / 2.0f).toFloat()))
            if (crossed) {
                renderPlane(
                    poseStack, renderer, partialTick,
                    Quaternionf().rotateY((Math.PI / 4.0f).toFloat()).rotateX((Math.PI / 2.0f).toFloat())
                )
            }
        }
    }

    private inner class ShieldWaveParticle(position: Vec3) : EffectParticle(position, 30, arrayOf(SHIELD_WAVE)) {

        private var scaler = 1.0f

        init {
            this.size = 0.3f
            this.friction = 0.0f
        }

        override fun afterTick() {
            size += scaler * 0.2f
            scaler = Mth.clamp(scaler - 1.0f / lifetime, 0.0f, 1.0f)
            alpha = Mth.clamp(alpha - 1.0f / lifetime, 0.0f, 1.0f)
        }
    }

    private inner class FlickParticle(position: Vec3, private val scale: Float) :
        EffectParticle(position, 20, arrayOf(FLICK)) {

        private var time = 0.0f

        init {
            this.friction = 0.0f
            this.xd = random(-0.04f, 0.04f).toDouble()
            this.yd = random(0.02f, 0.1f).toDouble()
            this.zd = random(-0.04f, 0.04f).toDouble()
        }

        override fun afterTick() {
            alpha = Mth.clamp((1.0f - time) * 1.5f, 0.2f, 1.0f) - 0.2f
            time += 1.0f / lifetime
            size = alpha * scale
        }
    }

    private inner class FlashParticle(position: Vec3) :
        EffectParticle(position, 48 + random.nextInt(12), arrayOf(FLASH)) {

        init {
            this.size = 0.12f
            this.alpha = 0.99f
            this.gravity = 0.1f
            this.friction = 0.91f
        }

        override fun afterTick() {
            if (age > lifetime / 2) {
                alpha = 1.0f - (age.toFloat() - lifetime / 2.0f) / lifetime
            }
        }
    }

    private inner class ScrapParticle(position: Vec3, motion: Vec3, texture: Identifier) :
        EffectParticle(position, 20, arrayOf(texture)) {

        init {
            this.gravity = 1.0f
            this.physics = true
            this.friction = 1.0f
            this.xd = motion.x
            this.yd = motion.y
            this.zd = motion.z
        }

        override fun afterTick() {
            size = (1.0f - age.toFloat() / lifetime) * 0.35f
        }
    }

    private inner class ReviveParticle(
        position: Vec3, private val targetId: Int, private val scale: Float,
    ) : EffectParticle(position, 40, REVIVE) {

        private val rotX = randomInclusive(-180, 180)
        private val rotZ = randomInclusive(-180, 180)
        private var rotY = randomInclusive(-180, 180).toFloat()

        init {
            this.alpha = 0.0f
            this.size = 0.2f
            this.friction = 0.0f
            if (random.nextBoolean()) {
                this.red = 0.0f
                this.green = 1.0f
                this.blue = 0.0f
            } else {
                this.red = 1.0f
                this.green = 1.0f
                this.blue = 0.0f
            }
        }

        override fun afterTick() {
            val target = target(targetId)
            if (target != null) {
                x = target.x
                y = target.y + target.getDimensions(target.pose).height() / 2.0f
                z = target.z
            }

            val opacity = MasEffects.getTotemEffectOpacity()
            if (opacity <= 0.0f) {
                alpha = 0.0f
                size = 0.0f
                return
            }

            rotY += 20.0f
            alpha = Mth.clamp(
                Math.sqrt(Math.sin(age.toDouble() / lifetime * Math.PI)).toFloat() / 1.2f, 0.0f, 1.0f
            ) * opacity
            size = alpha / opacity * scale
        }

        override fun render(poseStack: PoseStack, renderer: LuminImmediateRenderer.PosTexColorQuads, partialTick: Float) {
            val rotation = euler(rotZ.toFloat(), rotX.toFloat(), -rotZ.toFloat())
                .mul(euler(0.0f, 0.0f, rotY))
            renderPlane(poseStack, renderer, partialTick, rotation)
        }
    }

    private inner class ReviveSparkParticle(position: Vec3, private val targetId: Int) : EffectParticle(
        position, randomInclusive(30, 48), arrayOf(REVIVE_SPARK[random.nextInt(REVIVE_SPARK.size)])
    ) {

        init {
            this.alpha = MasEffects.getTotemEffectOpacity()
            this.size = 0.1f
            this.xd = randomInclusive(-10, 10).toDouble() / 25.0
            this.yd = randomInclusive(-10, 10).toDouble() / 25.0
            this.zd = randomInclusive(-10, 10).toDouble() / 25.0
            this.friction = 1.0f
            this.physics = true
            if (random.nextBoolean()) {
                this.red = 0.0f
                this.green = 1.0f
                this.blue = 0.0f
            } else {
                this.red = 1.0f
                this.green = 1.0f
                this.blue = 0.0f
            }
        }

        override fun afterTick() {
            val target = target(targetId)
            if (target !is LivingEntity) {
                alpha = MasEffects.getTotemEffectOpacity()
                return
            }

            val targetDirection = Vec3(
                target.x - x,
                target.y + target.getDimensions(target.pose).height() / 2.0f - y,
                target.z - z
            ).normalize()

            if (age.toFloat() >= lifetime / 5.0f && age.toFloat() < lifetime / 4.0f) {
                friction = 0.0f
            }
            if (age.toFloat() >= lifetime / 4.0f &&
                target.position().distanceTo(Vec3(x, y, z)) < 20.0
            ) {
                alpha = Mth.clamp(
                    1.0f - (age.toFloat() - lifetime / 1.5f) / 20.0f, 0.0f, 1.0f
                ) * MasEffects.getTotemEffectOpacity()
                friction = 1.0f
                val speed = ((age - 15) * 0.05f).toDouble()
                xd = targetDirection.x * speed
                yd = targetDirection.y * speed
                zd = targetDirection.z * speed
                if (target.position().distanceTo(Vec3(x, y, z)) < 2.0) {
                    removed = true
                }
            }
        }
    }

    private inner class DeathSkullParticle(position: Vec3) : EffectParticle(position, 58, DEATH_SKULL) {

        init {
            this.yd = 0.2
            this.size = 1.0f
            this.friction = 1.0f
        }

        override fun afterTick() {
            if (age > 30) {
                alpha -= 1.0f / 30.0f
            }
            yd *= 0.95
        }

        override fun frameAge(): Int = Math.min(age, 30)

        override fun frameDuration(): Int = 30
    }

    private inner class DeathSparkParticle(position: Vec3) : EffectParticle(position, 20, DEATH_SPARK) {

        init {
            this.gravity = -0.1f
            this.friction = 1.0f
            this.size = randomInclusive(5, 10) * 0.06f
            this.xd = randomInclusive(-10, 10).toDouble() * 0.02
            this.zd = randomInclusive(-10, 10).toDouble() * 0.02
        }

        override fun afterTick() {
            alpha = (1.0f - age.toFloat() / lifetime) * 0.45f
            xd *= 0.9
            zd *= 0.9
        }
    }

    private inner class PearlTrailParticle(position: Vec3, motion: Vec3) :
        EffectParticle(position, 10, PEARL_TRAIL) {

        private var alphaControl = 1.0f

        init {
            this.xd = motion.x + randomInclusive(-10, 10).toDouble() * 0.005
            this.yd = motion.y + randomInclusive(-10, 10).toDouble() * 0.005
            this.zd = motion.z + randomInclusive(-10, 10).toDouble() * 0.005
            this.friction = 0.98f
            val player = Constants.mc.player
            if (player != null && player.position().distanceTo(position) < 6.0) {
                alphaControl -= 0.8f
            }
            this.alpha = Mth.clamp(alphaControl * MasEffects.getPearlTrailOpacity(), 0.0f, 1.0f)
        }

        override fun afterTick() {
            size = (1.0f - age.toFloat() / lifetime) * 0.15f
            alphaControl -= 0.1f
            val player = Constants.mc.player
            if (player != null && player.position().distanceTo(Vec3(x, y, z)) < 6.0) {
                alphaControl -= 0.8f
            }
            alpha = Mth.clamp(alphaControl * MasEffects.getPearlTrailOpacity(), 0.0f, 1.0f)
        }
    }

    companion object {

        private const val MAX_PARTICLES = 8192

        private val DIAMOND_ARMOR: Set<Item> = setOf(
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
        )
        private val NETHERITE_ARMOR: Set<Item> = setOf(
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS
        )

        private val SHOCKWAVE: Array<Identifier> = textures("shockwave", 5)
        private val WINDWAVE: Array<Identifier> = textures("windwave", 3)
        private val REVIVE: Array<Identifier> = textures("revive", 3)
        private val REVIVE_SPARK: Array<Identifier> = textures("revive_spark", 5)
        private val DEATH_SKULL: Array<Identifier> = textures("skull", 6)
        private val DEATH_SPARK: Array<Identifier> = textures("vapor", 4)
        private val PEARL_TRAIL: Array<Identifier> = textures("trail", 4)
        private val DIAMOND_SCRAP: Identifier = texture("diamond_scrap_0")
        private val NETHERITE_SCRAP: Identifier = texture("netherite_scrap_0")
        private val SHIELD_WAVE: Identifier = texture("shockwave")
        private val FLICK: Identifier = texture("spark_0")
        private val FLASH: Identifier = texture("flash")

        private val PARTICLE_PIPELINE: RenderPipeline = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_mas_effects")
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()

        private fun texture(name: String): Identifier =
            ResourceLocationUtils.getIdentifier("textures/maseffects/particle/$name.png")

        private fun textures(prefix: String, count: Int): Array<Identifier> =
            Array(count) { index -> texture(prefix + "_" + index) }

        private fun euler(x: Float, y: Float, z: Float): Quaternionf {
            val qx = Quaternionf().fromAxisAngleDeg(Vector3f(1.0f, 0.0f, 0.0f), x)
            val qy = Quaternionf().fromAxisAngleDeg(Vector3f(0.0f, 1.0f, 0.0f), y)
            val qz = Quaternionf().fromAxisAngleDeg(Vector3f(0.0f, 0.0f, 1.0f), z)
            return qx.mul(qy).mul(qz)
        }

    }

}
