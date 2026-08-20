package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * https://github.com/blackd/Inventory-Profiles/tree/all-in-one is handling the offhand slot even when
 * ViaVersion removes the slot in <= 1.8.9, so we have to cancel the handling of the offhand slot
 *
 * Fixes https://github.com/ViaVersion/ViaFabricPlus/issues/209
 */
@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.AutoRefillHandler$ItemSlotMonitor", remap = false)
public abstract class MixinAutoRefillHandler_ItemSlotMonitor {

    @Shadow
    public int currentSlotId;

    @Inject(method = {"checkHandle", "checkShouldHandle"}, at = @At("HEAD"), cancellable = true)
    public void dontHandleOffhandSlot(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            if (currentSlotId == 45) ci.cancel();
        }
    }

    @Inject(method = "updateCurrent", at = @At(value = "FIELD", target = "Lorg/anti_ad/mc/ipnext/event/AutoRefillHandler$ItemSlotMonitor;currentSlotId:I", shift = At.Shift.AFTER), cancellable = true)
    public void dontUpdateCurrentOffhandSlot(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            if (currentSlotId == 45) ci.cancel();
        }
    }

}
