package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.timer.TimerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Xray extends Module {

    public static final Xray INSTANCE = new Xray();

    private Xray() {
        super("Xray", Category.RENDER);
    }

    private enum Plugin {
        Old,
        New;
    }

    private final EnumSetting<Plugin> plugin = enumSetting("Plugin", Plugin.New);
    public final BoolSetting wallHack = boolSetting("WallHack", false, _ -> mc.levelRenderer.allChanged());
    private final BoolSetting brutForce = boolSetting("Ore Deobf", false);
    private final BoolSetting fast = boolSetting("Fast", false, brutForce::getValue);
    private final IntSetting delay = intSetting("Delay", 25, 1, 100, 1, brutForce::getValue);
    private final IntSetting radius = intSetting("Radius", 5, 1, 64, 1, brutForce::getValue);
    private final IntSetting up = intSetting("Up", 5, 1, 32, 1, brutForce::getValue);
    private final IntSetting down = intSetting("Down", 5, 1, 32, 1, brutForce::getValue);
    private final BoolSetting netherite = boolSetting("Netherite", false);
    private final BoolSetting diamond = boolSetting("Diamond ", false);
    private final BoolSetting gold = boolSetting("Gold", false);
    private final BoolSetting iron = boolSetting("Iron", false);
    private final BoolSetting emerald = boolSetting("Emerald", false);
    private final BoolSetting redstone = boolSetting("Redstone", false);
    private final BoolSetting lapis = boolSetting("Lapis", false);
    private final BoolSetting coal = boolSetting("Coal", false);
    private final BoolSetting quartz = boolSetting("Quartz", false);
    private final BoolSetting water = boolSetting("Water", false);
    private final BoolSetting lava = boolSetting("Lava", false);

    private final TimerUtils delayTimer = new TimerUtils();
    private final ArrayList<BlockPos> ores = new ArrayList<>();
    private final ArrayList<BlockPos> toCheck = new ArrayList<>();
    private final ArrayList<BlockMemory> checked = new ArrayList<>();
    private BlockPos displayBlock;
    private int done, all;
    private AABB area = new AABB(BlockPos.ZERO);

    @Override
    public void onEnable() {
        ores.clear();
        toCheck.clear();
        checked.clear();
        toCheck.addAll(getBlocks());
        all = toCheck.size();
        done = 0;
        mc.smartCull = false;
        mc.levelRenderer.allChanged();
        area = getArea();
    }

    @Override
    public void onDisable() {
        mc.levelRenderer.allChanged();
        mc.smartCull = true;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (plugin.is(Plugin.New)) {
            checked.forEach(blockMemory -> {
                if (blockMemory.isDelayed() && !ores.contains(blockMemory.blockPos))
                    ores.add(blockMemory.blockPos);
            });
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket pac) {
            if (isCheckableOre(pac.getBlockState().getBlock()) && !ores.contains(pac.getPos())) {
                ores.add(pac.getPos());
            }
        }
    }

    @EventHandler
    private void onMove(MoveEvent event) {
        if (brutForce.getValue()) {
            if (all != done) {
                event.setZ(0);
                event.setX(0);
                event.cancel();
                if (mc.player.tickCount % 8 == 0 && mc.player.isMoving()) {
                    log("Don't move while deobf!");
                }
            } else {
                AABB newArea = getArea();
                if (!newArea.intersects(area)) {
                    area = newArea;
                    toCheck.clear();
                    toCheck.addAll(getBlocks());
                    checked.clear();
                    all = toCheck.size();
                    done = 0;
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        PoseStack stack = event.getPoseStack();

        for (BlockPos pos : ores) {
            Block block = mc.level.getBlockState(pos).getBlock();
            if ((block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) && diamond.getValue()) {
                draw(stack, pos, 0, 255, 255);
            }
            if ((block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) && gold.getValue()) {
                draw(stack, pos, 255, 215, 0);
            }
            if (block == Blocks.NETHER_GOLD_ORE && gold.getValue()) {
                draw(stack, pos, 255, 215, 0);
            }
            if ((block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) && iron.getValue()) {
                draw(stack, pos, 213, 213, 213);
            }
            if ((block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) && emerald.getValue()) {
                draw(stack, pos, 0, 255, 77);
            }
            if ((block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) && redstone.getValue()) {
                draw(stack, pos, 255, 0, 0);
            }
            if (block == Blocks.COAL_ORE && coal.getValue()) {
                draw(stack, pos, 0, 0, 0);
            }
            if ((block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) && lapis.getValue()) {
                draw(stack, pos, 38, 97, 156);
            }
            if (block == Blocks.ANCIENT_DEBRIS && netherite.getValue()) {
                draw(stack, pos, 255, 255, 255);
            }
            if (block == Blocks.NETHER_QUARTZ_ORE && quartz.getValue()) {
                draw(stack, pos, 170, 170, 170);
            }
        }

        if (displayBlock != null && (done != all)) {
            draw(stack, displayBlock, 255, 0, 60);
        }

        if (brutForce.getValue()) {
            Render3DScheduler.INSTANCE.addOutlineBox(area, new Color(149, 149, 149, 100));
        }

        if (toCheck.isEmpty() || !brutForce.getValue()) return;

        if (mc.isSingleplayer()) {
            log("单人游戏你反你老冯呢");
            toggle();
            return;
        }

        if (mc.player.getMainHandItem().is(ItemTags.PICKAXES)) {
            if (mc.player.tickCount % 8 == 0) {
                log("别在主手放镐子");
                toggle();
            }
            return;
        }

        if (delayTimer.every(delay.getValue())) {
            BlockPos pos = toCheck.remove(toCheck.size() - 1 <= 1 ? 0 : ThreadLocalRandom.current().nextInt(0, toCheck.size() - 1));
            mc.gameMode.startDestroyBlock(displayBlock = pos, mc.player.getDirection());
            mc.gameMode.stopDestroyBlock();
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            checked.add(new BlockMemory(pos));
            ++done;
        }
    }

    private AABB getArea() {
        int radius_ = plugin.is(Plugin.New) ? Math.min(4, radius.getValue()) : radius.getValue();
        int down_ = plugin.is(Plugin.New) ? Math.min(3, down.getValue()) : down.getValue();
        int up_ = plugin.is(Plugin.New) ? Math.min(4, up.getValue()) : up.getValue();
        return new AABB(mc.player.getX() - radius_, mc.player.getY() - down_, mc.player.getZ() - radius_, mc.player.getX() + radius_, mc.player.getY() + up_, mc.player.getZ() + radius_);
    }

    private void draw(PoseStack stack, BlockPos pos, int r, int g, int b) {
        AABB box = new AABB(pos);
        Render3DScheduler.INSTANCE.addFilledBox(box, new Color(r, g, b, 100));
        Render3DScheduler.INSTANCE.addOutlineBox(box, new Color(r, g, b, 200));
    }

    public boolean isCheckableOre(Block block) {
        if (diamond.getValue() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (gold.getValue() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE))
            return true;
        if (iron.getValue() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (emerald.getValue() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (redstone.getValue() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE))
            return true;
        if (coal.getValue() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (netherite.getValue() && block == Blocks.ANCIENT_DEBRIS) return true;
        if (water.getValue() && block == Blocks.WATER) return true;
        if (lava.getValue() && block == Blocks.LAVA) return true;
        if (quartz.getValue() && block == Blocks.NETHER_QUARTZ_ORE) return true;
        if (lapis.getValue() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        return lapis.getValue() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE);
    }

    private ArrayList<BlockPos> getBlocks() {
        int radius_ = plugin.is(Plugin.New) ? Math.min(4, radius.getValue()) : radius.getValue();
        int down_ = plugin.is(Plugin.New) ? Math.min(3, down.getValue()) : down.getValue();
        int up_ = plugin.is(Plugin.New) ? Math.min(4, up.getValue()) : up.getValue();

        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = (int) (mc.player.getX() - radius_); x < mc.player.getX() + radius_; x++) {
            for (int y = (int) (mc.player.getY() - down_); y < mc.player.getY() + up_; y++) {
                for (int z = (int) (mc.player.getZ() - radius_); z < mc.player.getZ() + radius_; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(pos).isAir() || (fast.getValue() && plugin.is(Plugin.Old) && (x % 2 == 0 || y % 2 == 0 || z % 2 == 0))) {
                        continue;
                    }
                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    private void log(String message) {
        Managers.NOTIFICATION.info("Xray", message);
    }

    public static class BlockMemory {

        private final BlockPos blockPos;
        private long time = 0;

        public BlockMemory(BlockPos blockPos) {
            this.blockPos = blockPos;
        }

        private boolean isDelayed() {
            return this.time++ > 10;
        }

    }

}
