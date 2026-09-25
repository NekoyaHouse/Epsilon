package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

/**
 * ZealotCrystalPlus 的公有配置枚举。
 * <p>
 * 这些类型会被 Setting 直接引用，因此必须对 {@code com.github.epsilon.modules.impl.combat} 可见。
 */
public final class ZealotOptions {

    private ZealotOptions() {
    }

    /**
     * 候选评分策略；{@code score} 越大越优先。
     */
    public enum DamagePriority {
        Efficient {
            @Override
            public float score(float selfDamage, float targetDamage) {
                return targetDamage - selfDamage;
            }
        },
        Aggressive {
            @Override
            public float score(float selfDamage, float targetDamage) {
                return targetDamage;
            }
        };

        public abstract float score(float selfDamage, float targetDamage);
    }

    public enum SwingHand {
        Auto,
        OffHand,
        MainHand
    }

    public enum SwitchMode {
        Off,
        Legit,
        Ghost
    }

    public enum PlaceMode {
        Off,
        Single,
        Multi
    }

    public enum PacketPlaceMode {
        Off(false, false),
        Weak(true, false),
        Strong(true, true);

        final boolean onRemove;
        final boolean onBreak;

        PacketPlaceMode(boolean onRemove, boolean onBreak) {
            this.onRemove = onRemove;
            this.onBreak = onBreak;
        }
    }

    public enum PlaceBypass {
        Up,
        Down,
        Closest
    }

    public enum BreakMode {
        Off,
        Target,
        Own,
        Smart,
        All
    }

    public enum RangeMode {
        Feet,
        Eyes
    }

    public enum SwingMode {
        None,
        Client,
        Packet
    }

    public enum RenderPredictMode {
        Off,
        Single,
        Multi
    }

    public enum HudInfo {
        Off,
        Speed,
        Target,
        Damage,
        CalculationTime
    }
}
