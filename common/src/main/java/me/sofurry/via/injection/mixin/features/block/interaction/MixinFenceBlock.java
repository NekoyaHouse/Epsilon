package me.sofurry.via.injection.mixin.features.block.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FenceBlock.class)
public abstract class MixinFenceBlock extends CrossCollisionBlock {

    protected MixinFenceBlock(float radius1, float radius2, float boundingHeight1, float boundingHeight2, float collisionHeight, Properties settings) {
        super(radius1, radius2, boundingHeight1, boundingHeight2, collisionHeight, settings);
    }

    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level world, @NonNull BlockPos pos, @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hit) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return stack.is(Items.LEAD) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_10)) {
            return InteractionResult.SUCCESS;
        } else {
            return super.useItemOn(stack, state, world, pos, player, hand, hit);
        }
    }

}
