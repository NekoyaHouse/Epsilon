# LuminUI recompose：状态驱动的增量绘制

> 本文记录 LuminUI 在 layer 重构之后新增的第二套绘制模型：**recompose（状态驱动重建 + 节点缓存重放）**，
> 以及 GUI 各子系统迁移到该模型的现状、约束与遗留项。
> 相关前置文档：[`gui-layer-refactor.md`](gui-layer-refactor.md)（layer 扩展函数化与 Compose 风格 API）。

## 1. 动机

layer 重构之后，GUI 每帧仍然从头重建整棵 `UiTree`：一个展开的 panel 约 200–400 个节点对象，
每个文本都要测量、每个圆角/阴影都要重新算颜色插值。**内容没变的帧也在做同样的工作**。

`LuminUI` 在保留立即模式的同时引入 Compose 式模型：

- 内容是**状态**的函数：读 `MutableState` 时登记依赖；
- 状态写入只让**读它的那部分**失效；
- 其余帧直接重放上一轮构建好的节点；
- 列表项逐项缓存，改一个设置只重建那一行。

## 2. 两套绘制模型

| 模型 | 入口 | 语义 | 典型用途 |
|---|---|---|---|
| **recompose**（默认推荐） | `LuminUIScene` / `LuminUISceneImmediate` | 默认 `LuminUIScene`：只在依赖状态变化时重建，其余帧重放缓存 | Panel、Dropdown、popup、HudEditor |
| **立即** | `LuminUISceneImmediate`（旧 `Content` 语义） | 每帧重建 | HUD 元素、时间驱动的装饰（Reisa 立绘） |

两者可以嵌套：recompose 块内的立即子块用 `LuminUIGroupImmediate { }` 表达（它每帧重建，但作为独立单元参与拼装，
不会污染父级缓存）；立即块内也可以继续用 `LuminUIItem` 逐项缓存。

### 入口一览

```kotlin
// 场景级
scene.LuminUIScene(bounds, layer, relative, key) { … }
scene.LuminUISceneImmediate(bounds, layer, relative) { … }

// 批次级（组件只拿到 UiRenderBatch 时；独立批次会退化为立即模式）
batch.LuminUIScene(bounds, baseLayer, key) { … }
batch.LuminUISceneImmediate(bounds, baseLayer) { … }

// 块内的子单元
LuminUIGroup(key) { … }            // 子缓存单元，失效粒度更细
LuminUIGroupImmediate { … }        // 每帧重建的子块（拉取式动画可在此使用）
LuminUIItem(key, revision) { … }   // 逐项缓存（列表行）
LuminUIItems(items, key, revision) { … }
```

`key` 是**外部失效键**：`bounds` 或 `key` 变化即整层重建。列表项用 `LuminUIItem` 时，
`key` 必须对列表项稳定（例如 setting 实例），`revision` 必须覆盖「静止时也会变化」的输入。

## 3. 运行时结构

```
LuminRuntime                    渲染线程单例：当前重建上下文、动画表、帧时钟
├─ recordRead(state)            重建期间把 state 记进当前 composition 的依赖集
├─ invalidate(state)            写入时让订阅者标脏
├─ registerAnimatable(...)      动画自注册，结束后自动摘除
└─ advanceAnimations(now)       每帧由 UiScene.beginFrame() 推进

LuminState<T>                   可订阅状态基类
├─ MutableState<T>              mutableStateOf(initial)，写入用 == 判等
└─ SettingState<V>              Setting.asState() 适配器：读缓存、写回 setting.value

LuminComposition                recompose 缓存单元
├─ segments: [字面节点段 | 子单元段]   父级重放不牵动子单元
├─ dependencies                      重建时重新收集，自动收敛
└─ treeScope: UiTree.Scope           跨帧复用的作用域（逐项缓存随它存活）

LuminSceneRoot                  场景级位置化注册表（入口按出现顺序编号）
UiTree.Scope.itemCache          逐项节点缓存（LuminSceneCache）
```

### 为什么逐项缓存挂在 `UiTree.Scope` 上

缓存本质是「节点收集 + 捕获 + 重放」的一部分，而这三件事本来就由 `UiTree.Scope` 负责
（`nodes`、`openCapture`、`appendNodes`）。因此：

- **作用域存活多久，缓存就存活多久**；
- recompose 入口各自持有长生命周期 `Scope`（`LuminComposition.treeScope`），每帧 `clear()` 后重新填充，
  缓存因此能跨帧命中；
- `UiRenderBatch.Content` 复用场景入口的作用域，面板内容里的列表项同样能跨帧命中；
- 一次性作用域（独立批次）等价于不缓存，行为退化为立即模式。

全仓库只有 `UiTree.Scope` 一处构造 `LuminSceneCache`，面板/场景不再各自持有缓存对象。

## 4. 状态与动画

### 状态

```kotlin
val hover = mutableStateOf(0.0f)
var hovered by mutableStateOf(false)      // 属性委托

// Setting 接入：同一个 Setting 共享同一个适配器
val enabled by setting.asState()
```

- 写入用 `==` 判等，值没变不触发任何重建；
- 读取只在 recompose 期间登记依赖，立即模式下是纯读取；
- `Setting.asState()` 通过 `Setting.addChangeListener` 推送（`writeValue` 派发），
  与既有 `onChanged` 互不影响；`setValueSilently`（配置加载）不推送，加载后需 `scene.invalidateAllUi()`。

### 动画

拉取式 `animate(animation, target)` **不能在 recompose 块内使用**：它依赖每帧被调用才前进，
在只于状态变化时执行的块里会冻结。因此：

```kotlin
// 旧（仅立即模式可用）
val progress = animate(animation, target)

// 新：推式动画，帧循环推进，结束后自动停止写状态
val progress = rememberAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
progress.target = if (hovered) 1.0f else 0.0f
val value = progress.value
```

误用保护：recompose 块内调用 `UiScope.animate(...)` 会直接抛 `IllegalStateException`，
错误信息提示改用 `LuminAnimatable` 或 `LuminUIGroupImmediate`（按团队决定：宁可直接报错，也不要静默冻结）。

## 5. GUI 迁移现状

| 区域 | 状态 |
|---|---|
| `gui/lib`（运行时、入口、图元、控件） | ✅ recompose 基础设施 |
| `gui/panel/PanelScreen`（chrome 层） | ✅ `LuminUIScene(key = layout)` |
| `gui/dropdown/**`（23 文件） | ✅ 全部 pass → `LuminUIScene`；Reisa 立绘保留立即模式 |
| `gui/panel/popup/**`（6 个弹窗 + host） | ✅ `LuminUIScene` + `LuminUIItem` 行缓存 |
| `gui/panel/view/ClientSettingPanel` | ✅ `LuminUIScene`，指示条/tab hover → `LuminAnimatable` |
| `gui/hudeditor/HudEditorScreen` | ✅ 6 层 `LuminUIScene`，面板区域 `LuminUIGroupImmediate` |
| `gui/panel/view/{ModuleDetailPanel,ModuleListPanel,CategoryRailPanel}` | 🚧 仍立即模式，但行/项已用 `LuminUIItem` 跨帧缓存 |
| `gui/panel/view/settings/*ClientSettingTab` | 🚧 仍 `renderBatch.Content`，行已逐项缓存 |
| `elements/**`（HUD 元素） | ⏸ 按约定保持立即模式（数据每帧变化） |
| `gui/troll/**` | ⏸ 未迁移（layer 已于前一阶段迁移） |

### 逐项缓存（`LuminUIItem`）

行/项按 `key + revision` 缓存节点；命中时只做指针拷贝。典型 revision：

```kotlin
val animating = !hover.isFinished || row.hasActiveAnimation()
val revision = row.cacheRevision() * 31L + (if (animating) System.nanoTime() else 0L)
LuminUIItem(setting to rowBounds, revision) {
    SettingRow(row, rowBounds, hover.value, mouseX, mouseY)
}
```

- `revision` 覆盖：设置值、选中/展开/监听、输入文本与光标、动画进度、行位置与宽高、i18n、主题；
- 动画进行中叠加 `nanoTime()` → 该行每帧重建；静止后 revision 稳定 → 命中缓存；
- `key` 里并入 `rowBounds` 可让滚动/布局变化自然失效（缓存条目数有界）。

## 6. 迁移新界面时的检查清单

1. **入口**：能拿到 `UiScene` 用 `scene.LuminUIScene`；只有 `UiRenderBatch` 用 `batch.LuminUIScene`。
2. **key**：把一切影响几何/静态内容的输入放进 key（bounds 已自动参与；补充 scroll、鼠标、交互事件版本、
   i18n `TranslationManager.getRevision()`、主题指纹）。
3. **动画**：块内不得调用 `animate()`；改 `LuminAnimatable`，或包 `LuminUIGroupImmediate { }`。
4. **每帧推进的值**：必须在进入子块之前刷新到字段/状态，不能依赖块内执行（命中缓存的帧不会执行）。
5. **列表**：重复项用 `LuminUIItem`；`key` 稳定、`revision` 完整。
6. **副作用**：块内只在重建时执行一次，写设置/发网络包必须放到事件回调。
7. **坐标系**：块内坐标仍相对作用域原点，不要引入绝对定位。

## 7. 已知陷阱与取舍

| 陷阱 | 说明 / 处理 |
|---|---|
| 缓存节点烘焙绝对坐标 | 几何必须进 key，否则重放旧坐标 |
| `LuminUIGroupImmediate` 的父级重建帧 | `group()` 内渲染一次 + `emit` 再执行一次，滚动平滑在该帧前进两步；只影响入场/改尺寸/拖动帧 |
| 从未启动过的 `Animation` 被判定为活动 | 新增 `Animation.isAdvancing()`（`!isFinished && millis != 0`）避免缓存永久失效 |
| 主题在面板打开期间切换 | `PanelScreen.drawChrome` 的 key 目前只含 `layout`，切主题可能不刷新；建议接主题指纹或主题变更时 `scene.invalidateAllUi()` |
| 弹窗缓存跨实例保留 | 缓存挂在共享 entry scope 上；key 含 setting 实例，不会串内容，只是少量常驻条目 |
| `LuminAnimatable` 依赖宿主 | 宿主必须每帧调用 `UiScene.beginFrame()`（推进动画）；新增宿主需遵守 |
| `UiScrollBar` 悬停动画在库外 | dropdown 用 240ms 宽限期兜底（鼠标离开后 ~0.24s 额外重建） |

## 8. 相关提交

| 提交 | 内容 |
|---|---|
| `a605c18f` | 新增 LuminUI recompose 运行时：状态驱动重建与节点缓存重放 |
| `e3792112` | 列表内容改为逐行节点缓存（后被下移进 `UiTree.Scope`） |
| `5384f097` | 把逐项缓存收进场景：`LuminUIScene` 自动分割 |
| `248c244e` | 逐项缓存下沉到 `UiTree.Scope`，入口复用长生命周期作用域 |
| `4b346ae1` | dropdown / popup / HudEditor 迁移到 recompose |

## 9. 后续

1. 把 `CategoryRailPanel`（10 处拉取动画）、`ModuleDetailPanel`（6 处）、`ModuleListPanel`（2 处）的
   动画改为 `LuminAnimatable`，然后切到 `LuminUIScene`；
2. `*ClientSettingTab` 切到 `LuminUIScene`（其行已逐项缓存）；
3. 主题变更统一通过 `scene.invalidateAllUi()` 广播；
4. 实机验证清单：切换主题、拖滚动条、折叠分组、枚举展开、取色拖拽、输入框光标、外部改设置后重绘。

验证方式：`./gradlew :common:compileKotlin :fabric:compileKotlin :neoforge:compileKotlin`。
