package me.sofurry.via.injection.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(Font.class)
public abstract class MixinFont {

    @Shadow
    public abstract List<FormattedCharSequence> split(FormattedText text, int width);

    @Shadow
    public abstract String bidirectionalShaping(String text);

    @Shadow
    @Final
    public int lineHeight;

    @Shadow
    public abstract int width(FormattedCharSequence text);

    @Shadow
    public abstract boolean isBidirectional();

    @Shadow
    public abstract Font.PreparedText prepareText(final FormattedCharSequence text, final float x, final float y, final int originalColor, final boolean drawShadow, final boolean includeEmpty, final int backgroundColor);

    // TODO 26.2
//    @Inject(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), cancellable = true)
//    private void allowNewLines_String(String text, float x, float y, int originalColor, boolean drawShadow, int backgroundColor, CallbackInfoReturnable<Font.PreparedText> cir) {
//        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
//            final List<FormattedCharSequence> lines = split(FormattedText.of(isBidirectional() ? this.bidirectionalShaping(text) : text), Integer.MAX_VALUE);
//            if (!lines.isEmpty()) {
//                ci.cancel();
//                for (int i = 0, size = lines.size(); i < size; i++) {
//                    this.prepareText(lines.get(i), x, y - (size * (lineHeight + 2)) + (i * (lineHeight + 2)), originalColor, drawShadow, false, backgroundColor);
//                }
//            }
//        }
//    }
//
//    @Inject(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), cancellable = true)
//    private void allowNewLines_Text(FormattedCharSequence text, float x, float y, int originalColor, boolean drawShadow, boolean includeEmpty, int backgroundColor, CallbackInfoReturnable<Font.PreparedText> cir) {
//        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
//            final List<FormattedCharSequence> lines = split(str, Integer.MAX_VALUE);
//            if (!lines.isEmpty()) {
//                ci.cancel();
//                for (int i = 0, size = lines.size(); i < size; i++) {
//                    this.drawInBatch(lines.get(i), x, y - (lines.size() * (lineHeight + 2)) + (i * (lineHeight + 2)), color, dropShadow, new Matrix4f(pose), bufferSource, displayMode, backgroundColor, packedLightCoords);
//                }
//            }
//        }
//    }
//
//    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
//    private void allowNewLines_getWidth(FormattedText text, CallbackInfoReturnable<Integer> cir) {
//        if (Minecraft.getInstance().level != null && ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
//            int i = 0;
//            for (FormattedCharSequence wrapLine : this.split(text, Integer.MAX_VALUE)) {
//                if (width(wrapLine) >= i) i = width(wrapLine);
//            }
//            cir.setReturnValue(Mth.ceil(i));
//        }
//    }

}
