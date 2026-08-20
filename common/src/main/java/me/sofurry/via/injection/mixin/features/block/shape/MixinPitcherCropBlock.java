package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PitcherCropBlock.class)
public abstract class MixinPitcherCropBlock extends DoublePlantBlock {

    @Shadow
    @Final
    public static IntegerProperty AGE;

    @Shadow
    @Final
    public static EnumProperty<DoubleBlockHalf> HALF;

    @Shadow
    @Final
    private static VoxelShape SHAPE_BULB;

    @Shadow
    @Final
    private static VoxelShape SHAPE_CROP;

    @Unique
    private static final VoxelShape viaVersion$grown_upper_outline_shape_r1_21_4 = Block.box(3.0, 0.0, 3.0, 13.0, 15.0, 13.0);

    @Unique
    private static final VoxelShape viaVersion$grown_lower_outline_shape_r1_21_4 = Block.box(3.0, -1.0, 3.0, 13.0, 16.0, 13.0);

    @Unique
    private static final VoxelShape[] viaVersion$upper_outline_shapes_r1_21_4 = new VoxelShape[]{Block.box(3.0, 0.0, 3.0, 13.0, 11.0, 13.0), viaVersion$grown_upper_outline_shape_r1_21_4};

    @Unique
    private static final VoxelShape[] viaVersion$lower_outline_shapes_r1_21_4 = new VoxelShape[]{SHAPE_BULB, Block.box(3.0, -1.0, 3.0, 13.0, 14.0, 13.0), viaVersion$grown_lower_outline_shape_r1_21_4, viaVersion$grown_lower_outline_shape_r1_21_4, viaVersion$grown_lower_outline_shape_r1_21_4};

    public MixinPitcherCropBlock(final Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            final int age = state.getValue(AGE);
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                cir.setReturnValue(viaVersion$upper_outline_shapes_r1_21_4[Math.min(Math.abs(4 - (age + 1)), viaVersion$upper_outline_shapes_r1_21_4.length - 1)]);
            } else {
                cir.setReturnValue(viaVersion$lower_outline_shapes_r1_21_4[age]);
            }
        }
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void changeBlockStatePropertyPriority(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            if (state.getValue(AGE) == 0) {
                cir.setReturnValue(SHAPE_BULB);
            } else {
                cir.setReturnValue(state.getValue(HALF) == DoubleBlockHalf.LOWER ? SHAPE_CROP : super.getCollisionShape(state, level, pos, context));
            }
        }
    }

}
