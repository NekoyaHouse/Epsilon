# LuminUI 自动状态观察与动画调度

LuminUI 保留现有 `UiTree`、Kotlin DSL 和 Lumin 绘制后端，通过轻量运行时实现 Group/Item 级重组。没有 Compose 依赖、编译插件或完整 Snapshot 多版本事务。

覆盖 Panel、Dropdown、popup、HudEditor、Troll 及共享控件。HUD 元素自身和其他 Screen 继续按现有方式绘制；使用共享 UiScene 的立即模式宿主仍需配对帧生命周期。

## 状态与失效

```text
输入回调 / Manager 刷新 / 外部线程写入
                  ↓
        kernel.state 的可观察存储
                  ↓
      宿主线程排空合并的变更通知
                  ↓
        读取该状态的组合单元失效
                  ↓
   重建失效单元，重放其余不可变节点
```

通用状态内核位于 `kernel.state`，不依赖 GUI：

```kotlin
interface State<out T> { val value: T }
interface MutableState<T> : State<T> { override var value: T }

val enabled = mutableStateOf(false)
var expanded by mutableStateOf(false)
val label = derivedStateOf { if (enabled.value) "on" else "off" }
```

- 写入默认按结构相等比较，同值不通知。
- 读取只归属最内层观察作用域。成功重建后替换依赖集合，解除条件分支不再读取的订阅。
- `derivedStateOf` 观察输入，仅在结果改变时通知下游。没有观察者时解除上游订阅，下一次读取按输入版本重新计算。
- 构建失败不提交新依赖和半成品节点，新创建的记忆资源会清理，下次仍重试。
- 普通业务状态禁止在组合构建期间改变。输入、配置写入、模块切换和网络操作在事件回调执行。
- 外部线程只写状态和排队通知；观察图、组合和帧任务由宿主线程管理。观察器记录读取版本，构建期间发生外部写入也会重新失效。
- 同一批配置加载的多次写入在宿主排空队列前合并通知；业务 `onChanged` 的调用顺序保持原样。
- 集合发布不可变快照。普通对象内部修改、可变集合原地修改和直接读取系统时间不能自动产生失效。

`gui.lib.state` 中的旧状态导入保留为兼容别名。新业务状态优先使用 `kernel.state`。

## 已接入的数据源

| 数据源 | 观察入口 |
|---|---|
| Setting | 实际底层存储；原有 getter、委托、归一化和回调保留 |
| 静默配置更新与 reset | 与普通写入共用可观察存储；静默路径继续跳过业务回调 |
| BoolSetting | 区分配置值与经 Via 条件计算的有效值，特殊 setter 同样通知 UI |
| Setting 可见性、分组折叠 | 在布局观察作用域执行条件，内部 Setting 读取成为布局依赖 |
| Module | 启用、隐藏、快捷键和绑定模式的可观察存储；原有生命周期保留 |
| 主题 | 一次发布不可变调色板，颜色 getter 观察同一状态 |
| 翻译 | 完成组件资源刷新后发布资源状态，翻译 getter 自动观察 |
| 字体 | 帧开始应用字体设置，默认字体加载器身份可观察 |
| 好友与配置列表 | Manager 发布快照；配置目录外部变动沿用已有缓存周期检查 |
| HudEditor | 选中、悬停、吸附及 HUD 位置、anchor、bounds；仍用既有移动/尺寸 API |

`Setting.asState()` 是直接访问真实 getter/setter 的兼容视图，不保存第二份值，也没有全局适配器缓存。布局可以直接读取 `setting.value`，无需先转为 State。

覆盖范围内的宿主调用 `prepareLuminDataSources()` 同步 Via、配置列表、字体和主题，然后再构建 UI。配置目录的 I/O 与字体切换发生在宿主准备阶段；缓存绘制块只读取快照。

## 组合身份与记忆

```kotlin
scene.LuminUIScene(bounds, key = "settings-root") {
    LuminUIGroup(key = "header") { /* 标题 */ }
    LuminUIItems(settings, key = { it }) { setting ->
        val local = remember(key = "editor") { EditorModel() }
        // 直接读取 setting.value、主题和翻译。
    }
}
```

`LuminUIItem` 与 `LuminUIGroup` 共用组合树、依赖观察和生命周期，没有另一套 revision 缓存。

- 根入口、Group/Item、remember 使用显式稳定 key。同一父作用域下重复的组件或 remember key 会报错。
- key 只表示身份。不要加入坐标、鼠标、颜色、主题、时间、翻译版本或动画进度。
- 列表使用元素身份，不使用可变化的索引。插入、删除与重排保留对应组件的记忆。
- 父级命中缓存时，子项仍独立响应自己的状态变化。
- 父级重建并重新绑定子项时，保守重建该子项，更新普通参数和闭包捕获值；不比较 lambda 地址。
- 根入口每帧的新 lambda 不使缓存失效。变化的宿主输入必须接入 State 或放在 `LuminView` 的布局观察作用域中。
- 单独创建、没有 Scene 宿主的批次继续采用立即模式。

## 布局、嵌套与输入

第一版继续保存绝对坐标节点，不拆分 Compose 的布局、放置、绘制三个阶段。

组合会比较 origin、bounds、文本度量资源和绘制上下文。几何变化重建节点，同时保留 remember。独立重建子项时重建其定位上下文，避免使用已退出的临时 bound 栈。

子组合引用保留在 Layer、Clip、Viewport 的实际嵌套位置，递归解析后才交给 renderer，不将子组合追加到根输出。

`LuminView.render(mouseX, mouseY) { ... }` 包住宿主布局准备阶段，观察可见性、行高、展开进度与布局资源等输入。准备代码仍每帧运行；静止时组合内容不再重建，Minecraft 的绘制提交正常继续。需要先完成布局再路由命中的窗口使用同一 `LuminView.prepare { ... }` 观察布局，确保列表或可见性变化在尺寸不变时也能失效对应窗口。Dropdown 的帧前测量归属各 Panel 的布局观察；模块的帧内高度缓存以可观察状态发布，缓存命中也保留布局依赖，展开/折叠时后续行随高度更新，无需滚动触发重建。

完全折叠的 Dropdown 模块与设置分组只读取展开状态和动画进度，不测量其内部设置。进度离开零后恢复测量和可见性依赖，关闭动画期间仍测量，到零才停止。依赖读取以对象身份记录；成功提交后订阅直接复用依赖表的 key 视图，避免每帧复制成另一份哈希集合。

命中探针由框架维护。宿主更新当前输入，只有局部 hovered 布尔值改变时通知读取者；鼠标在同一区域移动不会使每行订阅原始鼠标坐标。布局变化重新建立命中区域，支持鼠标静止时控件移动。被遮挡窗口使用宿主传入的禁用坐标。

嵌套组合的鼠标参数可能来自父级上次执行时的闭包，因此子级继承父级的输入坐标基准；父级独立重建时也保持该基准。命中探针以参数与基准的差值恢复局部偏移，再用宿主当前鼠标判定，避免将旧参数与新帧基准混用而产生高亮错位。重组块的悬停判断使用 `UiRect.contains()` 接入探针，不能用普通坐标比较代替；Dropdown 设置分组标题也遵循这一入口。

配置面板等输入框从外部数据填充文本时，在组合中读取目标值与焦点条件，通过 `afterLuminCommit { ... }` 同步文本，避免在构建阶段写入状态；失败构建不执行该同步。

旧式滑条/取色控件通过 `luminPointerInput(key)` 在宿主输入阶段更新拖动预览，离开组合时自动解除。IME 位置通过组合的逐帧提交回调维持，Viewport 滚动条读取当前宿主鼠标，不使用缓存帧的旧坐标。

### UiScope 输入订阅

`Interaction(key, bounds, enabled) { ... }` 把点击、拖动、滚轮与焦点键盘/文字订阅声明为组合树节点。Panel、Dropdown、Troll、HudEditor、所有 popup，以及主菜单、账号页、对话框和下载卡片都使用此入口。Screen 只转换坐标、处理全局快捷键并转发到 `scene.input`；容器不再遍历子控件分发输入。

同一个 Scene 的所有 LuminView 共享一个焦点和指针捕获。显式 View 继续负责布局与悬停观察；没有显式 View 的 Scene 入口使用 Scene 自己的 View。独立 View 仍可使用自己的路由。普通 `Content` 没有跨帧身份；需要输入的立即内容使用带稳定 key 的 `LuminUIScene(..., immediate = true)`。

```kotlin
view.render(mouseX, mouseY) {
    scene.LuminUIScene(bounds, key = "editor") {
        Positioned(8f, 8f, 120f, 24f) {
            Interaction(key = "text") {
                onClick { true }
                onFocusChanged { focused -> editor.setFocused(focused) }
                onKeyPressed { event -> editor.keyPressed(event) }
                onTextInput { event -> editor.charTyped(event) }
            }
            // 读取编辑模型的可观察状态，绘制文本。
        }
        Positioned(8f, 40f, 120f, 16f) {
            Interaction(key = "slider") {
                onDrag(
                    onStart = { event -> slider.begin(event.localX); true },
                    onEnd = { slider.commit() },
                    onCancel = { slider.cancel() },
                ) { event -> slider.preview(event.localX) }
            }
        }
    }
}

// 先把原版坐标和拖动增量转换为 Epsilon 坐标，再转发一次。
scene.input.mouseClicked(epsilonEvent, doubleClick)
scene.input.mouseDragged(epsilonEvent, epsilonDeltaX, epsilonDeltaY)
scene.input.mouseReleased(epsilonEvent)
scene.input.mouseScrolled(epsilonX, epsilonY, scrollX, scrollY)
scene.input.keyPressed(keyEvent)
scene.input.charTyped(characterEvent)
scene.input.preeditUpdated(preeditEvent)
```

- `key` 在当前组合内唯一，只表示身份；`bounds` 默认为当前 `size`，显式矩形相对当前作用域。Positioned、Clip 和 Viewport 沿用绘制树的坐标与滚动偏移，事件提供屏幕坐标和区域局部坐标。
- 高 layer 先命中，同 layer 后声明的节点先命中；Clip/Viewport 的交集限制初始命中。Viewport 内部与内容缓冲一致，忽略内部 Layer 偏移，按声明顺序命中，并沿用外层的 layer。返回 false 继续向下查找。纯绘制图元不阻挡输入，窗口表面和模态背景显式声明消费事件的节点。
- `onClick` 在鼠标按下时触发，默认键为 Minecraft 26.3 的 `InputConstants.MOUSE_BUTTON_LEFT`。一个节点可分别订阅多个按钮的点击与拖动；`onDrag` 成功开始后优先于同一按钮的点击。
- 接受拖动后捕获指针，区域与裁剪外仍收到移动，只由相同按钮的释放结束。其他按钮不结束捕获，重复释放不重复提交。禁用、移除、关闭 View 或 `cancelPointer()` 触发取消；取消先于失焦执行，预览不能因清理而提交。按下回调打开模态时，原控件不会在回调返回后重新获取捕获或焦点。
- 键盘、字符、preedit 和 `onPreviewClick` 订阅使节点可聚焦。点击被接受后切换焦点；点击空白、非焦点控件、禁用或移除时失焦。`requestFocus(key)` 用稳定身份请求焦点；`autoFocus = true` 在节点首次出现或从 false 切换为 true 时请求，不会逐帧抢焦点。尚无 Tab 导航。
- 快捷键录制通过聚焦节点的 `onPreviewClick` 优先读取区域外的鼠标按下，避免扫描所有模块或设置行。返回 false 后继续普通命中。
- `InputRegion` 是编辑模型的便利适配：用具名回调声明按下、预览按下、拖动、释放、取消、键盘、文本、preedit、失焦和滚动。传入释放/拖动处理器时，接受按下即捕获对应按钮；其余组件可直接使用 `Interaction`。
- `PanelPopupHost { scene.input }` 打开时清除旧焦点与捕获，派发时用 `within(popup.inputView)` 限定所属 View。未消费的模态事件不会进入下层；外部点击依据已提交的输入区域关闭弹窗，随入场动画和裁剪一起更新。
- 构建只声明回调，业务修改在事件阶段执行。缓存重放保留订阅，局部重建更新回调和几何，稳定 key 保留焦点与捕获；组合更换所属 View 时重建订阅并释放旧焦点与捕获。Scene 完整 `endFrame()` 后统一发布本帧快照；构建或提交失败通过 `abortFrame()` 保留上一轮输入快照。
- Screen 移除时 `resetComposition()`，弹窗关闭时关闭自己的 View。销毁只解除该 View 的订阅，不会关闭同 Scene 的其他 View。系统 IME 引用由编辑模型/模态宿主管理，重复聚焦不会重复获取引用；候选框位置继续通过 `IMEFocusHelper.updateCursorPos` 逐帧提交。

旧 `PanelInputRouter`、Dropdown 的容器递归输入协议、设置行命中缓存和 `luminPointerInput` 鼠标轮询入口已删除。拖动由宿主真实事件推进，滑条和颜色预览在释放时提交，取消时丢弃。

## 帧时钟与动画

每个 Scene 宿主持有一个单调纳秒时钟与活动任务集合。batch 和内容缓冲共享宿主时钟，同一任务每帧只推进一次。

```kotlin
LuminUIItem(key = setting) {
    val progress = animateFloatAsState(
        key = "toggle", targetValue = if (setting.value) 1f else 0f,
        easing = Easing.EASE_OUT_CUBIC, durationMs = 160L,
    )
    // 绘制时读取 progress.value。
}
```

事件驱动的连续操作使用 `rememberAnimatable(key, initial, easing, durationMs)`，在回调里调用 `animateTo` / `snapTo`。

目标声明在成功提交后执行。同目标不重启，反向或中途改目标从当前值继续；零时长直接到终值；结束精确写入终值并注销任务，`snapTo` 取消旧任务。未启动的动画不占活动集合。

迁移期保留 `LuminAnimatable.run` 等兼容调用，语义为声明目标，数值仍由宿主时钟推进。旧 `utils.render.animation.Animation` 只允许立即模式调用，重组块内使用拉取式 `animate` 会报错。

Troll 的惯性滚动是独立物理帧任务，沿用衰减、越界回弹和停止阈值。光标闪烁使用周期状态，仅在显隐切换时失效；失焦后撤销任务。跑马灯显式读取 `luminFrameTime`。Reisa 等即时内容保留立即 pass。

## 帧与销毁

- 每个完整宿主帧配对 `UiScene.beginFrame()` / `endFrame()`。异常帧使用 `abortFrame()` 丢弃命令并保留组合以便重试。
- `flush()` 保留命令，`endFrame()` 仍会提交剩余命令。Dropdown 为先输出主场景、再输出 popup 私有缓冲，提前调用 `flushAndClear()` 消费主命令，避免帧末重复绘制及半透明叠加。
- 父级成功重建才清理未出现的子组合；缓存重放保留子树。
- 根入口按稳定 key 登记，在完整帧结束清理未出现的入口。
- 销毁组合解除订阅、关闭 AutoCloseable 记忆资源，并取消其动画、周期与输入任务。
- Screen 关闭调用 `resetComposition()`，单例重开创建新组合树；业务模型按现有规则保留。
- 立即模式子组合每帧执行一次，父级重建帧不会额外执行第二次。

## 验证

`common/src/test` 使用假时钟、重建计数和无 GPU 文本度量桩覆盖：状态判等与条件订阅、派生状态、跨线程与构建期间写入、静默/重置/BoolSetting、资源通知、稳定身份与记忆清理、失败重试、Layer/Clip/Viewport 坐标、动态输入、配置 Dropdown 首次展开的输入框同步与失败回滚、模块展开/折叠时静止鼠标下的后续行位置、动画反向/结束/销毁和周期任务。

折叠测量回归分别使用 100 个设置：连续 10 帧完全折叠时可见性计算次数为零，展开动画中恢复测量，动态可见性更新高度，关闭后再次归零。状态内核还验证同一次观察中发生跨线程写入并重复读取时，保留第一次读取版本而不漏失效。可选类缓存通过并发重复查询验证存在与缺失类均只解析一次，LinkageError 继续向外传播，不被误缓存为类缺失；Via 后端实例仍每次获取，避免未初始化实例被永久缓存。

悬停回归覆盖缓存父级下子组合独立重建及再次绑定孙级、局部坐标偏移、移出/重新进入/禁用鼠标输入，以及真实 Dropdown 分组标题从下向上移动后的高亮和动画结束。根级保持缓存，同一区域内继续移动不会增加重建次数。

`UiScopeInputTest` 覆盖缓存重放与局部坐标、layer/声明顺序/交叠裁剪、Viewport 滚动与区域外捕获、多根层级与辅助鼠标键、跨 View 唯一焦点与 Unicode 字符、模态隔离、自动/显式聚焦、焦点预览录制、滚轮回落、局部重建与失败回滚、禁用/移除/关闭时取消及清理顺序、无显式 View 的立即宿主和按下时打开模态。OS IME 与游戏内实机验收仍单独执行。

```powershell
.\gradlew.bat :common:test :common:compileJava :common:compileKotlin :fabric:compileJava :fabric:compileKotlin :neoforge:compileJava :neoforge:compileKotlin
.\gradlew.bat buildRelease
```

实机验收还需覆盖主题/语言/字体切换、展开折叠、滑条与取色拖拽、IME、外部设置更新、HudEditor 移动缩放和 Troll 惯性滚动。单元测试不代替 GPU、系统 IME 和游戏中提取/提交效果检查；没有据此推断 FPS 提升。

## Spark 采样依据（2026-11-07）

[用户提供的客户端采样](https://spark.lucko.me/lXOHqjLs8l) 持续约 18.4 秒，间隔 4 ms；Render thread 的记录权重合计 15,332 ms。以下比例以该线程采样权重为分母，来自调用树的包含子调用耗时，不代表优化后的 FPS 提升，也不能与其嵌套热点相加。

| 调用路径 | 采样占比 | 对应修改 |
| --- | ---: | --- |
| `prepareLuminDataSources → refreshUiVersion → queryVersion` | 11.7% | 缓存不存在的 Via 可选类，避免每帧扫描类路径并构造异常 |
| `DropdownScreen.beginPanelFrames` | 14.6% | 跳过完全折叠的模块/分组内部测量，减少状态读取；观察器移除订阅集合复制 |
| `DropdownScreen.extractRenderState → UiScene.endFrame → flush` | 5.8% | 消费此前已提交的命令，取消重复输出主场景 |

这次采样来自连接 IDE 调试器的开发客户端，且采样较短。已验证操作次数与刷新正确性；实际帧时间、分配率和 GPU 输出需在相同场景下重新采样，性能对照宜使用充分预热且未连接调试器的客户端。

## Compose 参考

- [SnapshotStateObserver](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/snapshots/SnapshotStateObserver.kt)：按作用域观察读取与清理订阅。
- [Composition](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Composition.kt)：读取/写入失效、提交与销毁。
- [BroadcastFrameClock](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/BroadcastFrameClock.kt)、[AnimateAsState](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/AnimateAsState.kt)：宿主时钟、动画记忆与提交后目标更新。

坐标与 layer 规则见 [GUI layer 重构](gui-layer-refactor.md)。
