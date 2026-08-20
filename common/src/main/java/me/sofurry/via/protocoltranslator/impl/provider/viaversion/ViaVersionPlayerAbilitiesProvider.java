package me.sofurry.via.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import net.minecraft.client.Minecraft;

public final class ViaVersionPlayerAbilitiesProvider extends PlayerAbilitiesProvider {

    @Override
    public float getFlyingSpeed(UserConnection connection) {
        return Minecraft.getInstance().player.getAbilities().getFlyingSpeed();
    }

    @Override
    public float getWalkingSpeed(UserConnection connection) {
        return Minecraft.getInstance().player.getAbilities().getWalkingSpeed();
    }

}
