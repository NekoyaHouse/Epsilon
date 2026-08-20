package me.sofurry.via.injection.mixin.features.block.shape;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CropBlock.class, CarrotBlock.class, PotatoBlock.class})
public abstract class MixinCropBlocks {

    @Unique
    private static final VoxelShape viaVersion$shape_r1_8_x = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(CallbackInfoReturnable<VoxelShape> cir) {
        if (ClientSetting.INSTANCE.legacyCropOutlines.isEnabled()) {
            cir.setReturnValue(viaVersion$shape_r1_8_x);
        }
    }

}
