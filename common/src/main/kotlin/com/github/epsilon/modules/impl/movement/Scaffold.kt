package com.github.epsilon.modules.impl.movement

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.player.FallingPlayer
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.render.animation.Easing
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.StandingAndWallBlockItem
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

object Scaffold : Module("Scaffold", Category.MOVEMENT) {

    private enum class Mode {
        TellyBridge,
        GodBridge,
    }

    private enum class RotationMode {
        Static,
        Hypixel,
        Heypixel,
    }

    private enum class RaytraceMode {
        Normal,
        Strict,
    }

    private enum class SwapMode {
        None,
        Normal,
        Silent,
        InvSwitch,
    }

    private val blacklistedBlocks by blockListSetting(
        "Blacklisted Blocks",
        listOf(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT,
            Blocks.SCAFFOLDING
        )
    )
    private val toggleOnTeleport by boolSetting("Toggle On Teleport", false)
    private val selfRescue by boolSetting("Self Rescue", true)
    private val mode by enumSetting("Mode", Mode.TellyBridge)
    private val swapMode by enumSetting("Swap Mode", SwapMode.Normal)
    private val swapBack by boolSetting("Swap Back", true, dependency = { swapMode == SwapMode.Normal })
    private val snap by boolSetting("Snap", false, dependency = { mode == Mode.GodBridge })
    private val degrees45 by boolSetting("45 Degrees", false)
    private val rotationMode by enumSetting("Rotation Mode", RotationMode.Static)
    private val raytrace by enumSetting("Raytrace Mode", RaytraceMode.Normal)
    private val rotationSpeed by intSetting(
        "Rotation Speed", 127, 10, 180, 10,
        dependency = { rotationMode != RotationMode.Hypixel }
    )
    private val rotationSpeed2 by intSetting(
        "Rotation Speed 2", 36, 10, 180, 10,
        dependency = { rotationMode == RotationMode.Heypixel }
    )
    private val rotationBackSpeed by intSetting(
        "Rotation Back Speed", 180, 10, 180, 10,
        dependency = { mode == Mode.TellyBridge }
    )
    private val tellyTicks by intSetting(
        "Telly Ticks", 1, 0, 6, 1,
        dependency = { mode == Mode.TellyBridge }
    )

    private val swingHand by boolSetting("Swing Hand", true)
    private val render by boolSetting("Render", true)
    private val fade by boolSetting("Fade", true, dependency = { render })
    private val fadeTime by intSetting(
        "Fade Time", 500, 0, 3000, 50,
        dependency = { render && fade }
    )
    private val shrink by boolSetting("Shrink", false, dependency = { render })
    private val sideColor by colorSetting("Side Color", Color(255, 183, 197, 100), dependency = { render })
    private val lineColor by colorSetting("Line Color", Color(255, 105, 180), dependency = { render })

    private var airTicks = 0
    private var yLevel = 0
    private var blockPos: BlockPos? = null
    private var direction: Direction? = null
    private var rotation: Rot2f? = null
    private var rotateCount = 0
    private var forwardInput = 0.0f
    private var strafeInput = 0.0f
    private var inputYaw = 0.0f
    private var rawInputYaw = 0.0f
    private var lengthSqr = 4.5 * 4.5

    private var blockResult: FindItemResult = FindItemResult(-1, 0, 0)
    private var shouldSwapBack = false
    private var emergencyPlacementActive = false
    private var pearlUsePacketSent = false

    private val renderBoxes: MutableList<RenderInfo> = ArrayList()

    init {
        // 原 Java 在构造器里直接 EventBus.subscribe(ConsumerListener)，这里保持「不按启用状态过滤」的语义。
        handler<Render3DEvent>(alwaysListening = true) {
            if (!render || renderBoxes.isEmpty()) return@handler

            val time = System.currentTimeMillis()
            val fadeMillis = fadeTime.toLong()

            renderBoxes.removeAll { box -> time - box.startTime > fadeMillis }

            for (box in renderBoxes) {
                val progress = Mth.clamp((time - box.startTime).toFloat() / fadeMillis, 0.0f, 1.0f)

                var scale = 1.0
                if (box.shrink) {
                    scale = 1.0 - Easing.EASE_IN_OUT_EXPO.function.apply(progress)
                    if (scale < 0) scale = 0.0
                }

                val alphaFactor = if (box.fade) Mth.clamp(1.0f - progress, 0.0f, 1.0f) else 1.0f

                val boxSideColor = box.sideColor
                val boxLineColor = box.lineColor

                val side = Color(
                    boxSideColor.red, boxSideColor.green, boxSideColor.blue,
                    (boxSideColor.alpha * alphaFactor).toInt()
                )
                val line = Color(
                    boxLineColor.red, boxLineColor.green, boxLineColor.blue,
                    (boxLineColor.alpha * alphaFactor).toInt()
                )

                var renderBox = box.aabb
                if (box.shrink) {
                    renderBox = AABB.ofSize(
                        renderBox.center,
                        renderBox.xsize * scale,
                        renderBox.ysize * scale,
                        renderBox.zsize * scale
                    )
                }

                Render3DScheduler.addFilledBox(renderBox, side)
                Render3DScheduler.addOutlineBox(renderBox, line)
            }
        }

        nonNullHandler<PlayerTickEvent.Pre> { event ->
            if (!event.isCancelled) emergencyPlacementActive = false

            blockResult = findBlockResult()
            if (!blockResult.found()) return@nonNullHandler

            if (player.onGround()) {
                airTicks = 0
                yLevel = Mth.floor(player.y) - 1
            } else {
                airTicks++
            }

            getBlockInfo()

            if (selfRescue) {
                var reachable = true
                if (player.deltaMovement.y < -0.1 && blockPos != null) {
                    val fallingPlayer = FallingPlayer(player).calculate(2)
                    if (blockPos!!.y > fallingPlayer.y) {
                        reachable = false
                    }
                }
                val strength = player.deltaMovement.horizontal().length()
                if (strength >= 1.5) {
                    NotificationManager.warning(
                        translatedName,
                        EpsilonTranslations.Notifications.SCAFFOLD_FLYING_WARNING.getTranslatedName(),
                        this.hashCode()
                    )
                }
                if ((!reachable || strength >= 1.5) && rotateCount <= 8 && getBlockCount() >= 1 && canUseBlockResult()) {
                    emergencyPlacementActive = true
                    event.cancel()

                    rotateCount++
                    val pos = blockPos ?: return@nonNullHandler
                    val face = direction ?: return@nonNullHandler
                    val rotation = getRotation(pos, face)
                    RotationManager.INSTANCE.rotations = rotation
                    RotationManager.INSTANCE.updateActive(true)

                    // 代替 sendPosition()
                    connection.send(
                        ServerboundMovePlayerPacket.Rot(
                            rotation.yaw,
                            rotation.pitch,
                            player.onGround(),
                            player.horizontalCollision
                        )
                    )

                    swap()

                    val hand = blockResult.getHand()

                    val result = interaction.useItemOn(
                        player,
                        hand,
                        BlockHitResult(getVec3(pos, face), face, pos, false)
                    )
                    if (result.consumesAction()) {
                        SwingMode.of(swingHand).interactSwing(hand)
                        if (render) {
                            renderBoxes.add(
                                RenderInfo(
                                    AABB(pos.relative(face)),
                                    lineColor,
                                    sideColor,
                                    System.currentTimeMillis(),
                                    fade,
                                    shrink
                                )
                            )
                        }
                    }

                    swapBack()
                    return@nonNullHandler
                } else {
                    rotateCount = 0
                }
            }

            when (mode) {
                Mode.TellyBridge -> handleTelly()
                Mode.GodBridge -> handleNormal()
            }
        }

        nonNullHandler<KeyboardInputEvent>(EventPriority.HIGHEST) { event ->
            forwardInput = event.forward
            strafeInput = event.strafe
            inputYaw = Mth.wrapDegrees(
                ((player.yRot + (atan2(-strafeInput.toDouble(), forwardInput.toDouble())).toDegrees().toFloat()) / 45.0f).roundToInt() * 45.0f
            )
            rawInputYaw = Mth.wrapDegrees(
                (player.yRot + (atan2(-strafeInput.toDouble(), forwardInput.toDouble())).toDegrees().toFloat()).roundToInt().toFloat()
            )

            if (mode == Mode.TellyBridge && player.onGround() && !mc.options.keyJump.isDown && player.isMoving) {
                event.isJump = true
            }
        }

        nonNullHandler<PacketEvent.Receive> { event ->
            if (toggleOnTeleport && pearlUsePacketSent && event.packet is ClientboundPlayerPositionPacket) {
                pearlUsePacketSent = false
                NotificationManager.error(
                    translatedName,
                    EpsilonTranslations.Notifications.SCAFFOLD_TOGGLE_ON_TELEPORT.getTranslatedName()
                )
                isEnabled = false
            }
        }

        nonNullHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet is ServerboundUseItemPacket) {
                val usedStack = player.getItemInHand(packet.hand)
                if (usedStack.`is`(Items.ENDER_PEARL) ||
                    usedStack.isEmpty && player.cooldowns.isOnCooldown(Items.ENDER_PEARL.defaultInstance)
                ) {
                    pearlUsePacketSent = true
                }
            }
        }
    }

    override fun onEnable() {
        airTicks = 0
        blockPos = null
        direction = null
        rotation = null
        rotateCount = 0
        blockResult = FindItemResult(-1, 0, 0)
        shouldSwapBack = false
        emergencyPlacementActive = false
        pearlUsePacketSent = false
    }

    override fun onDisable() {
        yLevel = 0
        emergencyPlacementActive = false
        pearlUsePacketSent = false
        if (shouldSwapBack) {
            NonNullContext.current?.let { nn -> context(nn) { InvUtils.swapBack() } }
            shouldSwapBack = false
        }
    }

    fun getBlockCount(): Int = runSafeOrElse(91) {
        var total = 0

        if (isValidStack(player.offhandItem)) {
            total += player.offhandItem.count
        }

        val maxSlot = if (swapMode == SwapMode.InvSwitch) player.inventory.containerSize else 9
        for (i in 0 until maxSlot) {
            val stack = player.inventory.getItem(i)
            if (isValidStack(stack)) {
                total += stack.count
            }
        }

        total
    }

    fun getBlockStack(): ItemStack = runSafeOrElse(ItemStack.EMPTY) {
        val offhand = player.offhandItem
        if (isValidStack(offhand)) return@runSafeOrElse offhand

        if (blockResult.found()) {
            val stack = if (blockResult.isOffhand()) player.offhandItem else player.inventory.getItem(blockResult.slot)
            if (isValidStack(stack)) return@runSafeOrElse stack
        }

        ItemStack.EMPTY
    }

    private fun handleTelly() {
        runSafe {
            if (player.onGround() && (strafeInput != 0.0f || forwardInput != 0.0f)) {
                val current = rotation
                RotationManager.INSTANCE.setRotations(
                    Rot2f(rawInputYaw, current?.pitch ?: player.xRot),
                    rotationBackSpeed.toDouble()
                )
                return@runSafe
            }

            rotation = getRotation(blockPos, direction)
            var speed = rotationSpeed

            if (rotationMode == RotationMode.Hypixel) {
                speed = if (airTicks <= 1) 127 else 35
            } else if (rotationMode == RotationMode.Heypixel) {
                speed = if (airTicks <= 1) rotationSpeed else rotationSpeed2
            }

            val current = rotation
            if (current != null) {
                RotationManager.INSTANCE.setRotations(current, speed.toDouble())
            }

            if (airTicks > tellyTicks) {
                place()
            }
        }
    }

    private fun handleNormal() {
        runSafe {
            if (Eagle.isOverEdge() || !snap or !player.onGround()) {
                rotation = getRotation(blockPos, direction)
                val current = rotation
                if (current != null) {
                    RotationManager.INSTANCE.setRotations(current, rotationSpeed.toDouble())
                }
            }
            place()
        }
    }

    private fun place() {
        runSafe {
            if (!onAir() || blockPos == null || direction == null || !canUseBlockResult()) {
                return@runSafe
            }
            val pos = blockPos ?: return@runSafe
            val face = direction ?: return@runSafe

            val skip = when (raytrace) {
                RaytraceMode.Normal -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), pos)
                RaytraceMode.Strict -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), pos, face)
            }
            if (skip) {
                return@runSafe
            }

            swap()

            val hand = blockResult.getHand()

            val result = interaction.useItemOn(
                player,
                hand,
                BlockHitResult(getVec3(pos, face), face, pos, false)
            )

            if (result.consumesAction()) {
                SwingMode.of(swingHand).interactSwing(hand)

                if (render) {
                    renderBoxes.add(
                        RenderInfo(
                            AABB(pos.relative(face)),
                            lineColor,
                            sideColor,
                            System.currentTimeMillis(),
                            fade,
                            shrink
                        )
                    )
                }
            }

            swapBack()
        }
    }

    private fun getYLevel(): Int = runSafeOrElse(0) {
        if ((!mc.options.keyJump.isDown && player.isMoving && mode == Mode.TellyBridge) &&
            abs(yLevel - (Mth.floor(player.y) - 1)) <= 1.25
        ) {
            return@runSafeOrElse yLevel
        }
        Mth.floor(player.y) - 1
    }

    private fun getBlockInfo() {
        runSafe {
            lengthSqr = 4.5 * 4.5
            blockPos = null
            direction = null

            val baseVec = player.eyePosition
            val base = BlockPos.containing(baseVec.x, getYLevel().toDouble(), baseVec.z)
            val baseX = base.x
            val baseZ = base.z

            if (!onAir()) {
                return@runSafe
            }

            if (checkBlock(baseVec, base)) {
                return@runSafe
            }

            for (d in 1..6) {
                if (checkBlock(baseVec, BlockPos(baseX, getYLevel() - d, baseZ))) {
                    return@runSafe
                }

                for (x in 0..d) {
                    for (z in 0..(d - x)) {
                        val y = d - x - z
                        for (rev1 in 0..1) {
                            for (rev2 in 0..1) {
                                val pos = BlockPos(
                                    baseX + (if (rev1 == 0) x else -x),
                                    getYLevel() - y,
                                    baseZ + (if (rev2 == 0) z else -z)
                                )
                                checkBlock(baseVec, pos)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkBlock(baseVec: Vec3, pos: BlockPos): Boolean = runSafeOrFalse {
        if (!onAir() || pos.y > getYLevel()) {
            return@runSafeOrFalse false
        }

        val center = Vec3.atBottomCenterOf(pos)
        for (dir in Direction.values()) {
            val normal = dir.unitVec3
            val hit = center.add(normal.scale(0.5))
            val baseBlockPos = pos.relative(dir)

            val state: BlockState = level.getBlockState(baseBlockPos)
            if (state.getCollisionShape(level, baseBlockPos).isEmpty || state.getMenuProvider(level, baseBlockPos) != null) {
                continue
            }

            val face = dir.opposite
            val relevant = hit.subtract(baseVec)

            if (relevant.lengthSqr() > lengthSqr || relevant.dot(normal) < 0.0) {
                continue
            }

            if (face == Direction.UP && player.isMoving && !mc.options.keyJump.isDown) {
                continue
            }

            lengthSqr = relevant.lengthSqr()
            blockPos = baseBlockPos
            direction = face
            return@runSafeOrFalse true
        }

        false
    }

    private fun getRotation(pos: BlockPos?, direction: Direction?): Rot2f = runSafeOrElse(Rot2f(0.0f, 0.0f)) {
        val current = rotation
        if (current == null) {
            return@runSafeOrElse Rot2f(Mth.wrapDegrees(player.yRot - 135.0f), 82.0f)
        }

        if (!onAir() || pos == null || direction == null) {
            return@runSafeOrElse current
        }

        val calculated = RotationUtils.calculate(pos, direction)
        val yawArray = floatArrayOf(
            -135f,
            -90f,
            -45f,
            0f,
            45f,
            90f,
            135f,
            180f
        )
        val baseYaw = Mth.wrapDegrees(inputYaw - 180f)

        for (i in 1 until yawArray.size) {
            val key = yawArray[i]
            var j = i - 1
            while (j >= 0 && abs(Mth.wrapDegrees(baseYaw - yawArray[j])) > abs(Mth.wrapDegrees(baseYaw - key))) {
                yawArray[j + 1] = yawArray[j]
                j -= 1
            }
            yawArray[j + 1] = key
        }

        val pitchArray = floatArrayOf(75.0f, 82.0f, 87.0f)

        val finalYawArray = FloatArray(yawArray.size + 1)
        System.arraycopy(yawArray, 0, finalYawArray, 0, yawArray.size)
        finalYawArray[yawArray.size] = calculated.yaw

        for (yaw in finalYawArray) {
            if (degrees45 && yaw % 90 == 0.0f) {
                continue
            }
            for (pitch in pitchArray) {
                val candidate = Rot2f(
                    yaw + MathUtils.getRandom(-0.3f, 0.3f),
                    pitch + MathUtils.getRandom(-0.3f, 0.3f)
                )
                val matches = if (raytrace == RaytraceMode.Normal) {
                    RaytraceUtils.overBlock(candidate, pos)
                } else {
                    RaytraceUtils.overBlock(candidate, pos, direction)
                }
                if (matches) {
                    return@runSafeOrElse candidate
                }
            }

            for (pitch in -90 until 90) {
                val candidate = Rot2f(yaw, pitch.toFloat())
                val matches = if (raytrace == RaytraceMode.Normal) {
                    RaytraceUtils.overBlock(candidate, pos)
                } else {
                    RaytraceUtils.overBlock(candidate, pos, direction)
                }
                if (matches) {
                    return@runSafeOrElse candidate
                }
            }
        }

        calculated
    }

    private fun onAir(): Boolean = runSafeOrFalse {
        val baseVec = player.eyePosition
        val base = BlockPos.containing(baseVec.x, getYLevel().toDouble(), baseVec.z)
        level.getBlockState(base).canBeReplaced()
    }

    private fun getVec3(pos: BlockPos, face: Direction): Vec3 {
        var x = pos.x + 0.5
        var y = pos.y + 0.5
        var z = pos.z + 0.5

        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08
        } else {
            x += MathUtils.getRandom(-0.3, 0.3)
            z += MathUtils.getRandom(-0.3, 0.3)
        }

        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(-0.3, 0.3)
        }

        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(-0.3, 0.3)
        }

        return Vec3(x, y, z)
    }

    private fun findBlockResult(): FindItemResult = runSafeOrElse(FindItemResult(-1, 0, 0)) {
        val offhandStack = player.offhandItem
        if (isValidStack(offhandStack)) {
            return@runSafeOrElse FindItemResult(40, offhandStack.count, offhandStack.maxStackSize)
        }
        if (swapMode == SwapMode.InvSwitch) {
            InvUtils.find { isValidStack(it) }
        } else {
            InvUtils.findInHotbar { isValidStack(it) }
        }
    }

    private fun canUseBlockResult(): Boolean = runSafeOrFalse {
        if (blockResult.isOffhand()) {
            return@runSafeOrFalse isValidStack(player.offhandItem)
        }
        swapMode != SwapMode.None || isValidStack(player.inventory.selectedItem)
    }

    private fun swap() {
        runSafe {
            if (blockResult.isOffhand()) {
                return@runSafe
            }

            when (swapMode) {
                SwapMode.Normal -> {
                    val selectedSlot = player.inventory.selectedSlot
                    InvUtils.swap(blockResult.slot, true)
                    if (swapBack && blockResult.slot != selectedSlot) {
                        shouldSwapBack = true
                    }
                }

                SwapMode.Silent -> InvUtils.swap(blockResult.slot, true)
                SwapMode.InvSwitch -> InvUtils.invSwap(blockResult.slot)
                SwapMode.None -> {
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun swapBack() {
        if (blockResult.isOffhand()) {
            return
        }

        when (swapMode) {
            SwapMode.Silent -> InvUtils.swapBack()
            SwapMode.InvSwitch -> InvUtils.invSwapBack()
            else -> {
            }
        }
    }

    private fun isValidStack(stack: ItemStack?): Boolean {
        if (stack == null || stack.isEmpty || stack.item !is BlockItem) {
            return false
        }

        val name = stack.displayName.string
        if (name.contains("Click") || name.contains("点击")) {
            return false
        }

        if (stack.item is StandingAndWallBlockItem) {
            return false
        }

        val block = (stack.item as BlockItem).block
        return !(block is FlowerBlock || block is BushBlock || block is NetherFungusBlock || block is CropBlock) && block !is SlabBlock && !blacklistedBlocks.contains(block)
    }

    val isEmergencyPlacementActive: Boolean
        get() = isEnabled && emergencyPlacementActive

    private data class RenderInfo(
        val aabb: AABB,
        val lineColor: Color,
        val sideColor: Color,
        val startTime: Long,
        val fade: Boolean,
        val shrink: Boolean,
    )

}
