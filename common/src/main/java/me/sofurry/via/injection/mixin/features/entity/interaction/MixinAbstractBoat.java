package me.sofurry.via.injection.mixin.features.entity.interaction;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBoat.class)
public abstract class MixinAbstractBoat extends VehicleEntity {

    public MixinAbstractBoat(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(method = "interact", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/VehicleEntity;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult makeNotLeashable(VehicleEntity instance, Player player, InteractionHand interactionHand, Vec3 vec3) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            return InteractionResult.PASS;
        } else {
            return super.interact(player, interactionHand, vec3);
        }
    }

}
