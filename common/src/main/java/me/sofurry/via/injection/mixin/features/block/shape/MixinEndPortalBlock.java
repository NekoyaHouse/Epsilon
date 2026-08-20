package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalBlock.class)
public abstract class MixinEndPortalBlock extends BaseEntityBlock {

    @Unique
    private static final VoxelShape viaVersion$shape_r1_8_x = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    @Unique
    private static final VoxelShape viaVersion$shape_r1_16_5 = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    protected MixinEndPortalBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(viaVersion$shape_r1_8_x);
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
            cir.setReturnValue(viaVersion$shape_r1_16_5);
        }
    }

}
