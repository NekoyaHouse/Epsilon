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

`Module.resetCustomState()`、`saveCustomState()`、`loadCustomState(JsonObject)` 用于 Setting 之外的持久化
状态。`setDefaultEnabled()` 和 `setDefaultHidden()` 同时影响 `reset()` 行为；普通模块默认 disabled、hidden。

键位默认值为 `-1`。`Module.BindMode.Toggle` 在按下时切换，`Hold` 在按下时启用、松开时禁用。鼠标键由
`KeybindUtils` 编码：26.3 起键盘保存 SDL 扫描码，鼠标键保存 SDL 编号（左 1、中 2、右 3）。

## 长矛与鞘翅战斗

- `SpearAura.INSTANCE` 在 `ModuleManager.initModules()` 注册。主手持矛时自动蓄力；目标距离、相对速度与射线校验通过后请求现有 `RotationManager.INSTANCE` 瞄准，可选择按瞄准方向移动。网络线程只记录本地玩家的 kinetic 命中，主线程延迟一 tick 尝试重锤补刀，再等待命中冷却；禁用时释放自动蓄力按键并清空 pending 状态。
- `ElytraCombat` 的重锤行为负责接近、俯冲和拉升，长矛行为负责蓄力窗口、冲锋及命中或打空后的脱战。飞行规划保留避障方向偏好，方向求解器限制意图俯仰偏差并复用安全逃逸解；`Max Turn Speed` 限制飞行意图转向，长矛近身瞄准使用更小的俯仰容差。`Debug` 默认关闭，可观察行为、轨迹与烟花决策。
- `ElytraFly` 提供 Control、Pitch40、NCP 三种模式。NCP 直接接管滑翔位移，可配置起飞、升降、限速和阻力；禁用或切换模式时释放临时计时器加速。Control 支持甲飞进食时临时装备鞘翅并恢复胸甲，`No Eat` 可拦截右键使用；烟花在低动量时缩短补发间隔，并仅在 ElytraCombat 实际驾驶时服从战斗意图。
- ElytraFly 启用时停止疾跑并短暂压制 AutoSprint。三个模块继续使用本体已有的全局转头模式，不引入模块级转头选项或新的 RotationManager 协议。

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
