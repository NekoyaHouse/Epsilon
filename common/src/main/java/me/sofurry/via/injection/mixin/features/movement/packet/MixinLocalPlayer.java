package me.sofurry.via.injection.mixin.features.movement.packet;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    public ClientInput input;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    private boolean lastOnGround;

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @ModifyExpressionValue(method = "sendPosition", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;positionReminder:I", ordinal = 2))
    private int moveLastPosPacketIncrement(int original) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return original - 1; // Reverting original operation
        } else {
            return original;
        }
    }

    @Redirect(method = "sendPosition", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;lastOnGround:Z", ordinal = 0, opcode = Opcodes.GETFIELD))
    private boolean sendIdlePacket(LocalPlayer instance) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(LegacyProtocolVersion.r1_4_2, ProtocolVersion.v1_8) || ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
            return !onGround();
        } else {
            return this.lastOnGround;
        }
    }

}
