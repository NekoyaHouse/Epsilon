package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface FollowerNavigator {

    FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config);

}
