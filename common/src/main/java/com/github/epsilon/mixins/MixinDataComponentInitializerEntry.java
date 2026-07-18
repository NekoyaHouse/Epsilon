package com.github.epsilon.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.core.component.DataComponentInitializers$InitializerEntry")
public class MixinDataComponentInitializerEntry<T> {

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/component/DataComponentInitializers$Initializer;run(Lnet/minecraft/core/component/DataComponentMap$Builder;Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/resources/ResourceKey;)V"))
    private void skipMissingInstrument(DataComponentInitializers.Initializer<T> initializer, DataComponentMap.Builder components, HolderLookup.Provider context, ResourceKey<T> key, Operation<Void> original) {
        try {
            original.call(initializer, components, context, key);
        } catch (IllegalStateException exception) {
            // 某些旧协议/异常服务端不会同步 1.21.6+ 的 instrument
        }
    }

}
