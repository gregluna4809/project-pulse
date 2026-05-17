package com.projectpulse.gitignore;

import com.projectpulse.model.ProjectType;
import com.projectpulse.model.RuleFinding;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GitignoreRuleEvaluator {

    public List<RuleFinding> strengths(GitignoreAnalysis gitignore, List<ProjectType> projectTypes) {
        if (!gitignore.hasGitignore()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        findings.add(new RuleFinding(
                "gitignore-present",
                ".gitignore detected.",
                ".gitignore"
        ));

        if (gitignore.hygieneScore() >= 80) {
            findings.add(new RuleFinding(
                    "gitignore-strong-coverage",
                    ".gitignore hygiene coverage is strong (" + gitignore.hygieneScore() + "%).",
                    ".gitignore"
            ));
        }

        return List.copyOf(findings);
    }

    public List<RuleFinding> improvements(GitignoreAnalysis gitignore, List<ProjectType> projectTypes) {
        if (!gitignore.hasGitignore()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        List<String> missingBuild = missingBuildArtifactPatterns(gitignore, projectTypes);
        if (!missingBuild.isEmpty()) {
            findings.add(new RuleFinding(
                    "gitignore-missing-build-artifacts",
                    "Build output directories not excluded by .gitignore: " + String.join(", ", missingBuild) + ".",
                    ".gitignore"
            ));
        }

        List<String> missingSecondary = missingDependencyArtifactPatterns(gitignore, projectTypes);
        if (!missingSecondary.isEmpty()) {
            findings.add(new RuleFinding(
                    "gitignore-missing-dependency-artifacts",
                    "Common artifact patterns not excluded by .gitignore: " + String.join(", ", missingSecondary) + ".",
                    ".gitignore"
            ));
        }

        return List.copyOf(findings);
    }

    // Risk flags fire only when a .gitignore exists but is missing critical patterns.
    // If no .gitignore is present, the missing-gitignore improvement already captures that.
    public List<RuleFinding> riskFlags(GitignoreAnalysis gitignore, List<ProjectType> projectTypes) {
        if (!gitignore.hasGitignore()) {
            return List.of();
        }
        List<RuleFinding> findings = new ArrayList<>();

        if (isMissing(gitignore, ".env")) {
            findings.add(new RuleFinding(
                    "gitignore-env-not-ignored",
                    ".env not excluded by .gitignore — secrets may be committed accidentally.",
                    ".gitignore"
            ));
        }

        if (hasPython(projectTypes) && isMissing(gitignore, ".venv/")) {
            findings.add(new RuleFinding(
                    "gitignore-venv-not-ignored",
                    ".venv/ not excluded by .gitignore — virtual environment may be committed.",
                    ".gitignore"
            ));
        }

        if (hasNode(projectTypes) && isMissing(gitignore, "node_modules/")) {
            findings.add(new RuleFinding(
                    "gitignore-node-modules-not-ignored",
                    "node_modules/ not excluded by .gitignore — dependency directory may be committed.",
                    ".gitignore"
            ));
        }

        return List.copyOf(findings);
    }

    private boolean isMissing(GitignoreAnalysis gitignore, String pattern) {
        return gitignore.missingExpectedPatterns().contains(pattern);
    }

    private boolean hasJava(List<ProjectType> types) {
        return types.contains(ProjectType.MAVEN) || types.contains(ProjectType.SPRING_BOOT);
    }

    private boolean hasNode(List<ProjectType> types) {
        return types.contains(ProjectType.NODE)
                || types.contains(ProjectType.REACT)
                || types.contains(ProjectType.VITE);
    }

    private boolean hasPython(List<ProjectType> types) {
        return types.contains(ProjectType.PYTHON);
    }

    private List<String> missingBuildArtifactPatterns(GitignoreAnalysis gitignore, List<ProjectType> projectTypes) {
        List<String> relevant = new ArrayList<>();
        if (hasJava(projectTypes)) relevant.add("target/");
        if (hasNode(projectTypes)) {
            relevant.add("dist/");
            relevant.add("build/");
        }
        if (hasPython(projectTypes)) relevant.add("__pycache__/");

        return relevant.stream()
                .filter(p -> isMissing(gitignore, p))
                .toList();
    }

    private List<String> missingDependencyArtifactPatterns(GitignoreAnalysis gitignore, List<ProjectType> projectTypes) {
        List<String> relevant = new ArrayList<>();
        if (hasJava(projectTypes)) relevant.add("*.log");
        if (hasNode(projectTypes)) relevant.add("coverage/");
        if (hasPython(projectTypes)) {
            relevant.add("*.pyc");
            relevant.add(".pytest_cache/");
        }

        return relevant.stream()
                .filter(p -> isMissing(gitignore, p))
                .toList();
    }
}
