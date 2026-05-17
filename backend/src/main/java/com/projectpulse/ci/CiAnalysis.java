package com.projectpulse.ci;

import java.util.List;
import org.springframework.lang.Nullable;

public record CiAnalysis(
        boolean hasCi,
        List<String> workflowFiles,
        int workflowCount,
        boolean hasPushTrigger,
        boolean hasPullRequestTrigger,
        boolean hasManualTrigger,
        boolean hasBuildJob,
        boolean hasTestJob,
        boolean hasDeployJob,
        List<String> detectedToolchains,
        @Nullable String notes
) {
    public CiAnalysis {
        workflowFiles = List.copyOf(workflowFiles);
        detectedToolchains = List.copyOf(detectedToolchains);
    }

    public static CiAnalysis noCi() {
        return new CiAnalysis(false, List.of(), 0, false, false, false, false, false, false, List.of(), null);
    }
}
