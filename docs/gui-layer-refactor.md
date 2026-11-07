# GUI 层（layer）重构方案：Kotlin 扩展函数 + Compose 风格

> **状态**：已完成（Phase 0–4 全部落地，`gui/dropdown`、`gui/panel`、`elements`、`gui/hudeditor`、`gui/overlay` 迁移完毕）
>
> - **影响范围**：[`gui/lib`](../common/src/main/kotlin/com/github/epsilon/gui/lib) 及其全部调用方（`gui/panel`、`gui/dropdown`、`gui/screen`、`gui/troll`、`gui/hud*`、`gui/overlay`）
> - **验证**：`./gradlew :common:compileKotlin :fabric:compileKotlin :neoforge:compileKotlin` 通过
> - **相关约束**：[`AGENTS.md`](../AGENTS.md) 的「GUI、HUD 与渲染约束」与 [`graphics/README.md`](../common/src/main/kotlin/com/github/epsilon/graphics/README.md)

## 实施进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 0 | `LayerOffset`、`UiScope`、`ColumnScope`、`StackScope`、图元/控件扩展、`Content` 入口、`asUiScope` 桥 | ✅ 完成 |
| Phase 1 | `gui/troll/` 的 47 处图元级 layer 调用 → `TrollZ` + `layer {}` | ✅ 完成 |
| Phase 2 | 32 处 `.layer(字面量)` → `MenuZ` / `ReisaZ` / `ReisaDropdownZ` / `DialogZ` / `TrollZ` | ✅ 完成 |
| Phase 3 | Dropdown / Panel / HUD 元素 / HudEditor / overlay 全部改为 `UiScope` 扩展 + 相对坐标 | ✅ 完成 |
| Phase 4 | 删除 21 个 `layer: Int` 重载、`LayeredNode`、`addNode`、死代码 `LayoutScope`/`LinearScope`/`UiTree.layout` | ✅ 完成 |
| 待办 | `gui/screen/**` 与 `gui/troll/**` 的坐标仍未相对化（layer 已迁移） | 📋 未开始 |

### Phase 3 落地后的最终形态

- 容器入口：`renderBatch.Content(bounds) { }` / `scene.Content(bounds, layer, relative) { }`；作用域 `origin = bounds`。
- 定位只有相对入口：`Positioned` / `Box` / `ColumnScope.Item` / `StackScope.Item`。**绝对定位入口 `pushAbsolute` 已删除**，也不再提供 `Place` 之类的替代物。
- 度量：`UiScope.metrics: UiTextMetrics`（不是 `TextRenderer`），等价的 `TextRenderer.asUiTextMetrics()` 适配器用于迁移期。
- 绘制与逻辑分离：`x`/`y`/`width`/`height` 字段、命中测试、anchor 与持久化坐标仍是绝对坐标；只有绘制路径换算成 `local = absolute - origin`。
- HUD 元素：`HudModule.renderWithBatch` 改为 `measure()` → `Content(bounds)` → `drawUi()`，避免用上一帧 bounds 导致锚点延迟。
- 跨包面板接口：`DropdownPanel` 用 `context(scope: UiScope) fun drawBackground()` / `fun drawContent(mouseX, mouseY)`，调用点依赖隐式 `UiScope`。

## 目录

1. [背景与目标](#1-背景与目标)
2. [现状盘点](#2-现状盘点)
3. [设计](#3-设计)
4. [完整示例](#4-完整示例)
5. [迁移方案](#5-迁移方案)
6. [风险与兼容性](#6-风险与兼容性)
7. [验证记录](#7-验证记录)
8. [待决策项](#8-待决策项)
9. [附录 A：原型 API 全量清单](#附录-a-原型-api-全量清单)
10. [附录 B：原型文件](#附录-b-原型文件)
11. [附录 C：实现完成后需同步更新的文档](#附录-c-实现完成后需同步更新的文档)

---

## 1. 背景与目标

### 1.1 问题

当前 GUI 库用 `UiTree.Scope` 的 lambda-receiver DSL 描述绘制，图层（layer）以**裸 `Int` 首参**的形式复制粘贴在每个图元签名上：

```kotlin
scope.rect(1, x, y, w, h, Color(0, 0, 0, 40))          // gui/troll/TrollSettingRow.kt:141
scope.textWithShadow(3, label, x, y, scale, text)       // gui/troll/TrollSettingRow.kt:172
scope.layer(-22) { layer -> ... }                       // gui/screen/MainMenuScreen.kt:1016
```

由此产生三类问题：

| 问题 | 表现 |
|---|---|
| **layer 是参数，不是结构** | 每个图元都要重复携带 `layer: Int` 重载；漏传即静默画错遮挡 |
| **数字无语义** | 同一文件里 `-20` 与 `+20` 含义完全不同；`-60..-19` 无人能读懂 |
| **校验有缺口** | `-99..99` 只在 `UiLayerStack` 校验，多条路径绕过（详见 [2.4](#24-校验缺口)） |

### 1.2 目标

- **唯一来源**：图层只由 `LayerOffset` 表达，图元签名里不再出现 `layer` 参数。
- **Compose 写法**：作用域即接收者（`UiScope`），组件是扩展函数，布局是 `Column/Box/Clip`，命名遵循 Compose API 规范。
- **零语义变更**：渲染结果、批次规划、`Render2DScheduler` 的命令累积方式完全不变。
- **可渐进迁移**：新旧 API 可并存，不需要一次性改完 88 个调用点。

### 1.3 非目标

- 不实现 Compose 的**重组（recomposition）**机制。本库是每帧重建的即时模式；"状态提升"仅指动画进度由调用方作为参数传入。
- 不引入 `Modifier` 体系（留作后续可选阶段，见 [8. 待决策项](#8-待决策项)）。
- 不改 `Render2DScheduler`、`LuminUiRenderer` 的节点编译逻辑（仅删 `LayeredNode` 分支）。

---

## 2. 现状盘点

### 2.1 layer 以裸 Int 出现在每个图元签名里

[`UiTree.kt`](../common/src/main/kotlin/com/github/epsilon/gui/lib/UiTree.kt) 中共 **24 个带 `layer: Int` 首参的函数**：

| 家族 | 函数数 | 明细 |
|---|---|---|
| shadow | 2 | `shadow` ×2 |
| roundRect | 4 | `roundRect` ×2、`roundRectGradient`、`roundRectHorizontalGradient` |
| rect | 3 | `rect`、`rectOutline`、`rectGradient` |
| outline | 1 | `outline` |
| text | 8 | `text` ×2、`textWithShadow` ×2、`blurredText` ×2、`rotatedText` ×2 |
| 其他图元 | 3 | `triangle`、`arc`、`gradientArc` |
| 容器入口 | 2 | `Scope.layer`、`LayoutScope.layer` |
| 私有 helper | 1 | `addNode` |

合计 **21 个图元重载 + 2 个容器入口 + 1 个私有 helper**。每个重载内部都走 `addNode(layer, node)` 包一层 `LayeredNode`。

> 全部 `layer` 均为**首参**，不存在 `relativeLayer` 之类的变体；`LayerNode` / `LayeredNode` 的 `layer` 是首属性，编译时由 [`LuminUiRenderer.kt:30-46`](../common/src/main/kotlin/com/github/epsilon/gui/lib/render/LuminUiRenderer.kt) 以 `layer + node.layer` 沿树累积。

### 2.2 散落的魔数

`.layer(字面量)` 共 **32 处调用**：

| 文件 | 处数 | 字面量 |
|---|---|---|
| [`MainMenuScreen.kt`](../common/src/main/kotlin/com/github/epsilon/gui/screen/MainMenuScreen.kt) | 24 | `-60 -50 -40 -30 -24 -23 -22 -21 -20 -19 0 1 10 20 21` |
| [`ReisaDropdownCompanion.kt`](../common/src/main/kotlin/com/github/epsilon/gui/dropdown/ReisaDropdownCompanion.kt) | 3 | `-3 -2 -1` |
| [`EpsilonDialogScreen.kt`](../common/src/main/kotlin/com/github/epsilon/gui/screen/EpsilonDialogScreen.kt) | 2 | `0 10` |
| [`AccountsScreen.kt`](../common/src/main/kotlin/com/github/epsilon/gui/screen/accounts/AccountsScreen.kt) | 2 | `0 10` |
| [`TrollSettingRow.kt`](../common/src/main/kotlin/com/github/epsilon/gui/troll/TrollSettingRow.kt) | 1 | `3` |

语义分组（现状推断）：

- `0/1`：Columbina 视频背景与渐变遮罩
- `-50`×3：标题 / 菜单行 / 页脚发光文字 pass
- `-60..-19`：Reisa 立绘 z 序栈（`-60` 透明贴图预热、`-40` 遮罩、`-24` 光晕、`-23` 底线、`-22` 拖影、`-21` 本体、`-20` 姿态切片、`-19` 折边）
- `20/21`：气泡外壳与气泡文字
- `0/10`：卡片外壳 vs 按钮/内容

### 2.3 图元级 layer 重载仍在使用

**47 处**图元级 `layer` 重载调用，全部集中在 [`gui/troll/`](../common/src/main/kotlin/com/github/epsilon/gui/troll)：

| 图元 | 处数 | 字面量范围 |
|---|---|---|
| `rect` | 20 | 1..5 |
| `textWithShadow` | 20 | 1..5 |
| `rectGradient` | 3 | 1 |
| `rectOutline` | 3 | 2..4 |
| `roundRect` | 1 | 2 |

零调用的重载：`shadow`、`outline`、`text`、`blurredText`、`rotatedText`、`triangle`、`arc`、`gradientArc`、`roundRectGradient`、`roundRectHorizontalGradient`。

### 2.4 校验缺口

`-99..99` 的校验只存在于 [`UiLayerStack.resolve`](../common/src/main/kotlin/com/github/epsilon/gui/lib/scene/UiLayerStack.kt)（仅被 `UiScene.batch(layer, relative)` / `submit(layer, relative, tree)` 调用）。以下路径**不校验**：

| 路径 | 实际越界值 |
|---|---|
| `UiRenderBatch.render(tree, baseLayer)` | [`HudEditorScreen.kt:129`](../common/src/main/kotlin/com/github/epsilon/gui/hudeditor/HudEditorScreen.kt) `editorLayer = -120` |
| `UiRenderBatch.view(relativeBaseLayer)` | [`ClientSettingPanel.kt:75`](../common/src/main/kotlin/com/github/epsilon/gui/panel/view/ClientSettingPanel.kt) `view(10)`；[`UiContentBuffer.kt:24-25`](../common/src/main/kotlin/com/github/epsilon/gui/lib/render/UiContentBuffer.kt) `view(8)` / `view(9)` |
| `UiRenderBatch.layerHandle(relativeLayer)` | [`UiContentBuffer.kt:37/64/114`](../common/src/main/kotlin/com/github/epsilon/gui/lib/render/UiContentBuffer.kt) |

> `-120` 目前能跑通，纯粹因为 `render(tree, baseLayer)` 直接做整数加法。重构后会在构造期被拒绝。

### 2.5 已经死掉的 Compose 式脚手架

[`UiTree.kt`](../common/src/main/kotlin/com/github/epsilon/gui/lib/UiTree.kt) 里已有 `LayoutScope`（1306）、`LinearScope`（1365）、`Axis`（1396）、`UiTree.layout`（1645）以及 `box/column/row/fill/spacer`，**全仓库 0 调用点**。

这轮重构会**删除**它们，并替换为本方案中真正被使用的 `UiScope` / `Box` / `Column`。

### 2.6 消费方清单

| 层 | 数量 | 说明 |
|---|---|---|
| `UiScene` 持有者 | 9 | PanelScreen、HudEditorScreen、DropdownScreen、TrollGuiScreen、MainMenuScreen、EpsilonDialogScreen、AccountsScreen、DownloadJobOverlay、HudElementManager |
| `UiRenderBatch` 形参组件 | 21 | CategoryRailPanel、ModuleListPanel、ModuleDetailPanel、ClientSettingPanel、三个 ClientSettingTab、ColorPicker、ConfirmAction、Enum、Message、Registry、StringListList… |
| `Scope.pushAbsolute/pushRelative` 调用 | 47 处 / 24 文件 | 子树平移 |
| `Scope.scissor` / `scissorIf` | 16 + 6 处 | 裁剪 |
| `Scope.viewport` | 10 处 | 滚动视口 |
| `Scope.animate` | 40 处 | 动画推进 |

---

## 3. 设计

### 3.1 三条原则

1. **图层是结构，不是参数**：`layer` 从每个图元的签名里删掉，改由作用域（接收者）携带。
2. **作用域即接收者**：`UiScope` 携带 `layer` / `origin` / `theme` / `metrics` / `size`，对应 Compose 的 `DrawScope` + `ColumnScope`。
3. **一个原语**：层叠只由 `Layer()` 一个扩展函数实现；图元、容器、组件全是它的调用方。

### 3.2 `LayerOffset`：layer 成为值类型

```kotlin
@JvmInline
value class LayerOffset(val value: Int) {

    init {
        require(value > -STRIDE && value < STRIDE) {
            "LayerOffset 必须落在 (-$STRIDE, $STRIDE) 内，实际为 $value；需要更细遮挡时请拆语义层"
        }
    }

    operator fun plus(other: LayerOffset): LayerOffset = LayerOffset(value + other.value)
    operator fun unaryMinus(): LayerOffset = LayerOffset(-value)

    companion object {
        const val STRIDE = 100          // 与 UiLayerStack 的 stride、AGENTS.md 的 -99..99 对齐

        /**
         * 相对父作用域不动，不产生 LayerNode。
         */
        val Same = LayerOffset(0)

        // 语义槽位：替代各 GUI 里散落的 -60 / -50 / -20 / +20 / +21 等魔法数字
        val Backdrop = LayerOffset(-90)
        val Surface = LayerOffset(0)
        val Edge = LayerOffset(1)
        val Content = LayerOffset(10)
        val Floating = LayerOffset(20)
        val Overlay = LayerOffset(40)
    }
}
```

要点：

- `init` 校验把「相对 layer 必须在 `-99..99`」从文档约束变成**构造期错误**，且错误信息提示正确做法。
- `plus` 让 `LayerOffset.Content + LayerOffset.Floating` 可读、可越界报错。
- `Same` 是零开销快路径（见 3.6）。

### 3.3 `UiScope`：layer / origin / size 挂在接收者上

```kotlin
open class UiScope internal constructor(
    private val sink: MutableList<UiNode>,
    val theme: UiTheme,
    val metrics: UiTextMetrics,
    /** 相对根作用域累积后的 layer。 */
    val layer: LayerOffset,
    /** 当前绘制原点：图元坐标相对它解析。 */
    val origin: UiRect,
) {

    /** 当前可用尺寸（Compose 的 size）。 */
    val size: UiRect get() = UiRect(0.0f, 0.0f, origin.width, origin.height)

    internal fun emit(node: UiNode) {
        sink.add(node)
    }

    internal fun collector(): MutableList<UiNode> = sink

    /** 换 origin/layer，但写回同一个节点列表：用于 Box / 布局槽，不产生节点。 */
    internal fun ambient(layer: LayerOffset = this.layer, origin: UiRect = this.origin): UiScope =
        UiScope(sink, theme, metrics, layer, origin)

    /** 换 origin/layer 并隔离节点列表：用于 Layer / Clip 这类需要单独成节点的子树。 */
    internal fun capture(layer: LayerOffset, origin: UiRect, content: UiScope.() -> Unit): List<UiNode> {
        val buffer = ArrayList<UiNode>()
        content(UiScope(buffer, theme, metrics, layer, origin))
        return buffer
    }

    internal fun resolve(x: Float, y: Float, width: Float, height: Float): UiRect =
        UiRect(origin.x + x, origin.y + y, width, height)
}
```

关键点：

- **`ambient` vs `capture`** 的分工就是「不产生节点」与「产生一个节点」的分界：`Box`/`Column.Item` 只换 origin 复用同一个 sink；`Layer`/`Clip` 需要独立子树，走 `capture` 隔离列表。
- `resolution` 用 `origin` 做平移，图元坐标天然相对当前槽位，组件代码不必再手算 `bounds.x + ...`。
- **`size`** 让 `ModuleRow` 这类组件可以直接 `Fill(0f, 0f, size.width, size.height, ...)`，不再需要把 `rowBounds` 传进来再算一遍。

### 3.4 唯一的 `Layer()` 扩展函数

```kotlin
// 唯一的 layer 原语：24 个 `layer: Int` 首参重载收敛成这一个
fun UiScope.Layer(offset: LayerOffset, content: UiScope.() -> Unit) {
    if (offset == LayerOffset.Same) {
        // 等价层级直接内联，不留空 LayerNode，保持现有零开销路径
        content(this)
        return
    }
    emit(LayerNode(offset.value, capture(layer + offset, origin) { content(this) }))
}

/** 语义化薄封装：调用方读的是意图，不再是数字。 */
fun UiScope.Above(content: UiScope.() -> Unit) = Layer(LayerOffset.Edge, content)

fun UiScope.Below(content: UiScope.() -> Unit) = Layer(-LayerOffset.Edge, content)
```

- 子树捕获后包成**一个** `LayerNode`，偏移相对父作用域；与现有 `LuminUiRenderer` 的累积方式完全一致。
- 命名冲突顺带解决：属性 `layer` 与函数 `layer()` 同名会互相干扰，改成 `Layer()` 后不再碰撞。

### 3.5 图元、容器与命名规范

图元全部变成扩展函数，**签名里没有 layer，没有 scope，没有 textRenderer**：

```kotlin
fun UiScope.Fill(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
    emit(FillNode(resolve(x, y, width, height), radius, color))
}

/** 铺满当前作用域的快捷方式（Compose 的 fillMaxSize）。 */
fun UiScope.Fill(radius: Float, color: Int) {
    Fill(0.0f, 0.0f, size.width, size.height, radius, color)
}

fun UiScope.Text(text: String, x: Float, y: Float, scale: Float, color: Int) {
    emit(TextNode(text, origin.x + x, origin.y + y, scale, color))
}

fun UiScope.Outline(x: Float, y: Float, width: Float, height: Float, radius: Float, strokeWidth: Float, color: Int) {
    emit(OutlineNode(resolve(x, y, width, height), radius, strokeWidth, color))
}

fun UiScope.Box(insets: Insets, content: UiScope.() -> Unit) {
    content(ambient(origin = insets.apply(origin)))
}

/** 直接指定子树矩形（Compose 的 Modifier.size + offset），坐标相对当前 origin。 */
fun UiScope.Positioned(x: Float, y: Float, width: Float, height: Float, content: UiScope.() -> Unit) {
    content(ambient(origin = UiRect(origin.x + x, origin.y + y, width, height)))
}

fun UiScope.Clip(x: Float, y: Float, width: Float, height: Float, content: UiScope.() -> Unit) {
    val clipRect = resolve(x, y, width, height)
    emit(ClipNode(clipRect, capture(layer, clipRect) { content(this) }))
}

fun UiScope.Column(gap: Float, content: ColumnScope.() -> Unit) {
    content(ColumnScope(collector(), theme, metrics, layer, origin, gap))
}
```

`ColumnScope` 对应 Compose 的 `ColumnScope`，只有列内部才能拿到槽位 API：

```kotlin
class ColumnScope internal constructor(
    sink: MutableList<UiNode>,
    theme: UiTheme,
    metrics: UiTextMetrics,
    layer: LayerOffset,
    origin: UiRect,
    private val gap: Float,
) : UiScope(sink, theme, metrics, layer, origin) {

    private var cursor = 0.0f
    private var used = 0.0f

    val contentHeight: Float get() = used

    fun Item(height: Float, content: UiScope.() -> Unit) {
        val rect = UiRect(origin.x, origin.y + cursor, origin.width, height)
        content(ambient(origin = rect))
        cursor += height + gap
        used = cursor
    }

    fun Fill(content: UiScope.() -> Unit) {
        Item(max(0.0f, origin.height - cursor), content)
    }

    fun Spacer(height: Float) {
        cursor += height + gap
        used = cursor
    }
}
```

右上角入口也变成扩展函数，一个 lambda 描述一整层：

```kotlin
fun UiScene.Content(
    bounds: UiRect,
    layer: UiLayer = UiLayer.CONTENT,
    relative: LayerOffset = LayerOffset.Same,
    content: UiScope.() -> Unit,
) {
    submit(layer.baseLayer() + relative.value, UiTree.build(bounds, theme, metrics, content))
}
```

#### 命名规范（遵循 Compose API 规范）

| 类别 | 规则 | 示例 |
|---|---|---|
| 发射 UI、返回 `Unit` | **PascalCase** | `Fill()` `Text()` `Box()` `Column()` `Clip()` `Layer()` `Above()` `Below()` `Positioned()` `Shadow()` `Outline()` `Switch()` `MarqueeText()` `Item()` `Spacer()` `Content()` |
| 自定义组件 | PascalCase | `ModuleRow()` `ModulePanel()` `PopupCard()` `ModuleScreen()` |
| 返回值、非发射（Compose 自身也用小写：`animateFloatAsState` / `remember`） | camelCase | `animate()`、`UiTree.build()`、`metrics.textWidth()` |
| 属性 | camelCase | `scope.layer`、`scope.size`、`scope.origin` |
| 编译后的节点 IR | 保持不变 | `FillNode` / `TextNode` / `LayerNode` |

> 大小写差异带来一个额外收益：新旧 API 名字天然不同（`fill()` vs `Fill()`），`@Deprecated` 过渡期**不会产生重载歧义**，可以安全并存。

### 3.6 语义与性能等价性

| 维度 | 现状 | 重构后 | 结论 |
|---|---|---|---|
| layer 累积 | `LuminUiRenderer` 沿树 `layer + node.layer` | 同 | 不变 |
| 同层合批 | `Render2DScheduler` 按整数 layer 分桶 | 同（`LayerNode` 数 = `Layer{}` 块数） | 不变 |
| `layer == 0` 快路径 | `addNode` 直接 append，不包装 | `LayerOffset.Same` 直接内联 `content(this)`，不产生 `LayerNode` | 不变 |
| 单节点提层 | `LayeredNode` 包装（现状 18 处 `addNode` 调用） | 删除；由 `Layer { }` 覆盖 | 少一层间接 |
| 每帧分配 | 每个 `layer{}` 一个 `ArrayList` | 同 | 不变 |

实测（见第 7 节）：8 个 `Layer{}` 块 → 8 个 `LayerNode`，与整棵树的 36 个节点数量无关。

### 3.7 实现约束：为什么是 `class` 而不是 `interface`

`emit` / `ambient` / `capture` / `resolve` 必须**库内可见、外部不可见**。但 Kotlin **不允许 `internal` 成员出现在 `interface` 中**（编译器实测）：

```
error: modifier 'internal' is not applicable inside 'interface'
```

因此 `UiScope` 用 `open class` + `internal constructor`，`ColumnScope` 继承它。这也让「作用域只能由库内部派生」成为类型系统保证。

### 3.8 设计取舍

| 备选方案 | 放弃原因 |
|---|---|
| 继续用 `layer: Int` 参数，只做常量化 | 解决不了「漏传即静默错」和 21 个重复重载 |
| 用 Kotlin 2.4 `context(...)` 参数传 writer | lambda 类型变成 `context(UiNodeWriter) UiScope.() -> Unit`，所有签名都变复杂；Receiver 方案已足够 |
| 直接用 `Modifier` 链（`Modifier.zIndex().padding()`） | 需要先有布局与测量系统（本库仍是固定坐标），收益不足以支撑本期成本；留作后续 |
| 引入 Compose 运行时做真重组 | 与每帧重建的即时模式、`Render2DScheduler` 生命周期冲突，成本远超收益 |

---

## 4. 完整示例

以下代码均来自已编译运行的原型（`Lib.kt` / `Example.kt` / `Main.kt`，见附录 B）。

### 4.1 组件：`ModuleRow`

对应现有 [`gui/panel/component/ModuleRow.kt`](../common/src/main/kotlin/com/github/epsilon/gui/panel/component/ModuleRow.kt)。

**改之前**（现状）：

```kotlin
fun buildUi(
    scope: UiTree.Scope,
    textRenderer: TextRenderer,
    hoverProgress: Float,
    selectedProgress: Float,
    toggleProgress: Float,
    toggleHoverProgress: Float,
) {
    scope.roundRect(0.0f, 0.0f, rowBounds.width, rowBounds.height, radius, hoveredSurface)
    scope.text(moduleView.displayName, INSET, titleY, titleScale, titleColor)
    scope.toggle(localToggleBounds, toggleProgress, toggleHoverProgress)
}
```

**改之后**：

```kotlin
fun UiScope.ModuleRow(
    module: ModuleViewModel,
    hover: Float,
    selected: Float,
    toggle: Float,
    toggleHover: Float,
) {
    val radius = theme.controlRadius()
    val titleScale = 0.70f
    val subScale = 0.60f
    val keyScale = 0.60f

    val titleHeight = metrics.textHeight(titleScale)
    val subHeight = metrics.textHeight(subScale)
    val lineGap = 2.0f
    val titleY = (size.height - titleHeight - lineGap - subHeight) / 2.0f
    val subY = titleY + titleHeight + lineGap

    val keyWidth = metrics.textWidth(module.keybind, keyScale)
    val keyHeight = metrics.textHeight(keyScale)
    val keyRight = size.width - SWITCH_WIDTH - KEYBIND_GAP
    val keyX = keyRight - KEYBIND_CLIP_WIDTH
    val keyY = (size.height - keyHeight) / 2.0f

    // 行表面：默认 layer，签名里没有 layer 参数
    Fill(0.0f, 0.0f, size.width, size.height, radius, theme.rowSurface(hover))
    if (selected > 0.01f) {
        Fill(radius, theme.stateLayer(selected))
    }

    Text(module.displayName, LABEL_INSET, titleY, titleScale, theme.textPrimary())
    Text(module.category, LABEL_INSET, subY, subScale, theme.textMuted())

    // 开关必须压在行表面之上。
    // 旧写法：scope.layer(2) { layer -> layer.toggle(...) }（layer 只是同作用域别名）
    // 新写法：一个扩展函数，layer 值有语义名字，且不会漏传 layer 参数
    Layer(LayerOffset.Edge) {
        Switch(
            UiRect(size.width - SWITCH_WIDTH - SWITCH_PADDING, (size.height - SWITCH_HEIGHT) / 2.0f, SWITCH_WIDTH, SWITCH_HEIGHT),
            toggle,
            toggleHover,
        )
    }

    if (keyWidth <= KEYBIND_CLIP_WIDTH) {
        Text(module.keybind, keyRight - keyWidth, keyY, keyScale, theme.textMuted())
    } else {
        Clip(keyX, keyY, KEYBIND_CLIP_WIDTH, keyHeight) {
            MarqueeText(module.keybind, keyX, keyY, keyScale, theme.textMuted(), UiRect(keyX, keyY, KEYBIND_CLIP_WIDTH, keyHeight))
        }
    }

    if (module.enabled) {
        // 高亮描边要压在文字之上：相对关系用 Above/Below 表达
        Above {
            Outline(0.0f, 0.0f, size.width, size.height, radius, 1.0f, theme.accent())
        }
    }
}
```

### 4.2 面板：`ModulePanel`

`Column/Box/Clip/Layer` 组合替代手算 `UiRect` 与 `scene.batch(layer, relative)`：

```kotlin
fun UiScope.ModulePanel(
    title: String,
    modules: List<ModuleViewModel>,
    hoverIndex: Int,
    selectedIndex: Int,
) {
    val radius = theme.controlRadius() + 4.0f

    // 投影 → 面板表面 → 内容，三档遮挡用语义 layer 表达，没有数字
    Layer(LayerOffset.Backdrop) {
        Shadow(0.0f, 4.0f, size.width, size.height, radius, 12.0f, 0x66000000)
    }
    Fill(radius, theme.surface())
    Outline(0.0f, 0.0f, size.width, size.height, radius, 1.0f, theme.textMuted())

    Column(gap = 0.0f) {
        // 标题栏固定高度
        Item(HEADER_HEIGHT) {
            Text(title, LABEL_INSET, (HEADER_HEIGHT - metrics.textHeight(0.75f)) / 2.0f, 0.75f, theme.textPrimary())
            Above {
                Fill(0.0f, HEADER_HEIGHT - 1.0f, size.width, 1.0f, 0.0f, theme.textMuted())
            }
        }
        // 列表吃剩余高度（Compose 的 weight(1f)）
        Fill {
            Box(Insets.all(LIST_INSET)) {
                Clip(0.0f, 0.0f, size.width, size.height) {
                    Column(gap = ROW_GAP) {
                        for (index in modules.indices) {
                            Item(MODULE_ROW_HEIGHT) {
                                ModuleRow(
                                    module = modules[index],
                                    hover = if (index == hoverIndex) 1.0f else 0.0f,
                                    selected = if (index == selectedIndex) 1.0f else 0.0f,
                                    toggle = if (modules[index].enabled) 1.0f else 0.0f,
                                    toggleHover = if (index == hoverIndex) 1.0f else 0.0f,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### 4.3 浮层：`PopupCard`

现有代码需要 `scene.batch(UiLayer.POPUP)` + 另一套 `popupHost`；现在 popup 只是同一个作用域里更高 layer 的子树：

```kotlin
fun UiScope.PopupCard(bounds: UiRect, content: UiScope.() -> Unit) {
    Layer(LayerOffset.Overlay) {
        Positioned(bounds.x, bounds.y, bounds.width, bounds.height) {
            Shadow(0.0f, 8.0f, bounds.width, bounds.height, 8.0f, 16.0f, 0x88000000.toInt())
            Fill(8.0f, theme.surface())
            content()
        }
    }
}
```

### 4.4 整帧：`ModuleScreen`

对应 `scene.beginFrame(); scene.submit(UiLayer.CONTENT, tree); scene.endFrame()`：

```kotlin
fun UiScene.ModuleScreen(
    bounds: UiRect,
    modules: List<ModuleViewModel>,
    hoverIndex: Int,
    selectedIndex: Int,
    popup: Boolean,
) {
    beginFrame()

    Content(bounds, UiLayer.CONTENT) {
        Fill(0.0f, 0x33000000)
        Box(Insets.all(12.0f)) {
            ModulePanel(title = "MODULES", modules = modules, hoverIndex = hoverIndex, selectedIndex = selectedIndex)
            if (popup) {
                PopupCard(UiRect(40.0f, 120.0f, 220.0f, 160.0f)) {
                    Text("Settings", 12.0f, 10.0f, 0.7f, theme.textPrimary())
                }
            }
        }
    }

    // 需要独立 pass 的浮层仍可各提交一层，但坐标与语义 layer 都是同一个模型
    Content(bounds, UiLayer.OVERLAY) {
        Layer(LayerOffset.Overlay) {
            Text("overlay", 8.0f, 8.0f, 0.6f, theme.accent())
        }
    }

    endFrame()
}
```

### 4.5 运行结果

```
=== 提交的 pass ===
  pass layer = 200，节点数 = 36，顶层节点 = 8
  pass layer = 500，节点数 = 2，顶层节点 = 1

=== CONTENT pass 节点树 ===
FillNode(layer=0, 0.0,0.0 360.0x520.0)
LayerNode(offset=-90) -> 累积 layer = -90          ← 投影
  ShadowNode(layer=-90)
FillNode(layer=0, 12.0,12.0 336.0x496.0)           ← 面板表面
OutlineNode(layer=0)
TextNode(layer=0, "MODULES" @24.0,27.088749)
LayerNode(offset=1) -> 累积 layer = 1              ← 标题栏分隔线
  FillNode(layer=1, 12.0,43.0 336.0x1.0)
ClipNode(layer=0, 18.0,50.0 324.0x452.0)           ← 列表裁剪
  FillNode(layer=0, 18.0,50.0 324.0x34.0)          ← 行表面
  TextNode(layer=0, "KillAura" @30.0,64.4205)
  LayerNode(offset=1) -> 累积 layer = 1
    SwitchNode(layer=1, 304.0,59.0)                ← 开关压在行之上
  ...
LayerNode(offset=40) -> 累积 layer = 40            ← popup
  ShadowNode(layer=40)
  FillNode(layer=40, 52.0,132.0 220.0x160.0)
  TextNode(layer=40, "Settings" @64.0,142.0)

LayerNode 累积值 = [-90, 1, 1, 1, 1, 1, 1, 40]
Layer {} 块数量 = 8，与图元数量解耦
LayerOffset(120) 被拒绝: LayerOffset 必须落在 (-100, 100) 内，实际为 120；需要更细遮挡时请拆语义层
LayerOffset.Content + LayerOffset.Floating = 30
嵌套 layer：顶层节点 1 个，内层 1 个，图元不额外包装
全部断言通过
```

---

## 5. 迁移方案

### 5.1 映射表

| 旧写法 | 新写法 | 影响处数 |
|---|---|---|
| `scope.rect(1, x, y, w, h, c)` | `Layer(TrollZ.Base) { Rect(x, y, w, h, c) }` | 20 |
| `scope.textWithShadow(3, ...)` | `Layer(TrollZ.Text) { TextWithShadow(...) }` | 20 |
| `scope.layer(-20) { layer -> ... }` | `Layer(ReisaZ.Body) { ... }` | 32 |
| `buildUi(scope, textRenderer, hover, ...)` | `fun UiScope.ModuleRow(row, hover, ...)` | 21 个组件 |
| `renderBatch.render(UiTree.from(scope))` | `renderBatch.Content { ... }`（与 `UiScene.Content` 同构） | 21 |
| `scene.batch(UiLayer.CONTENT, -20)` | `Content(bounds, UiLayer.CONTENT, -LayerOffset.Floating)` | 9 |
| `scope.animate(anim, target)` | 不变（返回值，非发射，保持 camelCase） | 40 |

`gui/troll/` 的 `1..5` 不需要重排，起名即可：

```kotlin
private object TrollZ {
    val Base = LayerOffset(1)      // 行底色 / 进度条
    val Highlight = LayerOffset(2) // 悬停高亮
    val Island = LayerOffset(3)    // 输入框 / 色块底衬
    val Text = LayerOffset(4)      // 文字 / 光标 / 色块
    val Overlay = LayerOffset(5)   // 描边
}
```

`MainMenuScreen` 的 24 个魔数需要逐项语义化，建议命名空间：

```kotlin
private object ColumbinaZ {
    val Video = LayerOffset(0); val Shade = LayerOffset(1)
}

private object ReisaZ {
    val Prewarm = LayerOffset(-60); val Shade = LayerOffset(-40); val Glow = LayerOffset(-24)
    val BaseLine = LayerOffset(-23); val Trail = LayerOffset(-22); val Body = LayerOffset(-21)
    val PoseSlice = LayerOffset(-20); val Fold = LayerOffset(-19)
}

private object MenuZ {
    val GlowText = LayerOffset(-50)  // 标题 / 菜单行 / 页脚三处共用
    val Bubble = LayerOffset(20); val BubbleText = LayerOffset(21)
}

private object DialogZ {
    val Card = LayerOffset(0); val Content = LayerOffset(10)
}
```

### 5.2 分阶段计划

| 阶段 | 内容 | 可编译 | 风险 |
|---|---|---|---|
| **Phase 0** | 新增 `LayerOffset`、`UiScope`、`ColumnScope`、扩展函数族、`UiScene.Content`；旧 API 标 `@Deprecated` | ✅ 调用方不动 | 低 |
| **Phase 1** | 迁移 `gui/troll/`（47 处图元重载 + `TrollZ`） | ✅ | 中：1..5 需确认语义 |
| **Phase 2** | 迁移 `MainMenuScreen`（24 处魔数）+ 其余 5 文件 8 处 | ✅ | 中：魔数需要语义化 |
| **Phase 3** | 21 个 `renderBatch` 形参组件改为 `UiRenderBatch.Content { }`；9 个 `UiScene` 消费方改用 `Content` | ✅ | 中：涉及动画状态归属 |
| **Phase 4** | 删除 24 个 `layer: Int` 重载、`LayeredNode`、死代码 `LayoutScope`/`LinearScope`/`UiTree.layout`/`box`/`column`/`row`；同步更新 `AGENTS.md` 与 `graphics/README.md` | ✅ | 低 |

> 因为大小写命名不同，Phase 0 的新旧 API 并存不会产生重载歧义。

---

## 6. 风险与兼容性

### 6.1 会立即暴露的越界 layer

| 位置 | 现值 | 处理 |
|---|---|---|
| `HudEditorScreen.kt:129` | `editorLayer = -120` | 改 `-90`，或为 HUD 编辑器新增 `UiLayer` 语义层 |

该值现在靠 `UiRenderBatch.render(tree, baseLayer)` 的未校验加法生效，Phase 0 会让它抛 `IllegalArgumentException`。**这是预期行为**：`-120` 本来就超出了 `AGENTS.md` 规定的 `-99..99`。

### 6.2 注意事项

- **`ColumnScope.Fill { }` 与 `UiScope.Fill(radius, color)` 同名**：靠接收者与参数签名区分，已验证不冲突。
- **`UiTree.build` 新增 `bounds` 参数**：作用域需要知道自己分到的矩形（`size` 的来源）。现有 28 处 `UiTree.build` 调用需补一个 `bounds`，多数场景就是面板/HUD 元素的矩形。
- **`UiContentBuffer.view(8)/(9)`、`ClientSettingPanel.view(10)`** 属于 `UiRenderBatch` 的局部视图，Phase 3 前保持不变，但应加注释标注是「子批次偏移」而非「遮挡 layer」。
- **动画状态归属**：`animate` 仍由组件持有 `Animation` 字段并由调用方传入进度（状态提升），与 Compose 的 `remember` 语义等价，不引入隐藏状态。

### 6.3 AGENTS.md 约束核对

| 约束 | 是否满足 |
|---|---|
| Panel/Dropdown/popup 统一通过 `UiTree`/`UiScene`/`UiRenderBatch` 提交 | ✅ 不变 |
| 一个 Screen / HUD 帧共享一个 `UiScene`；`beginFrame()` / `endFrame()` 配对 | ✅ `Content()` 不改变职责，仍由宿主配对调用 |
| 遮挡关系使用不同 `UiLayer` 或相对 layer；相对 layer 在 `-99..99` | ✅ 从文档约束升级为构造期强制 |
| 不为每个面板各建一套 renderer | ✅ 不新增 renderer |
| 文本测量与绘制使用相同 scale 与 font loader | ✅ `metrics` 来自 `scope`，与绘制同源 |
| HUD 尺寸变化调用 `setBounds()`，位置通过 anchor/move API | ✅ 未触及 |
| 2D 世界覆盖层使用 `WorldToScreen` | ✅ 未触及 |

---

## 7. 验证记录

原型位于 `%TEMP%\eps-ui-proto\`（未写入仓库），使用与项目一致的 kotlinc 2.4.0 + JDK 25：

```shell
kotlinc Lib.kt Example.kt Main.kt -include-runtime -d proto.jar
java -Dstdout.encoding=UTF-8 -jar proto.jar
```

断言覆盖：

1. 两个 pass 的 base layer 分别为 200（`CONTENT`）、500（`OVERLAY`）。
2. `Layer{}` 数量（8）等于 `LayerNode` 数量，且与图元数量（36）无关。
3. 所有累积 layer 落在 `(-99, 99)` 内；`Backdrop`（-90）与 `Overlay`（40）正确出现。
4. `LayerOffset(120)` 在构造期抛异常，异常信息包含正确取值范围。
5. `LayerOffset.Content + LayerOffset.Floating == 30`。
6. 嵌套 `Layer { Layer { ... } }` 只产生嵌套节点，图元不被额外包装。

结论：类型安全成立，图层语义与现状等价，新增校验按预期拦截越界值。

---

## 8. 待决策项

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| 1 | **scene 级是否一起换** | A. 只改 tree 内 layer（影响面小）<br>B. 同时把 `scene.batch(UiLayer.CONTENT, -20)` 换成 `Content(bounds, layer, relative)`（动 9 个消费方） | B，一次到位，避免两套 layer 模型并存 |
| 2 | **troll 的 `1..5`** | A. 保留数字、起名为 `TrollZ`<br>B. 重排进 `LayerOffset` 通用槽位 | A，语义由所在 GUI 命名空间决定，不强行统一 |
| 3 | **旧 `layer: Int` 重载** | A. 保留一个版本周期，标 `@Deprecated`<br>B. Phase 0 即删除，47 处一次改完 | A，降低单次改动规模；大小写命名不冲突 |
| 4 | **`Modifier` 体系** | A. 本期不做<br>B. 追加 Phase 5（`Modifier.padding().background().layer()`） | A，先确认布局/测量需求 |
| 5 | **`Row`/`RowScope`** | A. 本期只做 `Column`<br>B. 同时做 `Row` | 看现有代码是否存在横向布局需求（`LinearScope` 无调用点，倾向 A） |
| 6 | **文档落地位置** | A. `docs/gui-layer-refactor.md`（当前）<br>B. 并入 `graphics/README.md` | A 作为提案；实现完成后按 AGENTS.md 更新正式文档 |

---

## 附录 A：原型 API 全量清单

| 函数 | 接收者 | 说明 |
|---|---|---|
| `Layer(offset) {}` | `UiScope` | 唯一层叠原语；`Same` 时内联 |
| `Above {}` / `Below {}` | `UiScope` | 相对父层 `+1` / `-1` |
| `Fill(x, y, w, h, radius, color)` | `UiScope` | 填充 |
| `Fill(radius, color)` | `UiScope` | 铺满当前作用域 |
| `Outline(x, y, w, h, radius, strokeWidth, color)` | `UiScope` | 描边 |
| `Text(text, x, y, scale, color)` | `UiScope` | 文本 |
| `Shadow(x, y, w, h, radius, blur, color)` | `UiScope` | 投影 |
| `Switch(bounds, progress, hover)` | `UiScope` | 开关 |
| `MarqueeText(...)` | `UiScope` | 跑马灯文本 |
| `Box(insets) {}` | `UiScope` | 内缩作用域（不产生节点） |
| `Positioned(x, y, w, h) {}` | `UiScope` | 固定矩形子树（不产生节点） |
| `Clip(x, y, w, h) {}` | `UiScope` | scissor 子树 |
| `Column(gap) {}` | `UiScope` | 垂直布局 |
| `Item(height) {}` / `Fill {}` / `Spacer(height)` | `ColumnScope` | 列内槽位 |
| `Content(bounds, layer, relative) {}` | `UiScene` | 一帧一层入口 |
| `animate(anim, target)` | `UiScope` | 保留（返回值，非发射） |

> 原型只实现了示例所需的图元子集；正式落地需把 `UiTree.Scope` 现有的全部图元（`shadow`/`roundRect`/`rect`/`outline`/`text`/`texture`/`button`/`input`/`slider`/`chip`/`segmentedControl`/`iconButton`/`popupCard`/`triangle`/`arc`/`triangle`/`marqueeText`/`playerHead` 等）逐个改为扩展函数。

## 附录 B：原型文件

| 文件 | 行数 | 内容 |
|---|---|---|
| `Lib.kt` | 312 | `LayerOffset`、`UiScope`、`ColumnScope`、扩展函数族、`UiScene.Content` |
| `Example.kt` | 193 | `ModuleRow` / `ModulePanel` / `PopupCard` / `ModuleScreen` |
| `Main.kt` | 106 | 驱动与断言（节点树打印、越界、加法、嵌套） |

原型中对 `UiRect` / `Insets` / `UiTheme` / `UiTextMetrics` 做了最小复刻以便独立编译；正式实现直接复用仓库现有类型，仅 `UiRect` 保持不变。

---

## 附录 C：文档同步清单

按 [`AGENTS.md`](../AGENTS.md) 要求，Phase 4 落地时必须：

- 更新 `AGENTS.md`「GUI、HUD 与渲染约束」：把「相对 layer 必须在 `-99..99` 内」改写为「由 `LayerOffset` 强制」，并移除对已删除 API 的描述。
- 更新 [`graphics/README.md`](../common/src/main/kotlin/com/github/epsilon/graphics/README.md) / `README_zh.md` 中涉及 GUI 层提交方式的段落。
- 删除本文档中「待决策项」与「未落地」标记，或把本文档归档为历史提案。
