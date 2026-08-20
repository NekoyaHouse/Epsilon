package me.sofurry.via.injection.mixin.features.scoreboard;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public abstract class MixinPlayerTeam {

    @Shadow
    private Component playerPrefix;

    @Shadow
    private Component playerSuffix;

    @Inject(method = "getFormattedName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true)
    private void decorateName1_12_2(Component teamMemberName, CallbackInfoReturnable<MutableComponent> cir) {
        // All components were legacy strings prior to 1.13, meaning their styles are not separated but used across the whole component.
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            final Style prefixStyle = viaVersion$getLastStyle(this.playerPrefix);
            final Component nameWithStyle = viaVersion$fillStyle(teamMemberName, prefixStyle);
            final Style nameStyle = viaVersion$getLastStyle(nameWithStyle);

            cir.setReturnValue(Component.empty()
                    .append(this.playerPrefix)
                    .append(nameWithStyle)
                    .append(viaVersion$fillStyle(this.playerSuffix, nameStyle))
            );
        }
    }

    @Unique
    private Style viaVersion$getLastStyle(final Component text) {
        for (int i = text.getSiblings().size() - 1; i >= 0; i--) {
            final Component sibling = text.getSiblings().get(i);
            if (sibling.getStyle() != Style.EMPTY) {
                return sibling.getStyle();
            }
        }

        return text.getStyle();
    }

    @Unique
    private Component viaVersion$fillStyle(final Component text, final Style style) {
        if (text.getStyle() != Style.EMPTY) {
            return text;
        } else {
            return text.copy().withStyle(style);
        }
    }

}
