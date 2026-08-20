package me.sofurry.via.injection.mixin;

import me.sofurry.via.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ProfilePublicKey.Data.class)
public abstract class MixinProfilePublicKey_Data implements IProfilePublicKey_Data {

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
