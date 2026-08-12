package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.epsilon.Constants.mc;

public class ExtrapolationManager {

    public static final ExtrapolationManager INSTANCE = new ExtrapolationManager();

    private Map<AbstractClientPlayer, List<Vec3>> motions = new HashMap<>();

    private ExtrapolationManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onClientTick(ClientTickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.level.players().isEmpty()) return;

        Map<AbstractClientPlayer, List<Vec3>> newMotions = new HashMap<>();

        for (AbstractClientPlayer player : mc.level.players()) {
            Vec3 vec = player.position().subtract(player.xo, player.yo, player.zo);

            if (!motions.containsKey(player)) {
                List<Vec3> v = new ArrayList<>();
                v.add(vec);
                newMotions.put(player, v);
                continue;
            }

            List<Vec3> v = motions.get(player);
            v.addFirst(vec);

            if (v.size() > 20) {
                v.subList(20, v.size()).clear();
            }

            newMotions.put(player, v);
        }

        motions = newMotions;
    }

    public void extrapolateMap(Map<AbstractClientPlayer, AABB> old, EpicInterface<AbstractClientPlayer, Integer> extrapolation, EpicInterface<AbstractClientPlayer, Integer> smoothening) {
        old.clear();

        motions.forEach((player, m) -> {
            if (m == null) return;
            old.put(player, extrapolate(player, m, extrapolation.get(player), smoothening.get(player)));
        });
    }

    public AABB extrapolate(AbstractClientPlayer player, int extrapolation, int smoothening) {
        List<Vec3> m = motions.get(player);
        if (m == null) return null;
        return extrapolate(player, m, extrapolation, smoothening);
    }

    public AABB extrapolate(AbstractClientPlayer player, List<Vec3> m, int extrapolation, int smoothening) {
        Vec3 motion = getMotion(m, smoothening);

        double x = motion.x;
        double y = motion.y;
        double z = motion.z;

        double stepHeight = 0.6;

        AABB box = new AABB(player.getX() - 0.3, player.getY(), player.getZ() - 0.3, player.getX() + 0.3, player.getY() + (player.getBoundingBox().maxY - player.getBoundingBox().minY), player.getZ() + 0.3);
        boolean onGround = inside(player, box.move(0, -0.04, 0));

        for (int i = 0; i < extrapolation; i++) {
            List<VoxelShape> list = mc.level.getEntityCollisions(player, box.contract(x, y, z));
            Vec3 movement = new Vec3(x, y, z);
            Vec3 vec3d = movement.lengthSqr() == 0.0 ? movement : Entity.collideBoundingBox(player, movement, box, mc.level, list);

            boolean canStep = (onGround || (y < 0 && vec3d.y != y)) && (vec3d.x != x || vec3d.z != z);

            if (canStep) {
                Vec3 vec3d2 = Entity.collideBoundingBox(player, new Vec3(x, stepHeight, z), box, mc.level, list);
                Vec3 vec3d3 = Entity.collideBoundingBox(player, new Vec3(0.0, stepHeight, 0.0), box.contract(x, 0.0, z), mc.level, list);
                if (vec3d3.y < stepHeight) {
                    Vec3 vec3d4 = Entity.collideBoundingBox(player, new Vec3(movement.x, 0.0, movement.z), box.move(vec3d3), mc.level, list).add(vec3d3);
                    if (vec3d4.horizontalDistanceSqr() > vec3d2.horizontalDistanceSqr()) {
                        vec3d2 = vec3d4;
                    }
                }

                if (vec3d2.horizontalDistanceSqr() > vec3d.horizontalDistanceSqr()) {
                    Vec3 vec = vec3d2.add(Entity.collideBoundingBox(player, new Vec3(0.0, -vec3d2.y + movement.y, 0.0), box.move(vec3d2), mc.level, list));
                    box = box.move(vec);
                    onGround = true;
                    continue;
                }
            }

            box = box.move(vec3d);
            onGround = inside(player, box.move(0, -0.04, 0));

            if (onGround) y = 0;
            y = (y - 0.08) * 0.98;
        }

        return box;
    }

    private boolean inside(Player player, AABB box) {
        return mc.level.getBlockCollisions(player, box).iterator().hasNext();
    }

    private Vec3 getMotion(List<Vec3> vecs, int max) {
        Vec3 avg = new Vec3(0, (vecs.getFirst().y - 0.08) * 0.98, 0);

        int s = Math.min(vecs.size(), max);
        for (int i = 0; i < s; i++) {
            avg = avg.add(vecs.get(i).x, 0, vecs.get(i).z);
        }

        return avg.multiply(1 / (float) s, 1, 1 / (float) s);
    }

    @FunctionalInterface
    public interface EpicInterface<T, E> {
        E get(T t);
    }

}
