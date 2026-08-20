package me.sofurry.via.injection.mixin;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import me.sofurry.via.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = KeyPairResponse.class, remap = false)
public abstract class MixinKeyPairResponse implements IProfilePublicKey_Data {

    @Unique
    private byte[] viaVersion$legacyKeySignature;

    @Override
    public byte[] viafabricplus$getLegacyPublicKeySignature() {
        return this.viaVersion$legacyKeySignature;
    }

    @Override
    public void viafabricplus$setLegacyPublicKeySignature(byte[] signature) {
        this.viaVersion$legacyKeySignature = signature;
    }

}
