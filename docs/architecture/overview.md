# 架构总览

## 项目定位

Epsilon 是面向 Minecraft 客户端的多加载器工具模组，同时支持 Fabric 和 NeoForge。共享业务代码位于
`common/`，两个加载器子项目复用同一份 Java 源码与资源。

## 仓库结构

```text
Epsilon/
├── common/       # 共享核心：模块、事件、GUI、渲染、配置、工具类和公共资源
├── fabric/       # Fabric 启动、Addon 入口、资源重载和平台 Mixin
├── neoforge/     # NeoForge 启动、事件桥接、Jar-in-Jar 依赖和平台 Mixin
├── native/       # 本地组件源码，目前是 Windows SMTC 读取桥
├── buildSrc/     # Gradle 约定插件
├── gradle/       # 版本目录与 Gradle Wrapper 配置
├── scripts/      # 维护脚本，目前是 i18n 同步
└── .github/      # CI 工作流
```

Lumin Graphics 目前是 `common` 内的源码包，而不是独立子项目；`reference/` 是本地解压的 Minecraft
参考源码目录，被 `.gitignore` 忽略，不属于仓库内容。

## 多加载器分层

`common/` 持有加载器无关的业务实现并可直接调用 Minecraft API。Fabric 与 NeoForge 项目只负责平台启动、
平台事件桥接、Addon 收集和确有差异的 Mixin。

`multiloader-loader` 会将下列内容加入两个加载器子项目：

- `common/src/main/java`
- `common` 生成的 `BuildConfig`
- `common/src/main/resources`

访问扩展分别由 Fabric Access Widener（`epsilon.accesswidener`）和 NeoForge Access Transformer
（`META-INF/accesstransformer.cfg`）提供。

Minecraft 反编译源码是构建产物，不纳入仓库源码目录。获取方式和产物位置见根目录 [`AGENTS.md`](../../AGENTS.md)
的“Minecraft 源码获取与检索”。

## 2D UI 边界

Epsilon 自有的声明式 UI 库位于 `com.github.epsilon.gui.lib`，负责 `UiTree`、`UiScene`、语义 layer、
批次提交、滚动视口和缓存失效；渲染落到 `com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler`。
业务 Screen（Panel、Dropdown、HUD Editor、MainMenu）只负责状态与输入，不各自维护 renderer。

`MixinGuiRenderer` 在 `GuiRenderer.render` 头部构造两份 `GuiRenderState`，分别发布 `Render2DEvent.Level`
与 `Render2DEvent.HUD`；原版与 Epsilon 的提取结果由 `EpsilonGuiRenderer` 统一提交。HUD 元素由
`HudElementManager` 订阅 `Render2DEvent.HUD` 统一驱动。

## `common` 核心包

全部共享 Java 代码位于 `common/src/main/java/`。

| 包 | 职责 |
|---|---|
| `com.github.epsilon.addon` | `EpsilonAddon`、`EpsilonAddonSetupEvent` 与 `AddonBootstrap` |
| `com.github.epsilon.accounts` | 账号模型、缓存、Microsoft 登录与皮肤纹理请求 |
| `com.github.epsilon.assets` | 配置迁移、i18n 与资源位置工具 |
| `com.github.epsilon.elements` | `HudModule`、已注册 HUD 元素、岛屿/通知等展示组件 |
| `com.github.epsilon.events` | 自定义 EventBus、监听器实现和事件类型 |
| `com.github.epsilon.graphics` | Lumin 渲染框架：pipelines、renderers、schedulers、shaders、buffers、text |
| `com.github.epsilon.gui` | `gui/lib` 声明式 UI 库，以及 Panel、Dropdown、HUD Editor、MainMenu、账号界面 |
| `com.github.epsilon.interfaces` | Mixin accessor/duck 接口 |
| `com.github.epsilon.managers` | Module、HUD、Addon、配置、翻译、账号、渲染资源、Rotation、Target 等运行时管理 |
| `com.github.epsilon.mixins` | 共享客户端 Mixin；启用列表以 `epsilon.mixins.json` 为准 |
| `com.github.epsilon.modules` | `Module`、`Category`、`ClientSetting` 和 combat/player/movement/render 模块 |
| `com.github.epsilon.settings` | `SettingHost` DSL、分组、布局规划和各类 Setting 实现 |
| `com.github.epsilon.utils` | client、combat、entity、math、network、player、render、rotation、timer、world 工具 |
| `me.sofurry.smtc` | Windows SMTC（系统媒体控制）JNI 桥；native 源码位于 `native/smtc` |

本体模块和 HUD 的实际数量以 `ModuleManager` 与 `HudElementManager` 注册表为准。新增组件时应同步注册和
i18n，不能依赖文档中的静态数量。
