package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class AutoMend extends Module {

    public static final AutoMend INSTANCE = new AutoMend();

    private AutoMend() {
        super("Auto Mend", Category.COMBAT);
    }

    private enum SwitchMode {
        Normal,
        Silent
    }

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch Mode", SwitchMode.Normal);
    private final BoolSetting swingHand = boolSetting("Swing Hand", false);

    private boolean shouldSwapBack;

    @Override
    protected void onEnable() {
        shouldSwapBack = false;
    }

    @Override
    protected void onDisable() {
        if (shouldSwapBack) {
            InvUtils.swapBack();
        }
    }

    @EventHandler
    private void onClientTick(PlayerTickEvent.Pre event) {
        FindItemResult result = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!result.found()) return;

        Managers.ROTATION.setRotations(new Rot2f(mc.player.getYRot(), 90), 180, Priority.High);

        InvUtils.swap(result.slot(), true);

        InteractionHand hand = result.getHand();
        mc.gameMode.useItem(mc.player, hand);
        if (swingHand.getValue()) {
            mc.player.swing(hand);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(hand));
        }

        if (switchMode.is(SwitchMode.Silent)) {
            InvUtils.swapBack();
        } else {
            shouldSwapBack = true;
        }
    }

}
