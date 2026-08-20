package me.sofurry.via.visuals;

import me.sofurry.via.api.ViaVersionBase;
import me.sofurry.via.api.entrypoint.ViaVersionLoadEntrypoint;
import me.sofurry.via.visuals.features.classic.creative_menu.GridItemSelectionScreen;
import me.sofurry.via.visuals.features.force_unicode_font.UnicodeFontFix1_12_2;
import net.minecraft.client.Minecraft;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public class ViaVersionVisuals implements ViaVersionLoadEntrypoint {

    public static final ViaVersionVisuals INSTANCE = new ViaVersionVisuals();

    @Override
    public void onPlatformLoad(ViaVersionBase platform) {
        UnicodeFontFix1_12_2.init();

        platform.registerOnChangeProtocolVersionCallback((oldVersion, newVersion) -> Minecraft.getInstance().execute(() -> {
            if (newVersion.olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30)) {
                GridItemSelectionScreen.INSTANCE.itemGrid = null;
            }
        }));
    }

}
