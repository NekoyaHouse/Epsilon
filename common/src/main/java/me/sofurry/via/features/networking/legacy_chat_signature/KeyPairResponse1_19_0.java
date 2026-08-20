package me.sofurry.via.features.networking.legacy_chat_signature;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;

import java.nio.ByteBuffer;

/**
 * This class is part of the AuthLib, we are overwriting this class to add the {@link #publicKeySignature} field.
 */

public record KeyPairResponse1_19_0(
        KeyPairResponse.KeyPair keyPair,
        ByteBuffer publicKeySignatureV2,
        ByteBuffer publicKeySignature /* removed in 1.20-rc1 */,
        String expiresAt,
        String refreshedAfter) {
}
