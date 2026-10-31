package com.github.epsilon.modules.impl.player

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object UseCooldown : Module("Use Cooldown", Category.PLAYER) {

    // 该设置由 MixinMinecraft 的注入点读取，保持 public 可见性。
    val cooldown by intSetting("Cooldown", 0, 0..4, 1)

}
