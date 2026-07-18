package com.github.epsilon.utils.rotation;

public enum Priority {

    Lowest(0),
    Low(10),
    Medium(50),
    High(100),
    Highest(1000);

    public final int priority;

    Priority(int priority) {
        this.priority = priority;
    }

}
