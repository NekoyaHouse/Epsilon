package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.sofurry.via.features.block.interaction.Block1_14;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(WallBlock.class)
public abstract class MixinWallBlock extends Block {

    @Unique
    private final Object2IntMap<BlockState> viaVersion$shapeIndexCache_r1_12_2 = new Object2IntOpenHashMap<>();

    @Shadow
    @Final
    private Function<BlockState, VoxelShape> shapes;

    @Unique
    private VoxelShape[] viaVersion$collision_shape_r1_12_2;

    @Unique
    private VoxelShape[] viaVersion$outline_shape_r1_12_2;

    public MixinWallBlock(Properties settings) {
        super(settings);
    }

    @Unique
    private static BlockState viaVersion$oldWallPlacementLogic(BlockState state) {
        boolean addUp = false;
        if (state.getValue(WallBlock.NORTH) == WallSide.TALL) {
            state = state.setValue(WallBlock.NORTH, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.EAST) == WallSide.TALL) {
            state = state.setValue(WallBlock.EAST, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.SOUTH) == WallSide.TALL) {
            state = state.setValue(WallBlock.SOUTH, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.WEST) == WallSide.TALL) {
            state = state.setValue(WallBlock.WEST, WallSide.LOW);
            addUp = true;
        }
        if (addUp) {
            state = state.setValue(WallBlock.UP, true);
        }
        return state;
    }

    @Unique
    private static int viaVersion$getDirectionMask(Direction dir) {
        return 1 << dir.get2DDataValue();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initShapes1_12_2(Properties properties, CallbackInfo ci) {
        this.viaVersion$collision_shape_r1_12_2 = this.viaVersion$createShapes1_12_2(24.0F, 24.0F);
        this.viaVersion$outline_shape_r1_12_2 = this.viaVersion$createShapes1_12_2(16.0F, 14.0F);
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void modifyPlacementState(CallbackInfoReturnable<BlockState> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(viaVersion$oldWallPlacementLogic(cir.getReturnValue()));
        }
    }

    @Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true)
    private void modifyBlockState(CallbackInfoReturnable<BlockState> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(viaVersion$oldWallPlacementLogic(cir.getReturnValue()));
        }
    }

    @Inject(method = "connectsTo", at = @At("RETURN"), cancellable = true)
    private void shouldConnectTo1_14(BlockState state, boolean faceSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14)) {
            if (!Block1_14.isExceptBlockForAttachWithPiston(state.getBlock())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void changeCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.getValue(WallBlock.UP) && ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(this.viaVersion$collision_shape_r1_12_2[this.viaVersion$getShapeIndex(state)]);
        }
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.getValue(WallBlock.UP) && ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(this.viaVersion$outline_shape_r1_12_2[this.viaVersion$getShapeIndex(state)]);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(BlockState state) {
        if (state.getValue(WallBlock.UP) && ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return this.shapes.apply(state);
        } else {
            return super.getOcclusionShape(state);
        }
    }

    @Unique
    private VoxelShape[] viaVersion$createShapes1_12_2(final float height1, final float height2) {
        final float f = 4.0F;
        final float g = 12.0F;
        final float h = 5.0F;
        final float i = 11.0F;

        final VoxelShape baseShape = Block.box(f, 0.0D, f, g, height1, g);
        final VoxelShape northShape = Block.box(h, 0.0, 0.0D, i, height2, i);
        final VoxelShape southShape = Block.box(h, 0.0, h, i, height2, 16.0D);
        final VoxelShape westShape = Block.box(0.0D, 0.0, h, i, height2, i);
        final VoxelShape eastShape = Block.box(h, 0.0, h, 16.0D, height2, i);
        final VoxelShape[] voxelShapes = new VoxelShape[]{
                Shapes.empty(),
                Block.box(f, 0.0, h, g, height1, 16.0D),
                Block.box(0.0D, 0.0, f, i, height1, g),
                Block.box(f - 4, 0.0, h - 1, g, height1, 16.0D),
                Block.box(f, 0.0, 0.0D, g, height1, i),
                Shapes.or(southShape, northShape),
                Block.box(f - 4, 0.0, 0.0D, g, height1, i + 1),
                Block.box(f - 4, 0.0, h - 5, g, height1, 16.0D),
                Block.box(h, 0.0, f, 16.0D, height1, g),
                Block.box(h - 1, 0.0, f, 16.0D, height1, g + 4),
                Shapes.or(westShape, eastShape),
                Block.box(h - 5, 0.0, f, 16.0D, height1, g + 4),
                Block.box(f, 0.0, 0.0D, g + 4, height1, i + 1),
                Block.box(f, 0.0, 0.0D, g + 4, height1, i + 5),
                Block.box(h - 5, 0.0, f - 4, 16.0D, height1, g),
                Block.box(0, 0.0, 0, 16.0D, height1, 16.0D)
        };

        for (int j = 0; j < 16; ++j) {
            voxelShapes[j] = Shapes.or(baseShape, voxelShapes[j]);
        }

        return voxelShapes;
    }

    @Unique
    private int viaVersion$getShapeIndex(BlockState state) {
        return this.viaVersion$shapeIndexCache_r1_12_2.computeIntIfAbsent(state, statex -> {
            int i = 0;
            if (!WallSide.NONE.equals(statex.getValue(WallBlock.NORTH))) {
                i |= viaVersion$getDirectionMask(Direction.NORTH);
            }

            if (!WallSide.NONE.equals(statex.getValue(WallBlock.EAST))) {
                i |= viaVersion$getDirectionMask(Direction.EAST);
            }

            if (!WallSide.NONE.equals(statex.getValue(WallBlock.SOUTH))) {
                i |= viaVersion$getDirectionMask(Direction.SOUTH);
            }

            if (!WallSide.NONE.equals(statex.getValue(WallBlock.WEST))) {
                i |= viaVersion$getDirectionMask(Direction.WEST);
            }

            return i;
        });
    }

}
