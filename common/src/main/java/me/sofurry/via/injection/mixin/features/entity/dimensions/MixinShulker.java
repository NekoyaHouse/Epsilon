package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shulker.class)
public abstract class MixinShulker extends AbstractGolem { // ???????????????

    protected MixinShulker(final EntityType<? extends AbstractGolem> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void keepDefaultDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(super.getDefaultDimensions(pose));
        }
    }

}
