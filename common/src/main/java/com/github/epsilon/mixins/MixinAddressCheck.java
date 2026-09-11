package com.github.epsilon.mixins;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

@Mixin(AddressCheck.class)
public interface MixinAddressCheck {

    @ModifyVariable(method = "createFromService", at = @At("STORE"), name = "blockLists")
    private static ImmutableList<Predicate<String>> clearBlockLists(ImmutableList<Predicate<String>> blockLists) {
        return ImmutableList.of();
    }

}