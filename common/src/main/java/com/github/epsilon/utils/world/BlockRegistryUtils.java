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

    public static String id(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id.toString();
    }

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

    public static String displayName(Block block) {
        if (block == null) {
            return "";
        }
        return block.getName().getString();
    }

    public static String searchText(Block block) {
        return (displayName(block) + " " + id(block)).toLowerCase(Locale.ROOT);
    }

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
