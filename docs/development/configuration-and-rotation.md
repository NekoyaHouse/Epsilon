# 配置与旋转

## 配置目录

`ConfigManager` 的根目录位于用户目录下的 `.epsilon/`：

```text
~/.epsilon/
├── active-config.txt
├── client-settings.json
├── accounts.json
├── configs/<name>/
│   ├── <addonId>/<moduleName>.json
│   ├── <addonId>/addon-settings.json
│   └── friends.json
├── imports/
└── exports/
```

- 模块、HUD 和 Addon Setting 按当前活动配置保存；`epsilon` 是本体模块与 HUD 的 `<addonId>`。
- `client-settings.json` 保存标记为 root 的客户端设置，`accounts.json` 保存账号列表。
- 配置支持新建、切换、删除、另存、重载、Zip 导入和导出；导出时写入 `config-info.json` 元数据。
- `saveNow()` 已由 JVM shutdown hook 调用，账号增删也会主动保存。
- `CONFIG_VERSION` 当前为 3；旧版 `config.json` 布局由 `LegacyConfigMigrator` 迁移到新目录结构。
- 配置名会做非法字符与 `..` 校验；Zip 导入通过 `unzipSecurely` 拒绝越界条目，不得绕过该校验。

## RotationManager

`RotationManager` 是抽象基类，当前实例通过可变的 `RotationManager.INSTANCE` 获取。模式由
`ClientSetting.rotationMode` 选择：

- `RotationMode.SILENT` → `SilentRotationManager`：静默发包旋转。
- `RotationMode.SNAP` → `SnapRotationManager`：直接修改并恢复玩家旋转。

`RotationManager.switchRotationManager(mode)` 会在切换时通过 `copyStateFrom()` 迁移状态，并替换
`INSTANCE`，因此调用方不得缓存实例。

主要 API：

```java
RotationManager.INSTANCE.setRotations(rotation, speed);
RotationManager.INSTANCE.setRotations(rotation, speed, Priority.High);
RotationManager.INSTANCE.setRotations(rotation, speed, raytrace);
RotationManager.INSTANCE.setRotations(rotation, speed, raytrace, Priority.High);

Rot2f current = RotationManager.INSTANCE.getRotation();
Rot2f previous = RotationManager.INSTANCE.getLastRotation();
HitResult hitResult = RotationManager.INSTANCE.getHitResult();
boolean active = RotationManager.INSTANCE.isActive();
```

旋转值类型为 `com.github.epsilon.utils.rotation.Rot2f`。`getHitResult()` 返回按当前托管旋转计算的逻辑
命中结果；没有活动旋转时返回 `null`。

Rotation priority 与 EventBus priority 是两套系统：

| Priority | 数值 |
|---|---:|
| `Lowest` | 0 |
| `Low` | 10 |
| `Medium` | 50 |
| `High` | 100 |
| `Highest` | 1000 |

仅当新 priority 不低于当前活动 priority 时才覆盖请求。

运行时行为：

- `Function<Rot2f, Boolean>` raytrace 会在平滑随机偏移校验中调用，且可能在一帧内被多次调用，必须无副作用。
- 平滑后通过 `LocalPlayer.raycastHitResult(1.0f, mc.player)` 更新逻辑命中结果。
- `shouldModifyCrosshair()` 决定是否把托管旋转应用到视觉准星；`SilentRotationManager` 在
  `ClientSetting.modifyCrosshair` 关闭或 FreeCamera 启用时不修改准星。
- `SilentRotationManager` 在 `SendPositionEvent` 与 `UseItemRaytraceEvent` 中写入托管旋转，并让物品
  使用包与移动包保持同一服务端旋转；`SnapRotationManager` 直接发送 `ServerboundMovePlayerPacket.PosRot`
  并在玩家 tick 后恢复真实旋转。
- 服务端位置/旋转包会重置平滑状态，下一次请求重新同步真实视角。
- 需要等待命中后攻击/放置时，模块保存 pending 状态，每 tick 继续请求旋转，并用当前 `getRotation()`
  做 raytrace 后执行一次性动作。
- 旋转接近玩家真实角度时自动结束，没有 callback 或 `isDone()`。
