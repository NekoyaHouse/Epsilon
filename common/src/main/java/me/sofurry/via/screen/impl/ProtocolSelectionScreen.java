package me.sofurry.via.screen.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.screen.ViaVersionList;
import me.sofurry.via.screen.ViaVersionListEntry;
import me.sofurry.via.screen.ViaVersionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.awt.*;

public final class ProtocolSelectionScreen extends ViaVersionScreen {

    public static final ProtocolSelectionScreen INSTANCE = new ProtocolSelectionScreen();

    private ProtocolSelectionScreen() {
        super("ViaVersion", true);
    }

    @Override
    protected void init() {
        this.setupDefaultSubtitle();
        this.addRenderableWidget(new SlotList(this.minecraft, width, height, 3 + 3 /* start offset */ + (font.lineHeight + 2) * 3 /* title is 2 */, 30, font.lineHeight + 4));

        this.addRenderableWidget(Button.builder(EpsilonTranslations.component(EpsilonTranslations.Gui.CLIENT_SETTINGS), button -> ClientSetting.INSTANCE.openViaSettingsScreen()).pos(width - 98 - 5, 5).size(98, 20).build());

        super.init();
    }

    public static class SlotList extends ViaVersionList {
        private static double scrollAmount;

        public SlotList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);

            ProtocolVersion.getReversedProtocols().stream().map(ProtocolSlot::new).forEach(this::addEntry);
            initScrollY(scrollAmount);
        }

        @Override
        protected void updateSlotAmount(double amount) {
            scrollAmount = amount;
        }
    }

    public static class ProtocolSlot extends ViaVersionListEntry {

        private final ProtocolVersion protocolVersion;

        public ProtocolSlot(final ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public Component getNarration() {
            return Component.nullToEmpty(this.protocolVersion.getName());
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            if (Minecraft.getInstance().getConnection() != null) {
                // Setting the target version while connected to a server is not allowed as this will
                // literally break our code away.
                return;
            }

            ProtocolTranslator.setTargetVersion(this.protocolVersion);
        }

        @Override
        public void extractContent(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final boolean hovered, final float deltaTicks) {
            final boolean isSelected = ProtocolTranslator.getTargetVersion().equals(protocolVersion);

            Color color = isSelected ? Color.GREEN : Color.RED;
            if (Minecraft.getInstance().getConnection() != null) {
                color = color.darker();
            }

            final Font textRenderer = Minecraft.getInstance().font;
            graphics.centeredText(textRenderer, this.protocolVersion.getName(), getContentXMiddle(), getContentYMiddle() - textRenderer.lineHeight / 2, color.getRGB());
        }
    }

}
