# Epsilon 开发文档

本目录保存 Epsilon 本体仓库的架构和具体项目说明。强制开发约束仍集中在仓库根目录的 [`AGENTS.md`](../AGENTS.md)，避免约束与背景资料混杂。

## 目录

```text
docs/
├── README.md
├── architecture/
│   ├── overview.md
│   └── lifecycle-and-components.md
└── development/
    ├── build-and-versioning.md
    ├── modules-and-addons.md
    ├── events-and-mixins.md
    ├── configuration-and-rotation.md
    ├── rendering.md
    └── internationalization.md
```

## 阅读路径

| 主题 | 文档 |
|---|---|
| 项目定位、仓库结构、分层与 `common` 包 | [架构总览](architecture/overview.md) |
| 启动顺序、Holders、Managers | [生命周期与核心组件](architecture/lifecycle-and-components.md) |
| 版本来源、Gradle 约定、构建命令 | [构建与版本](development/build-and-versioning.md) |
| Module、Setting DSL、Addon | [模块与 Addon](development/modules-and-addons.md) |
| EventBus、事件目录、Mixin | [事件与 Mixin](development/events-and-mixins.md) |
| 配置目录、持久化、RotationManager | [配置与旋转](development/configuration-and-rotation.md) |
| Lumin、GUI/HUD、2D/3D 渲染 | [渲染](development/rendering.md) |
| key、JSON 格式和同步流程 | [国际化](development/internationalization.md) |

## 维护原则

- 文档与源码冲突时，以当前源码、`gradle/libs.versions.toml` 和本地 Minecraft 参考源码为准。
- 修改架构、公共 API、配置/资源格式、注册或构建流程时，在同一次修改中更新对应主题文档。
- 本目录描述“项目是什么、如何工作、API 如何使用”；强制限制只在 `AGENTS.md` 维护。
