package com.projectpulse.model;

import java.util.List;

public record ProjectAnalysis(
        String name,
        String path,
        List<ProjectType> projectTypes,
        List<String> detectedFiles,
        List<RuleFinding> findings,
        List<RuleFinding> strengths,
        List<RuleFinding> improvements,
        List<RuleFinding> riskFlags,
        List<String> technologies,
        List<DependencyFinding> dependencyFindings,
        List<DependencyHealthAssessment> dependencyHealthAssessments
) {
    public ProjectAnalysis(
            String name,
            String path,
            List<ProjectType> projectTypes,
            List<String> detectedFiles,
            List<RuleFinding> findings,
            List<RuleFinding> strengths,
            List<RuleFinding> improvements,
            List<RuleFinding> riskFlags,
            List<String> technologies,
            List<DependencyFinding> dependencyFindings
    ) {
        this(name, path, projectTypes, detectedFiles, findings, strengths, improvements, riskFlags, technologies,
                dependencyFindings, List.of());
    }

    public ProjectAnalysis {
        projectTypes = List.copyOf(projectTypes);
        detectedFiles = List.copyOf(detectedFiles);
        findings = List.copyOf(findings);
        strengths = List.copyOf(strengths);
        improvements = List.copyOf(improvements);
        riskFlags = List.copyOf(riskFlags);
        technologies = List.copyOf(technologies);
        dependencyFindings = List.copyOf(dependencyFindings);
        dependencyHealthAssessments = List.copyOf(dependencyHealthAssessments);
    }
}
