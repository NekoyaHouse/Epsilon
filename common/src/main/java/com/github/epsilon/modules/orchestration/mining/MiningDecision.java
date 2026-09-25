package com.github.epsilon.modules.orchestration.mining;
public record MiningDecision(boolean accepted, String reason, long revision) {
    public static MiningDecision rejected(String reason, long revision) { return new MiningDecision(false, reason, revision); }
    public static MiningDecision accepted(long revision) { return new MiningDecision(true, "accepted", revision); }
}
