package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.StartDestroyBlockEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;

public class AutoTool extends Module {

    public static final AutoTool INSTANCE = new AutoTool();

    private AutoTool() {
        super("Auto Tool", Category.PLAYER);
    }

    private final BoolSetting swapBack = boolSetting("Swap Back", true);
    private final IntSetting swapBackDelay = intSetting("Swap Back Delay", 0, 0, 15, 1, swapBack::getValue);

    private int oldSlot = -1;
    private int lastDestroyTick = -1;

    @Override
    protected void onDisable() {
        if (oldSlot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        oldSlot = -1;
        lastDestroyTick = -1;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onStartDestroyBlock(StartDestroyBlockEvent event) {
        lastDestroyTick = mc.player.tickCount;
        int bestTool = getBestTool(event.getBlockPos());
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (bestTool == -1 || bestTool == selectedSlot) return;
        if (swapBack.getValue()) {
            if (oldSlot == -1) oldSlot = selectedSlot;
        } else {
            oldSlot = -1;
        }
        mc.player.getInventory().setSelectedSlot(bestTool);
        mc.gameMode.ensureHasSentCarriedItem();
    }

    @EventHandler
    private void onAfterSendPosition(AfterSendPositionEvent event) {
        if (oldSlot == -1) return;

        if (mc.gameMode.isDestroying() || BedNuker.INSTANCE.isBreakingTarget()) {
            lastDestroyTick = mc.player.tickCount;
            return;
        }

        if (mc.player.tickCount - lastDestroyTick >= swapBackDelay.getValue()) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            oldSlot = -1;
        }
    }

    public int getBestTool(BlockPos pos) {
        BlockState blockState = mc.level.getBlockState(pos);
        Block block = blockState.getBlock();
        int slot = 0;
        float dmg = 1.0F;

        for (int index = 0; index < 9; index++) {
            ItemStack itemStack = mc.player.getInventory().getItem(index);
            if (
                    !InvHelper.isGodItem(itemStack)
                            && !itemStack.isEmpty()
                            && !blockState.isAir()
                            && (!(itemStack.is(ItemTags.SWORDS)) || block instanceof WebBlock)
            ) {
                float strVsBlock = itemStack.getItem().getDestroySpeed(itemStack, blockState);
                if (strVsBlock > 1.0F && !(block instanceof DropExperienceBlock) && !(block instanceof RedStoneOreBlock)) {
                    int i = EnchantmentUtils.getEnchantmentLevel(itemStack, Enchantments.EFFICIENCY);
                    if (i > 0) {
                        strVsBlock += (float) (i * i + 1);
                    }
                }

                if (strVsBlock > dmg) {
                    slot = index;
                    dmg = strVsBlock;
                }
            }
        }

        return dmg > 1.0F ? slot : -1;
    }

}
