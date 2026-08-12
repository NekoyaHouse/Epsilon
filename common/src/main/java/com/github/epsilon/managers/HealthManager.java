package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.epsilon.Constants.mc;

public class HealthManager {

    public static final HealthManager INSTANCE = new HealthManager();

    private final Map<String, Integer> scoreboardHealth = new ConcurrentHashMap<>();

    private HealthManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public float getHealth(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            String name = livingEntity.getName().getString();
            Integer scoreHealth = scoreboardHealth.get(name);
            return scoreHealth == null ? livingEntity.getHealth() + livingEntity.getAbsorptionAmount() : scoreHealth;
        }
        return 0f;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundSetScorePacket packet) {
            String objectiveName = packet.objectiveName();
            if (isHealthObjective(objectiveName)) {
                scoreboardHealth.put(packet.owner(), packet.score());
            }
        }
    }

    private boolean isHealthObjective(String objectiveName) {
        return "belowHealth".equalsIgnoreCase(objectiveName) || "health".equalsIgnoreCase(objectiveName);
    }

}
