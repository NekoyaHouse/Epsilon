package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModulePart;
import com.github.epsilon.modules.orchestration.NodeKey;
import com.github.epsilon.modules.orchestration.NodeRef;
import com.github.epsilon.modules.orchestration.Phase;

/**
 * ZealotCrystalPlus 的节点声明入口。
 *
 * <p>把节点图集中在一个 Part 里，可以让节点身份、阶段和依赖关系一眼可见；具体行为分散在
 * {@link ZealotObservePart}、{@link ZealotDecidePart}、{@link ZealotCommitPart} 和
 * {@link ZealotRenderPart} 中。
 *
 * <p>依赖只描述跨阶段的语义约束：{@code decide.plan} 必须看到本帧的 {@code observe.snapshot}，
 * {@code commit.action} 必须执行本帧 {@code decide.plan} 的产出。渲染节点同属 RENDER 阶段，
 * 顺序由阶段内优先级决定，不需要额外依赖。
 */
final class ZealotSettingsPart extends ZealotPartBase implements ModulePart {

    static final NodeKey SNAPSHOT = NodeKey.of("observe.snapshot");
    static final NodeKey PACKET = NodeKey.of("observe.packet");
    static final NodeKey PLAN = NodeKey.of("decide.plan");
    static final NodeKey ACTION = NodeKey.of("commit.action");
    static final NodeKey RENDER_WORLD = NodeKey.of("render.world");
    static final NodeKey RENDER_HUD = NodeKey.of("render.hud");

    private final ZealotObservePart observe;
    private final ZealotDecidePart decide;
    private final ZealotCommitPart commit;
    private final ZealotRenderPart render;

    ZealotSettingsPart(ZealotCrystalPlus module) {
        super(module);
        this.observe = module.observePart;
        this.decide = module.decidePart;
        this.commit = module.commitPart;
        this.render = module.renderPart;
    }

    @Override
    public void declare(ModuleDeclaration declaration) {
        NodeRef<PlayerTickEvent.Pre> snapshot = node(PlayerTickEvent.Pre.class, SNAPSHOT)
                .phase(Phase.OBSERVE)
                .handler(observe::observeSnapshot);

        node(PacketEvent.Receive.class, PACKET)
                .phase(Phase.OBSERVE)
                .handler(observe::observePacket);

        // NodeRef 依赖不能跨事件类型解析，因此由 Part 自己保存本事件类型的节点引用。
        decide.bindPlanNode(node(PlayerTickEvent.Pre.class, PLAN)
                .phase(Phase.DECIDE)
                .after(snapshot)
                .handler(decide::decidePlan));

        node(PlayerTickEvent.Pre.class, ACTION)
                .phase(Phase.COMMIT)
                .after(decide.planNode())
                .handler(commit::commitAction);

        node(Render3DEvent.class, RENDER_WORLD)
                .phase(Phase.RENDER)
                .handler(render::renderWorld);

        node(Render2DEvent.Level.class, RENDER_HUD)
                .phase(Phase.RENDER)
                .handler(render::renderHud);
    }
}
