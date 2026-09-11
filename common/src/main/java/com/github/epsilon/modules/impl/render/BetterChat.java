package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.interfaces.ChatComponentAccessor;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;

import java.awt.*;
import java.util.function.Supplier;

public class BetterChat extends Module {

    public static final BetterChat INSTANCE = new BetterChat();

    private BetterChat() {
        super("Better Chat", Category.RENDER);
        setDefaultEnabled(true);
    }

    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(16, 17, 20, 150));
    private final ColorSetting inputBackgroundColor = colorSetting("Input Background Color", new Color(16, 17, 20, 185));
    private final DoubleSetting chatRadius = doubleSetting("Chat Radius", 4.5, 0.0, 12.0, 0.5);
    private final DoubleSetting inputRadius = doubleSetting("Input Radius", 3.5, 0.0, 12.0, 0.5);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 8.0, 1.0, 20.0, 0.5);
    private final BoolSetting dropShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 10.0, 2.0, 24.0, 1.0, dropShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(255, 255, 255, 110), dropShadow::getValue);

    private static final float CHAT_BOTTOM_OFFSET = 40.0f;
    private static final float CHAT_HORIZONTAL_PADDING = 4.0f;
    private static final float CHAT_VERTICAL_PADDING = 3.0f;
    private static final float INPUT_X = 2.0f;
    private static final float INPUT_BOTTOM_OFFSET = 2.0f;
    private static final float INPUT_HEIGHT = 12.0f;
    private static final float CHAT_SLIDE_DISTANCE = 3.0f;
    private static final float INPUT_SLIDE_DISTANCE = 2.0f;
    private static final long CHAT_VISIBILITY_DURATION_MS = 180L;
    private static final long CHAT_RESIZE_DURATION_MS = 210L;
    private static final long INPUT_VISIBILITY_DURATION_MS = 150L;

    private final Animation chatVisibilityAnimation = new Animation(Easing.EASE_OUT_CUBIC, CHAT_VISIBILITY_DURATION_MS);
    private final Animation chatHeightAnimation = new Animation(Easing.EASE_OUT_CUBIC, CHAT_RESIZE_DURATION_MS);
    private final Animation inputVisibilityAnimation = new Animation(Easing.EASE_OUT_CUBIC, INPUT_VISIBILITY_DURATION_MS);

    private final Supplier<Render2DScheduler> scheduler = Suppliers.memoize(Render2DScheduler::new);

    private float lastChatBottom;
    private float lastPanelOpacity = 1.0f;
    private ScreenRectangle chatTextScissor;

    @Override
    protected void onEnable() {
        resetAnimations();
    }

    @Override
    protected void onDisable() {
        resetAnimations();
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        boolean focused = mc.gui.screen() instanceof ChatScreen;
        float screenWidth = event.getGuiGraphics().guiWidth();
        float screenHeight = event.getGuiGraphics().guiHeight();
        if (screenWidth <= 4.0f || screenHeight <= CHAT_BOTTOM_OFFSET) {
            chatTextScissor = null;
            return;
        }

        float chatScale = mc.options.chatScale().get().floatValue();
        float contentWidth = ChatComponent.getWidth(mc.options.chatWidth().get());
        float chatBottom = screenHeight - CHAT_BOTTOM_OFFSET;
        ChatComponentAccessor.ChatVisibility visibility = ((ChatComponentAccessor) mc.gui.hud.getChat()).epsilon$getVisibility(mc.gui.hud.getGuiTicks(), focused);
        boolean hasQueue = mc.gui.chatListener().queueSize() > 0L;
        boolean drawChatPanel = visibility.lineCount() > 0 || hasQueue;

        float lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0)) * chatScale;
        float contentHeight = visibility.heightFactor() * lineHeight;
        float contentBelowChat = hasQueue ? 9.0f * chatScale : 0.0f;
        float panelOpacity = hasQueue ? 1.0f : visibility.opacity();
        float verticalPadding = CHAT_VERTICAL_PADDING * (focused || hasQueue ? 1.0f : panelOpacity);

        float chatX = INPUT_X;
        float chatWidth = Math.min(screenWidth - INPUT_X * 2.0f, contentWidth + CHAT_HORIZONTAL_PADDING * 2.0f * chatScale);
        float targetChatY = Math.max(0.0f, chatBottom - contentHeight - verticalPadding);
        float targetChatHeight = Math.min(screenHeight - targetChatY, contentHeight + contentBelowChat + verticalPadding * 2.0f);
        if (drawChatPanel) {
            lastChatBottom = targetChatY + targetChatHeight;
            lastPanelOpacity = panelOpacity;
        } else if (lastChatBottom == 0.0f) {
            lastChatBottom = chatBottom + CHAT_VERTICAL_PADDING;
        }

        chatVisibilityAnimation.run(drawChatPanel ? 1.0f : 0.0f);
        chatHeightAnimation.run(drawChatPanel ? targetChatHeight : 0.0f);
        inputVisibilityAnimation.run(focused ? 1.0f : 0.0f);

        float chatProgress = chatVisibilityAnimation.getValue();
        float inputProgress = inputVisibilityAnimation.getValue();
        float animatedChatHeight = chatHeightAnimation.getValue();
        boolean renderChatPanel = chatProgress > 0.01f && animatedChatHeight > 0.01f;
        boolean renderInputPanel = inputProgress > 0.01f;
        if (!renderChatPanel && !renderInputPanel) {
            chatTextScissor = null;
            return;
        }

        float chatY = lastChatBottom - animatedChatHeight + (1.0f - chatProgress) * CHAT_SLIDE_DISTANCE;
        float animatedPanelOpacity = chatProgress * (drawChatPanel ? panelOpacity : lastPanelOpacity);
        updateChatTextScissor(renderChatPanel, chatX, chatY, chatWidth, animatedChatHeight, screenWidth, screenHeight);

        float inputY = screenHeight - INPUT_BOTTOM_OFFSET - INPUT_HEIGHT + (1.0f - inputProgress) * INPUT_SLIDE_DISTANCE;
        float inputWidth = screenWidth - INPUT_X * 2.0f;

        float chatRadius = Math.min(this.chatRadius.getValue().floatValue(), animatedChatHeight * 0.5f);
        float inputRadius = this.inputRadius.getValue().floatValue();
        float blur = blurStrength.getValue().floatValue();
        float coordinateScale = (float) (mc.getWindow().getGuiScale() / LuminRenderSystem.getGuiScale());
        float renderChatX = chatX * coordinateScale;
        float renderChatY = chatY * coordinateScale;
        float renderChatWidth = chatWidth * coordinateScale;
        float renderChatHeight = animatedChatHeight * coordinateScale;
        float renderChatRadius = chatRadius * coordinateScale;
        float renderInputX = INPUT_X * coordinateScale;
        float renderInputY = inputY * coordinateScale;
        float renderInputWidth = inputWidth * coordinateScale;
        float renderInputHeight = INPUT_HEIGHT * coordinateScale;
        float renderInputRadius = inputRadius * coordinateScale;

        // 模糊必须先读取尚未叠加聊天面板的主渲染目标。
        if (renderChatPanel) {
            BlurShader.INSTANCE.render(renderChatX, renderChatY, renderChatWidth, renderChatHeight, renderChatRadius, blur * animatedPanelOpacity);
        }
        if (renderInputPanel) {
            BlurShader.INSTANCE.render(renderInputX, renderInputY, renderInputWidth, renderInputHeight, renderInputRadius, blur * inputProgress);
        }

        Render2DScheduler renderScheduler = scheduler.get();
        renderScheduler.clear();
        Render2DScheduler.LayerHandle layer = renderScheduler.layer(0);

        if (dropShadow.getValue()) {
            float shadowRadius = shadowBlur.getValue().floatValue() * coordinateScale;
            if (renderChatPanel) {
                layer.addShadow(renderChatX, renderChatY, renderChatWidth, renderChatHeight, renderChatRadius, shadowRadius, withOpacity(shadowColor.getValue(), animatedPanelOpacity));
            }
            if (renderInputPanel) {
                layer.addShadow(renderInputX, renderInputY, renderInputWidth, renderInputHeight, renderInputRadius, shadowRadius, withOpacity(shadowColor.getValue(), inputProgress));
            }
        }

        if (renderChatPanel) {
            layer.addRoundRect(renderChatX, renderChatY, renderChatWidth, renderChatHeight, renderChatRadius, withOpacity(backgroundColor.getValue(), animatedPanelOpacity));
        }
        if (renderInputPanel) {
            layer.addRoundRect(renderInputX, renderInputY, renderInputWidth, renderInputHeight, renderInputRadius, withOpacity(inputBackgroundColor.getValue(), inputProgress));
        }
        renderScheduler.flushAndClear();
    }

    private void updateChatTextScissor(boolean visible, float x, float y, float width, float height, float screenWidth, float screenHeight) {
        if (!visible) {
            chatTextScissor = null;
            return;
        }

        int left = Math.max(0, (int) Math.floor(x));
        int top = Math.max(0, (int) Math.floor(y));
        int right = Math.min((int) screenWidth, (int) Math.ceil(x + width));
        int bottom = Math.min((int) screenHeight, (int) Math.ceil(y + height));
        chatTextScissor = right > left && bottom > top ? new ScreenRectangle(left, top, right - left, bottom - top) : null;
    }

    public float getChatContentAlpha() {
        return isEnabled() ? chatVisibilityAnimation.getValue() : 1.0f;
    }

    public ScreenRectangle getChatTextScissor() {
        return isEnabled() ? chatTextScissor : null;
    }

    private void resetAnimations() {
        resetAnimation(chatVisibilityAnimation);
        resetAnimation(chatHeightAnimation);
        resetAnimation(inputVisibilityAnimation);
        lastChatBottom = 0.0f;
        lastPanelOpacity = 1.0f;
        chatTextScissor = null;
    }

    private void resetAnimation(Animation animation) {
        animation.setStartValue(0.0f);
        animation.reset();
    }

    private Color withOpacity(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * opacity));
    }

}
