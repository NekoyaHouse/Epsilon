package com.github.epsilon.utils

import com.github.epsilon.Constants
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer

/**
 * 事件处理期间可用的非空客户端上下文。
 *
 * 只有世界、玩家、交互管理器与连接同时存在时才会构造；监听器用 Kotlin 上下文参数（context parameters）
 * 声明这一依赖后，即可在 lambda 内直接使用 [player]、[world] 等非空值，省去逐个 null 检查：
 *
 * ```kotlin
 * EventBus.INSTANCE.nonNullHandler<PlayerTickEvent.Post> {
 *     player.jumpFromGround()
 * }
 * ```
 *
 * 上下文按需计算并缓存：只要 `Minecraft` 的对应字段没有变化就复用同一实例，避免每个事件都分配新对象；
 * 世界卸载或返回主菜单后字段变为 null，[current] 随即返回 null，监听器不会执行。
 *
 * [player]、[world] 等是声明在本文件中的上下文属性；在 `com.github.epsilon.utils` 以外的包使用时
 * 需要导入对应属性，或改用 `contextOf<NonNullContext>()` 显式取值。
 */
class NonNullContext private constructor(
    val world: ClientLevel,
    val player: LocalPlayer,
    val interaction: MultiPlayerGameMode,
    val netHandler: ClientPacketListener,
) {

    val mc: Minecraft
        get() = checkNotNull(Constants.mc) { "Minecraft 尚未初始化" }

    companion object {

        @Volatile
        private var cached: NonNullContext? = null

        /**
         * 当前可用的非空上下文；世界、玩家、交互管理器或连接任一缺失时为 null。
         */
        val current: NonNullContext?
            get() {
                val mc = Constants.mc ?: return clear()
                val world = mc.level ?: return clear()
                val player = mc.player ?: return clear()
                val interaction = mc.gameMode ?: return clear()
                val netHandler = mc.getConnection() ?: return clear()

                val previous = cached
                if (previous != null
                    && previous.world === world
                    && previous.player === player
                    && previous.interaction === interaction
                    && previous.netHandler === netHandler
                ) {
                    return previous
                }

                return NonNullContext(world, player, interaction, netHandler).also { cached = it }
            }

        private fun clear(): NonNullContext? {
            cached = null
            return null
        }

    }

}

/**
 * 监听器内可直接使用的玩家实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val player: LocalPlayer
    get() = nn.player

/**
 * 监听器内可直接使用的客户端世界实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val world: ClientLevel
    get() = nn.world

/**
 * 监听器内可直接使用的交互管理器实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val interaction: MultiPlayerGameMode
    get() = nn.interaction

/**
 * 监听器内可直接使用的连接实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val netHandler: ClientPacketListener
    get() = nn.netHandler

/**
 * 上下文存在时执行 [block]，否则跳过并返回 null。
 */
inline fun <R> runSafe(crossinline block: context(NonNullContext) () -> R): R? {
    val ctx = NonNullContext.current ?: return null
    return context(ctx) { block() }
}

/**
 * 上下文存在时执行 [block] 并返回其布尔结果，否则返回 false。
 */
inline fun runSafeOrFalse(crossinline block: context(NonNullContext) () -> Boolean): Boolean =
    runSafe(block) ?: false

/**
 * 上下文存在时执行 [block]，否则返回 [defaultValue]。
 */
inline fun <R> runSafeOrElse(defaultValue: R, crossinline block: context(NonNullContext) () -> R): R =
    runSafe(block) ?: defaultValue
