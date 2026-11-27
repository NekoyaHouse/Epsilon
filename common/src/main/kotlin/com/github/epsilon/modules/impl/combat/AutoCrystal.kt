package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.dropAll
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.swap
import com.github.epsilon.kernel.gameplay.player.DamageUtils
import com.github.epsilon.kernel.gameplay.rotation.Rotation
import com.github.epsilon.kernel.gameplay.rotation.RotationPriority
import com.github.epsilon.kernel.gameplay.rotation.RotationUtils.calculate
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.render.WorldToScreen
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.group
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.Set
import kotlin.collections.any
import kotlin.collections.contains
import kotlin.collections.emptySet
import kotlin.collections.filter
import kotlin.collections.filterIsInstance
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.listOfNotNull
import kotlin.collections.map
import kotlin.collections.minByOrNull
import kotlin.collections.minOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.none
import kotlin.collections.orEmpty
import kotlin.collections.removeAll
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toMap
import kotlin.collections.toSet
import kotlin.math.ceil
import kotlin.math.round

object AutoCrystal : Module("Auto Crystal", Category.COMBAT) {
    private enum class Sequential {
        None,
        Strict,
        Strong
    }

    private enum class Instant {
        Always,
        Strict,
        None
    }

    private enum class Weakness {
        None,
        Normal,
        Silent
    }

    private enum class Silent {
        Normal,
        Alternative
    }

    private enum class SwapMode {
        None,
        Normal,
        Silent
    }

    private var place by boolSetting("Place", true).group(settingGroup("Place"))
    private var placeDelay by intSetting("Place Delay", 0, 0..1000, 1, { place }).group(settingGroup("Place"))
    private var placeRange by doubleSetting("Place Range", 4.0, 0.0..6.0, 0.1, { place }).group(settingGroup("Place"))
    private var placeWallRange by doubleSetting(
        "Place Wall Range",
        4.0,
        0.0..6.0,
        0.1,
        { place }).group(settingGroup("Place"))
    private var minPlaceDamage by doubleSetting(
        "Place Min Damage",
        6.0,
        0.1..20.1,
        0.1,
        { place }).group(settingGroup("Place"))
    private var maxPlaceSelfDamage by doubleSetting("Place Max Self Damage", 20.0, 0.0..20.0, 0.1, { place }).group(
        settingGroup("Place")
    )
    private var placeDamageRatio by doubleSetting("Place Damage Ratio", 1.8, 0.5..2.0, 0.1, { place }).group(
        settingGroup("Place")
    )
    private var sequential by enumSetting("Place Sequential", Sequential.Strong, { place }).group(settingGroup("Place"))
    private var requireUpperAir by boolSetting("Place 1.12", false, { place }).group(settingGroup("Place"))
    private var strictDirection by boolSetting("Place Strict Direction", false, { place }).group(settingGroup("Place"))
    private var lowerHitBox by boolSetting("Place Lower Hit Box", false, { place }).group(settingGroup("Place"))
    private var mineIgnore by boolSetting("Place Mine Ignore", false, { place }).group(settingGroup("Place"))
    private var alwaysIgnoreMining by boolSetting("Place Always", true, { place }).group(settingGroup("Place"))
    private var miningIgnoreThreshold by intSetting(
        "Place Threshold",
        1,
        1..5,
        1,
        { place }).group(settingGroup("Place"))
    private var breaking by boolSetting("Break", true).group(settingGroup("Break"))
    private var breakDelay by intSetting("Break Delay", 0, 0..1000, 1, { breaking }).group(settingGroup("Break"))
    private var attackFactor by intSetting("Break Factor", 1, 1..5, 1, { breaking }).group(settingGroup("Break"))
    private var breakRange by doubleSetting(
        "Break Range",
        4.0,
        0.0..6.0,
        0.1,
        { breaking }).group(settingGroup("Break"))
    private var breakWallRange by doubleSetting(
        "Break Wall Range",
        4.0,
        0.0..6.0,
        0.1,
        { breaking }).group(settingGroup("Break"))
    private var minBreakDamage by doubleSetting("Break Min Damage", 3.0, 0.1..20.0, 0.1, { breaking }).group(
        settingGroup("Break")
    )
    private var maxBreakSelfDamage by doubleSetting("Break Max Self Damage", 12.0, 0.0..20.0, 0.1, { breaking }).group(
        settingGroup("Break")
    )
    private var antiSuicide by boolSetting("Break Anti Suicide", false, { breaking }).group(settingGroup("Break"))
    private var instant by enumSetting("Break Instant", Instant.Always, { breaking }).group(settingGroup("Break"))
    private var await by boolSetting("Break Await", false, { breaking }).group(settingGroup("Break"))
    private var ticksExisted by intSetting(
        "Break Ticks Existed",
        1,
        0..40,
        1,
        { breaking }).group(settingGroup("Break"))
    private var weakness by enumSetting("Break Weakness", Weakness.Silent, { breaking }).group(settingGroup("Break"))
    private var basePlace by boolSetting("Base Place", true).group(settingGroup("Base Place"))
    private var basePlaceDelay by intSetting(
        "Base Place Delay",
        800,
        0..1000,
        1,
        { basePlace }).group(settingGroup("Base Place"))
    private var basePlaceAir by boolSetting("Base Place Air", false, { basePlace }).group(settingGroup("Base Place"))
    private var rotate by boolSetting("Rotate", true).group(settingGroup("Rotate"))
    private var rotatePlace by boolSetting("Rotate Place", true, { rotate }).group(settingGroup("Rotate"))
    private var rotateBreak by boolSetting("Rotate Break", true, { rotate }).group(settingGroup("Rotate"))
    private var facePlace by boolSetting("Face Place", true).group(settingGroup("Face Place"))
    private var facePlaceHealth by doubleSetting("Face Place Health", 36.0, 0.0..36.0, 0.1, { facePlace }).group(
        settingGroup("Face Place")
    )
    private var facePlaceArmorThreshold by intSetting("Face Place Armor Threshold", 26, 0..100, 1, { facePlace }).group(
        settingGroup("Face Place")
    )
    private var facePlaceShieldBreaker by boolSetting("Face Place Shield Breaker", false, { facePlace }).group(
        settingGroup("Face Place")
    )
    private var damageSync by boolSetting(
        "Face Place Damage Sync",
        true,
        { facePlace }).group(settingGroup("Face Place"))
    private var prediction by boolSetting("Extrapolation", true).group(settingGroup("Extrapolation"))
    private var predictionTicks by intSetting(
        "Extrapolation Ticks",
        3,
        1..8,
        1,
        { prediction }).group(settingGroup("Extrapolation"))
    private var drawPrediction by boolSetting(
        "Extrapolation Draw",
        false,
        { prediction }).group(settingGroup("Extrapolation"))
    private var render by boolSetting("Render", true).group(settingGroup("Render"))
    private var fill by colorSetting(
        "Render Fill",
        Color(148, 161, 244, 32),
        true,
        { render }).group(settingGroup("Render"))
    private var outline by colorSetting("Render Outline", Color(148, 161, 244, 255), true, { render }).group(
        settingGroup(
            "Render"
        )
    )
    private var lineWidth by doubleSetting(
        "Render Line Width",
        1.0,
        0.1..3.0,
        0.1,
        { render }).group(settingGroup("Render"))
    private var renderDamage by boolSetting("Render Damage", false, { render }).group(settingGroup("Render"))
    private var fade by boolSetting("Render Fade", true, { render }).group(settingGroup("Render"))
    private var multiFade by boolSetting("Render Multi Fade", false, { render }).group(settingGroup("Render"))
    private var fadeTime by doubleSetting(
        "Render Fade Time",
        0.5,
        0.1..1.5,
        0.1,
        { render }).group(settingGroup("Render"))
    private var pauseMining by boolSetting("Pause Mining", false).group(settingGroup("Pause"))
    private var pauseEating by boolSetting("Pause Eating", true).group(settingGroup("Pause"))
    private var pauseMending by boolSetting("Pause Mending", false).group(settingGroup("Pause"))
    private var targetRange by doubleSetting(
        "Targeting Target Range",
        16.0,
        1.0..16.0,
        0.1
    ).group(settingGroup("Targeting"))
    private var crystalRange by doubleSetting(
        "Targeting Crystal Range",
        6.0,
        0.1..12.0,
        0.1
    ).group(settingGroup("Targeting"))
    private var antiFriendKill by boolSetting("Targeting Anti Friend Kill", false).group(settingGroup("Targeting"))
    private var antiQuiver by boolSetting("Targeting Anti Quiver", true).group(settingGroup("Targeting"))
    private var ignoreNakeds by boolSetting("Targeting Ignore Nakeds", false).group(settingGroup("Targeting"))
    private var assumeBestArmor by boolSetting("Targeting Assume Best Armor", true).group(settingGroup("Targeting"))
    private var bosses by boolSetting("Targeting Bosses", false).group(settingGroup("Targeting"))
    private var forceSuicide by boolSetting("Targeting Force Suicide", false).group(settingGroup("Targeting"))
    private var forceSuicideNoArmor by boolSetting("Targeting No Armor", false).group(settingGroup("Targeting"))
    private var assumeInvincibility by boolSetting(
        "Targeting Assume Invincibility",
        false
    ).group(settingGroup("Targeting"))
    private var assumeSelfInvincibility by boolSetting("Targeting Self", true).group(settingGroup("Targeting"))
    private var invincibilityTimeout by doubleSetting(
        "Targeting Timeout",
        1.8,
        0.0..4.0,
        0.1
    ).group(settingGroup("Targeting"))
    private var autoSwap by enumSetting("Misc Auto Swap", SwapMode.Silent).group(settingGroup("Misc"))
    private var silent by enumSetting("Misc Silent", Silent.Normal).group(settingGroup("Misc"))
    private var swapBack by boolSetting("Misc Swap Back", true).group(settingGroup("Misc"))
    private var strictTiming by boolSetting("Misc Strict Timing", false).group(settingGroup("Misc"))
    private var swapDelay by doubleSetting("Misc Swap Delay", 0.0, 0.0..10.0, 0.1).group(settingGroup("Misc"))
    private var swapPenalty by doubleSetting("Misc Swap Penalty", 0.0, 0.0..10.0, 0.1).group(settingGroup("Misc"))
    private var multipoint by boolSetting("Misc Multipoint", true).group(settingGroup("Misc"))

    private data class Placement(
        val base: BlockPos,
        val target: LivingEntity,
        val damage: Double,
        val needsBase: Boolean
    )

    private data class Mark(val base: BlockPos, val damage: Double, val time: Long)

    private var placement: Placement? = null
    private var target: LivingEntity? = null
    private var previousTarget: LivingEntity? = null
    private var world: net.minecraft.client.multiplayer.ClientLevel? = null
    @Volatile
    private var generation = 0
    private var lastPlace = 0L
    private var lastBreak = 0L
    private var lastBase = 0L
    private var lastSlotChange = 0L
    private var selectedSlot = -1
    private var savedSlot = -1
    private var lastPlacedBase: BlockPos? = null
    private var previousPlacement: BlockPos? = null
    private var miningPos: BlockPos? = null
    private var unconfirmedBreak: BlockPos? = null
    private var unconfirmedTime = 0L
    private val attacks = mutableMapOf<Int, Long>()
    private val joined = mutableMapOf<java.util.UUID, Long>()
    private val dead = mutableSetOf<Int>()
    private val predictedAttackIds = mutableMapOf<Int, Long>()
    private val marks = ArrayDeque<Mark>()
    private val placements = ArrayDeque<Long>()
    private var rotationRequest: Rotation? = null
    private var strictFollowup = false
    private var obsidianDropNearTarget = false
    private var quiverThreat = false
    private fun now() = System.nanoTime() / 1_000_000L

    init {
        onEnable { val times = joined.toMap(); resetState(); joined.putAll(times) }
        onDisable {
            runSafe { restoreSlot() };
            val times = joined.toMap(); resetState(); joined.putAll(times)
        }
        handler<RespawnEvent> { resetState(); forceSuicide = false }
        handler<ClientTickEvent.Pre>(alwaysListening = true) {
            observeWorld()
        }
        safeEventHandler<StartDestroyBlockEvent> { miningPos = it.blockPos }
        safeEventHandler<DestroyedBlockEvent> {
            unconfirmedBreak = it.blockPos; unconfirmedTime = now(); miningPos = null
        }
        safeEventHandler<PlayerTickEvent.Pre> {
            world = level
            val time = now()
            if (selectedSlot != player.inventory.selectedSlot) {
                selectedSlot = player.inventory.selectedSlot; lastSlotChange = time
            }
            attacks.entries.removeIf { time - it.value > 1000 }
            predictedAttackIds.entries.removeIf { time - it.value > 1000 }
            if (time - unconfirmedTime > 1000) unconfirmedBreak = null
            if (!gameMode.isDestroying) miningPos = null
            strictFollowup = false
            refreshTargets()
            if (shouldPause()) {
                placement = null; clearRotation(); return@safeEventHandler
            }
            if (forceSuicide && forceSuicideNoArmor && player.containerMenu === player.inventoryMenu) {
                for (slot in 5..8) {
                    val stack = player.inventoryMenu.getSlot(slot).item
                    if (!stack.isEmpty && stack.enchantments.entrySet()
                            .none { it.key.`is`(Enchantments.BINDING_CURSE) }
                    ) dropAll(slot)
                }
            }
            val previous = placement
            placement = calculatePlacement()
            if (placement == null && previous != null && savedSlot >= 0) restoreSlot()
            placement?.let { target = it.target; previousTarget = it.target }
            val crystal = chooseCrystal()
            val point = if (crystal != null && rotateBreak) crystal.position()
            else if (rotatePlace) placement?.base?.let { Vec3.upFromBottomCenterOf(it, 1.0) } else null
            if (rotate && point != null) requestRotation(point, false) else clearRotation()
        }
        safeEventHandler<PlayerTickEvent.Post> {
            if (!shouldPause()) {
                chooseCrystal()?.let { attack(it.id, it.position(), false) }
                runPlacement()
            }
        }
        // Receive is posted on Netty. Only immutable packet data crosses this boundary.
        handler<PacketEvent.Receive>(alwaysListening = true) { event ->
            val packet = event.packet
            if (packet !is ClientboundAddEntityPacket && packet !is ClientboundRemoveEntitiesPacket &&
                packet !is ClientboundEntityEventPacket && packet !is ClientboundPlayerInfoUpdatePacket &&
                packet !is ClientboundPlayerInfoRemovePacket && packet !is ClientboundPlayerCombatKillPacket
            ) return@handler
            val epoch = generation
            mc.execute {
                observeWorld()
                runSafe {
                    if (packet is ClientboundPlayerInfoUpdatePacket || packet is ClientboundPlayerInfoRemovePacket) {
                        if (packet is ClientboundPlayerInfoUpdatePacket && ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER in packet.actions()) {
                            for (entry in packet.entries()) joined[entry.profileId()] = now()
                        }
                        if (packet is ClientboundPlayerInfoRemovePacket) for (id in packet.profileIds()) joined.remove(
                            id
                        )
                        return@runSafe
                    }
                    if (epoch != generation || !isEnabled || world !== level) return@runSafe
                    when (packet) {
                        is ClientboundAddEntityPacket -> if (packet.type == EntityTypes.END_CRYSTAL) onSpawn(packet)
                        is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach { id ->
                            attacks.remove(id); dead.remove(
                            id
                        ); predictedAttackIds.remove(id)
                        }

                        is ClientboundEntityEventPacket -> if (packet.eventId == EntityEvent.DEATH) {
                            packet.getEntity(level)?.let { dead.add(it.id) }
                        }

                        is ClientboundPlayerCombatKillPacket -> forceSuicide = false
                    }
                }
            }
        }
        safeEventHandler<Render3DEvent> {
            if (!render) return@safeEventHandler
            val time = now()
            marks.removeAll { !fade || time - it.time > (fadeTime * 1000).toLong() }
            val current = placement
            val drawn = if (multiFade) marks.toList() else marks.lastOrNull()?.let(::listOf).orEmpty()
            for (mark in drawn) {
                if (mark.base == current?.base) continue
                val alpha = (1.0 - (time - mark.time) / (fadeTime * 1000)).coerceIn(0.0, 1.0)
                drawBox(AABB(mark.base), alpha)
            }
            current?.let { drawBox(AABB(it.base), 1.0) }
            if (drawPrediction && prediction) target?.let {
                Render3DScheduler.addOutlineBox(predictedBounds(it), outline.rgb, lineWidth.toFloat())
            }
        }
        safeEventHandler<Render2DEvent.Level> { event ->
            if (!render || !renderDamage) return@safeEventHandler
            val visible = placement?.let { Mark(it.base, it.damage, now()) }
                ?: if (fade) marks.lastOrNull() else null
            if (visible != null && now() - visible.time <= (fadeTime * 1000).toLong()) {
                val anchor = Vec3.upFromBottomCenterOf(visible.base, 1.2)
                val screen = WorldToScreen.calcWorld2Screen(anchor) ?: return@safeEventHandler
                val text = String.format(Locale.US, "%.1f", visible.damage)
                val scale = maxOf(0.45f, WorldToScreen.calcScale(anchor) * 0.7f)
                event.canvas.addText(
                    text, screen.x - event.textMetrics.getWidth(text, scale) / 2f,
                    screen.y, scale, Color.WHITE, StaticFontLoader.defaultFont()
                )
            }
        }
    }

    override fun getInfo(): String? = target?.name?.string

    /** Observe world changes even while disabled; never carry join timestamps across sessions. */
    private fun observeWorld() {
        if (world != null && mc.level !== world) {
            resetState(); forceSuicide = false
        }
        world = mc.level
    }

    private fun clearRotation() {
        rotationRequest?.let { RotationManager.clearRotations(it) }
        rotationRequest = null
    }

    private fun resetState() {
        generation++
        clearRotation()
        placement = null; target = null; previousTarget = null; world = null
        lastPlace = 0; lastBreak = 0; lastBase = 0; lastSlotChange = 0
        savedSlot = -1; selectedSlot = -1; lastPlacedBase = null; previousPlacement = null
        miningPos = null; unconfirmedBreak = null; strictFollowup = false
        attacks.clear(); joined.clear(); dead.clear(); predictedAttackIds.clear(); marks.clear(); placements.clear()
    }

    private fun NonNullContext.shouldPause(): Boolean = player.isDeadOrDying ||
            (pauseMining && gameMode.isDestroying) || (pauseEating && player.isUsingItem && player.useItem.has(
        DataComponents.FOOD
    )) ||
            (pauseMending && AutoMend.isEnabled) || now() - lastSlotChange < swapPenalty * 50.0

    private fun NonNullContext.invalid(entity: LivingEntity?): Boolean {
        if (entity == null || !entity.isAlive || entity.isDeadOrDying || entity.isRemoved || entity.isSpectator || entity.id in dead) return true
        if (forceSuicide) return entity !== player
        if (entity === player || player.distanceTo(entity) > targetRange) return true
        return entity is Player && (entity.isCreative || FriendManager.isFriend(entity))
    }

    private fun NonNullContext.targets(): List<LivingEntity> = if (forceSuicide) listOf(player) else
        level.entitiesForRendering().filterIsInstance<LivingEntity>().filter {
            !invalid(it) && (it is Player || bosses && (it is WitherBoss || it is EnderDragon || it is Warden)) &&
                    (!ignoreNakeds || hasArmor(it) || it.isHolding(Items.END_CRYSTAL))
        }

    private fun NonNullContext.refreshTargets() {
        if (invalid(target)) target = null
        if (target == null && !invalid(previousTarget)) target = previousTarget
        if (target == null) target = targets().minByOrNull { player.distanceTo(it) }
        val focus = target
        obsidianDropNearTarget = focus != null && level.entitiesForRendering().filterIsInstance<ItemEntity>().any {
            it.isAlive && it.item.`is`(Items.OBSIDIAN) && it.item.count >= 8 &&
                    it.boundingBox.intersects(
                        focus.boundingBox.setMinY(focus.boundingBox.center.y).inflate(0.5, 0.0, 0.5)
                    )
        }
        // open-zio detects an arrow over the target's head (quiver exploit), not bow use.
        quiverThreat = antiQuiver && focus is Player && level.entitiesForRendering().filterIsInstance<Arrow>().any {
            val dx = it.x - focus.eyePosition.x;
            val dz = it.z - focus.eyePosition.z
            it.y >= focus.eyeY && dx * dx + dz * dz < 0.25 && kotlin.math.abs(focus.eyeY - it.eyeY) < 4.0
        }
    }

    private fun hasArmor(entity: LivingEntity) = DamageUtils.armorSlots.any { !entity.getItemBySlot(it).isEmpty }
    private fun health(entity: LivingEntity) = (entity.health + entity.absorptionAmount).toDouble()
    private fun lethal(entity: LivingEntity, damage: Double) = damage >= health(entity)
    private fun killed(entity: LivingEntity, damage: Double) =
        lethal(entity, damage) && !entity.isHolding(Items.TOTEM_OF_UNDYING)

    private fun isInvincible(entity: LivingEntity): Boolean {
        if (!assumeInvincibility || entity === mc.player && !assumeSelfInvincibility) return false
        if (entity === mc.player) return entity.tickCount / 20.0 < invincibilityTimeout
        val time = joined[entity.uuid] ?: return false
        return now() - time <= invincibilityTimeout * 1000.0
    }

    private fun NonNullContext.ignoreMining() = mineIgnore &&
            (unconfirmedBreak != null || miningPos != null && (alwaysIgnoreMining || gameMode.destroyStage >= 10 - miningIgnoreThreshold))

    private fun NonNullContext.ignoredBlocks(): Set<BlockPos> = if (!ignoreMining()) emptySet() else
        listOfNotNull(miningPos, unconfirmedBreak).toSet()

    private fun NonNullContext.predictedBounds(entity: LivingEntity, ticks: Int = predictionTicks): AABB {
        var box = entity.boundingBox
        if (!prediction || entity !is Player) return box
        // Remote players are position-interpolated; their deltaMovement may be stale/zero.
        val motion = if (entity === player) entity.deltaMovement else entity.knownSpeed
        // Collision-aware horizontal extrapolation: do not predict players through walls.
        repeat(ticks) {
            val moved = box.move(motion.x, 0.0, motion.z)
            if (level.noCollision(entity, moved)) box = moved else return box
        }
        return box
    }

    private fun NonNullContext.minimum(entity: LivingEntity, hit: Boolean): Double {
        if (obsidianDropNearTarget || quiverThreat) return 0.1
        val armor = DamageUtils.armorSlots.map { entity.getItemBySlot(it) }.minOf {
            if (it.isEmpty) 0.0 else if (!it.isDamageableItem) 100.0 else (it.maxDamage - it.damageValue) * 100.0 / it.maxDamage
        }
        if (facePlace && (health(entity) <= facePlaceHealth || armor <= facePlaceArmorThreshold ||
                    facePlaceShieldBreaker && entity.isBlocking) || level.getBlockState(entity.blockPosition())
                .`is`(Blocks.NETHER_PORTAL)
        ) return 0.6
        var min = if (hit) minBreakDamage else if (minPlaceDamage >= 20.1) health(entity) else minPlaceDamage
        if (ignoreMining()) min = if (hit) 3.0 else minOf(min, if (alwaysIgnoreMining) 0.6 else 5.0)
        return minOf(min, health(entity))
    }

    private fun NonNullContext.accept(
        entity: LivingEntity, self: Double, damage: Double, hit: Boolean,
        intersection: Boolean = false
    ): Boolean {
        if (isInvincible(entity) && entity !== player) return false
        if (forceSuicide) return true
        val selfDamage = if (isInvincible(player)) 0.0 else self
        if (antiSuicide && selfDamage >= health(player) && !(killed(
                entity,
                damage
            ) && player.isHolding(Items.TOTEM_OF_UNDYING))
        ) return false
        if (hit && intersection || killed(entity, damage)) return true
        if (!hit && selfDamage > 0 && damage / selfDamage < placeDamageRatio) return false
        if (damage < minimum(entity, hit)) return false
        return lethal(entity, damage) || selfDamage <= if (hit) maxBreakSelfDamage else maxPlaceSelfDamage
    }

    private fun NonNullContext.friendSafe(center: Vec3, ignored: Set<BlockPos>, solid: BlockPos?): Boolean =
        !antiFriendKill || level.players().filter { FriendManager.isFriend(it) }.none {
            DamageUtils.damage(level, center, it, predictedBounds(it), ignored, solid) >= health(it)
        }

    private fun NonNullContext.visible(point: Vec3): Boolean = level.clip(
        ClipContext(player.eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
    ).type == HitResult.Type.MISS

    private fun NonNullContext.baseVisible(base: BlockPos): Boolean {
        val samples = if (!multipoint) listOf(Vec3.atCenterOf(base)) else
            listOf(0.05, 0.95).flatMap { x ->
                listOf(0.05, 0.95).flatMap { y ->
                    listOf(
                        0.05,
                        0.95
                    ).map { z -> Vec3(base.x + x, base.y + y, base.z + z) }
                }
            }
        return samples.any { point ->
            val ray = level.clip(
                ClipContext(
                    player.eyePosition,
                    point,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
                )
            )
            ray.type == HitResult.Type.MISS || ray.blockPos == base
        }
    }

    private fun NonNullContext.canPlace(base: BlockPos, needsBase: Boolean): Boolean {
        if (!level.hasChunk(base.x shr 4, base.z shr 4)) return false
        val ignored = ignoredBlocks()
        val above = base.above()
        if ((!level.isEmptyBlock(above) && !level.getBlockState(above)
                .`is`(Blocks.FIRE) && above !in ignored) || !level.getFluidState(above).isEmpty
        ) return false
        if (requireUpperAir && !level.isEmptyBlock(base.above(2))) return false
        if (needsBase && (!level.getBlockState(base).canBeReplaced() || !level.noCollision(
                player,
                AABB(base)
            ))
        ) return false
        val box = AABB(above).expandTowards(0.0, if (lowerHitBox) 0.0 else 1.0, 0.0)
        for (entity in level.entitiesForRendering()) {
            if (entity.isRemoved || !entity.isAlive) continue
            if (entity is EndCrystal && (entity.id in attacks || player.eyePosition.distanceToSqr(entity.position()) <= breakRange * breakRange)) continue
            if (entity is ItemEntity && ignoreMining() && entity.tickCount < 3) continue
            if (entity.boundingBox.intersects(box)) return false
            if (entity is Player && entity !== player && prediction) {
                for (tick in 1..predictionTicks) if (predictedBounds(entity, tick).intersects(box)) return false
            }
        }
        return true
    }

    private fun NonNullContext.calculatePlacement(): Placement? {
        if (!place || shouldPause()) return null
        val candidates = targets()
        if (candidates.isEmpty()) return null
        val ignored = ignoredBlocks()
        val eye = player.eyePosition
        val radius = ceil(placeRange).toInt()
        val origin = BlockPos.containing(eye)
        var best: Placement? = null
        // The previous base wins ties after rounding, preventing jitter between equivalent positions.
        val oldBase = placement?.base
        val scan =
            BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))
        for (mutable in scan) {
            val base = mutable.immutable()
            val distance = eye.distanceTo(Vec3.atCenterOf(base))
            if (distance > placeRange || !ignoreMining() && distance > breakRange) continue
            val state = level.getBlockState(base)
            val needsBase = !state.`is`(Blocks.OBSIDIAN) && !state.`is`(Blocks.BEDROCK)
            if (needsBase && (!basePlace || now() - lastBase < basePlaceDelay)) continue
            if (!canPlace(base, needsBase) || !baseVisible(base) && distance > placeWallRange) continue
            if (needsBase && !basePlaceAir && supportHit(base) == null) continue
            val center = Vec3.upFromBottomCenterOf(base, 1.0)
            val solid = if (needsBase) base else null
            if (!friendSafe(center, ignored, solid)) continue
            val self = DamageUtils.damage(level, center, player, predictedBounds(player), ignored, solid)
            for (candidate in candidates) {
                if (needsBase && (base.y > candidate.y + 1.5 || predictedBounds(candidate).minmax(candidate.boundingBox)
                        .intersects(AABB(base)))
                ) continue
                val bounds = predictedBounds(candidate)
                val damage = DamageUtils.damage(level, center, candidate, bounds, ignored, solid, assumeBestArmor)
                if (center.distanceTo(bounds.center) > crystalRange && !lethal(
                        candidate,
                        damage
                    ) && !obsidianDropNearTarget && !quiverThreat
                ) continue
                if (!accept(candidate, self, damage, false)) continue
                val choice = Placement(base, candidate, damage, needsBase)
                val current = best
                if (current == null || current.needsBase && !needsBase || current.needsBase == needsBase &&
                    (damage > current.damage || base == oldBase && round(damage) >= current.damage)
                ) best = choice
            }
        }
        return best
    }

    private fun NonNullContext.chooseCrystal(): EndCrystal? {
        val focus = target ?: return null
        if (!breaking || shouldPause() || isInvincible(focus) || now() - lastBreak < breakDelay) return null
        var best: EndCrystal? = null;
        var bestDamage = 0.0
        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || entity.isRemoved || entity.id in attacks) continue
            if (entity.tickCount < ticksExisted && !ignoreMining()) continue
            val position = entity.position()
            if (player.eyePosition.distanceTo(position) > if (visible(position)) breakRange else breakWallRange) continue
            val ignored = ignoredBlocks()
            val damage =
                DamageUtils.damage(level, position, focus, predictedBounds(focus), ignored, bestArmor = assumeBestArmor)
            val self = DamageUtils.damage(level, position, player, predictedBounds(player), ignored)
            val intersection = lastPlacedBase?.let {
                it.above() != entity.blockPosition() && now() - lastPlace < 200 &&
                        entity.boundingBox.intersects(AABB(it.above()).expandTowards(0.0, 1.0, 0.0))
            } == true
            if (!friendSafe(position, ignored, null) || !accept(focus, self, damage, true, intersection)) continue
            if ((damage > self || lethal(focus, damage) || forceSuicide || intersection) &&
                (intersection && best == null || damage >= minimum(focus, true) && damage > bestDamage)
            ) {
                best = entity; bestDamage = damage
            }
        }
        return best
    }

    private fun NonNullContext.requestRotation(point: Vec3, immediate: Boolean): Boolean {
        val request = calculate(point, false)
        if (!RotationManager.setRotationsExact(request, RotationPriority.High)) return false
        rotationRequest = request
        if (immediate) connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x, player.y, player.z,
                request.yaw,
                request.pitch,
                player.onGround(),
                player.horizontalCollision
            )
        )
        return true
    }

    private fun NonNullContext.supportHit(base: BlockPos): BlockHitResult? {
        for (direction in Direction.values()) {
            val support = base.relative(direction)
            val state = level.getBlockState(support)
            if (state.isAir || state.canBeReplaced()) continue
            val face = direction.opposite
            val point = Vec3.atCenterOf(support).add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)
            if (strictDirection && !baseVisible(support)) continue
            return BlockHitResult(point, face, support, false)
        }
        return null
    }

    /** Use the game's shared sequence allocator; never invent sequence IDs. */
    private fun NonNullContext.sendPlacement(hit: BlockHitResult, hand: InteractionHand) {
        level.blockStatePredictionHandler.startPredicting().use {
            connection.send(ServerboundUseItemOnPacket(hand, hit, it.currentSequence()))
        }
        player.swing(hand, player.getItemInHand(hand).interactAnimation, false)
    }

    private fun NonNullContext.runPlacement() {
        val data = placement ?: return
        if (!place || shouldPause() || invalid(data.target) || now() - lastPlace < placeDelay) return
        if (await && instant != Instant.None && ignoreMining() && attacks.isNotEmpty()) return
        if (sequential == Sequential.Strict && strictFollowup) return
        if ((damageSync || obsidianDropNearTarget || quiverThreat) && data.damage < maxOf(
                minPlaceDamage,
                5.0
            ) && now() - lastPlace < 50
        ) return
        if (!canPlace(data.base, data.needsBase)) return
        if (data.needsBase) {
            if (!basePlace || now() - lastBase < basePlaceDelay) return
            val hit = supportHit(data.base) ?: if (basePlaceAir) BlockHitResult(
                Vec3.atCenterOf(data.base),
                Direction.UP,
                data.base,
                false
            ) else return
            if (rotate && rotatePlace && !requestRotation(hit.location, true)) return
            if (withItem(Items.OBSIDIAN) { hand -> sendPlacement(hit, hand) }) lastBase = now()
            return // Wait for authoritative base state before placing the crystal.
        }
        if (level.getBlockState(data.base.above()).`is`(Blocks.FIRE)) {
            // Request extinguishing, then retry only after the authoritative block update.
            level.blockStatePredictionHandler.startPredicting().use {
                connection.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        data.base.above(), Direction.DOWN, it.currentSequence()
                    )
                )
                connection.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        data.base.above(), Direction.DOWN, it.currentSequence()
                    )
                )
            }
            lastPlace = now()
            return
        }
        val direction = if (strictDirection) {
            val ray = level.clip(
                ClipContext(
                    player.eyePosition,
                    Vec3.atCenterOf(data.base),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
                )
            )
            if (ray.type != HitResult.Type.BLOCK || ray.blockPos != data.base) return
            ray.direction
        } else if (player.eyePosition.y > data.base.y + 1.0) Direction.UP else Direction.DOWN
        val point = Vec3.atCenterOf(data.base).add(direction.stepX * 0.5, direction.stepY * 0.5, direction.stepZ * 0.5)
        if (rotate && rotatePlace && !requestRotation(point, true)) return
        if (withItem(Items.END_CRYSTAL) { hand ->
                sendPlacement(
                    BlockHitResult(point, direction, data.base, false),
                    hand
                )
            }) {
            lastPlace = now(); lastPlacedBase = data.base
            if (!multiFade) marks.clear()
            marks.addLast(Mark(data.base, data.damage, lastPlace))
            while (marks.size > 1000) marks.removeFirst()
        }
    }

    private fun NonNullContext.attack(id: Int, position: Vec3, spawned: Boolean): Boolean {
        if (!breaking || shouldPause() || id in attacks || !spawned && now() - lastBreak < breakDelay) return false
        if (rotate && rotateBreak && !requestRotation(position, true)) return false
        val held = player.mainHandItem
        val weak = player.hasEffect(MobEffects.WEAKNESS) && !held.has(DataComponents.TOOL)
        val weapon = if (weak && weakness != Weakness.None) (0..8).firstOrNull {
            player.inventory.getItem(it).has(DataComponents.TOOL)
        } else null
        if (weak && weakness != Weakness.None && weapon == null) return false
        val original = player.inventory.selectedSlot
        if (weapon != null) {
            player.inventory.selectedSlot = weapon; gameMode.ensureHasSentCarriedItem()
        }
        try {
            gameMode.ensureHasSentCarriedItem()
            connection.send(ServerboundAttackPacket(id))
            player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
            connection.send(ServerboundPunchPacket.INSTANCE)
        } finally {
            if (weapon != null && weakness == Weakness.Silent) {
                player.inventory.selectedSlot = original; gameMode.ensureHasSentCarriedItem()
            }
        }
        lastBreak = now(); attacks[id] = lastBreak; previousPlacement = BlockPos.containing(position)
        lastBase = lastBreak
        return true
    }

    private fun NonNullContext.onSpawn(packet: ClientboundAddEntityPacket) {
        val position = Vec3(packet.x, packet.y, packet.z)
        dead.remove(packet.id)
        if (packet.id in predictedAttackIds) {
            predictedAttackIds.remove(packet.id); attacks[packet.id] = now()
        }
        if (lastPlacedBase?.let { Vec3.atCenterOf(it).distanceToSqr(position) }?.let { it <= 9.0 } == true) {
            placements.addLast(now()); while (placements.isNotEmpty() && now() - placements.first() > 1000) placements.removeFirst()
        }
        val focus = target ?: return
        if (instant == Instant.None || !breaking || shouldPause() || isInvincible(focus)) return
        if (instant == Instant.Strict && previousPlacement == BlockPos.containing(position)) return
        if (await && ignoreMining()) return
        if (player.eyePosition.distanceTo(position) > if (visible(position)) breakRange else breakWallRange) return
        val damage = DamageUtils.damage(level, position, focus, bestArmor = assumeBestArmor)
        val self = DamageUtils.damage(level, position, player)
        if (damage <= self && !forceSuicide && !lethal(focus, damage) || damage < minimum(focus, true)) return
        if (!friendSafe(position, emptySet(), null) || !accept(focus, self, damage, true)) return
        if (attack(packet.id, position, true)) {
            if (sequential != Sequential.None && !strictFollowup) {
                placement = calculatePlacement()
                runPlacement()
                if (sequential == Sequential.Strict) strictFollowup = true
            }
            // Upstream Factor predicts consecutive IDs after a confirmed crystal spawn.
            // Never deliberately attack an already-known non-crystal entity.
            if (level.entitiesForRendering().none { !it.isAlive || it is LivingEntity && it.isRemoved }) {
                for (offset in 1 until attackFactor) {
                    val next = packet.id + offset
                    val known = level.getEntity(next)
                    if (known != null && known !is EndCrystal) break
                    connection.send(ServerboundAttackPacket(next))
                    connection.send(ServerboundPunchPacket.INSTANCE)
                    predictedAttackIds[next] = now()
                }
            }
        }
    }

    /** Transactional slot changes: every silent swap is restored even if packet submission fails. */
    private fun NonNullContext.withItem(item: Item, action: (InteractionHand) -> Unit): Boolean {
        if (player.offhandItem.`is`(item)) {
            action(InteractionHand.OFF_HAND); return true
        }
        if (player.mainHandItem.`is`(item)) {
            action(InteractionHand.MAIN_HAND); return true
        }
        if (autoSwap == SwapMode.None || player.containerMenu !== player.inventoryMenu) return false
        if (autoSwap == SwapMode.Normal && now() - lastSlotChange < swapDelay * 50.0) return false
        if (strictTiming && autoSwap == SwapMode.Silent && silent == Silent.Alternative && now() - lastPlace < 50) return false
        val slot = (0..(if (autoSwap == SwapMode.Silent && silent == Silent.Alternative) 35 else 8)).firstOrNull {
            player.inventory.getItem(it).`is`(item)
        } ?: return false
        val original = player.inventory.selectedSlot
        if (!player.inventoryMenu.carried.isEmpty) return false
        val inventorySwap = slot >= 9 || autoSwap == SwapMode.Silent && silent == Silent.Alternative
        if (inventorySwap) swap(if (slot < 9) slot + 36 else slot, original)
        else {
            if (autoSwap == SwapMode.Normal && savedSlot == -1) savedSlot = original; player.inventory.selectedSlot =
                slot
        }
        gameMode.ensureHasSentCarriedItem()
        selectedSlot = player.inventory.selectedSlot
        try {
            action(InteractionHand.MAIN_HAND)
        } finally {
            if (inventorySwap) swap(if (slot < 9) slot + 36 else slot, original)
            else if (autoSwap == SwapMode.Silent || swapBack) {
                player.inventory.selectedSlot = original; savedSlot = -1
            }
            gameMode.ensureHasSentCarriedItem(); selectedSlot = player.inventory.selectedSlot
        }
        return true
    }

    private fun NonNullContext.restoreSlot() {
        if (savedSlot in 0..8 && swapBack) {
            player.inventory.selectedSlot = savedSlot; gameMode.ensureHasSentCarriedItem()
        }
        savedSlot = -1
    }

    private fun drawBox(box: AABB, alpha: Double) {
        fun color(c: Color) = Color(c.red, c.green, c.blue, (c.alpha * alpha).toInt()).rgb
        Render3DScheduler.addFilledBox(box, color(fill))
        Render3DScheduler.addOutlineBox(box, color(outline), lineWidth.toFloat())
    }
}
