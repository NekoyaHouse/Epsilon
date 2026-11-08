# Lumin Graphics

[English](README.md) | **简体中文**

Lumin Graphics 是一个为现代 Minecraft 模组开发设计的轻量化、高性能渲染框架。

本包已使用 Kotlin 实现（`common/src/main/kotlin/com/github/epsilon/graphics`），提供 Kotlin 与 Java 入口。下文的有序 2D canvas API 替代旧 layer handle API。

---

## 核心特性

* **SDF 圆角矩形**：通过片段着色器（Fragment Shader）计算平滑且抗锯齿的圆角。支持动态半径，无需改变顶点数据。
* **高性能 TTF 渲染**：先进的 TrueType 字体渲染技术，采用图集（Atlas）批处理机制，显著降低绘制调用（Draw Calls）。

---

## 💡警告

### 独立 renderer 的缓冲复用

在单帧渲染循环内，尽可能不在调用一次或多次 `Renderer.draw()` 之后再调用 `Renderer.clear()`
并再次对同一个实例调用 `draw()`。这会导致单帧内使用多个 Buffers，导致 In-Flight 优化力度减少。

同帧多次提交优先使用消费式 2D canvas，其分配由 fence 保护，可安全多次 flush；独立 renderer 仍沿用原环形缓冲行为。

---

## 持久 UI 与有序 2D 渲染

`UiScene` 持有组合树和共享 2D 后端。组合节点保存自身的 DisplayList 与子节点引用；未变化的记录复用局部坐标 GPU 几何。transform、clip、paint 分别订阅状态。执行器缓存兼容的绘制范围，设备支持时使用 indexed indirect multi-draw，否则逐范围提交普通 draw。

```text
Composition → UiRenderNode → 局部 DisplayList → GeometryArena
                    ↓                              ↓
             Transform / Clip / Paint → 有序 DrawPlan → Executor
立即 canvas → 临时几何 ─────────────────────────────────┘
```

### 顺序与提交

每次 flush 内按显式 layer 升序绘制，同层保持声明顺序，子树在插入位置展开。视口遵循同样的规则，内容与滚动条进入宿主批次。图元种类、字体图集和纹理不会覆盖该顺序。矩形、圆角、描边与箭头三角形共用 shape pipeline，文字、纹理与特效保留各自管线。只有相邻且 pipeline、几何页、资源、uniform 和 scissor 兼容的范围能够合批。普通 AA/非 AA 文字允许一个批次绑定最多八组图集/sampler，每个范围通过实例属性选择图集并保留字形顺序；第九组另起批次。模糊/故障文字与普通纹理仍遵循各自资源边界。不做空间重排或全局重叠依赖图。

Dropdown 模块行在每个面板预留的层区间内显式保留旧版透明叠加关系：按钮填充/描边、半透明行表面与 Hold 下划线、名称/按键文字/可见性图标。旧调度器隐式先画圆角和描边、后画矩形，因此行表面会压暗按钮；直接改为声明顺序会改变外观。局部分层保留压暗效果与浅色标签，后提交的面板仍覆盖先前面板。互不遮挡的标题箭头先于标题文字提交，行内图标与文字共用阶段。兼容范围保持相邻以进行间接合批；执行器在同一个 pass 内复用未变化的 buffer、scissor 与资源绑定。

包含四十个折叠模块行、两张图集的绘制规划回归用例，连同阴影/标题共生成五个批次，末尾带滚动条时为六个。这是规划测试，不能代替 GPU 捕获验收：几何分页、展开设置、不同 clip/uniform、额外特效或超过八组图集/sampler 都可能增加批次。设备支持 multi-draw indirect 与非零 first-instance 时，每个索引批次使用一次 API draw；否则沿用逐范围普通 draw 回退。

旧的 `layer()` / `LayerHandle` 已替换为 `canvas(layer)` / `Canvas`；`UiRenderBatch.layerHandle()` 改为 `canvas()`。

```kotlin
val canvas = scheduler.canvas(0)
canvas.addRect(10f, 10f, 100f, 24f, Color.WHITE)
scheduler.flush() // 消费当前记录。
scheduler.flush() // 不重复绘制。
canvas.addText("Next", 10f, 40f, 1f, Color.WHITE)
scheduler.flush() // 同帧新分配不会覆盖已提交的数据。
```

立即模式不要求稳定 key。`clear()` 丢弃未提交记录，`resetCache()` 独立释放持久缓存。需要重放时显式再次提交。flush 是顺序屏障：与原版、目标切换、模糊处理、`LuminImmediateRenderer` 或 3D 绘制交错前先 flush。独立立即 renderer 与 3D API 保持现状。`RectRenderer.draw()` 等独立 renderer 仍会重放自身缓冲，其语义与消费式 scheduler 不同。

### 延迟属性与几何

DSL 从 `com.github.epsilon.gui.lib` 导入，变换类型从 `graphics.schedulers.render2d` 导入：

```kotlin
scene.LuminUIScene(bounds, key = "list") {
    Clip({ UiRect(0f, 0f, size.width, size.height) }) {
        Transform({ Render2DTransform.translation(0f, -scroll.value) }) {
            Paint({ foreground.value }) {
                CacheDraw { Text(label.value, 4f, 4f, 0.7f, Color.WHITE) }
            }
        }
    }
}
```

`Transform`、`Clip` 和 `Paint` 中的状态读取仅使对应属性阶段失效。`CacheDraw` 单独观察几何依赖，只允许图元和绘制容器，不能声明 composition、输入或视口。直接在组合中读取状态仍触发正常重组。`Paint` 对每个图元做 RGBA 乘色，不代表离屏合成后的组透明度。输入使用逆变换命中，几何复用时动画时钟与帧回调仍继续运行。

列表使用稳定的 `LuminUIItem` key。旧绝对坐标在编译几何时归一化，不作为缓存身份。`LuminUIScene(retainPosition = true)` 允许同尺寸场景仅通过属性移动，但闭包必须遵循局部坐标契约；为兼容依赖绝对位置的旧回调，默认仍为 `false`。滚动应使用延迟 `Transform`；旧 `Viewport(scroll = ...)` 参数仍在组合阶段平移坐标，可能触发重建。Dropdown 分类行已迁移至延迟变换，其他旧布局与动画控件仍按几何输入变化重组。

裁剪采用硬件 scissor；旋转和倾斜的裁剪矩形使用变换后的轴对齐包围框，不是精确旋转遮罩。视口外范围不提交绘制，但首次编译仍准备其几何，不等于列表虚拟化。

### 资源、生命周期与计量

scene 与 GPU 资源在渲染线程创建、更新和销毁。场景帧配对调用 `beginFrame()` / `endFrame()`，失败时 `abortFrame()` 丢弃待提交工作。提交捕获稳定几何引用和属性值。更新先分配新范围再替换句柄，旧范围只在 GPU fence 完成后复用。scene 关闭沿用 Manager 生命周期，清理订阅、DisplayList、查询池和几何页。buffer 销毁交给后端处理，不阻塞等待尚未提交的 fence。保留同一个 Screen 实例返回页面时，`beginFrame()` 为已关闭 scene 创建新后端。字体 loader 身份、字形/图集 revision、缩放及资源重载 generation 使相关缓存失效。编译失败保留旧缓存，记录并抛出错误，由宿主中止异常帧后重试。

每个 scheduler 的 arena 驻留上限为 64 MiB。冷 DisplayList 使用 32 MiB / 2,048 条软预算，不再提交的记录会清理；活跃或仍排队的内容不得提前释放。硬预算耗尽会报告分配失败，不阻塞渲染线程等待当前 submit 的 fence。空闲相邻范围自动合并。`stats` 提供累计记录数、几何构建数、DrawPlan 构建数、命中数、上传字节、实际 API draw 数/范围数、CPU 组合/记录/几何/规划/上传/提交时间及 live/resident/peak 字节。`unusedResidentBytes` 包括空闲范围与页内填充，不单指外部碎片；上传时间统计 CPU 编码，不代表 GPU 传输完成。开启 `measureGpu = true` 使用异步时间戳查询，`gpuNanos` 表示最近完成的样本，不同步等待当前帧 GPU。

静态记录预热后避免重录、几何生成和静态顶点上传。执行器仍遍历持久引用并比较当前绘制 packet，以验证可复用计划；本版尚未实现变更计划的局部拼接，不保证遍历耗时与节点数无关。测量时同时比较预热后计数增量、完整 CPU/GPU 帧时间、上传量和实际 draw 数；编译成功不代表视觉验收或具体加速倍数。

---

## 快速上手

Lumin Graphics 的所有渲染操作均通过专门的 **Renderer（渲染器）** 完成。

### 可用渲染器

* `RectRenderer`：针对标准扁平矩形进行了优化。
* `RoundRectRenderer`：用于具有动态圆角半径且抗锯齿的矩形。
* `TtfTextRenderer`：用于高性能的 TrueType 字体渲染。
* `TextureRenderer`：用于批量绘制不同贴图。

### 初始化与线程安全

渲染器 **必须** 在 **渲染线程（Render Thread）** 上进行初始化。我们建议使用 `lazy`（或 Guava 的 `Suppliers.memoize`）
来确保安全且延迟的初始化。

```kotlin
// 推荐的初始化方式
private val rectRenderer: RectRenderer by lazy { RectRenderer.create() }

// 直接使用渲染器实例
rectRenderer.addRect(10f, 10f, 100f, 100f, Color.WHITE)
```

Java 调用方的等价写法：

```java
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::create);

rectRenderer.get().addRect(10f, 10f, 100f, 100f, Color.WHITE);
```

---

### 使用方式

#### 1. 基础绘制与重置

对于大多数即时模式（Immediate-mode）的 UI 任务，你需要在同一帧内添加形状并清理缓冲区：

```kotlin
// 1. 向缓冲区添加形状
rectRenderer.addRect(10f, 10f, 200f, 200f, Color.WHITE)

// 2. 绘制到屏幕并在下一帧前清理数据
rectRenderer.draw()
rectRenderer.clear()

// 你也可以直接使用 drawAndClear()
rectRenderer.drawAndClear()
```

#### 2. 缓冲区复用

如果你的 UI 内容并非每帧都在变化，你可以只添加一次顶点，并在后续帧中多次绘制，从而节省 CPU Overhead。

```kotlin
// 在初始化阶段或首帧中：
rectRenderer.addRect(10f, 10f, 200f, 200f, Color.CYAN)

// 在渲染循环中：
rectRenderer.draw() // 内容会一直保存在 GPU 缓冲区中，直到调用 clear()
```

---

### 💡 优化建议

在使用 **Lumin Graphics** 时请记住：多次调用 `.draw()` 而不调用 `.clear()` 是极其高效的。它仅会重新触发现有 GPU
数据的绘制指令，而无需重新上传顶点数据。

---

## 开源协议

* **Lumin Graphics** 采用 [GNU General Public License v3.0](LICENSE) 协议。

---

Copyright © 2026 NekoyaHouse.
