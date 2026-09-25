# Module Orchestrator 与模块开发规范

## 1. 文档目的

本文定义 Epsilon 新模块架构的开发规范。

模块的核心模型是一个声明式节点图：

- Module 声明自己拥有的节点；
- 节点声明事件类型、阶段、优先级和依赖；
- ModulePart 用于拆分复杂模块；
- ModuleOrchestrator 收集节点并建立调度计划；
- 事件执行按照阶段和拓扑依赖进行；
- 模块之间通过公开 contract、Capability 和不可变数据协作。

现有 `Module`、配置格式、模块 ID、Addon 生命周期和 `EventBus` 继续保留。迁移期间旧模块可以通过 Legacy Adapter 接入 Orchestrator。

本文同时区分两类内容：

- 当前已经实现并可直接使用的 API；
- 设计中要求后续补齐的能力。

## 2. 架构原则

### 2.1 Module 是声明容器

Module 继续负责：

- 模块名称和分类；
- 键位和启用状态；
- Setting 和 SettingGroup；
- 配置保存与恢复；
- ModulePart 的装配；
- 当前模块的节点声明；
- 模块公开 contract。

Module 不应承担所有业务实现。复杂逻辑应该拆分为多个 Part，但所有 Part 仍属于同一个父 Module。

```java
public final class ExampleModule extends Module {
    public static final ExampleModule INSTANCE = new ExampleModule();

    private ExampleModule() {
        super("Example", Category.COMBAT);

        setDispatchMode(ModuleDispatchMode.MANAGED);

        part(new ObservePart());
        part(new DecidePart());
        part(new CommitPart());
        part(new RenderPart());
    }
}
```

Part 不继承 `Module`，因此：

- 不会出现在模块列表中；
- 不会拥有独立键位；
- 不会拥有独立的配置顶层；
- 不会被 `ModuleManager` 单独注册；
- 不会单独出现在 Addon 模块列表中。

### 2.2 Module 是唯一生命周期边界

父 Module 负责：

- 启用和禁用；
- Setting 注册；
- 配置生命周期；
- Part 装配；
- 节点注册；
- 外部状态清理。

Part 不能创建第二个 Module，也不能绕过父 Module 的 SettingHost。

模块禁用时，必须保证：

1. 模块节点不再执行；
2. pending rotation 被清理；
3. 物品栏切换被恢复；
4. 计时器和缓存被清理；
5. 临时渲染状态被清理；
6. worker、异步任务和等待信号被停止或失效；
7. Part 的清理顺序与依赖顺序一致。

## 3. 节点模型

### 3.1 NodeKey

`NodeKey` 是模块内部节点的稳定身份。

```java
public final class ExampleNodes {
    public static final NodeKey OBSERVE =
            NodeKey.of("observe.snapshot");

    public static final NodeKey DECIDE =
            NodeKey.of("decide.plan");

    public static final NodeKey COMMIT =
            NodeKey.of("commit.action");

    private ExampleNodes() {
    }
}
```

NodeKey 规则：

- 必须非空；
- 必须是稳定标识；
- 推荐使用点号分隔的层级名称；
- 不使用方法名作为唯一业务身份；
- 不在业务代码中拼接跨模块字符串；
- 不因为重命名私有方法而改变节点身份；
- 节点重命名属于调度协议变更，必须同步更新依赖和文档。

节点的全局身份由模块 ID 和 NodeKey 组成：

```text
<module-id>.<node-key>
```

例如：

```text
zealot_crystal+.observe.snapshot
zealot_crystal+.decide.plan
zealot_crystal+.commit.action
```

### 3.2 NodeRef

`NodeRef<E>` 同时携带：

- 节点身份；
- 事件类型；
- 所属 Module。

```java
public interface NodeRef<E> {
    NodeKey key();

    Class<E> eventType();

    ModuleId owner();
}
```

业务代码应该保存 `NodeRef<E>`，而不是保存字符串节点 ID。

```java
private NodeRef<PlayerTickEvent.Pre> observeNode;
```

这样可以让依赖关系具备：

- 明确的事件类型；
- 方法引用的泛型检查；
- IDE 重命名支持；
- 注册阶段缺失依赖诊断；
- 跨模块 contract 引用能力。

### 3.3 NodeBuilder

节点通过 Builder 声明：

```java
node(PlayerTickEvent.Pre.class, ExampleNodes.OBSERVE)
        .phase(Phase.OBSERVE)
        .handler(this::observe);
```

可用属性包括：

```java
NodeBuilder<E> phase(Phase phase);

NodeBuilder<E> after(NodeRef<?> dependency);

NodeBuilder<E> before(NodeRef<?> dependency);

NodeBuilder<E> provides(Class<?> valueType);

NodeBuilder<E> priority(int priority);

NodeRef<E> handler(EventHandler<E> handler);
```

处理器可以使用带上下文的方法：

```java
private void observe(
        PlayerTickEvent.Pre event,
        ModuleContext context
) {
}
```

也可以使用只接收事件的方法：

```java
private void observe(PlayerTickEvent.Pre event) {
}
```

处理器必须满足：

- 事件参数类型与节点事件类型一致；
- 不在节点中保存跨帧的可变事件对象；
- 不依赖监听器注册顺序；
- 不通过字符串猜测其他模块节点；
- 不在错误阶段修改外部状态。

## 4. 阶段模型

节点执行阶段定义如下：

```text
OBSERVE < DECIDE < TRANSFORM < COMMIT < RENDER < CLEANUP
```

### 4.1 OBSERVE

`OBSERVE` 用于建立本帧事实快照。

允许：

- 读取 Minecraft 当前状态；
- 读取实体、方块、玩家和网络状态；
- 读取能力提供者的快照；
- 更新本模块的只读缓存；
- 发布不可变 Snapshot；
- 记录本帧 revision。

禁止：

- 发送攻击、放置或破坏包；
- 切换物品栏；
- 修改旋转管理器的最终状态；
- 直接执行攻击或放置；
- 写入渲染资源；
- 依赖后续节点已经执行。

示例：

```java
private void observe(PlayerTickEvent.Pre event) {
    Snapshot snapshot = captureSnapshot();

    latestSnapshot = snapshot;
}
```

Snapshot 必须描述同一时刻的事实。后续阶段不得重新读取并覆盖 Snapshot 中已经确定的事实。

### 4.2 DECIDE

`DECIDE` 读取 Snapshot，生成本帧意图或决策。

允许：

- 读取 OBSERVE 产生的不可变 Snapshot；
- 计算目标；
- 选择旋转目标；
- 选择放置或攻击方案；
- 生成不可变 Decision；
- 提交给能力裁决器的 Intent。

禁止：

- 发送网络包；
- 切换物品栏；
- 修改世界；
- 直接调用攻击、放置或破坏动作；
- 写入最终旋转；
- 写入 GPU 或渲染目标。

示例：

```java
private void decide(PlayerTickEvent.Pre event) {
    Snapshot snapshot = latestSnapshot;
    if (snapshot == null) return;

    Decision decision = evaluate(snapshot);
    pendingDecision = decision;
}
```

Decision 必须包含：

- 来源节点；
- 输入 Snapshot revision；
- 目标；
- 动作类型；
- 优先级；
- 过期时间；
- 必要的 tie-breaker 信息。

### 4.3 TRANSFORM

`TRANSFORM` 用于修改事件字段或生成经过裁决的中间结果。

例如：

- 修改移动事件；
- 修改旋转事件；
- 修改输入事件；
- 应用能力裁决后的最终值。

规则：

- 只能修改明确声明允许修改的事件字段；
- 一个字段应有唯一最终写入节点；
- 修改后的值必须被后续节点读取；
- 不得绕过能力裁决直接写入共享 Manager 状态；
- 事件取消状态必须按统一协议处理。

### 4.4 COMMIT

`COMMIT` 是唯一允许执行外部副作用的阶段。

允许：

- 发送网络包；
- 攻击实体；
- 放置方块；
- 开始或停止挖掘；
- 切换物品栏；
- 应用已经裁决的旋转；
- 更新模块的提交状态。

COMMIT 执行前必须重新验证：

1. Decision 的 Snapshot revision 仍然有效；
2. 目标仍然存在；
3. 模块仍然启用；
4. 世界和玩家状态仍然满足条件；
5. 旋转和距离仍然有效；
6. 请求没有被其他能力拒绝；
7. 动作尚未执行过。

COMMIT 必须保证一次性执行：

```java
if (decision == null) return;
if (decision.revision() != currentRevision) return;
if (decision.committed()) return;
if (!isStillValid(decision)) return;

decision.markCommitted();
performAction(decision);
```

实际实现中推荐使用不可变 Decision 加独立的提交 revision 或 pending 状态，避免在异步线程直接修改共享对象。

### 4.5 RENDER

`RENDER` 只用于渲染。

允许：

- 读取当前帧快照；
- 读取渲染预测结果；
- 向现有 Render3DScheduler 提交命令；
- 向 UiScene、UiRenderBatch 或 HUD 调度器提交命令。

禁止：

- 在渲染节点中执行攻击、放置或破坏；
- 修改模块调度计划；
- 创建非渲染线程 GPU 资源；
- 在渲染线程之外创建 renderer、render target、字体 atlas 或 shader。

### 4.6 CLEANUP

`CLEANUP` 用于：

- 清理模块离开世界后的状态；
- 清理 packet pending；
- 清理外部状态；
- 恢复键位、物品栏和 rotation pending；
- 释放不再使用的资源。

清理节点应该具有幂等性。重复执行不会产生额外副作用。

## 5. ModulePart

### 5.1 接口

```java
public interface ModulePart {
    default void declare(ModuleDeclaration declaration) {
    }

    default void onEnable(ModuleContext context) {
    }

    default void onDisable(ModuleContext context) {
    }

    default void reset(ModuleContext context) {
    }

    default void save(StateWriter writer) {
    }

    default void load(StateReader reader) {
    }
}
```

### 5.2 Part 的职责

一个 Part 应该只负责一个行为簇。

推荐拆分：

```text
ExampleModule.java
ExampleObservePart.java
ExampleDecisionPart.java
ExampleCommitPart.java
ExampleRenderPart.java
```

常见职责：

```text
ObservePart
  目标、世界、网络和玩家快照

DecisionPart
  目标选择、伤害计算、动作规划

CommitPart
  攻击、放置、破坏、物品栏和旋转提交

RenderPart
  世界覆盖层、预测框和 HUD

CleanupPart
  pending 状态、缓存和资源释放
```

Part 不应该：

- 直接持有另一个 Module 的 INSTANCE；
- 创建新的 SettingHost；
- 注册到 ModuleManager；
- 独立调用 EventBus；
- 直接修改父 Module 的私有状态；
- 在 DECIDE 阶段执行 COMMIT 动作。

### 5.3 Part 的注册

父 Module 在构造阶段装配 Part：

```java
private ExampleModule() {
    super("Example", Category.COMBAT);

    setDispatchMode(ModuleDispatchMode.MANAGED);

    part(new ObservePart());
    part(new DecisionPart());
    part(new CommitPart());
    part(new RenderPart());
}
```

每个 Part 的节点仍然属于父 Module：

```java
private final class ObservePart implements ModulePart {
    @Override
    public void declare(ModuleDeclaration declaration) {
        node(PlayerTickEvent.Pre.class, NodeKey.of("observe.snapshot"))
                .phase(Phase.OBSERVE)
                .handler(ExampleModule.this::observe);
    }
}
```

Part 不会形成独立的 ModuleId。

## 6. 调度计划

### 6.1 计划构建

`ModuleOrchestrator` 的计划构建流程：

```text
注册 Module
  -> 收集 Part
  -> 收集 Node
  -> 检查 NodeKey 唯一性
  -> 检查事件类型
  -> 检查依赖存在性
  -> 建立 before/after 边
  -> 应用阶段顺序
  -> 拓扑排序
  -> 应用 priority 和稳定注册序
  -> 发布不可变 DispatchPlan
```

计划按事件类型建立：

```java
DispatchPlan<PlayerTickEvent.Pre> tickPlan =
        orchestrator.plan(PlayerTickEvent.Pre.class);
```

计划必须是不可变的。事件执行期间不得修改正在使用的计划。

### 6.2 阶段和依赖

阶段顺序是隐式依赖：

```text
OBSERVE -> DECIDE -> TRANSFORM -> COMMIT -> RENDER -> CLEANUP
```

显式依赖用于表达同一阶段或跨模块的细粒度顺序：

```java
decide.after(observe);
commit.after(decide);
```

同一阶段没有显式依赖时，使用 priority 排序。

priority 只用于同一依赖层级的稳定排序，不能代替跨模块业务依赖。

### 6.3 环路和缺失依赖

以下情况必须产生诊断：

- 节点身份重复；
- 依赖节点不存在；
- 节点事件类型不一致；
- 节点依赖形成环路；
- 节点在错误线程注册；
- 节点声明的能力不存在；
- 同一模块同时使用独立 EventBus 和 Orchestrator。

开发环境应抛出包含完整节点路径的诊断异常：

```text
Dependency cycle:
  zealot.decide.plan
  -> mining.resolve
  -> zealot.decide.plan
```

生产环境应：

- 拒绝错误计划；
- 保留上一个有效计划；
- 禁用错误节点或相关模块；
- 记录模块、节点、依赖和事件类型；
- 不静默退回注册顺序。

## 7. 启停与线程

### 7.1 模块启用

模块启用时：

1. 模块状态切换为 enabled；
2. Orchestrator 纳入该模块节点；
3. 重新生成受影响事件的 DispatchPlan；
4. 执行 Module 和 Part 的启用逻辑；
5. 恢复正常事件调度。

### 7.2 模块禁用

模块禁用时：

1. 模块节点从有效计划中移除；
2. 当前正在执行的事件继续使用旧计划直到结束；
3. 事件结束后原子替换计划；
4. 执行 Part 的反向清理；
5. 恢复外部状态。

禁止在事件节点遍历过程中直接修改当前计划。

### 7.3 线程边界

默认节点运行在 Minecraft 主线程。

异步线程只能：

- 读取已发布的不可变输入；
- 执行纯计算；
- 生成不可变结果；
- 通过线程安全队列返回主线程。

异步线程不能：

- 直接读取或修改 Minecraft 世界；
- 修改玩家；
- 发送网络包；
- 修改 RotationManager；
- 修改模块 Setting；
- 创建渲染资源；
- 直接执行 COMMIT。

主线程在 OBSERVE 阶段交换异步结果，并检查结果 revision 是否过期。

## 8. ModuleContext 与能力

`ModuleContext` 是节点和能力之间的窄接口。

```java
public interface ModuleContext {
    <T> Optional<T> capability(Class<T> type);

    <T> Optional<T> capability(CapabilityKey<T> key);

    <T> void publish(T value);

    <T> List<T> values(Class<T> type);

    FrameTrace trace();
}
```

能力用于表达跨模块协议，不用于暴露实现细节。

模块之间不应该直接依赖：

- 另一个模块的 INSTANCE；
- 另一个模块的私有 Setting；
- 另一个模块的静态字段；
- 另一个模块的实现类；
- 另一个模块的 EventPriority；
- 另一个模块的注册顺序。

推荐使用：

```java
public static final CapabilityKey<MiningCapability> MINING =
        CapabilityKey.of(MiningCapability.class);
```

消费者读取能力：

```java
MiningCapability mining = context
        .capability(MINING)
        .orElse(null);

if (mining == null) {
    return;
}
```

能力提供者禁用时，消费者应该得到 `Optional.empty()`。能力缺失属于运行时状态；节点 ID 拼写错误属于注册错误，两者必须区分。

## 9. Mining 协议

PacketMine 和 ZealotCrystalPlus 使用以下协议类型：

```java
public enum MiningKnowledge {
    NONE,
    ACTIVE_PROGRESS,
    INSTANT_READY,
    COMPLETED
}
```

```java
public enum MiningIntent {
    PREPARE_FOR_CRYSTAL,
    PRESERVE_FOR_CRYSTAL,
    BREAK_NOW
}
```

```java
public record MiningSnapshot(
        BlockPos position,
        MiningKnowledge knowledge,
        int progressPercent,
        long revision,
        String sourceId
) {
}
```

```java
public record MiningIntentRequest(
        BlockPos position,
        MiningIntent intent,
        int priority,
        String sourceId,
        long revision
) {
}
```

```java
public interface MiningCapability {
    Optional<MiningSnapshot> snapshot(BlockPos position);

    MiningDecision request(MiningIntentRequest request);
}
```

PacketMine 负责：

```text
OBSERVE
  发布 MiningSnapshot

DECIDE
  解析挖掘意图

COMMIT
  在裁决通过并且 revision 有效时发送挖掘包
```

ZealotCrystalPlus 负责：

```text
OBSERVE
  读取目标、世界和 MiningSnapshot

DECIDE
  生成放置、破坏和旋转决策

COMMIT
  重新验证决策并执行一次水晶动作
```

## 10. ZealotCrystalPlus 示例

ZealotCrystalPlus 应拆成以下 Part：

```text
ZealotCrystalPlus.java
  Module、Setting、状态和 Part 装配

ZealotObservePart
  目标快照、网络包观察、爆炸样本

ZealotDecisionPart
  目标解析、伤害计算、旋转和动作方案

ZealotCommitPart
  水晶放置、破坏、物品栏和旋转提交

ZealotRenderPart
  3D 预测框、2D 伤害文本和 HUD

ZealotCleanupPart
  pending 状态、缓存、worker 和渲染状态清理
```

节点关系：

```text
observe.snapshot
    -> decide.plan
    -> commit.action
```

节点声明示例：

```java
private final class ObservePart implements ModulePart {
    @Override
    public void declare(ModuleDeclaration declaration) {
        node(PlayerTickEvent.Pre.class, NodeKey.of("observe.snapshot"))
                .phase(Phase.OBSERVE)
                .handler(ZealotCrystalPlus.this::observeSnapshot);

        node(PacketEvent.Receive.class, NodeKey.of("observe.packet"))
                .phase(Phase.OBSERVE)
                .handler(ZealotCrystalPlus.this::observePacket);
    }
}
```

```java
private final class DecisionPart implements ModulePart {
    @Override
    public void declare(ModuleDeclaration declaration) {
        node(PlayerTickEvent.Pre.class, NodeKey.of("decide.plan"))
                .phase(Phase.DECIDE)
                .after(nodeRef("observe.snapshot"))
                .handler(ZealotCrystalPlus.this::decidePlan);
    }
}
```

```java
private final class CommitPart implements ModulePart {
    @Override
    public void declare(ModuleDeclaration declaration) {
        node(PlayerTickEvent.Pre.class, NodeKey.of("commit.action"))
                .phase(Phase.COMMIT)
                .after(nodeRef("decide.plan"))
                .handler(ZealotCrystalPlus.this::commitAction);
    }
}
```

OBSERVE 只生成事实：

```java
private void observeSnapshot(PlayerTickEvent.Pre event) {
    updateTimeouts();
    updateExplosionSamples();
    captureSnapshotIfNeeded();
}
```

DECIDE 只生成决策：

```java
private void decidePlan(PlayerTickEvent.Pre event) {
    if (isEatingPaused()) return;

    AsyncResult result = asyncResult;
    PlaceInfo place = getValidPlaceInfo(cachedRotationPlaceInfo, false);

    target = resolveCurrentTarget(result, place);

    if (preRotation.getValue()) {
        prepareRotation(
                getValidBreakPlan(cachedRotationBreakPlan),
                place
        );
    }
}
```

COMMIT 执行动作：

```java
private void commitAction(PlayerTickEvent.Pre event) {
    if (isEatingPaused()) return;

    BreakPlan breakPlan = getActionBreakPlan();

    boolean acted = breakPlan != null
            && breakMode.getValue() != BreakMode.Off
            && breakTimer.passedMillise(breakDelay.getValue())
            && breakDirect(breakPlan);

    PlaceInfo place = getActionPlaceInfo();

    if (!acted
            && place != null
            && placeMode.getValue() != PlaceMode.Off
            && placeTimer.passedMillise(placeDelay.getValue())
            && shouldAttemptPlace(place)) {
        placeDirect(place, false);
    }
}
```

实际提交前仍必须验证：

- 目标是否存在；
- 目标位置是否变化；
- 旋转是否满足要求；
- 玩家是否仍然持有正确物品；
- 目标是否在范围内；
- 决策是否过期；
- 同一动作是否已提交；
- 模块是否仍然启用。

## 11. Legacy Adapter

旧模块仍然可以使用：

```java
@EventHandler
private void onTick(PlayerTickEvent.Pre event) {
}
```

Legacy Adapter 会：

1. 扫描旧 `@EventHandler`；
2. 生成稳定的 legacy 节点；
3. 将 priority 映射为节点排序；
4. 按事件类型选择兼容阶段；
5. 把处理器放入 Orchestrator 的 DispatchPlan；
6. 禁止该模块再次独立订阅 EventBus。

Legacy Adapter 节点身份格式：

```text
legacy.<module>.<method>.<event-type>
```

迁移完成后的模块应设置：

```java
setDispatchMode(ModuleDispatchMode.MANAGED);
```

Managed Module 不得：

- 调用 `EventBus.INSTANCE.subscribe(this)`；
- 保留同一业务的 `@EventHandler`；
- 在构造器中创建独立 ConsumerListener；
- 同时注册 Legacy Adapter 和 Managed 节点；
- 使用注册顺序表达跨模块依赖。

EventBus 仍可以用于：

- 核心 Manager；
- 平台桥接；
- 尚未迁移的非 Module 对象；
- 明确列入白名单的兼容层。

## 12. 可观测性与诊断

调度器和开发诊断应记录：

- DispatchPlan 版本；
- 事件类型；
- 当前线程；
- Snapshot revision；
- 每个节点的开始和结束时间；
- 节点执行状态；
- 节点跳过原因；
- 取消事件的节点；
- 缺失依赖；
- 环路依赖；
- 重复节点；
- 能力提供者状态；
- COMMIT 成功、拒绝或过期原因；
- 重复提交被拒绝的原因。

推荐的 trace 内容：

```text
event=PlayerTickEvent.Pre
planVersion=42
snapshotRevision=381

OBSERVE zealot.observe.snapshot
  status=SUCCESS
  duration=0.42ms

DECIDE zealot.decide.plan
  status=SUCCESS
  decisionRevision=381
  duration=0.18ms

COMMIT zealot.commit.action
  status=REJECTED
  reason=rotation_not_ready
```

trace 不应改变业务执行结果。

## 13. 常见错误

### 在 DECIDE 中直接攻击

错误：

```java
private void decide(PlayerTickEvent.Pre event) {
    mc.gameMode.attack(mc.player, target);
}
```

正确做法：

```java
private void decide(PlayerTickEvent.Pre event) {
    pendingDecision = new AttackDecision(
            target,
            snapshotRevision
    );
}
```

然后在 COMMIT 中验证并执行。

### 依赖字符串拼接

错误：

```java
node.after("packetmine.observe");
```

正确做法：

```java
node.after(packetMineContract.observe());
```

跨 Addon 或配置边界可以使用字符串，但必须在注册边界立即解析为 NodeRef 或 contract。

### 在模块之间读取静态字段

错误：

```java
if (PacketMine.INSTANCE.completed) {
}
```

正确做法：

```java
MiningCapability mining = context
        .capability(PacketMineContract.MINING)
        .orElse(null);
```

### 同时使用两套事件入口

错误：

```java
setDispatchMode(ModuleDispatchMode.MANAGED);
EventBus.INSTANCE.subscribe(this);
```

错误：

```java
node(PlayerTickEvent.Pre.class, key).handler(this::onTick);

@EventHandler
private void onTick(PlayerTickEvent.Pre event) {
}
```

一个业务行为只能进入一个有效 DispatchPlan。

### 在异步线程修改 Minecraft 状态

错误：

```java
workerThread = new Thread(() -> {
    mc.player.setYRot(yaw);
});
```

异步线程只能计算不可变结果。旋转和其他 Minecraft 状态必须回到主线程的 COMMIT 阶段应用。

### COMMIT 重复执行

错误：

```java
if (decision != null) {
    sendPacket(decision);
}
```

必须记录提交状态或 revision，确保同一个 Decision 只执行一次。

## 14. 迁移步骤

迁移单个旧模块时：

1. 保留模块名称、类别、ID 和配置；
2. 查找所有旧事件监听器；
3. 为每个行为簇定义 NodeKey；
4. 为每个节点指定事件类型；
5. 将读取逻辑移动到 OBSERVE；
6. 将目标和方案计算移动到 DECIDE；
7. 将网络包、攻击、放置和物品栏动作移动到 COMMIT；
8. 将渲染逻辑移动到 RENDER；
9. 将外部状态恢复移动到 CLEANUP；
10. 删除旧 `@EventHandler`；
11. 设置 `ModuleDispatchMode.MANAGED`；
12. 在 `ModuleManager` 中注册 Module 的 declaration；
13. 检查是否仍然存在 EventBus 双重订阅；
14. 为跨模块关系增加 contract 或 Capability；
15. 运行 Fabric 和 NeoForge 编译。

## 15. 迁移验收清单

每个迁移后的 Module 必须检查：

- [ ] Module ID 没有改变；
- [ ] Setting 仍由父 Module 注册；
- [ ] Part 没有出现在模块列表中；
- [ ] 所有行为节点都有唯一 NodeKey；
- [ ] 每个 NodeRef 的事件类型正确；
- [ ] OBSERVE 不执行副作用；
- [ ] DECIDE 只读取快照并产生决策；
- [ ] COMMIT 重新验证输入；
- [ ] COMMIT 对重复提交具备幂等保护；
- [ ] 依赖图没有缺失节点；
- [ ] 依赖图没有环；
- [ ] 阶段顺序符合设计；
- [ ] 没有独立 EventBus 双重订阅；
- [ ] 取消事件语义保持一致；
- [ ] 模块禁用后外部状态恢复；
- [ ] pending rotation 被清理；
- [ ] 物品栏切换被恢复；
- [ ] worker 和异步结果不会在禁用后继续提交；
- [ ] 渲染资源使用正确线程；
- [ ] 配置可以 round-trip；
- [ ] Fabric 编译通过；
- [ ] NeoForge 编译通过；
- [ ] `git diff --check` 通过。

## 16. 测试要求

最低测试范围：

- 默认 `node(key)` 生成 `PlayerTickEvent.Pre` 节点；
- 显式事件类型和 handler 参数类型匹配；
- 同模块节点拓扑排序；
- 跨模块 NodeRef 依赖排序；
- 重复 NodeKey 诊断；
- 缺失依赖诊断；
- 环路依赖诊断；
- 阶段顺序稳定；
- 同阶段 priority 顺序稳定；
- 模块启停不会产生半更新计划；
- Legacy Adapter 与 Managed 节点不会双重执行；
- Cancellable 事件在取消后停止后续普通节点；
- COMMIT 拒绝过期 revision；
- COMMIT 拒绝重复提交；
- Part 按反向依赖顺序清理；
- PacketMine 禁用时能力返回 empty；
- MiningSnapshot revision 过期时不执行动作；
- ZealotCrystalPlus 配置可以保存和恢复；
- Fabric 和 NeoForge 编译通过。

## 17. 当前实现边界

当前已经实现：

- `NodeKey`、`NodeRef`、`NodeBuilder`、`Phase`、`ModuleDeclaration`
- `ModulePart`、`ModuleContext`、`FrameTrace`
- `ModuleOrchestrator`、`DispatchPlan`、`ModuleDispatchMode`
- Legacy Adapter（`ModuleManager` 会把 `COMBAT` 分类下的 `LEGACY` 模块交给它兜底转换）
- `CapabilityKey` 与 `CapabilityRegistry`
- Mining 协议数据类型（已声明，尚未接入裁决器）
- `COMBAT` 分类全部模块的 Managed 节点迁移：语义化 `NodeKey`、与副作用一致的 `Phase`、
  显式跨阶段依赖，且不再存在 `@EventHandler` 双重订阅
- `ZealotCrystalPlus` 的多文件拆分：父 Module 持有 Setting 与运行时状态，其余按 Part 与工具类拆到
  `modules/impl/combat/zealot_crystal_plus/`

参考实现：

```text
modules/impl/combat/AimBot.java                     单文件 + 多 Part
modules/impl/combat/zealot_crystal_plus/
  ZealotCrystalPlus.java     Module、Setting、运行时状态、worker 线程
  ZealotPartBase.java        Part 共同基类（解析节点、访问父 Module）
  ZealotSettingsPart.java    节点图声明（键、阶段、依赖）
  ZealotObservePart.java     OBSERVE：快照、计时、水晶与位置采集
  ZealotDecidePart.java      DECIDE：候选评分、旋转请求、有效性校验
  ZealotCommitPart.java      COMMIT：放置、破坏、换手、包观察触发的动作
  ZealotRenderPart.java      RENDER：预测框与伤害文本
  ZealotSnapshot.java        不可变数据契约
  ZealotDamage.java          爆炸伤害估算与抗性方块缓存
  ZealotMath.java            纯数学、几何与缓动
  ZealotState.java           运行时状态（跨线程字段 volatile）
  ZealotRenderState.java     渲染状态机
  ZealotOptions.java         配置枚举
```

后续仍需要继续完善：

- 完整的 ModuleContext 帧内 Snapshot 和 Intent 存储（`publish` / `values` 目前仍是空实现）；
- 能力请求的统一裁决器，以及 `MiningCapability` 的实际提供者与消费者；
- pending 启停队列；
- COMMIT revision 和幂等提交基础设施；
- Part 独立持久化实现（当前 Part 只共享父 Module 的配置）；
- 完整 trace 和调试界面；
- 跨模块 contract 的标准注册流程（当前 combat 模块之间仍有 `INSTANCE` 直读）；
- 更严格的线程访问检查。

新增功能必须遵循本文的阶段和依赖模型，不应通过注册顺序、静态字段或隐式 EventPriority 恢复旧式跨模块耦合。
