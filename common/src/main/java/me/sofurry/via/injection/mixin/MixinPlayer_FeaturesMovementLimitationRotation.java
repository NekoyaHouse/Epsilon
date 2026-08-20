package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class MixinPlayer_FeaturesMovementLimitationRotation {

    @Redirect(method = "getMaxHeadRotationRelativeToBody", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isBlocking()Z"))
    private boolean dontModifyHeadRotationWhenBlocking(Player instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_20_2) && instance.isBlocking();
    }

}
