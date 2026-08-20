package me.sofurry.via.injection.mixin.core.integration;

import com.github.epsilon.modules.impl.ClientSetting;
import net.raphimc.viabedrock.ViaBedrockConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = ViaBedrockConfig.class, remap = false)
public abstract class MixinViaBedrockConfig {

    /**
     * @author Florian Reuth (EnZaXD)
     * @reason Move setting into the settings GUI, changes default to true
     */
    @Overwrite
    public boolean shouldEnableExperimentalFeatures() {
        return ClientSetting.INSTANCE.experimentalFeatures.getValue();
    }

}
