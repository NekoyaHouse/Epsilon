package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ChestAura extends Module {

    public static final ChestAura INSTANCE = new ChestAura();

    private ChestAura() {
        super("Chest Aura", Category.PLAYER);
    }

    private final BoolSetting chests = boolSetting("Chests", true);
    private final BoolSetting furnaces = boolSetting("Furnaces", false);
    private final BoolSetting blastFurnaces = boolSetting("Blast Furnaces", false);
    private final BoolSetting smokers = boolSetting("Smokers", false);
    private final BoolSetting brewingStands = boolSetting("Brewing Stands", false);
    private final BoolSetting swing = boolSetting("Swing", true);
    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true);
    private final BoolSetting requireStealer = boolSetting("Require Stealer", true);
    private final BoolSetting ignoreOtherOpened = boolSetting("Ignore Other Opened", false);
    private final DoubleSetting range = doubleSetting("Range", 4.5, 0.0, 7.0, 0.1);
    private final DoubleSetting throughWallRange = doubleSetting("Through Wall Range", 4.5, 0.0, 7.0, 0.1);
    private final DoubleSetting cancelRange = doubleSetting("Cancel Range", 0.0, 0.0, 20.0, 0.1);
    private final IntSetting delay = intSetting("Delay", 400, 0, 1000, 1);
    private final DoubleSetting turnSpeed = doubleSetting("Turn Speed", 180.0, 0.0, 180.0, 0.1);
    private final BoolSetting disableInHypixelLobby = boolSetting("Disable In Hypixel Lobby", false);

    private final TimerUtils timer = new TimerUtils();
    private final Set<BlockPos> clickedContainers = new HashSet<>();
    private final Set<BlockPos> playerClickedContainers = new HashSet<>();

    private ClientLevel trackedLevel;
    private boolean opening;
    private long openingStartedAt;

    @Override
    protected void onEnable() {
        opening = false;
        openingStartedAt = 0L;
        timer.reset();
        updateTrackedLevel();
    }

    @Override
    protected void onDisable() {
        opening = false;
        openingStartedAt = 0L;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        updateTrackedLevel();

        if (opening) {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?>) return;
            if (System.currentTimeMillis() - openingStartedAt < 1000L) return;
            opening = false;
            timer.reset();
        }

        if (shouldPause() || !timer.passedMillise(delay.getValue())) return;

        if (hasNearbyPlayer()) {
            if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
                mc.player.closeContainer();
            }
            return;
        }

        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) return;

        Target target = findTarget();
        if (target == null) return;

        Rot2f targetRotation = RotationUtils.calculate(mc.player.getEyePosition(), target.hit().getLocation());
        RotationManager.INSTANCE.setRotations(
                targetRotation,
                turnSpeed.getValue(),
                rotation -> hitsTarget(rotation, target),
                Priority.Lowest
        );

        BlockHitResult hit = raycastTarget(RotationManager.INSTANCE.getRotation(), target.pos(), target.throughWalls());
        if (hit == null || !hit.getBlockPos().equals(target.pos())) return;

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) return;

        playerClickedContainers.add(target.pos());
        clickedContainers.add(target.pos());
        opening = true;
        openingStartedAt = System.currentTimeMillis();
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.level == null) return;

        if (event.getPacket() instanceof ServerboundUseItemOnPacket packet) {
            BlockPos pos = packet.getHitResult().getBlockPos();
            if (isSelectedContainer(mc.level.getBlockEntity(pos))) {
                clickedContainers.add(pos.immutable());
                playerClickedContainers.add(pos.immutable());
            }
        } else if (event.getPacket() instanceof ServerboundContainerClosePacket) {
            finishOpening();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.level == null) return;

        if (event.getPacket() instanceof ClientboundBlockEventPacket packet) {
            markContainerOpened(packet.getPos());
        } else if (event.getPacket() instanceof ClientboundSoundPacket packet) {
            markContainerOpened(BlockPos.containing(packet.getX(), packet.getY(), packet.getZ()));
        } else if (event.getPacket() instanceof ClientboundContainerClosePacket) {
            finishOpening();
        }
    }

    private boolean shouldPause() {
        if (Scaffold.INSTANCE.isEnabled()) return true;
        if (KillAura.INSTANCE.isEnabled() && KillAura.INSTANCE.target != null) return true;
        if (pauseOnEat.getValue() && PlayerUtils.isEating()) return true;
        if (requireStealer.getValue() && !Stealer.INSTANCE.isEnabled()) return true;
        return disableInHypixelLobby.getValue() && isInLobby();
    }

    private boolean hasNearbyPlayer() {
        double distance = cancelRange.getValue();
        if (distance <= 0.0) return false;

        double distanceSq = distance * distance;
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.distanceToSqr(mc.player) <= distanceSq) {
                return true;
            }
        }
        return false;
    }

    private Target findTarget() {
        int radius = (int) Math.ceil(range.getValue());
        BlockPos origin = mc.player.blockPosition();
        Vec3 eyes = mc.player.getEyePosition();

        return BlockPos.betweenClosedStream(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))
                .map(BlockPos::immutable)
                .filter(pos -> isSelectedContainer(mc.level.getBlockEntity(pos)))
                .filter(pos -> !isBlockedChest(pos))
                .filter(pos -> !wasAlreadyOpened(pos))
                .map(pos -> createTarget(pos, eyes))
                .filter(Objects::nonNull)
                .min(Comparator.comparingDouble(target -> eyes.distanceToSqr(Vec3.atCenterOf(target.pos()))))
                .orElse(null);
    }

    private Target createTarget(BlockPos pos, Vec3 eyes) {
        double distanceSq = eyes.distanceToSqr(Vec3.atCenterOf(pos));
        double maxRange = range.getValue();
        if (distanceSq > maxRange * maxRange) return null;

        BlockHitResult visibleHit = mc.level.clip(new ClipContext(
                eyes,
                Vec3.atCenterOf(pos),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (visibleHit.getType() == HitResult.Type.BLOCK && visibleHit.getBlockPos().equals(pos)) {
            return new Target(pos, visibleHit, false);
        }

        double wallRange = throughWallRange.getValue();
        if (distanceSq > wallRange * wallRange) return null;

        Rot2f rotation = RotationUtils.calculate(eyes, Vec3.atCenterOf(pos));
        BlockHitResult throughWallHit = raycastTarget(rotation, pos, true);
        return throughWallHit == null ? null : new Target(pos, throughWallHit, true);
    }

    private boolean hitsTarget(Rot2f rotation, Target target) {
        BlockHitResult hit = raycastTarget(rotation, target.pos(), target.throughWalls());
        return hit != null && hit.getBlockPos().equals(target.pos());
    }

    private BlockHitResult raycastTarget(Rot2f rotation, BlockPos pos, boolean throughWalls) {
        Vec3 start = mc.player.getEyePosition(1.0F);
        Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 end = start.add(direction.scale(range.getValue()));

        if (!throughWalls) {
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    start,
                    end,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
            return hit.getType() == HitResult.Type.BLOCK ? hit : null;
        }

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        return shape.isEmpty() ? null : shape.clip(start, end, pos);
    }

    private boolean isSelectedContainer(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity) return chests.getValue();
        if (blockEntity instanceof BlastFurnaceBlockEntity) return blastFurnaces.getValue();
        if (blockEntity instanceof SmokerBlockEntity) return smokers.getValue();
        if (blockEntity instanceof FurnaceBlockEntity) return furnaces.getValue();
        return blockEntity instanceof BrewingStandBlockEntity && brewingStands.getValue();
    }

    private boolean isBlockedChest(BlockPos pos) {
        BlockPos above = pos.above();
        return mc.level.getBlockEntity(pos) instanceof ChestBlockEntity && mc.level.getBlockState(above).isRedstoneConductor(mc.level, above);
    }

    private boolean wasAlreadyOpened(BlockPos pos) {
        return ignoreOtherOpened.getValue()
                ? playerClickedContainers.contains(pos)
                : clickedContainers.contains(pos);
    }

    private void markContainerOpened(BlockPos pos) {
        if (isSelectedContainer(mc.level.getBlockEntity(pos))) {
            clickedContainers.add(pos.immutable());
        }
    }

    private void finishOpening() {
        if (!opening) return;
        opening = false;
        openingStartedAt = 0L;
        timer.reset();
    }

    private void updateTrackedLevel() {
        if (trackedLevel == mc.level) return;
        trackedLevel = mc.level;
        clickedContainers.clear();
        playerClickedContainers.clear();
        opening = false;
        openingStartedAt = 0L;
    }

    private boolean isInLobby() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getName().getString().contains("\u00a7e\u00a7lCLICK TO PLAY")) {
                return true;
            }
        }
        return mc.player.getInventory().getItem(8).is(Items.NETHER_STAR) && mc.player.getInventory().getItem(0).is(Items.COMPASS);
    }

    private record Target(BlockPos pos, BlockHitResult hit, boolean throughWalls) {
    }

}
