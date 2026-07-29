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

    /**
     * 判断编码后的绑定值是否表示鼠标按键。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 判断结果
     */
    public static boolean isMouseButton(int keyBind) {
        return keyBind <= MOUSE_OFFSET;
    }

    /**
     * 将 GLFW 鼠标按键编号编码为 Epsilon 键位值。
     *
     * @param button 点击按钮编号
     * @return 操作结果
     */
    public static int encodeMouseButton(int button) {
        return MOUSE_OFFSET - button;
    }

    /**
     * 从 Epsilon 键位值解码 GLFW 鼠标按键编号。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 操作结果
     */
    public static int decodeMouseButton(int keyBind) {
        return MOUSE_OFFSET - keyBind;
    }

    /**
     * 获取按键映射当前绑定的原始键值。
     *
     * @param keyMapping Minecraft 按键映射
     * @return 获取或计算得到的结果
     */
    public static int getKey(KeyMapping keyMapping) {
        return keyMapping.key.getValue();
    }

    /**
     * 判断指定按键绑定当前是否按下。
     *
     * @param keyMapping Minecraft 按键映射
     * @return 判断结果
     */
    public static boolean isPressed(KeyMapping keyMapping) {
        return isPressed(getKey(keyMapping));
    }

    /**
     * 判断指定按键绑定当前是否按下。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 判断结果
     */
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

    /**
     * 将键位值格式化为面向用户的名称。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 操作结果
     */
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
