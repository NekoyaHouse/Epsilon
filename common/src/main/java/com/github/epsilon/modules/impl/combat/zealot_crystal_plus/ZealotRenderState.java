package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * 渲染预测框的状态机。
 *
 * <p>水晶位置变化时从上一个已渲染位置平滑移动到新位置；目标丢失时播放淡出。所有字段只在渲染线程
 * （即主线程的渲染阶段）读写，因此不需要同步。
 */
final class ZealotRenderState {

    private BlockPos blockPos;
    private Vec3 prevPos;
    private Vec3 currentPos;
    private Vec3 lastRenderedPos;
    private long moveStartTime;
    private long fadeStartTime;
    private float scale;
    private float damage;
    private float selfDamage;
    private boolean hasTarget;

    private final Supplier<TextRenderer> textRenderer = Suppliers.memoize(() -> TextRenderer.create(128 * 1024));

    /**
     * 记录一次成功动作的落点与伤害，用于绘制预测框与伤害文本。
     */
    void updateTarget(BlockPos pos, float damage, float selfDamage) {
        long now = System.currentTimeMillis();
        if (!pos.equals(blockPos)) {
            currentPos = Vec3.atCenterOf(pos);
            prevPos = lastRenderedPos != null ? lastRenderedPos : currentPos;
            moveStartTime = now;
            if (blockPos == null) {
                fadeStartTime = now;
            }
            blockPos = pos;
        }
        if (!hasTarget) {
            fadeStartTime = now;
        }
        hasTarget = true;
        this.damage = damage;
        this.selfDamage = selfDamage;
    }

    void deactivate() {
        if (hasTarget) {
            hasTarget = false;
            fadeStartTime = System.currentTimeMillis();
        }
    }

    void reset() {
        blockPos = null;
        prevPos = null;
        currentPos = null;
        lastRenderedPos = null;
        moveStartTime = 0L;
        fadeStartTime = 0L;
        scale = 0.0f;
        damage = 0.0f;
        selfDamage = 0.0f;
        hasTarget = false;
    }

    boolean hasTrackedPosition() {
        return prevPos != null && currentPos != null;
    }

    /**
     * 是否仍然持有目标；为 false 时表示正在播放淡出。
     */
    boolean hasTarget() {
        return hasTarget;
    }

    /**
     * 缓动后的插值位置，移动与淡入淡出共用。
     */
    Vec3 interpolatedPos(int movingLength) {
        float moveMultiplier = ZealotMath.easeOutQuart(ZealotMath.toDelta(moveStartTime, movingLength));
        return prevPos.add(currentPos.subtract(prevPos).scale(moveMultiplier));
    }

    /**
     * 更新淡入（有目标）/ 淡出（目标丢失）比例。
     */
    float updateScale(int fadeLength) {
        float fadeDelta = ZealotMath.toDelta(fadeStartTime, fadeLength);
        scale = hasTarget ? ZealotMath.easeOutCubic(fadeDelta) : 1.0f - ZealotMath.easeInCubic(fadeDelta);
        return scale;
    }

    void markRendered(Vec3 pos) {
        lastRenderedPos = pos;
    }

    float scale() {
        return scale;
    }

    float damage() {
        return damage;
    }

    float selfDamage() {
        return selfDamage;
    }

    TextRenderer textRenderer() {
        return textRenderer.get();
    }

    /**
     * 把世界坐标投影到当前 GUI 空间；在屏幕外或摄像机后方时返回 null。
     */
    static Vector2f projectToScreen(Vec3 pos) {
        Vector3f projected = WorldToScreen.calcWorld2Screen(pos);
        if (projected == null) return null;

        float centerX = projected.x;
        float centerY = projected.y;
        if (centerX < 0.0f || centerY < 0.0f
                || centerX > LuminRenderSystem.getScaledWidth()
                || centerY > LuminRenderSystem.getScaledHeight()) {
            return null;
        }
        return new Vector2f(centerX, centerY);
    }
}
