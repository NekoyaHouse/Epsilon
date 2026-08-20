package me.sofurry.via.injection.mixin;

import com.google.common.collect.ImmutableMap;
import me.sofurry.via.features.entity.legacy_boat_model.BoatModel1_8;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LayerDefinitions.class)
public abstract class MixinLayerDefinitions {

    @ModifyVariable(method = "createRoots", at = @At(value = "STORE", ordinal = 0), name = "result")
    private static ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> addBoatModel(ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> result) {
        return result.put(BoatModel1_8.MODEL_LAYER, BoatModel1_8.getTexturedModelData());
    }

}
