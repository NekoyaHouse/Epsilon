package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemModelResolver.class)
public abstract class MixinItemRenderer {

    @Unique
    private static final Identifier viaVersionVisuals$missingIdentifier = Identifier.parse(String.valueOf(System.currentTimeMillis()));

    @Redirect(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object removeModel(ItemStack instance, DataComponentType<?> componentType) {
        if (ClientSetting.INSTANCE.replacePetrifiedOakSlab.isEnabled() && instance.is(Items.PETRIFIED_OAK_SLAB)) {
            return viaVersionVisuals$missingIdentifier;
        } else {
            return instance.get(componentType);
        }
    }

}
