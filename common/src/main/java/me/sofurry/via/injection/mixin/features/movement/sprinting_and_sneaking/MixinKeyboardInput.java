package me.sofurry.via.injection.mixin.features.movement.sprinting_and_sneaking;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec2;normalized()Lnet/minecraft/world/phys/Vec2;"))
    private Vec2 simplifyDiagonalMovementSpeedValues(Vec2 instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return instance;
        } else {
            return instance.normalized();
        }
    }

}
