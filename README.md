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
  <a href="https://qun.qq.com/universal-share/share?ac=1&authKey=iRA8XvRnt0XxfYdZZVuLbBfRx%2BulcLVEHbj4yfUJ14RK6apLxsMQO4juClGoznIo&busi_data=eyJncm91cENvZGUiOiIxMTI4MTcxNTcxIiwidG9rZW4iOiJLSmtnVXBhNWJsWUpoeTc1VjJHRE54ZFg2cnY4MlJlaTdzVTlOVElKNjdlVEU1VDlWVGlVTmpzdjNvN3VOd3JoIiwidWluIjoiMzE5NjU5NDMwMCJ9&data=lNsvbgpmSqL4brANGeMmvZPzZVouEkrmglv7k_2wytUMN0Qvg8wyQ4Lw_6gDEQhW93z9WRbrydA3rkAO1SisCg&svctype=4&tempid=h5_group_info"><img alt="QQ Group 1" src="https://img.shields.io/badge/QQ%201%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
  <a href="https://qm.qq.com/q/3hhg8ww9ag"><img alt="QQ Group 2" src="https://img.shields.io/badge/QQ%202%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
</p>

## 📌 Overview
A modern multi loader Minecraft utility client built on NeoForge & Fabric with advanced rendering system and modular architecture.

## 🎨 Graphics System

The Lumin rendering system provides custom render pipelines for:
- Rectangles & Round Rectangles
- Shadows & Blur effects
- TTF Font rendering
- Texture rendering
- Custom vertex formats

Retained UI caches local GPU geometry and updates transform, clip and paint independently. The shared 2D canvas preserves layer/declaration order and consumes records on flush; immediate mode remains available.

Dropdown module rows use explicit drawing stages within each panel to batch compatible shapes and labels while preserving panel overlap and button text visibility.

See [Lumin Graphics README](common/src/main/kotlin/com/github/epsilon/graphics/README.md) for details.

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
- [LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- [TrollHack](https://github.com/Luna5ama/TrollHack)
- [ThunderHack-Recode](https://github.com/Pan4ur/ThunderHack-Recode)

## 📝 License

This project, including Lumin Graphics, is licensed under the [GNU General Public License v3.0](LICENSE).

---

Copyright © 2026 NekoyaHouse.
