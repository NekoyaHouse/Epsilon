package me.sofurry.via.injection.mixin.features.entity.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCow.class)
public abstract class MixinAbstractCow extends Animal {

    public MixinAbstractCow(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void disableMilkingInCreative(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2) && player.getAbilities().instabuild) {
            cir.setReturnValue(super.mobInteract(player, hand));
        }
    }

}
