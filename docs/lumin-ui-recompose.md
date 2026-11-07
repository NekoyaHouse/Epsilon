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

`LuminView.render(mouseX, mouseY) { ... }` 包住宿主布局准备阶段，观察可见性、行高、展开进度与布局资源等输入。准备代码仍每帧运行；静止时组合内容不再重建，Minecraft 的绘制提交正常继续。需要先完成布局再路由命中的窗口使用同一 `LuminView.prepare { ... }` 观察布局，确保列表或可见性变化在尺寸不变时也能失效对应窗口。

命中探针由框架维护。宿主更新当前输入，只有局部 hovered 布尔值改变时通知读取者；鼠标在同一区域移动不会使每行订阅原始鼠标坐标。布局变化重新建立命中区域，支持鼠标静止时控件移动。被遮挡窗口使用宿主传入的禁用坐标。

配置面板等输入框从外部数据填充文本时，在组合中读取目标值与焦点条件，通过 `afterLuminCommit { ... }` 同步文本，避免在构建阶段写入状态；失败构建不执行该同步。

旧式滑条/取色控件通过 `luminPointerInput(key)` 在宿主输入阶段更新拖动预览，离开组合时自动解除。IME 位置通过组合的逐帧提交回调维持，Viewport 滚动条读取当前宿主鼠标，不使用缓存帧的旧坐标。

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
- 父级成功重建才清理未出现的子组合；缓存重放保留子树。
- 根入口按稳定 key 登记，在完整帧结束清理未出现的入口。
- 销毁组合解除订阅、关闭 AutoCloseable 记忆资源，并取消其动画、周期与输入任务。
- Screen 关闭调用 `resetComposition()`，单例重开创建新组合树；业务模型按现有规则保留。
- 立即模式子组合每帧执行一次，父级重建帧不会额外执行第二次。

## 验证

`common/src/test` 使用假时钟、重建计数和无 GPU 文本度量桩覆盖：状态判等与条件订阅、派生状态、跨线程与构建期间写入、静默/重置/BoolSetting、资源通知、稳定身份与记忆清理、失败重试、Layer/Clip/Viewport 坐标、动态输入、配置 Dropdown 首次展开的输入框同步与失败回滚、动画反向/结束/销毁和周期任务。

```powershell
.\gradlew.bat :common:test :common:compileJava :common:compileKotlin :fabric:compileJava :fabric:compileKotlin :neoforge:compileJava :neoforge:compileKotlin
.\gradlew.bat buildRelease
```

实机验收还需覆盖主题/语言/字体切换、展开折叠、滑条与取色拖拽、IME、外部设置更新、HudEditor 移动缩放和 Troll 惯性滚动。单元测试不代替 GPU、系统 IME 和游戏中提取/提交效果检查；没有据此推断 FPS 提升。

## Compose 参考

- [SnapshotStateObserver](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/snapshots/SnapshotStateObserver.kt)：按作用域观察读取与清理订阅。
- [Composition](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Composition.kt)：读取/写入失效、提交与销毁。
- [BroadcastFrameClock](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/BroadcastFrameClock.kt)、[AnimateAsState](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/AnimateAsState.kt)：宿主时钟、动画记忆与提交后目标更新。

坐标与 layer 规则见 [GUI layer 重构](gui-layer-refactor.md)。
