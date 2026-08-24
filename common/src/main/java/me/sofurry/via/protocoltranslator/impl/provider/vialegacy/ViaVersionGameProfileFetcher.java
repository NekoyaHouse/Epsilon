package me.sofurry.via.protocoltranslator.impl.provider.vialegacy;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.response.NameAndId;
import com.viaversion.viaversion.api.minecraft.GameProfile;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;

import java.net.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ViaVersionGameProfileFetcher extends GameProfileFetcher {

    private static final HttpAuthenticationService AUTHENTICATION_SERVICE = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
    private static final MinecraftSessionService SESSION_SERVICE = AUTHENTICATION_SERVICE.createMinecraftSessionService();
    private static final GameProfileRepository GAME_PROFILE_REPOSITORY = AUTHENTICATION_SERVICE.createProfileRepository();

    @Override
    public UUID loadMojangUuid(final String playerName) {
        final Optional<NameAndId> nameAndId = GAME_PROFILE_REPOSITORY.findProfileByName(playerName);
        if (nameAndId.isEmpty()) {
            throw new ProfileNotFoundException();
        }

        return nameAndId.get().id();
    }

    @Override
    public GameProfile loadGameProfile(final UUID uuid) {
        final ProfileResult result = SESSION_SERVICE.fetchProfile(uuid, true);
        if (result == null) {
            throw new ProfileNotFoundException();
        }

        final com.mojang.authlib.GameProfile gameProfile = result.profile();
        final GameProfile.Property[] properties = new GameProfile.Property[gameProfile.properties().size()];
        int i = 0;
        for (final Map.Entry<String, Property> entry : gameProfile.properties().entries()) {
            properties[i++] = new GameProfile.Property(entry.getValue().name(), entry.getValue().value(), entry.getValue().signature());
        }
        return new GameProfile(gameProfile.name(), gameProfile.id(), properties);
    }

}
