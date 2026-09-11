package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

public class AutoCrossbowRelease extends Module {

    public static final AutoCrossbowRelease INSTANCE = new AutoCrossbowRelease();

    private AutoCrossbowRelease() {
        super("Auto Crossbow Release", Category.PLAYER);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!mc.player.isUsingItem()) return;

        ItemStack crossbow = mc.player.getUseItem();
        if (!(crossbow.getItem() instanceof CrossbowItem)) return;

        int chargeDuration = CrossbowItem.getChargeDuration(crossbow, mc.player);
        if (mc.player.getTicksUsingItem() < chargeDuration + 1) return;

        mc.options.keyUse.setDown(false);
    }

}
