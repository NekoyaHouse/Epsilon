# 构建与版本

## 版本来源

- `gradle.properties`：Epsilon 自身的 `version`、`group`、`mod_id`、`mod_name`、`mod_author`、许可证和描述。
- `gradle/libs.versions.toml`：JDK、Minecraft、NeoForm、Fabric API、Fabric Loader、NeoForge、Mixin、Sodium、Iris 和测试库版本。
- 根 `build.gradle.kts`：将版本目录中的值映射为各子项目使用的 Gradle 属性。
- `common/build.gradle.kts`：生成 `com.github.epsilon.BuildConfig`，当前暴露 `MOD_ID` 和有效构建版本。

普通开发构建会在项目版本后追加当前 Git 短提交号；`buildRelease` 使用 `gradle.properties` 中的纯发布版本。

## 约定插件

- `multiloader-common.gradle.kts`：Java 25 工具链、仓库、资源展开、Jar 元数据、源码 Jar、发布和 `buildRelease`。
- `multiloader-loader.gradle.kts`：将 `:common` 的 Java、资源和生成源码加入 Fabric/NeoForge 编译与打包流程。

Sodium 与 Iris 兼容代码只在对应平台编译；Iris 的 Fabric/NeoForge Jar 从 Modrinth Maven 以 `compileOnly` 引入，不会打入 Epsilon 成品。

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

## Windows SMTC 原生桥接

Island 的 Music 实例通过 `native/smtc/` 中的 C++/WinRT JNI 桥接读取 Windows System Media Transport Controls。桥接仅支持 Windows x64，其他平台不会加载原生库，也不会创建 Music 实例。

本机需要 Visual Studio C++ 工具链、Windows SDK、CMake 和 Java 25 JDK。DLL 会直接输出到 `common/src/main/resources/natives/windows-x86_64/epsilon_smtc.dll`，并随 Fabric/NeoForge Jar 打包：

```powershell
$env:JAVA_HOME = "C:/Program Files/Java/jdk-25.0.4"
cmake -S native/smtc -B native/smtc/build -A x64 -DJAVA_HOME="$env:JAVA_HOME"
cmake --build native/smtc/build --config Release --parallel
```

独立探针用于在不启动 Minecraft 的情况下核验本机 SMTC 和封面读取：

```powershell
.\native\smtc\build\Release\epsilon_smtc_probe.exe
```

原生源码或 JNI 数据结构变化后必须重新构建 DLL，并同时检查 probe、`:common:test` 和两个加载器构建。Java 侧按 DLL 内容哈希解压到系统临时目录，避免覆盖已被 JVM 加载的文件。

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
