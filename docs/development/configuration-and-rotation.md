# 配置与旋转

## 配置目录

`ConfigHolder` 的根目录位于用户目录下的 `.epsilon/`：

```text
~/.epsilon/
├── active-config.txt
├── client-settings.json
├── configs/<name>/
├── imports/
└── exports/
```

- 模块、HUD、Addon Setting 和好友按当前活动配置保存。
- `client-settings.json` 保存标记为 root 的客户端设置。
- 配置支持新建、切换、删除、另存、重载、Zip 导入和导出。
- `saveNow()` 已由 JVM shutdown hook 调用。

安全解压、迁移和主动保存约束见 [`AGENTS.md`](../../AGENTS.md)。

## RotationManager

`RotationManager` 是抽象基类，当前实例只能通过 `Managers.ROTATION` 获取。模式由 `ClientSetting.rotationMode` 选择：

- `SilentRotationManager`：静默发包旋转。
- `SnapRotationManager`：直接修改并恢复玩家旋转。

主要 API：

```java
Managers.ROTATION.setRotations(rotation, speed);
Managers.ROTATION.setRotations(rotation, speed, Priority.High);
Managers.ROTATION.setRotations(rotation, speed, raytrace);
Managers.ROTATION.setRotations(rotation, speed, raytrace, Priority.High);

Rot2f current = Managers.ROTATION.getRotation();
Rot2f previous = Managers.ROTATION.getLastRotation();
boolean active = Managers.ROTATION.isActive();
```

旋转值类型为 `com.github.epsilon.utils.rotation.Rot2f`。

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

- `Function<Rot2f, Boolean>` raytrace 会在平滑随机偏移校验中多次调用。
- 服务端位置/旋转包会设置 S08 重置标记；下一次请求先同步真实视角。
- 旋转接近玩家真实角度时自动结束，没有 callback 或 `isDone()`。
- 需要等待命中后攻击/放置时，模块保存 pending 状态，每 tick 继续请求旋转，并用当前 `getRotation()` 做 raytrace 后执行一次性动作。
- 切换旋转模式会调用 `copyStateFrom()` 并替换实例。
