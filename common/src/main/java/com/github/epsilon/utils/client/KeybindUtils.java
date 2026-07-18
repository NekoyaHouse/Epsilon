package com.github.epsilon.utils.client;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import static com.github.epsilon.Constants.mc;

public class KeybindUtils {

    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = -2;

    private KeybindUtils() {
    }

    public static boolean isMouseButton(int keyBind) {
        return keyBind <= MOUSE_OFFSET;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_OFFSET - button;
    }

    public static int decodeMouseButton(int keyBind) {
        return MOUSE_OFFSET - keyBind;
    }

    public static int getKey(KeyMapping keyMapping) {
        return keyMapping.key.getValue();
    }

    public static boolean isPressed(KeyMapping keyMapping) {
        return isPressed(getKey(keyMapping));
    }

    public static boolean isPressed(int keyBind) {
        if (keyBind == NONE) {
            return false;
        }
        Window window = mc.getWindow();
        if (isMouseButton(keyBind)) {
            return GLFW.glfwGetMouseButton(window.handle(), decodeMouseButton(keyBind)) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, keyBind);
    }

    public static String format(int keyBind) {
        if (keyBind == NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName();
        }
        if (isMouseButton(keyBind)) {
            return "Mouse " + (decodeMouseButton(keyBind) + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyBind).getDisplayName().getString();
    }

}
