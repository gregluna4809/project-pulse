package com.projectpulse.ci;

import com.projectpulse.model.RuleFinding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CiRuleEvaluator {

    // Note: "ci-workflow-detected" is intentionally omitted here — the RuleEngine already
    // generates "github-actions-detected" from detected files. These findings cover the
    // more granular CI quality signals that file-presence detection cannot.

    public List<RuleFinding> strengths(CiAnalysis ci) {
        if (!ci.hasCi()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (ci.hasPullRequestTrigger()) {
            findings.add(new RuleFinding(
                    "ci-pull-request-validation",
                    "CI workflow triggers on pull requests.",
                    ".github/workflows"
            ));
        }

        if (ci.hasBuildJob()) {
            findings.add(new RuleFinding(
                    "ci-build-job-detected",
                    "CI build job detected.",
                    ".github/workflows"
            ));
        }

        if (ci.hasTestJob()) {
            findings.add(new RuleFinding(
                    "ci-test-job-detected",
                    "CI test job detected.",
                    ".github/workflows"
            ));
        }

        return List.copyOf(findings);
    }

    public List<RuleFinding> improvements(CiAnalysis ci) {
        if (!ci.hasCi()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (!ci.hasPullRequestTrigger()) {
            findings.add(new RuleFinding(
                    "ci-no-pull-request-trigger",
                    "CI workflow does not trigger on pull requests.",
                    ".github/workflows"
            ));
        }

        if (!ci.hasTestJob()) {
            findings.add(new RuleFinding(
                    "ci-no-test-job",
                    "No test job detected in CI workflow.",
                    ".github/workflows"
            ));
        }

        if (!ci.hasBuildJob()) {
            findings.add(new RuleFinding(
                    "ci-no-build-job",
                    "No build job detected in CI workflow.",
                    ".github/workflows"
            ));
        }

        return List.copyOf(findings);
    }

    public List<RuleFinding> riskFlags(CiAnalysis ci) {
        if (!ci.hasCi()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (ci.hasDeployJob() && !ci.hasManualTrigger()) {
            findings.add(new RuleFinding(
                    "ci-deploy-without-manual-trigger",
                    "Deploy job detected without a workflow_dispatch (manual) trigger — deployments may run automatically.",
                    ".github/workflows"
            ));
        }

        return List.copyOf(findings);
    }
}
