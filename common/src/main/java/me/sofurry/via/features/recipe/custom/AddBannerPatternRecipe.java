package me.sofurry.via.features.recipe.custom;

import com.mojang.serialization.MapCodec;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class AddBannerPatternRecipe extends CustomRecipe {

    public static final RecipeSerializer<AddBannerPatternRecipe> SERIALIZER = new RecipeSerializer<>(MapCodec.unit(new AddBannerPatternRecipe()), StreamCodec.unit(new AddBannerPatternRecipe()));

    @Override
    public boolean matches(CraftingInput input, Level world) {
        boolean foundBanner = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof BannerItem) {
                if (foundBanner)
                    return false;
                if (stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size() >= 6)
                    return false;
                foundBanner = true;
            }
        }
        return foundBanner && getBannerPattern(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BannerItem) {
                result = stack.copy();
                result.setCount(1);
                break;
            }
        }

        final BannerPattern_1_13_2 pattern = getBannerPattern(input);
        if (pattern != null) {
            final Holder.Reference<BannerPattern> patternKey = Minecraft.getInstance().getConnection().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(pattern.getKey());
            DyeColor color = ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) ? DyeColor.BLACK : DyeColor.WHITE;
            for (int i = 0; i < input.size(); i++) {
                ItemStack item = input.getItem(i);
                if (item.get(DataComponents.DYE) != null) {
                    color = item.get(DataComponents.DYE);
                }
            }

            final BannerPatternLayers.Builder patternsBuilder = new BannerPatternLayers.Builder();
            if (result.has(DataComponents.BANNER_PATTERNS)) {
                patternsBuilder.addAll(result.get(DataComponents.BANNER_PATTERNS));
            }
            patternsBuilder.add(new BannerPatternLayers.Layer(patternKey, color));
            result.set(DataComponents.BANNER_PATTERNS, patternsBuilder.build());
        }

        return result;
    }

    @Override
    public RecipeSerializer<AddBannerPatternRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static BannerPattern_1_13_2 getBannerPattern(CraftingInput input) {
        for (BannerPattern_1_13_2 pattern : BannerPattern_1_13_2.values()) {
            if (!pattern.isCraftable())
                continue;

            boolean matches = true;
            if (pattern.hasBaseStack()) {
                boolean foundBaseItem = false;
                boolean foundDye = false;
                for (int i = 0; i < input.size(); i++) {
                    ItemStack stack = input.getItem(i);
                    if (!stack.isEmpty() && !(stack.getItem() instanceof BannerItem)) {
                        if (stack.getItem() instanceof DyeItem) {
                            if (foundDye) {
                                matches = false;
                                break;
                            }
                            foundDye = true;
                        } else {
                            if (foundBaseItem || !ItemStack.isSameItem(stack, pattern.getBaseStack())) {
                                matches = false;
                                break;
                            }
                            foundBaseItem = true;
                        }
                    }
                }
                if (!foundBaseItem || (!foundDye && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_10)))
                    matches = false;
            } else if (input.size() == pattern.getRecipePattern().length * pattern.getRecipePattern()[0].length()) {
                DyeColor patternColor = null;
                for (int i = 0; i < input.size(); i++) {
                    int row = i / 3;
                    int col = i % 3;
                    ItemStack stack = input.getItem(i);
                    Item item = stack.getItem();
                    if (!stack.isEmpty() && !(item instanceof BannerItem)) {
                        DyeColor color = stack.get(DataComponents.DYE);
                        if (color == null) {
                            matches = false;
                            break;
                        }

                        if (patternColor != null && color != patternColor) {
                            matches = false;
                            break;
                        }

                        if (pattern.getRecipePattern()[row].charAt(col) == ' ') {
                            matches = false;
                            break;
                        }

                        patternColor = color;
                    } else if (pattern.getRecipePattern()[row].charAt(col) != ' ') {
                        matches = false;
                        break;
                    }
                }
            } else {
                matches = false;
            }

            if (matches)
                return pattern;
        }

        return null;
    }

}
