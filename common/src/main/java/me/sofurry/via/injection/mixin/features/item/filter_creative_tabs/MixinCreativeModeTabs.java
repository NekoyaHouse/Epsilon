package me.sofurry.via.injection.mixin.features.item.filter_creative_tabs;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTabs.class)
public abstract class MixinCreativeModeTabs {

    @Shadow
    private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;

    @Shadow
    private static void buildAllTabContents(CreativeModeTab.ItemDisplayParameters displayContext) {
    }

    @Unique
    private static ProtocolVersion viaVersion$version;

    @Unique
    private static int viaVersion$state;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"), cancellable = true)
    private static void trackLastVersion(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider lookup, CallbackInfoReturnable<Boolean> cir) {
        if (viaVersion$version != ProtocolTranslator.getTargetVersion() || viaVersion$state != ClientSetting.INSTANCE.removeNotAvailableItemsFromCreativeTab.getModeIndex()) {
            viaVersion$version = ProtocolTranslator.getTargetVersion();
            viaVersion$state = ClientSetting.INSTANCE.removeNotAvailableItemsFromCreativeTab.getModeIndex();

            CACHED_PARAMETERS = new CreativeModeTab.ItemDisplayParameters(enabledFeatures, hasPermissions, lookup);
            buildAllTabContents(CACHED_PARAMETERS);

            cir.setReturnValue(true);
        }
    }

}
