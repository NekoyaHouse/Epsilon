package me.sofurry.via.injection.mixin;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.screen.impl.PerServerVersionScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
public abstract class MixinManageServerScreen extends Screen {

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    private EditBox nameEdit;

    @Shadow
    private EditBox ipEdit;

    @Unique
    private String viaVersion$nameField;

    @Unique
    private String viaVersion$addressField;

    public MixinManageServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addVersionSetterButton(CallbackInfo ci) {
        final int buttonPosition = ClientSetting.INSTANCE.addServerScreenButtonOrientation.getModeIndex();
        if (buttonPosition == 0) { // Off
            return;
        }

        final IServerData mixinServerInfo = (IServerData) serverData;
        final ProtocolVersion forcedVersion = mixinServerInfo.viaVersion$forcedVersion();

        // Restore input if the user cancels the version selection screen (or if the user is editing an existing server)
        if (viaVersion$nameField != null && viaVersion$addressField != null) {
            this.nameEdit.setValue(viaVersion$nameField);
            this.ipEdit.setValue(viaVersion$addressField);

            viaVersion$nameField = null;
            viaVersion$addressField = null;
        }

        final Button.Builder buttonBuilder = Button.builder(forcedVersion == null
                ? EpsilonTranslations.component(EpsilonTranslations.Via.BASE_SET_VERSION)
                : Component.nullToEmpty(forcedVersion.getName()), _ -> {
            // Store current input in case the user cancels the version selection
            viaVersion$nameField = nameEdit.getValue();
            viaVersion$addressField = ipEdit.getValue();

            minecraft.gui.setScreen(new PerServerVersionScreen(this, mixinServerInfo::viaVersion$forceVersion, mixinServerInfo::viaVersion$forcedVersion));
        }).size(98, 20);
        ClientSetting.setOrientation(buttonBuilder::pos, buttonPosition, width, height);
        this.addRenderableWidget(buttonBuilder.build());
    }

}
