package com.github.epsilon.gui.dropdown;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;

public final class ReisaDropdownCompanion {

    private static final float IMAGE_ASPECT_RATIO = 710.0f / 1280.0f;

    private final Animation entranceAnim = new Animation(Easing.EASE_OUT_BACK, 520L);

    private Action shownAction = Action.IDLE;
    private Action activeAction;
    private long actionStartedMs;
    private long actionEndsMs;
    private long nextBlinkMs;
    private long blinkEndsMs;
    private long reactionRevision;
    private boolean texturesPrewarmed;
    private int sessionId = -1;

    public void open(int newSessionId) {
        if (sessionId == newSessionId) {
            return;
        }

        sessionId = newSessionId;
        shownAction = Action.IDLE;
        activeAction = null;
        texturesPrewarmed = false;
        entranceAnim.setStartValue(0.0f);
        entranceAnim.run(0.0f);
        entranceAnim.run(1.0f);

        long now = Util.getMillis();
        nextBlinkMs = now + 2_800L + Math.floorMod(newSessionId * 347L, 1_300L);
        blinkEndsMs = 0L;
        react(Action.PANEL_OPEN);
    }

    public void react(Action action) {
        if (action == null || !action.isTransient()) {
            return;
        }

        long now = Util.getMillis();
        if (activeAction != null
                && now - actionStartedMs < 32L
                && action.priority() < activeAction.priority()) {
            return;
        }

        activeAction = action;
        actionStartedMs = now;
        actionEndsMs = now + action.durationMs();
        reactionRevision++;
    }

    public long getReactionRevision() {
        return reactionRevision;
    }

    public void draw(UiTree.Scope scope, float screenWidth, float screenHeight,
                     int mouseX, int mouseY, boolean mouseBlockedByGui) {
        long now = Util.getMillis();
        Layout layout = resolveLayout(screenWidth, screenHeight);
        Action desiredAction = resolveAction(now, layout, mouseX, mouseY, mouseBlockedByGui);
        switchExpression(desiredAction);
        if (!texturesPrewarmed) {
            prewarmTextures(scope);
            texturesPrewarmed = true;
        }

        entranceAnim.run(1.0f);

        float entrance = Mth.clamp(entranceAnim.getValue(), 0.0f, 1.0f);
        float pulse = resolveActionPulse(now);
        float bob = (float) Math.sin((now % 3_896L) / 620.0) * 1.15f;
        float motionX = resolveMotionX(now, pulse);
        float motionY = resolveMotionY(pulse);
        float actionScale = resolveActionScale(pulse);

        float drawWidth = layout.width() * actionScale;
        float drawHeight = layout.height() * actionScale;
        float drawX = layout.x() - (drawWidth - layout.width()) * 0.5f
                + (1.0f - entrance) * 34.0f + motionX;
        float drawY = layout.y() - (drawHeight - layout.height()) + bob + motionY;
        float alpha = entrance * 0.94f;

        scope.layer(-2, layer -> layer.texture(
                shownAction.texture(), drawX + 2.0f, drawY + 3.0f, drawWidth, drawHeight,
                0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.BLACK, alpha * 0.15f), true
        ));
        scope.layer(-1, layer -> layer.texture(
                shownAction.texture(), drawX, drawY, drawWidth, drawHeight,
                0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.WHITE, alpha), true
        ));
    }

    public float getLeftEdge(float screenWidth, float screenHeight) {
        return resolveLayout(screenWidth, screenHeight).x();
    }

    private Action resolveAction(long now, Layout layout, int mouseX, int mouseY, boolean mouseBlockedByGui) {
        if (activeAction != null && now >= actionEndsMs) {
            activeAction = null;
        }
        if (activeAction != null) {
            return activeAction;
        }

        if (!mouseBlockedByGui && isHeadHovered(layout, mouseX, mouseY)) {
            return Action.HEAD_HOVER;
        }

        if (now >= nextBlinkMs) {
            blinkEndsMs = now + 145L;
            nextBlinkMs = now + 3_200L + Math.floorMod(now, 1_500L);
        }
        return now < blinkEndsMs ? Action.BLINK : Action.IDLE;
    }

    private void switchExpression(Action desiredAction) {
        shownAction = desiredAction;
    }

    private void prewarmTextures(UiTree.Scope scope) {
        Color transparent = withAlpha(Color.WHITE, 0.0f);
        scope.layer(-3, layer -> {
            for (Action action : Action.values()) {
                layer.texture(action.texture(), -1.0f, -1.0f, 1.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f, transparent, true);
            }
        });
    }

    private float resolveActionPulse(long now) {
        if (activeAction == null || activeAction.durationMs() <= 0L) {
            return 0.0f;
        }
        float progress = Mth.clamp((now - actionStartedMs) / (float) activeAction.durationMs(), 0.0f, 1.0f);
        return Mth.sin(progress * Mth.PI);
    }

    private float resolveMotionX(long now, float pulse) {
        if (activeAction == Action.DRAG) {
            return (float) Math.sin((now % 226L) / 36.0) * 1.8f;
        }
        if (activeAction == Action.SECONDARY_CLICK || activeAction == Action.KEY_BIND) {
            return -2.2f * pulse;
        }
        return 0.0f;
    }

    private float resolveMotionY(float pulse) {
        if (activeAction == Action.SCROLL_UP) {
            return -4.0f * pulse;
        }
        if (activeAction == Action.SCROLL_DOWN) {
            return 4.0f * pulse;
        }
        if (activeAction == Action.CANCEL || activeAction == Action.PANEL_CLOSE) {
            return 2.5f * pulse;
        }
        return -1.5f * pulse;
    }

    private float resolveActionScale(float pulse) {
        if (activeAction == Action.BUTTON_ACTION
                || activeAction == Action.CONFIRM
                || activeAction == Action.TOGGLE_ON) {
            return 1.0f + 0.018f * pulse;
        }
        if (activeAction == Action.DRAG || activeAction == Action.CANCEL) {
            return 1.0f - 0.012f * pulse;
        }
        return 1.0f;
    }

    private boolean isHeadHovered(Layout layout, int mouseX, int mouseY) {
        float headX = layout.x() + layout.width() * 0.20f;
        float headY = layout.y() + layout.height() * 0.035f;
        float headWidth = layout.width() * 0.48f;
        float headHeight = layout.height() * 0.27f;
        return mouseX >= headX && mouseX <= headX + headWidth
                && mouseY >= headY && mouseY <= headY + headHeight;
    }

    private Layout resolveLayout(float screenWidth, float screenHeight) {
        float imageHeight = Mth.clamp(screenHeight * 0.60f, 150.0f, 520.0f);
        float maxWidth = Math.max(90.0f, screenWidth * 0.24f);
        imageHeight = Math.min(imageHeight, maxWidth / IMAGE_ASPECT_RATIO);
        imageHeight = Math.min(imageHeight, screenHeight * 0.82f);
        float imageWidth = imageHeight * IMAGE_ASPECT_RATIO;
        float imageX = screenWidth - imageWidth - Math.max(2.0f, DropdownTheme.PANEL_MARGIN_X * 0.45f);
        float imageY = screenHeight - imageHeight;
        return new Layout(imageX, imageY, imageWidth, imageHeight);
    }

    private Color withAlpha(Color color, float alpha) {
        int value = Mth.clamp((int) (255.0f * Mth.clamp(alpha, 0.0f, 1.0f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), value);
    }

    private record Layout(float x, float y, float width, float height) {
    }

    public enum Action {
        PANEL_OPEN("00", 720L, 60),
        COLOR_PICK("01", 760L, 80),
        SECONDARY_CLICK("02", 440L, 30),
        PRIMARY_CLICK("03", 380L, 20),
        PANEL_CLOSE("04", 620L, 65),
        MODULE_HIDDEN("05", 720L, 75),
        CANCEL("06", 760L, 90),
        TOGGLE_ON("07", 660L, 80),
        TOGGLE_OFF("08", 660L, 80),
        IDLE("09", 0L, 0),
        SLIDER_ADJUST("10", 520L, 70),
        CONFIRM("11", 620L, 85),
        TYPING("12", 480L, 70),
        SCROLL_UP("13", 340L, 55),
        KEY_BIND("14", 1_600L, 90),
        BUTTON_ACTION("15", 720L, 90),
        DRAG("16", 360L, 80),
        SCROLL_DOWN("17", 340L, 55),
        HEAD_HOVER("18", 0L, 0),
        BLINK("99", 0L, 0);

        private final Identifier texture;
        private final long durationMs;
        private final int priority;

        Action(String textureSuffix, long durationMs, int priority) {
            this.texture = ResourceLocationUtils.getIdentifier(
                    "textures/gui/galgame/reisa_" + textureSuffix + ".png"
            );
            this.durationMs = durationMs;
            this.priority = priority;
        }

        public Identifier texture() {
            return texture;
        }

        public long durationMs() {
            return durationMs;
        }

        public int priority() {
            return priority;
        }

        public boolean isTransient() {
            return durationMs > 0L;
        }
    }
}
