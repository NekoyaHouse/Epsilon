package me.sofurry.via.injection.mixin.features.networking.legacy_chat_signature;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import me.sofurry.via.features.networking.legacy_chat_signature.LegacyKeyPairResponse;
import me.sofurry.via.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AccountProfileKeyPairManager.class)
public abstract class MixinAccountProfileKeyPairManager {

    @Redirect(method = "fetchProfileKeyPair", at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/minecraft/UserApiService;getKeyPair()Lcom/mojang/authlib/yggdrasil/response/KeyPairResponse;", remap = false))
    private KeyPairResponse fetchLegacyKeyPair(final UserApiService userApiService) {
        return LegacyKeyPairResponse.fetch(userApiService);
    }

    @Inject(method = "parsePublicKey", at = @At("RETURN"))
    private static void trackLegacyKey(KeyPairResponse response, CallbackInfoReturnable<ProfilePublicKey.Data> cir) {
        final byte[] legacySignature = LegacyKeyPairResponse.legacySignature(response);
        if (legacySignature != null) {
            ((IProfilePublicKey_Data) (Object) cir.getReturnValue()).viafabricplus$setLegacyPublicKeySignature(legacySignature);
        }
    }

}
