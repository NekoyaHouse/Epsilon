package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class StraightFollowerNavigator implements FollowerNavigator {

    @Override
    public FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config) {
        return new FollowerPath(targetPos, List.of(player.position(), targetPos));
    }

}
