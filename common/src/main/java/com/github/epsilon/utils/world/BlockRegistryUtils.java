package com.github.epsilon.utils.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class BlockRegistryUtils {

    private BlockRegistryUtils() {
    }

    /**
     * 获取注册表中全部可选择方块。
     *
     * @return 获取或计算得到的结果
     */
    public static List<Block> allSelectableBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isSelectable(block)) {
                blocks.add(block);
            }
        }
        blocks.sort(Comparator.comparing(BlockRegistryUtils::displayName, String.CASE_INSENSITIVE_ORDER));
        return blocks;
    }

    /**
     * 判断方块是否适合出现在方块选择列表中。
     *
     * @param block 方块
     * @return 判断结果
     */
    public static boolean isSelectable(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        if (block.asItem() == Items.AIR) {
            return false;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return !id.getPath().endsWith("_wall_banner");
    }

    /**
     * 获取方块的注册表标识符字符串。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    public static String id(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id.toString();
    }

    /**
     * 按注册表标识符查找方块。
     *
     * @param id 方块注册表标识符
     * @return 操作结果
     */
    public static Block byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
        if (identifier == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        return block == Blocks.AIR && !identifier.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR)) ? null : block;
    }

    /**
     * 获取方块的本地化显示名称。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    public static String displayName(Block block) {
        if (block == null) {
            return "";
        }
        return block.getName().getString();
    }

    /**
     * 生成用于搜索方块的规范化文本。
     *
     * @param block 方块
     * @return 获取或计算得到的结果
     */
    public static String searchText(Block block) {
        return (displayName(block) + " " + id(block)).toLowerCase(Locale.ROOT);
    }

    /**
     * 将方块集合转换为注册表标识符列表。
     *
     * @param blocks 方块集合
     * @return 操作结果
     */
    public static List<String> toIds(Collection<Block> blocks) {
        List<String> ids = new ArrayList<>();
        if (blocks == null) {
            return ids;
        }
        for (Block block : blocks) {
            if (isSelectable(block)) {
                ids.add(id(block));
            }
        }
        return ids;
    }

}
