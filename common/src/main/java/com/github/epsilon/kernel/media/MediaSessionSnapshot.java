package com.github.epsilon.kernel.media;

public record MediaSessionSnapshot(
        String source,
        String title,
        String artist,
        boolean playing,
        long positionMillis,
        long durationMillis,
        long sampledNanos,
        MediaArtwork artwork
) {
    public long positionAt(long nowNanos) {
        long elapsed = playing ? Math.clamp((nowNanos - sampledNanos) / 1_000_000L, 0L, 1500L) : 0L;
        return Math.clamp(positionMillis + elapsed, 0L, durationMillis);
    }
}
