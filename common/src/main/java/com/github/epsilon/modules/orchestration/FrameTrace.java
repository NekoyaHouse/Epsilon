package com.github.epsilon.modules.orchestration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FrameTrace {
    private final List<String> entries = new CopyOnWriteArrayList<>();
    public void record(String entry) { entries.add(entry); }
    public List<String> entries() { return List.copyOf(entries); }
}
