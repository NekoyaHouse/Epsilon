package me.sofurry.via.injection.mixin.features.movement.collision;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBoat.class)
public abstract class MixinAbstractBoat {

    @Redirect(method = "floatBoat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean alwaysUpdatePosition(Level instance, Entity entity, AABB box) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            return true;
        } else {
            return instance.noCollision(entity, box);
        }
    }

}
