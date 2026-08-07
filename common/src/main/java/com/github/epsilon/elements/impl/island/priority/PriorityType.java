package com.github.epsilon.elements.impl.island.priority;

/**
 * 岛屿实例的优先级档位，索引越大越优先展示。
 */
public enum PriorityType {

    Low(1),
    Med(2),
    High(3);

    public final int index;

    PriorityType(int index) {
        this.index = index;
    }

}
