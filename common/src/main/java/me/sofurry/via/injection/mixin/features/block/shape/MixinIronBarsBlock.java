package me.sofurry.via.injection.mixin.features.block.shape;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.block.shape.ICrossCollisionBlock;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronBarsBlock.class)
public abstract class MixinIronBarsBlock extends CrossCollisionBlock implements ICrossCollisionBlock {

    @Unique
    private VoxelShape[] viaVersion$shape_r1_12_2;

    @Unique
    private VoxelShape[] viaVersion$shape_r1_8;

    protected MixinIronBarsBlock(float radius1, float radius2, float boundingHeight1, float boundingHeight2, float collisionHeight, Properties settings) {
        super(radius1, radius2, boundingHeight1, boundingHeight2, collisionHeight, settings);
    }

    @Inject(method = "getVisualShape", at = @At("HEAD"), cancellable = true)
    private void useCollisionVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(this.getCollisionShape(state, level, pos, context));
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initShapes1_8(Properties properties, CallbackInfo ci) {
        final float f = 7.0F;
        final float g = 9.0F;
        final float h = 7.0F;
        final float i = 9.0F;

        final VoxelShape baseShape = Block.box(f, 0.0, f, g, (float) 16.0, g);

        viaVersion$shape_r1_12_2 = new VoxelShape[]{
                baseShape,
                Block.box(h, 0.0, h, i, 16.0, 16.0), // south
                Block.box(0.0, 0.0, h, i, 16.0, i), // west
                Block.box(0.0, 0.0, h, i, 16.0, 16.0), // south-west corner
                Block.box(h, 0.0, 0.0, i, 16.0, i), // north
                Block.box(h, 0.0, 0.0, i, 16.0, 16.0), // south-north line
                Block.box(0.0, 0.0, 0.0, i, 16.0, i), // west-north corner
                Block.box(0.0, 0.0, 0.0, i, 16.0, 16.0), // south-west-north T
                Block.box(h, 0.0, h, 16.0, 16.0, i), // east
                Block.box(h, 0.0, h, 16.0, 16.0, 16.0), // south-east corner
                Block.box(0.0, 0.0, h, 16.0, 16.0, i), // west-east line
                Block.box(0.0, 0.0, h, 16.0, 16.0, 16.0), // south-west-east T
                Block.box(h, 0.0, 0.0, 16.0, 16.0, i), // north-east corner
                Block.box(h, 0.0, 0.0, 16.0, 16.0, 16.0), // south-north-east T
                Block.box(0.0, 0.0, 0.0, 16.0, 16.0, i), // west-north-east T
                Shapes.block() // cross
        };

        final VoxelShape northShape = Block.box(h, (float) 0.0, 0.0, i, (float) 16.0, i - 1);
        final VoxelShape southShape = Block.box(h, (float) 0.0, h + 1, i, (float) 16.0, 16.0);
        final VoxelShape westShape = Block.box(0.0, (float) 0.0, h, i - 1, (float) 16.0, i);
        final VoxelShape eastShape = Block.box(h + 1, (float) 0.0, h, 16.0, (float) 16.0, i);

        final VoxelShape northEastCornerShape = Shapes.or(northShape, eastShape);
        final VoxelShape southWestCornerShape = Shapes.or(southShape, westShape);

        viaVersion$shape_r1_8 = new VoxelShape[]{
                baseShape,
                southShape,
                westShape,
                southWestCornerShape,
                northShape,
                Shapes.or(southShape, northShape),
                Shapes.or(westShape, northShape),
                Shapes.or(southWestCornerShape, northShape),
                eastShape,
                Shapes.or(southShape, eastShape),
                Shapes.or(westShape, eastShape),
                Shapes.or(southWestCornerShape, eastShape),
                northEastCornerShape,
                Shapes.or(southShape, northEastCornerShape),
                Shapes.or(westShape, northEastCornerShape),
                Shapes.or(southWestCornerShape, northEastCornerShape)
        };
    }

    @Override
    public @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ClientSetting.INSTANCE.legacyPaneOutlines.isEnabled()) {
            return this.viaVersion$shape_r1_12_2[this.viaVersion$getShapeIndex(state)];
        } else {
            return super.getShape(state, world, pos, context);
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return this.viaVersion$shape_r1_8[this.viaVersion$getShapeIndex(state)];
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
