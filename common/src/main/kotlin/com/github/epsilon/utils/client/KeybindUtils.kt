package com.github.epsilon.utils.client

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import org.lwjgl.sdl.SDLMouse

object KeybindUtils {

    const val NONE = -1
    const val MOUSE_OFFSET = -2

    /**
     * 判断编码后的绑定值是否表示鼠标按键。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 判断结果
     */
    fun isMouseButton(keyBind: Int): Boolean {
        return keyBind <= MOUSE_OFFSET
    }

    /**
     * 将 SDL 鼠标按键编号编码为 Epsilon 键位值。
     *
     * @param button 点击按钮编号
     * @return 操作结果
     */
    fun encodeMouseButton(button: Int): Int {
        return MOUSE_OFFSET - button
    }

    /**
     * 从 Epsilon 键位值解码 SDL 鼠标按键编号。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 操作结果
     */
    fun decodeMouseButton(keyBind: Int): Int {
        return MOUSE_OFFSET - keyBind
    }

    /**
     * 获取按键映射当前绑定的原始键值。
     *
     * @param keyMapping Minecraft 按键映射
     * @return 获取或计算得到的结果
     */
    fun getKey(keyMapping: KeyMapping): Int {
        return keyMapping.key.value
    }

    /**
     * 判断指定按键绑定当前是否按下。
     *
     * @param keyMapping Minecraft 按键映射
     * @return 判断结果
     */
    fun isPressed(keyMapping: KeyMapping): Boolean {
        return isPressed(getKey(keyMapping))
    }

    /**
     * 判断指定按键绑定当前是否按下。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 判断结果
     */
    fun isPressed(keyBind: Int): Boolean {
        if (keyBind == NONE) {
            return false
        }
        if (isMouseButton(keyBind)) {
            return isMouseButtonDown(decodeMouseButton(keyBind))
        }
        return InputConstants.isKeyDown(keyBind)
    }

    /**
     * 判断指定 SDL 鼠标按键当前是否按下。
     *
     * @param button SDL 鼠标按键编号，1 为左键
     * @return 判断结果
     */
    private fun isMouseButtonDown(button: Int): Boolean {
        if (button < InputConstants.MOUSE_BUTTON_LEFT || button > InputConstants.MOUSE_BUTTON_8) {
            return false
        }
        val state = SDLMouse.SDL_GetMouseState(null, null)
        return state and (1 shl (button - 1)) != 0
    }

    /**
     * 将键位值格式化为面向用户的名称。
     *
     * @param keyBind Epsilon 键位编码值
     * @return 操作结果
     */
    fun format(keyBind: Int): String {
        if (keyBind == NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName()
        }
        if (isMouseButton(keyBind)) {
            return "Mouse " + (decodeMouseButton(keyBind) + 1)
        }
        return InputConstants.Type.KEYBOARD.getOrCreate(keyBind).displayName.string
    }

}
