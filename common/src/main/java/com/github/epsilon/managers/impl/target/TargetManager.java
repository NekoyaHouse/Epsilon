package com.github.epsilon.managers.impl.target;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.combat.AntiBot;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class TargetManager {

    private LivingEntity sharedTarget;

    public TargetManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            sharedTarget = null;
            return;
        }

        if (!isSharedTargetAlive()) {
            sharedTarget = null;
        }
    }

    public LivingEntity acquirePrimary(TargetRequest request) {
        List<LivingEntity> targets = acquireTargets(request);
        if (targets.isEmpty()) {
            return null;
        } else {
            return targets.getFirst();
        }
    }

    public List<LivingEntity> acquireTargets(TargetRequest request) {
        if (mc.player == null || mc.level == null) return List.of();

        List<LivingEntity> candidates = collectTargets(request);
        if (candidates.isEmpty()) return candidates;

        if (!isSharedTargetAlive()) {
            sharedTarget = null;
        }

        if (sharedTarget != null && isValidTarget(sharedTarget, request)) {
            // Keep the same target as the highest priority when it is still valid for this module.
            if (candidates.remove(sharedTarget)) {
                candidates.add(0, sharedTarget);
            }
        } else if (sharedTarget == null) {
            sharedTarget = candidates.getFirst();
        }

        int maxTargets = Math.max(1, request.maxTargets());
        if (candidates.size() > maxTargets) {
            return List.copyOf(candidates.subList(0, maxTargets));
        }
        return List.copyOf(candidates);
    }

    public LivingEntity getSharedTarget() {
        if (!isSharedTargetAlive()) {
            sharedTarget = null;
        }
        return sharedTarget;
    }

    private List<LivingEntity> collectTargets(TargetRequest request) {
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand) continue;
            if (living == mc.player) continue;
            if (!isValidTarget(living, request)) continue;
            targets.add(living);
        }

        targets.sort(Comparator.comparingDouble(RotationUtils::getEyeDistanceToEntity));
        return targets;
    }

    private boolean isSharedTargetAlive() {
        if (sharedTarget == null) return false;
        if (sharedTarget == mc.player) return false;
        if (!sharedTarget.isAlive() || sharedTarget.isDeadOrDying()) return false;
        if (sharedTarget.level() != mc.level) return false;
        return !AntiBot.INSTANCE.isBot(sharedTarget);
    }

    private boolean isValidTarget(LivingEntity entity, TargetRequest request) {
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;
        if (AntiBot.INSTANCE.isBot(entity)) return false;

        double dist = RotationUtils.getEyeDistanceToEntity(entity);
        if (dist > request.range()) return false;

        if (request.fov() < 360.0f && !RotationUtils.isInFov(entity, request.fov())) return false;

        switch (entity) {
            case Player player -> {
                if (Managers.FRIEND.isFriend(player)) return false;
                if (!request.player()) return false;
                if (entity.isInvisible() && !request.invisible()) return false;
            }
            case Villager _ -> {
                if (!request.villager()) return false;
            }
            case Animal _ -> {
                if (!request.animal()) return false;
            }
            case Monster _ -> {
                if (!request.mob()) return false;
            }
            default -> {
            }
        }

        return request.extraFilter().test(entity);
    }

}
