package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.player.LocalPlayer;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer_FeaturesBedrockMovement {

    @Shadow
    protected abstract boolean isSprintingPossible(final boolean allowedInShallowWater);

    @Redirect(method = {"shouldStopRunSprinting", "canStartSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprintingPossible(Z)Z"))
    private boolean allowNonSwimWaterSprinting(LocalPlayer instance, boolean allowedInShallowWater) {
        return this.isSprintingPossible(allowedInShallowWater || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) && (instance.isSwimming() || instance.onGround()));
    }

}
