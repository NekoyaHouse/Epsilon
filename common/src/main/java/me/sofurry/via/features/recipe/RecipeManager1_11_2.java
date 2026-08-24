package me.sofurry.via.features.recipe;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class RecipeManager1_11_2 {

    private final Multimap<RecipeType<?>, RecipeHolder<?>> recipesByType;
    private final Map<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipesById;

    public RecipeManager1_11_2(final Iterable<RecipeHolder<?>> recipes) {
        final ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> recipesByTypeBuilder = ImmutableMultimap.builder();
        final ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> recipesByIdBuilder = ImmutableMap.builder();

        for (RecipeHolder<?> recipeEntry : recipes) {
            final RecipeType<?> recipeType = recipeEntry.value().getType();
            recipesByTypeBuilder.put(recipeType, recipeEntry);
            recipesByIdBuilder.put(recipeEntry.id(), recipeEntry);
        }

        this.recipesByType = recipesByTypeBuilder.build();
        this.recipesById = recipesByIdBuilder.build();
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getFirstMatch(final RecipeType<T> type, final I input, final Level world) {
        if (input.isEmpty()) {
            return Optional.empty();
        } else {
            return this.recipesByType.get(type).stream().map(e -> (RecipeHolder<T>) e).filter(recipe -> recipe.value().matches(input, world)).findFirst();
        }
    }

    public Optional<RecipeHolder<?>> get(final ResourceKey<Recipe<?>> id) {
        return Optional.ofNullable(this.recipesById.get(id));
    }

    public Collection<RecipeHolder<?>> values() {
        return this.recipesById.values();
    }

    public Stream<ResourceKey<Recipe<?>>> keys() {
        return this.recipesById.keySet().stream();
    }

}
