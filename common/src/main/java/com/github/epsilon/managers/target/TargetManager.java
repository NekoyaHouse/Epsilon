package com.github.epsilon.managers.target;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.combat.AntiBot;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.github.epsilon.Constants.mc;

public class TargetManager {

    public static final TargetManager INSTANCE = new TargetManager();

    private LivingEntity sharedTarget;

    private TargetManager() {
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
            if (candidates.remove(sharedTarget)) {
                candidates.addFirst(sharedTarget);
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
        if (isSameTeam(entity)) return false;

        double dist = RotationUtils.getEyeDistanceToEntity(entity);
        if (dist > request.range()) return false;

        if (request.fov() < 360.0f && !RotationUtils.isInFov(entity, request.fov())) return false;

        if (entity instanceof Player player) {
            if (FriendManager.INSTANCE.isFriend(player)) return false;
            if (!request.player()) return false;
            if (entity.isInvisible() && !request.invisible()) return false;
        } else if (entity instanceof Villager) {
            if (!request.villager()) return false;
        } else if (entity instanceof Monster) {
            if (!request.mob()) return false;
        } else if (entity instanceof Animal) {
            if (!request.animal()) return false;
        } else {
            MobCategory category = entity.getType().getCategory();
            switch (category) {
                case AMBIENT, WATER_AMBIENT -> {
                    if (!request.ambient()) return false;
                }
                case WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> {
                    if (!request.water()) return false;
                }
                default -> {
                    if (!request.others()) return false;
                }
            }
        }

        return request.extraFilter().test(entity);
    }

    public boolean isSameTeam(Entity entity) {
        EnumSetting<ClientSetting.Teams> teams = ClientSetting.INSTANCE.teams;
        if (teams.is(ClientSetting.Teams.None)) {
            return false;
        }
        if (entity instanceof Player) {
            if (teams.is(ClientSetting.Teams.Color)) {
                return entity.getTeamColor() == mc.player.getTeamColor();
            } else {
                return Objects.equals(getTeam(entity), getTeam(mc.player));
            }
        }
        return false;
    }

    private String getTeam(Entity entity) {
        PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(entity.getUUID());
        if (playerInfo == null) {
            return null;
        }
        if (playerInfo.getTeam() != null) {
            return playerInfo.getTeam().getName();
        }
        return null;
    }

}
