package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemFrame.class)
public abstract class MixinItemFrame {

    @Redirect(method = "calculateBoundingBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/decoration/ItemFrame;hasFramedMap()Z"))
    private boolean changeBoundingBox(ItemFrame instance) {
        return instance.hasFramedMap() && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_7);
    }

}
