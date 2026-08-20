package me.sofurry.via.injection.mixin.features.world.duplicated_sounds;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @WrapWithCondition(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private boolean disableBlockPlaceSounds(Level instance, Entity except, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        return !ClientSetting.INSTANCE.serversidePlaceSounds.isEnabled(); // Sent by the server in older versions
    }

}
