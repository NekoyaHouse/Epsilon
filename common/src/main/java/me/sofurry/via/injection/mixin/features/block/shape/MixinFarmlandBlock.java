package me.sofurry.via.injection.mixin.features.block.shape;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FarmlandBlock.class)
public abstract class MixinFarmlandBlock extends Block {

    @Shadow
    @Final
    private static VoxelShape SHAPE;

    public MixinFarmlandBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().isLocalServer()) {
            // When joining the singleplayer, we set the target version to the native version when the integrated server is started
            // However this is already to late for blocks since the world and entities have already been loaded, causing block collisions
            // to make issues as described via https://github.com/ViaVersion/ViaFabricPlus/issues/436. A proper fix would be to
            // move version setting to earlier stage but /shrug
            return;
        }

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_9_3)) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().isLocalServer()) {
            // See above for explanation
            return super.getOcclusionShape(state);
        }

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_9_3)) {
            return SHAPE;
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
