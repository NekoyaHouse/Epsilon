package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(TrapDoorBlock.class)
public abstract class MixinTrapDoorBlock extends HorizontalDirectionalBlock {

    @Unique
    private static final Map<Direction, VoxelShape> viaVersion$shape_bedrock = Map.of(
            Direction.NORTH, Shapes.box(0, 0, 0.8175, 1, 1, 1),
            Direction.SOUTH, Shapes.box(0, 0, 0, 1, 1, 0.1825),
            Direction.WEST, Shapes.box(0.8175, 0, 0, 1, 1, 1),
            Direction.EAST, Shapes.box(0, 0, 0, 0.1825, 1, 1),
            Direction.DOWN, Shapes.box(0, 0.8175, 0, 1, 1, 1),
            Direction.UP, Shapes.box(0, 0, 0, 1, 0.1825, 1)
    );

    @Shadow
    @Final
    private static Map<Direction, VoxelShape> SHAPES;

    @Shadow
    @Final
    public static EnumProperty<Half> HALF;

    @Shadow
    @Final
    public static BooleanProperty OPEN;

    public MixinTrapDoorBlock(final Properties settings) {
        super(settings);
    }

    @Redirect(method = "getShape", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/TrapDoorBlock;SHAPES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private Map<Direction, VoxelShape> changeOutlineShape() {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return viaVersion$shape_bedrock;
        }
        return SHAPES;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return SHAPES.get(state.getValue(OPEN) ? state.getValue(FACING) : (state.getValue(HALF) == Half.TOP ? Direction.DOWN : Direction.UP));
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
