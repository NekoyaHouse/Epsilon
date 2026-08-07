package com.github.epsilon.elements.impl.island.priority;

/**
 * 岛屿实例的优先级，先比较 {@link PriorityType} 档位，再比较同档位内的数值。
 */
public class Priority {

    private final PriorityType type;
    private final int value;

    public Priority(PriorityType type, int value) {
        this.type = type;
        this.value = value;
    }

    public PriorityType type() {
        return type;
    }

    public int value() {
        return value;
    }

}
