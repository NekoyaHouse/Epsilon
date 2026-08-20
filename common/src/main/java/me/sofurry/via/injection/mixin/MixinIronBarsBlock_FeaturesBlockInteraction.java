package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronBarsBlock.class)
public abstract class MixinIronBarsBlock_FeaturesBlockInteraction extends CrossCollisionBlock {

    protected MixinIronBarsBlock_FeaturesBlockInteraction(final float radius1, final float radius2, final float boundingHeight1, final float boundingHeight2, final float collisionHeight, final Properties settings) {
        super(radius1, radius2, boundingHeight1, boundingHeight2, collisionHeight, settings);
    }

    @WrapOperation(method = "getStateForPlacement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/IronBarsBlock;attachsTo(Lnet/minecraft/world/level/block/state/BlockState;Z)Z"))
    private boolean countConnections(IronBarsBlock instance, BlockState state, boolean faceSolid, Operation<Boolean> original, @Share("count") LocalIntRef countRef) {
        final boolean connectsTo = original.call(instance, state, faceSolid);
        if (connectsTo) {
            countRef.set(countRef.get() + 1);
        }
        return connectsTo;
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void changePlacementState(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir, @Share("count") LocalIntRef countRef) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8) && countRef.get() == 0) {
            cir.setReturnValue(cir.getReturnValue().setValue(NORTH, true).setValue(SOUTH, true).setValue(WEST, true).setValue(EAST, true));
        }
    }

}
