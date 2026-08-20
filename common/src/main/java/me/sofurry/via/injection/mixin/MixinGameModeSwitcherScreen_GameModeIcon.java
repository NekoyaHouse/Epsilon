package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.ViaVersion;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(GameModeSwitcherScreen.GameModeIcon.class)
public abstract class MixinGameModeSwitcherScreen_GameModeIcon {

    @Shadow
    @Final
    public static GameModeSwitcherScreen.GameModeIcon SURVIVAL;

    @Shadow
    @Final
    public static GameModeSwitcherScreen.GameModeIcon CREATIVE;

    @Inject(method = "getNext", at = @At("HEAD"), cancellable = true)
    private void unwrapGameModes(CallbackInfoReturnable<GameModeSwitcherScreen.GameModeIcon> cir) {
        if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            final GameModeSwitcherScreen.GameModeIcon icon = (GameModeSwitcherScreen.GameModeIcon) (Object) this;
            if (icon == CREATIVE) {
                cir.setReturnValue(SURVIVAL);
            } else if (icon == SURVIVAL) {
                if (ViaVersion.getImpl().getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
                    cir.setReturnValue(CREATIVE);
                } else {
                    cir.setReturnValue(GameModeSwitcherScreen.GameModeIcon.ADVENTURE);
                }
            } else if (icon == GameModeSwitcherScreen.GameModeIcon.ADVENTURE) {
                cir.setReturnValue(CREATIVE);
            }
        }
    }

}
