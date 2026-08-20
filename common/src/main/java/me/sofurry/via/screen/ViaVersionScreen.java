package me.sofurry.via.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.awt.*;

public class ViaVersionScreen extends Screen {

    private static final String MOD_URL = "Get good, get Epsilon";

    private final boolean backButton;
    public Screen prevScreen;

    private Component subtitle;
    private Button.OnPress subtitlePressAction;

    private PlainTextButton subtitleWidget;

    public ViaVersionScreen(final String title, final boolean backButton) {
        this(Component.nullToEmpty(title), backButton);
    }

    public ViaVersionScreen(final Component title, final boolean backButton) {
        super(title);
        this.backButton = backButton;
    }

    /**
     * Sets the subtitle and the subtitle press action to the default values
     * The default value of the subtitle is the url to the GitHub repository of ViaVersion
     * The default value of the subtitle press action is to open the url in a confirmation screen
     */
    public void setupDefaultSubtitle() {
        this.setupUrlSubtitle(MOD_URL);
    }

    /**
     * Sets the subtitle and the subtitle press action to the default values
     *
     * @param subtitle The subtitle which should be rendered
     */
    public void setupUrlSubtitle(final String subtitle) {
        this.setupSubtitle(Component.nullToEmpty(subtitle), ConfirmLinkScreen.confirmLink(this, subtitle));
    }

    /**
     * Sets the subtitle and the subtitle press action
     *
     * @param subtitle The subtitle that should be rendered
     */
    public void setupSubtitle(final Component subtitle) {
        this.setupSubtitle(subtitle, null);
    }

    /**
     * Sets the subtitle and the subtitle press action
     *
     * @param subtitle            The subtitle that should be rendered
     * @param subtitlePressAction The press action that should be executed when the subtitle is clicked
     */
    public void setupSubtitle(final Component subtitle, final Button.OnPress subtitlePressAction) {
        this.subtitlePressAction = subtitlePressAction;

        if (subtitleWidget != null) { // Allows removing the subtitle when calling this method twice.
            removeWidget(subtitleWidget);
            subtitleWidget = null;
        }
        if (subtitlePressAction == null) {
            this.subtitle = subtitle;
        } else {
            this.subtitle = null;
            assert subtitle != null;
            final int subtitleWidth = font.width(subtitle);
            this.addRenderableWidget(subtitleWidget = new PlainTextButton(width / 2 - (subtitleWidth / 2), (font.lineHeight + 2) * 2 + 3, subtitleWidth, font.lineHeight + 2, subtitle, subtitlePressAction, font));
        }
    }

    /**
     * Intended method to open a ViaVersion screen
     *
     * @param prevScreen The current screen from which the ViaVersion screen is opened
     */
    public void open(final Screen prevScreen) {
        this.prevScreen = prevScreen;
        setScreen(this);
    }

    /**
     * Returns this screen instance after setting the previous screen.
     *
     * @param prevScreen The screen to return to when this screen is closed
     * @return This screen instance
     */
    public Screen get(final Screen prevScreen) {
        this.prevScreen = prevScreen;
        return this;
    }

    public static void setScreen(final Screen screen) {
        final Minecraft client = Minecraft.getInstance();

        client.execute(() -> client.gui.setScreen(screen));
    }

    @Override
    protected void init() {
        if (backButton) {
            this.addRenderableWidget(Button.builder(Component.nullToEmpty("<-"), button -> this.onClose()).pos(5, 5).size(20, 20).build());
        }
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        this.renderTitle(graphics);
    }

    @Override
    public void onClose() {
        if (prevScreen instanceof ViaVersionScreen viaVersionScreen) {
            viaVersionScreen.open(viaVersionScreen.prevScreen); // Support recursive opening
        } else {
            Minecraft.getInstance().gui.setScreen(prevScreen);
        }
    }

    /**
     * Renders the ViaVersion title
     *
     * @param context The current draw context
     */
    public void renderTitle(final GuiGraphicsExtractor context) {
        final Matrix3x2fStack matrices = context.pose();

        matrices.pushMatrix();
        matrices.scale(2F, 2F);
        context.centeredText(font, "ViaVersion", width / 4, 3, Color.ORANGE.getRGB());
        matrices.popMatrix();

        renderSubtitle(context);
    }

    /**
     * Renders the subtitle that doesn't have a press action
     *
     * @param context The current draw context
     */
    public void renderSubtitle(final GuiGraphicsExtractor context) {
        if (subtitle != null && subtitlePressAction == null) {
            final int startY = (font.lineHeight + 2) * 2 + 3;
            context.centeredText(font, subtitle, width / 2, subtitleCentered() ? this.height / 2 - startY : startY, -1);
        }
    }

    protected boolean subtitleCentered() {
        // To be overridden
        return false;
    }

    public void renderScreenTitle(final GuiGraphicsExtractor context) {
        context.centeredText(this.font, this.title, this.width / 2, 70, 16777215);
    }

    public Component getSubtitle() {
        return subtitle;
    }

    public PlainTextButton getSubtitleWidget() {
        return subtitleWidget;
    }

    /**
     * Opens an error screen with a specific title and throws the given throwable
     *
     * @param title     The title of the error screen
     * @param throwable The throwable that should be thrown
     * @param next      The screen that should be opened after the error screen is closed
     */
    public static void showErrorScreen(final Component title, final Throwable throwable, final Screen next) {
        Constants.LOGGER.error("Something went wrong!", throwable);

        final Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.setScreen(new AlertScreen(() -> client.gui.setScreen(next), title, EpsilonTranslations.component(EpsilonTranslations.Via.BASE_SOMETHING_WENT_WRONG).append("\n" + throwable.getMessage()), EpsilonTranslations.component(EpsilonTranslations.Via.BASE_CANCEL), false)));
    }

}
