package com.github.epsilon

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * 全局常量与客户端实例引用。
 *
 * [mc] 在 [EpsilonCommon.init] 中赋值；Mixin 的极早期阶段（26.3 会在 Minecraft 构造期间渲染首帧）
 * 仍可能为 null，因此保持可空语义，Java Mixin 侧可直接判空。
 *
 * 注意：Java Mixin 通过 `import static com.github.epsilon.Constants.client;` / `Constants.skipTicks`
 * 读取静态字段，所以这两个字段必须保留 `@JvmField`（Kotlin object 的普通属性只生成 getter）。
 */
object Constants {

    const val NAME: String = "Epsilon"

    /** 来自 BuildConfig（Java 生成源码），不是 Kotlin 编译期常量，因此用静态字段而非 const。 */
    @JvmField
    val MOD_ID: String = BuildConfig.MOD_ID

    @JvmField
    val VERSION: String = BuildConfig.VERSION

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(NAME)

    /** 客户端实例；初始化完成前为 null。Java 侧按静态字段访问，Kotlin 侧优先用 [client]。 */
    @JvmField
    @Volatile
    var mc: Minecraft? = null

    /** 跳帧计数，由 MixinClientLevel 读写。 */
    @JvmField
    var skipTicks: Int = 0

    /** Kotlin 侧的非空入口：初始化前访问会抛出 NPE，与迁移前的 Java 行为一致。 */
    val client: Minecraft
        get() = checkNotNull(mc) { "Minecraft 尚未初始化" }

}
