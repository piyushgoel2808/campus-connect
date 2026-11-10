package com.bvicam.campusconnect.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Snapshot of the automated, read-only BVICAM portal synchronization pipeline state.
 */
public record BvicamSyncStatusDto(
        boolean running,
        String mode,
        String phase,
        String currentActivity,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int discoveredCount,
        int indexedNewCount,
        int scannedFallbackCount,
        int updatedCount,
        int skippedExistingCount,
        int failedCount,
        String lastMessage,
        String stoppedReason,
        List<String> recentIssues
) {
    /**
     * Returns the default idle status snapshot before any sync run starts.
     */
    public static BvicamSyncStatusDto idle() {
        return new BvicamSyncStatusDto(
                false,
                "NONE",
                "IDLE",
                "Idle — awaiting schedule or manual sync",
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                "No BVICAM sync has run yet in this session.",
                null,
                List.of()
        );
    }
}
