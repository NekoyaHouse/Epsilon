# Epsilon 本体开发约束

本文件只保存对仓库内开发工作的强制约束。项目架构、API 说明和开发流程位于 [`docs/README.md`](docs/README.md)。遇到文档与源码不一致时，以当前源码、`gradle/libs.versions.toml` 和本地 Minecraft 参考源码为准，并在同一次修改中更新对应文档。

## 开发前的强制检查

1. 先查看 `git status --short`，不得覆盖或回滚不属于本次任务的改动。
2. 先查看 `gradle/libs.versions.toml`，确认 Minecraft、加载器、映射和依赖版本。
3. 涉及 Minecraft 类、方法、字段、渲染流程、网络包或 Mixin 目标时，必须直接查阅当前版本源码。源码由 NeoForge ModDev 生成并保存在 `common/build/moddev/`。
4. 不得根据训练数据或记忆猜测 Minecraft API。类名存在不代表方法签名、调用时机或线程约束仍然相同。
5. 涉及 Fabric 或 NeoForge API 时，再查对应加载器实现和官方文档；不得把加载器专有调用放进 `common/`。
6. 修改架构、公共 API、配置格式、资源格式、注册流程或构建流程时，必须同步更新 `docs/`；修改本文件中的约束时同步更新 `AGENTS.md`。

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
mkdir -p reference && unzip common/build/moddev/artifacts/vanilla-*-sources.jar -d reference/vanilla-26.2/
```

解压后可使用 `rg` 检索，例如：

```shell
rg -n "class Minecraft|record KeyEvent" reference/vanilla-26.2 -g "*.java"
rg -n "methodName" reference/vanilla-26.2/net/minecraft -g "*.java"
```

升级 Minecraft 后，不得继续使用旧版源码 Jar 或旧版 `reference/vanilla-xx.x/`；重新运行生成任务，并解压到新的版本目录。

## 分层边界

- `common/` 可以调用 Minecraft API，但不得导入 `net.fabricmc.*`、`net.neoforged.*` 或其他 ModLoader API。
- 加载器专有功能必须放在 `fabric/` 或 `neoforge/`。共享能力应先在 `common/` 定义加载器无关的协议或数据结构，再由平台层接入。
- 不得在三个子项目复制同一份共享实现；`multiloader-loader` 已复用 `common` 的 Java、生成源码和资源。
- Access Widener 只服务 Fabric，Access Transformer 只服务 NeoForge。共享代码需要额外访问权限时必须同时核验两个平台。
- 不得在子项目脚本硬编码版本；版本统一来自 `gradle.properties` 和 `gradle/libs.versions.toml`。

## 生命周期约束

不得随意改变 `EpsilonCommon.init()` 的初始化顺序。Addon 必须在 `AddonManager.setupAddons()` 前完成收集；配置加载依赖模块、HUD 和 Addon Setting 已注册；Manager 预热必须早于配置恢复。

- `ModuleManager.initModules()`、`HudElementManager.initElements()` 和 `AddonManager.setupAddons()` 必须在 `ConfigManager.initConfig()` 之前完成。
- Manager 统一为 `public static final Xxx INSTANCE` 加私有构造函数的单例；调用方不得为已由 Manager 持有的资源另建实例。
- `RotationManager.INSTANCE` 是可变静态字段，切换旋转模式会替换实例；每次使用都必须重新读取，不得长期缓存。
- GPU renderer、render target、字体 atlas 和 shader 必须在渲染线程创建和使用。
- 字段持有 renderer 时使用 `Suppliers.memoize(Renderer::create)` 延迟创建，或交给 `RendererManager` 注册。
- 不再使用的 GPU 资源调用 `close()`；全局销毁交给 `RendererManager`、`RenderTargetManager`、`ShaderManager` 等 Manager 生命周期。
- `LanguageReloadListener` 触发的资源重载必须保持线程安全；语言与翻译刷新不得持有已失效的资源。

## Module 与 Addon 约束

- 本体模块优先使用 `public static final ... INSTANCE` 和私有构造函数；维护既有例外时遵循现状，不做无关统一。
- Setting 必须是实例字段，通过 `SettingHost` DSL 自动注册。依赖条件使用 lambda 或方法引用延迟读取，不得在字段初始化时固化结果。
- 世界内事件处理器先执行 `nullCheck()`；仅依赖主菜单或资源系统的处理器按实际前置条件检查。
- 模块禁用必须恢复按键、计时器、物品栏、旋转 pending 状态、缓存和其他外部状态。
- 内置模块必须加入 `ModuleManager.initModules()`；HUD 元素必须加入 `HudElementManager.initElements()`，不得只创建 `INSTANCE` 而漏注册。
- 仅在确有 Setting 之外的持久状态时重写 `resetCustomState()`、`saveCustomState()`、`loadCustomState(JsonObject)`。
- Addon ID 必须非空且全局唯一；`AddonManager` 会忽略空 ID 和重复 ID 的注册。
- Addon 模块只能在 `onSetup()` 中通过 `registerModule(module)` 注册；注册发生在 `AddonManager.setupAddons()` 执行期间。
- 外部 Addon 在 `com.github.epsilon` 之外声明 `@EventHandler` 时，必须为自己的包前缀注册 EventBus lambda factory。

## 事件与 Mixin 约束

- EventBus 按事件精确运行时 class 分发；不得假设父类型监听器会收到子类型事件。
- 监听方法必须带 `@EventHandler`、返回 `void`，且只有一个非 primitive 参数。
- `Cancellable` 是类。使用 `cancel()`/`isCancelled()`，不存在 `setCancelled(boolean)`。
- 有明确监听顺序时必须设置 priority，不得依赖跨对象的隐式注册顺序。
- 修改 Minecraft 事件字段、构造参数或触发点前必须核对 `common/src/main/java/com/github/epsilon/events/impl/` 和当前版本参考源码。
- 共享 Mixin 目标优先放 `common/mixins`；仅在加载器字节码或调用链不同时拆到平台 Mixin。
- 新 Mixin 必须加入对应 JSON；删除或重命名时同步移除旧条目。
- Mixin 类名使用 `Mixin<目标类名>`；优先使用 `@Inject`、MixinExtras `@WrapOperation` 等局部注入，避免 `@Overwrite`。
- `defaultRequire` 为 1。不得用 `require = 0` 掩盖未核验的注入点。
- Sodium/Iris 兼容 Mixin 使用 `@Pseudo` 与 `targets = "..."` 软定位；不得在 Epsilon 中直接引用其类。
- Mixin 发布可修改事件时，必须在注入点处理取消状态或修改后的值。
- `Render2DEvent` 携带 `GuiGraphicsExtractor`，`Render2DEvent.Level` 与 `Render2DEvent.HUD` 共用该类型；修改 2D 渲染前必须核验当前版本的提取/提交流程。

## 配置与运行时状态约束

- 配置根目录固定为用户目录下的 `.epsilon/`，不得改到仓库或游戏运行目录。
- Zip 导入必须继续使用 `ConfigManager` 的安全解压逻辑，不得绕过路径穿越校验。
- 修改配置 schema 时必须同步更新 `ConfigManager.CONFIG_VERSION`、迁移逻辑和文档；不得直接丢弃旧配置。
- 影响数据完整性的操作应主动保存，不能只依赖 JVM shutdown hook。
- Rotation 请求每次从 `RotationManager.INSTANCE` 读取；切换模式会通过 `copyStateFrom()` 替换实例。
- raytrace 回调会在平滑旋转校验中被多次调用，必须无副作用。
- 服务端位置/旋转包会重置托管旋转；需要等待命中后攻击或放置时，由模块自行维护 pending 状态并保证动作只执行一次。

## GUI、HUD 与渲染约束

- Panel、Dropdown、popup 和 HUD chrome 优先通过 `UiTree`/`UiScene`/`UiRenderBatch` 提交，不得为每个面板各建一套 renderer。
- 一个 Screen 或 HUD 帧共享一个 `UiScene`；`beginFrame()` 与 `endFrame()` 必须配对。
- 存在遮挡关系的 pass 必须使用不同的 `UiLayer` 或相对 layer 表达，相对 layer 必须在 `-99..99` 内。
- HUD 尺寸变化调用 `setBounds()`；位置通过 anchor/move API 修改，不得绕过 anchor 状态直接写持久化坐标。
- `Render2DScheduler` 按 layer 规划批次并复用 renderer；需要严格遮挡顺序时必须显式分层，不能依赖同 layer 内不同 pipeline 的提交顺序。
- `Render3DScheduler` 在 priority `-999` flush；模块必须在更高 priority 的监听器中提交命令。
- 2D 世界覆盖层优先使用 `WorldToScreen.calcWorld2Screen`，不得再次除以 GUI scale，也不得自行用归一化深度判断摄像机后方。
- 2D AABB 边界必须投影 8 个顶点后取屏幕空间最小/最大坐标；贴近近裁剪面时还要把边与近裁剪面的交点纳入边界。
- 文本测量与绘制必须使用相同的 scale 和 font loader；自定义字体经 `StaticFontLoader` 解析，失败时回退默认字体。
- 调用后处理前必须核验 render target、采样器和当前 RenderPipeline 状态。

## i18n 约束

- Module、HUD、Setting、SettingGroup、Enum 选项和静态 UI 文案必须进入 i18n，不得在 GUI 类散落重复 key。
- 名称 key 使用 `toLowerCase()`；空格保留，不自动转下划线。
- `_value` 只能保存当前 object 对应 key 的翻译，不得作为普通路径段。
- i18n JSON 叶节点只能是字符串，不允许数组、数字、布尔或 null。
- 新增或删除可翻译对象后必须生成模板、运行 `scripts/complete_i18n.py`，并人工填写 `en_us.json` 与 `zh_cn.json`，不得把空模板视为完成翻译。

## 代码规范

- Java 25、UTF-8，沿用相邻代码格式和命名。
- 新增 Javadoc 与解释性注释使用中文；只注释不明显的约束、线程或算法原因。
- Logger 使用 `Constants.LOGGER`。
- Minecraft 实例使用 `Constants.mc`；Module/HudModule 内优先使用继承的 `mc`。
- 不得吞异常。边界层隔离单个 Addon 或资源失败时，记录包含上下文的日志。
- 不做与任务无关的重构、批量格式化或生成文件改写。
- 修改 `native/smtc` 时必须同步重建 `common/src/main/resources/natives/windows-x86_64/epsilon_smtc.dll`，并保持 Java 侧 JNI 签名一致。

## 提交前检查

- 运行与改动范围匹配的任务。共享行为至少检查 Fabric 和 NeoForge 编译；Mixin、资源、启动或 Gradle 变更运行完整 `buildRelease`。
- 最终执行 `git diff --check`、`git diff -- AGENTS.md` 和 `git status --short`。
- 文档入口和主题划分见 [`docs/README.md`](docs/README.md)；文档移动或重命名时必须修复仓库内链接。
