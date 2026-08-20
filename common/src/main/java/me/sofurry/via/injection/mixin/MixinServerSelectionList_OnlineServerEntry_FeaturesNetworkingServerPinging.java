package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinServerSelectionList_OnlineServerEntry_FeaturesNetworkingServerPinging {

    @Shadow
    @Final
    private ServerData serverData;

    @Mutable
    @Shadow
    @Final
    private FaviconTexture icon;

    @Unique
    private boolean viaVersion$disableServerPinging;

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ThreadPoolExecutor;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"))
    private Future<?> disableServerPinging(ThreadPoolExecutor instance, Runnable runnable, Operation<Future<?>> original) {
        ProtocolVersion version = ((IServerData) serverData).viaVersion$forcedVersion();
        if (version == null) {
            version = ProtocolTranslator.getTargetVersion();
        }

        viaVersion$disableServerPinging = ClientSetting.INSTANCE.disableServerPinging.isEnabled(version);
        if (viaVersion$disableServerPinging) {
            this.serverData.version = Component.nullToEmpty(version.getName()); // Show target version
            return null;
        }
        return original.call(instance, runnable);
    }

    @Redirect(method = "extractContent", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ServerData$State;INCOMPATIBLE:Lnet/minecraft/client/multiplayer/ServerData$State;", opcode = Opcodes.GETSTATIC))
    private ServerData.State disableServerPinging() {
        if (viaVersion$disableServerPinging) {
            return this.serverData.state(); // server version will always be shown (as we don't have a player count anyway)
        } else {
            return ServerData.State.INCOMPATIBLE;
        }
    }

    @Redirect(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
    private List<FormattedCharSequence> disableServerPinging(Font instance, FormattedText input, int maxWidth) {
        if (viaVersion$disableServerPinging) { // server label will just show the server address
            return instance.split(Component.nullToEmpty(serverData.ip), maxWidth);
        } else {
            return instance.split(input, maxWidth);
        }
    }

    @ModifyArg(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2)
    private int disableServerPinging(int x) {
        if (viaVersion$disableServerPinging) { // Move server label to the right (as we remove the ping bar)
            x += 15 /* ping bar width */ - 3 /* magical offset */;
        }
        return x;
    }

    @WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0))
    private boolean disableServerPinging(GuiGraphicsExtractor instance, RenderPipeline renderPipeline, Identifier location, int x, int y, int width, int height) {
        return !viaVersion$disableServerPinging; // Remove ping bar
    }

    @WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Ljava/util/List;II)V"))
    private boolean disableServerPinging(GuiGraphicsExtractor instance, List<FormattedCharSequence> formattedCharSequences, int x, int y) {
        return !viaVersion$disableServerPinging; // Remove player list tooltip
    }

    @Redirect(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/FaviconTexture;textureLocation()Lnet/minecraft/resources/Identifier;"))
    private Identifier disableServerPinging(FaviconTexture instance) {
        if (viaVersion$disableServerPinging) { // Remove server icon
            return FaviconTexture.MISSING_LOCATION;
        } else {
            return this.icon.textureLocation();
        }
    }

    @WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V"))
    private boolean disableServerPinging(GuiGraphicsExtractor instance, Component component, int x, int y) {
        return !viaVersion$disableServerPinging; // Remove ping bar tooltip
    }

}
