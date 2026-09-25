# 模块与 Addon

## Module 基本模式

本体功能模块继承 `Module`。现有模块通常使用单例和私有构造函数：

```java
public class MyModule extends Module {

    public static final MyModule INSTANCE = new MyModule();

    private final SettingGroup sgGeneral = settingGroup("General");
    private final BoolSetting enabledOption = boolSetting("Enabled Option", true).group(sgGeneral);
    private final DoubleSetting range = doubleSetting(
            "Range", 3.0, 1.0, 6.0, 0.1,
            enabledOption::getValue
    ).group(sgGeneral);

    private MyModule() {
        super("My Module", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
    }
}
```

`Module.setEnabled(true)` 会先订阅事件、发送通知，再调用 `onEnable()`；禁用时先取消订阅、发送通知，
再调用 `onDisable()`。

上面的 `@EventHandler` 写法是 **Legacy 模式**（`ModuleDispatchMode.LEGACY`）：`setEnabled` 会把模块
订阅到 `EventBus`。新建的本体模块应改用下面的 Module Orchestrator，`COMBAT` 分类下的模块已全部迁移。

`Module.resetCustomState()`、`saveCustomState()`、`loadCustomState(JsonObject)` 用于 Setting 之外的持久化
状态。`setDefaultEnabled()` 和 `setDefaultHidden()` 同时影响 `reset()` 行为；普通模块默认 disabled、hidden。

键位默认值为 `-1`。`Module.BindMode.Toggle` 在按下时切换，`Hold` 在按下时启用、松开时禁用。鼠标键由
`KeybindUtils` 编码：26.3 起键盘保存 SDL 扫描码，鼠标键保存 SDL 编号（左 1、中 2、右 3）。

## Module Orchestrator

声明式节点图的完整设计见 [Module Orchestrator 设计](../architecture/module-orchestration-design.md)；
本节只给出写模块时的落地约定。

模块在构造函数里设置 `setDispatchMode(ModuleDispatchMode.MANAGED)`，然后把行为拆成若干 `ModulePart`：

```java
private MyModule() {
    super("My Module", Category.COMBAT);
    setDispatchMode(ModuleDispatchMode.MANAGED);
    part(new TickPart());
    part(new CommitPart());
}

/** DECIDE：只读取状态并算出本 tick 的意图，不产生副作用。 */
private final class TickPart implements ModulePart {
    @Override
    public void declare(ModuleDeclaration declaration) {
        node(PlayerTickEvent.Pre.class, NodeKey.of("decide.target"))
                .phase(Phase.DECIDE)
                .handler(MyModule.this::decideTarget);
    }
}
```

约定：

- **NodeKey 表达业务含义**，用点号分组，前缀只能是 `observe` / `decide` / `transform` / `commit` /
  `render` / `cleanup` 之一；不要用方法名或事件类名当身份。
- **阶段必须与真实副作用一致**：`OBSERVE` 只读；`DECIDE` 只产出决策；`TRANSFORM` 只改事件字段；
  `COMMIT` 才允许发包、攻击、放置、切换物品栏、写 `RotationManager`；`RENDER` 只提交渲染命令；
  `CLEANUP` 负责离开世界后的恢复。把攻击写在 `OBSERVE` 里是迁移前最常见的错误。
- **priority 只用于同阶段稳定排序**，语义是数值越大越先执行，沿用 `EventPriority` 的整数
  （`HIGHEST=200`、`MEDIUM=0`、`LOWEST=-200`）。默认 `MEDIUM` 时省略 `.priority(...)`。
- **`NodeRef` 依赖不能跨事件类型**：`before` / `after` 只在同一事件类型的 `DispatchPlan` 内解析，
  跨事件顺序由阶段语义保证。需要保存节点引用时让 Part 自己持有 `NodeRef`。
- **一个业务行为只能有一个入口**：Managed 模块不得再写 `@EventHandler`，也不得调用
  `EventBus.INSTANCE.subscribe(this)`。`ModuleManager` 只会把 `MANAGED` 模块交给
  `ModuleOrchestrator.register(...)`，`COMBAT` 分类下的 `LEGACY` 模块会被 `LegacyAdapter` 兜底转换。
- **跨线程状态必须 volatile**：`OBSERVE` 在主线程把不可变快照交给 worker，worker 只写回结果字段；
  worker 不得读取 Setting、世界或玩家。

拆分参考实现：

- 中等规模模块：`modules/impl/combat/AimBot.java`（单文件、多 Part）。
- 大型模块：`modules/impl/combat/zealot_crystal_plus/`（父 Module 持有 Setting 与状态，Part 分文件，
  不可变快照集中在 `ZealotSnapshot`，纯计算在 `ZealotDamage` / `ZealotMath`）。

本地校验（沙箱内无外网、无法运行 Gradle 时）：

```shell
pwsh -File scripts/dev/compile-check.ps1 -Target common
pwsh -File scripts/dev/orch-verify.ps1 -Task all
```

`orch-verify.ps1` 会重新编译整个 `common` 并校验调度契约与所有 combat 模块的节点图不变量
（键唯一、依赖存在、无环、阶段与 priority 稳定、事件类型与处理器一致）。

## Setting DSL

`Module` 与 `EpsilonAddon` 都实现 `SettingHost`，共享同一套 DSL，也都支持适用类型的 `onChanged` 重载。

可用设置：

- `boolSetting`、`intSetting`、`doubleSetting`、`enumSetting`、`colorSetting`
- `stringSetting`、`stringListSetting`、`keybindSetting`、`buttonSetting`
- `blockListSetting`、`itemListSetting`、`entityTypeListSetting`
- `enchantmentListSetting`、`soundEventListSetting`

完整重载以 `SettingHost.java` 为准。依赖类型为 `Setting.Dependency`；返回 `false` 时设置不可用且不可见：

```java
private final BoolSetting advanced = boolSetting("Advanced", false);
private final IntSetting threshold = intSetting(
        "Threshold", 50, 0, 100, 1,
        advanced::getValue,
        value -> refresh(value)
);
```

相关能力：

- `settingGroup(name)` 在顶层按名称忽略大小写复用分组。
- `SettingGroup.child(name)` 在父分组下按名称忽略大小写复用子分组，可以继续嵌套；子分组只属于创建它的
  父分组，父子关系决定 GUI 缩进和翻译 key 层级。
- `.group(group)` 仅指定 GUI 分组（可以是任意层级的子分组），不负责注册 Setting。
- `.rootSetting()` 表示值由根配置单独持久化；当前 `ClientSetting.showWelcomeScreen`、`WorldTweaks`
  的雾与时间设置使用它。
- `.applyWhenRelease()` 表示滑动或编辑结束后再应用昂贵更新。
- `Setting.isAvailable()` 的语义由 dependency 决定；DSL 默认传入恒真的 dependency。

嵌套分组示例；父分组内直接 Setting 与首次出现的子分组按声明顺序交错渲染：

```java
private final SettingGroup sgWeapon = settingGroup("Weapon");
private final SettingGroup sgEnchants = sgWeapon.child("Enchants");
private final SettingGroup sgSword = sgEnchants.child("Sword");

private final BoolSetting autoSwitch = boolSetting("Auto Switch", true).group(sgWeapon);
private final IntSetting minLevel = intSetting("Min Level", 1, 1, 5, 1).group(sgSword);
```

## Addon

`EpsilonAddon` 提供元信息、Addon 自身设置和模块注册能力：

- 必须重写 `onSetup()`。
- 可选重写 `getDisplayName()`、`getDescription()`、`getVersion()`、`getAuthors()`。
- 在 `onSetup()` 中通过受保护的 `registerModule(module)` 注册 Addon 模块；注册会把模块交给
  `ModuleManager.registerAddonModule(...)` 并绑定 Addon 的翻译前缀。

`AddonManager` 按 ID 去重并只执行一次 setup，空 ID 和重复 ID 的注册会被忽略并记录警告；晚注册对象不会
自动初始化。`AddonManager.setupAddons()` 逐个隔离异常，单个 Addon 失败不会阻断其他 Addon。

平台收集方式：

- Fabric 使用自定义 entrypoint key `epsilon:addon`，入口实现 `FabricEpsilonAddonEntrypoint`。
- NeoForge 通过 `NeoForge.EVENT_BUS` 发布平台 `EpsilonAddonSetupEvent` 收集 Addon。

接入细节见 [Addon 开发](../addon-development.md)。强制注册、状态恢复和事件包前缀约束见
[`AGENTS.md`](../../AGENTS.md)。
