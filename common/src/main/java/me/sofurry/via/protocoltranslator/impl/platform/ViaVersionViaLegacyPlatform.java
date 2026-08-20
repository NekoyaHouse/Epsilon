package me.sofurry.via.protocoltranslator.impl.platform;

import com.viaversion.viaversion.api.Via;
import net.raphimc.vialegacy.ViaLegacyPlatformImpl;

public final class ViaVersionViaLegacyPlatform extends ViaLegacyPlatformImpl {

    @Override
    public String getCpeAppName() {
        return Via.getPlatform().getPlatformName() + " " + Via.getPlatform().getPlatformVersion();
    }

}
