package me.sofurry.via.injection.mixin;

import com.github.epsilon.Constants;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.ProfileKey;
import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_0;
import com.viaversion.viaversion.api.minecraft.signature.storage.ChatSession1_19_1;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.injection.access.networking.legacy_chat_signature.IProfilePublicKey_Data;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.security.PrivateKey;
import java.util.UUID;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class MixinConnectScreen_1_FeaturesNetworkingLegacyChatSignature {

    @Shadow
    @Final
    Minecraft val$minecraft;

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lio/netty/channel/ChannelFuture;syncUninterruptibly()Lio/netty/channel/ChannelFuture;", remap = false, shift = At.Shift.AFTER))
    private void setupChatSessions(CallbackInfo ci, @Local Connection clientConnection) {
        final UserConnection connection = ((IConnection) clientConnection).viaVersion$getUserConnection();

        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_19, ProtocolVersion.v1_19_1)) {
            final ProfileKeyPair keyPair = Minecraft.getInstance().getProfileKeyPairManager().prepareKeyPair().join().orElse(null);
            if (keyPair != null) {
                final ProfilePublicKey.Data publicKeyData = keyPair.publicKey().data();
                final PrivateKey privateKey = keyPair.privateKey();
                final long expiresAt = publicKeyData.expiresAt().toEpochMilli();
                final byte[] publicKey = publicKeyData.key().getEncoded();
                final UUID uuid = this.val$minecraft.getUser().getProfileId();

                connection.put(new ChatSession1_19_1(uuid, privateKey, new ProfileKey(expiresAt, publicKey, publicKeyData.keySignature())));
                if (ProtocolTranslator.getTargetVersion() == ProtocolVersion.v1_19) {
                    final byte[] legacyKeySignature = ((IProfilePublicKey_Data) (Object) publicKeyData).viafabricplus$getLegacyPublicKeySignature();
                    if (legacyKeySignature != null) {
                        connection.put(new ChatSession1_19_0(uuid, privateKey, new ProfileKey(expiresAt, publicKey, legacyKeySignature)));
                    }
                }
            } else {
                Constants.LOGGER.error("Could not get public key signature. Joining servers with enforce-secure-profiles enabled will not work!");
            }
        }
    }

}
