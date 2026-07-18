package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class AutoTool extends Module {

    public static final AutoTool INSTANCE = new AutoTool();

    private AutoTool() {
        super("Auto Tool", Category.PLAYER);
    }

    private final BoolSetting swapBack = boolSetting("Swap Back", true);
    private final BoolSetting saveItem = boolSetting("Save Item", true);
    private final BoolSetting silent = boolSetting("Silent", false);
    private final BoolSetting echestSilk = boolSetting("Ender Chest Silk Touch", true);

    public static int itemIndex;
    private boolean swap;
    private long swapDelay;
    private final List<Integer> lastItem = new ArrayList<>();

    @EventHandler
    public void onClientTick(PlayerTickEvent.Pre event) {
        if (!(mc.hitResult instanceof BlockHitResult result)) return;

        BlockPos pos = result.getBlockPos();
        if (mc.level.getBlockState(pos).isAir()) {
            return;
        }

        if (getTool(pos) != -1 && mc.options.keyAttack.isDown()) {
            lastItem.add(mc.player.getInventory().getSelectedSlot());

            if (silent.getValue()) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(getTool(pos)));
            } else {
                mc.player.getInventory().setSelectedSlot(getTool(pos));
            }

            itemIndex = getTool(pos);
            swap = true;

            swapDelay = System.currentTimeMillis();
        } else if (swap && !lastItem.isEmpty() && System.currentTimeMillis() >= swapDelay + 300 && swapBack.getValue()) {
            if (silent.getValue()) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(lastItem.get(0)));
            } else {
                mc.player.getInventory().setSelectedSlot(lastItem.get(0));
            }

            itemIndex = lastItem.get(0);
            lastItem.clear();
            swap = false;
        }
    }

    public int getTool(final BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                if (!(mc.player.getInventory().getItem(i).getMaxDamage() - mc.player.getInventory().getItem(i).getDamageValue() > 10) && saveItem.getValue()) {
                    continue;
                }

                float digSpeed = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
                float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));

                if (mc.level.getBlockState(pos).getBlock() instanceof AirBlock) return -1;
                if (mc.level.getBlockState(pos).getBlock() instanceof EnderChestBlock && echestSilk.getValue()) {
                    if (EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.SILK_TOUCH) > 0 && digSpeed + destroySpeed > CurrentFastest) {
                        CurrentFastest = digSpeed + destroySpeed;
                        index = i;
                    }
                } else if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }

}
