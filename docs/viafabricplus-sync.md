# ViaFabricPlus 同步说明

本文档记录当前嵌入 Epsilon 的 ViaFabricPlus 源码，以及后续同步上游变更的流程。

## 当前基线

- 上游仓库：<https://github.com/ViaVersion/ViaFabricPlus>
- 上游分支：`ver/26.2`
- 已导入提交：`c54b78b3b8daaa171e55f78774f0e12f7a2d70b5`
- 已导入提交标题：`ViaFabricPlus 4.6.3`
- 上一个上游基线：`a53db9f064021ee2e830173117dc095966a5cd49`
- Epsilon 导入提交：待定（本次同步尚未提交）
- Epsilon 导入日期：`2026-08-26`

导入后的源码已经针对本仓库进行了适配。上游哈希仅标识源码基线，并不表示
Epsilon 的源码树与其逐字节一致。加载器边界、NeoForge 集成、Epsilon 专用 Mixin
以及配置归属均为本地改动。

## 仓库布局

- 共享实现：`common/src/main/java/me/sofurry/via`
- 共享资源和 Mixin 配置：`common/src/main/resources`
- 仅 Fabric 使用的桥接和 Mixin：`fabric/src/main/java` 与 `fabric/src/main/resources`
- 仅 NeoForge 使用的桥接、Mixin 和访问转换器：`neoforge/src/main/java` 与 `neoforge/src/main/resources`

Java 符号已重命名为 ViaVersion。Java 包命名空间为 `me.sofurry.via`；嵌入的源码
不使用上游包 `com.viaversion.viafabricplus`。资产命名空间使用 `viaversion` 和
`viaversion-visuals`；翻译键和上游 URL 均刻意保留。本地 Mixin 配置文件使用
`viaversion*.mixins.json` 名称。

ViaFabricPlus 的保存/设置子系统以及协议库 YAML 文件已移除。用户设置由 Epsilon 的
`ConfigManager` 负责，设置本身位于 `ClientSetting` 模块中。选定的协议版本全局存储于
`~/.epsilon/via/settings.json`。协议库通过 `ConfigManager` 获取只读的运行时配置。
ViaBedrock 缓存和可选覆盖 JAR 等运行时数据也位于 `~/.epsilon/via`，绝不位于游戏目录的
`config` 文件夹中。

## 同步上游更新

### 1. 保留本地状态

从干净的同步点开始，并在修改嵌入源码前记录本地工作。不要重置或丢弃无关的 Epsilon 改动。

```powershell
git status --short
git diff -- common fabric neoforge > epsilon-before-vfp-sync.patch
git remote add viafabricplus https://github.com/ViaVersion/ViaFabricPlus.git
git fetch viafabricplus ver/26.2
```

若远程仓库已存在，请跳过 `git remote add`，仅执行 `git fetch`。

### 2. 选择并记录上游提交

检查记录的基线之后的提交，选择与当前维护的 Minecraft 版本匹配的提交。在应用任何改动前，
记录完整的 40 字符哈希。

```powershell
git log --date=short --format='%H %ad %s' `
  a53db9f064021ee2e830173117dc095966a5cd49..viafabricplus/ver/26.2
git show --stat --oneline <new-upstream-hash>
```

不要自动使用不断移动的上游 `HEAD`。先确认更新是否修改了 Minecraft 映射、ViaVersion API、
ViaBedrock/ViaLegacy API 或上游模块布局。

### 3. 按职责比对源码

将上游 common 代码与对应的 Epsilon common 代码进行比对。上游 API 和 visuals 模块已合并到
Epsilon 的 `common` 源码树中，因此应按对应包映射，而不是整块复制上游项目根目录。

```powershell
git diff --find-renames `
  a53db9f064021ee2e830173117dc095966a5cd49..<new-upstream-hash> -- `
  src/main/java viafabricplus-api/src/main/java viafabricplus-visuals/src/main/java
```

只应用相关的实现和资源变更。Epsilon 构建文件、加载器入口点、Epsilon Mixin 及本地 NeoForge
修复应单独审查。

### 4. 重新应用本地命名和布局规则

对于新复制或重命名的上游文件，应用此移植使用的同一套源码符号映射：

```text
ViaFabricPlus  -> ViaVersion
viaFabricPlus  -> viaVersion
VFP            -> ViaVersion
vfp            -> viaVersion
```

Java 包映射为：

```text
com.viaversion.viafabricplus -> me.sofurry.via
```

除非计划进行刻意迁移，否则保持以下运行时标识符不变：

```text
viafabricplus
viaversion*.mixins.json
viafabricplus*.accesswidener
```

资产位于 `assets/viaversion` 和 `assets/viaversion-visuals`。同步资产时，必须一并更新
文件名及所有 Java/Mixin/JSON 中的资源定位符引用。保留许可证头和来源注释中的上游 GitHub URL。

### 5. 重新应用加载器特定适配

- 保持 `common` 中的通用平台接口不依赖 Fabric 或 NeoForge 类。
- 将加载器安装逻辑保留在 `ViaVersionFabricPlatform` 和
  `ViaVersionNeoForgePlatform` 中。
- 不要将反射或硬编码的加载器类路径恢复到 common 层。
- 有意识地将 NeoForge 访问改动合并到 `META-INF/accesstransformer.cfg`；不要用上游
  Fabric access widener 替换该文件。
- 逐项合并 common access widener 与 NeoForge access transformer。针对每个变更的 Minecraft
  成员，检查相应的 `reference/fabric-26.2` 和 `reference/neoforge-26.2` 源码。
- 除非上游改动使其不再需要，否则保留 Epsilon 专用 Mixin 和自定义同步载荷钩子。
- 不要恢复 ViaFabricPlus 的 `save`、`settings` 或 `api.settings` 包。新的持久化用户状态必须
  集成到 Epsilon 的 `ConfigManager`。
- 不要恢复 `getConfigDir`、`FMLPaths.CONFIGDIR` 或协议库 YAML 配置。保留 `ConfigManager`
  运行时配置适配器和 `~/.epsilon/via` 运行时数据根目录。

### 6. 更新此记录

源码合并后，在本文档中将基线哈希和提交标题替换为选定提交的完整信息。同时记录本次更新所需的
任何本地兼容性补丁。请在同步提交消息或审查记录中保留旧哈希，以便下次更新有明确的提交范围。

### 7. 构建并手动测试

在游戏内测试前，运行两个加载器的编译和打包：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3'
.\gradlew.bat :fabric:compileJava :neoforge:compileJava --no-daemon
.\gradlew.bat :fabric:build :neoforge:jar --no-daemon
git diff --check
```

随后手动启动加载器，测试连接有代表性的协议版本、打开 ViaVersion 设置界面、更改每服务器版本，
并加载 NeoForge 客户端。确认不会创建 Via YAML 文件或 `config/viafabricplus` 目录。应将
Mixin 应用错误、缺失资源和访问错误视为同步阻塞问题，而不是用反射绕过。
