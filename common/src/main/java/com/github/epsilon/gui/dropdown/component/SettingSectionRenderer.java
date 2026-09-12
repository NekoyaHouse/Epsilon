package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.dropdown.widget.ColorWidget;
import com.github.epsilon.gui.dropdown.widget.DoubleSliderWidget;
import com.github.epsilon.gui.dropdown.widget.IntSliderWidget;
import com.github.epsilon.gui.dropdown.widget.KeybindWidget;
import com.github.epsilon.gui.dropdown.widget.SettingWidget;
import com.github.epsilon.gui.dropdown.widget.StringWidget;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Dropdown 侧 Setting section 树的共用渲染器。
 * <p>
 * 负责把 {@link SettingLayoutPlanner.Section} 树包装成控件树，并统一处理嵌套分组的
 * 高度、卡片背景、缩进、展开动画、命中与输入分发；{@code SettingsContent} 与
 * {@code ModuleButton} 只提供控件工厂和坐标原点。
 * <p>
 * 绘制坐标使用调用方 scope 的局部坐标，命中测试使用「局部坐标 + hitOffset」得到的绝对坐标，
 * 因此控件缓存的绝对位置可以直接参与命中。
 */
public final class SettingSectionRenderer {

    private final List<Node> nodes;
    private final Map<String, Animation> hoverAnimations = new HashMap<>();
    private final Map<String, Animation> expandAnimations = new HashMap<>();

    private SettingSectionRenderer(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    /**
     * 按 owner key 构建 section 树。
     *
     * @param ownerKey      section key 前缀，用于区分同一帧内的不同宿主
     * @param settings      宿主 Setting 列表
     * @param widgetFactory Setting 到 Dropdown 控件的工厂，返回 {@code null} 表示该 Setting 不参与渲染
     */
    public static SettingSectionRenderer create(String ownerKey, List<Setting<?>> settings,
                                                Function<Setting<?>, SettingWidget<?>> widgetFactory) {
        List<Node> nodes = new ArrayList<>();
        for (SettingLayoutPlanner.Section section : SettingLayoutPlanner.plan(ownerKey, settings)) {
            Node node = buildNode(section, widgetFactory);
            if (node != null) {
                nodes.add(node);
            }
        }
        return new SettingSectionRenderer(nodes);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * 返回所有 section 的高度之和（不含宿主自行添加的起始间距）。
     */
    public float height() {
        float height = 0.0f;
        for (Node node : nodes) {
            height += nodeHeight(node);
        }
        return height;
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY,
                     float x, float y, float width, float hitOffsetX, float hitOffsetY) {
        float sectionX = x + DropdownTheme.SETTING_INDENT;
        float sectionWidth = sectionWidth(width);
        float cursor = y;
        for (Node node : nodes) {
            drawNode(scope, textMetrics, mouseX, mouseY, node, 0, sectionX, cursor, sectionWidth, hitOffsetX, hitOffsetY);
            cursor += nodeHeight(node);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                float x, float y, float width, float hitOffsetX, float hitOffsetY) {
        float sectionX = x + DropdownTheme.SETTING_INDENT + hitOffsetX;
        float sectionWidth = sectionWidth(width);
        float cursor = y + hitOffsetY;
        for (Node node : nodes) {
            if (clickNode(node, 0, sectionX, cursor, sectionWidth, mouseX, mouseY, button)) {
                return true;
            }
            cursor += nodeHeight(node);
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return anyWidget(widget -> widget.mouseReleased(mouseX, mouseY, button));
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return anyWidget(widget -> widget.keyPressed(keyCode, scanCode, modifiers));
    }

    public boolean charTyped(String typedText) {
        return anyWidget(widget -> widget.charTyped(typedText));
    }

    public boolean hasActiveInput() {
        return hasListeningKeybind() || hasFocusedInput();
    }

    public boolean hasListeningKeybind() {
        return anyWidget(widget -> widget instanceof KeybindWidget keybindWidget && keybindWidget.isListening(), false);
    }

    public boolean hasFocusedInput() {
        return anyWidget(SettingSectionRenderer::isFocusedInput, false);
    }

    public void blurAllInputs() {
        for (Node node : nodes) {
            forEachWidget(node, SettingSectionRenderer::blurInput);
        }
    }

    // --- 结构构建 ---

    private static Node buildNode(SettingLayoutPlanner.Section section,
                                  Function<Setting<?>, SettingWidget<?>> widgetFactory) {
        List<Element> elements = new ArrayList<>();
        for (SettingLayoutPlanner.Section.Element element : section.elements()) {
            switch (element) {
                case SettingLayoutPlanner.Section.SettingElement settingElement -> {
                    SettingWidget<?> widget = widgetFactory.apply(settingElement.setting());
                    if (widget == null) {
                        continue;
                    }
                    elements.add(new WidgetElement(widget));
                }
                case SettingLayoutPlanner.Section.GroupElement groupElement -> {
                    Node child = buildNode(groupElement.section(), widgetFactory);
                    if (child == null) {
                        continue;
                    }
                    elements.add(new ChildElement(child));
                }
            }
        }
        if (elements.isEmpty()) {
            return null;
        }
        return new Node(section, elements);
    }

    // --- 高度 ---

    private float nodeHeight(Node node) {
        if (!node.hasHeader()) {
            return elementsHeight(node);
        }
        float contentHeight = DropdownTheme.GROUP_INSET + elementsHeight(node);
        return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP + contentHeight * expandProgress(node);
    }

    private float elementsHeight(Node node) {
        float height = 0.0f;
        for (Element element : node.elements) {
            switch (element) {
                case WidgetElement widgetElement -> {
                    SettingWidget<?> widget = widgetElement.widget();
                    if (widget.isVisible()) {
                        height += widget.getHeight() + DropdownTheme.SETTING_GAP;
                    }
                }
                case ChildElement childElement -> height += nodeHeight(childElement.node());
            }
        }
        return height;
    }

    // --- 绘制 ---

    private void drawNode(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY,
                          Node node, int depth, float x, float y, float width,
                          float hitOffsetX, float hitOffsetY) {
        if (!node.hasHeader()) {
            drawElements(scope, textMetrics, mouseX, mouseY, node, depth, x, y, width, hitOffsetX, hitOffsetY);
            return;
        }

        Card cardBounds = card(node, depth, x, width);
        float progress = expandProgress(node);
        float contentHeight = DropdownTheme.GROUP_INSET + elementsHeight(node);
        float cardHeight = DropdownTheme.GROUP_HEADER_HEIGHT
                + (DropdownTheme.SETTING_GAP + contentHeight) * progress;
        float radius = DropdownTheme.groupCardRadius(progress);

        scope.roundRect(cardBounds.x(), y, cardBounds.width(), cardHeight, radius, DropdownTheme.groupCardBackground(depth));
        scope.outline(cardBounds.x(), y, cardBounds.width(), cardHeight, radius, 0.8f, DropdownTheme.groupCardOutline(depth));

        Animation hoverAnimation = hoverAnimations.computeIfAbsent(node.key(), key -> createAnimation(DropdownTheme.ANIM_HOVER, 0.0f));
        boolean hovered = isHovered(mouseX, mouseY, cardBounds.x() + hitOffsetX, y + hitOffsetY,
                cardBounds.width(), DropdownTheme.GROUP_HEADER_HEIGHT);
        hoverAnimation.run(hovered ? 1.0f : 0.0f);
        float hoverProgress = hoverAnimation.getValue();
        if (hoverProgress > 0.01f) {
            scope.roundRect(cardBounds.x(), y, cardBounds.width(), DropdownTheme.GROUP_HEADER_HEIGHT, radius,
                    DropdownTheme.groupHeaderHover(hoverProgress));
        }

        String label = trimToWidth(node.title(), DropdownTheme.GROUP_HEADER_TEXT_SCALE,
                cardBounds.width() - 74.0f, textMetrics);
        float labelY = y + (DropdownTheme.GROUP_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.GROUP_HEADER_TEXT_SCALE)) * 0.5f;
        scope.text(label, cardBounds.x() + DropdownTheme.SETTING_PADDING_X, labelY,
                DropdownTheme.GROUP_HEADER_TEXT_SCALE, DropdownTheme.groupText());

        String countLabel = Integer.toString(node.totalSettingCount());
        float countWidth = textMetrics.textWidth(countLabel, DropdownTheme.GROUP_COUNT_TEXT_SCALE)
                + DropdownTheme.GROUP_COUNT_CHIP_PADDING * 2.0f;
        float countX = cardBounds.x() + cardBounds.width() - DropdownTheme.SETTING_PADDING_X - countWidth - 12.0f;
        float chipHeight = DropdownTheme.GROUP_COUNT_CHIP_HEIGHT;
        float countY = y + (DropdownTheme.GROUP_HEADER_HEIGHT - chipHeight) * 0.5f;
        scope.roundRect(countX, countY, countWidth, chipHeight, chipHeight / 2.0f, DropdownTheme.groupCountChip());
        float countTextY = countY + (chipHeight - textMetrics.textHeight(DropdownTheme.GROUP_COUNT_TEXT_SCALE)) * 0.5f;
        scope.text(countLabel, countX + DropdownTheme.GROUP_COUNT_CHIP_PADDING, countTextY,
                DropdownTheme.GROUP_COUNT_TEXT_SCALE, DropdownTheme.groupCountText());

        float chevronSize = 2.5f;
        scope.triangle(cardBounds.x() + cardBounds.width() - DropdownTheme.SETTING_PADDING_X - chevronSize,
                y + DropdownTheme.GROUP_HEADER_HEIGHT * 0.5f, chevronSize, progress,
                DropdownTheme.groupChevron(hoverProgress));

        if (progress > 0.001f) {
            float contentY = y + DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP;
            scope.scissorIf(progress < 1.0f, cardBounds.x(), contentY, cardBounds.width(),
                    contentHeight * progress + DropdownTheme.SETTING_GAP,
                    clippedScope -> {
                        float innerX = cardBounds.x() + DropdownTheme.GROUP_INSET;
                        float innerWidth = Math.max(1.0f, cardBounds.width() - DropdownTheme.GROUP_INSET * 2.0f);
                        drawElements(clippedScope, textMetrics, mouseX, mouseY, node, depth + 1, innerX,
                                contentY + DropdownTheme.GROUP_INSET, innerWidth, hitOffsetX, hitOffsetY);
                    });
        }
    }

    private void drawElements(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY,
                              Node node, int depth, float x, float y, float width,
                              float hitOffsetX, float hitOffsetY) {
        float cursor = y;
        for (Element element : node.elements) {
            switch (element) {
                case WidgetElement widgetElement -> {
                    SettingWidget<?> widget = widgetElement.widget();
                    if (!widget.isVisible()) {
                        continue;
                    }
                    // 控件位置必须使用绝对坐标：UiTree.pushAbsolute 不会叠加当前 bound。
                    UiRect bounds = new UiRect(x + hitOffsetX, cursor + hitOffsetY, width, widget.getHeight());
                    scope.pushAbsolute(bounds, itemScope -> widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds));
                    cursor += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
                case ChildElement childElement -> {
                    Node child = childElement.node();
                    drawNode(scope, textMetrics, mouseX, mouseY, child, depth, x, cursor, width, hitOffsetX, hitOffsetY);
                    cursor += nodeHeight(child);
                }
            }
        }
    }

    // --- 命中 ---

    private boolean clickNode(Node node, int depth, float x, float y, float width,
                              double mouseX, double mouseY, int button) {
        if (!node.hasHeader()) {
            return clickElements(node, depth, x, y, width, mouseX, mouseY, button);
        }

        Card cardBounds = card(node, depth, x, width);
        if (isHovered(mouseX, mouseY, cardBounds.x(), y, cardBounds.width(), DropdownTheme.GROUP_HEADER_HEIGHT)) {
            toggleCollapsed(node);
            return true;
        }
        if (expandProgress(node) < 0.999f) {
            return false;
        }

        float contentY = y + DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP;
        float innerX = cardBounds.x() + DropdownTheme.GROUP_INSET;
        float innerWidth = Math.max(1.0f, cardBounds.width() - DropdownTheme.GROUP_INSET * 2.0f);
        return clickElements(node, depth + 1, innerX, contentY + DropdownTheme.GROUP_INSET, innerWidth,
                mouseX, mouseY, button);
    }

    private boolean clickElements(Node node, int depth, float x, float y, float width,
                                  double mouseX, double mouseY, int button) {
        float cursor = y;
        for (Element element : node.elements) {
            switch (element) {
                case WidgetElement widgetElement -> {
                    SettingWidget<?> widget = widgetElement.widget();
                    if (!widget.isVisible()) {
                        continue;
                    }
                    if (widget.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    cursor += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
                case ChildElement childElement -> {
                    Node child = childElement.node();
                    if (clickNode(child, depth, x, cursor, width, mouseX, mouseY, button)) {
                        return true;
                    }
                    cursor += nodeHeight(child);
                }
            }
        }
        return false;
    }

    private void toggleCollapsed(Node node) {
        node.toggleCollapsed();
        if (node.isCollapsed()) {
            forEachWidget(node, SettingSectionRenderer::blurInput);
        }
        SoundManager.INSTANCE.playInUi(node.isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
        DropdownScreen.INSTANCE.react(node.isCollapsed()
                ? ReisaDropdownCompanion.Action.PANEL_CLOSE
                : ReisaDropdownCompanion.Action.PANEL_OPEN);
    }

    // --- 控件分发 ---

    private boolean anyWidget(Predicate<SettingWidget<?>> action) {
        return anyWidget(action, true);
    }

    /**
     * @param skipCollapsed 是否跳过折叠分组内的控件；焦点检查需要覆盖所有分组
     */
    private boolean anyWidget(Predicate<SettingWidget<?>> action, boolean skipCollapsed) {
        for (Node node : nodes) {
            if (visitWidget(node, action, skipCollapsed)) {
                return true;
            }
        }
        return false;
    }

    private boolean visitWidget(Node node, Predicate<SettingWidget<?>> action, boolean skipCollapsed) {
        if (skipCollapsed && node.hasHeader() && node.isCollapsed()) {
            return false;
        }
        for (Element element : node.elements) {
            switch (element) {
                case WidgetElement widgetElement -> {
                    SettingWidget<?> widget = widgetElement.widget();
                    if (widget.isVisible() && action.test(widget)) {
                        return true;
                    }
                }
                case ChildElement childElement -> {
                    if (visitWidget(childElement.node(), action, skipCollapsed)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void forEachWidget(Node node, Consumer<SettingWidget<?>> action) {
        for (Element element : node.elements) {
            switch (element) {
                case WidgetElement widgetElement -> action.accept(widgetElement.widget());
                case ChildElement childElement -> forEachWidget(childElement.node(), action);
            }
        }
    }

    private static boolean isFocusedInput(SettingWidget<?> widget) {
        if (widget instanceof StringWidget stringWidget && stringWidget.isFocused()) return true;
        if (widget instanceof IntSliderWidget intWidget && intWidget.isFocused()) return true;
        if (widget instanceof DoubleSliderWidget doubleWidget && doubleWidget.isFocused()) return true;
        return widget instanceof ColorWidget colorWidget && colorWidget.hasFocusedInput();
    }

    private static void blurInput(SettingWidget<?> widget) {
        if (widget instanceof StringWidget stringWidget && stringWidget.isFocused()) {
            stringWidget.blurInput();
        } else if (widget instanceof IntSliderWidget intWidget && intWidget.isFocused()) {
            intWidget.blurInput();
        } else if (widget instanceof DoubleSliderWidget doubleWidget && doubleWidget.isFocused()) {
            doubleWidget.blurInput();
        } else if (widget instanceof ColorWidget colorWidget && colorWidget.hasFocusedInput()) {
            colorWidget.blurAllInputs();
        }
    }

    // --- 布局工具 ---

    private float expandProgress(Node node) {
        Animation animation = expandAnimations.computeIfAbsent(node.key(),
                key -> createAnimation(DropdownTheme.ANIM_GROUP, node.isCollapsed() ? 0.0f : 1.0f));
        animation.run(node.isCollapsed() ? 0.0f : 1.0f);
        return animation.getValue();
    }

    private static Animation createAnimation(long duration, float startValue) {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, duration);
        animation.setStartValue(startValue);
        return animation;
    }

    private static float sectionWidth(float width) {
        return Math.max(1.0f, width - DropdownTheme.SETTING_INDENT * 2.0f);
    }

    private static Card card(Node node, int depth, float x, float width) {
        if (!node.hasHeader()) {
            return new Card(x, width);
        }
        float nest = DropdownTheme.groupNestInset(depth, width);
        return new Card(x + nest, Math.max(1.0f, width - nest * 2.0f));
    }

    private static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String trimToWidth(String value, float scale, float maxWidth, UiTextMetrics textMetrics) {
        if (value == null || value.isEmpty() || maxWidth <= 0.0f) return "";
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = textMetrics.textWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    private record Card(float x, float width) {
    }

    private sealed interface Element permits WidgetElement, ChildElement {
    }

    private record WidgetElement(SettingWidget<?> widget) implements Element {
    }

    private record ChildElement(Node node) implements Element {
    }

    private static final class Node {

        private final SettingLayoutPlanner.Section model;
        private final List<Element> elements;

        private Node(SettingLayoutPlanner.Section model, List<Element> elements) {
            this.model = model;
            this.elements = List.copyOf(elements);
        }

        private String key() {
            return model.key();
        }

        private String title() {
            return model.title();
        }

        private boolean hasHeader() {
            return model.hasHeader();
        }

        private boolean isCollapsed() {
            return model.isCollapsed();
        }

        private void toggleCollapsed() {
            model.toggleCollapsed();
        }

        private int totalSettingCount() {
            return model.totalSettingCount();
        }
    }

}
