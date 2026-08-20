package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowerPotBlock.class)
public abstract class MixinFlowerPotBlock {

    @Shadow
    @Final
    private Block potted;

    @Inject(method = "useWithoutItem", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/FlowerPotBlock;potted:Lnet/minecraft/world/level/block/Block;", ordinal = 0, opcode = Opcodes.GETFIELD), cancellable = true)
    private void alwaysConsume(CallbackInfoReturnable<InteractionResult> ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_10) && potted != Blocks.AIR) {
            ci.setReturnValue(InteractionResult.CONSUME);
        }
    }

}
