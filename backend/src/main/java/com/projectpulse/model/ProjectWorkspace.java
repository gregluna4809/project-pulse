package com.projectpulse.model;

import com.projectpulse.ci.CiAnalysis;
import com.projectpulse.git.GitAnalysis;
import com.projectpulse.gitignore.GitignoreAnalysis;
import java.util.List;

public record ProjectWorkspace(
        String name,
        String path,
        List<String> detectedFiles,
        List<ProjectType> projectTypes,
        List<String> technologies,
        List<RuleFinding> findings,
        List<RuleFinding> strengths,
        List<RuleFinding> improvements,
        List<RuleFinding> riskFlags,
        List<DependencyFinding> dependencyFindings,
        List<DependencyHealthAssessment> dependencyHealthAssessments,
        List<ProjectModule> modules,
        ProjectHealthAssessment healthAssessment,
        GitAnalysis gitAnalysis,
        GitignoreAnalysis gitignoreAnalysis,
        CiAnalysis ciAnalysis,
        TestAnalysis testAnalysis
) {
    public ProjectWorkspace {
        detectedFiles = List.copyOf(detectedFiles);
        projectTypes = List.copyOf(projectTypes);
        technologies = List.copyOf(technologies);
        findings = List.copyOf(findings);
        strengths = List.copyOf(strengths);
        improvements = List.copyOf(improvements);
        riskFlags = List.copyOf(riskFlags);
        dependencyFindings = List.copyOf(dependencyFindings);
        dependencyHealthAssessments = List.copyOf(dependencyHealthAssessments);
        modules = List.copyOf(modules);
    }
}
