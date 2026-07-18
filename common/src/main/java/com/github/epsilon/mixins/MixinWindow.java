package com.github.epsilon.mixins;

import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.modules.impl.ClientSetting;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Mixin(Window.class)
public class MixinWindow {

    @Redirect(method = "setIcon", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/IconSet;getStandardIcons(Lnet/minecraft/server/packs/PackResources;)Ljava/util/List;"))
    private List<IoSupplier<InputStream>> onSetIcon(IconSet instance, PackResources resources) throws IOException {
        final InputStream epsilon_16x16 = EpsilonCommon.class.getResourceAsStream("/assets/epsilon/textures/icons/icon_16x16.png");
        final InputStream epsilon_32x32 = EpsilonCommon.class.getResourceAsStream("/assets/epsilon/textures/icons/icon_32x32.png");
        final InputStream table_16x16 = EpsilonCommon.class.getResourceAsStream("/assets/epsilon/textures/icons/table_16x16.png");
        final InputStream table_32x32 = EpsilonCommon.class.getResourceAsStream("/assets/epsilon/textures/icons/table_32x32.png");

        if (ClientSetting.INSTANCE.customIcon.is(ClientSetting.IconMode.Epsilon)) {
            if (epsilon_16x16 != null && epsilon_32x32 != null) {
                return List.of(() -> epsilon_16x16, () -> epsilon_32x32);
            }
        } else if (ClientSetting.INSTANCE.customIcon.is(ClientSetting.IconMode.Minecraft_1_8_9)) {
            if (table_16x16 != null && table_32x32 != null) {
                return List.of(() -> table_16x16, () -> table_32x32);
            }
        }

        return instance.getStandardIcons(resources);
    }

}
