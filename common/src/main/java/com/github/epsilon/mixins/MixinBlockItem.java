package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.PlaceBlockEvent;
import com.github.epsilon.modules.impl.render.NoGhostBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @Shadow
    protected abstract BlockState getPlacementState(BlockPlaceContext context);

    @Inject(method = "placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockPlaceContext context, BlockState placementState, CallbackInfoReturnable<Boolean> cir) {
        if (!context.getLevel().isClientSide()) return;

        if (EventBus.INSTANCE.post(new PlaceBlockEvent(context.getClickedPos(), placementState.getBlock())).isCancelled()) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"), name = "placedState")
    private BlockState modifyState(BlockState placedState, BlockPlaceContext placeContext) {
        NoGhostBlocks noGhostBlocks = NoGhostBlocks.INSTANCE;
        if (noGhostBlocks.isEnabled() && noGhostBlocks.placing.getValue()) {
            return getPlacementState(placeContext);
        }
        return placedState;
    }

}
