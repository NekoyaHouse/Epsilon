# 架构总览

## 项目定位

Epsilon 是面向 Minecraft 客户端的多加载器工具模组，同时支持 Fabric 和 NeoForge。共享业务代码位于 `common/`，两个加载器子项目复用同一份 Java 源码与资源。

## 仓库结构

```text
Epsilon/
├── common/       # 共享核心：模块、事件、GUI、渲染、配置、工具类和公共资源
├── fabric/       # Fabric 启动、Addon 入口、资源重载和平台 Mixin
├── neoforge/     # NeoForge 启动、事件桥接和平台 Mixin
├── buildSrc/     # Gradle 约定插件
├── gradle/       # 版本目录与 Gradle Wrapper 配置
├── scripts/      # 维护脚本，目前主要用于 i18n 同步
├── docs/         # 架构与项目开发文档
└── .github/      # CI 工作流
```

## 多加载器分层

`common/` 持有加载器无关的业务实现并可直接调用 Minecraft API。Fabric 与 NeoForge 项目只负责平台启动、平台事件桥接、Addon 收集和确有差异的 Mixin。

`multiloader-loader` 会将下列内容加入两个加载器子项目：

- `common/src/main/java`
- `common` 生成的 `BuildConfig`
- `common/src/main/resources`

访问扩展分别由 Fabric Access Widener 和 NeoForge Access Transformer 提供。

Minecraft 反编译源码是构建产物，不纳入仓库源码目录。获取方式和产物位置见根目录 [`AGENTS.md`](../../AGENTS.md) 的“Minecraft 源码获取与检索”。

## `common` 核心包

全部共享 Java 代码位于 `common/src/main/java/com/github/epsilon/`。

| 包 | 职责 |
|---|---|
| `addon/` | `EpsilonAddon`、公共 Addon 收集事件与 `AddonBootstrap` |
| `assets/` | 配置迁移、i18n、资源位置工具 |
| `elements/` | `HudModule`、已注册 HUD 元素和通知模型 |
| `events/` | 自定义 EventBus、监听器实现和事件类型 |
| `graphics/` | Lumin GPU 抽象、buffer、renderer、shader、2D/3D scheduler、字体与即时绘制 |
| `gui/` | Dropdown、Panel、HUD 编辑器、主菜单、UI Tree/Scene、主题和控件 |
| `holders/` | 模块、HUD、Addon、配置、翻译、Renderer、RenderTarget、Shader、纹理缓存的所有权与生命周期 |
| `interfaces/` | Mixin accessor/duck 接口 |
| `managers/` | Rotation、Target、Extrapolation、Health、Packet、Friend、Sound、Notification、Timer |
| `mixins/` | 共享客户端 Mixin；启用列表以 `epsilon.mixins.json` 为准 |
| `modules/` | `Module`、`Category`、`ClientSetting` 和 combat/player/movement/render 模块 |
| `settings/` | `SettingHost` DSL、分组、布局规划和各类 Setting 实现 |
| `utils/` | client、combat、math、network、player、render、rotation、timer、world 工具 |

本体模块和 HUD 的实际数量以 `ModuleHolder` 与 `HudElementHolder` 注册表为准。新增组件时应同步注册和 i18n，不能依赖文档中的静态数量。
