package me.sofurry.via.screen;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.screen.impl.PerServerVersionScreen;
import me.sofurry.via.screen.impl.ProtocolSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.jspecify.annotations.NonNull;

/**
 * Wrapper class for {@link ObjectSelectionList} including the following features:
 * <ul>
 *     <li>Changing the constructor arguments to be more readable and customizable</li>
 *     <li>Adds {@link #initScrollY(double)} to save the scroll state after closing the screen, requires static tracking by the implementation</li>
 *     <li>Removes the selection box</li>
 * </ul>
 *
 * @see ProtocolSelectionScreen
 * @see PerServerVersionScreen
 */
public class ViaVersionList extends ObjectSelectionList<ViaVersionListEntry> {

    public ViaVersionList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
        super(minecraftClient, width, height - top - bottom, top, entryHeight);
    }

    public void initScrollY(final double scrollY) {
        // Needs calling last in init to have data loaded before setting scroll amount
        if (ClientSetting.INSTANCE.saveScrollPositionInSlotScreens.getValue()) {
            this.setScrollAmount(scrollY);
        }
    }

    @Override
    public void setScrollAmount(double scrollY) {
        super.setScrollAmount(scrollY);
        updateSlotAmount(scrollAmount()); // Ensure value is clamped
    }

    @Override
    protected void extractSelection(final @NonNull GuiGraphicsExtractor graphics, final @NonNull ViaVersionListEntry entry, final int outlineColor) {
        // Remove selection box
    }

    protected void updateSlotAmount(final double amount) {
        // To be overridden
    }

}
