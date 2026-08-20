package me.sofurry.via.injection.mixin.features.block.shape;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(ChestBlock.class)
public abstract class MixinChestBlock extends AbstractChestBlock<ChestBlockEntity> {

    @Unique
    private static final VoxelShape viaVersion$single_chest_shape_bedrock = Shapes.box(0.025, 0, 0.025, 0.975, 0.95, 0.975);

    @Unique
    private static final Map<Direction, VoxelShape> viaVersion$double_chest_shapes_bedrock = Map.of(
            Direction.NORTH, Shapes.box(0.025, 0, 0, 0.975, 0.95, 0.975),
            Direction.SOUTH, Shapes.box(0.025, 0, 0.025, 0.975, 0.95, 1),
            Direction.WEST, Shapes.box(0, 0, 0.025, 0.975, 0.95, 0.975),
            Direction.EAST, Shapes.box(0.025, 0, 0.025, 1, 0.95, 0.975)
    );

    @Shadow
    @Final
    private static Map<Direction, VoxelShape> HALF_SHAPES;

    @Shadow
    @Final
    private static VoxelShape SHAPE;

    @Shadow
    @Final
    public static EnumProperty<ChestType> TYPE;

    @Shadow
    public static Direction getConnectedDirection(final BlockState state) {
        return null;
    }

    protected MixinChestBlock(Properties settings, Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityTypeSupplier) {
        super(settings, blockEntityTypeSupplier);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_2)) {
            cir.setReturnValue(Shapes.block());
        } else if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(switch (state.getValue(TYPE)) {
                case SINGLE -> viaVersion$single_chest_shape_bedrock;
                case LEFT, RIGHT -> viaVersion$double_chest_shapes_bedrock.get(getConnectedDirection(state));
            });
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_2)
                || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
                return SHAPE;
            } else {
                return HALF_SHAPES.get(ChestBlock.getConnectedDirection(state));
            }
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
