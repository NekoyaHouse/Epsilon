# 国际化

## Key 规则

| 所有者 | Key 格式 |
|---|---|
| 本体模块 | `epsilon.modules.{module name lower-case}` |
| 本体 HUD | `epsilon.elements.{element name lower-case}` |
| 分类 | `epsilon.categories.{category}` |
| Addon 模块 | `{addonId}.modules.{module name lower-case}` |
| Addon 自身设置 | `{addonId}.settings.{setting/group name lower-case}` |

Module/HUD 的 Setting、SettingGroup 和 Enum 选项使用所属组件 key 的子 key。名称通过 `toLowerCase()`
处理，空格会保留。

静态 UI 文案集中在 `EpsilonTranslations`。`EpsilonTranslateComponent.create(prefix, suffix)` 自动添加
`epsilon.` 前缀；任意 owner 使用 `DefaultTranslateComponent.create(fullKey)`。未命中的 key 在
`ClientSetting.i18nFallback` 打开时按最后一段生成可读名称，关闭时直接显示原始 key。

## JSON 格式

语言文件位于：

- `common/src/main/resources/assets/epsilon/i18n/en_us.json`
- `common/src/main/resources/assets/epsilon/i18n/zh_cn.json`

格式为与 dotted key 对应的嵌套 object，叶节点是字符串。父 key 同时具有自身翻译和子 key 时，使用保留
属性 `_value`：

```json
{
  "epsilon": {
    "modules": {
      "kill aura": {
        "_value": "Kill Aura",
        "mode": {
          "_value": "Mode",
          "single": "Single"
        }
      }
    }
  }
}
```

`I18NJson` 在读取时拒绝非字符串叶节点（数组、数字、布尔、null），写入时拒绝空路径段和把 `_value`
当作普通路径段。语言文件按 Dotted key 展开，`_value` 只保存父 key 自身的翻译。

## 加载流程

`EpsilonLanguageManager` 在语言切换和资源重载时重新读取资源栈：

1. 先加载 `en_us`，再叠加当前语言（`EpsilonLanguage.English` / `ChineseSimplified` /
   `Custom`，Custom 使用 `ClientSetting.customLanguage`）。
2. 遍历所有 namespace 的 `i18n/<code>.json`，因此 Addon 可以自带语言文件。
3. 把原版格式化占位符（`%d`、`%f`）规整为 `%s`，避免翻译文本触发格式异常。
4. 刷新 `TranslationManager` 中登记的 `TranslateComponent` 缓存。

Fabric 通过 `ResourceLoader.registerReloadListener`、NeoForge 通过 `AddClientReloadListenersEvent`
注册 `LanguageReloadListener`，两者都调用同一份加载逻辑。

## 同步流程

新增或删除 Module、HUD、Setting、SettingGroup、Enum 选项或 `EpsilonTranslations` 后：

1. 启动一次客户端（`./gradlew :fabric:runClient` 或 `:neoforge:runClient`），
   `I18NFileGenerator.generate("epsilon-empty-i18n.json")` 会在
   `fabric/runs/client/` 或 `neoforge/runs/client/` 写出当前模板。
2. 运行 `scripts/complete_i18n.py` 按模板补全、排序并删除多余 key：

   ```shell
   python scripts/complete_i18n.py --source fabric
   python scripts/complete_i18n.py --source neoforge --owner epsilon
   ```

   脚本支持 `--source fabric|neoforge|custom`、`--empty-i18n`、`--target`、`--owner`（本体用
   `epsilon`，Addon 用其 addonId）、`--dry-run` 与 `--no-backup`；未指定参数时进入交互选择。
3. 人工填写新增翻译，确认 `en_us.json` 与 `zh_cn.json` 都是合法嵌套 object。
4. 通过完整构建验证资源处理。

注意：26.2.x 的 `I18NFileGenerator` 目前只遍历本体 key、分类、静态 `EpsilonTranslations` 以及已注册
模块/HUD（含带 addonId 的 Addon 模块），Addon 自身 setting 的模板生成尚未启用；这部分 key 需要按
`{addonId}.settings.*` 约定手工维护。

Key、`_value` 和叶节点类型的强制约束见 [`AGENTS.md`](../../AGENTS.md)。
