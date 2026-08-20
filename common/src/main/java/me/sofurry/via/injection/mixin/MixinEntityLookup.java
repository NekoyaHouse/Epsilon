package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(EntityLookup.class)
public abstract class MixinEntityLookup<T extends EntityAccess> {

    @Shadow
    @Final
    private Int2ObjectMap<T> byId;

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z", remap = false))
    private boolean allowDuplicateUuid(Map<UUID, T> instance, Object o) {
        return instance.containsKey(o) && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_16_4);
    }

    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void returnRealSize(CallbackInfoReturnable<Integer> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
            cir.setReturnValue(this.byId.size());
        }
    }

}
