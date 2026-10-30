package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.settings.getValue
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.utils.player.ChatUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.BarrierBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.CommandBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.AABB
import java.awt.Color
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object BlockESP : Module("Block ESP", Category.RENDER) {

    private val blockListValue by blockListSetting("Block List", defaultBlocks())
    private val illegals by boolSetting("Illegals", false)
    private val range by doubleSetting("Range", 64.0, 1.0, 128.0, 1.0)
    private val sideColor by colorSetting("Side Color", Color(160, 210, 255, 30))
    private val lineColor by colorSetting("Line Color", Color(160, 210, 255, 180))
    private val blur by boolSetting("Blur", true)
    private val blurStrength by doubleSetting("Blur Strength", 8.0, 0.0, 16.0, 0.5, com.github.epsilon.settings.Setting.Dependency { blur })

    private val searchThread: ExecutorService = Executors.newSingleThreadExecutor()
    private val searchTimer = TimerUtils()
    private var canContinue = false

    var boxes: MutableList<AABB> = ArrayList()

    init {
        nonNullListener<PlayerTickEvent.Pre> { onPlayerTick(it) }
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun defaultBlocks(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        blocks.add(Blocks.CHEST)
        blocks.add(Blocks.TRAPPED_CHEST)
        blocks.addAll(Blocks.COPPER_CHEST.asList())
        blocks.add(Blocks.ENDER_CHEST)
        blocks.add(Blocks.BARREL)
        blocks.add(Blocks.SHULKER_BOX)
        blocks.addAll(Blocks.DYED_SHULKER_BOX.asList())
        return blocks
    }

    override fun onEnable() {
        boxes.clear()
        canContinue = true
    }

    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        if (searchTimer.every(1000L) && canContinue) {
            CompletableFuture.supplyAsync({ scan() }, searchThread).thenAcceptAsync({ newAABBList ->
                boxes = newAABBList
                canContinue = true
            }, Util.backgroundExecutor())
            canContinue = false
        }
    }

    private fun onRender3D(event: Render3DEvent) {
        if (boxes.isEmpty()) return

        val player = mc.player
        if (mc.fps < 8 && player != null && player.tickCount > 150) {
            ChatUtils.addChatMessage("你的 FPS 太低了，我帮你给 BlockESP 关掉了。")
            toggle()
            return
        }

        for (aabb in ArrayList(boxes)) {
            if (blur) Render3DScheduler.addBlurredBox(aabb, blurStrength)
            Render3DScheduler.addFilledBox(aabb, sideColor)
            Render3DScheduler.addOutlineBox(aabb, lineColor)
        }
    }

    private fun scan(): MutableList<AABB> {
        val boxes = ArrayList<AABB>()
        val processed = HashSet<BlockPos>()

        val player = mc.player ?: return boxes
        val level = mc.level ?: return boxes

        val startX = Mth.floor(player.x - range)
        val endX = Mth.ceil(player.x + range)
        val startY = level.getMinY() + 1
        val endY = level.getMaxY()
        val startZ = Mth.floor(player.z - range)
        val endZ = Mth.ceil(player.z + range)

        for (x in startX..endX) {
            for (y in startY..endY) {
                for (z in startZ..endZ) {
                    val blockPos = BlockPos(x, y, z)
                    if (!processed.contains(blockPos)) {
                        val blockState = level.getBlockState(blockPos)
                        if (shouldAdd(blockState.block, blockPos)) {
                            boxes.add(getConnectedShapeAABB(blockPos, blockState, processed))
                        }
                    }
                }
            }
        }
        return boxes
    }

    private fun shouldAdd(block: Block, pos: BlockPos): Boolean {
        if (block is AirBlock) return false
        if (blockListValue.contains(block)) return true
        if (illegals) return isIllegal(block, pos)
        return false
    }

    private fun isIllegal(block: Block, pos: BlockPos): Boolean {
        if (block is CommandBlock || block is BarrierBlock) return true

        val level = mc.level
        if (block === Blocks.BEDROCK && level != null) {
            return if (Level.NETHER != level.dimension()) {
                pos.y > 4
            } else {
                pos.y > 127 || (pos.y < 123 && pos.y > 4)
            }
        }
        return false
    }

    private fun getConnectedShapeAABB(blockPos: BlockPos, state: BlockState, processed: MutableSet<BlockPos>): AABB {
        processed.add(blockPos)
        var box = getShapeAABB(blockPos, state)

        if (state.block is ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            val connectedPos = ChestBlock.getConnectedBlockPos(blockPos, state)
            val level = mc.level
            if (level != null) {
                val connectedState = level.getBlockState(connectedPos)
                if (isConnectedChestPart(blockPos, state, connectedState, connectedPos)) {
                    processed.add(connectedPos)
                    box = box.minmax(getShapeAABB(connectedPos, connectedState))
                }
            }
        }

        return box
    }

    private fun isConnectedChestPart(
        blockPos: BlockPos,
        state: BlockState,
        connectedState: BlockState,
        connectedPos: BlockPos,
    ): Boolean {
        val chestBlock = state.block as? ChestBlock ?: return false
        if (!chestBlock.chestCanConnectTo(connectedState)) return false
        if (!connectedState.hasProperty(ChestBlock.TYPE) || !connectedState.hasProperty(ChestBlock.FACING)) return false
        if (connectedState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false
        if (connectedState.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) return false
        if (ChestBlock.getConnectedBlockPos(connectedPos, connectedState) != blockPos) return false

        return shouldAdd(connectedState.block, connectedPos)
    }

    private fun getShapeAABB(blockPos: BlockPos, state: BlockState): AABB {
        val level = mc.level ?: return AABB(blockPos)
        return state.getShape(level, blockPos).bounds().move(blockPos)
    }

}
