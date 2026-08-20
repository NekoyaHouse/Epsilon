package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.ViaVersion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(GameModeSwitcherScreen.class)
public abstract class MixinGameModeSwitcherScreen_VisualsFilterGameModeSelections extends Screen {

    @Mutable
    @Shadow
    @Final
    private static int ALL_SLOTS_WIDTH;

    @Unique
    private GameModeSwitcherScreen.GameModeIcon[] viaVersionVisuals$unwrappedGameModes;

    public MixinGameModeSwitcherScreen_VisualsFilterGameModeSelections(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fixUIWidth(CallbackInfo ci) {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            final List<GameModeSwitcherScreen.GameModeIcon> selections = new ArrayList<>(Arrays.stream(GameModeSwitcherScreen.GameModeIcon.values()).toList());

            selections.remove(GameModeSwitcherScreen.GameModeIcon.SPECTATOR);
            if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
                selections.remove(GameModeSwitcherScreen.GameModeIcon.ADVENTURE);
            }

            viaVersionVisuals$unwrappedGameModes = selections.toArray(GameModeSwitcherScreen.GameModeIcon[]::new);
            ALL_SLOTS_WIDTH = viaVersionVisuals$unwrappedGameModes.length * 31 - 5;
        }
    }

    @Redirect(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;VALUES:[Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;", opcode = Opcodes.GETSTATIC))
    private GameModeSwitcherScreen.GameModeIcon[] removeNewerGameModes() {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            return viaVersionVisuals$unwrappedGameModes;
        } else {
            return GameModeSwitcherScreen.GameModeIcon.values();
        }
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void disableInClassic(CallbackInfo ci) {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30)) { // survival mode was added in a1.0.15
            this.onClose();
        }
    }

}
