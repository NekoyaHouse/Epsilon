# Module Orchestration 架构设计（审阅稿）

> 状态：提案，不改变现有运行时代码。目标是先统一模型、边界和迁移顺序，再按阶段实现。

## 1. 背景与目标

当前 `Module` 直接把自身所有 `@EventHandler` 订阅到全局 `EventBus`。总线只按事件精确类型、数值
`priority` 和插入顺序调度，因此以下关系无法被代码完整表达：

- “A 模块先产出旋转意图，B 模块再消费并裁决”；
- “同一模块的采集、决策、执行、清理必须按阶段运行”；
- “只有某个模块启用时，另一个模块才提供协作能力”；
- “一个复杂模块拆成多个文件后，子组件拥有统一生命周期、设置宿主和状态恢复”。

本提案的目标是：

1. 让监听器可以声明跨模块的 `before/after` 依赖，并在启用时生成可验证的调度计划。
2. 让模块通过类型化的能力（Capability）和帧内协作上下文交换意图，而不是互相调用实现细节或争抢
   全局可变状态。
3. 保留现有 Minecraft 事件和 Fabric/NeoForge 分层，避免把加载器 API 引入 `common/`。
4. 允许一个模块由多个 `ModulePart` 文件组成，每个 part 具有独立的监听器、生命周期和持久化状态。
5. 让日志、调试界面和测试能够回答“本帧谁在什么阶段修改了什么”。

非目标：本提案不把所有模块合并成一个大状态机，也不试图自动解决两个功能意图冲突时的业务语义。
冲突必须由能力提供者定义裁决策略或显式拒绝。

## 2. 核心模型

### 2.1 Module 是声明容器，ModulePart 是执行单元

`Module` 继续拥有名称、分类、Setting、键位和配置入口，但不再要求所有行为都写在一个类中：

```java
public final class KillAura extends Module {
    private final TargetPart target = part(new TargetPart());
    private final RotationPart rotation = part(new RotationPart());
    private final AttackPart attack = part(new AttackPart());

    private KillAura() {
        super("Kill Aura", Category.COMBAT);
    }
}
```

```java
public final class RotationPart implements ModulePart {
    @Override
    public void declare(PartDeclaration d) {
        d.id("rotation")
         .after("target.snapshot")
         .provides(RotationIntent.class);
    }

    @EventNode(event = PlayerTickEvent.Pre.class, phase = Phase.DECIDE)
    private void plan(PlayerTickEvent.Pre event, ModuleContext ctx) {
        ctx.publish(new RotationIntent(/* ... */));
    }
}
```

`ModulePart` 不继承 `Module`，因此不会出现在模块列表、键位列表或配置顶层。它可以放在独立文件、子包
或策略实现中。推荐接口如下（名称可调整）：

依赖关系的归属仍然是顶层 `Module`，而不是某个实现文件。`Module` 创建并持有自己的
`ModuleContract`，Part 只向父模块注册节点；这样拆分文件和单文件实现使用完全相同的依赖引用。单文件模块
不需要创建 `ModulePart`，直接调用 `node(...)` 即可：

```java
public final class PacketMine extends Module {
    public static final NodeKey STATE_OBSERVE = NodeKey.of("state.observe");
    public static final NodeKey COMMIT = NodeKey.of("commit");

    private final NodeRef<PlayerTickEvent.Pre> stateObserve;
    private final NodeRef<PlayerTickEvent.Pre> commit;

    private PacketMine() {
        super("Packet Mine", Category.COMBAT);

        stateObserve = node(STATE_OBSERVE)
                .event(PlayerTickEvent.Pre.class)
                .phase(Phase.OBSERVE)
                .provides(MiningSnapshot.class)
                .handler(this::observe);

        commit = node(COMMIT)
                .event(PlayerTickEvent.Pre.class)
                .phase(Phase.COMMIT)
                .after(stateObserve)
                .handler(this::commit);
    }

    private void observe(PlayerTickEvent.Pre event, ModuleContext context) {
    }

    private void commit(PlayerTickEvent.Pre event, ModuleContext context) {
    }
}
```

`node(...)` 返回的是当前 Module 的 `NodeBuilder`，调用 `.handler(...)` 后才完成注册并返回
`NodeRef<E>`。因此事件类型从 `event(PlayerTickEvent.Pre.class)` 和 `NodeRef<PlayerTickEvent.Pre>` 同时
确定，处理函数的方法引用也会受到泛型检查。

```java
public interface ModulePart {
    default void declare(PartDeclaration declaration) {}
    default void onEnable(ModuleContext context) {}
    default void onDisable(ModuleContext context) {}
    default void reset(ModuleContext context) {}
    default void save(StateWriter state) {}
    default void load(StateReader state) {}
}
```

Part 版本使用父模块传入的声明器：

```java
public final class ZealotBreakPart implements ModulePart {
    public static final NodeKey BREAK_DECIDE = NodeKey.of("break.decide");

    @Override
    public void declare(ModuleDeclaration module) {
        module.node(BREAK_DECIDE)
                .event(PlayerTickEvent.Pre.class)
                .phase(Phase.DECIDE)
                .after(module.node(ZealotNodes.TARGET_OBSERVE))
                .before(module.capability(MiningCapability.KEY).resolve())
                .handler(this::decide);
    }

    private void decide(PlayerTickEvent.Pre event, ModuleContext context) {
    }
}
```

顶层模块负责装配 Part：

```java
public final class ZealotCrystalPlus extends Module {
    private final ZealotBreakPart breakPart = part(new ZealotBreakPart());

    private ZealotCrystalPlus() {
        super("Zealot Crystal+", Category.COMBAT);
    }
}
```

`part(...)` 接收的 `ModuleDeclaration` 属于当前 Module，所以 Part 中创建的节点仍登记在父模块的
contract 下。跨模块依赖使用对方公开的 contract，例如：

```java
zealotBreak.after(PacketMine.CONTRACT.resolve());
```

模块之间公开的是最小 contract 或 `CapabilityKey<T>`，不公开 `NodeBuilder` 和私有节点，避免消费者修改
提供者的调度定义。

Part 的 Setting 通过父模块提供的 `SettingHost` 注册，禁止 Part 偷建第二个模块或绕过配置管理器。
父模块禁用时，调度器撤销该模块全部节点，然后按反向拓扑顺序调用 `onDisable`；这样可以集中恢复按键、
计时器、物品栏、rotation pending 和缓存。

### 2.2 EventNode：事件监听器的稳定身份

`@EventHandler(priority = ...)` 适合表达粗粒度优先级，但不适合跨对象依赖。新 API 用显式节点描述：

```java
@EventNode(
    event = MoveEvent.class,
    phase = Phase.TRANSFORM,
    id = "movement.fix",
    after = {"movement.input"},
    before = {"movement.commit"}
)
private void onMove(MoveEvent event, ModuleContext context) { }
```

建议字段：

- `id`：在模块内唯一；调度器使用 `moduleId + "." + id` 形成全局稳定 ID；
- `event`：精确事件类型，保持现有 EventBus 的精确分发语义；
- `phase`：有限的系统阶段，如 `OBSERVE`、`DECIDE`、`TRANSFORM`、`COMMIT`、`RENDER`、`CLEANUP`；
- `after` / `before`：引用节点 ID、模块 ID 或能力阶段；
- `priority`：仅作为同一依赖层级内的次序，默认 0；不再作为跨层的隐式协议；
- `cancellable`：声明节点是否允许取消事件，调度器可在开发模式检查不匹配。

依赖引用不应要求业务代码裸写字符串。推荐使用注册阶段生成的 `NodeRef`、能力接口的 `CapabilityKey<T>`
和模块声明对象；它们在同一编译单元内可以获得 IDE 重命名、泛型检查和注册期校验。只有跨 Addon、配置或
外部脚本的边界才使用稳定字符串 ID，并在边界解析时立即转换成类型化引用。引用不存在时报错并禁用本次
计划构建，不静默退回插入顺序。

## 2.6 类型安全的依赖引用

节点声明采用显式的声明对象，节点本身成为可复用的常量：

```java
public final class MiningNodes {
    public static final NodeKey STATE_OBSERVE = NodeKey.of("state.observe");
    public static final NodeKey RESOLVE = NodeKey.of("resolve");

    private MiningNodes() {}
}

public final class ZealotNodes {
    public static final NodeKey TARGET_OBSERVE = NodeKey.of("target.observe");
    public static final NodeKey BREAK_DECIDE = NodeKey.of("break.decide");
    public static final NodeKey CRYSTAL_COMMIT = NodeKey.of("crystal.commit");

    private ZealotNodes() {}
}
```

`NodeKey` 只负责一个模块内部的稳定身份，不允许直接跨模块拼接。跨模块引用必须通过模块声明返回的
`NodeRef`：

```java
public final class PacketMineContract {
    public static final CapabilityKey<MiningCapability> MINING =
            CapabilityKey.of(MiningCapability.class);

    public final NodeRef stateObserve;
    public final NodeRef resolve;

    public PacketMineContract(ModuleDeclaration module) {
        stateObserve = module.node(MiningNodes.STATE_OBSERVE);
        resolve = module.node(MiningNodes.RESOLVE);
    }
}

public final class ZealotContract {
    public final NodeRef targetObserve;
    public final NodeRef breakDecide;
    public final NodeRef crystalCommit;

    public ZealotContract(ModuleDeclaration module) {
        targetObserve = module.node(ZealotNodes.TARGET_OBSERVE);
        breakDecide = module.node(ZealotNodes.BREAK_DECIDE);
        crystalCommit = module.node(ZealotNodes.CRYSTAL_COMMIT);
    }
}
```

业务声明因此变成：

```java
zealotTargetObserve.after(packetMineContract.stateObserve);
zealotBreakDecide
        .after(zealotContract.targetObserve)
        .before(packetMineContract.resolve);
packetMineCommit.after(packetMineContract.resolve);
zealotCrystalCommit.after(packetMineContract.resolve);
```

能力依赖也可以直接由泛型表达，避免把能力类型写成字符串：

```java
zealotTargetObserve.requires(PacketMineContract.MINING);
zealotBreakDecide.requires(PacketMineContract.MINING);
```

`requires(CapabilityKey<MiningCapability>)` 只建立能力存在性和阶段依赖；真正读取时仍通过
`ctx.capability(PacketMineContract.MINING)` 获得 `Optional<MiningCapability>`。能力未启用是正常运行状态，
而节点 ID 拼写错误属于注册错误，两者应分别处理。

### 依赖引用的三层边界

1. **同一模块/同一源码树**：使用 `NodeKey` 常量和 `NodeRef`，IDE 可重命名，编译器可检查泛型。
2. **Addon API**：Addon 暴露 `public static final CapabilityKey<T>` 或 `ModuleContract`，消费者依赖接口
   和 contract，不依赖实现模块的 `INSTANCE`。
3. **配置、诊断和外部脚本**：允许字符串，但只能通过 `NodeId.parse(...)` 或
   `CapabilityId.parse(...)` 进入注册器；注册器检查命名空间、版本和目标是否存在，业务节点不直接保存这些
   字符串。

Java 注解不能可靠地对任意跨类字符串做编译期检查，因此不建议把 `after = {"..."}` 作为唯一 API。若后续
需要注解语法，可由注解处理器生成 `NodeKey`/`ModuleContract`，让字符串只出现在声明文件中并在编译时报告
缺失引用；运行时仍使用生成的类型化对象。

### 2.3 调度计划与启停

`ModuleOrchestrator` 维护注册表和每个事件类型的 `DispatchPlan`：

```text
注册 Module/Part
  -> 收集节点、能力和依赖
  -> 校验 ID、线程、事件类型和启用条件
  -> 按依赖做拓扑排序
  -> 同层按 phase、priority、稳定注册序排序
  -> 发布不可变 DispatchPlan
```

启用或禁用模块不会在回调中修改正在执行的计划。请求先进入 pending 队列，在当前事件派发结束后于主线程
应用，并一次性重建受影响事件的计划。这样避免监听器遍历 `CopyOnWriteArrayList` 时出现半更新状态。

拓扑排序发现环时，记录完整环路（例如 `a -> b -> c -> a`），拒绝包含该环的计划；开发环境抛出诊断
异常，生产环境禁用相关模块并保留其他模块的旧计划。系统阶段本身也是隐式边：`OBSERVE < DECIDE <
TRANSFORM < COMMIT < RENDER < CLEANUP`。

现有 `EventBus` 可以保留作为底层发布 API。迁移完成后，模块节点由 `ModuleOrchestrator` 订阅，普通
`EventBus.INSTANCE.subscribe(module)` 只允许给核心 Manager 或兼容层使用，避免两套调度同时修改同一事件。

### 2.4 ModuleContext：受限的互操作面

每次事件调用收到一个与模块绑定的 `ModuleContext`。它不是万能 Service Locator，而是以下能力的窄接口：

```java
public interface ModuleContext {
    <T> Optional<T> capability(Class<T> type);
    <T> void publish(T value);                 // 本事件/本帧的意图
    <T> List<T> values(Class<T> type);          // 只读快照
    <T> void request(ExclusiveRequest<T> req); // 交给能力裁决器
    FrameTrace trace();
}
```

共享数据分为三类：

1. **Snapshot**：目标、玩家状态、世界查询等只读快照，生产者在 `OBSERVE` 阶段写入，后续节点只读。
2. **Intent**：旋转、移动、攻击、放置、计时等意图，包含 `sourceId`、优先级、有效期和原因；不能直接
   覆盖另一个模块的字段。
3. **ExclusiveRequest**：确实只能有一个获胜者的动作，由命名能力（如 `rotation`, `hotbar`, `attack`）
   统一裁决，获胜规则必须写在能力注册处并可追踪。

能力是显式协议：

```java
public interface RotationCapability {
    void submit(RotationIntent intent);
    Optional<RotationDecision> decision();
}
```

模块依赖能力接口，不依赖另一个模块的 `INSTANCE`、私有 Setting 或实现类。能力可以由核心 Manager、某个
模块或 Addon 提供；提供者禁用时，消费者拿到 `Optional.empty()`，而不是悬空引用。

### 2.5 协作与冲突

默认规则如下：

- 读取共享状态不需要抢占；只读快照在事件开始时冻结。
- 写入原版事件字段只能在声明的 `TRANSFORM` 节点发生，并且每个字段有唯一最终提交节点。
- rotation、hotbar、timer 等资源用能力裁决，不允许直接写 Manager 的可变字段。
- 取消事件是终止协议：只有声明 `cancellable = true` 的节点可取消；取消原因写入 trace，后续节点按事件
 语义跳过。
- 两个意图优先级相同且没有明确 tie-breaker 时，裁决结果为冲突并记录警告，不使用注册顺序猜测。

这不能消除所有业务冲突，但把冲突从“谁碰巧先执行”变成可见、可测试的协议错误。

## 3. 一个跨模块流程示例

以“KillAura 选目标并请求旋转，Scaffold 需要优先看方块，MovementFix 消费最终旋转”为例：

```text
PlayerTick.Pre
  OBSERVE: Targeting.snapshot
  DECIDE: Scaffold.rotation-intent
  DECIDE: KillAura.rotation-intent
  COMMIT: RotationArbiter.resolve (Scaffold > KillAura)
  TRANSFORM: MovementFix.apply
```

对应声明可以写成：

```java
scaffoldNode.before("capability:rotation.resolve");
killauraNode.before("capability:rotation.resolve");
movementFixNode.after("capability:rotation.resolve");
```

KillAura 不需要知道 Scaffold 的类名；它只知道 rotation 能力和自己的意图会被裁决。调试 trace 可以显示
每个意图、获胜原因和最终写入点。

## 3.1 ZealotCrystalPlus 与 PacketMine：Enum 协议示例

这两个模块特别适合说明“值可以通过 Enum 传入”的用法。当前实现中，ZealotCrystalPlus 在计算抗性方块时
直接调用 `PacketMine.INSTANCE.isInstantMining(pos)`，并把 `assumeInstantMine` 固定成一个布尔开关；PacketMine
则通过静态字段暴露目标方块、完成状态和进度。两者之间的协议隐藏在实现细节里，调用方无法表达“我需要的
挖矿语义是什么”，也无法声明必须在 PacketMine 更新之后读取。

新架构中可以定义一个共享的、位于 `common` 的协议 Enum：

```java
public enum MiningKnowledge {
    NONE,              // 不假设 PacketMine 会完成
    ACTIVE_PROGRESS,   // 目标正在挖掘，只能读取当前进度
    INSTANT_READY,     // 已完成蓄力，下一次允许执行即时破坏
    COMPLETED          // 已经提交破坏包，方块状态等待服务端确认
}

public record MiningSnapshot(
        BlockPos position,
        MiningKnowledge knowledge,
        int progressPercent,
        long revision,
        String sourceId
) {}
```

PacketMine 作为 `mining` 能力提供者，不再要求消费者读取它的静态字段：

```java
public interface MiningCapability {
    Optional<MiningSnapshot> snapshot(BlockPos position);
    MiningDecision request(BlockPos position, MiningRequest request);
}

public record MiningRequest(
        MiningKnowledge required,
        MiningConsumer consumer,
        boolean mayStart
) {}

public enum MiningConsumer {
    CRYSTAL_CALCULATION,
    CRYSTAL_BREAK,
    OTHER
}
```

ZealotCrystalPlus 的配置也可以从布尔值提升为 Enum，让用户明确选择计算策略：

```java
private enum MiningAssumption {
    IGNORE,             // 与 PacketMine 无关
    ACTIVE_IS_RESISTANT,// 正在挖掘时仍按抗性方块处理
    READY_IS_INSTANT,   // 只有 INSTANT_READY 才按即时挖掘处理
    ANY_PACKET_PLAN     // ACTIVE_PROGRESS 及以上都按计划挖掘处理
}

private final EnumSetting<MiningAssumption> miningAssumption = enumSetting(
        "Mining Assumption", MiningAssumption.READY_IS_INSTANT
).group(sgCalculation);
```

计算节点只读快照，并把 Enum 转成清晰的业务谓词：

```java
@EventNode(
        event = PlayerTickEvent.Pre.class,
        phase = Phase.OBSERVE,
        id = "target.snapshot"
)
private void observeMining(PlayerTickEvent.Pre event, ModuleContext ctx) {
    MiningCapability mining = ctx.capability(MiningCapability.class).orElse(null);
    if (mining == null) return;

    ctx.publish(new MiningView(pos -> mining.snapshot(pos).orElse(null)));
}

private boolean mayTreatAsInstant(MiningSnapshot snapshot) {
    return switch (miningAssumption.getValue()) {
        case IGNORE -> false;
        case ACTIVE_IS_RESISTANT -> snapshot.knowledge() == MiningKnowledge.INSTANT_READY
                || snapshot.knowledge() == MiningKnowledge.COMPLETED;
        case READY_IS_INSTANT -> snapshot.knowledge() == MiningKnowledge.INSTANT_READY;
        case ANY_PACKET_PLAN -> snapshot.knowledge() != MiningKnowledge.NONE;
    };
}
```

这里的 Enum 有两个作用：`MiningKnowledge` 是模块间传递的协议值，`MiningAssumption` 是 ZealotCrystalPlus
自己的用户策略。两者分开后，PacketMine 可以新增 `SERVER_CONFIRMED` 或 `PAUSED` 状态，而不会把设置名、
静态字段或内部计时器暴露给 ZealotCrystalPlus。新增 Enum 值时，能力协议可以定义兼容默认值和日志提示。

### 3.2 显式的破坏协调

ZealotCrystalPlus 不应只在伤害计算中“猜测” PacketMine 状态；当它真的要破坏水晶或方块时，应提交一个
带 Enum 目的的请求：

```java
public enum MiningIntent {
    PREPARE_FOR_CRYSTAL,
    PRESERVE_FOR_CRYSTAL,
    BREAK_NOW
}

public record MiningRequest(
        BlockPos position,
        MiningIntent intent,
        int priority,
        String sourceId
) {}
```

节点依赖和执行过程变成：

```text
PacketMine.observe       -> 发布 MiningSnapshot
Zealot.target.snapshot   -> 读取快照并计算水晶方案
Zealot.break.intent      -> 提交 PRESERVE_FOR_CRYSTAL / BREAK_NOW
MiningArbiter.resolve    -> 按 MiningIntent 和优先级裁决
PacketMine.commit        -> 只在获胜后发送 START/STOP_DESTROY_BLOCK
Zealot.crystal.commit    -> 只在方块破坏承诺成立后执行水晶动作
```

对应声明可以写成：

```java
zealotBreak.after("packetmine.observe");
zealotBreak.before("capability:mining.resolve");
packetMineCommit.after("capability:mining.resolve");
crystalCommit.after("packetmine.commit");
```

这样可以消除当前两类隐患：

- ZealotCrystalPlus 在 PacketMine 尚未更新进度时读取旧的 `completed` 或 `targetPos`；
- PacketMine 正在切换物品栏或发送 STOP 包时，ZealotCrystalPlus 同一 Tick 又直接执行攻击，导致动作顺序
  和旋转/物品栏状态不一致。

如果 PacketMine 未启用，能力返回 `Optional.empty()`，ZealotCrystalPlus 根据 `MiningAssumption` 的
`IGNORE` 兼容行为继续工作；如果两个模块都启用但请求冲突，`MiningArbiter` 返回拒绝原因，trace 中会记录
“ZealotCrystalPlus 的 BREAK_NOW 被 PacketMine 的 PRESERVE_FOR_CRYSTAL 覆盖”，而不是依靠事件注册顺序。

### 3.3 ModulePart 拆分后的文件结构

两个模块可以拆成下面的结构，同时仍各自只占一个公开 Module：

```text
modules/impl/combat/zealot/
  ZealotCrystalPlus.java       // Module、Setting、Part 装配
  ZealotTargetPart.java        // OBSERVE：目标与伤害快照
  ZealotPlacementPart.java     // DECIDE/COMMIT：放置意图
  ZealotBreakPart.java         // DECIDE：读取 MiningCapability
  ZealotRenderPart.java        // RENDER：只消费快照

modules/impl/combat/packetmine/
  PacketMine.java              // Module、Setting、Part 装配
  MiningStatePart.java         // OBSERVE：发布 MiningSnapshot
  MiningProgressPart.java      // TRANSFORM：推进进度
  MiningCommitPart.java        // COMMIT：统一物品栏与破坏包
  MiningRenderPart.java        // RENDER：消费只读状态
  MiningCapabilityImpl.java    // 对外能力适配
```

每个 Part 都能单独测试：ZealotBreakPart 可以用假的 `MiningCapability` 输入各个 Enum 状态；
MiningCommitPart 可以验证 `PRESERVE_FOR_CRYSTAL`、`BREAK_NOW` 和同优先级冲突，而不需要启动完整的水晶模块。

## 4. 生命周期、线程与持久化

- `ModuleManager.initModules()` 仍负责创建模块和 Setting；随后 `ModuleOrchestrator.buildRegistry()` 收集
  Part/Node；Addon 在 `setupAddons()` 中完成注册，最后才允许配置恢复。
- `onEnable` 先建立模块上下文和计划，再执行 Part 的启用回调；`onDisable` 先停止新节点，再清理外部状态。
- 所有 Minecraft 事件节点默认主线程；异步任务只能发布不可变结果，在下一个主线程 `OBSERVE` 阶段交换。
- Part 的持久化 key 为 `modules.<moduleId>.parts.<partId>`，旧模块的 `saveCustomState` 在兼容期继续支持。
- `RotationManager.INSTANCE` 仍是可变静态字段；能力实现每次提交/解析都重新读取，不缓存 Manager 实例。
- GPU 和 UI Part 只能在现有渲染线程、`UiScene`、`Render2DScheduler`/`Render3DScheduler` 生命周期内提交。

## 5. 迁移方案

### 阶段 A：兼容层

新增 `ModuleOrchestrator`、`ModulePart`、`@EventNode`、`ModuleContext` 和 trace，但保留旧 `@EventHandler`。
旧监听器被包装成 `legacy.<moduleId>.<method>` 节点，`priority` 映射到同一 phase 的排序。这样可逐模块迁移。

### 阶段 B：基础能力与高冲突域

先迁移 rotation、movement、hotbar、attack、timer 等最容易互相覆盖的域；为每个域定义能力接口、唯一提交点、
tie-breaker 和测试。禁止新代码直接写这些 Manager 的共享可变状态。

### 阶段 C：复杂模块拆分

把 ElytraCombat、ElytraFly、CrystalAura 等已有策略/行为类改为 Part；保留一个顶层 Module 作为配置与公开
身份。每次拆分只移动一个行为簇，并确保 disable/reset/load 的状态测试覆盖。

### 阶段 D：收紧旧 API

开发环境对新增裸 `@EventHandler` 给出诊断；核心模块迁移完成后，Module 默认禁止直接订阅 EventBus。Manager
和平台桥接保留显式白名单。最后删除 `EventPriority` 对模块间顺序的依赖，只保留兼容层。

## 6. 可观测性与测试要求

开发模式提供 `/epsilon debug orchestration` 或等价 GUI，至少展示：

- 每个事件的拓扑顺序和依赖边；
- 当前启用模块/Part、计划版本和线程；
- 被取消事件的节点与原因；
- 能力提交、胜者、被拒绝意图和冲突；
- 环路、缺失引用、重复 ID 和非法跨线程访问。

最低测试集：

1. 拓扑排序、稳定同层排序、环路和缺失依赖诊断；
2. 启停期间计划原子替换，事件派发不出现半启用节点；
3. 能力裁决的相同优先级冲突、过期意图和禁用提供者；
4. Part 的反向清理、配置 round-trip 和旧 `customState` 兼容；
5. Fabric 与 NeoForge 编译，且不让加载器类型进入 `common/`。

## 7. 需要审阅的取舍

1. `@EventNode` 的依赖引用是否允许通配模块依赖，还是强制引用完整节点 ID，以提高重构安全性？
2. 能力裁决器应由核心 Manager 提供固定实现，还是允许 Addon 注册策略并声明更高层级？
3. 事件取消后是否允许显式 `afterCancellation = true` 的清理节点运行？
4. trace 默认只在开发环境启用，还是保留低成本的 ring buffer 供用户提交日志？
5. 是否接受每个事件一次拓扑计划查找的开销，还是需要按事件类型生成静态 dispatch table？

## 8. 与现有约束的关系

本设计继续遵守 `AGENTS.md`：模块/Addon/Manager 的生命周期顺序不变；EventBus 仍按精确运行时 class 分发；
`Cancellable` 仍使用 `cancel()`/`isCancelled()`；Mixin、渲染线程、RotationManager、配置目录和 Fabric/NeoForge
分层约束均不变。实现阶段若改变公共 API、配置 schema 或注册流程，应同步更新对应开发文档和迁移说明。
