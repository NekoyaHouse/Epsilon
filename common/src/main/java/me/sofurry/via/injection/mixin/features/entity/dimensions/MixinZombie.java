package me.sofurry.via.injection.mixin.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Zombie.class)
public abstract class MixinZombie extends Monster {

    @Shadow
    @Final
    private static EntityDimensions BABY_DIMENSIONS;

    @Unique
    private static final EntityDimensions viaVersion$baby_dimensions_r26_1 = EntityDimensions.scalable(0.49F, 0.99F)
            .withEyeHeight(0.775F)
            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, 0.0F, 0.1875F, 0.0F));

    @Unique
    private static final EntityDimensions viaVersion$baby_dimensions_r1_21_11 = EntityTypes.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);

    public MixinZombie(final EntityType<? extends Monster> type, final Level level) {
        super(type, level);
    }

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;BABY_DIMENSIONS:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETSTATIC))
    private EntityDimensions changeBabyDimensions() {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return viaVersion$baby_dimensions_r1_21_11;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return viaVersion$baby_dimensions_r26_1;
        } else {
            return BABY_DIMENSIONS;
        }
    }

}
