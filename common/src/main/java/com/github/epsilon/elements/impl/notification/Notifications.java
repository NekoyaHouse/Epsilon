package com.github.epsilon.elements.impl.notification;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.notification.style.AkarinNotification;
import com.github.epsilon.elements.impl.notification.style.NVIDIANotification;
import com.github.epsilon.elements.impl.notification.style.QuantumNotification;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class Notifications extends HudModule {

    public static final Notifications INSTANCE = new Notifications();

    public enum Style {
        NVIDIA,
        Quantum,
        Akarin
    }

    private final EnumSetting<Style> style = enumSetting("Style", Style.Quantum, ignored -> clearQuantum());
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.05);
    private final DoubleSetting fontScale = doubleSetting("Font Scale", 0.8, 0.5, 2.0, 0.05);

    private final NVIDIANotification nvidia = new NVIDIANotification(this);
    private final QuantumNotification quantum = new QuantumNotification(this);
    private final AkarinNotification akarin = new AkarinNotification(this);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private Notifications() {
        super("Notifications", 3.2f, 3.2f, 120.0f, 30.0f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        NotificationManager.INSTANCE.update();

        Notification preview = null;
        if (mc.gui.screen() instanceof HudEditorScreen) {
            preview = Notification.preview(
                    EpsilonTranslations.Notifications.PREVIEW_TITLE.getTranslatedName(),
                    EpsilonTranslations.Notifications.PREVIEW_MESSAGE.getTranslatedName(),
                    isStyle(Style.Quantum) ? NotificationMode.Info : NotificationMode.Success
            );
        }

        if (NotificationManager.INSTANCE.isEmpty() && preview == null) return;

        TextRenderer metrics = textRendererSupplier.get();
        UiTree.Scope scope = renderScope();
        if (isStyle(Style.Quantum)) {
            float scale = getScale();
            List<Notification> entries = new ArrayList<>(NotificationManager.INSTANCE.getNotifications());
            entries.removeIf(notification -> !quantum.isVisible(notification));
            Collections.reverse(entries);
            if (entries.isEmpty() && preview != null) entries.add(preview);
            if (entries.isEmpty()) return;
            setNotificationBounds(quantum.width(scale), quantum.totalHeight(entries.size(), scale));
            quantum.render(scope, metrics, entries, x, y, scale, getFontScale(), getHorizontalAnchor(), getVerticalAnchor());
        } else if (isStyle(Style.Akarin)) {
            float scale = getScale();
            List<Notification> entries = new ArrayList<>(NotificationManager.INSTANCE.getNotifications());
            entries.removeIf(notification -> !akarin.isVisible(notification));
            Collections.reverse(entries);
            if (entries.isEmpty() && preview != null) entries.add(preview);
            if (entries.isEmpty()) return;
            float boundsWidth = akarin.width(metrics, entries, scale, getFontScale());
            setNotificationBounds(boundsWidth, akarin.totalHeight(entries.size(), scale));
            akarin.render(scope, metrics, entries, x, y, boundsWidth, scale, getFontScale(),
                    getHorizontalAnchor(), getVerticalAnchor());
        } else {
            nvidia.render(scope, metrics, preview);
        }
    }

    public int getDisplayTime() {
        return isStyle(Style.Quantum) ? quantum.getDisplayTime()
                : isStyle(Style.Akarin) ? akarin.getDisplayTime() : nvidia.getDisplayTime();
    }

    public float getScale() {
        return scale.getValue().floatValue();
    }

    public float getFontScale() {
        return fontScale.getValue().floatValue();
    }

    public boolean isStyle(Style expected) {
        return style.is(expected);
    }

    public void setNotificationBounds(float width, float height) {
        setBounds(width, height);
    }

    private void clearQuantum() {
        quantum.clear();
    }

}
