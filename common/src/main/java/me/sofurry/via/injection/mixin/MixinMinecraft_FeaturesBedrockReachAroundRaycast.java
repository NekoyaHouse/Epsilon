package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft_FeaturesBedrockReachAroundRaycast {

    @Shadow
    public abstract Entity getCameraEntity();

    @ModifyExpressionValue(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult bedrockReachAroundRaycast(HitResult hitResult) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            final Entity entity = this.getCameraEntity();
            if (hitResult.getType() != HitResult.Type.MISS) return hitResult;
            if (!this.viaVersion$canReachAround(entity)) return hitResult;

            final int x = Mth.floor(entity.getX());
            final int y = Mth.floor(entity.getY() - 0.2F);
            final int z = Mth.floor(entity.getZ());
            final BlockPos floorPos = new BlockPos(x, y, z);

            return new BlockHitResult(Vec3.atCenterOf(floorPos), entity.getDirection(), floorPos, false);
        }

        return hitResult;
    }

    @Unique
    private boolean viaVersion$canReachAround(final Entity entity) {
        return entity.onGround() && entity.getVehicle() == null && entity.getXRot() >= 45;
    }

}
