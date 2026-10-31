package com.github.epsilon.utils.world

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.*

object BlockRegistryUtils {

    /**
     * 获取注册表中全部可选择方块。
     *
     * @return 获取或计算得到的结果
     */
    fun allSelectableBlocks(): List<Block> {
        val blocks = ArrayList<Block>()
        for (block in BuiltInRegistries.BLOCK) {
            if (isSelectable(block)) {
                blocks.add(block)
            }
        }
        blocks.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { block: Block -> displayName(block) })
        return blocks
    }

    /**
     * 判断方块是否适合出现在方块选择列表中。
     *
     * @param block 方块
     * @return 判断结果
     */
    fun isSelectable(block: Block?): Boolean {
        if (block == null || block === Blocks.AIR) {
            return false
        }
        if (block.asItem() === Items.AIR) {
            return false
        }
        val id = BuiltInRegistries.BLOCK.getKey(block)
        return !id.path.endsWith("_wall_banner")
    }

    /**
     * 获取方块的注册表标识符字符串。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    fun id(block: Block): String {
        val id = BuiltInRegistries.BLOCK.getKey(block)
        return id.toString()
    }

    /**
     * 按注册表标识符查找方块。
     *
     * @param id 方块注册表标识符
     * @return 操作结果
     */
    fun byId(id: String?): Block? {
        if (id == null || id.isBlank()) {
            return null
        }
        val identifier = Identifier.tryParse(id.trim().lowercase(Locale.ROOT)) ?: return null
        val block = BuiltInRegistries.BLOCK.getValue(identifier)
        return if (block === Blocks.AIR && identifier != BuiltInRegistries.BLOCK.getKey(Blocks.AIR)) null else block
    }

    /**
     * 获取方块的本地化显示名称。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    fun displayName(block: Block?): String {
        if (block == null) {
            return ""
        }
        return block.name.string
    }

    /**
     * 生成用于搜索方块的规范化文本。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    fun searchText(block: Block): String {
        return (displayName(block) + " " + id(block)).lowercase(Locale.ROOT)
    }

    /**
     * 将方块集合转换为注册表标识符列表。
     *
     * @param blocks 方块集合
     * @return 操作结果
     */
    fun toIds(blocks: Collection<Block>?): List<String> {
        val ids = ArrayList<String>()
        if (blocks == null) {
            return ids
        }
        for (block in blocks) {
            if (isSelectable(block)) {
                ids.add(id(block))
            }
        }
        return ids
    }

}
