package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoTool extends Module {

    public static final AutoTool INSTANCE = new AutoTool();

    private AutoTool() {
        super("Auto Tool", Category.PLAYER);
    }

    private final BoolSetting swapBack = boolSetting("Swap Back", true);

    private int originSlot = -1;

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (mc.gameMode.isDestroying()) {
            if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult hitResult = (BlockHitResult) mc.hitResult;
                int bestTool = this.getBestTool(hitResult.getBlockPos());
                if (bestTool != -1 && bestTool != mc.player.getInventory().getSelectedSlot()) {
                    this.originSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(bestTool);
                }
            }
        }
    }

    @EventHandler
    private void onAfterSendPosition(AfterSendPositionEvent event) {
        if (!mc.gameMode.isDestroying() && this.swapBack.getValue() && this.originSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originSlot);
            this.originSlot = -1;
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
