# 构建与版本

## 版本来源

- `gradle.properties`：Epsilon 自身的 `version`、`group`、`mod_id`、`mod_name`、`mod_author`、许可证和描述。
- `gradle/libs.versions.toml`：JDK、Minecraft、NeoForm、Fabric API、Fabric Loader、NeoForge、Mixin、Sodium 和测试库版本。
- 根 `build.gradle.kts`：将版本目录中的值映射为各子项目使用的 Gradle 属性。
- `common/build.gradle.kts`：生成 `com.github.epsilon.BuildConfig`，当前暴露 `MOD_ID` 和有效构建版本。

普通开发构建会在项目版本后追加当前 Git 短提交号；`buildRelease` 使用 `gradle.properties` 中的纯发布版本。

## 约定插件

- `multiloader-common.gradle.kts`：Java 25 工具链、仓库、资源展开、Jar 元数据、源码 Jar、发布和 `buildRelease`。
- `multiloader-loader.gradle.kts`：将 `:common` 的 Java、资源和生成源码加入 Fabric/NeoForge 编译与打包流程。

## 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat buildRelease --stacktrace
.\gradlew.bat :common:test
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

CI 使用 Java 25 执行：

```powershell
.\gradlew.bat buildRelease --no-daemon --stacktrace
```

构建产物包括 Fabric 与 NeoForge Jar，并由 CI 上传。

## 测试

仓库当前没有 Java 测试文件。新增可脱离游戏运行的配置、解析、排序或数学逻辑时，优先在 `common/src/test/java` 添加 JUnit 5 测试。构建验证范围必须遵循 [`AGENTS.md`](../../AGENTS.md) 的提交前检查。

## 外部资料

- [NeoForge 文档](https://docs.neoforged.net/)
- [NeoForge Primer](https://docs.neoforged.net/primer/docs/)
- [Fabric 文档](https://docs.fabricmc.net/develop/)
- [Mixin 介绍](https://wiki.fabricmc.net/tutorial:mixin_introduction)
- [Mixin 示例](https://wiki.fabricmc.net/tutorial:mixin_examples)
- [Porting Primers](https://gu-zt.github.io/Porting-Primers/)

外部资料用于理解加载器与 Mixin 机制。项目当前 Minecraft 版本的类和签名仍以 `common/build/moddev/` 中由当前 NeoForm 生成的源码为准；获取流程见根目录 [`AGENTS.md`](../../AGENTS.md)。
