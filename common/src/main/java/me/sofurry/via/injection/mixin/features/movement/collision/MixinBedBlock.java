package me.sofurry.via.injection.mixin.features.movement.collision;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BedBlock.class)
public abstract class MixinBedBlock extends HorizontalDirectionalBlock {

    protected MixinBedBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public float getBounceRestitution() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
            return 0F;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return 0.66F;
        } else {
            return super.getBounceRestitution();
        }
    }

}
