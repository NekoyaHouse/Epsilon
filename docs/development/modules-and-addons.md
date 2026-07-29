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

`Module.setEnabled(true)` 会先订阅事件、发送通知，再调用 `onEnable()`；禁用时先取消订阅、发送通知，再调用 `onDisable()`。

`Module.resetCustomState()`、`saveCustomState()`、`loadCustomState(JsonObject)` 用于 Setting 之外的持久化状态。`setDefaultEnabled()` 和 `setDefaultHidden()` 同时影响 reset 行为；普通模块默认 disabled、hidden。

键位默认值为 `-1`。`Module.BindMode.Toggle` 在按下时切换，`Hold` 在按下时启用、松开时禁用。鼠标键由 `KeybindUtils` 编码。

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

- `settingGroup(name)` 按名称忽略大小写复用分组。
- `.group(group)` 仅指定 GUI 分组，不负责注册 Setting。
- `.rootSetting()` 表示值由根配置单独持久化；当前 `ClientSetting.showWelcomeScreen` 使用它。
- `.applyWhenRelease()` 表示滑动或编辑结束后再应用昂贵更新。
- `Setting.isAvailable()` 的语义由 dependency 决定；DSL 默认传入恒真的 dependency。

## Addon

`EpsilonAddon` 提供元信息、Addon 自身设置和模块注册能力：

- 必须重写 `onSetup()`。
- 可选重写 `getDisplayName()`、`getDescription()`、`getVersion()`、`getAuthors()`。
- 在 `onSetup()` 中通过受保护的 `registerModule(module)` 注册 Addon 模块。

`AddonHolder` 按 ID 去重并只执行一次 setup，晚注册对象不会自动初始化。

平台收集方式：

- Fabric 使用自定义 entrypoint key `epsilon:addon`，入口实现 `FabricEpsilonAddonEntrypoint`。
- NeoForge 通过 `NeoForge.EVENT_BUS` 发布平台 `EpsilonAddonSetupEvent` 收集 Addon。

强制注册、状态恢复和事件包前缀约束见 [`AGENTS.md`](../../AGENTS.md)。
