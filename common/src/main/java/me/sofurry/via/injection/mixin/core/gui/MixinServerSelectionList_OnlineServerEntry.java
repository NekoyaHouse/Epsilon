package me.sofurry.via.injection.mixin.core.gui;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.modules.impl.ClientSetting;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.sofurry.via.injection.access.core.IServerData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinServerSelectionList_OnlineServerEntry {

    @Shadow
    @Final
    private ServerData serverData;

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V"))
    private void drawTranslatingState(GuiGraphicsExtractor instance, Component component, int x, int y, Operation<Void> original) {
        final List<Component> tooltips = new ArrayList<>();
        tooltips.add(component);
        if (ClientSetting.INSTANCE.showAdvertisedServerVersion.getValue()) {
            tooltips.add(EpsilonTranslations.component(EpsilonTranslations.Via.BASE_TARGET_VERSION, ((IServerData) serverData).viaVersion$translatingVersion()));
            tooltips.add(EpsilonTranslations.component(EpsilonTranslations.Via.BASE_SERVER_VERSION, serverData.version.getString() + " (" + serverData.protocol + ")"));
        }
        instance.setTooltipForNextFrame(Lists.transform(tooltips, Component::getVisualOrderText), x, y);
    }

}
