package me.sofurry.via.screen.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.screen.ViaVersionList;
import me.sofurry.via.screen.ViaVersionListEntry;
import me.sofurry.via.screen.ViaVersionScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PerServerVersionScreen extends ViaVersionScreen {

    private final Consumer<ProtocolVersion> selectionConsumer;
    private final Supplier<ProtocolVersion> selectionSupplier;

    public PerServerVersionScreen(final Screen prevScreen, final Consumer<ProtocolVersion> selectionConsumer, final Supplier<ProtocolVersion> selectionSupplier) {
        super(EpsilonTranslations.component(EpsilonTranslations.Via.SCREEN_FORCE_VERSION), true);

        this.prevScreen = prevScreen;
        this.selectionConsumer = selectionConsumer;
        this.selectionSupplier = selectionSupplier;

        this.setupSubtitle(EpsilonTranslations.component(EpsilonTranslations.Via.FORCE_VERSION_TITLE));
    }

    @Override
    protected void init() {
        super.init();

        this.addRenderableWidget(new SlotList(this.minecraft, width, height, 3 + 3 /* start offset */ + (font.lineHeight + 2) * 3 /* title is 2 */, -5, font.lineHeight + 4));
    }

    public final class SlotList extends ViaVersionList {

        public SlotList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);

            this.addEntry(new ResetSlot());
            ProtocolVersion.getReversedProtocols().stream().map(ProtocolSlot::new).forEach(this::addEntry);
        }
    }

    public abstract class SharedSlot extends ViaVersionListEntry {

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            onClose();
        }
    }

    public final class ResetSlot extends SharedSlot {

        @Override
        public Component getNarration() {
            return EpsilonTranslations.component(EpsilonTranslations.Via.BASE_CANCEL_AND_RESET);
        }

        @Override
        public void mappedMouseClicked(final double mouseX, final double mouseY, final int button) {
            selectionConsumer.accept(null);
        }

        @Override
        public void extractContent(final GuiGraphicsExtractor context, final int mouseX, final int mouseY, final boolean hovered, final float deltaTicks) {
            final Font textRenderer = Minecraft.getInstance().font;
            context.centeredText(textRenderer, ((MutableComponent) getNarration()).withStyle(ChatFormatting.GOLD), getContentXMiddle(), getContentYMiddle() - textRenderer.lineHeight / 2, -1);
        }
    }

    public final class ProtocolSlot extends SharedSlot {

        private final ProtocolVersion protocolVersion;

        public ProtocolSlot(final ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public Component getNarration() {
            return Component.nullToEmpty(this.protocolVersion.getName());
        }

        @Override
        public void mappedMouseClicked(final double mouseX, final double mouseY, final int button) {
            selectionConsumer.accept(protocolVersion);
        }

        @Override
        public void extractContent(final GuiGraphicsExtractor context, final int mouseX, final int mouseY, final boolean hovered, final float deltaTicks) {
            final boolean isSelected = protocolVersion.equals(selectionSupplier.get());

            final Font textRenderer = Minecraft.getInstance().font;
            context.centeredText(textRenderer, this.protocolVersion.getName(), getContentXMiddle(), getContentYMiddle() - textRenderer.lineHeight / 2, isSelected ? Color.GREEN.getRGB() : -1);
        }
    }

}
