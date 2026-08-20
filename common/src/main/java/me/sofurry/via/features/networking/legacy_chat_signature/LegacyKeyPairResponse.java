package me.sofurry.via.features.networking.legacy_chat_signature;

import com.github.epsilon.Constants;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class LegacyKeyPairResponse {

    private static final Map<KeyPairResponse, byte[]> LEGACY_SIGNATURES = Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyKeyPairResponse() {
    }

    public static KeyPairResponse fetch(final UserApiService userApiService) {
        if (!(userApiService instanceof YggdrasilUserApiService yggdrasilService)) {
            return userApiService.getKeyPair();
        }

        try {
            final Field minecraftClientField = YggdrasilUserApiService.class.getDeclaredField("minecraftClient");
            final Field routeKeyPairField = YggdrasilUserApiService.class.getDeclaredField("routeKeyPair");
            minecraftClientField.setAccessible(true);
            routeKeyPairField.setAccessible(true);

            final MinecraftClient minecraftClient = (MinecraftClient) minecraftClientField.get(yggdrasilService);
            final URL routeKeyPair = (URL) routeKeyPairField.get(yggdrasilService);
            final KeyPairResponse1_19_0 response = minecraftClient.post(routeKeyPair, KeyPairResponse1_19_0.class);
            if (response == null) {
                return null;
            }

            final KeyPairResponse keyPairResponse = new KeyPairResponse(
                    response.keyPair(),
                    response.publicKeySignatureV2(),
                    response.expiresAt(),
                    response.refreshedAfter()
            );
            final ByteBuffer legacySignature = response.publicKeySignature();
            if (legacySignature != null && legacySignature.hasRemaining()) {
                final ByteBuffer signatureCopy = legacySignature.duplicate();
                final byte[] signatureBytes = new byte[signatureCopy.remaining()];
                signatureCopy.get(signatureBytes);
                LEGACY_SIGNATURES.put(keyPairResponse, signatureBytes);
            } else {
                Constants.LOGGER.error("Could not get legacy public key signature. Minecraft 1.19.0 servers with secure profiles enabled will not work!");
            }
            return keyPairResponse;
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            Constants.LOGGER.error("Could not access Authlib's legacy key-pair endpoint; falling back to the modern response", exception);
            return userApiService.getKeyPair();
        }
    }

    public static byte[] legacySignature(final KeyPairResponse response) {
        return LEGACY_SIGNATURES.get(response);
    }

}
