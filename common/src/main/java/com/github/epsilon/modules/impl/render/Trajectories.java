package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.entity.ProjectileSimulator;
import com.github.epsilon.utils.player.EnchantmentUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {

    public static final Trajectories INSTANCE = new Trajectories();

    private Trajectories() {
        super("Trajectories", Category.RENDER);
    }

    private final SettingGroup generalGroup = settingGroup("General");
    private final SettingGroup renderGroup = settingGroup("Render");

    private final RegistryListSetting<Item> items = addSetting(new RegistryListSetting<>("Items", defaultItems(), RegistryListSetting.Type.ITEM, ProjectileSimulator::supports, () -> true)).group(generalGroup);
    private final BoolSetting otherPlayers = boolSetting("Other Players", true).group(generalGroup);
    private final BoolSetting firedProjectiles = boolSetting("Fired Projectiles", false).group(generalGroup);
    private final BoolSetting ignoreWitherSkulls = boolSetting("Ignore Wither Skulls", true, firedProjectiles::getValue).group(generalGroup);
    private final IntSetting simulationSteps = intSetting("Simulation Steps", 500, 0, 5000, 25).group(generalGroup);
    private final IntSetting ignoreFirstTicks = intSetting("Ignore First Ticks", 3, 0, 20, 1).group(renderGroup);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 150, 0, 245)).group(renderGroup);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 2.0, 0.5, 5.0, 0.25).group(renderGroup);
    private final BoolSetting impactFill = boolSetting("Impact Fill", true).group(renderGroup);
    private final BoolSetting impactOutline = boolSetting("Impact Outline", true).group(renderGroup);
    private final ColorSetting impactFillColor = colorSetting("Impact Fill Color", new Color(255, 150, 0, 45), impactFill::getValue).group(renderGroup);
    private final ColorSetting impactOutlineColor = colorSetting("Impact Outline Color", new Color(255, 180, 55, 245), impactOutline::getValue).group(renderGroup);
    private final BoolSetting positionBoxes = boolSetting("Render Position Boxes", false).group(renderGroup);
    private final DoubleSetting positionBoxSize = doubleSetting("Position Box Size", 0.02, 0.01, 0.1, 0.005, positionBoxes::getValue).group(renderGroup);
    private final ColorSetting positionFillColor = colorSetting("Position Fill Color", new Color(255, 150, 0, 28), positionBoxes::getValue).group(renderGroup);
    private final ColorSetting positionOutlineColor = colorSetting("Position Outline Color", new Color(255, 180, 55, 180), positionBoxes::getValue).group(renderGroup);

    private static final double MULTISHOT_OFFSET = Math.toRadians(10.0);

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        float partialTick = mc.level.tickRateManager().isFrozen() ? 1.0F : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Player player : mc.level.players()) {
            if (!otherPlayers.getValue() && player != mc.player) continue;

            ItemStack stack = player.getMainHandItem();
            if (!items.contains(stack.getItem())) {
                stack = player.getOffhandItem();
                if (!items.contains(stack.getItem())) continue;
            }

            if (stack.getItem() instanceof CrossbowItem) {
                ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                if (charged == null || charged.isEmpty()) continue;

                boolean firework = charged.contains(Items.FIREWORK_ROCKET);
                int piercing = firework ? 0 : EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PIERCING);
                renderCrossbowPath(player, partialTick, firework, piercing, 0.0);

                if (EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.MULTISHOT) > 0) {
                    renderCrossbowPath(player, partialTick, firework, piercing, MULTISHOT_OFFSET);
                    renderCrossbowPath(player, partialTick, firework, piercing, -MULTISHOT_OFFSET);
                }
                continue;
            }

            ProjectileSimulator simulator = new ProjectileSimulator(mc.level);
            if (!simulator.configureHeld(player, stack, 0.0, partialTick, 0)) continue;
            renderPath(calculate(simulator, player == mc.player ? ignoreFirstTicks.getValue() : 0, null));
        }

        if (firedProjectiles.getValue()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Projectile projectile)) continue;
                if (ignoreWitherSkulls.getValue() && projectile instanceof WitherSkull) continue;
                if (projectile instanceof ThrownTrident && projectile.noPhysics) continue;
                ProjectileSimulator simulator = new ProjectileSimulator(mc.level);
                if (!simulator.configureFired(projectile)) continue;
                renderPath(calculate(simulator, 0, projectile.getPosition(partialTick)));
            }
        }
    }

    private void renderCrossbowPath(Player player, float partialTick, boolean firework, int piercing, double angle) {
        ProjectileSimulator simulator = new ProjectileSimulator(mc.level);
        simulator.configureCrossbow(player, firework, angle, partialTick, piercing);
        renderPath(calculate(simulator, player == mc.player ? ignoreFirstTicks.getValue() : 0, null));
    }

    private Path calculate(ProjectileSimulator simulator, int ignoredTicks, Vec3 renderedStart) {
        List<Vec3> points = new ArrayList<>();
        points.add(renderedStart != null ? renderedStart : simulator.position());
        BlockHitResult blockHit = null;
        List<Entity> entityHits = new ArrayList<>();
        int limit = simulationSteps.getValue() == 0 ? Integer.MAX_VALUE : simulationSteps.getValue();

        for (int i = 0; i < limit; i++) {
            ProjectileSimulator.Step step = simulator.tick();
            points.add(step.position());
            if (step.hit() instanceof BlockHitResult hit) blockHit = hit;
            else if (step.hit() instanceof EntityHitResult hit && !entityHits.contains(hit.getEntity())) {
                entityHits.add(hit.getEntity());
            }
            if (step.stop()) break;
        }

        int start = Mth.clamp(points.size() - 2, 0, ignoredTicks);
        return new Path(points, start, blockHit, entityHits);
    }

    private void renderPath(Path path) {
        Render3DScheduler scheduler = Render3DScheduler.INSTANCE;
        float width = lineWidth.getValue().floatValue();

        for (int i = path.start + 1; i < path.points.size(); i++) {
            Vec3 from = path.points.get(i - 1);
            Vec3 to = path.points.get(i);
            scheduler.addLine(from, to, lineColor.getValue(), width);
            if (positionBoxes.getValue()) {
                double size = positionBoxSize.getValue();
                AABB box = new AABB(to.x - size, to.y - size, to.z - size, to.x + size, to.y + size, to.z + size);
                scheduler.addFilledBox(box, positionFillColor.getValue());
                scheduler.addOutlineBox(box, positionOutlineColor.getValue(), Math.min(width, 1.5F));
            }
        }

        if (path.blockHit != null) {
            BlockHitResult hit = path.blockHit;
            Vec3 point = hit.getLocation();
            Direction direction = hit.getDirection();
            double radius = 0.25;
            double depth = 0.002;
            AABB box;

            if (direction.getAxis() == Direction.Axis.Y) {
                box = new AABB(point.x - radius, point.y - depth, point.z - radius, point.x + radius, point.y + depth, point.z + radius);
            } else if (direction.getAxis() == Direction.Axis.Z) {
                box = new AABB(point.x - radius, point.y - radius, point.z - depth, point.x + radius, point.y + radius, point.z + depth);
            } else {
                box = new AABB(point.x - depth, point.y - radius, point.z - radius, point.x + depth, point.y + radius, point.z + radius);
            }

            if (impactFill.getValue()) {
                scheduler.addFilledSide(box, impactFillColor.getValue().getRGB(), direction);
            }
            if (impactOutline.getValue()) {
                scheduler.addSideOutline(box, impactOutlineColor.getValue().getRGB(), width, direction);
            }
        }

        for (Entity entity : path.entityHits) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
            AABB box = entity.getBoundingBox().move(offset);
            if (impactFill.getValue()) scheduler.addFilledBox(box, impactFillColor.getValue());
            if (impactOutline.getValue()) scheduler.addOutlineBox(box, impactOutlineColor.getValue(), width);
        }
    }

    private static List<Item> defaultItems() {
        return List.of(
                Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.FISHING_ROD,
                Items.SNOWBALL, Items.EGG, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE,
                Items.SPLASH_POTION, Items.LINGERING_POTION, Items.WIND_CHARGE
        );
    }

    private record Path(List<Vec3> points, int start, BlockHitResult blockHit, List<Entity> entityHits) {
    }

}
