package me.sofurry.via.injection.mixin.features.interaction.container_clicking;

import com.viaversion.viaversion.data.entity.EntityTrackerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityTrackerBase.class, remap = false)
public abstract class MixinEntityTrackerBase {

    /**
     * @author Florian Reuth (EnZaXD)
     * @reason Don't cancel serverbound set creative slot packets
     */
    @Overwrite
    public boolean canInstaBuild() {
        return true;
    }

}
