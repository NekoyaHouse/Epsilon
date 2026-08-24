package me.sofurry.via.visuals.features.force_unicode_font;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.ViaVersion;
import me.sofurry.via.util.ViaVersionClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

/**
 * Older versions only had unicode font support for some languages and therefore servers are expecting the client
 * to use a unicode font, not using it on older versions can cause issues with wrong dimensions in chat components.
 */
public class UnicodeFontFix1_12_2 {

    private static boolean enabled = false;
    private static Runnable task = null;

    public static void init() {
        ViaVersion.getImpl().registerOnChangeProtocolVersionCallback((oldVersion, newVersion) -> {
            updateUnicodeFontOverride(newVersion);
        });

        ViaVersionClientEvents.START_LEVEL_TICK.register(world -> {
            // Prevent usages of RenderSystem.recordRenderCall()
            if (task != null) {
                task.run();
                task = null;
            }
        });
    }

    public static void updateUnicodeFontOverride(final ProtocolVersion version) {
        final OptionInstance<Boolean> option = Minecraft.getInstance().options.forceUnicodeFont();

        if (ClientSetting.INSTANCE.forceUnicodeFontForNonAsciiLanguages.isEnabled(version)) {
            if (Language.getInstance() instanceof ClientLanguage storage) {
                enabled = LanguageUtil.isUnicodeFont1_12_2(storage.storage);
                task = () -> option.set(enabled);
            }
        } else if (enabled) {
            enabled = false;
            task = () -> option.set(false);
        }
    }

}
