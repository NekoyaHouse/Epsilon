package me.sofurry.via.visuals.injection.mixin.player_rotations;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public float yBodyRot;

    public MixinLivingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Redirect(method = "tickHeadTurn", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
    private float changeBodyRotationInterpolation(float g) {
        if (ClientSetting.INSTANCE.changeBodyRotationInterpolation.isEnabled()) {
            g = Mth.clamp(g, -75.0F, 75.0F);
            this.yBodyRot = this.getYRot() - g;
            if (Math.abs(g) > 50.0F) {
                this.yBodyRot += g * 0.2F;
            }
            return Float.MIN_VALUE; // Causes the if to always fail
        } else {
            return Math.abs(g);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;abs(F)F"))
    private float alwaysRotateWhenWalkingBackwards(float v) {
        if (ClientSetting.INSTANCE.sidewaysBackwardsRunning.isEnabled()) {
            return 0F;
        } else {
            return Mth.abs(v);
        }
    }

}
