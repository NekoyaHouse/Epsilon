package me.sofurry.via.injection.mixin.features.networking.player_abilities;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerboundPlayerAbilitiesPacket.class)
public abstract class MixinServerboundPlayerAbilitiesPacket {

    @Unique
    private Abilities viaVersion$abilities;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Abilities;)V", at = @At("RETURN"))
    private void capturePlayerAbilities(Abilities abilities, CallbackInfo ci) {
        this.viaVersion$abilities = abilities;
    }

    @Group(name = "viaVersion$writeAbilityFlags", min = 1, max = 1)
    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lnet/minecraft/network/FriendlyByteBuf;"), require = 0)
    private FriendlyByteBuf implementFlags(FriendlyByteBuf instance, int value) {
        return instance.writeByte(this.viaVersion$addLegacyFlags(value));
    }

    @Group(name = "viaVersion$writeAbilityFlags")
    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(B)Lnet/minecraft/network/FriendlyByteBuf;"), require = 0)
    private FriendlyByteBuf implementNeoForgeFlags(FriendlyByteBuf instance, byte value) {
        return instance.writeByte(this.viaVersion$addLegacyFlags(value));
    }

    @Unique
    private int viaVersion$addLegacyFlags(int value) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            if (viaVersion$abilities.invulnerable) value |= 1;
            if (viaVersion$abilities.mayfly) value |= 4;
            if (viaVersion$abilities.instabuild) value |= 8;
        }

        return value;
    }

}
