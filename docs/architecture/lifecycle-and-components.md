# 生命周期与核心组件

## 启动流程

两个平台都通过平台 Mixin 注入 `Minecraft` 构造函数尾部，再进入各自 Loader：

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

## Holders

| Holder | 职责 |
|---|---|
| `ModuleHolder` | 注册本体/Addon 模块，处理键盘与鼠标绑定 |
| `HudElementHolder` | 注册 HUD，通过 `UiScene` 统一渲染并处理原版 overlay |
| `AddonHolder` | Addon 去重、一次性 setup 与查询 |
| `ConfigHolder` | 多配置、导入导出、Setting/custom state、好友与迁移 |
| `TranslateHolder` | 跟踪 `TranslateComponent`，切换语言时刷新缓存 |
| `RendererHolder` | 跟踪并销毁 `IRenderer` GPU 资源 |
| `RenderTargetHolder` | 跟踪并销毁 Lumin render target |
| `ShaderHolder` | 手部/箱子 outline 和屏幕后处理状态 |
| `TextureCacheHolder` | `Identifier -> LuminTexture` 缓存与释放 |

## Managers

运行时管理器通过 `Managers` 的静态字段访问：

`ROTATION`、`EXTRAPOLATION`、`TARGET`、`HEALTH`、`C2SPACKET`、`S2CPACKET`、`FRIEND`、`SOUND`、`NOTIFICATION`、`TIMER`。

这些字段由 `Managers.initManagers()` 初始化。其中 Rotation Manager 可在运行时因模式切换而替换，调用方应通过 `Managers.ROTATION` 获取当前实例。
