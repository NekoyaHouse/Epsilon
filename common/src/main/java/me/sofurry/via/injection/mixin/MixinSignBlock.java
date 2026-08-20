package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignBlock.class)
public abstract class MixinSignBlock {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void changeInteractionCalculation(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (!level.isClientSide()) {
            return;
        }

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            // <= 1.14.4 doesn't have any sign interactions.
            cir.setReturnValue(InteractionResult.SUCCESS);
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            // Removes the isWaxed() condition and reverts the interaction changes from 1.19.4 -> 1.20 when signs
            // got a front and back side.
            final ItemStack itemInHand = player.getItemInHand(hand);
            final Item item = itemInHand.getItem();
            final boolean isSuccess = (item instanceof DyeItem || itemInHand.is(Items.GLOW_INK_SAC) || itemInHand.is(Items.INK_SAC)) && player.mayBuild();

            cir.setReturnValue(isSuccess ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
        }
    }

}
