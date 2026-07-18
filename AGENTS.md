# Epsilon 本体开发

## 概述

Epsilon 是一个面向 Minecraft 的多加载器（Multi-Loader）客户端工具模组，同时支持 Fabric 和 NeoForge，采用三层架构。

## 项目架构

```
Epsilon/
├── common/          ← 平台无关核心代码（模块、事件、图形、工具类）
├── fabric/          ← Fabric 加载器适配层
├── neoforge/        ← NeoForge 加载器适配层
├── buildSrc/        ← Gradle 自定义插件（multiloader-common、multiloader-loader）
└── docs/            ← 文档
```

> 以下结构图仅描述 `common/` 下的核心代码组织。所有平台无关代码位于 `common/src/main/java/com/github/epsilon/`。

## 关键原则
- `common/` 中的代码**不能调用任何 ModLoader 的 API**
- 需要加载器 API 时，在 `fabric/` 或 `neoforge/` 子项目中实现，通过 compat 接口传入 common
- `common/` 中的 Java 源码会被 Gradle 自动共享到 fabric/ 和 neoforge/ 的编译路径
- 编写前必须查阅 Minecraft 源码！
- 修改本文档已说明的架构、API、开发约定、文件格式或工作流时，必须在同一次修改中同步更新 `AGENTS.md`

### 查阅 Minecraft 源代码

**编写前必须查阅 Minecraft 源码！**

绝对不要基于训练数据中的知识判断版本信息。 Minecraft 版本迭代很快，任何关于最新版本号、版本兼容性、API 变更、 迁移步骤的判断都必须通过查阅官方文档或在线搜索确认。即便是"当前最新版本是什么"这种看似简单的问题，也优先查文档而非依赖记忆。
任何关于调用哪个方法、哪个事件、哪个类的判断都必须基于对当前版本 Minecraft 代码的直接查阅，而非依赖过时的训练数据。

源代码位于：`common/build/moddev/artifacts/vanilla-<游戏版本>-sources.jar`

如果不存在，执行：
```bash
./gradlew :common:downloadAssets
./gradlew :common:createMinecraftArtifacts
```

查阅后解压：
```bash
mkdir -p reference && unzip common/build/moddev/artifacts/vanilla-*-sources.jar -d reference/vanilla/
```

## 构建系统

### 版本信息

- **项目元信息**（`mod_id`、`mod_name`、`mod_author`、`version`）在 `gradle.properties` 中定义
- **依赖版本**（Minecraft、Fabric API、Fabric Loader、NeoForge、NeoForm、Mixin、Sodium 等）在 `gradle/libs.versions.toml` 中定义

编写代码时优先参考 `gradle/libs.versions.toml` 获取准确的依赖版本号。`gradle.properties` 中的 `version` 字段是 Epsilon 自身的发布版本号。

### buildSrc 约定插件

- `multiloader-common.gradle.kts` — common 子项目的共享配置：JDK 工具链、Maven 仓库、资源处理、发布配置
- `multiloader-loader.gradle.kts` — fabric/neoforge 子项目的共享配置：自动关联 `:common` 源码和资源

## 核心包结构

所有平台无关代码位于 `common/src/main/java/com/github/epsilon/`：

| 包名 | 用途 |
|------|------|
| `addon/` | Addon 基类、注册事件、Bootstrap 工具 |
| `assets/` | i18n 翻译、配置文件迁移、资源持有者 |
| `elements/` | HUD 元素基类 `HudModule` 与具体实现（`Notifications`、`BPS`、`MTF`、`Inventory`、`ModuleList`、`Potions`、`ScaffoldBlock`、`TargetHUD`、`Watermark`） |
| `events/` | 自定义事件总线与事件类型 |
| `graphics/` | Lumin Graphics 渲染框架（含 `renderers/`、`shaders/`、`text/`、`buffer/`、`immediate/`、`schedulers/` 子包） |
| `gui/` | 点击 GUI（Panel、Dropdown、HUD 编辑器、Scene 系统） |
| `holders/` | 各种持有者（`ModuleHolder`、`ConfigHolder`、`AddonHolder`、`HudElementHolder`、`RendererHolder`、`ShaderHolder` 等，负责初始化与生命周期） |
| `interfaces/` | Mixin 用的 accessor 接口（`ChatComponentAccessor`、`EntityRenderStateAccessor`、`WalkAnimationStateAccessor`） |
| `managers/` | 各种管理器（Rotation、Target、Health、Friend、Sound、Notification、Packet 等） |
| `mixins/` | Mixin 注入类 |
| `modules/` | Module 基类、Category 枚举、`ClientSetting`、若干个内置模块（combat/movement/player/render 四大类） |
| `settings/` | Setting 基类与 10 种设置类型（Bool、Button、Color、Double、Enum、Int、Keybind、RegistryList、StringList、String） |
| `utils/` | 工具类（client、combat、math、network、player、render、rotation、timer、world） |

## Module 开发

### Module 基类 (`common/.../modules/Module.java`)

所有功能模块继承 `Module`，参考现有模块（如 `common/.../modules/impl/combat/KillAura.java`）。

```java
public class MyModule extends Module {

    public static final MyModule INSTANCE = new MyModule();

    private MyModule() {
        super("My Module", Category.COMBAT); // 名称 + 分类
    }

    // -- Setting DSL --

    // Boolean 设置
    private final BoolSetting enabled = boolSetting("Enabled", true);

    // Boolean 设置 + 可见性依赖
    private final BoolSetting advanced = boolSetting("Advanced", false, () -> enabled.getValue());

    // Integer 设置 (name, default, min, max, step)
    private final IntSetting speed = intSetting("Speed", 5, 1, 10, 1);

    // Integer 设置 + 依赖
    private final IntSetting range = intSetting("Range", 4, 1, 64, 1, () -> advanced.getValue());

    // Double 设置 (name, default, min, max, step)
    private final DoubleSetting reach = doubleSetting("Reach", 3.0, 1.0, 6.0, 0.1);

    // Enum 设置
    private enum Mode { Fast, Slow }
    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Fast);

    // Enum 设置 + 依赖 + onChanged 回调
    private final EnumSetting<Mode> mode2 = enumSetting("Mode 2", Mode.Slow, () -> enabled.getValue(), newMode -> { /* ... */ });

    // Color 设置 (name, default, allowAlpha: boolean)
    private final ColorSetting color = colorSetting("Color", new Color(255, 0, 0), true);

    // Keybind 设置 (name, defaultKeyCode: int)
    private final KeybindSetting key = keybindSetting("Key", -1);

    // Button 设置 (name, action: Runnable)
    private final ButtonSetting reset = buttonSetting("Reset", () -> { /* ... */ });

    // String 设置
    private final StringSetting text = stringSetting("Text", "default");

    // -- 生命周期 --
    @Override
    protected void onEnable() {
        // 模块启用时调用
    }

    @Override
    protected void onDisable() {
        // 模块禁用时调用
    }

    // -- 事件监听 --
    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return; // 检查 mc.player == null || mc.level == null
        // 处理每 tick 逻辑
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        // 3D 渲染逻辑
        PoseStack stack = event.getPoseStack();
    }
}
```

### Module 开发约定

1. **单例模式**：每个 Module 使用 `public static final MyModule INSTANCE = new MyModule()`，构造函数私有
2. **私有构造函数**：防止外部实例化
3. **nullCheck()**：事件处理方法中先调用 `nullCheck()` 确保世界中存在玩家
4. **Setting 字段**：Setting 必须声明为类的**实例字段**，构造时初始化
5. **@EventHandler 注解**：事件监听方法必须用 `@EventHandler` 标记，且必须是**实例方法**（非 static），访问修饰符任意
6. **事件方法签名**：必须 `void` 返回，只有一个参数（事件类型），参数类型不能是原始类型
7. **注册**：内部模块在 `ModuleHolder.initModules()` 列表中注册；Addon 模块通过 `EpsilonAddon.registerModule()` 注册
8. **i18n 翻译**：需要为模块名称和设置名称添加 i18n 翻译，详见后文 i18n 章节

### Module.Setting 可见性依赖（Dependency）

Setting 的第四个参数（如果有）是一个 `Setting.Dependency` 函数式接口（`() -> boolean`），返回 `false` 时该设置在 GUI 中隐藏。

```java
private final BoolSetting enableEsp = boolSetting("Enable ESP", true);
private final ColorSetting espColor = colorSetting("ESP Color", Color.WHITE, () -> enableEsp.getValue());
// 使用已有的 EnumSetting/BoolSetting::getValue 引用作为依赖
```

也可用 lambda 实现 cross-setting 引用（A 依赖 B，而 B 也在同一个类中）：

```java
private final BoolSetting advanced = boolSetting("Advanced", false);
// 使用 lambda 延迟引用，解决声明顺序问题
private final IntSetting threshold = intSetting("Threshold", 50, 0, 100, 1, () -> advanced.getValue());
```

**注意**：EpsilonAddon 的 Setting 不支持带 `onChanged` 参数的 DSL 变体（与 Module 不同）。

### Setting 分组

```java
private final SettingGroup sgCombat = settingGroup("Combat");
private final BoolSetting autoAttack = boolSetting("Auto Attack", true).group(sgCombat);
```

### 模块键位

- `getKeyBind()` / `setKeyBind(int)` — 键位代码（GLFW key code）
- `BindMode.Toggle` — 按下切换开关（默认）
- `BindMode.Hold` — 按住启用，松开禁用

## 事件系统

### EventBus (`common/.../events/bus/EventBus.java`)

Epsilon 使用**自定义事件总线**，与 Minecraft 原生事件系统独立。

- **订阅**：`EventBus.INSTANCE.subscribe(object)` / `EventBus.INSTANCE.subscribe(Class)`
- **取消订阅**：`EventBus.INSTANCE.unsubscribe(object)` / `EventBus.INSTANCE.unsubscribe(Class)`
- **发布**：`EventBus.INSTANCE.post(event)`
- **Lambda 工厂注册**：`EventBus.INSTANCE.registerLambdaFactory("com.github.epsilon", factory)` — 已由 EpsilonCommon.init() 自动注册

Module 启用时自动 `subscribe(this)`，禁用时自动 `unsubscribe(this)`。

### 可用事件类型（`common/.../events/impl/`）

| 事件类 | 描述 |
|--------|------|
| `PlayerTickEvent.Pre` / `PlayerTickEvent.Post` | 玩家每 tick 前后（最常用，可取消 Pre） |
| `ClientTickEvent.Pre` / `ClientTickEvent.Post` | 客户端每 tick 前后 |
| `Render2DEvent.Level` / `Render2DEvent.HUD` | 2D 渲染（含 GuiGraphics） |
| `Render3DEvent` / `AfterRender3DEvent` | 3D 渲染（含 PoseStack）及渲染后回调 |
| `PacketEvent.Send` / `PacketEvent.Receive` | 网络包收发（可取消） |
| `KeyPressEvent` | 按键按下/释放 |
| `MousePressEvent` | 鼠标按键 |
| `AttackEntityEvent` / `AttackBlockEvent` / `DestroyBlockEvent` | 攻击实体/方块/破坏方块 |
| `ClickEvent` | 点击 |
| `JumpEvent` | 跳跃 |
| `CollisionEvent` | 碰撞检测 |
| `SlowdownEvent` / `AttackSlowDownEvent` | 减速（灵魂沙等）/攻击减速 |
| `StrafeEvent` / `MoveEvent` / `TravelEvent` | 侧移 / 移动 / 行进 |
| `KeyboardInputEvent` | 键盘输入 |
| `RotationAnimationEvent` | 旋转动画（可设置渲染用的 yaw/pitch） |
| `AttackYawEvent` | 攻击偏航角 |
| `SendPositionEvent` | 发送位置包（RotationManager 在此写入旋转） |
| `SwingHandEvent` | 挥手 |
| `RaytraceEvent` / `UseItemRaytraceEvent` | 射线追踪 / 使用物品射线追踪 |
| `UseItemEvent` / `StartUseItemEvent` | 使用物品 |
| `FallFlyingEvent` / `FireworkRotationEvent` | 鞘翅飞行 / 烟花旋转 |
| `LevelUpdateEvent` / `RespawnEvent` | 世界切换 / 重生 |
| ... | 其余事件及字段以 `common/src/main/java/com/github/epsilon/events/impl/` 中的当前源码为准 |

### 事件优先级

`@EventHandler(priority = EventPriority.HIGH)` — 优先级：`HIGHEST` > `HIGH` > `MEDIUM` > `LOW` > `LOWEST`

支持直接传入 int 值（如 `priority = -1000`）。**数值越大越先执行**，数值越小越晚执行。`EventPriority` 常量映射：`HIGHEST=200`, `HIGH=100`, `MEDIUM=0`, `LOW=-100`, `LOWEST=-200`。

### 可取消事件

实现 `Cancellable` 接口的事件可以通过 `event.setCancelled(true)` 取消。取消后，后续低优先级监听器不再执行。

## Mixin 开发

Mixin 类位于 `common/src/main/java/com/github/epsilon/mixins/`。

### Mixin 配置

- `common/src/main/resources/epsilon.mixins.json` — common 层 mixins
- `fabric/src/main/resources/epsilon.fabric.mixins.json` — Fabric 专有 mixins
- `neoforge/src/main/resources/epsilon.neoforge.mixins.json` — NeoForge 专有 mixins

### Access Widener / Access Transformer

- `common/src/main/resources/epsilon.accesswidener` — Fabric 访问拓宽
- `common/src/main/resources/META-INF/accesstransformer.cfg` — NeoForge 访问转换

### Mixin 编写规则

1. **不要凭空猜测 API**。优先查阅官方文档：
    - NeoForge 文档：https://docs.neoforged.net/
    - Porting Primers（中文）：https://gu-zt.github.io/Porting-Primers/
    - Mixin 文档：https://wiki.fabricmc.net/zh_cn:tutorial:mixin_introduction
    - Mixin 示例：https://wiki.fabricmc.net/zh_cn:tutorial:mixin_examples
2. Mixin 类命名：`Mixin<目标类名>`（如 `MixinLocalPlayer`）
3. 优先使用 `@Inject` + `@WrapOperation` 而非 `@Overwrite`
4. 事件发布通常在 Mixin 的 `@Inject` 回调中进行：`EventBus.INSTANCE.post(new SomeEvent(...))`
5. 如果遇到不熟悉的类/方法签名，必须查阅文档确认

## 管理器（Managers）与持有者（Holders）

Epsilon 把"持有者"（Holders，负责初始化与生命周期）和"管理器"（Managers，负责运行时逻辑）分开：

- **Holders** 位于 `common/.../holders/`，单例模式，由 `EpsilonCommon.init()` 依次调用其 `initXxx()` 方法
- **Managers** 位于 `common/.../managers/`，运行时业务逻辑，部分通过 `Managers.initManagers()` 实例化并暴露为 `Managers.XXX` 静态字段

### ModuleHolder (`common/.../holders/ModuleHolder.java`)

- `initModules()` — 注册所有内置模块（在 `EpsilonCommon.init()` 中调用，当前注册 94 个内置模块）
- `registerAddonModule(addonId, module, translateComponent)` — 注册 Addon 的模块
- `getModules()` — 获取所有已注册模块列表
- 内部订阅 `KeyPressEvent` / `MousePressEvent`，负责把键位分发到对应模块（Toggle / Hold 模式）

### AddonHolder (`common/.../holders/AddonHolder.java`)

- `registerAddon(addon)` — 注册单个 addon（含重复 ID 校验）
- `registerAddons(Iterable)` — 批量注册
- `setupAddons()` — 依次初始化所有 addon 的 i18n 并调用 `onSetup()`（含异常隔离）
- `getAddons()` — 获取已注册 addon 列表

### 其他 Holder / Manager

| 类 | 职责 |
|--------|------|
| `ConfigHolder` | 配置序列化/反序列化、自动保存（`initConfig()` / `saveNow()`） |
| `HudElementHolder` | HUD 元素初始化（`initElements()`） |
| `RotationManager`（抽象基类） | 旋转角度管理（优先级队列、平滑插值、raytrace 偏移） |
| `SilentRotationManager` / `SnapRotationManager` | RotationManager 的两种实现，由 `Managers.switchRotationManager()` 动态切换 |
| `TargetManager` | 目标选择（FOV、距离、实体类型过滤） |
| `HealthManager` | 实体生命值缓存 |
| `FriendManager` | 好友管理 |
| `SoundManager` | 音效播放 |
| `NotificationManager` | 通知系统 |
| `ServerboundPacketManager` / `ClientboundPacketManager` | 网络包管理 |

> 注意：Rotation / Target / Health / C2SPacket / S2CPacket / Friend / Sound / Notification 这些管理器实例通过 `Managers.ROTATION`、`Managers.TARGET` 等静态字段访问，而非各自类的 `INSTANCE`。

### RotationManager (`common/.../managers/impl/rotations/RotationManager.java`)

RotationManager 是**抽象基类**，管理玩家视角旋转，支持优先级队列、平滑插值和射线追踪偏移。有两种实现：

- `SilentRotationManager` — 静默旋转（不改变玩家可见视角，只在发包时写入旋转）
- `SnapRotationManager` — 瞬时旋转（直接 snap 到目标角度）

通过 `ClientSetting.rotationMode` 配置选择，由 `Managers.switchRotationManager(mode)` 动态切换。**所有访问必须通过 `Managers.ROTATION`**（而非 `RotationManager.INSTANCE`，因为是抽象类）。

**核心概念**：
- `setRotations()` 设置目标旋转角度后，RotationManager 每 tick 自动平滑旋转，并在 `SendPositionEvent` 中将旋转角度写入发包
- 旋转完成后（与真实角度差值 < 1 度）自动 `active = false`
- 多个模块可同时调用 `setRotations()`，高优先级覆盖低优先级（仅当新 priority 数值 ≥ 当前 priority 时才接受）
- 收到 `ClientboundPlayerPositionPacket` / `ClientboundPlayerRotationPacket`（S08 旋转包）时自动重置状态以避免与服务端冲突
- RotationManager 不提供 callback 或旋转完成事件；旋转后的攻击、放置等操作必须由模块自己的事件监听与状态字段驱动

**主要 API**：

```java
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.managers.Managers;

// 获取当前旋转角度（active 时返回平滑后的旋转，否则返回玩家真实视角）
float yaw = Managers.ROTATION.getYaw();     // 水平角度
float pitch = Managers.ROTATION.getPitch(); // 垂直角度
Rot2f current = Managers.ROTATION.getRotation();       // Rot2f(yaw, pitch)
Rot2f last = Managers.ROTATION.getLastRotation();      // 上一 tick 旋转

// 设置旋转（默认优先级 Medium）
Managers.ROTATION.setRotations(new Rot2f(yaw, pitch), rotationSpeed);

// 设置旋转 + 优先级
Managers.ROTATION.setRotations(new Rot2f(yaw, pitch), rotationSpeed, Priority.High);

// 设置旋转 + 射线追踪偏移（用于绕过反作弊）
Managers.ROTATION.setRotations(rotations, rotationSpeed, rayTraceFunction, Priority.High);

// 检查旋转是否激活（注意：没有 isDone() 方法，用 !isActive() 判断完成）
boolean active = Managers.ROTATION.isActive();
```

> 类型说明：旋转角度使用 `Rot2f`（位于 `utils.rotation.Rot2f`，含 `getYaw()` / `getPitch()`），不是 Mojang 的 `Vector2f`。

**优先级（`com.github.epsilon.utils.rotation.Priority`）**：

| 优先级 | 值    | 用途 |
|--------|------|------|
| `Lowest` | 0    | 预旋转（Pre Rotation） |
| `Low` | 10   | 低优先级旋转 |
| `Medium` | 50   | 默认优先级 |
| `High` | 100  | 攻击/放置等即时操作 |
| `Highest` | 1000 | 最高优先级 |

> 注意：Priority 枚举的数值与 EventBus 的 `EventPriority` 是**两套独立的系统**，不要混淆。EventPriority 的 HIGHEST=200、HIGH=100、MEDIUM=0、LOW=-100、LOWEST=-200。

**旋转后操作模式**：

`setRotations` 只负责更新旋转状态。需要等待 raycast 命中后再操作时，由模块保存 pending 状态，并在自己的 tick 等事件中持续设置旋转、检查当前角度和执行操作。

```java
@EventHandler
private void onTick(PlayerTickEvent.Pre event) {
    if (nullCheck() || !pendingPlace) return;

    Managers.ROTATION.setRotations(targetRotation, rotationSpeed.getValue(), Priority.High);
    Rot2f current = Managers.ROTATION.getRotation();
    if (RaytraceUtils.overBlock(current, side, blockPos, true)) {
        mc.gameMode.useItemOn(mc.player, hand, hitResult);
        pendingPlace = false;
    }
}
```

**关键设计要点**：
- `setRotations` 的可用重载只有 `(rotations, speed)`、`(rotations, speed, priority)`、`(rotations, speed, raytrace)` 和 `(rotations, speed, raytrace, priority)`
- raytrace 函数（`Function<Rot2f, Boolean>`）仅用于在平滑旋转时校验随机偏移；它可能被多次调用，必须无副作用，不能在其中执行攻击或放置
- 需要基于当前平滑角度判断时，使用 `Managers.ROTATION.getRotation()`，或通过 `getYaw()` / `getPitch()` 读取
- `Managers.switchRotationManager()` 切换实现时会通过 `copyStateFrom()` 把当前状态迁移到新实例
- 模块禁用时应清理 pending 状态并 `InvUtils.swapBack()` 恢复物品栏

## Lumin Graphics 渲染系统

Lumin Graphics 是 Epsilon 的轻量高性能渲染框架，位于 `common/.../graphics/`。

### 核心 Renderer

| Renderer | 用途 |
|----------|------|
| `RectRenderer` | 矩形渲染（纯色/渐变） |
| `RoundRectRenderer` | SDF 圆角矩形（Shader 实现，支持独立四角半径） |
| `RoundRectOutlineRenderer` | 圆角矩形边框 |
| `TextureRenderer` | 纹理批量渲染 |
| `ShadowRenderer` | 阴影渲染 |
| `TriangleRenderer` | 三角形渲染 |
| `TextRenderer` | 文本渲染（静态字体 + TTF） |
| `TtfTextRenderer` | TTF TrueType 字体渲染（Atlas 批处理） |

### Renderer 生命周期

1. **线程安全**：Renderer 必须在**渲染线程**初始化
2. **推荐初始化方式**：使用 `Suppliers.memoize` 延迟初始化

```java
private final Supplier<RectRenderer> rectRenderer = Suppliers.memoize(RectRenderer::create);
```

3. **使用模式一：即时绘制并清理**

```java
rectRenderer.get().addRect(10, 10, 100, 100, Color.WHITE);
rectRenderer.get().drawAndClear(); // 等价于 draw() + clear()
```

4. **使用模式二：缓冲区复用（内容不变时更高效）**

```java
// 初始化时添加顶点一次
rectRenderer.get().addRect(10, 10, 100, 100, Color.WHITE);
// 渲染循环中每帧只 draw 不清除
rectRenderer.get().draw(); // GPU 数据已存在，无需重新上传
```

5. **关闭**：关闭时释放 GPU 资源

```java
rectRenderer.get().close();
```

### 重要约束

**同一帧内，不要在 `draw()` 之后再 `clear()` 然后继续 `draw()`** — 这会导致帧内多次缓冲区分配，破坏 In-Flight 优化。如需多次清空-绘制循环，请创建新的 Renderer 实例。

### 文本渲染

静态字体加载器（实际为 `TtfFontLoader` 实例）：`StaticFontLoader.DEFAULT`、`StaticFontLoader.ICONS`、`StaticFontLoader.JURA_LIGHT`、`StaticFontLoader.OSAKA_CHIPS`。

```java
TextRenderer textRenderer = textRendererSupplier.get();
textRenderer.addText("Hello", x, y, scale, color, StaticFontLoader.OSAKA_CHIPS);
float width = textRenderer.getWidth("Hello", scale);
float height = textRenderer.getHeight(scale);
textRenderer.drawAndClear();
```

### 特效

- `BlurShader.INSTANCE.render(x, y, width, height, radius, strength)` — 背景模糊
- `FXAAShader` — FXAA 抗锯齿
- `FilterShader` — 色彩滤镜

### 参考示例

完整 HUD 渲染示例：`common/.../elements/impl/Watermark.java`

## i18n 翻译系统

- `TranslateComponent` — 翻译组件接口
- `EpsilonTranslateComponent.create("modules", "kill aura")` → key: `epsilon.modules.kill aura`
- `DefaultTranslateComponent.create("example_addon.settings.enable_particles")` → key: `example_addon.settings.enable_particles`
- 本体语言文件位于 `common/src/main/resources/assets/epsilon/i18n/{languageCode}.json`
- `I18NFileGenerator.generate("epsilon-empty-i18n.json")` 生成全部 owner 的空模板；传入第二个参数（如 `"epsilon"` 或 Addon ID）可只生成指定 owner

### i18n JSON 格式

语言文件使用与 dotted key 对应的**嵌套 JSON object**，不再把完整 key 直接写成根节点属性。叶节点的翻译值必须是字符串。

当一个 key 既有自己的翻译，又有子 key 时，使用保留属性 `_value` 保存该 key 自身的翻译。例如下列 JSON 同时定义 `epsilon`、`epsilon.modules.kill aura`、`epsilon.modules.kill aura.mode` 和枚举选项 `epsilon.modules.kill aura.mode.single`：

```json
{
  "epsilon": {
    "_value": "Epsilon",
    "modules": {
      "kill aura": {
        "_value": "Kill Aura",
        "range": "Range",
        "mode": {
          "_value": "Mode",
          "single": "Single",
          "switch": "Switch"
        }
      }
    }
  }
}
```

- `_value` 只能用于 object 内保存父 key 的字符串值，不能作为翻译 key 的普通路径段
- object 中除 `_value` 外的属性会按层级用 `.` 拼接为运行时 key
- 不允许数组、数字、布尔值或 null；翻译叶节点和 `_value` 都必须是字符串
- 新增或删除模块、HUD 元素、Setting、SettingGroup、Enum 选项或静态 `EpsilonTranslations` 时，应重新生成空模板并同步语言文件
- 使用 `python scripts/complete_i18n.py` 可按模板补全、排序并删除多余 key；用 `--owner epsilon` 或 `--owner <addonId>` 可只同步指定 owner

### Module 翻译 key 约定

- 名称通过 `toLowerCase()` 生成 key 段，空格会保留，不会自动转换为下划线
- 内置模块：`epsilon.modules.{moduleNameLowerCase}`（如 `epsilon.modules.kill aura`）
- 内置 HUD 元素：`epsilon.elements.{elementNameLowerCase}`
- Addon 模块：`{addonId}.modules.{moduleNameLowerCase}`（如 `example_addon.modules.my module`）
- `Module.getTranslatedName()` — 获取翻译后的显示名称
- 模块/HUD 内的 Setting 与 SettingGroup：直接作为所属模块 key 的子 key，如 `epsilon.modules.kill aura.range`
- EnumSetting 选项：直接作为 Setting key 的子 key，如 `epsilon.modules.kill aura.mode.single`
- Addon 自身的 Setting 与 SettingGroup（不属于模块）：`{addonId}.settings.{nameLowerCase}`

## 代码规范

1. 使用 Java 编写，遵循项目现有代码风格
2. 私有构造函数 + 单例 `INSTANCE` 模式用于 Module 和 Addon
3. 所有注释用中文
4. Logger 通过 `Constants.LOGGER` 获取
5. Minecraft 实例通过 `Constants.mc` 或 Module 中的 `this.mc` 获取
6. 编译环境使用 JDK 25

## 查阅文档资源

- **NeoForge 官方文档**：https://docs.neoforged.net/
- **NeoForge 迁移入门**：https://docs.neoforged.net/primer/docs/
- **Porting Primers（中文）**：https://gu-zt.github.io/Porting-Primers/
- **Mixin 文档**：https://wiki.fabricmc.net/zh_cn:tutorial:mixin_introduction
- **Fabric API 文档**：https://docs.fabricmc.net/develop/
- **项目自身 graphics 文档**：`common/src/main/java/com/github/epsilon/graphics/README.md`
