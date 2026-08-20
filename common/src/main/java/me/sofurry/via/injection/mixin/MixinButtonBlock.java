package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ButtonBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ButtonBlock.class)
public abstract class MixinButtonBlock {

    @WrapWithCondition(
            method = "playSound",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V"))
    private boolean disableClickSounds(LevelAccessor instance, Entity except, BlockPos pos, SoundEvent soundEvent, SoundSource source) {
        return !ClientSetting.INSTANCE.serversidePlaceSounds.isEnabled(); // Sent by the server in older versions
    }

}
