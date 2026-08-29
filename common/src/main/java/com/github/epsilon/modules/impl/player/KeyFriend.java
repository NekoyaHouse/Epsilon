package com.github.epsilon.modules.impl.player;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class KeyFriend extends Module {

    public static final KeyFriend INSTANCE = new KeyFriend();

    private KeyFriend() {
        super("Key Friend", Category.PLAYER);
    }

    private final KeybindSetting activateKey = keybindSetting("Activate Key", KeybindUtils.encodeMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyPress(KeyPressEvent event) {
        if (handleInput(event.getKey(), event.getAction())) {
            event.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMousePress(MousePressEvent event) {
        if (handleInput(KeybindUtils.encodeMouseButton(event.getButton()), event.getAction())) {
            event.cancel();
        }
    }

    private boolean handleInput(int key, int action) {
        if (nullCheck() || mc.gui.screen() != null || key != activateKey.getValue() || action != InputConstants.PRESS || !(mc.hitResult instanceof EntityHitResult hitResult) || !(hitResult.getEntity() instanceof Player player)) {
            return false;
        } else {
            String playerName = player.getGameProfile().name();
            if (FriendManager.INSTANCE.isFriend(playerName)) {
                FriendManager.INSTANCE.removeFriend(playerName);
                NotificationManager.INSTANCE.error(getTranslatedName(), EpsilonTranslations.component(EpsilonTranslations.Notifications.KEY_FRIEND_REMOVED, playerName).getString());
            } else {
                FriendManager.INSTANCE.addFriend(playerName);
                NotificationManager.INSTANCE.success(getTranslatedName(), EpsilonTranslations.component(EpsilonTranslations.Notifications.KEY_FRIEND_ADDED, playerName).getString());
            }
            return true;
        }
    }

}
