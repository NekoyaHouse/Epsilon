package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 普通 AStar 的线程化入口；碰撞采样仍由客户端线程完成，搜索在专用工作线程执行。
 */
public class AStarFollowerNavigator implements FollowerNavigator {

    private final HierarchicalAStarFollowerNavigator delegate =
            new HierarchicalAStarFollowerNavigator(false);

    @Override
    public FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config) {
        return this.delegate.getPath(player, target, targetPos, config);
    }

    public void setDataSize(int size) {
        this.delegate.setDataSize(size);
    }

    public void stop() {
        this.delegate.stop();
    }
}
