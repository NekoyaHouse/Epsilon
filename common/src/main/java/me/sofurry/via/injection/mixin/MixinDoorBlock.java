package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
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

@Mixin(DoorBlock.class)
public abstract class MixinDoorBlock extends Block {

    @Unique
    private static final Map<Direction, VoxelShape> viaVersion$shape_bedrock = Map.of(
            Direction.NORTH, Shapes.box(0, 0, 0.8175, 1, 1, 1),
            Direction.SOUTH, Shapes.box(0, 0, 0, 1, 1, 0.1825),
            Direction.WEST, Shapes.box(0.8175, 0, 0, 1, 1, 1),
            Direction.EAST, Shapes.box(0, 0, 0, 0.1825, 1, 1)
    );

    @Shadow
    @Final
    private static Map<Direction, VoxelShape> SHAPES;

    @Shadow
    @Final
    public static EnumProperty<Direction> FACING;

    public MixinDoorBlock(final Properties settings) {
        super(settings);
    }

    @Redirect(method = "getShape", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/DoorBlock;SHAPES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private Map<Direction, VoxelShape> changeOutlineShape() {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return viaVersion$shape_bedrock;
        }
        return SHAPES;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return SHAPES.get(state.getValue(FACING));
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
