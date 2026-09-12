package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 鞘翅 Grim 拉回加速，移植自 SlimefunHelper 的 {@code ElytraGrimAccelerate}。
 * <p>
 * 原理：滑翔期间丢弃本 tick 的真实移动包并把本地坐标回滚到 tick 起点，客户端坐标因此停留在服务端
 * 最后收到的位置上，而速度不受影响；随后发一个不可能的伪造移动包（Y 轴抬高 2.5~7.5 格，或把 X/Z
 * 写成世界边界外的坐标），强迫 Grim 判定预测失败并执行拉回（setback）。Grim 的拉回传送包会把客户端
 * 速度一并回传，并且在拉回被确认之前挂起预测检查；因为本地坐标已经回滚，拉回目标与客户端当前位置
 * 一致，玩家不会被拖走，只拿到速度，于是可以逐轮累积速度，直到达到本模块的速度上限。
 * <p>
 * 该功能依赖服务端安装 Grim，本地世界或没有 Grim 的服务器不会有任何加速效果；速度上限用于避免
 * 服务端直接判定超速拉回或踢出。
 */
public class ElytraGrimAccelerate extends Module {

    public static final ElytraGrimAccelerate INSTANCE = new ElytraGrimAccelerate();

    /**
     * 触发拉回的方式。
     */
    public enum SetBackTrigger {
        /**
         * 抬高 Y 轴坐标，改动最小。
         */
        Simulation,
        /**
         * 把 X/Z 写到世界边界外，强制产生拉回，风险更高。
         */
        CrashPackets
    }

    /**
     * 伪造包使用的 Y 轴偏移，按 tick 轮换以避开固定的位置漂移。
     */
    private static final double[] SIMULATION_Y_OFFSETS = {2.5D, 5.0D, 7.5D};

    /**
     * Grim 会把超过 3.0E7 的坐标夹回边界，用更大的坐标可以稳定触发拉回。
     */
    private static final double BORDER_COORDINATE = 3.9999999E7D;

    /**
     * 旧版本下服务端速度包会放大本地速度的水平阈值。
     */
    private static final double LEGACY_VELOCITY_THRESHOLD = 3.8D;

    /**
     * 判断真实位置包是否被视为发生位移的最小平方距离阈值。
     */
    private static final double POSITION_EPSILON = 1.0E-7D;

    private final EnumSetting<SetBackTrigger> mode = enumSetting("Mode", SetBackTrigger.Simulation);
    private final DoubleSetting maxAccelerateVelocity = doubleSetting("Max Accelerate Velocity", 6.0D, 0.0D, 100.0D, 0.5D);
    private final IntSetting setbackWait = intSetting("Setback Wait", 20, 1, 100, 1);
    private final BoolSetting fixOldVersionVelocity = boolSetting("Fix Old Version Velocity", false);
    private final BoolSetting fixKickFromLag = boolSetting("Fix Kick From Lag", true);

    private double tickStartX;
    private double tickStartY;
    private double tickStartZ;

    private double lastSentX;
    private double lastSentY;
    private double lastSentZ;
    private boolean hasSentPosition;

    /**
     * 服务端视角下最后一次真实位移，用于判断速度是否已经超过上限。
     */
    private Vec3 lastKnownMovementSpeed = Vec3.ZERO;

    private boolean currentTryWorking;

    private ServerboundMovePlayerPacket pendingSetBackPacket;

    /**
     * 上一次发出伪造包的 tick；收到客户端对拉回的确认后会重置，允许立即开始下一轮。
     */
    private volatile int lastSetBackPacketTick;

    private ElytraGrimAccelerate() {
        super("Elytra Grim Acc", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        pendingSetBackPacket = null;
        currentTryWorking = false;
        lastSetBackPacketTick = 0;
        lastKnownMovementSpeed = Vec3.ZERO;
        hasSentPosition = false;
        if (!nullCheck()) {
            hasSentPosition = true;
            lastSentX = mc.player.getX();
            lastSentY = mc.player.getY();
            lastSentZ = mc.player.getZ();
        }
    }

    @Override
    protected void onDisable() {
        pendingSetBackPacket = null;
        currentTryWorking = false;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        tickStartX = mc.player.getX();
        tickStartY = mc.player.getY();
        tickStartZ = mc.player.getZ();

        // 旧版本速度异常时不再接管移动，否则会把服务端已经接受的速度继续放大
        boolean legacyVelocity = fixOldVersionVelocity.getValue() && isLegacyVelocity(lastKnownMovementSpeed);
        currentTryWorking = isEnabled()
                && !legacyVelocity
                && mc.player.isFallFlying()
                && !mc.player.onGround()
                && !isFireworkBoosted();
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onSendPosition(SendPositionEvent event) {
        if (nullCheck()) return;

        if (!currentTryWorking || !isSpeedAllowed()) {
            trackSentPosition(event.getX(), event.getY(), event.getZ());
            return;
        }

        // 回滚到 tick 起点：本地坐标停在服务端最后收到的位置上，拉回才不会把玩家拖走
        double moveX = mc.player.getX() - tickStartX;
        double moveZ = mc.player.getZ() - tickStartZ;
        mc.player.setPos(tickStartX, tickStartY, tickStartZ);
        event.cancel();

        if (pendingSetBackPacket != null) {
            // 上一轮的伪造包尚未发出，丢弃它，避免同一 tick 连发两个包
            pendingSetBackPacket = null;
            return;
        }
        pendingSetBackPacket = createSetBackPacket(moveX, moveZ);
    }

    @EventHandler
    private void onAfterSendPosition(AfterSendPositionEvent event) {
        ServerboundMovePlayerPacket packet = pendingSetBackPacket;
        pendingSetBackPacket = null;

        if (packet == null || nullCheck()) return;

        // 绕过 Epsilon 的发送事件，避免伪造包被其他模块记录或延迟
        NetworkUtils.sendPacketNoEvent(packet);
        lastSetBackPacketTick = mc.player.tickCount;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundAcceptTeleportationPacket) {
            // 客户端已确认服务端拉回，预测检查重新可用，可以开始下一轮
            lastSetBackPacketTick = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            // 服务端传送会直接改写客户端坐标，下一个位置包的位移不能当作真实速度
            hasSentPosition = false;
        }
    }

    /**
     * 速度上限用于避免服务端已经接受的速度被继续放大成超速拉回或踢出。
     */
    private boolean isSpeedAllowed() {
        return lastKnownMovementSpeed.length() <= maxAccelerateVelocity.getValue();
    }

    /**
     * 记录真正发给服务端的位置，作为服务端视角下的移动速度。
     * <p>
     * 零位移的位置包（例如只更新朝向）不会覆盖速度，与上游保持一致。
     */
    private void trackSentPosition(double x, double y, double z) {
        if (!hasSentPosition) {
            hasSentPosition = true;
            lastSentX = x;
            lastSentY = y;
            lastSentZ = z;
            return;
        }

        Vec3 delta = new Vec3(x - lastSentX, y - lastSentY, z - lastSentZ);
        lastSentX = x;
        lastSentY = y;
        lastSentZ = z;
        if (delta.lengthSqr() > POSITION_EPSILON) {
            lastKnownMovementSpeed = delta;
        }
    }

    /**
     * 构造用于触发拉回的伪造位置包，坐标取回滚之后的本地位置。
     */
    private ServerboundMovePlayerPacket createSetBackPacket(double moveX, double moveZ) {
        if (fixKickFromLag.getValue()) {
            // 区块未加载时服务端会忽略移动，发送伪造包只会换来踢出
            if (isFacingUnloadedChunk(moveX, moveZ)) return null;
            // 已经在等待上一次拉回结果时不再继续发，避免刷包
            if (mc.player.tickCount < lastSetBackPacketTick + setbackWait.getValue()) return null;
        }

        double y = mc.player.getY() + SIMULATION_Y_OFFSETS[mc.player.tickCount % SIMULATION_Y_OFFSETS.length];
        float yRot = mc.player.getYRot();
        float xRot = mc.player.getXRot();
        boolean onGround = mc.player.onGround();
        boolean horizontalCollision = mc.player.horizontalCollision;

        return switch (mode.getValue()) {
            case Simulation -> new ServerboundMovePlayerPacket.PosRot(
                    mc.player.getX(), y, mc.player.getZ(), yRot, xRot, onGround, horizontalCollision);
            case CrashPackets -> new ServerboundMovePlayerPacket.PosRot(
                    BORDER_COORDINATE, y, BORDER_COORDINATE, yRot, xRot, onGround, horizontalCollision);
        };
    }

    /**
     * 判断前进方向上相邻的区块是否已经加载，对应上游 AntiChunkLag 的卡区块保护。
     */
    private boolean isFacingUnloadedChunk(double moveX, double moveZ) {
        double directionX = moveX;
        double directionZ = moveZ;
        if (Math.abs(directionX) < 1.0E-3D && Math.abs(directionZ) < 1.0E-3D) {
            Vec3 velocity = mc.player.getDeltaMovement();
            directionX = velocity.x;
            directionZ = velocity.z;
        }

        int signX = (int) Math.signum(directionX);
        int signZ = (int) Math.signum(directionZ);
        if (signX == 0 && signZ == 0) return false;

        int chunkX = SectionPos.blockToSectionCoord(mc.player.getX());
        int chunkZ = SectionPos.blockToSectionCoord(mc.player.getZ());
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                if (!mc.level.hasChunk(chunkX + x * signX, chunkZ + z * signZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 上游在玩家被烟花推进时不介入：烟花产生的位移是合法的，交给其他模块控制。
     * <p>
     * 26.2 的 {@code FireworkRocketEntity#isAttachedToEntity} 不可见，这里用贴身检测代替。
     */
    private boolean isFireworkBoosted() {
        AABB box = mc.player.getBoundingBox().inflate(1.0D);
        for (FireworkRocketEntity rocket : mc.level.getEntitiesOfClass(FireworkRocketEntity.class, box)) {
            if (rocket.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 旧版本（服务端速度包放大本地速度）下会出现异常的水平速度，此时不应该继续加速。
     */
    private boolean isLegacyVelocity(Vec3 velocity) {
        return Math.abs(velocity.x) >= LEGACY_VELOCITY_THRESHOLD || Math.abs(velocity.z) >= LEGACY_VELOCITY_THRESHOLD;
    }

}
