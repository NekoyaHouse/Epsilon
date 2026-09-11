<h1 align="center">Epsilon</h1>
<h4 align="center">
    <p>
        <b>English</b> |
        <a href="./README_zh.md">中文</a>
    </p>
</h4>

<p align="center">
  <a href="https://github.com/NekoyaHouse/Epsilon/actions"><img alt="Build" src="https://img.shields.io/badge/build-gradle-4c1?style=flat-square"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-blue?style=flat-square"></a>
  <img alt="Loaders" src="https://img.shields.io/badge/loaders-NeoForge%20%26%20Fabric-6a5acd?style=flat-square">
  <a href="https://discord.gg/vYbaae3X7e"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join%20Server-5865F2?style=flat-square&logo=discord&logoColor=white"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/WPvwQZvYci"><img alt="QQ Group 1" src="https://img.shields.io/badge/QQ%201%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
  <a href="https://qm.qq.com/q/3hhg8ww9ag"><img alt="QQ Group 2" src="https://img.shields.io/badge/QQ%202%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
</p>

## 📌 Overview
A modern multi loader Minecraft utility client built on NeoForge & Fabric with advanced rendering system and modular architecture.

## 🚀 Addon System
[Epsilon Addon Template](https://github.com/slmpc/Epsilon-Addon-Template)

[Addon Development Guide](docs/addon-development.md)

## 🎨 Graphics System

The Lumin rendering system provides custom render pipelines for:
- Rectangles & Round Rectangles
- Shadows & Blur effects
- TTF Font rendering
- Texture rendering
- Custom vertex formats

See [Lumin Graphics README](common/src/main/java/com/github/epsilon/graphics/README.md) for details.

The declarative UI layer built on Lumin is documented in the [Epsilon GUI Library Guide](docs/gui-library.md).

## ⚙️ Build & Run

```bash
# Build the mod
./gradlew build

# Run client
./gradlew runClient
```

## 🙏 Credits

Special thanks to the following projects. See [NOTICE](NOTICE.md) for third-party code attributions.
- [Meteor Client](https://github.com/MeteorDevelopment/meteor-client)
- [Orbit](https://github.com/MeteorDevelopment/orbit)
- [LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- [TrollHack](https://github.com/Luna5ama/TrollHack)

## 📝 License

This project, including Lumin Graphics, is licensed under the [GNU General Public License v3.0](LICENSE).

---

Copyright © 2026 NekoyaHouse.
