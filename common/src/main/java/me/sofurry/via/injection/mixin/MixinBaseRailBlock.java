package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BaseRailBlock.class)
public abstract class MixinBaseRailBlock extends Block {

    @Shadow
    @Final
    private static VoxelShape SHAPE_SLOPE;

    @Unique
    private static final VoxelShape viaVersion$ascending_shape_r1_10_x = Shapes.block();

    @Unique
    private static final VoxelShape viaVersion$ascending_shape_r1_9_x = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.5D, 16.0D);

    @Unique
    private static final VoxelShape viaVersion$ascending_shape_r1_8_x = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D);

    public MixinBaseRailBlock(Properties settings) {
        super(settings);
    }

    @Redirect(method = "getShape", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/BaseRailBlock;SHAPE_SLOPE:Lnet/minecraft/world/phys/shapes/VoxelShape;", opcode = Opcodes.GETSTATIC))
    private VoxelShape changeOutlineShape() {
        if (ProtocolTranslator.getTargetVersion().equalTo(ProtocolVersion.v1_10)) {
            return viaVersion$ascending_shape_r1_10_x;
        } else if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_9, ProtocolVersion.v1_9_3)) {
            return viaVersion$ascending_shape_r1_9_x;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return viaVersion$ascending_shape_r1_8_x;
        } else {
            return SHAPE_SLOPE;
        }
    }

}
