package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.hole.HoleUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 自动挖掘：在无政府服务器的水晶战斗中，拆掉敌人用来阻挡爆炸射线的坑、卡位方块与脚下支撑。
 * <p>
 * 本模块只负责挑选目标方块，实际破坏交给 {@link PacketMine} 执行，因此依赖其处于启用状态。
 */
public class AutoMine extends Module {

    public static final AutoMine INSTANCE = new AutoMine();

    private AutoMine() {
        super("Auto Mine", Category.COMBAT);
    }

    public enum TargetOrder {
        Distance,
        Health,
        Hole
    }

    public enum HeadMode {
        Inside,
        Above,
        Both
    }

    public enum FeetMode {
        Below,
        Inside,
        Both
    }

    public enum FeetLogic {
        /**
         * 优先挖掘当前目标脚下的方块。
         */
        First,
        /**
         * 所有候选方块按距离排序。
         */
        Normal,
        /**
         * 最后挖掘当前目标脚下的方块。
         */
        Last
    }

    private enum TargetCategory {
        Feet,
        Head,
        Box
    }

    private record Candidate(BlockPos pos, TargetCategory category) {
    }

    /**
     * 坑（盒子）的水平方向：正前后左右四格。
     */
    private static final int[][] SIDE_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * 坑（盒子）的角落方向：四个斜角，仅在开启角落选项时使用。
     */
    private static final int[][] CORNER_OFFSETS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * 用于表示“立即可挖”的计时起点：远早于当前时间，使首次选择不受切换延迟限制。
     */
    private static final long FIRST_PICK_DELAY = 917813L;

    private static final long PAUSE_NOTIFY_INTERVAL = 5000L;

    /**
     * 服务端拒绝破坏（例如领地保护、反作弊）时客户端方块仍会保留，
     * 这里对这类方块设置短暂冷却，避免对同一位置无限重试。
     */
    private static final long FAILED_BLOCK_COOLDOWN = 3000L;

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgTargets = settingGroup("Targets");
    private final SettingGroup sgFeet = settingGroup("Feet Settings");
    private final SettingGroup sgBox = settingGroup("Box Settings");

    private final DoubleSetting range = doubleSetting("Range", 4.5, 1.0, 8.0, 0.1).group(sgGeneral);
    private final EnumSetting<TargetOrder> order = enumSetting("Order", TargetOrder.Distance).group(sgGeneral);
    private final BoolSetting invisible = boolSetting("Invisible", false).group(sgGeneral);
    private final BoolSetting friends = boolSetting("Friends", false).group(sgGeneral);
    private final IntSetting delay = intSetting("Switch Delay", 100, 0, 1000, 10).group(sgGeneral);
    private final BoolSetting autoPacketMine = boolSetting("Auto Packet Mine", true).group(sgGeneral);

    private final BoolSetting mineBox = boolSetting("Box", true).group(sgTargets);
    private final BoolSetting mineHead = boolSetting("Head", false).group(sgTargets);
    private final BoolSetting mineFeet = boolSetting("Feet", true).group(sgTargets);
    private final EnumSetting<HeadMode> headMode = enumSetting("Head Mode", HeadMode.Both, mineHead::getValue).group(sgTargets);

    private final EnumSetting<FeetMode> feetMode = enumSetting("Feet Mode", FeetMode.Below, mineFeet::getValue).group(sgFeet);
    private final EnumSetting<FeetLogic> feetLogic = enumSetting("Feet Logic", FeetLogic.First, mineFeet::getValue).group(sgFeet);
    private final BoolSetting feetOnGround = boolSetting("Only On Ground", false, mineFeet::getValue).group(sgFeet);

    private final BoolSetting boxCorners = boolSetting("Box Corners", false, mineBox::getValue).group(sgBox);
    private final BoolSetting boxHeadLevel = boolSetting("Box Head Level", false, mineBox::getValue).group(sgBox);

    private final TimerUtils switchTimer = new TimerUtils();
    private final TimerUtils pauseNotifyTimer = new TimerUtils();

    private final Map<BlockPos, Long> failedUntil = new HashMap<>();

    /**
     * 最近一次交给 Packet Mine 的方块，用于判断它是否被服务端拒绝破坏。
     */
    private BlockPos pendingPos;

    private boolean autoEnabledPacketMine;

    @Override
    protected void onEnable() {
        switchTimer.setMs(FIRST_PICK_DELAY);
        pauseNotifyTimer.reset();
        failedUntil.clear();
        pendingPos = null;
        autoEnabledPacketMine = false;

        if (nullCheck()) {
            toggle();
            return;
        }

        if (PacketMine.INSTANCE.isEnabled()) return;

        if (autoPacketMine.getValue()) {
            PacketMine.INSTANCE.setEnabled(true);
            autoEnabledPacketMine = true;
            NotificationManager.INSTANCE.info(getTranslatedName(), EpsilonTranslations.AutoMine.PACKET_MINE_ENABLED.getTranslatedName());
        } else {
            NotificationManager.INSTANCE.error(getTranslatedName(), EpsilonTranslations.AutoMine.PACKET_MINE_REQUIRED.getTranslatedName());
            setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        // 只回收本模块自动开启的 Packet Mine，用户原本就开启的状态保持不变。
        if (autoEnabledPacketMine && PacketMine.INSTANCE.isEnabled()) {
            PacketMine.INSTANCE.setEnabled(false);
        }
        autoEnabledPacketMine = false;
        failedUntil.clear();
        pendingPos = null;
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck() || !mc.player.isAlive()) return;

        if (!PacketMine.INSTANCE.isEnabled()) {
            pendingPos = null;
            notifyPacketMinePaused();
            return;
        }

        checkPendingBlock();

        // Packet Mine 同一时间只维护一个（双挖时两个）目标，必须等它挖完再切换，否则会反复重置进度。
        // 忙碌期间持续重置计时器，使切换延迟真正从当前方块挖完的时刻开始计算。
        if (PacketMine.INSTANCE.isBusy()) {
            switchTimer.reset();
            return;
        }
        if (!switchTimer.passedMillise(delay.getValue())) return;

        List<Candidate> candidates = findCandidates();
        if (candidates.isEmpty()) return;

        pendingPos = candidates.getFirst().pos();
        PacketMine.INSTANCE.mine(pendingPos);
        switchTimer.reset();
    }

    /**
     * Packet Mine 空闲后仍未消失的方块视为服务端拒绝破坏，加入冷却名单。
     */
    private void checkPendingBlock() {
        failedUntil.values().removeIf(expireAt -> expireAt <= System.currentTimeMillis());
        if (pendingPos == null || PacketMine.INSTANCE.isBusy()) return;

        if (!isAirOrReplacable(pendingPos) && withinMineRange(pendingPos)) {
            failedUntil.put(pendingPos, System.currentTimeMillis() + FAILED_BLOCK_COOLDOWN);
        }
        pendingPos = null;
    }

    private boolean withinMineRange(BlockPos pos) {
        double limit = Math.min(range.getValue(), PacketMine.INSTANCE.getRange());
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < limit * limit;
    }

    private boolean isAirOrReplacable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private void notifyPacketMinePaused() {
        if (!pauseNotifyTimer.passedMillise(PAUSE_NOTIFY_INTERVAL)) return;
        pauseNotifyTimer.reset();
        NotificationManager.INSTANCE.warning(
                getTranslatedName(),
                EpsilonTranslations.AutoMine.PACKET_MINE_DISABLED.getTranslatedName(),
                hashCode()
        );
    }

    /**
     * 按目标排序逐个玩家收集候选方块，返回第一个存在可挖方块的玩家的候选列表。
     */
    private List<Candidate> findCandidates() {
        List<LivingEntity> targets = new ArrayList<>(TargetManager.INSTANCE.acquireTargets(createTargetRequest()));
        targets.sort(createTargetComparator());

        for (LivingEntity living : targets) {
            if (!(living instanceof Player player)) continue;
            List<Candidate> candidates = collectCandidates(player);
            if (candidates.isEmpty()) continue;
            sortCandidates(candidates);
            return candidates;
        }
        return List.of();
    }

    private TargetRequest createTargetRequest() {
        return TargetRequest.of(
                range.getValue(),
                360.0f,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                invisible.getValue(),
                friends.getValue(),
                living -> true,
                64
        );
    }

    private Comparator<LivingEntity> createTargetComparator() {
        Comparator<LivingEntity> byDistance = Comparator.comparingDouble(living -> mc.player.distanceToSqr(living));
        return switch (order.getValue()) {
            case Distance -> byDistance;
            case Health -> Comparator.comparingDouble(LivingEntity::getHealth).thenComparing(byDistance);
            case Hole -> Comparator.comparingInt((LivingEntity living) ->
                            living instanceof Player player && HoleUtils.inHole(player) ? 0 : 1)
                    .thenComparing(byDistance);
        };
    }

    /**
     * 收集目标玩家周围所有可挖方块，并按目标类型去重。
     */
    private List<Candidate> collectCandidates(Player target) {
        List<Candidate> candidates = new ArrayList<>();
        double limit = Math.min(range.getValue(), PacketMine.INSTANCE.getRange());
        BlockPos feet = target.blockPosition();

        if (mineFeet.getValue()) {
            boolean belowAllowed = !feetOnGround.getValue() || target.onGround();
            switch (feetMode.getValue()) {
                case Below -> {
                    if (belowAllowed) addCandidate(candidates, feet.below(), TargetCategory.Feet, limit);
                }
                case Inside -> addCandidate(candidates, feet, TargetCategory.Feet, limit);
                case Both -> {
                    if (belowAllowed) addCandidate(candidates, feet.below(), TargetCategory.Feet, limit);
                    addCandidate(candidates, feet, TargetCategory.Feet, limit);
                }
            }
        }

        if (mineHead.getValue()) {
            BlockPos head = feet.above();
            switch (headMode.getValue()) {
                case Inside -> addCandidate(candidates, head, TargetCategory.Head, limit);
                case Above -> addCandidate(candidates, head.above(), TargetCategory.Head, limit);
                case Both -> {
                    addCandidate(candidates, head, TargetCategory.Head, limit);
                    addCandidate(candidates, head.above(), TargetCategory.Head, limit);
                }
            }
        }

        if (mineBox.getValue()) {
            BlockPos head = feet.above();
            for (int[] offset : SIDE_OFFSETS) {
                addCandidate(candidates, feet.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit);
                if (boxHeadLevel.getValue()) {
                    addCandidate(candidates, head.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit);
                }
            }
            if (boxCorners.getValue()) {
                for (int[] offset : CORNER_OFFSETS) {
                    addCandidate(candidates, feet.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit);
                    if (boxHeadLevel.getValue()) {
                        addCandidate(candidates, head.offset(offset[0], 0, offset[1]), TargetCategory.Box, limit);
                    }
                }
            }
        }

        return candidates;
    }

    private void addCandidate(List<Candidate> candidates, BlockPos pos, TargetCategory category, double limit) {
        for (Candidate candidate : candidates) {
            if (candidate.pos().equals(pos)) return;
        }
        if (!isMineable(pos, limit)) return;
        candidates.add(new Candidate(pos, category));
    }

    /**
     * 复用 Packet Mine 的可挖判定：必须有碰撞体积、可破坏、在 Packet Mine 距离内且不在失败冷却中。
     */
    private boolean isMineable(BlockPos pos, double limit) {
        if (!mc.level.isLoaded(pos)) return false;
        if (failedUntil.getOrDefault(pos, 0L) > System.currentTimeMillis()) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return false;
        // Packet Mine 对硬度 < 0 的方块永远无法完成挖掘，选入会使其卡在忙碌状态。
        if (state.getDestroySpeed(mc.level, pos) < 0.0f) return false;
        if (state.getCollisionShape(mc.level, pos).isEmpty()) return false;
        // 严格小于 Packet Mine 的 farCancel 阈值，避免在临界距离被反复取消。
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < limit * limit;
    }

    private void sortCandidates(List<Candidate> candidates) {
        Comparator<Candidate> byDistance = Comparator.comparingDouble(
                candidate -> mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(candidate.pos())));

        switch (feetLogic.getValue()) {
            case First -> {
                Comparator<Candidate> feetFirst = Comparator.comparingInt(
                        candidate -> candidate.category() == TargetCategory.Feet ? 0 : 1);
                candidates.sort(feetFirst.thenComparing(byDistance));
            }
            case Normal -> candidates.sort(byDistance);
            case Last -> {
                Comparator<Candidate> feetLast = Comparator.comparingInt(
                        candidate -> candidate.category() == TargetCategory.Feet ? 1 : 0);
                candidates.sort(feetLast.thenComparing(byDistance));
            }
        }
    }

}
