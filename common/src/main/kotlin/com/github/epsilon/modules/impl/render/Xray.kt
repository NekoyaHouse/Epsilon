package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.MoveEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.timer.TimerUtils
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import java.awt.Color
import java.util.concurrent.ThreadLocalRandom

object Xray : Module("Xray", Category.RENDER) {

    private enum class Plugin {
        Old,
        New
    }

    private val plugin: EnumSetting<Plugin> = enumSetting("Plugin", Plugin.New)
    val wallHack by boolSetting("WallHack", false, onChanged = { _ -> mc.levelExtractor.allChanged() })
    private val brutForce by boolSetting("Ore Deobf", false)
    private val fast by boolSetting("Fast", false, { brutForce })
    private val delay by intSetting("Delay", 25, 1..100, 1, { brutForce })
    private val radius by intSetting("Radius", 5, 1..64, 1, { brutForce })
    private val up by intSetting("Up", 5, 1..32, 1, { brutForce })
    private val down by intSetting("Down", 5, 1..32, 1, { brutForce })
    private val netherite by boolSetting("Netherite", false)
    private val diamond by boolSetting("Diamond ", false)
    private val gold by boolSetting("Gold", false)
    private val iron by boolSetting("Iron", false)
    private val emerald by boolSetting("Emerald", false)
    private val redstone by boolSetting("Redstone", false)
    private val lapis by boolSetting("Lapis", false)
    private val coal by boolSetting("Coal", false)
    private val quartz by boolSetting("Quartz", false)
    private val water by boolSetting("Water", false)
    private val lava by boolSetting("Lava", false)

    private val delayTimer = TimerUtils()
    private val ores: MutableList<BlockPos> = ArrayList()
    private val toCheck: MutableList<BlockPos> = ArrayList()
    private val checked: MutableList<BlockMemory> = ArrayList()
    private var displayBlock: BlockPos? = null
    private var done = 0
    private var all = 0
    private var area: AABB = AABB(BlockPos.ZERO)

    init {
        safeEventHandler<PlayerTickEvent.Pre> {
            if (plugin.`is`(Plugin.New)) {
                checked.forEach { blockMemory ->
                    if (blockMemory.isDelayed() && !ores.contains(blockMemory.blockPos))
                        ores.add(blockMemory.blockPos)
                }
            }
        }

        handler<PacketEvent.Receive> { event ->
            val packet = event.packet
            if (packet is ClientboundBlockUpdatePacket) {
                if (isCheckableOre(packet.blockState.block) && !ores.contains(packet.pos)) {
                    ores.add(packet.pos)
                }
            }
        }

        safeEventHandler<MoveEvent> { event ->
            if (brutForce) {
                if (all != done) {
                    event.z = 0.0
                    event.x = 0.0
                    event.cancel()
                    if (player.tickCount % 8 == 0 && player.isMoving) {
                        log("Don't move while deobf!")
                    }
                } else {
                    val newArea = getArea()
                    if (!newArea.intersects(area)) {
                        area = newArea
                        toCheck.clear()
                        toCheck.addAll(getBlocks())
                        checked.clear()
                        all = toCheck.size
                        done = 0
                    }
                }
            }
        }

        handler<Render3DEvent> { onRender3D(it.poseStack) }
    }

    override fun onEnable() {
        ores.clear()
        toCheck.clear()
        checked.clear()
        toCheck.addAll(getBlocks())
        all = toCheck.size
        done = 0
        mc.smartCull = false
        mc.levelExtractor.allChanged()
        area = getArea()
    }

    override fun onDisable() {
        mc.levelExtractor.allChanged()
        mc.smartCull = true
    }

    private fun onRender3D(stack: PoseStack) {
        val level = mc.level ?: return
        val player = mc.player ?: return

        for (pos in ores) {
            val block = level.getBlockState(pos).block
            if ((block === Blocks.DIAMOND_ORE || block === Blocks.DEEPSLATE_DIAMOND_ORE) && diamond) {
                draw(pos, 0, 255, 255)
            }
            if ((block === Blocks.GOLD_ORE || block === Blocks.DEEPSLATE_GOLD_ORE) && gold) {
                draw(pos, 255, 215, 0)
            }
            if (block === Blocks.NETHER_GOLD_ORE && gold) {
                draw(pos, 255, 215, 0)
            }
            if ((block === Blocks.IRON_ORE || block === Blocks.DEEPSLATE_IRON_ORE) && iron) {
                draw(pos, 213, 213, 213)
            }
            if ((block === Blocks.EMERALD_ORE || block === Blocks.DEEPSLATE_EMERALD_ORE) && emerald) {
                draw(pos, 0, 255, 77)
            }
            if ((block === Blocks.REDSTONE_ORE || block === Blocks.DEEPSLATE_REDSTONE_ORE) && redstone) {
                draw(pos, 255, 0, 0)
            }
            if (block === Blocks.COAL_ORE && coal) {
                draw(pos, 0, 0, 0)
            }
            if ((block === Blocks.LAPIS_ORE || block === Blocks.DEEPSLATE_LAPIS_ORE) && lapis) {
                draw(pos, 38, 97, 156)
            }
            if (block === Blocks.ANCIENT_DEBRIS && netherite) {
                draw(pos, 255, 255, 255)
            }
            if (block === Blocks.NETHER_QUARTZ_ORE && quartz) {
                draw(pos, 170, 170, 170)
            }
        }

        val currentDisplay = displayBlock
        if (currentDisplay != null && done != all) {
            draw(currentDisplay, 255, 0, 60)
        }

        if (brutForce) {
            Render3DScheduler.addOutlineBox(area, Color(149, 149, 149, 100))
        }

        if (toCheck.isEmpty() || !brutForce) return

        if (mc.isLocalServer) {
            log("单人游戏你反你老冯呢")
            toggle()
            return
        }

        if (player.mainHandItem.`is`(ItemTags.PICKAXES)) {
            if (player.tickCount % 8 == 0) {
                log("别在主手放镐子")
                toggle()
            }
            return
        }

        if (delayTimer.every(delay.toLong())) {
            val pos = toCheck.removeAt(
                if (toCheck.size - 1 <= 1) 0 else ThreadLocalRandom.current().nextInt(0, toCheck.size - 1)
            )
            displayBlock = pos
            mc.gameMode!!.startDestroyBlock(pos, player.direction)
            mc.gameMode!!.stopDestroyBlock()
            SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)
            checked.add(BlockMemory(pos))
            done++
        }
    }

    private fun getArea(): AABB {
        val player = mc.player ?: return AABB(BlockPos.ZERO)
        val radiusValue = if (plugin.`is`(Plugin.New)) minOf(4, radius) else radius
        val downValue = if (plugin.`is`(Plugin.New)) minOf(3, down) else down
        val upValue = if (plugin.`is`(Plugin.New)) minOf(4, up) else up
        return AABB(
            player.x - radiusValue, player.y - downValue, player.z - radiusValue,
            player.x + radiusValue, player.y + upValue, player.z + radiusValue
        )
    }

    private fun draw(pos: BlockPos, r: Int, g: Int, b: Int) {
        val box = AABB(pos)
        Render3DScheduler.addFilledBox(box, Color(r, g, b, 100))
        Render3DScheduler.addOutlineBox(box, Color(r, g, b, 200))
    }

    fun isCheckableOre(block: Block): Boolean {
        if (diamond && (block === Blocks.DIAMOND_ORE || block === Blocks.DEEPSLATE_DIAMOND_ORE)) return true
        if (gold && (block === Blocks.GOLD_ORE || block === Blocks.DEEPSLATE_GOLD_ORE ||
                    block === Blocks.NETHER_GOLD_ORE)
        ) return true
        if (iron && (block === Blocks.IRON_ORE || block === Blocks.DEEPSLATE_IRON_ORE)) return true
        if (emerald && (block === Blocks.EMERALD_ORE || block === Blocks.DEEPSLATE_EMERALD_ORE)) return true
        if (redstone && (block === Blocks.REDSTONE_ORE || block === Blocks.DEEPSLATE_REDSTONE_ORE)) return true
        if (coal && (block === Blocks.COAL_ORE || block === Blocks.DEEPSLATE_COAL_ORE)) return true
        if (netherite && block === Blocks.ANCIENT_DEBRIS) return true
        if (water && block === Blocks.WATER) return true
        if (lava && block === Blocks.LAVA) return true
        if (quartz && block === Blocks.NETHER_QUARTZ_ORE) return true
        if (lapis && (block === Blocks.LAPIS_ORE || block === Blocks.DEEPSLATE_LAPIS_ORE)) return true
        return lapis && (block === Blocks.LAPIS_ORE || block === Blocks.DEEPSLATE_LAPIS_ORE)
    }

    private fun getBlocks(): MutableList<BlockPos> {
        val player = mc.player ?: return ArrayList()
        val level = mc.level ?: return ArrayList()
        val radiusValue = if (plugin.`is`(Plugin.New)) minOf(4, radius) else radius
        val downValue = if (plugin.`is`(Plugin.New)) minOf(3, down) else down
        val upValue = if (plugin.`is`(Plugin.New)) minOf(4, up) else up

        val positions = ArrayList<BlockPos>()
        for (x in (player.x - radiusValue).toInt() until (player.x + radiusValue).toInt()) {
            for (y in (player.y - downValue).toInt() until (player.y + upValue).toInt()) {
                for (z in (player.z - radiusValue).toInt() until (player.z + radiusValue).toInt()) {
                    val pos = BlockPos(x, y, z)
                    if (level.getBlockState(pos).isAir ||
                        (fast && plugin.`is`(Plugin.Old) && (x % 2 == 0 || y % 2 == 0 || z % 2 == 0))
                    ) {
                        continue
                    }
                    positions.add(pos)
                }
            }
        }
        return positions
    }

    private fun log(message: String) {
        NotificationManager.info("Xray", message)
    }

    class BlockMemory(val blockPos: BlockPos) {

        private var time = 0L

        fun isDelayed(): Boolean = this.time++ > 10
    }

}
