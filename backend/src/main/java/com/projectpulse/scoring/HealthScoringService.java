package com.projectpulse.scoring;

import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.HealthTier;
import com.projectpulse.model.ProjectHealthAssessment;
import com.projectpulse.model.RuleFinding;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HealthScoringService {

    public ProjectHealthAssessment assess(
            List<RuleFinding> strengths,
            List<RuleFinding> improvements,
            List<RuleFinding> riskFlags,
            List<DependencyHealthAssessment> dependencyAssessments
    ) {
        int score = 100;
        score += Math.min(strengths.stream().mapToInt(this::strengthBonus).sum(), 8);
        score -= improvements.stream().mapToInt(this::improvementPenalty).sum();
        score -= riskFlags.stream().mapToInt(this::riskPenalty).sum();

        for (DependencyHealthAssessment dep : dependencyAssessments) {
            score += penaltyFor(dep.healthStatus());
        }

        score = Math.max(0, Math.min(100, score));

        HealthTier tier = tierFor(score);
        return new ProjectHealthAssessment(score, tier, summaryFor(tier));
    }

    private int strengthBonus(RuleFinding finding) {
        return switch (finding.ruleId()) {
            case "git-repository-detected", "git-remote-configured", "git-recent-activity" -> 1;
            case "automated-tests-detected" -> 3;
            case "integration-tests-detected" -> 2;
            default -> 2;
        };
    }

    private int improvementPenalty(RuleFinding finding) {
        return switch (finding.ruleId()) {
            case "no-recognized-build-manifest" -> 18;
            case "no-test-files-detected" -> 12;
            case "missing-ci-workflow" -> 10;
            default -> 6;
        };
    }

    private int riskPenalty(RuleFinding finding) {
        return switch (finding.ruleId()) {
            case "compiled-binaries-detected" -> 25;
            case "ci-without-tests", "backend-api-without-tests" -> 10;
            default -> 15;
        };
    }

    private int penaltyFor(HealthStatus status) {
        return switch (status) {
            case CURRENT           ->   0;
            case ACCEPTABLE        ->  -1;
            case UPGRADE_CANDIDATE ->  -4;
            case LEGACY            -> -10;
            case CRITICAL          -> -20;
            case UNKNOWN           ->   0;
            case MANAGED           ->   0;
        };
    }

    private HealthTier tierFor(int score) {
        if (score >= 90) return HealthTier.EXCELLENT;
        if (score >= 75) return HealthTier.GOOD;
        if (score >= 60) return HealthTier.FAIR;
        if (score >= 40) return HealthTier.AT_RISK;
        return HealthTier.CRITICAL;
    }

    private String summaryFor(HealthTier tier) {
        return switch (tier) {
            case EXCELLENT -> "Healthy engineering posture.";
            case GOOD      -> "Good engineering posture with minor improvements available.";
            case FAIR      -> "Moderate engineering debt detected.";
            case AT_RISK   -> "Significant engineering risk detected.";
            case CRITICAL  -> "Critical engineering issues require immediate attention.";
        };
    }
}
