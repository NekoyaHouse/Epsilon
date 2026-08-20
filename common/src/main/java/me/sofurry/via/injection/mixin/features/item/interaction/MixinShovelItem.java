package me.sofurry.via.injection.mixin.features.item.interaction;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Map;

@Mixin(ShovelItem.class)
public abstract class MixinShovelItem {

    @Group(name = "disablePathAction", min = 1, max = 1)
    @Redirect(method = "useOn", slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/item/ShovelItem;FLATTENABLES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC)), at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0, remap = false), require = 0)
    private Object disablePathAction(Map<Object, Object> instance, Object grassBlock) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return null;
        } else {
            return instance.get(grassBlock);
        }
    }

    @Group(name = "disablePathAction", min = 1, max = 1)
    @ModifyExpressionValue(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getToolModifiedState(Lnet/minecraft/world/item/context/UseOnContext;Lnet/neoforged/neoforge/common/ItemAbility;Z)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0), require = 0)
    private BlockState disableNeoForgePathAction(BlockState state) {
        return ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8) ? null : state;
    }

}
