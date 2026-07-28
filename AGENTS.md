# Epsilon 本体开发

## 项目定位

Epsilon 是面向 Minecraft 客户端的多加载器工具模组，同时支持 Fabric 和 NeoForge。项目把共享业务代码放在 `common/`，再由两个加载器子项目复用同一份 Java 源码与资源。

本文档描述本体仓库的实际架构和开发约定。遇到文档与源码不一致时，以当前源码、`gradle/libs.versions.toml` 和本地 Minecraft 参考源码为准，并在同一次修改中更新本文档。

## 开发前的强制检查

1. 先查看 `git status --short`，不得覆盖或回滚不属于本次任务的改动。
2. 先查看 `gradle/libs.versions.toml`，确认 Minecraft、加载器、映射和依赖版本。
3. 涉及 Minecraft 类、方法、字段、渲染流程、网络包或 Mixin 目标时，必须直接查阅当前版本源码：

   `references/Fabric-26.1.2/`、`references/NeoForge-26.1.2/`

4. 不得根据训练数据或记忆猜测 Minecraft API。类名存在并不代表方法签名、调用时机或线程约束仍然相同。
5. 涉及 Fabric 或 NeoForge API 时，再查对应加载器实现和官方文档；不要把加载器专有调用放进 `common/`。
6. 修改本文档描述的架构、公共 API、配置格式、资源格式、注册流程或构建流程时，必须同步更新 `AGENTS.md`。

可优先用 `rg` 定位参考源码，例如：

```powershell
rg -n "class Minecraft|record KeyEvent" references/Fabric-26.1.2 -g "*.java"
rg -n "methodName" references/Fabric-26.1.2/net/minecraft -g "*.java"
```

## 仓库结构

```text
Epsilon/
├── common/       # 共享核心：模块、事件、GUI、渲染、配置、工具类和公共资源
├── fabric/       # Fabric 启动、Addon 入口、资源重载和平台 Mixin
├── neoforge/     # NeoForge 启动、事件桥接和平台 Mixin
├── buildSrc/     # Gradle 约定插件
├── gradle/       # 版本目录与 Gradle Wrapper 配置
├── scripts/      # 维护脚本，目前主要是 i18n 同步脚本
├── references/   # 当前 Minecraft/Fabric 反编译参考源码
└── .github/      # CI 工作流
```

### 分层边界

- `common/` 可以调用 Minecraft API，但不能导入 `net.fabricmc.*`、`net.neoforged.*` 或其他 ModLoader API。
- 加载器专有功能放在 `fabric/` 或 `neoforge/`。需要共享能力时，先在 `common/` 定义与加载器无关的协议或数据结构，再由平台层接入。
- `multiloader-loader` 会把 `common/src/main/java`、生成的 `BuildConfig` 和 `common/src/main/resources` 加入两个加载器子项目；不要在三个子项目复制同一份共享实现。
- Access Widener 只服务 Fabric，Access Transformer 只服务 NeoForge。修改共享代码所需的访问权限时，要核验两个平台是否都需要对应条目。

## 构建与版本

### 版本来源

- `gradle.properties`：Epsilon 自身的 `version`、`group`、`mod_id`、`mod_name`、`mod_author`、许可证和描述。
- `gradle/libs.versions.toml`：JDK、Minecraft、NeoForm、Fabric API、Fabric Loader、NeoForge、Mixin、Sodium、测试库等依赖版本。
- 根 `build.gradle.kts` 把版本目录中的值映射为各子项目使用的 Gradle 属性；不要在子项目脚本再次硬编码版本。
- 普通开发构建会在版本后追加当前 Git 短提交号；`buildRelease` 使用 `gradle.properties` 中的纯发布版本。
- `common/build.gradle.kts` 生成 `com.github.epsilon.BuildConfig`，当前只暴露 `MOD_ID` 和有效构建版本。

### 约定插件

- `multiloader-common.gradle.kts`：Java 25 工具链、仓库、资源展开、Jar 元数据、源码 Jar、发布和 `buildRelease`。
- `multiloader-loader.gradle.kts`：把 `:common` 的 Java、资源和生成源码加入 Fabric/NeoForge 编译与打包流程。

### 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat buildRelease --stacktrace
.\gradlew.bat :common:test
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

CI 使用 Java 25 执行 `buildRelease --no-daemon --stacktrace`，并上传 Fabric 与 NeoForge Jar。提交前至少运行与改动范围匹配的任务；共享代码、资源或 Mixin 变更应运行完整 `buildRelease`。

当前仓库没有 Java 测试文件。新增可脱离游戏运行的配置、解析、排序或数学逻辑时，应优先在 `common/src/test/java` 添加 JUnit 5 测试。

## 启动与生命周期

两个平台都通过平台 Mixin 注入 `Minecraft` 构造函数尾部，然后进入各自的 `Loader`：

```text
Minecraft.<init> TAIL
  -> fabric/neoforge Loader
  -> EpsilonFabric/EpsilonNeoForge
  -> 收集并注册 Addon
  -> EpsilonCommon.init()
```

`EpsilonCommon.init()` 当前顺序：

1. 设置 `Constants.mc`，注册 `com.github.epsilon` 包的 EventBus lambda factory。
2. `ModuleHolder.initModules()`。
3. `HudElementHolder.initElements()`。
4. `AddonHolder.setupAddons()`。
5. `ConfigHolder.initConfig()`。
6. 选择当前语言。
7. `Managers.initManagers()`。
8. 初始化 `Render3DScheduler`。
9. 生成空 i18n 模板，并注册退出时保存配置的 shutdown hook。

不要随意改变这个顺序。Addon 必须在 `setupAddons()` 之前完成收集；配置加载依赖模块、HUD 和 Addon Setting 已经注册；运行时模块若在初始化阶段启用，还要注意 Managers 是否已经可用。

## `common` 核心包

所有共享 Java 代码位于 `common/src/main/java/com/github/epsilon/`。

| 包 | 职责                                                                                     |
|---|------------------------------------------------------------------------------------------|
| `addon/` | `EpsilonAddon`、公共 Addon 收集事件与 `AddonBootstrap`                                   |
| `assets/` | 配置迁移、i18n、资源位置工具                                                             |
| `elements/` | `HudModule`、若干个已注册 HUD 元素及通知模型                                             |
| `events/` | 自定义 EventBus、监听器实现和事件类型                                                    |
| `graphics/` | Lumin GPU 抽象、buffer、renderer、shader、2D/3D scheduler、字体与即时绘制                |
| `gui/` | Dropdown、Panel、HUD 编辑器、主菜单、UI Tree/Scene、主题和控件                           |
| `holders/` | 模块、HUD、Addon、配置、翻译、Renderer、RenderTarget、Shader、纹理缓存的所有权与生命周期 |
| `interfaces/` | Mixin accessor/duck 接口                                                                 |
| `managers/` | Rotation、Target、Extrapolation、Health、Packet、Friend、Sound、Notification、Timer      |
| `mixins/` | 共享客户端 Mixin；实际启用列表以 `epsilon.mixins.json` 为准                              |
| `modules/` | `Module`、`Category`、`ClientSetting` 和 combat/player/movement/render 模块              |
| `settings/` | `SettingHost` DSL、分组、布局规划和 10 种 Setting 实现                                   |
| `utils/` | client、combat、math、network、player、render、rotation、timer、world 工具               |

截至本文档更新时，`ModuleHolder` 注册若干个本体模块（含 `ClientSetting`），`HudElementHolder` 注册若干个 HUD 元素。这个数字仅用于发现注册遗漏；新增功能时仍以两个 Holder 的注册表为准并同步 i18n。

## Module 开发

### 基本模式

本体功能模块继承 `Module`。现有模块通常使用单例和私有构造函数：

```java
public class MyModule extends Module {

    public static final MyModule INSTANCE = new MyModule();

    private final SettingGroup sgGeneral = settingGroup("General");
    private final BoolSetting enabledOption = boolSetting("Enabled Option", true).group(sgGeneral);
    private final DoubleSetting range = doubleSetting(
            "Range", 3.0, 1.0, 6.0, 0.1,
            enabledOption::getValue
    ).group(sgGeneral);

    private MyModule() {
        super("My Module", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
    }
}
```

约定：

- 本体模块优先采用 `public static final ... INSTANCE`，并使用私有构造函数。维护已有例外时遵循该类现状，不要为了形式统一做无关重构。
- `Module` 构造函数本身是 public；单例约束来自具体模块，而不是基类强制。
- Setting 必须是实例字段，字段初始化会自动通过 `SettingHost.addSetting()` 注册。
- 世界内事件先调用 `nullCheck()`；只依赖主菜单或资源系统的处理器按实际前置条件判断。
- 启用时 `Module.setEnabled(true)` 先订阅事件、发送通知，再调用 `onEnable()`；禁用时先取消订阅、发送通知，再调用 `onDisable()`。
- 模块禁用必须恢复按键、计时器、物品栏、旋转 pending 状态、缓存和其他外部状态。
- 内置模块必须加入 `ModuleHolder.initModules()`；不要只创建 `INSTANCE` 而漏掉注册。
- `Module.resetCustomState()`、`saveCustomState()`、`loadCustomState(JsonObject)` 用于 Setting 之外的持久化状态。只有确有自定义状态时才重写。
- `setDefaultEnabled()` 和 `setDefaultHidden()` 同时影响 reset 行为；普通模块默认 disabled、hidden。
- 键位默认 `-1`。`Module.BindMode.Toggle` 按下切换，`Hold` 按下启用、松开禁用；鼠标键通过 `KeybindUtils` 编码。

### Setting DSL

`Module` 与 `EpsilonAddon` 都实现 `SettingHost`，共享同一套 DSL，也都支持适用类型的 `onChanged` 重载。不要在文档或实现中再假设 Addon 不支持回调。

可用设置：

- `boolSetting`、`intSetting`、`doubleSetting`、`enumSetting`、`colorSetting`
- `stringSetting`、`stringListSetting`、`keybindSetting`、`buttonSetting`
- `blockListSetting`、`itemListSetting`、`entityTypeListSetting`、`enchantmentListSetting`、`soundEventListSetting`

每个支持的 DSL 都以 `SettingHost.java` 中的当前重载为准。依赖是 `Setting.Dependency`，返回 `false` 时设置不可用/不可见：

```java
private final BoolSetting advanced = boolSetting("Advanced", false);
private final IntSetting threshold = intSetting(
        "Threshold", 50, 0, 100, 1,
        advanced::getValue,
        value -> refresh(value)
);
```

- 使用 lambda 或方法引用延迟读取其他 Setting，不要在字段初始化时直接固化依赖结果。
- `settingGroup(name)` 按名称忽略大小写复用已有分组。
- `.group(group)` 只指定 GUI 分组，不负责注册 Setting。
- `.rootSetting()` 表示该值由根配置单独持久化；目前 `ClientSetting.showWelcomeScreen` 使用它。
- `.applyWhenRelease()` 用于滑动或编辑结束后再应用昂贵更新。
- `Setting.isAvailable()` 的实际语义由 dependency 决定；所有 DSL 默认传入恒真的 dependency。

## Addon 开发

`EpsilonAddon` 实现 `SettingHost`，提供元信息、Addon 自身设置和模块注册：

- 重写 `onSetup()`；可选重写 `getDisplayName()`、`getDescription()`、`getVersion()`、`getAuthors()`。
- 在 `onSetup()` 中通过受保护的 `registerModule(module)` 注册 Addon 模块。
- Addon ID 必须非空且全局唯一；重复 ID 会被 `AddonHolder` 忽略。
- Addon 必须在 `AddonHolder.setupAddons()` 前注册。该方法只执行一次，晚注册对象不会自动执行初始化。
- Fabric 通过自定义 entrypoint key `epsilon:addon` 收集实现 `FabricEpsilonAddonEntrypoint` 的入口。
- NeoForge 通过 `NeoForge.EVENT_BUS` 发布平台 `EpsilonAddonSetupEvent` 收集 Addon。
- 外部 Addon 若在 `com.github.epsilon` 之外声明 `@EventHandler`，需要为自己的包前缀注册 EventBus lambda factory，否则订阅时会失败。

## 事件系统

### EventBus 规则

`EventBus.INSTANCE` 是独立于加载器事件系统的精确类型事件总线：

```java
EventBus.INSTANCE.subscribe(object);
EventBus.INSTANCE.unsubscribe(object);
EventBus.INSTANCE.subscribe(SomeStaticListener.class);
EventBus.INSTANCE.post(event);
```

- `subscribe(object)` 订阅实例方法；`subscribe(Class)` 只订阅 static 监听器。
- 监听方法必须有 `@EventHandler`、返回 `void`、只有一个非 primitive 参数。
- EventBus 按事件的精确运行时 class 查找监听器，不会把子类事件自动分发给父类参数监听器。例如监听 `Render2DEvent` 不会收到 `Render2DEvent.HUD`。
- priority 数值越大越先执行。`EventPriority` 为 `HIGHEST=200`、`HIGH=100`、`MEDIUM=0`、`LOW=-100`、`LOWEST=-200`。
- 同优先级保留插入顺序，不应依赖跨对象的隐式注册先后；有明确顺序需求时显式设置 priority。

### 可取消事件

`Cancellable` 是类，不是接口。可取消事件通过继承它获得：

```java
event.cancel();
if (event.isCancelled()) {
    return;
}
```

不存在 `setCancelled(boolean)`。EventBus 在某个监听器取消事件后立即停止调用后续监听器。

### 当前事件分组

实际字段和构造参数以 `common/src/main/java/com/github/epsilon/events/impl/` 为准。

| 分组 | 事件 |
|---|---|
| Tick/生命周期 | `ClientTickEvent.Pre/Post`、`PlayerTickEvent.Pre/Post`、`GameJoinedEvent`、`GameLeftEvent`、`LevelUpdateEvent`、`RespawnEvent` |
| 渲染 | `Render2DEvent.Level/HUD`、`Render3DEvent`、`AfterRender3DEvent`、`RotationAnimationEvent`、`ChunkOcclusionEvent` |
| 输入/界面 | `KeyPressEvent`、`MousePressEvent`、`MouseScrollEvent`、`KeyboardInputEvent`、`OpenScreenEvent` |
| 网络 | `PacketEvent.Send/Receive`、`SendPositionEvent` |
| 战斗/交互 | `AttackEntityEvent`、`AttackSlowDownEvent`、`AttackYawEvent`、`RightClickEvent`、`UseItemEvent`、`StartUseItemEvent`、`SwingHandEvent` |
| 方块 | `BlockCollisionEvent`、`StartDestroyBlockEvent`、`DestroyBlockEvent`、`DestroyedBlockEvent`、`PlaceBlockEvent` |
| 移动 | `MoveEvent`、`StrafeEvent`、`TravelEvent`、`JumpEvent`、`SlowdownEvent`、`FallFlyingEvent`、`FireworkRotationEvent` |
| Raytrace | `RaytraceEvent`、`UseItemRaytraceEvent` |

`Render2DEvent` 当前携带的是 Minecraft 26.1.2 的 `GuiGraphicsExtractor`，不是旧版 `GuiGraphics`。修改 2D 渲染代码前必须核验本地参考源码中的提取/提交渲染流程。

## Mixin 开发

### 配置与访问权限

- 共享 Mixin：`common/src/main/resources/epsilon.mixins.json`
- Fabric Mixin：`fabric/src/main/resources/epsilon.fabric.mixins.json`
- NeoForge Mixin：`neoforge/src/main/resources/epsilon.neoforge.mixins.json`
- Fabric Access Widener：`common/src/main/resources/epsilon.accesswidener`
- NeoForge Access Transformer：`common/src/main/resources/META-INF/accesstransformer.cfg`

规则：

1. 先在 `references/Fabric-26.1.2/net/minecraft/` 找到目标类并核对完整方法描述符、局部变量和调用点。
2. 共享目标优先放 `common/mixins`；只有加载器字节码或调用链不同才拆到平台 Mixin。
3. 类名使用 `Mixin<目标类名>`。优先 `@Inject`、MixinExtras 的 `@WrapOperation` 等局部注入，避免 `@Overwrite`。
4. 新 Mixin 必须加入相应 JSON。删除或重命名 Mixin 时同步移除旧条目。
5. `defaultRequire` 为 1，错误注入点会使启动失败；不要用 `require = 0` 掩盖未核验的目标。
6. Sodium 条件 Mixin 由两个平台的 `IMixinConfigPlugin` 判断依赖是否加载；添加兼容 Mixin 时沿用该门控方式。
7. Mixin 发布事件时，事件对象字段必须足够表达可修改状态，并在注入点明确处理 `isCancelled()` 或事件修改后的值。

## Holders、Managers 与配置

### Holders

| Holder | 职责 |
|---|---|
| `ModuleHolder` | 注册本体/Addon 模块，处理键盘与鼠标绑定 |
| `HudElementHolder` | 注册 HUD，统一通过 `UiScene` 渲染并处理原版 overlay |
| `AddonHolder` | Addon 去重、一次性 setup 与查询 |
| `ConfigHolder` | 多配置、导入导出、Setting/custom state、好友与迁移 |
| `TranslateHolder` | 跟踪 `TranslateComponent` 并在切换语言时刷新缓存 |
| `RendererHolder` | 跟踪并销毁 `IRenderer` GPU 资源 |
| `RenderTargetHolder` | 跟踪并销毁 Lumin render target |
| `ShaderHolder` | 手部/箱子 outline 和屏幕后处理状态 |
| `TextureCacheHolder` | `Identifier -> LuminTexture` 缓存与释放 |

### Managers

通过 `Managers` 静态字段访问运行时管理器：

`ROTATION`、`EXTRAPOLATION`、`TARGET`、`HEALTH`、`C2SPACKET`、`S2CPACKET`、`FRIEND`、`SOUND`、`NOTIFICATION`、`TIMER`。

这些字段在 `Managers.initManagers()` 前可能为 null。不要为它们另造 `INSTANCE`，也不要长期缓存可替换的 `Managers.ROTATION` 引用。

### 配置系统

`ConfigHolder` 的根目录是用户目录下的 `.epsilon/`，不是仓库或游戏运行目录：

```text
~/.epsilon/
├── active-config.txt
├── client-settings.json
├── configs/<name>/
├── imports/
└── exports/
```

- 模块、HUD、Addon Setting 和好友按当前活动配置保存。
- `client-settings.json` 保存标记为 root 的客户端设置。
- 配置支持新建、切换、删除、另存、重载、Zip 导入和导出。
- 导入 Zip 必须继续使用 `ConfigHolder` 的安全解压逻辑，不能另写不校验路径穿越的实现。
- 修改配置 schema 时同步更新版本、迁移逻辑和本文档；不得直接丢弃旧配置。
- `saveNow()` 已在 JVM shutdown hook 调用，但影响数据完整性的操作仍应在合适时机主动保存。

## RotationManager

`RotationManager` 是抽象基类，实例只能通过 `Managers.ROTATION` 获取。模式由 `ClientSetting.rotationMode` 选择：

- `SilentRotationManager`：静默发包旋转。
- `SnapRotationManager`：直接修改/恢复玩家旋转。

主要 API：

```java
Managers.ROTATION.setRotations(rotation, speed);
Managers.ROTATION.setRotations(rotation, speed, Priority.High);
Managers.ROTATION.setRotations(rotation, speed, raytrace);
Managers.ROTATION.setRotations(rotation, speed, raytrace, Priority.High);

Rot2f current = Managers.ROTATION.getRotation();
Rot2f previous = Managers.ROTATION.getLastRotation();
boolean active = Managers.ROTATION.isActive();
```

- 旋转类型是 `com.github.epsilon.utils.rotation.Rot2f`。
- Rotation priority 与 EventBus priority 是两套系统：`Lowest=0`、`Low=10`、`Medium=50`、`High=100`、`Highest=1000`。
- 仅当新 priority 不低于当前活动 priority 时才覆盖请求。
- `Function<Rot2f, Boolean>` raytrace 会在平滑随机偏移校验中多次调用，必须无副作用。
- 服务端位置/旋转包会设置 S08 重置标记；下一次请求先同步真实视角。
- 旋转接近玩家真实角度时自动结束，没有 callback 或 `isDone()`。
- 需要等待命中后攻击/放置时，由模块保存 pending 状态，每 tick 继续请求旋转并用当前 `getRotation()` 做 raytrace，再执行一次性动作。
- 切换旋转模式会 `copyStateFrom()` 并替换实例，因此每次从 `Managers.ROTATION` 读取。

## Lumin、GUI 与 HUD

### 资源生命周期

- GPU renderer、render target、字体 atlas 和 shader 必须在渲染线程创建/使用。
- `RectRenderer.create()`、`TextRenderer.create()` 等工厂会向 `RendererHolder` 注册；Lumin render target 工厂会向 `RenderTargetHolder` 注册。
- 需要在字段中持有 renderer 时，使用 `Suppliers.memoize(Renderer::create)` 延迟到首次渲染，避免类初始化线程创建 GPU 资源。
- 不再使用的资源调用 `close()`；全局销毁由对应 Holder/Lumin 生命周期处理。

### GUI/HUD 首选路径

Panel、Dropdown、popup 和 HUD chrome 使用声明式流水线：

```text
UiTree / UiTree.Scope
  -> LuminUiRenderer
  -> UiRenderBatch
  -> Render2DScheduler
  -> 合批 renderer
```

- 一个 Screen 或 HUD 帧共享一个 `UiScene`；调用 `beginFrame()` 后提交各语义 `UiLayer`，最后 `endFrame()`。
- 业务 UI 优先构造 `UiTree`，不要为每个面板各自创建一组 renderer。
- `UiRenderBatch.view()`/Scene layer 用于局部层级；scissor 和 popup 层级必须通过共享 scheduler 管理。
- `HudModule.render(DeltaTracker)` 内通过 `renderScope()` 提交 UI；只有原版物品等无法进入 Lumin batch 的内容才放在 `renderOverlay(GuiGraphicsExtractor, DeltaTracker)`。
- HUD 尺寸变化调用 `setBounds()`，位置使用 anchor/move API；不要直接绕过 anchor 状态写持久化坐标。

### 直接 Renderer

直接渲染仍用于部分模块和底层组件：`RectRenderer`、`RoundRectRenderer`、`RoundRectOutlineRenderer`、`TextureRenderer`、`ShadowRenderer`、`TriangleRenderer`、`TextRenderer`。

```java
private final Supplier<RectRenderer> renderer = Suppliers.memoize(RectRenderer::create);

renderer.get().addRect(x, y, width, height, color);
renderer.get().drawAndClear();
```

`IRenderer` 的帧内约束必须遵守：

- `drawAndClear()` 等价于 `draw()` 后 `clear()`。
- 同一帧调用 `clear()` 或 `drawAndClear()` 后，不得再次对该 renderer 调用 `draw()`/`drawAndClear()`。
- 内容长期不变时可以只 `draw()` 并复用 GPU 数据；内容变化时在下一帧重新填充。
- 需要同帧多个独立 pass 时，使用不同 renderer 或 `Render2DScheduler`，不要反复 clear/rebuild 同一个实例。
- `ShadowRenderer` 与 `UiTree.Scope.shadow(...)` 的 segmented 重载接受 `segmentRects`（每段依次为 `x, y, width, height`）、`segmentRadii` 和 `segmentCount`；一次最多提交 64 段，所有段按并集生成一份阴影。

### 3D Scheduler

普通方框、边框、面、线和 3D 模糊框提交到 `Render3DScheduler.INSTANCE`。它以 `Render3DEvent` priority `-999` 统一 flush 并清空，因此模块应在更高优先级监听器中提交命令。底层特殊效果才直接使用 `LuminImmediateRenderer`。

### 文本与特效

- 默认字体通过 `StaticFontLoader.defaultFont()` 获取，可随客户端设置切换。
- 固定字体：`StaticFontLoader.ICONS`、`JURA_LIGHT`、`OSAKA_CHIPS`。
- `TextRenderer.getWidth/getHeight` 必须使用与绘制相同的 scale 和 font loader。
- 后处理入口包括 `BlurShader.INSTANCE`、`FXAAShader.INSTANCE`、`FilterShader.INSTANCE` 和 `ShaderHolder`；调用前核验 render target、采样器和当前 Minecraft 26.1.2 RenderPipeline 状态。

## i18n

### Key 规则

- 本体模块：`epsilon.modules.{module name lower-case}`。
- 本体 HUD：`epsilon.elements.{element name lower-case}`。
- 分类：`epsilon.categories.{category}`。
- Addon 模块：`{addonId}.modules.{module name lower-case}`。
- Addon 自身设置：`{addonId}.settings.{setting/group name lower-case}`。
- Module/HUD 的 Setting、SettingGroup 和 Enum 选项是所属组件 key 的子 key。
- 名称使用 `toLowerCase()`，空格会保留，不会自动改成下划线。
- 静态 UI 文案集中到 `EpsilonTranslations`，不要在各 GUI 类散落重复 key。

`EpsilonTranslateComponent.create(prefix, suffix)` 自动添加 `epsilon.`；任意 owner 使用 `DefaultTranslateComponent.create(fullKey)`。

### JSON 格式

语言文件位于 `common/src/main/resources/assets/epsilon/i18n/en_us.json` 和 `zh_cn.json`。格式是与 dotted key 对应的嵌套 object；叶节点必须是字符串。父 key 同时有自身翻译和子 key 时使用保留属性 `_value`：

```json
{
  "epsilon": {
    "modules": {
      "kill aura": {
        "_value": "Kill Aura",
        "mode": {
          "_value": "Mode",
          "single": "Single"
        }
      }
    }
  }
}
```

- `_value` 只能保存当前 object 对应 key 的翻译，不能作为普通路径段。
- 不允许数组、数字、布尔或 null。
- 资源重载时 `EpsilonLanguageManager` 会读取资源栈、刷新 `TranslateHolder` 并清理纹理缓存。

### 同步流程

新增/删除模块、HUD、Setting、SettingGroup、Enum 选项或 `EpsilonTranslations` 后：

1. 让 `I18NFileGenerator.generate("epsilon-empty-i18n.json")` 生成当前模板；可传 owner ID 只生成一个 owner。
2. 运行 `python scripts/complete_i18n.py` 补全、排序并删除多余 key；需要时使用 `--owner epsilon` 或 `--owner <addonId>`。
3. 人工填写新增翻译，不要把空模板直接当成已完成语言文件。
4. 校验 `en_us.json`、`zh_cn.json` 都是合法嵌套 object，并在完整构建中验证资源处理。

## 代码规范与提交前检查

- Java 25，UTF-8，沿用相邻代码的格式和命名。
- 新增解释性注释使用中文；只注释不明显的约束、线程或算法原因。
- Logger 使用 `Constants.LOGGER`。
- Minecraft 实例使用 `Constants.mc`，Module/HudModule 内优先使用继承的 `mc`。
- 不要吞掉异常；边界层需要隔离单个 Addon/资源失败时，应记录包含上下文的日志。
- 不做与任务无关的重构、批量格式化或生成文件改写。
- 修改共享行为后至少检查 Fabric 和 NeoForge 编译；Mixin、资源、启动或 Gradle 变更运行完整 `buildRelease`。
- 最终审阅 `git diff --check`、`git diff -- AGENTS.md` 和 `git status --short`。

## 外部文档

- NeoForge：https://docs.neoforged.net/
- NeoForge Primer：https://docs.neoforged.net/primer/docs/
- Fabric：https://docs.fabricmc.net/develop/
- Mixin：https://wiki.fabricmc.net/tutorial:mixin_introduction
- Mixin Examples：https://wiki.fabricmc.net/tutorial:mixin_examples
- Porting Primers：https://gu-zt.github.io/Porting-Primers/

外部文档用于理解加载器与 Mixin 机制；具体到本项目当前 Minecraft 版本的类和签名，仍以 `references/Fabric-26.1.2/` 和 `references/NeoForge-26.1.2/` 为准。
