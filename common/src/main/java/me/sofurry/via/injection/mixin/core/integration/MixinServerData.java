package me.sofurry.via.injection.mixin.core.integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public abstract class MixinServerData implements IServerData {

    @Shadow
    public String name;

    @Unique
    private ProtocolVersion viaVersion$forcedVersion = null;

    @Unique
    private boolean viaVersion$passedDirectConnectScreen;

    @Unique
    private ProtocolVersion viaVersion$translatingVersion;

    @Inject(method = "write", at = @At("TAIL"))
    private void saveForcedVersion(CallbackInfoReturnable<CompoundTag> cir, @Local(name = "tag") CompoundTag tag) {
        if (viaVersion$forcedVersion != null) {
            tag.putString("viafabricplus_forcedversion", viaVersion$forcedVersion.getName());
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private static void loadForcedVersion(CompoundTag tag, CallbackInfoReturnable<ServerData> cir, @Local(name = "server") ServerData server) {
        if (tag.contains("viafabricplus_forcedversion")) {
            final ProtocolVersion version = ProtocolTranslator.protocolVersionByName(tag.getStringOr("viafabricplus_forcedversion", null));
            if (version != null) {
                ((IServerData) server).viaVersion$forceVersion(version);
            }
        }
    }

    @Inject(method = "copyNameIconFrom", at = @At("RETURN"))
    private void syncForcedVersion(ServerData other, CallbackInfo ci) {
        viaVersion$forceVersion(((IServerData) other).viaVersion$forcedVersion());
    }

    @Override
    public ProtocolVersion viaVersion$forcedVersion() {
        return viaVersion$forcedVersion;
    }

    @Override
    public void viaVersion$forceVersion(ProtocolVersion version) {
        viaVersion$forcedVersion = version;
    }

    @Override
    public boolean viaVersion$passedDirectConnectScreen() {
        return viaVersion$passedDirectConnectScreen;
    }

    @Override
    public void viaVersion$passDirectConnectScreen(boolean state) {
        viaVersion$passedDirectConnectScreen = state;
    }

    @Override
    public ProtocolVersion viaVersion$translatingVersion() {
        return viaVersion$translatingVersion;
    }

    @Override
    public void viaVersion$setTranslatingVersion(ProtocolVersion version) {
        viaVersion$translatingVersion = version;
    }

}
