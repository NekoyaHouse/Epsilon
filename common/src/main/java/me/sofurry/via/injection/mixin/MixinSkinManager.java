package me.sofurry.via.injection.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(SkinManager.class)
public abstract class MixinSkinManager {

    @Redirect(method = "createLookup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/SkinManager;get(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Optional<PlayerSkin>> fetchGameProfileProperties(SkinManager instance, GameProfile profile) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20) && !profile.properties().containsKey("textures")) {
            return CompletableFuture.supplyAsync(() -> {
                final ProfileResult profileResult = Minecraft.getInstance().services().sessionService().fetchProfile(profile.id(), true);
                return profileResult == null ? profile : profileResult.profile();
            }, Util.backgroundExecutor()).thenCompose(instance::get);
        } else {
            return instance.get(profile);
        }
    }

}
