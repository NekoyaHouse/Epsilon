package com.github.epsilon.gui.troll;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.screen.PlatformNoticeScreen;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.math.BigDecimal;
import java.util.Objects;

public class TrollSettingRow {

    public enum ModuleAction {TOGGLE, BIND, HIDDEN}

    private final Setting<?> setting;
    private final Module module;
    private final ModuleAction action;
    private final Animation progress = new Animation(Easing.EASE_OUT_QUART, 300L);
    private final Animation hover = new Animation(Easing.EASE_OUT_EXPO, 300L);
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private DropdownTextField input;
    private String originalText;
    private boolean listening;
    private boolean dragging;
    private boolean enumDragged;
    private double dragStartX;
    private Double pendingNumber;

    public TrollSettingRow(Setting<?> setting) {
        this.setting = setting;
        this.module = null;
        this.action = null;
        progress.setStartValue(target());
    }

    public TrollSettingRow(Module module, ModuleAction action) {
        this.setting = null;
        this.module = module;
        this.action = action;
        progress.setStartValue(target());
    }

    public void layout(UiRect bounds) {
        this.bounds = bounds;
    }

    public String label() {
        if (setting != null) return setting.getDisplayName();
        return switch (action) {
            case TOGGLE -> module.getTranslatedName();
            case BIND -> EpsilonTranslations.TrollGui.KEYBIND.getTranslatedName();
            case HIDDEN -> EpsilonTranslations.Module.HIDDEN.getTranslatedName();
        };
    }

    private String valueText() {
        if (listening) return EpsilonTranslations.TrollGui.LISTENING.getTranslatedName();
        if (action == ModuleAction.BIND) {
            return KeybindUtils.format(module.getKeyBind()) + " (" + (module.getBindMode() == Module.BindMode.Hold
                    ? EpsilonTranslations.Keybind.HOLD : EpsilonTranslations.Keybind.TOGGLE).getTranslatedName() + ")";
        }
        return switch (setting) {
            case KeybindSetting keybind -> KeybindUtils.format(keybind.getValue());
            case IntSetting number -> Integer.toString((int) Math.round(numberValue()));
            case DoubleSetting number -> formatNumber(numberValue());
            case EnumSetting<?> mode -> mode.getTranslatedValue();
            case StringSetting text -> text.getValue();
            case StringListSetting list ->
                    list.getValue().size() + " " + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName();
            case RegistryListSetting<?> list ->
                    list.getValue().size() + " " + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName();
            case null, default -> "";
        };
    }

    public float minimumWidth(TextRenderer metrics) {
        if (setting instanceof ColorSetting) {
            return Math.max(80.0f, metrics.getWidth(label(), TrollTheme.TEXT_SCALE)
                    + metrics.getHeight(TrollTheme.TEXT_SCALE) + 26.0f);
        }
        return Math.max(80.0f, metrics.getWidth(label(), TrollTheme.TEXT_SCALE)
                + metrics.getWidth(valueText(), TrollTheme.VALUE_SCALE) + 16.0f);
    }

    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        float h = scope.animate(hover, bounds.contains(mouseX, mouseY));
        float p = scope.animate(progress, target());
        scope.rect(1, bounds.x(), bounds.y(), bounds.width(), bounds.height(), new Color(0, 0, 0, 40));
        if (p > 0.001f)
            scope.rect(1, bounds.x(), bounds.y(), bounds.width() * p, bounds.height(), TrollTheme.primary());
        int hoverAlpha = ClientSetting.INSTANCE.trollHoverAlpha.getValue();
        scope.rect(2, bounds.x(), bounds.y(), bounds.width(), bounds.height(), new Color(255, 255, 255, (int) (hoverAlpha * h)));
        if (isEditing()) {
            scope.layer(3, content -> input.draw(content, TrollTheme.metrics(metrics), bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(), mouseX, mouseY, "", TrollTheme.TEXT_SCALE));
            return;
        }
        String value = valueText();
        float valueWidth = setting instanceof ColorSetting
                ? metrics.getHeight(TrollTheme.TEXT_SCALE) + 6.0f
                : Math.min(metrics.getWidth(value, TrollTheme.VALUE_SCALE), bounds.width() * 0.48f);
        float scale = TrollTheme.TEXT_SCALE * (1.0f + h * 0.05f - (dragging ? 0.1f : 0.0f));
        Color text = setting != null && !setting.isPlatformSupported() ? TrollTheme.alpha(TrollTheme.text(), 110) : TrollTheme.text();
        scope.text(3, TrollTheme.trim(label(), metrics, scale, bounds.width() - valueWidth - 7.0f),
                bounds.x() + 2.0f + h * 2.0f, bounds.y() + 1.5f, scale, text);
        if (setting instanceof ColorSetting color) {
            drawColorSwatch(scope, metrics, color.getValue());
            return;
        }
        scope.text(3, TrollTheme.trim(value, metrics, TrollTheme.VALUE_SCALE, valueWidth),
                bounds.right() - valueWidth - 2.0f, bounds.bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE) - 2.0f,
                TrollTheme.VALUE_SCALE, text);
    }

    private void drawColorSwatch(UiTree.Scope scope, TextRenderer metrics, Color color) {
        float size = metrics.getHeight(TrollTheme.TEXT_SCALE);
        float x = bounds.right() - size - 4.0f;
        float y = bounds.y() + (bounds.height() - size) / 2.0f;
        scope.rect(3, x - 1.0f, y - 1.0f, size + 2.0f, size + 2.0f, new Color(0, 0, 0, 80));
        scope.rect(4, x, y, size, size, color);
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (!bounds.contains(event.x(), event.y())) return false;
        if (setting != null && !setting.isPlatformSupported()) {
            PlatformNoticeScreen.show(setting);
            return true;
        }
        int button = event.button();
        if (action == ModuleAction.BIND || setting instanceof KeybindSetting) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT && module != null) {
                module.setBindMode(module.getBindMode() == Module.BindMode.Toggle ? Module.BindMode.Hold : Module.BindMode.Toggle);
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                listening = true;
            }
        } else if (action == ModuleAction.HIDDEN && button == InputConstants.MOUSE_BUTTON_LEFT) {
            module.setHidden(!module.isHidden());
        } else if (action == ModuleAction.TOGGLE) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT || module.getCategory() == null) {
                TrollGuiScreen.INSTANCE.openSettings(module, event.x(), event.y());
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                module.toggle();
            } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                TrollGuiScreen.INSTANCE.openSettings(module, event.x(), event.y()).listenForBind();
            }
        } else if (setting instanceof BoolSetting toggle && button == InputConstants.MOUSE_BUTTON_LEFT) {
            toggle.setValue(!toggle.getValue());
        } else if (setting instanceof IntSetting || setting instanceof DoubleSetting) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) startEditing();
            else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                dragging = true;
                dragStartX = event.x();
                updateNumber(event.x(), event.modifiers());
            }
        } else if (setting instanceof EnumSetting<?> && button == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = true;
            enumDragged = false;
            dragStartX = event.x();
        } else if (setting instanceof StringSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            startEditing();
        } else if (setting instanceof ColorSetting color && button == InputConstants.MOUSE_BUTTON_RIGHT) {
            TrollGuiScreen.INSTANCE.openColorPicker(color, event.x(), event.y());
        } else if (setting instanceof ButtonSetting buttonSetting && button == InputConstants.MOUSE_BUTTON_LEFT) {
            buttonSetting.getValue().run();
        } else if (setting instanceof StringListSetting list && button == InputConstants.MOUSE_BUTTON_LEFT) {
            TrollGuiScreen.INSTANCE.openStringListSettingPopup(list);
        } else if (setting instanceof RegistryListSetting<?> list && button == InputConstants.MOUSE_BUTTON_LEFT) {
            TrollGuiScreen.INSTANCE.openRegistryListSettingPopup(list);
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (setting instanceof EnumSetting<?> mode) {
            enumDragged |= Math.abs(event.x() - dragStartX) > 2.0;
            if (enumDragged)
                setMode(mode, Math.clamp((int) ((event.x() - bounds.x()) / bounds.width() * mode.getModes().length), 0, mode.getModes().length - 1));
        } else {
            updateNumber(event.x(), event.modifiers());
        }
        return true;
    }

    public void mouseReleased(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !dragging) return;
        dragging = false;
        if (pendingNumber != null) {
            double value = pendingNumber;
            pendingNumber = null;
            applyNumber(value);
        }
        if (setting instanceof EnumSetting<?> mode && !enumDragged && bounds.contains(event.x(), event.y())) {
            setMode(mode, (mode.getModeIndex() + 1) % mode.getModes().length);
        }
    }

    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            listening = false;
            if (!event.isEscape())
                setBind(event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE ? KeybindUtils.NONE : event.key());
            return true;
        }
        if (!isEditing()) return false;
        if (event.isEscape() || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            finishEditing(!event.isEscape());
        } else if (input.keyPressed(event)) {
            previewText();
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!isEditing()) return false;
        if (input.charTyped(event)) previewText();
        return true;
    }

    public boolean captureMouseBind(MouseButtonEvent event) {
        if (!listening) return false;
        listening = false;
        setBind(KeybindUtils.encodeMouseButton(event.button()));
        return true;
    }

    public void listenForBind() {
        listening = true;
    }

    public void resetInput() {
        listening = false;
        dragging = false;
        if (pendingNumber != null) {
            double value = pendingNumber;
            pendingNumber = null;
            applyNumber(value);
        }
        finishEditing(true);
    }

    private boolean isEditing() {
        return input != null && input.isFocused();
    }

    private void startEditing() {
        if (isEditing()) return;
        originalText = setting instanceof StringSetting text ? text.getValue() : valueText();
        input = new DropdownTextField(Math.max(256, originalText.length()));
        input.setText(originalText);
        input.focus();
    }

    private void finishEditing(boolean commit) {
        if (!isEditing()) return;
        String text = input.getText();
        input.blur();
        if (setting instanceof StringSetting value) {
            String next = commit ? text : originalText;
            if (!Objects.equals(value.getValue(), next)) value.setValue(next);
        } else if (commit) {
            try {
                double value = Double.parseDouble(text);
                if (Double.isFinite(value)) applyNumber(Math.clamp(value, minimum(), maximum()));
            } catch (NumberFormatException exception) {
                // 输入期间允许小数点和负号；未完成的数字保持原值。
                Constants.LOGGER.debug("Troll GUI 数值输入无效: {} = {}", setting.getName(), text, exception);
            }
        }
    }

    private void previewText() {
        if (setting instanceof StringSetting text && !text.isApplyWhenRelease() && !Objects.equals(text.getValue(), input.getText())) {
            text.setValue(input.getText());
        }
    }

    private void setBind(int bind) {
        if (module != null) module.setKeyBind(bind);
        else if (setting instanceof KeybindSetting keybind) keybind.setValue(bind);
    }

    private void setMode(EnumSetting<?> mode, int index) {
        Enum<?> next = mode.getModes()[index];
        if (!mode.isModeSupportedUnchecked(next)) PlatformNoticeScreen.showOption(mode, next);
        else if (mode.getValue() != next) mode.setMode(next.name());
    }

    private double numberValue() {
        return pendingNumber != null ? pendingNumber : ((Number) setting.getValue()).doubleValue();
    }

    private String formatNumber(double value) {
        return Double.isFinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : Double.toString(value);
    }

    private double minimum() {
        return setting instanceof IntSetting value ? value.getMin() : ((DoubleSetting) setting).getMin();
    }

    private double maximum() {
        return setting instanceof IntSetting value ? value.getMax() : ((DoubleSetting) setting).getMax();
    }

    private void updateNumber(double mouseX, int modifiers) {
        double step = setting instanceof IntSetting value ? value.getStep() : ((DoubleSetting) setting).getStep();
        double fraction = Math.clamp((mouseX - bounds.x()) / Math.max(1.0f, bounds.width()), 0.0, 1.0);
        if ((modifiers & InputConstants.MOD_ALT) != 0) {
            fraction = Math.clamp((numberValue() - minimum() + (mouseX - dragStartX) * (maximum() - minimum()) / bounds.width() * 0.1) / Math.max(0.000001, maximum() - minimum()), 0.0, 1.0);
            if (setting instanceof DoubleSetting) step *= 0.1;
        }
        dragStartX = mouseX;
        double value = minimum() + Math.round(fraction * (maximum() - minimum()) / Math.max(0.000001, step)) * step;
        value = Math.clamp(value, minimum(), maximum());
        if (setting.isApplyWhenRelease()) pendingNumber = value;
        else applyNumber(value);
    }

    private void applyNumber(double value) {
        if (setting instanceof IntSetting number && number.getValue() != (int) Math.round(value))
            number.setValue((int) Math.round(value));
        else if (setting instanceof DoubleSetting number && Double.compare(number.getValue(), value) != 0)
            number.setValue(value);
    }

    private float target() {
        if (action != null) return switch (action) {
            case TOGGLE -> module.isEnabled() ? 1.0f : 0.0f;
            case BIND -> listening ? 1.0f : 0.0f;
            case HIDDEN -> module.isHidden() ? 1.0f : 0.0f;
        };
        return switch (setting) {
            case BoolSetting value -> value.getValue() ? 1.0f : 0.0f;
            case IntSetting value ->
                    (float) Math.clamp((numberValue() - minimum()) / Math.max(1.0, maximum() - minimum()), 0.0, 1.0);
            case DoubleSetting value ->
                    (float) Math.clamp((numberValue() - minimum()) / Math.max(0.000001, maximum() - minimum()), 0.0, 1.0);
            case EnumSetting<?> value -> value.getModeIndex() / (float) Math.max(1, value.getModes().length - 1);
            case StringSetting value -> isEditing() ? 0.0f : 1.0f;
            case ColorSetting value -> 0.0f;
            case null, default -> 0.0f;
        };
    }

}
