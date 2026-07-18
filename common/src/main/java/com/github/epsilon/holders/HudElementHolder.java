package com.github.epsilon.holders;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.*;
import com.github.epsilon.elements.impl.notification.Notifications;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.utils.client.ClientUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class HudElementHolder {

    public static final HudElementHolder INSTANCE = new HudElementHolder();

    private HudElementHolder() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final List<HudModule> elements = new ArrayList<>();
    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);

    public void initElements() {
        addElement(Notifications.INSTANCE);
        addElement(BPS.INSTANCE);
        addElement(MTF.INSTANCE);
        addElement(Inventory.INSTANCE);
        addElement(ModuleList.INSTANCE);
        addElement(Potions.INSTANCE);
        addElement(ScaffoldBlock.INSTANCE);
        addElement(TargetHUD.INSTANCE);
        addElement(Watermark.INSTANCE);
    }

    private void addElement(HudModule module) {
        elements.add(module);
        module.setAddonId("epsilon");
        module.initI18n(EpsilonTranslateComponent.create("elements", module.getName().toLowerCase()));
    }

    public List<HudModule> getElements() {
        return elements;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (ClientUtils.isLoading() || mc.level == null || mc.screen instanceof HudEditorScreen) return;

        scene.beginFrame();
        for (HudModule element : elements) {
            if (element.isEnabled()) {
                element.updateLayout();
                // HUD chrome 统一提交到 scheduler；原版物品等 overlay 在 flush 后单独绘制。
                element.renderWithBatch(mc.getDeltaTracker(), scene.batch(UiLayer.CONTENT));
            }
        }
        scene.endFrame();

        for (HudModule element : elements) {
            if (element.isEnabled()) {
                element.renderOverlay(event.getGuiGraphics(), mc.getDeltaTracker());
            }
        }
    }

}
