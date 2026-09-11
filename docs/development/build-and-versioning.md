# 构建与版本

## 版本来源

- `gradle.properties`：Epsilon 自身的 `version`、`group`、`mod_id`、`mod_name`、`mod_author`、许可证和描述。
- `gradle/libs.versions.toml`：JDK、Minecraft、NeoForm、Fabric API、Fabric Loader、NeoForge、Mixin、
  MixinExtras、ASM、Sodium 和 Iris 版本。
- 根 `build.gradle.kts`：把版本目录中的值映射为各子项目使用的额外属性（`minecraft_version`、
  `neo_form_version`、`fabric_version` 等）。
- `common/build.gradle.kts`：生成 `com.github.epsilon.BuildConfig`，暴露 `MOD_ID` 和 `VERSION`。

普通开发构建会在项目版本后追加当前 Git 短提交号；`buildRelease` 使用 `gradle.properties` 中的纯发布版本。
`base.archivesName` 固定为 `<mod_id>-<子项目>-<minecraft_version>`。

## 约定插件

- `multiloader-common.gradle.kts`：Java 工具链、仓库、资源展开、Jar 元数据与许可证打包、源码 Jar、
  发布配置和 `buildRelease`。
- `multiloader-loader.gradle.kts`：将 `:common` 的 Java、生成源码和资源加入 Fabric/NeoForge 的编译、
  Javadoc 与打包流程。

## 依赖与打包

- `common` 使用 NeoForge ModDev 的 NeoForm 产物编译，AT 来自
  `common/src/main/resources/META-INF/accesstransformer.cfg`。
- Fabric 通过 Loom 读取 `common/src/main/resources/epsilon.accesswidener`。
- Sodium 和 Iris 只以 `compileOnly` 参与编译，不会打入成品；对应 Mixin 使用 `@Pseudo` 软定位。
- 视频能力通过 Jar-in-Jar 引入 `org.bytedeco:javacpp`、`javacv`、`ffmpeg` 的 Java API 与
  `javacpp` 原生库，两个平台的打包方式不同：Fabric 使用 Loom `include`，NeoForge 使用 `jarJar`
  并修正 metadata 中的 artifact 标识。
- `ffmpeg` 的 Windows 原生库、主菜单视频和玲纱立绘不再随 jar 分发，改为首次使用时下载到
  `~/.epsilon/assets/`，详见[运行时资源下载](runtime-assets.md)。
- Windows SMTC 桥的 native 产物随资源打包：`common/src/main/resources/natives/windows-x86_64/epsilon_smtc.dll`。

## 常用命令

```shell
./gradlew build
./gradlew buildRelease
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

生成 Minecraft 源码与反编译产物：

```shell
./gradlew :common:downloadAssets
./gradlew :common:createMinecraftArtifacts
```

Windows PowerShell 使用 `.\gradlew.bat` 前缀。CI 使用 Java 25 执行 `./gradlew build`，并上传 Fabric 与
NeoForge 的 Jar。

## CI 工作流

| 工作流 | 触发 | 作用 |
|---|---|---|
| `build.yml` | push / pull request | JDK 25 + `./gradlew build`，收集并上传构建产物 |
| `nightly-build.yml` | 每日定时 / 手动 | 构建 nightly 产物并发布 |
| `backport.yml` | PR 添加 `backport to 26.1.x` / `backport to 26.2.x` 标签 | 把改动回移到对应分支 |

## 验证

仓库当前不维护测试源码或测试专用依赖。修改后使用与范围匹配的编译、`buildRelease` 和客户端运行检查；
具体验证范围遵循 [`AGENTS.md`](../../AGENTS.md) 的提交前检查。

## 外部资料

- [NeoForge 文档](https://docs.neoforged.net/)
- [NeoForge Primer](https://docs.neoforged.net/primer/docs/)
- [Fabric 文档](https://docs.fabricmc.net/develop/)
- [Mixin 介绍](https://wiki.fabricmc.net/tutorial:mixin_introduction)
- [Mixin 示例](https://wiki.fabricmc.net/tutorial:mixin_examples)
- [Porting Primers](https://gu-zt.github.io/Porting-Primers/)

外部资料用于理解加载器与 Mixin 机制。项目当前 Minecraft 版本的类和签名仍以 `common/build/moddev/`
中由当前 NeoForm 生成的源码为准；获取流程见根目录 [`AGENTS.md`](../../AGENTS.md)。
