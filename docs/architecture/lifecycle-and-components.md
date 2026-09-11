# 生命周期与核心组件

## 启动流程

两个平台都通过平台 Mixin 注入 `Minecraft` 构造函数尾部，再进入各自 Loader：

```text
Minecraft.<init> TAIL
  -> fabric/neoforge Loader
  -> EpsilonFabric / EpsilonNeoForge
  -> 收集并注册 Addon
  -> EpsilonCommon.init()
```

- Fabric：`MixinMinecraftFabric` 调用 `EpsilonFabric.init()`，读取 entrypoint key `epsilon:addon`，
  并注册 `LanguageReloadListener`。
- NeoForge：`MixinMinecraft` 调用 `EpsilonNeoForge.init()`，先发布 `EpsilonAddonSetupEvent`，再在
  `NeoForgeEventHandler` 中注册 `LanguageReloadListener`。
- 两端在检测到 Iris 时把 TTF 字体 pipeline 注册为 `IrisProgram.TEXTURED`。

`EpsilonCommon.init()` 当前顺序：

1. 设置 `Constants.mc`，注册 `com.github.epsilon` 包的 EventBus lambda factory。
2. `ModuleManager.INSTANCE.initModules()`。
3. `HudElementManager.INSTANCE.initElements()`。
4. `AddonManager.INSTANCE.setupAddons()`。
5. 预热运行时 Manager：`ExecutorManager`、`ClientboundPacketManager`、`ServerboundPacketManager`、
   `TargetManager`、`ExtrapolationManager`、`HealthManager`、`SkinManager`。
6. `ConfigManager.INSTANCE.initConfig()`，随后选择当前语言。
7. 初始化 `Render3DScheduler` 的 RenderPipeline。
8. 生成空 i18n 模板，并注册退出时保存配置的 shutdown hook。

## Manager 组织

26.2.x 不再使用 Holders 包和 `Managers` 静态字段容器。所有 Manager 都是带
`public static final Xxx INSTANCE` 与私有构造函数的单例，通过 `INSTANCE` 直接访问：

| Manager | 职责 |
|---|---|
| `ModuleManager` | 注册本体/Addon 模块，处理键盘与鼠标绑定 |
| `HudElementManager` | 注册 HUD，持有共享 `UiScene`，统一提交 HUD 帧与原版 overlay |
| `AddonManager` | Addon 去重、一次性 setup 与查询 |
| `ConfigManager` | 多配置、导入导出、Setting/custom state、好友与账号数据 |
| `TranslationManager` | 跟踪 `TranslateComponent`，语言变化时刷新缓存 |
| `RendererManager` / `RenderTargetManager` | 跟踪 Epsilon 创建的 renderer 与 render target |
| `ShaderManager` | 手部/箱子 outline 等共享 shader 状态 |
| `AccountManager` / `SkinManager` / `QQAvatarManager` | 账号、皮肤与头像资源 |
| `ExecutorManager` / `TimerManager` / `VideoManager` | 线程池、计时与视频播放 |
| `AssetManager` | 视频/玲纱/FFmpeg 资源的按需下载、缓存与纹理注册 |

`RotationManager` 是抽象基类，实现为 `SilentRotationManager` 与 `SnapRotationManager`。它的
`INSTANCE` 是可变静态字段，`RotationManager.switchRotationManager(mode)` 会通过 `copyStateFrom()`
替换实例，调用方必须每次重新读取。

其余运行时管理器包括 `TargetManager`、`HealthManager`、`ExtrapolationManager`、`FriendManager`、
`NotificationManager`、`SoundManager`、`ClientboundPacketManager` 与 `ServerboundPacketManager`。

## 渲染资源生命周期

- `RendererManager.INSTANCE.register(...)` 登记业务 renderer，`destroyAll()` 在关闭时统一释放。
- `RenderTargetManager.INSTANCE` 管理 `LuminRenderSystem.LuminRenderTarget`，帧内借入的 Minecraft
  texture、view 与 native handle 不由 Epsilon 关闭。
- `Render2DScheduler` 由 `UiScene` 持有；Screen 或 HUD 帧结束后 `endFrame()` 会 flush 并清空命令流。
- `Render3DScheduler.INSTANCE` 订阅 `Render3DEvent` 并在 priority `-999` 统一 flush。
