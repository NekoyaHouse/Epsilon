package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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

@Mixin(LadderBlock.class)
public abstract class MixinLadderBlock extends Block {

    @Unique
    private static final Map<Direction, VoxelShape> viaVersion$shapes_r1_8_x = Map.of(
            Direction.NORTH, Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D),
            Direction.SOUTH, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D),
            Direction.WEST, Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Direction.EAST, Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D)
    );

    @Unique
    private static final Map<Direction, VoxelShape> viaVersion$shapes_bedrock = Map.of(
            Direction.NORTH, Shapes.box(0, 0, 0.8125, 1, 1, 1),
            Direction.SOUTH, Shapes.box(0, 0, 0, 1, 1, 0.1875),
            Direction.WEST, Shapes.box(0.8125, 0, 0, 1, 1, 1),
            Direction.EAST, Shapes.box(0, 0, 0, 0.1875, 1, 1)
    );

    @Shadow
    @Final
    public static Map<Direction, VoxelShape> SHAPES;

    @Shadow
    @Final
    public static EnumProperty<Direction> FACING;

    public MixinLadderBlock(final Properties settings) {
        super(settings);
    }

    @Redirect(method = "getShape", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/LadderBlock;SHAPES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private Map<Direction, VoxelShape> changeOutlineShape() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return viaVersion$shapes_r1_8_x;
        } else if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return viaVersion$shapes_bedrock;
        } else {
            return SHAPES;
        }
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)
                || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return SHAPES.get(state.getValue(FACING));
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
