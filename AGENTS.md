# Epsilon 本体开发约束

本文件只保存对仓库内开发工作的强制约束。项目说明见 [`README.md`](README.md) 与 [`README_zh.md`](README_zh.md)，Lumin 渲染系统说明见 [`common/src/main/kotlin/com/github/epsilon/graphics/README.md`](common/src/main/kotlin/com/github/epsilon/graphics/README.md)。遇到文档与源码不一致时，以当前源码、`gradle/libs.versions.toml` 和本地 Minecraft 参考源码为准，并在同一次修改中更新对应说明。

## 开发前的强制检查

1. 先查看 `git status --short`，不得覆盖或回滚不属于本次任务的改动。
2. 先查看 `gradle/libs.versions.toml`，确认 Minecraft、加载器、映射和依赖版本。
3. 涉及 Minecraft 类、方法、字段、渲染流程、网络包或 Mixin 目标时，必须直接查阅当前版本源码。源码由 NeoForge ModDev 生成并保存在 `common/build/moddev/`。
4. 不得根据训练数据或记忆猜测 Minecraft API。类名存在不代表方法签名、调用时机或线程约束仍然相同。
5. 涉及 Fabric 或 NeoForge API 时，再查对应加载器实现和官方文档；不得把加载器专有调用放进 `common/`。
6. 修改架构、公共 API、配置格式、资源格式、注册流程或构建流程时，必须同步更新受影响的注释。

### Minecraft 源码获取与检索

源码 Jar 位于：

```text
common/build/moddev/artifacts/vanilla-<游戏版本>-sources.jar
```

如果源码 Jar 不存在，依次执行：

```shell
./gradlew :common:downloadAssets
./gradlew :common:createMinecraftArtifacts
```

Windows PowerShell 使用对应的 Wrapper：

```powershell
.\gradlew.bat :common:downloadAssets
.\gradlew.bat :common:createMinecraftArtifacts
```

查阅前将源码解压到按游戏版本区分的参考目录：

```shell
mkdir -p reference && unzip common/build/moddev/artifacts/vanilla-*-sources.jar -d reference/vanilla-26.3/
```

解压后可使用 `rg` 检索，例如：

```shell
rg -n "class Minecraft|record KeyEvent" reference/vanilla-26.3 -g "*.java"
rg -n "methodName" reference/vanilla-26.3/net/minecraft -g "*.java"
```

升级 Minecraft 后，不得继续使用旧版源码 Jar 或旧版 `reference/vanilla-xx.x/`；重新运行生成任务，并解压到新的版本目录。

## 分层边界

- `common/` 可以调用 Minecraft API，但不得导入 `net.fabricmc.*`、`net.neoforged.*` 或其他 ModLoader API。
- 加载器专有功能必须放在 `fabric/` 或 `neoforge/`。共享能力应先在 `common/` 定义加载器无关的协议或数据结构，再由平台层接入。
- 不得在三个子项目复制同一份共享实现；`multiloader-loader` 已复用 `common` 的 Java、Kotlin、生成源码和资源。
- Access Widener 只服务 Fabric，Access Transformer 只服务 NeoForge。共享代码需要额外访问权限时必须同时核验两个平台。
- 不得在子项目脚本硬编码版本；版本统一来自 `gradle.properties` 和 `gradle/libs.versions.toml`。

## i18n 约束

- Module、HUD、Setting、SettingGroup、Enum 选项和静态 UI 文案必须进入 i18n，不得在 GUI 类散落重复 key。
- 名称 key 使用 `toLowerCase()`；空格保留，不自动转下划线。
- `_value` 只能保存当前 object 对应 key 的翻译，不得作为普通路径段。
- i18n JSON 叶节点只能是字符串，不允许数组、数字、布尔或 null。
- 新增或删除可翻译对象后必须生成模板、运行 `scripts/complete_i18n.py`，并人工填写 `en_us.json` 与 `zh_cn.json`，不得把空模板视为完成翻译。

## 提交前检查

- 运行与改动范围匹配的任务。共享行为至少检查 Fabric 和 NeoForge 编译；Mixin、资源、启动或 Gradle 变更运行完整 `buildRelease`。
- 最终执行 `git diff --check`、`git diff -- AGENTS.md` 和 `git status --short`。
- 文档入口为 [`README.md`](README.md) 与 [`README_zh.md`](README_zh.md)；移动或重命名文档时必须修复仓库内链接。
