package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotOptions.RenderPredictMode;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.SnapshotData;
import com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotSnapshot.TargetSnapshot;
import com.github.epsilon.modules.orchestration.ModuleDeclaration;
import com.github.epsilon.modules.orchestration.ModulePart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.awt.*;
import java.util.Locale;

/**
 * RENDER 阶段：只读取快照与预测状态，并向渲染调度器提交命令。
 *
 * <p>本 Part 不执行攻击、放置或破坏，也不修改调度计划。3D 与 2D 属于不同事件类型，各自拥有独立的
 * DispatchPlan，因此两者之间没有显式依赖；它们的共享状态由 {@link ZealotRenderState} 承载。
 */
final class ZealotRenderPart extends ZealotPartBase implements ModulePart {

    ZealotRenderPart(ZealotCrystalPlus module) {
        super(module);
    }

    @Override
    public void declare(ModuleDeclaration declaration) {
        // 节点统一由 ZealotSettingsPart 声明，保证节点图集中可见。
    }

    /**
     * 世界层渲染：先画目标运动预测框，再画上一次动作落点的方块框。
     */
    void renderWorld(Render3DEvent event) {
        if (nullCheck()) return;

        renderTargetPredictions();

        ZealotRenderState render = module.renderState;
        if (!render.hasTrackedPosition()) return;

        Vec3 renderPos = render.interpolatedPos(module.movingLength.getValue());
        float renderScale = render.updateScale(module.fadeLength.getValue());
        if (renderScale <= 0.01f) return;

        double halfSize = 0.5 * renderScale;
        AABB box = new AABB(
                renderPos.x - halfSize, renderPos.y - halfSize, renderPos.z - halfSize,
                renderPos.x + halfSize, renderPos.y + halfSize, renderPos.z + halfSize
        );

        Color base = module.renderColor.getValue();
        Color filled = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (module.filledAlpha.getValue() * renderScale), 0, 255));
        Color outline = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.clamp((int) (module.outlineAlpha.getValue() * renderScale), 0, 255));

        if (module.filledAlpha.getValue() > 0) {
            Render3DScheduler.INSTANCE.addFilledBox(box, filled);
        }
        if (module.outlineAlpha.getValue() > 0) {
            Render3DScheduler.INSTANCE.addOutlineBox(box, outline.getRGB(), module.outlineWidth.getValue().floatValue());
        }

        render.markRendered(renderPos);
    }

    /**
     * 世界层文字：在动作落点上方显示本次动作的伤害/自伤数值。
     */
    void renderHud(Render2DEvent.Level event) {
        ZealotRenderState render = module.renderState;
        if (nullCheck() || !render.hasTrackedPosition()) return;
        if (!module.renderTargetDamage.getValue() && !module.renderSelfDamage.getValue()) return;

        Vec3 renderPos = render.interpolatedPos(module.movingLength.getValue());
        Vector2f screenPos = ZealotRenderState.projectToScreen(renderPos);
        if (screenPos == null) return;

        StringBuilder text = new StringBuilder();
        if (module.renderTargetDamage.getValue()) {
            text.append(String.format(Locale.ROOT, "%.1f", render.damage()));
        }
        if (module.renderSelfDamage.getValue()) {
            if (!text.isEmpty()) text.append('/');
            text.append(String.format(Locale.ROOT, "%.1f", render.selfDamage()));
        }
        if (text.isEmpty()) return;

        TextRenderer renderer = render.textRenderer();
        float scale = 1.0f;
        float width = renderer.getWidth(text.toString(), scale);
        float height = renderer.getHeight(scale);
        Color color = new Color(255, 255, 255, Math.clamp((int) (220 * render.scale()), 0, 255));
        renderer.addText(text.toString(), screenPos.x - width / 2.0f, screenPos.y - height / 2.0f, scale, color);
        renderer.drawAndClear();
    }

    /**
     * 绘制目标的位置预测框；Single 模式只画当前焦点目标。
     */
    private void renderTargetPredictions() {
        if (module.renderPredict.getValue() == RenderPredictMode.Off) return;

        SnapshotData snapshot = state().latestSnapshot;
        if (snapshot == null || snapshot.targets().isEmpty()) return;

        LivingEntity focus = state().target;
        if (focus == null && state().asyncResult.primaryTarget() != null) {
            focus = state().asyncResult.primaryTarget().entity();
        }

        Color outline = new Color(100, 255, 100, 150);
        Color filled = new Color(100, 255, 100, 28);

        for (TargetSnapshot targetInfo : snapshot.targets()) {
            if (module.renderPredict.getValue() == RenderPredictMode.Single && targetInfo.entity() != focus) {
                continue;
            }

            Render3DScheduler.INSTANCE.addFilledBox(targetInfo.box(), filled);
            Render3DScheduler.INSTANCE.addOutlineBox(targetInfo.box(), outline.getRGB(), 1.5f);
        }
    }
}
