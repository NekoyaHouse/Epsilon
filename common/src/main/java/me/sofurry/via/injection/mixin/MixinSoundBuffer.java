package me.sofurry.via.injection.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.viaversion.viaaprilfools.api.AprilFoolsProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

@Mixin(SoundBuffer.class)
public abstract class MixinSoundBuffer {

    @Shadow
    @Final
    private AudioFormat format;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void modifyBuffer(ByteBuffer data, AudioFormat format, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().equals(AprilFoolsProtocolVersion.s3d_shareware)) {
            this.viaVersion$apply8BitSound(data);
        }
    }

    @Unique
    private void viaVersion$apply8BitSound(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return;
        }
        if (this.format.getChannels() == 1) {
            this.viaVersion$apply8BitMono(byteBuffer);
        } else {
            this.viaVersion$apply8BitStereo(byteBuffer);
        }
    }

    @Unique
    private void viaVersion$apply8BitMono(final ByteBuffer byteBuffer) {
        short short2 = 0;
        int integer3 = 0;
        while (byteBuffer.hasRemaining()) {
            if (integer3 == 0) {
                byteBuffer.mark();
                short2 = (short) (byteBuffer.getShort() & 0xFFFFFFFC);
                byteBuffer.reset();
                integer3 = 15;
            } else {
                --integer3;
            }
            byteBuffer.putShort(short2);
        }
        byteBuffer.flip();
    }

    @Unique
    private void viaVersion$apply8BitStereo(final ByteBuffer byteBuffer) {
        short short2 = 0;
        short short3 = 0;
        int integer4 = 0;
        while (byteBuffer.hasRemaining()) {
            if (integer4 == 0) {
                byteBuffer.mark();
                short2 = (short) (byteBuffer.getShort() & 0xFFFFFFFC);
                short3 = (short) (byteBuffer.getShort() & 0xFFFFFFFC);
                byteBuffer.reset();
                integer4 = 15;
            } else {
                --integer4;
            }
            byteBuffer.putShort(short2);
            byteBuffer.putShort(short3);
        }
        byteBuffer.flip();
    }

}
