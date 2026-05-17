package com.projectpulse.git;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Read-only git repository metadata for a scanned project directory.
 * All fields are populated by inspecting .git directory contents only.
 * lastCommitDate is an ISO-8601 date string (e.g. "2026-05-17") or null if unavailable.
 */
public record GitAnalysis(
        boolean gitRepository,
        @Nullable String currentBranch,
        @Nullable String headCommit,
        @Nullable String lastCommitDate,
        @Nullable Integer daysSinceLastCommit,
        int branchCount,
        boolean hasRemote,
        List<String> remoteNames,
        boolean detachedHead,
        GitActivityStatus activityStatus,
        @Nullable String notes
) {
    public GitAnalysis {
        remoteNames = List.copyOf(remoteNames);
    }

    public static GitAnalysis noGit() {
        return new GitAnalysis(
                false, null, null, null, null,
                0, false, List.of(), false,
                GitActivityStatus.NO_GIT, null
        );
    }
}
