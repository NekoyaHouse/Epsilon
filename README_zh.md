<h1 align="center">Epsilon</h1>
<h4 align="center">
    <p>
        <a href="./README.md">English</a> |
        <b>中文</b>
    </p>
</h4>

<p align="center">
  <a href="https://github.com/NekoyaHouse/Epsilon/actions"><img alt="构建" src="https://img.shields.io/badge/build-gradle-4c1?style=flat-square"></a>
  <a href="LICENSE"><img alt="许可证" src="https://img.shields.io/badge/license-GPLv3-blue?style=flat-square"></a>
  <img alt="加载器" src="https://img.shields.io/badge/loaders-NeoForge%20%26%20Fabric-6a5acd?style=flat-square">
  <a href="https://discord.gg/vYbaae3X7e"><img alt="Discord" src="https://img.shields.io/badge/Discord-加入社区-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/WPvwQZvYci"><img alt="QQ 一群" src="https://img.shields.io/badge/QQ%20%E4%B8%80%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
  <a href="https://qm.qq.com/q/3hhg8ww9ag"><img alt="QQ 二群" src="https://img.shields.io/badge/QQ%20%E4%BA%8C%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
</p>

## 📌 项目简介
基于 NeoForge & Fabric 构建的多加载器现代化 Minecraft 辅助客户端，拥有先进的渲染系统和模块化架构。

## 🚀 插件系统
[Epsilon 插件模板](https://github.com/slmpc/Epsilon-Addon-Template)

[Addon 开发文档](docs/addon-development.md)

## 🎨 渲染系统

Lumin 渲染系统提供自定义渲染管线，支持：
- 矩形与圆角矩形
- 阴影与模糊效果
- TTF 字体渲染
- 纹理渲染
- 自定义顶点格式

详见 [渲染系统文档](common/src/main/java/com/github/epsilon/graphics/README_zh.md)

基于 Lumin 的声明式 UI 层见 [Epsilon GUI Library 文档](docs/gui-library.md)。

## ⚙️ 构建与运行

```bash
# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient
```

## 🙏 鸣谢

感谢以下项目。第三方代码归属信息详见 [NOTICE](NOTICE.md)。
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)
- [Orbit](https://github.com/MeteorDevelopment/orbit)
- [LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- [TrollHack](https://github.com/Luna5ama/TrollHack)

## 📝 许可证

本项目，包括 Lumin Graphics，统一遵循 [GNU General Public License v3.0](LICENSE) 许可证。

---

版权所有 © 2026 NekoyaHouse.
