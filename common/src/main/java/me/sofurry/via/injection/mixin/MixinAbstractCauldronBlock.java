package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.platform.ViaVersionPlatform;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCauldronBlock.class)
public abstract class MixinAbstractCauldronBlock extends Block {

    @Unique
    private static final VoxelShape viaVersion$collision_shape_r1_12_2_bedrock = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
            Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 1.0),
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.125),
            Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 1.0),
            Shapes.box(0.0, 0.0, 0.875, 1.0, 1.0, 1.0)
    );

    @Unique
    private boolean viaVersion$requireOriginalShape;

    public MixinAbstractCauldronBlock(final Properties properties) {
        super(properties);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaVersionPlatform.isModLoaded("moreculling") && viaVersion$requireOriginalShape) {
            viaVersion$requireOriginalShape = false;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(Shapes.block());
        } else if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(viaVersion$collision_shape_r1_12_2_bedrock);
        }
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState blockState, final BlockGetter blockGetter, final BlockPos blockPos, final CollisionContext collisionContext) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return viaVersion$collision_shape_r1_12_2_bedrock;
        } else {
            return super.getCollisionShape(blockState, blockGetter, blockPos, collisionContext);
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        // Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/246
        // MoreCulling is caching the culling shape and doesn't reload it, so we have to force vanilla's shape here.
        viaVersion$requireOriginalShape = true;
        return super.getOcclusionShape(state);
    }

}
