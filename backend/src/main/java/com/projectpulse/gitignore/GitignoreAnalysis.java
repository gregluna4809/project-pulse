package com.projectpulse.gitignore;

import java.util.List;
import org.springframework.lang.Nullable;

public record GitignoreAnalysis(
        boolean hasGitignore,
        List<String> ignoredPatterns,
        List<String> missingExpectedPatterns,
        int hygieneScore,
        @Nullable String notes
) {
    public GitignoreAnalysis {
        ignoredPatterns = List.copyOf(ignoredPatterns);
        missingExpectedPatterns = List.copyOf(missingExpectedPatterns);
    }

    public static GitignoreAnalysis noGitignore() {
        return new GitignoreAnalysis(false, List.of(), List.of(), 0, null);
    }
}
