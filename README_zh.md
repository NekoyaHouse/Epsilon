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
  <a href="https://qun.qq.com/universal-share/share?ac=1&authKey=iRA8XvRnt0XxfYdZZVuLbBfRx%2BulcLVEHbj4yfUJ14RK6apLxsMQO4juClGoznIo&busi_data=eyJncm91cENvZGUiOiIxMTI4MTcxNTcxIiwidG9rZW4iOiJLSmtnVXBhNWJsWUpoeTc1VjJHRE54ZFg2cnY4MlJlaTdzVTlOVElKNjdlVEU1VDlWVGlVTmpzdjNvN3VOd3JoIiwidWluIjoiMzE5NjU5NDMwMCJ9&data=lNsvbgpmSqL4brANGeMmvZPzZVouEkrmglv7k_2wytUMN0Qvg8wyQ4Lw_6gDEQhW93z9WRbrydA3rkAO1SisCg&svctype=4&tempid=h5_group_info"><img alt="QQ 一群" src="https://img.shields.io/badge/QQ%20%E4%B8%80%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
  <a href="https://qm.qq.com/q/3hhg8ww9ag"><img alt="QQ 二群" src="https://img.shields.io/badge/QQ%20%E4%BA%8C%E7%BE%A4-join-12B7F5?style=flat-square&logo=tencentqq&logoColor=white"></a>
</p>

## 📌 项目简介
基于 NeoForge & Fabric 构建的多加载器现代化 Minecraft 辅助客户端，拥有先进的渲染系统和模块化架构。

## 🎨 渲染系统

Lumin 渲染系统提供自定义渲染管线，支持：
- 矩形与圆角矩形
- 阴影与模糊效果
- TTF 字体渲染
- 纹理渲染
- 自定义顶点格式

持久 UI 缓存局部 GPU 几何，独立更新变换、裁剪与颜色。共享 2D canvas 按显式层级和声明顺序绘制，flush 消费记录，并继续支持立即模式。

Dropdown 模块行通过面板内显式绘制分层合批兼容形状与文字，保持面板遮挡关系与按钮文字可读性。

详见 [渲染系统文档](common/src/main/kotlin/com/github/epsilon/graphics/README_zh.md)

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
- [LeavesHack](https://github.com/MrBZBZ/LeavesHack)
- [TrollHack](https://github.com/Luna5ama/TrollHack)
- [ThunderHack-Recode](https://github.com/Pan4ur/ThunderHack-Recode)

## 📝 许可证

本项目，包括 Lumin Graphics，统一遵循 [GNU General Public License v3.0](LICENSE) 许可证。

---

版权所有 © 2026 NekoyaHouse.
