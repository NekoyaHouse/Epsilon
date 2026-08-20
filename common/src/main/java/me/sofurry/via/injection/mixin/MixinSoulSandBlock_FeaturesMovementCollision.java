package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoulSandBlock.class)
public abstract class MixinSoulSandBlock_FeaturesMovementCollision extends Block {

    public MixinSoulSandBlock_FeaturesMovementCollision(Properties settings) {
        super(settings);
    }

    @Override
    protected void entityInside(final BlockState state, final Level world, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier handler, final boolean bl) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.4D, 1, 0.4D));
        }
    }

    @Override
    public float getSpeedFactor() {
        return ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4) ? 1F : super.getSpeedFactor();
    }

}
