package me.sofurry.via.injection.mixin.features.bedrock.chat;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

@Mixin(ClientSuggestionProvider.class)
public abstract class MixinClientSuggestionProvider {

    @Shadow
    @Final
    private Set<String> customCompletionSuggestions;

    @Inject(method = {"getOnlinePlayerNames", "getCustomTabSuggestions"}, at = @At("HEAD"), cancellable = true)
    private void returnChatSuggestions(CallbackInfoReturnable<Collection<String>> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(this.customCompletionSuggestions);
        }
    }

}
