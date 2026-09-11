# Addon Development Guide

本文档介绍如何为 Epsilon 开发 Addon，并同时兼容 Fabric 与 NeoForge。

## 1. 基础 API

使用 `common` 中的统一 Addon 基类：

- `com.github.epsilon.addon.EpsilonAddon`

按加载器使用对应的注册入口：

- Fabric: `com.github.epsilon.addon.EpsilonAddonSetupEvent`
- NeoForge: `com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent`

## 2. 编写 Addon 类

最小 Addon 示例：

```java
package your.mod.addon;

import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.settings.impl.BoolSetting;

public class ExampleAddon extends EpsilonAddon {

    private final BoolSetting enableParticles = boolSetting("Enable Particles", true);

    public ExampleAddon() {
        super("example_addon");
    }

    @Override
    public String getDisplayName() {
        return "Example Addon";
    }

    @Override
    public String getDescription() {
        return "An example addon that demonstrates addon metadata and addon settings.";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void onSetup() {
        // 在这里注册模块。
        // registerModule(new YourModule());
    }
}
```

### Addon 元信息与 Addon Setting

- `getAddonId()`：Addon 的唯一 ID，构造时传入，必须非空且全局唯一。
- `getDisplayName()`：用于在 `Client Settings -> Addons` 中显示名称。
- `getDescription()`：用于显示 Addon 简介。
- `getVersion()` / `getAuthors()`：用于显示基础信息。
- `boolSetting(...)` / `intSetting(...)` / `enumSetting(...)` 等：用于声明 Addon 自身设置，这些设置会
  显示在 `Client Settings -> Addons` 中，并随配置一起保存。
- 设置可以继续使用 `settingGroup(...).group(...)` 手动声明分组。GUI 只根据显式 group 生成可折叠
  section，不根据名称或控件类型自动推断分组。

Addon setting 的翻译 key 约定为：

- `{addonId}.settings.{settingNameLowerCase}`
- `{addonId}.settings.{groupNameLowerCase}` 用于 Addon SettingGroup 标题。

Addon 模块翻译 key 为：

- `{addonId}.modules.{moduleNameLowerCase}`

`registerModule(module)` 会自动绑定模块及其 Setting 的翻译前缀，Addon 自身的 setting 由
`initAddonI18n()` 绑定。因为 `I18NFileGenerator` 尚未生成 Addon 自身 setting 的模板，这部分 key 需要
手工维护；同步流程见 [国际化](development/internationalization.md)。

## 3. Fabric 接入

### 3.1 实现 Entrypoint 接口

```java
package your.mod.fabric;

import com.github.epsilon.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.fabric.addon.FabricEpsilonAddonEntrypoint;
import your.mod.addon.ExampleAddon;

public class ExampleFabricAddonEntrypoint implements FabricEpsilonAddonEntrypoint {

    @Override
    public void registerAddon(EpsilonAddonSetupEvent event) {
        event.registerAddon(new ExampleAddon());
    }
}
```

### 3.2 在 `fabric.mod.json` 注册 entrypoint

```json
{
  "entrypoints": {
    "epsilon:addon": [
      "your.mod.fabric.ExampleFabricAddonEntrypoint"
    ]
  }
}
```

Epsilon Fabric 会在客户端初始化时自动读取 `epsilon:addon` 并注册 addon。

## 4. NeoForge 接入

NeoForge 使用 `NeoForge.EVENT_BUS` 注册 addon：

```java
package your.mod.neoforge;

import com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import your.mod.addon.ExampleAddon;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class ExampleNeoHook {

    @SubscribeEvent
    public static void onAddonSetup(EpsilonAddonSetupEvent event) {
        event.registerAddon(new ExampleAddon());
    }
}
```

## 5. 异常与去重行为

- Fabric entrypoint 隔离：单个 entrypoint 抛异常时只记录 `Failed to register addon entrypoint from mod:`
  日志，不会阻断其他 addon 注册。
- Addon 注册去重：`AddonManager` 忽略空 ID 与重复 ID 的注册，并记录
  `忽略无有效ID的插件：` / `忽略重复插件ID：`。
- Addon setup 隔离：单个 addon 的 `onSetup()` 失败时只记录 `插件初始化失败：`，不会阻断其他 addon 加载；
  成功时记录 `插件加载完成：`。

建议在 addon 内部继续做好自身异常处理，避免注册到一半时产生不可预期状态。

## 6. 调试建议

- 检查日志关键词：`插件加载完成：`、`插件初始化失败：`、`忽略重复插件ID：`、
  `Failed to register addon entrypoint from mod:`。
- 首次接入时先做一个最小 addon，只输出日志，确认生命周期后再逐步注册模块。

模块与 Addon 的注册顺序、Setting DSL 约束见 [模块与 Addon](development/modules-and-addons.md) 和
[`AGENTS.md`](../AGENTS.md)。
