package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.rewriter.ComponentRewriter1_21_5;
import com.viaversion.viaversion.util.TagUtil;
import me.sofurry.via.util.ItemUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ComponentRewriter1_21_5.class, remap = false)
public abstract class MixinComponentRewriter1_21_5 {

    @WrapOperation(method = "handleShowItem", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/util/TagUtil;removeNamespaced(Lcom/viaversion/nbt/tag/CompoundTag;Ljava/lang/String;)Z"))
    private boolean storeBackupTag(CompoundTag tag, String key, Operation<Boolean> original) {
        if (key.equals("hide_additional_tooltip")) {
            CompoundTag customData = TagUtil.getNamespacedCompoundTag(tag, "custom_data");
            if (customData == null) {
                tag.put("custom_data", customData = new CompoundTag());
            }

            final CompoundTag backupTag = new CompoundTag();
            backupTag.putBoolean("hide_additional_tooltip", true);
            customData.put(ItemUtil.vvNbtName(Protocol1_21_4To1_21_5.class, "backup"), backupTag);
        }

        return original.call(tag, key);
    }

}
