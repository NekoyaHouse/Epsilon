package com.github.epsilon.managers;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.*;
import com.github.epsilon.elements.impl.island.Island;
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

public class HudElementManager {

    public static final HudElementManager INSTANCE = new HudElementManager();

    private final List<HudModule> elements = new ArrayList<>();
    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);

    private HudElementManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void initElements() {
        addElement(Hotkey.INSTANCE);
        addElement(Inventory.INSTANCE);
        addElement(Island.INSTANCE);
        addElement(ModuleList.INSTANCE);
        addElement(MTF.INSTANCE);
        addElement(Notifications.INSTANCE);
        addElement(PlayerInfo.INSTANCE);
        addElement(ScaffoldBlock.INSTANCE);
        addElement(TargetHUD.INSTANCE);
        addElement(VictoryBanner.INSTANCE);
        addElement(Watermark.INSTANCE);
    }

    private void addElement(HudModule module) {
        elements.add(module);
        module.initI18n(EpsilonTranslateComponent.create("elements", module.getName().toLowerCase()));
    }

    public List<HudModule> getElements() {
        return elements;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (ClientUtils.isLoading() || mc.level == null || mc.gui.screen() instanceof HudEditorScreen) return;

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
