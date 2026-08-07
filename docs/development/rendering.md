# 渲染

## Lumin 资源生命周期

GPU renderer、render target、字体 atlas 和 shader 由渲染线程创建和使用。

`RectRenderer.create()`、`TextRenderer.create()` 等工厂会向 `RendererHolder` 注册；Lumin render target 工厂会向 `RenderTargetHolder` 注册。字段需要持有 renderer 时，使用 `Suppliers.memoize(Renderer::create)` 延迟到首次渲染。

不再使用的资源调用 `close()`；全局销毁由对应 Holder 或 Lumin 生命周期处理。

## GUI/HUD 首选路径

Panel、Dropdown、popup 和 HUD chrome 使用声明式流水线：

```text
UiTree / UiTree.Scope
  -> LuminUiRenderer
  -> UiRenderBatch
  -> Render2DScheduler
  -> 合批 renderer
```

一个 Screen 或 HUD 帧共享一个 `UiScene`。调用 `beginFrame()` 后提交各语义 `UiLayer`，最后调用 `endFrame()`。

- 业务 UI 优先构造 `UiTree`。
- `UiRenderBatch.view()`/Scene layer 用于局部层级。
- scissor 和 popup 层级通过共享 scheduler 管理。
- `HudModule.render(DeltaTracker)` 通过 `renderScope()` 提交 UI。
- 原版物品等无法进入 Lumin batch 的内容放在 `renderOverlay(GuiGraphicsExtractor, DeltaTracker)`。
- HUD 尺寸变化调用 `setBounds()`，位置由 anchor/move API 管理。

## 直接 Renderer

部分模块和底层组件仍直接使用：

`RectRenderer`、`RoundRectRenderer`、`RoundRectOutlineRenderer`、`TextureRenderer`、`ShadowRenderer`、`TriangleRenderer`、`TextRenderer`。

```java
private final Supplier<RectRenderer> renderer = Suppliers.memoize(RectRenderer::create);

renderer.get().addRect(x, y, width, height, color);
renderer.get().drawAndClear();
```

`drawAndClear()` 等价于 `draw()` 后 `clear()`。内容长期不变时可只调用 `draw()` 并复用 GPU 数据；内容变化时在下一帧重新填充。

`ShadowRenderer` 与 `UiTree.Scope.shadow(...)` 的 segmented 重载接收：

- `segmentRects`：每段依次为 `x, y, width, height`
- `segmentRadii`
- `segmentCount`

一次最多提交 64 段，所有段按并集生成一份阴影。

## 3D Scheduler

普通方框、边框、面、线和 3D 模糊框提交到 `Render3DScheduler.INSTANCE`。Scheduler 在 `Render3DEvent` priority `-999` 统一 flush 并清空。底层特殊效果才直接使用 `LuminImmediateRenderer`。

## World To Screen

`WorldToScreen` 提供三个公共函数：

- `calcWorld2ScreenRaw(Vec3)`：返回 Lumin Render Scale 坐标系中的屏幕 `x/y`；`z` 是以世界单位表示的视图空间前向深度。
- `calcWorld2Screen(Vec3)`：默认入口；深度小于 `Camera.PROJECTION_Z_NEAR` 时返回 `null`。
- `calcScale(Vec3)`：根据当前投影矩阵和前向深度返回透视 UI 缩放；每世界单位投影为 20 个 Lumin 像素时取 `1.0`。

2D AABB 边界通过投影 8 个顶点并取屏幕空间最小/最大坐标计算。

## 文本与后处理

- 默认字体通过 `StaticFontLoader.defaultFont()` 获取，可随客户端设置切换。
- 固定字体包括 `StaticFontLoader.ICONS`、`JURA_LIGHT`、`OSAKA_CHIPS`。
- `TextRenderer.getWidth/getHeight` 使用与绘制相同的 scale 和 font loader。
- 后处理入口包括 `BlurShader.INSTANCE`、`FXAAShader.INSTANCE`、`FilterShader.INSTANCE` 和 `ShaderHolder`。

帧内 renderer、scheduler 和后处理的强制约束见 [`AGENTS.md`](../../AGENTS.md)。
