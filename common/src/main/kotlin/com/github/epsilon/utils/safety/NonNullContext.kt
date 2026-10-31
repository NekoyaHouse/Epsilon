package com.github.epsilon.utils.safety

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
 * 声明这一依赖后，即可在 lambda 内直接使用 [player]、[level] 等非空值，省去逐个 null 检查：
 *
 * ```kotlin
 * EventBus.nonNullHandler<PlayerTickEvent.Post> {
 *     player.jumpFromGround()
 * }
 * ```
 *
 * 上下文按需计算并缓存：只要 `Minecraft` 的对应字段没有变化就复用同一实例，避免每个事件都分配新对象；
 * 世界卸载或返回主菜单后字段变为 null，[current] 随即返回 null，监听器不会执行。
 *
 * [player]、[level] 等是声明在本文件中的上下文属性。依赖上下文的代码一律 `import com.github.epsilon.utils.safety.*`
 * 一次引入上下文类型与属性，在 `context(nn: NonNullContext)` 作用域内直接用 `player` / `level` / `interaction` /
 * `connection` / `mc` 读取，不得改写成 `nn.player` 这类显式访问。
 */
class NonNullContext private constructor(
    val level: ClientLevel,
    val player: LocalPlayer,
    val interaction: MultiPlayerGameMode,
    val connection: ClientPacketListener,
) {

    val mc: Minecraft
        get() = Constants.client

    companion object {

        @Volatile
        private var cached: NonNullContext? = null

        /**
         * 当前可用的非空上下文；世界、玩家、交互管理器或连接任一缺失时为 null。
         */
        val current: NonNullContext?
            get() {
                val mc = Constants.mc ?: return clear()
                val level = mc.level ?: return clear()
                val player = mc.player ?: return clear()
                val interaction = mc.gameMode ?: return clear()
                val connection = mc.connection ?: return clear()

                val previous = cached
                if (previous != null
                    && previous.level === level
                    && previous.player === player
                    && previous.interaction === interaction
                    && previous.connection === connection
                ) {
                    return previous
                }

                return NonNullContext(level, player, interaction, connection).also { cached = it }
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
val level: ClientLevel
    get() = nn.level

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
val connection: ClientPacketListener
    get() = nn.connection

/**
 * 上下文作用域内可直接使用的客户端实例；配合 [NonNullContext] 的上下文参数解析。
 */
context(nn: NonNullContext)
val mc: Minecraft
    get() = nn.mc

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
