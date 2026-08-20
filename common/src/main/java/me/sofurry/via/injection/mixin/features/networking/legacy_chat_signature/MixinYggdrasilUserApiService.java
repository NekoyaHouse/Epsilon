package me.sofurry.via.injection.mixin.features.networking.legacy_chat_signature;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.features.networking.legacy_chat_signature.KeyPairResponse1_19_0;
import me.sofurry.via.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.URL;

@Mixin(value = YggdrasilUserApiService.class, remap = false)
public abstract class MixinYggdrasilUserApiService {

    @Shadow
    @Final
    private MinecraftClient minecraftClient;

    @Shadow
    @Final
    private URL routeKeyPair;

    /**
     * @author Florian Reuth (EnZaXD)
     * @reason Fetch the legacy public key signature for 1.19.0 clients.
     */
    @Overwrite
    public KeyPairResponse getKeyPair() {
        final KeyPairResponse1_19_0 response = minecraftClient.post(routeKeyPair, KeyPairResponse1_19_0.class);

        // the response can't be null for us since we are constructing a new object with it.
        if (response == null) {
            return null;
        }

        // create the original KeyPairResponse object with the data
        final KeyPairResponse keyPairResponse = new KeyPairResponse(
                response.keyPair(),
                response.publicKeySignatureV2(),
                response.expiresAt(),
                response.refreshedAfter()
        );

        if (response.publicKeySignature() != null && response.publicKeySignature().array().length != 0) {
            ((IProfilePublicKey_Data) (Object) keyPairResponse).viafabricplus$setLegacyPublicKeySignature(response.publicKeySignature().array());
        } else {
            ViaVersionImpl.INSTANCE.getLogger().error("Could not get legacy public key signature. 1.19.0 with secure-profiles enabled will not work!");
        }

        return keyPairResponse;
    }

}
