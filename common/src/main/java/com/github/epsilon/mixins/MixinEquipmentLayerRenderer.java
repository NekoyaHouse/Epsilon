package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(EquipmentLayerRenderer.class)
public class MixinEquipmentLayerRenderer {

    @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType redirectRenderType(Identifier texture, Operation<RenderType> original, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId, Model<?> model, Object state, ItemStack itemStack) {
        if (state instanceof EntityRenderState entityState && ((EntityRenderStateAccessor) entityState).epsilon$getEntity() instanceof Player player && player != mc.player) {
            Chams chamsModule = Chams.INSTANCE;
            if (chamsModule.isEnabled() && chamsModule.noDepth.getValue()) {
                return Chams.INSTANCE.getRenderType(texture);
            }
        }
        return original.call(texture);
    }

}
