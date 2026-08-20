package me.sofurry.via.injection.mixin.features.movement.elytra;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void replaceGlidingCondition(CallbackInfoReturnable<Boolean> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            if (!this.onGround() && this.getDeltaMovement().y < 0D && !this.isFallFlying()) {
                final ItemStack itemStack = this.getItemBySlot(EquipmentSlot.CHEST);
                if (itemStack.is(Items.ELYTRA) && canGlideUsing(itemStack, EquipmentSlot.CHEST)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(false);
        }
    }

}
