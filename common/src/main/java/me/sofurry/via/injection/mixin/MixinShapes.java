package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class MixinShapes {

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private static void calculateMaxOffset1_12_2(Direction.Axis axis, AABB moving, Iterable<VoxelShape> shapes, double maxDist, CallbackInfoReturnable<Double> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            for (final VoxelShape shape : shapes) {
                for (final AABB shapeBox : shape.toAabbs()) {
                    if (axis == Direction.Axis.X) {
                        maxDist = viaVersion$intersectX(moving, shapeBox, maxDist);
                    } else if (axis == Direction.Axis.Y) {
                        maxDist = viaVersion$intersectY(moving, shapeBox, maxDist);
                    } else {
                        maxDist = viaVersion$intersectZ(moving, shapeBox, maxDist);
                    }
                }
            }
            cir.setReturnValue(maxDist);
        }
    }

    @Unique
    private static double viaVersion$intersectX(final AABB box, final AABB shapeBox, double maxDist) {
        if (box.maxY <= shapeBox.minY || box.minY >= shapeBox.maxY || box.maxZ <= shapeBox.minZ || box.minZ >= shapeBox.maxZ) {
            return maxDist;
        }

        double e;
        if (maxDist > 0.0 && box.maxX <= shapeBox.minX) {
            double d = shapeBox.minX - box.maxX;
            if (d < maxDist) {
                maxDist = d;
            }
        } else if (maxDist < 0.0 && box.minX >= shapeBox.maxX && (e = shapeBox.maxX - box.minX) > maxDist) {
            maxDist = e;
        }
        return maxDist;
    }

    @Unique
    private static double viaVersion$intersectY(final AABB playerBox, final AABB shapeBox, double maxDist) {
        if (playerBox.maxX <= shapeBox.minX || playerBox.minX >= shapeBox.maxX || playerBox.maxZ <= shapeBox.minZ || playerBox.minZ >= shapeBox.maxZ) {
            return maxDist;
        }

        double e;
        if (maxDist > 0.0 && playerBox.maxY <= shapeBox.minY) {
            double d = shapeBox.minY - playerBox.maxY;
            if (d < maxDist) {
                maxDist = d;
            }
        } else if (maxDist < 0.0 && playerBox.minY >= shapeBox.maxY && (e = shapeBox.maxY - playerBox.minY) > maxDist) {
            maxDist = e;
        }
        return maxDist;
    }

    @Unique
    private static double viaVersion$intersectZ(final AABB playerBox, final AABB shapeBox, double maxDist) {
        if (playerBox.maxX <= shapeBox.minX || playerBox.minX >= shapeBox.maxX || playerBox.maxY <= shapeBox.minY || playerBox.minY >= shapeBox.maxY) {
            return maxDist;
        }

        double e;
        if (maxDist > 0.0 && playerBox.maxZ <= shapeBox.minZ) {
            double d = shapeBox.minZ - playerBox.maxZ;
            if (d < maxDist) {
                maxDist = d;
            }
        } else if (maxDist < 0.0 && playerBox.minZ >= shapeBox.maxZ && (e = shapeBox.maxZ - playerBox.minZ) > maxDist) {
            maxDist = e;
        }
        return maxDist;
    }

}
