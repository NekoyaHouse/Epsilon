package me.sofurry.via.injection.mixin.features.item.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnEggItem.class)
public abstract class MixinSpawnEggItem {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void swingHand(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(InteractionResult.SUCCESS); // Skip type validation
        }
    }

    @Inject(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/SpawnEggItem;getType(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/entity/EntityType;"), cancellable = true)
    private void swingHand(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }

}
