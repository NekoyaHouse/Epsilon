# 国际化

## Key 规则

| 所有者 | Key 格式 |
|---|---|
| 本体模块 | `epsilon.modules.{module name lower-case}` |
| 本体 HUD | `epsilon.elements.{element name lower-case}` |
| 分类 | `epsilon.categories.{category}` |
| Addon 模块 | `{addonId}.modules.{module name lower-case}` |
| Addon 自身设置 | `{addonId}.settings.{setting/group name lower-case}` |

Module/HUD 的 Setting、SettingGroup 和 Enum 选项使用所属组件 key 的子 key。名称通过 `toLowerCase()` 处理，空格会保留。

静态 UI 文案集中在 `EpsilonTranslations`。`EpsilonTranslateComponent.create(prefix, suffix)` 自动添加 `epsilon.`；任意 owner 使用 `DefaultTranslateComponent.create(fullKey)`。

## JSON 格式

语言文件位于：

- `common/src/main/resources/assets/epsilon/i18n/en_us.json`
- `common/src/main/resources/assets/epsilon/i18n/zh_cn.json`

格式为与 dotted key 对应的嵌套 object，叶节点是字符串。父 key 同时具有自身翻译和子 key 时，使用保留属性 `_value`：

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

资源重载时，`EpsilonLanguageManager` 会读取资源栈、刷新 `TranslateHolder` 并清理纹理缓存。

## 同步流程

新增或删除 Module、HUD、Setting、SettingGroup、Enum 选项或 `EpsilonTranslations` 后：

1. 调用 `I18NFileGenerator.generate("epsilon-empty-i18n.json")` 生成当前模板。可传 owner ID 只生成一个 owner。
2. 运行 `python scripts/complete_i18n.py` 补全、排序并删除多余 key；需要时使用 `--owner epsilon` 或 `--owner <addonId>`。
3. 人工填写新增翻译。
4. 校验 `en_us.json` 与 `zh_cn.json` 都是合法嵌套 object，并通过完整构建验证资源处理。

Key、`_value` 和叶节点类型的强制约束见 [`AGENTS.md`](../../AGENTS.md)。
