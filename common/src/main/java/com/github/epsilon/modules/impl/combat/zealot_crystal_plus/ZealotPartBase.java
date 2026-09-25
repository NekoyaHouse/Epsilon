package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.orchestration.NodeBuilder;
import com.github.epsilon.modules.orchestration.NodeKey;
import com.github.epsilon.modules.orchestration.NodeRef;
import net.minecraft.client.Minecraft;

/**
 * ZealotCrystalPlus 各 Part 的共同基类。
 *
 * <p>Part 不继承 {@code Module}，因此不会出现在模块列表、也没有独立键位与配置顶层；它们只通过父 Module
 * 声明节点并共享 {@link ZealotState}。Part 之间不互相持有引用，需要协作时统一走父 Module 中的公开状态。
 */
abstract class ZealotPartBase {

    protected final ZealotCrystalPlus module;

    protected ZealotPartBase(ZealotCrystalPlus module) {
        this.module = module;
    }

    protected final ZealotState state() {
        return module.state;
    }

    protected final Minecraft mc() {
        return module.minecraft();
    }

    /**
     * 世界内事件处理器的统一前置检查；仅依赖主菜单或资源系统的处理器不应调用它。
     */
    protected final boolean nullCheck() {
        return mc().player == null || mc().level == null;
    }

    /**
     * 在父 Module 之下声明节点；节点的事件类型必须与处理器参数一致。
     * <p>
     * {@code Module#node} 是 protected，跨包只能通过 {@link ZealotCrystalPlus#declareNode} 访问。
     */
    protected final <E> NodeBuilder<E> node(Class<E> eventType, NodeKey key) {
        return module.declareNode(eventType, key);
    }

    /**
     * 声明默认事件类型（{@code PlayerTickEvent.Pre}）的节点。
     */
    protected final NodeBuilder<PlayerTickEvent.Pre> node(NodeKey key) {
        return module.declareNode(PlayerTickEvent.Pre.class, key);
    }

    /**
     * 解析同一 Module 内已声明的节点，用于表达阶段之间的显式依赖。
     * <p>
     * 依赖在声明顺序之后解析，因此只能引用已经声明的节点；缺失属于注册错误，必须立即暴露。
     */
    protected final <E> NodeRef<E> nodeRef(NodeKey key) {
        for (NodeRef<?> node : module.declaration().nodes()) {
            if (node.key().equals(key)) {
                return castNode(node);
            }
        }
        throw new IllegalStateException("Missing Zealot node: " + module.moduleId().value() + "." + key.value());
    }

    @SuppressWarnings("unchecked")
    private static <E> NodeRef<E> castNode(NodeRef<?> node) {
        return (NodeRef<E>) node;
    }
}
