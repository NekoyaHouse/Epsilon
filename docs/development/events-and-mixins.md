# 事件与 Mixin

## EventBus

`EventBus.INSTANCE` 是独立于加载器事件系统的精确类型事件总线：

```java
EventBus.INSTANCE.subscribe(object);
EventBus.INSTANCE.unsubscribe(object);
EventBus.INSTANCE.subscribe(SomeStaticListener.class);
EventBus.INSTANCE.post(event);
```

- `subscribe(object)` 订阅实例方法。
- `subscribe(Class)` 只订阅 static 监听器。
- EventBus 按事件的精确运行时 class 查找监听器。例如监听 `Render2DEvent` 不会收到 `Render2DEvent.HUD`。
- priority 数值越大越先执行。
- `EventPriority` 为 `HIGHEST=200`、`HIGH=100`、`MEDIUM=0`、`LOW=-100`、`LOWEST=-200`。
- 同优先级保留插入顺序。

## 可取消事件

`Cancellable` 是类，可取消事件通过继承它获得：

```java
event.cancel();
if (event.isCancelled()) {
    return;
}
```

EventBus 在某个监听器取消事件后立即停止调用后续监听器。

## 当前事件分组

实际字段和构造参数以 `common/src/main/java/com/github/epsilon/events/impl/` 为准。

| 分组 | 事件 |
|---|---|
| Tick/生命周期 | `ClientTickEvent.Pre/Post`、`PlayerTickEvent.Pre/Post`、`GameJoinedEvent`、`GameLeftEvent`、`LevelUpdateEvent`、`RespawnEvent` |
| 渲染 | `Render2DEvent.Level/HUD`、`Render3DEvent`、`AfterRender3DEvent`、`RotationAnimationEvent`、`ChunkOcclusionEvent` |
| 输入/界面 | `KeyPressEvent`、`MousePressEvent`、`MouseScrollEvent`、`KeyboardInputEvent`、`OpenScreenEvent` |
| 网络 | `PacketEvent.Send/Receive`、`SendPositionEvent` |
| 战斗/交互 | `AttackEntityEvent`、`AttackSlowDownEvent`、`AttackYawEvent`、`RightClickEvent`、`UseItemEvent`、`StartUseItemEvent`、`SwingHandEvent` |
| 方块 | `BlockCollisionEvent`、`StartDestroyBlockEvent`、`DestroyBlockEvent`、`DestroyedBlockEvent`、`PlaceBlockEvent` |
| 移动 | `MoveEvent`、`StrafeEvent`、`TravelEvent`、`JumpEvent`、`SlowdownEvent`、`FallFlyingEvent`、`FireworkRotationEvent` |
| Raytrace | `RaytraceEvent`、`UseItemRaytraceEvent` |

`Render2DEvent` 当前携带 Minecraft 26.1.2 的 `GuiGraphicsExtractor`。

## Mixin 配置

| 平台 | Mixin 配置 | 访问扩展 |
|---|---|---|
| 共享 | `common/src/main/resources/epsilon.mixins.json` | 不适用 |
| Fabric | `fabric/src/main/resources/epsilon.fabric.mixins.json` | `common/src/main/resources/epsilon.accesswidener` |
| NeoForge | `neoforge/src/main/resources/epsilon.neoforge.mixins.json` | `common/src/main/resources/META-INF/accesstransformer.cfg` |

Mixin 类名使用 `Mixin<目标类名>`。注入目标、描述符、局部变量和调用点必须以当前版本参考源码为准；具体强制规则见 [`AGENTS.md`](../../AGENTS.md)。
