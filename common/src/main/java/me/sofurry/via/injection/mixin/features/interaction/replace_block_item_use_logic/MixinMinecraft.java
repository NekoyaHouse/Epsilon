package me.sofurry.via.injection.mixin.features.interaction.replace_block_item_use_logic;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean allowBlockBreakAndItemUsageAtTheSameTime(boolean original) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_7_6) && original;
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean allowItemUsageAndBlockBreakAtTheSameTime(boolean original) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_7_6) && original;
    }

}
