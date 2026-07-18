package com.github.epsilon.gui.lib;

import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.schedulers.render2d.Render2DTexture;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.render.LuminUiRenderer;
import com.github.epsilon.gui.lib.render.UiContentBuffer;
import com.github.epsilon.utils.render.animation.Animation;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 与具体 Screen 无关的声明式 UI 节点树。
 * <p>
 * 调用方通过 {@link #build(Consumer)} 在一个 {@link Scope} 中描述本帧需要绘制的内容，
 * 然后再由 {@link LuminUiRenderer} 将树结构编译进调度器命令或视口缓冲。
 */
public final class UiTree {

    private final List<UiNode> nodes;
    private final boolean hasActiveAnimations;

    private UiTree(List<UiNode> nodes, boolean hasActiveAnimations) {
        this.nodes = nodes;
        this.hasActiveAnimations = hasActiveAnimations;
    }

    /**
     * 构建一棵新的 UI 树。
     *
     * @param content 用于向 {@link Scope} 写入节点的 DSL 构建函数
     * @return 当前帧的 UI 树快照
     */
    public static UiTree build(Consumer<Scope> content) {
        Scope scope = new Scope();
        content.accept(scope);
        return new UiTree(List.copyOf(scope.nodes), scope.hasActiveAnimations);
    }

    public static UiTree from(Scope scope) {
        return scope.snapshot();
    }

    /**
     * 构建带自动布局的 UI 树。
     * <p>
     * 新 GUI 优先通过该入口描述 row/column/slot，布局作用域会自动推进游标并把结果写入底层绘制节点。
     */
    public static UiTree layout(UiRect bounds, Consumer<LayoutScope> content) {
        Scope scope = new Scope();
        content.accept(new LayoutScope(scope, bounds));
        return new UiTree(List.copyOf(scope.nodes), scope.hasActiveAnimations);
    }

    public List<UiNode> nodes() {
        return nodes;
    }

    /**
     * 返回该树是否仍包含未结束的动画。
     * <p>
     * 该标记通常被面板层用于决定是否需要继续触发重绘。
     *
     * @return 若仍有活动动画则为 {@code true}
     */
    public boolean hasActiveAnimations() {
        return hasActiveAnimations;
    }

    /**
     * UI DSL 的构建作用域。
     * <p>
     * 作用域负责收集节点、传播动画活动状态，并在需要时把子树包装成 layer 或 viewport。
     */
    public static final class Scope {

        private List<UiNode> nodes = new ArrayList<>();
        private boolean hasActiveAnimations;
        private final List<UiRect> boundStack = new ArrayList<>(List.of(new UiRect(0.0f, 0.0f, 0.0f, 0.0f)));

        /**
         * 向当前作用域追加单个节点，并在需要时绑定到相对 layer。
         * <p>
         * 高频绘制上下文会大量调用单节点重载；这里直接包装 {@link LayeredNode}，
         * 避免为了一个图元额外创建临时 {@link Scope} 和子列表。
         */
        private void addNode(int layer, UiNode node) {
            if (layer == 0) {
                nodes.add(node);
            } else {
                nodes.add(new LayeredNode(layer, node));
            }
        }

        /**
         * 生成当前作用域内容的不可变树快照。
         * <p>
         * 长生命周期绘制上下文会复用同一个 {@link Scope} 收集整帧内容，
         * 快照用于把当帧节点稳定地交给批次编译。
         */
        public UiTree snapshot() {
            return new UiTree(List.copyOf(nodes), hasActiveAnimations);
        }

        /**
         * 清空当前作用域，便于长生命周期的渲染门面复用。
         */
        public void clear() {
            nodes.clear();
            hasActiveAnimations = false;
            boundStack.clear();
            boundStack.add(new UiRect(0.0f, 0.0f, 0.0f, 0.0f));
        }

        public UiRect bound() {
            return currentBound();
        }

        public Stack stack(UiRect bounds) {
            return new Stack(resolveRect(bounds));
        }

        public Stack stack(float x, float y, float width, float height) {
            return stack(new UiRect(x, y, width, height));
        }

        public void push(UiRect bounds, Consumer<Scope> content) {
            pushRelative(bounds, content);
        }

        public void push(float x, float y, Consumer<Scope> content) {
            pushRelative(x, y, content);
        }

        public void pushRelative(UiRect bounds, Consumer<Scope> content) {
            withBound(resolveRect(bounds), content);
        }

        public void pushRelative(float x, float y, Consumer<Scope> content) {
            UiRect current = currentBound();
            withBound(new UiRect(current.x() + x, current.y() + y, 0.0f, 0.0f), content);
        }

        public void pushAbsolute(UiRect bounds, Consumer<Scope> content) {
            withBound(bounds, content);
        }

        public void pushAbsolute(float x, float y, Consumer<Scope> content) {
            withBound(new UiRect(x, y, 0.0f, 0.0f), content);
        }

        public void stackPush(UiRect bounds) {
            stackPushRelative(bounds);
        }

        public void stackPush(float x, float y) {
            stackPushRelative(x, y);
        }

        public void stackPushRelative(UiRect bounds) {
            boundStack.add(resolveRect(bounds));
        }

        public void stackPushRelative(float x, float y) {
            UiRect current = currentBound();
            boundStack.add(new UiRect(current.x() + x, current.y() + y, 0.0f, 0.0f));
        }

        public void stackPushAbsolute(UiRect bounds) {
            boundStack.add(bounds);
        }

        public void stackPushAbsolute(float x, float y) {
            boundStack.add(new UiRect(x, y, 0.0f, 0.0f));
        }

        public void stackPop() {
            if (boundStack.size() <= 1) {
                throw new IllegalStateException("Cannot pop the root UiTree bound.");
            }
            boundStack.removeLast();
        }

        private void withBound(UiRect bounds, Consumer<Scope> content) {
            boundStack.add(bounds);
            try {
                content.accept(this);
            } finally {
                boundStack.removeLast();
            }
        }

        private UiRect currentBound() {
            return boundStack.getLast();
        }

        private float resolveX(float x) {
            return currentBound().x() + x;
        }

        private float resolveY(float y) {
            return currentBound().y() + y;
        }

        private UiRect resolveRect(UiRect rect) {
            return new UiRect(resolveX(rect.x()), resolveY(rect.y()), rect.width(), rect.height());
        }

        private ButtonElement resolveButton(ButtonElement element) {
            return new ButtonElement(resolveRect(element.bounds()), element.radius(), element.background(),
                    element.label(), element.labelScale(), element.labelColor());
        }

        private SwitchElement resolveSwitch(SwitchElement element) {
            return new SwitchElement(resolveRect(element.bounds()), element.toggleProgress(), element.hoverProgress());
        }

        private InputElement resolveInput(InputElement element) {
            return new InputElement(resolveRect(element.bounds()), element.focused(), element.hoverProgress(),
                    element.focusRingProgress(), element.focusRingColor(), element.focusRingInset(),
                    element.textInset(), element.text(), element.textScale(), element.textColor(),
                    element.selection(), element.selectionColor(),
                    element.caretIndex(), element.caretColor(),
                    element.trailingHint(), element.trailingHintScale(), element.trailingHintColor());
        }

        /**
         * 在一个相对层级中构建子树。
         * <p>
         * 层级只影响支持 layer 的批次输出；旧的直接编译入口会把它当作普通分组处理。
         *
         * @param layer   相对层级偏移，数值越大越靠上
         * @param content 子层级内容
         */
        public void layer(int layer, Consumer<Scope> content) {
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new LayerNode(layer, capture.nodes()));
        }

        public void scissor(UiRect clip, Consumer<Scope> content) {
            UiRect resolvedClip = resolveRect(clip);
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new ScissorNode(resolvedClip, capture.nodes()));
        }

        public void scissor(float x, float y, float width, float height, Consumer<Scope> content) {
            scissor(new UiRect(x, y, width, height), content);
        }

        public void scissorIf(boolean required, UiRect clip, Consumer<Scope> content) {
            if (required) {
                scissor(clip, content);
            } else {
                content.accept(this);
            }
        }

        public void scissorIf(boolean required, float x, float y, float width, float height,
                              Consumer<Scope> content) {
            scissorIf(required, new UiRect(x, y, width, height), content);
        }

        /**
         * 推进一个布尔目标动画，并返回当前值。
         *
         * @param animation 动画实例
         * @param target    目标布尔状态
         * @return 动画当前值，通常在 {@code 0..1} 之间
         */
        public float animate(Animation animation, boolean target) {
            return animate(animation, target ? 1.0f : 0.0f);
        }

        /**
         * 推进一个浮点目标动画，并返回当前值。
         *
         * @param animation 动画实例
         * @param target    目标值
         * @return 动画当前值
         */
        public float animate(Animation animation, float target) {
            animation.run(target);
            hasActiveAnimations = hasActiveAnimations || !animation.isFinished();
            return animation.getValue();
        }

        public void shadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            nodes.add(new ShadowNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, blurRadius, color));
        }

        public void shadow(int layer, float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            addNode(layer, new ShadowNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, blurRadius, color));
        }

        public void shadow(float x, float y, float width, float height,
                           float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                           float blurRadius, Color color) {
            nodes.add(new ShadowNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color));
        }

        public void shadow(int layer, float x, float y, float width, float height,
                           float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                           float blurRadius, Color color) {
            addNode(layer, new ShadowNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color));
        }

        public void roundRect(float x, float y, float width, float height, float radius, Color color) {
            nodes.add(new RoundRectNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, color));
        }

        public void roundRect(int layer, float x, float y, float width, float height, float radius, Color color) {
            addNode(layer, new RoundRectNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, color));
        }

        public void roundRect(float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                              Color color) {
            nodes.add(new RoundRectNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color));
        }

        public void roundRect(int layer, float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                              Color color) {
            addNode(layer, new RoundRectNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color));
        }

        public void roundRectGradient(float x, float y, float width, float height, float radius,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            roundRectGradient(x, y, width, height, radius, radius, radius, radius, topLeft, bottomLeft, bottomRight, topRight);
        }

        public void roundRectGradient(float x, float y, float width, float height,
                                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            nodes.add(new RoundRectGradientNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void roundRectGradient(int layer, float x, float y, float width, float height,
                                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            addNode(layer, new RoundRectGradientNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void roundRectVerticalGradient(float x, float y, float width, float height, float radius, Color top, Color bottom) {
            roundRectGradient(x, y, width, height, radius, top, bottom, bottom, top);
        }

        public void roundRectHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
            roundRectGradient(x, y, width, height, radius, left, left, right, right);
        }

        public void roundRectHorizontalGradient(int layer, float x, float y, float width, float height, float radius, Color left, Color right) {
            roundRectGradient(layer, x, y, width, height, radius, radius, radius, radius, left, left, right, right);
        }

        public void rect(float x, float y, float width, float height, Color color) {
            nodes.add(new RectNode(resolveX(x), resolveY(y), width, height, color));
        }

        public void rect(int layer, float x, float y, float width, float height, Color color) {
            addNode(layer, new RectNode(resolveX(x), resolveY(y), width, height, color));
        }

        public void rectOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            nodes.add(new RectOutlineNode(resolveX(x), resolveY(y), width, height, outlineWidth, color));
        }

        public void rectOutline(int layer, float x, float y, float width, float height, float outlineWidth, Color color) {
            addNode(layer, new RectOutlineNode(resolveX(x), resolveY(y), width, height, outlineWidth, color));
        }

        public void rectGradient(float x, float y, float width, float height,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            nodes.add(new RectGradientNode(resolveX(x), resolveY(y), width, height, topLeft, bottomLeft, bottomRight, topRight));
        }

        public void rectGradient(int layer, float x, float y, float width, float height,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            addNode(layer, new RectGradientNode(resolveX(x), resolveY(y), width, height, topLeft, bottomLeft, bottomRight, topRight));
        }

        public void rectVerticalGradient(float x, float y, float width, float height, Color top, Color bottom) {
            rectGradient(x, y, width, height, top, bottom, bottom, top);
        }

        public void rectHorizontalGradient(float x, float y, float width, float height, Color left, Color right) {
            rectGradient(x, y, width, height, left, left, right, right);
        }

        public void outline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            outline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
        }

        public void outline(float x, float y, float width, float height,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth, Color color) {
            nodes.add(new OutlineNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color));
        }

        public void outline(int layer, float x, float y, float width, float height,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth, Color color) {
            addNode(layer, new OutlineNode(resolveX(x), resolveY(y), width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color));
        }

        public void text(String text, float x, float y, float scale, Color color) {
            nodes.add(new TextNode(text, resolveX(x), resolveY(y), scale, color, null));
        }

        public void text(int layer, String text, float x, float y, float scale, Color color) {
            addNode(layer, new TextNode(text, resolveX(x), resolveY(y), scale, color, null));
        }

        public void text(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            nodes.add(new TextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader));
        }

        public void text(int layer, String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            addNode(layer, new TextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader));
        }

        public void rotatedText(String text, float x, float y, float scale, Color color, float originX, float originY, float rotationDegrees) {
            nodes.add(new RotatedTextNode(text, resolveX(x), resolveY(y), scale, color, null, resolveX(originX), resolveY(originY), rotationDegrees));
        }

        public void rotatedText(int layer, String text, float x, float y, float scale, Color color, float originX, float originY, float rotationDegrees) {
            addNode(layer, new RotatedTextNode(text, resolveX(x), resolveY(y), scale, color, null, resolveX(originX), resolveY(originY), rotationDegrees));
        }

        public void rotatedText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
            nodes.add(new RotatedTextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader, resolveX(originX), resolveY(originY), rotationDegrees));
        }

        public void rotatedText(int layer, String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
            addNode(layer, new RotatedTextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader, resolveX(originX), resolveY(originY), rotationDegrees));
        }

        /**
         * 添加一个带独立裁剪区域的文本节点（跑马灯）。
         * <p>
         * 仅在 viewport 上下文中生效；装入主批次后，会与视口区域取交集后单独以 scissor 输出。
         */
        public void marqueeText(String text, float x, float y, float scale, Color color, UiRect clip) {
            marqueeText(text, x, y, scale, color, null, clip);
        }

        public void marqueeText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, UiRect clip) {
            nodes.add(new MarqueeTextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader, resolveRect(clip)));
        }

        public void texture(Render2DTexture texture, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Color color) {
            roundedTexture(texture, x, y, width, height, 0.0f, u0, v0, u1, v1, color);
        }

        public void texture(Identifier texture, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Color color) {
            texture(texture, x, y, width, height, u0, v0, u1, v1, color, false);
        }

        public void texture(Identifier texture, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            texture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void texture(LuminTexture texture, float x, float y, float width, float height,
                            float u0, float v0, float u1, float v1, Color color) {
            texture(new Render2DTexture.LuminRef(texture), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void rotatedTexture(LuminTexture texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color,
                                   float originX, float originY, float rotationDegrees) {
            nodes.add(new RotatedTextureNode(new Render2DTexture.LuminRef(texture),
                    resolveX(x), resolveY(y), width, height, u0, v0, u1, v1, color,
                    resolveX(originX), resolveY(originY), rotationDegrees));
        }

        public void roundedTexture(Render2DTexture texture, float x, float y, float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, Color color) {
            roundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color);
        }

        public void roundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, Color color) {
            roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
        }

        public void roundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            roundedTexture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void roundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius,
                                   float u0, float v0, float u1, float v1, Color color) {
            roundedTexture(new Render2DTexture.LuminRef(texture), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void roundedTexture(Render2DTexture texture, float x, float y, float width, float height,
                                   float topLeft, float topRight, float bottomRight, float bottomLeft,
                                   float u0, float v0, float u1, float v1, Color color) {
            nodes.add(new TextureNode(texture, resolveX(x), resolveY(y), width, height, topLeft, topRight, bottomRight, bottomLeft, u0, v0, u1, v1, color));
        }

        public void playerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
            roundedTexture(texture, x, y, size, size, radius, 8.0f / 64.0f, 8.0f / 64.0f, 16.0f / 64.0f, 16.0f / 64.0f, color);
            roundedTexture(texture, x, y, size, size, radius, 40.0f / 64.0f, 8.0f / 64.0f, 48.0f / 64.0f, 16.0f / 64.0f, color);
        }

        public void button(float x, float y, float width, float height, float radius, Color background,
                           String label, float labelScale, Color labelColor) {
            button(new ButtonElement(new UiRect(x, y, width, height), radius, background, label, labelScale, labelColor));
        }

        /**
         * 添加一个标准按钮节点。
         *
         * @param bounds     按钮区域
         * @param radius     圆角半径
         * @param background 背景色
         * @param label      文本标签
         * @param labelScale 文本缩放
         * @param labelColor 文本颜色
         */
        public void button(UiRect bounds, float radius, Color background,
                           String label, float labelScale, Color labelColor) {
            button(new ButtonElement(bounds, radius, background, label, labelScale, labelColor));
        }

        public void button(ButtonElement element) {
            ButtonElement resolved = resolveButton(element);
            UiRect bounds = resolved.bounds();
            nodes.add(new ButtonNode(bounds.x(), bounds.y(), bounds.width(), bounds.height(), element.radius(),
                    resolved.background(), resolved.label(), resolved.labelScale(), resolved.labelColor()));
        }

        public void switchControl(UiRect bounds, float toggleProgress, float hoverProgress) {
            toggleSwitch(new SwitchElement(bounds, toggleProgress, hoverProgress));
        }

        /**
         * 添加一个开关控件节点。
         *
         * @param bounds         开关区域
         * @param toggleProgress 开关开启进度
         * @param hoverProgress  悬停高亮进度
         */
        public void toggle(UiRect bounds, float toggleProgress, float hoverProgress) {
            toggleSwitch(new SwitchElement(bounds, toggleProgress, hoverProgress));
        }

        public void toggleSwitch(SwitchElement element) {
            SwitchElement resolved = resolveSwitch(element);
            nodes.add(new SwitchNode(resolved.bounds(), resolved.toggleProgress(), resolved.hoverProgress()));
        }

        public void toggle(SwitchElement element) {
            toggleSwitch(element);
        }

        public void filledField(UiRect bounds, boolean focused, float hoverProgress) {
            input(new InputElement(bounds, focused, hoverProgress,
                    0.0f, new Color(0, 0, 0, 0), 0.0f,
                    6.0f, null, 0.0f, new Color(0, 0, 0, 0),
                    null, null,
                    null, null,
                    null, 0.0f, null));
        }

        /**
         * 添加一个完整输入框节点。
         * <p>
         * 该节点可以携带光标、选区、聚焦环和尾部提示等信息。
         *
         * @param element 输入框描述
         */
        public void input(InputElement element) {
            nodes.add(new InputNode(resolveInput(element)));
        }

        /**
         * 以简化参数形式添加输入框节点。
         *
         * @param bounds            输入框区域
         * @param focused           是否处于焦点状态
         * @param hoverProgress     悬停进度
         * @param textInset         文本左侧内边距
         * @param text              显示文本
         * @param textScale         文本缩放
         * @param textColor         文本颜色
         * @param caretIndex        光标索引，可为空
         * @param caretColor        光标颜色，可为空
         * @param trailingHint      右侧提示文字，可为空
         * @param trailingHintScale 提示文字缩放
         * @param trailingHintColor 提示文字颜色，可为空
         */
        public void input(UiRect bounds, boolean focused, float hoverProgress,
                          float textInset, String text, float textScale, Color textColor,
                          Integer caretIndex, Color caretColor,
                          String trailingHint, float trailingHintScale, Color trailingHintColor) {
            input(new InputElement(bounds, focused, hoverProgress,
                    0.0f, new Color(0, 0, 0, 0), 0.0f,
                    textInset, text, textScale, textColor,
                    null, null,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor));
        }

        /**
         * 以完整参数形式添加输入框节点。
         *
         * @param bounds            输入框区域
         * @param focused           是否处于焦点状态
         * @param hoverProgress     悬停进度
         * @param focusRingProgress 聚焦环进度
         * @param focusRingColor    聚焦环颜色
         * @param focusRingInset    聚焦环向外扩张的距离
         * @param textInset         文本左侧内边距
         * @param text              显示文本
         * @param textScale         文本缩放
         * @param textColor         文本颜色
         * @param selection         文本选区，可为空
         * @param selectionColor    选区颜色，可为空
         * @param caretIndex        光标索引，可为空
         * @param caretColor        光标颜色，可为空
         * @param trailingHint      右侧提示文字，可为空
         * @param trailingHintScale 提示文字缩放
         * @param trailingHintColor 提示文字颜色，可为空
         */
        public void input(UiRect bounds, boolean focused, float hoverProgress,
                          float focusRingProgress, Color focusRingColor, float focusRingInset,
                          float textInset, String text, float textScale, Color textColor,
                          SelectionRange selection, Color selectionColor,
                          Integer caretIndex, Color caretColor,
                          String trailingHint, float trailingHintScale, Color trailingHintColor) {
            input(new InputElement(bounds, focused, hoverProgress,
                    focusRingProgress, focusRingColor, focusRingInset,
                    textInset, text, textScale, textColor,
                    selection, selectionColor,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor));
        }

        public void assistChip(UiRect bounds, String label, float textScale, Color background, Color foreground,
                               String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont) {
            nodes.add(new AssistChipNode(resolveRect(bounds), label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont));
        }

        public void chip(UiRect bounds, String label, float textScale, Color background, Color foreground,
                         String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont) {
            assistChip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont);
        }

        public void segmentedControl(UiRect bounds, String leadingLabel, String trailingLabel,
                                     float progress, float hoverProgress) {
            nodes.add(new SegmentedControlNode(resolveRect(bounds), leadingLabel, trailingLabel, progress, hoverProgress));
        }

        public void segmented(UiRect bounds, String leadingLabel, String trailingLabel,
                              float progress, float hoverProgress) {
            segmentedControl(bounds, leadingLabel, trailingLabel, progress, hoverProgress);
        }

        public void iconButton(UiRect bounds, String label, float scale, Color tone, float hoverProgress) {
            nodes.add(new IconButtonNode(resolveRect(bounds), label, scale, tone, hoverProgress));
        }

        /**
         * 添加一个带阴影的弹窗卡片外壳节点。
         *
         * @param bounds       卡片区域
         * @param radius       圆角半径
         * @param blurRadius   阴影模糊半径
         * @param shadowColor  阴影颜色
         * @param surfaceColor 面颜色
         */
        public void popupCard(UiRect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor) {
            nodes.add(new PopupCardNode(resolveRect(bounds), radius, blurRadius, shadowColor, surfaceColor));
        }

        /**
         * 添加一个滑条节点。
         * <p>
         * 该节点同时描述底轨、激活轨和拖柄，可用于数值设置、颜色通道等场景。
         *
         * @param bounds         轨道区域
         * @param progress       当前进度，通常在 {@code 0..1} 之间
         * @param trackRadius    轨道圆角
         * @param trackColor     底轨颜色
         * @param activeEndInset 激活轨终点内缩
         * @param activeMinWidth 激活轨最小宽度
         * @param activeColor    激活轨颜色
         * @param handleWidth    拖柄宽度
         * @param handleHeight   拖柄高度
         * @param handleRadius   拖柄圆角
         * @param handleColor    拖柄颜色
         */
        public void slider(UiRect bounds, float progress, float trackRadius,
                           Color trackColor, float activeEndInset, float activeMinWidth, Color activeColor,
                           float handleWidth, float handleHeight, float handleRadius, Color handleColor) {
            nodes.add(new SliderNode(resolveRect(bounds), progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor,
                    handleWidth, handleHeight, handleRadius, handleColor));
        }

        /**
         * 添加一个chevron三角箭头节点。
         *
         * @param centerX  三角形中心 X
         * @param centerY  三角形中心 Y
         * @param size     三角形半尺寸
         * @param progress 旋转进度：0 = 指向右，1 = 指向下
         * @param color    三角形颜色
         */
        public void triangle(float centerX, float centerY, float size, float progress, Color color) {
            nodes.add(new TriangleNode(resolveX(centerX), resolveY(centerY), size, progress, color));
        }

        public void triangle(int layer, float centerX, float centerY, float size, float progress, Color color) {
            addNode(layer, new TriangleNode(resolveX(centerX), resolveY(centerY), size, progress, color));
        }

        /**
         * 在独立视口缓冲中构建一个可裁剪子树。
         * <p>
         * 子树会先被编译进 {@link UiContentBuffer}，稍后在统一 flush 阶段按视口裁剪后输出。
         *
         * @param buffer        目标内容缓冲
         * @param viewport      视口区域
         * @param scroll        当前滚动偏移
         * @param maxScroll     最大滚动偏移
         * @param contentHeight 内容总高度
         * @param content       视口内部的子树内容
         */
        public void viewport(UiContentBuffer buffer, UiRect viewport,
                             float scroll, float maxScroll, float contentHeight,
                             Consumer<Scope> content) {
            viewport(buffer, viewport, scroll, maxScroll, contentHeight,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, content);
        }

        public void viewport(UiContentBuffer buffer, UiRect viewport,
                             float scroll, float maxScroll, float contentHeight,
                             int mouseX, int mouseY, Consumer<Scope> content) {
            UiRect resolvedViewport = resolveRect(viewport);
            CaptureResult capture = capture(scope -> scope.pushAbsolute(
                    new UiRect(resolvedViewport.x(), resolvedViewport.y() - scroll,
                            resolvedViewport.width(), contentHeight),
                    content));
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new ViewportNode(buffer, resolvedViewport, scroll, maxScroll, contentHeight,
                    mouseX, mouseY, capture.nodes()));
        }

        private CaptureResult capture(Consumer<Scope> content) {
            List<UiNode> parent = nodes;
            boolean parentAnimations = hasActiveAnimations;
            int parentBoundDepth = boundStack.size();
            // 子树构建期间临时切换收集列表，finally 中恢复父作用域，避免子节点泄露到父层。
            nodes = new ArrayList<>();
            hasActiveAnimations = false;
            try {
                content.accept(this);
                return new CaptureResult(List.copyOf(nodes), hasActiveAnimations);
            } finally {
                nodes = parent;
                hasActiveAnimations = parentAnimations;
                while (boundStack.size() > parentBoundDepth) {
                    boundStack.removeLast();
                }
            }
        }

        /**
         * 当前作用域内的垂直自动布局栈。
         * <p>
         * Stack 会按 item 高度推进游标，并可直接在每个 item 的矩形内下传同一个 {@link Scope}。
         */
        public final class Stack {
            private final UiRect bounds;
            private float cursor;

            private Stack(UiRect bounds) {
                this.bounds = bounds;
                this.cursor = bounds.y();
            }

            public UiRect bounds() {
                return bounds;
            }

            public UiRect item(float height) {
                UiRect rect = new UiRect(bounds.x(), cursor, bounds.width(), Math.max(0.0f, height));
                cursor += Math.max(0.0f, height);
                return rect;
            }

            public UiRect item(float height, float gapAfter) {
                UiRect rect = item(height);
                gap(gapAfter);
                return rect;
            }

            public void item(float height, Consumer<Scope> content) {
                UiRect rect = item(height);
                pushAbsolute(rect, content);
            }

            public void item(float height, float gapAfter, Consumer<Scope> content) {
                UiRect rect = item(height, gapAfter);
                pushAbsolute(rect, content);
            }

            public void item(float height, BiConsumer<UiRect, Scope> content) {
                UiRect rect = item(height);
                pushAbsolute(rect, scope -> content.accept(rect, scope));
            }

            public void item(float height, float gapAfter, BiConsumer<UiRect, Scope> content) {
                UiRect rect = item(height, gapAfter);
                pushAbsolute(rect, scope -> content.accept(rect, scope));
            }

            public void gap(float gap) {
                cursor += Math.max(0.0f, gap);
            }

            public UiRect offset(float xOffset, float yOffset, float widthOffset, float heightOffset) {
                return new UiRect(
                        bounds.x() + xOffset,
                        cursor + yOffset,
                        Math.max(0.0f, bounds.width() + widthOffset),
                        Math.max(0.0f, heightOffset)
                );
            }

            public void offset(float xOffset, float yOffset, float widthOffset, float heightOffset, Consumer<Scope> content) {
                pushAbsolute(offset(xOffset, yOffset, widthOffset, heightOffset), content);
            }

            public float cursor() {
                return cursor;
            }
        }
    }

    /**
     * 自动布局作用域。
     * <p>
     * 该作用域负责在 row/column 中计算子元素位置，GUI 代码只需要声明间距、尺寸和内容。
     */
    public static final class LayoutScope {
        private final Scope draw;
        private final UiRect bounds;

        private LayoutScope(Scope draw, UiRect bounds) {
            this.draw = draw;
            this.bounds = bounds;
        }

        public UiRect bounds() {
            return bounds;
        }

        public Scope draw() {
            return draw;
        }

        public void layer(int layer, Consumer<LayoutScope> content) {
            draw.layer(layer, scope -> content.accept(new LayoutScope(scope, bounds)));
        }

        public void box(Insets insets, Consumer<LayoutScope> content) {
            content.accept(new LayoutScope(draw, insets.apply(bounds)));
        }

        public void column(float gap, Consumer<LinearScope> content) {
            LinearScope linear = new LinearScope(draw, bounds, Axis.VERTICAL, gap);
            content.accept(linear);
        }

        public void row(float gap, Consumer<LinearScope> content) {
            LinearScope linear = new LinearScope(draw, bounds, Axis.HORIZONTAL, gap);
            content.accept(linear);
        }

        public void roundRect(float radius, Color color) {
            draw.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, color);
        }

        public void text(String text, float scale, Color color) {
            float y = bounds.y() + (bounds.height() - drawTextHeight(scale)) / 2.0f;
            draw.text(text, bounds.x(), y, scale, color);
        }

        public void text(String text, float scale, Color color, TtfFontLoader fontLoader) {
            float y = bounds.y() + (bounds.height() - drawTextHeight(scale, fontLoader)) / 2.0f;
            draw.text(text, bounds.x(), y, scale, color, fontLoader);
        }

        private float drawTextHeight(float scale) {
            return 9.0f * 0.27f * scale;
        }

        private float drawTextHeight(float scale, TtfFontLoader fontLoader) {
            return fontLoader.fontFile.fontHeight * 0.27f * scale;
        }
    }

    /**
     * row/column 的线性布局作用域。
     * <p>
     * 固定尺寸和 fill 尺寸可以混用；fill 会使用当前容器尚未消费的剩余空间。
     */
    public static final class LinearScope {
        private final Scope draw;
        private final UiRect bounds;
        private final Axis axis;
        private final float gap;
        private float cursor;

        private LinearScope(Scope draw, UiRect bounds, Axis axis, float gap) {
            this.draw = draw;
            this.bounds = bounds;
            this.axis = axis;
            this.gap = gap;
            this.cursor = axis == Axis.VERTICAL ? bounds.y() : bounds.x();
        }

        public void item(float size, Consumer<LayoutScope> content) {
            UiRect rect = nextRect(Math.max(0.0f, size));
            content.accept(new LayoutScope(draw, rect));
            cursor += size + gap;
        }

        public void fill(Consumer<LayoutScope> content) {
            float end = axis == Axis.VERTICAL ? bounds.bottom() : bounds.right();
            item(Math.max(0.0f, end - cursor), content);
        }

        public void spacer(float size) {
            cursor += Math.max(0.0f, size) + gap;
        }

        private UiRect nextRect(float size) {
            if (axis == Axis.VERTICAL) {
                return new UiRect(bounds.x(), cursor, bounds.width(), size);
            }
            return new UiRect(cursor, bounds.y(), size, bounds.height());
        }
    }

    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    public record Insets(float left, float top, float right, float bottom) {
        public static Insets all(float value) {
            return new Insets(value, value, value, value);
        }

        public static Insets symmetric(float horizontal, float vertical) {
            return new Insets(horizontal, vertical, horizontal, vertical);
        }

        public UiRect apply(UiRect rect) {
            return new UiRect(
                    rect.x() + left,
                    rect.y() + top,
                    Math.max(0.0f, rect.width() - left - right),
                    Math.max(0.0f, rect.height() - top - bottom)
            );
        }
    }

    private record CaptureResult(List<UiNode> nodes, boolean hasActiveAnimations) {
    }


    /**
     * DSL 节点只描述本帧的绘制意图，不持有 renderer 状态。
     * <p>
     * 编译阶段会按节点类型把它们分发到调度器命令或视口缓冲。
     */
    public sealed interface UiNode permits LayerNode, LayeredNode, ScissorNode, ShadowNode, RoundRectNode, RoundRectGradientNode, RectNode, RectGradientNode, RectOutlineNode, OutlineNode, TextNode, RotatedTextNode, MarqueeTextNode, TextureNode, RotatedTextureNode, ButtonNode, SwitchNode, FilledFieldNode, InputNode, AssistChipNode, SegmentedControlNode, IconButtonNode, PopupCardNode, SliderNode, TriangleNode, ViewportNode {
    }

    /**
     * 按钮节点的语义描述。
     */
    public record ButtonElement(UiRect bounds, float radius, Color background,
                                String label, float labelScale, Color labelColor) {
    }

    /**
     * 开关节点的语义描述。
     */
    public record SwitchElement(UiRect bounds, float toggleProgress, float hoverProgress) {
    }

    /**
     * 输入框中文字选区的半开区间描述。
     */
    public record SelectionRange(int start, int end) {
    }

    /**
     * 输入框节点的完整语义描述。
     * <p>
     * 用于承载文本、焦点、选区、光标与尾部提示等输入态信息。
     */
    public record InputElement(UiRect bounds, boolean focused, float hoverProgress,
                               float focusRingProgress, Color focusRingColor, float focusRingInset,
                               float textInset, String text, float textScale, Color textColor,
                               SelectionRange selection, Color selectionColor,
                               Integer caretIndex, Color caretColor,
                               String trailingHint, float trailingHintScale,
                               Color trailingHintColor) {
    }

    /**
     * 子树级 layer 偏移；适合一组节点整体提升或下沉。
     */
    public record LayerNode(int layer, List<UiNode> children) implements UiNode {
    }

    /**
     * 单节点 layer 包装；供高频图元入口使用，减少小列表分配。
     */
    public record LayeredNode(int layer, UiNode child) implements UiNode {
    }

    public record ScissorNode(UiRect clip, List<UiNode> children) implements UiNode {
    }

    public record ShadowNode(float x, float y, float width, float height,
                             float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                             float blurRadius, Color color) implements UiNode {
    }

    public record RoundRectNode(float x, float y, float width, float height,
                                float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                                float radiusBottomLeft,
                                Color color) implements UiNode {
    }

    public record RoundRectGradientNode(float x, float y, float width, float height,
                                        float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                                        float radiusBottomLeft,
                                        Color topLeft, Color bottomLeft, Color bottomRight,
                                        Color topRight) implements UiNode {
    }

    public record RectNode(float x, float y, float width, float height, Color color) implements UiNode {
    }

    public record RectGradientNode(float x, float y, float width, float height,
                                   Color topLeft, Color bottomLeft, Color bottomRight,
                                   Color topRight) implements UiNode {
    }

    public record RectOutlineNode(float x, float y, float width, float height, float outlineWidth,
                                  Color color) implements UiNode {
    }

    public record OutlineNode(float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                              float radiusBottomLeft,
                              float outlineWidth, Color color) implements UiNode {
    }

    public record TextNode(String text, float x, float y, float scale, Color color,
                           TtfFontLoader fontLoader) implements UiNode {
    }

    public record RotatedTextNode(String text, float x, float y, float scale, Color color,
                                  TtfFontLoader fontLoader, float originX, float originY,
                                  float rotationDegrees) implements UiNode {
    }

    public record MarqueeTextNode(String text, float x, float y, float scale, Color color,
                                  TtfFontLoader fontLoader, UiRect clip) implements UiNode {
    }

    public record TextureNode(Render2DTexture texture, float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                              float radiusBottomLeft,
                              float u0, float v0, float u1, float v1, Color color) implements UiNode {
    }

    public record RotatedTextureNode(Render2DTexture texture, float x, float y, float width, float height,
                                     float u0, float v0, float u1, float v1, Color color,
                                     float originX, float originY, float rotationDegrees) implements UiNode {
    }

    public record ButtonNode(float x, float y, float width, float height, float radius, Color background,
                             String label, float labelScale, Color labelColor) implements UiNode {
    }

    public record SwitchNode(UiRect bounds, float toggleProgress, float hoverProgress) implements UiNode {
    }

    public record FilledFieldNode(UiRect bounds, boolean focused, float hoverProgress) implements UiNode {
    }

    public record InputNode(InputElement element) implements UiNode {
    }

    public record AssistChipNode(UiRect bounds, String label, float textScale, Color background, Color foreground,
                                 String trailingIcon, float trailingIconScale,
                                 TtfFontLoader trailingIconFont) implements UiNode {
    }

    public record SegmentedControlNode(UiRect bounds, String leadingLabel, String trailingLabel,
                                       float progress, float hoverProgress) implements UiNode {
    }

    public record IconButtonNode(UiRect bounds, String label, float scale, Color tone,
                                 float hoverProgress) implements UiNode {
    }

    public record PopupCardNode(UiRect bounds, float radius, float blurRadius, Color shadowColor,
                                Color surfaceColor) implements UiNode {
    }

    public record SliderNode(UiRect bounds, float progress, float trackRadius, Color trackColor,
                             float activeEndInset, float activeMinWidth, Color activeColor,
                             float handleWidth, float handleHeight, float handleRadius,
                             Color handleColor) implements UiNode {
    }

    public record TriangleNode(float centerX, float centerY, float size, float progress,
                               Color color) implements UiNode {
    }

    public record ViewportNode(UiContentBuffer buffer, UiRect viewport,
                               float scroll, float maxScroll, float contentHeight,
                               int mouseX, int mouseY, List<UiNode> children) implements UiNode {
    }

}
