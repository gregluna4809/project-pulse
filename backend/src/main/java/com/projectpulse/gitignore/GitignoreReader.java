package com.projectpulse.gitignore;

import com.projectpulse.model.ProjectType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GitignoreReader {

    public GitignoreAnalysis read(Path projectPath, List<ProjectType> projectTypes) {
        Path gitignorePath = projectPath.resolve(".gitignore");
        if (!Files.exists(gitignorePath)) {
            return GitignoreAnalysis.noGitignore();
        }

        List<String> ignoredPatterns = parsePatterns(gitignorePath);
        List<String> expectedPatterns = buildExpectedPatterns(projectTypes);
        List<String> missingPatterns = computeMissing(ignoredPatterns, expectedPatterns);

        int hygieneScore = computeHygieneScore(expectedPatterns, missingPatterns);
        String notes = buildNotes(expectedPatterns, missingPatterns);

        return new GitignoreAnalysis(true, ignoredPatterns, missingPatterns, hygieneScore, notes);
    }

    private List<String> parsePatterns(Path gitignorePath) {
        try {
            return Files.readAllLines(gitignorePath, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private List<String> buildExpectedPatterns(List<ProjectType> projectTypes) {
        Set<String> patterns = new LinkedHashSet<>();

        if (hasJavaTech(projectTypes)) {
            patterns.addAll(List.of("target/", "*.log", ".env", ".idea/"));
        }
        if (hasNodeTech(projectTypes)) {
            patterns.addAll(List.of("node_modules/", "dist/", "build/", ".env", "coverage/"));
        }
        if (projectTypes.contains(ProjectType.PYTHON)) {
            patterns.addAll(List.of("__pycache__/", "*.pyc", ".venv/", ".env", ".pytest_cache/"));
        }

        // General patterns expected for all projects
        patterns.addAll(List.of(".vscode/", ".idea/", "Thumbs.db", ".DS_Store", ".env"));

        return List.copyOf(patterns);
    }

    private boolean hasJavaTech(List<ProjectType> types) {
        return types.contains(ProjectType.MAVEN) || types.contains(ProjectType.SPRING_BOOT);
    }

    private boolean hasNodeTech(List<ProjectType> types) {
        return types.contains(ProjectType.NODE)
                || types.contains(ProjectType.REACT)
                || types.contains(ProjectType.VITE);
    }

    private List<String> computeMissing(List<String> ignoredPatterns, List<String> expectedPatterns) {
        return expectedPatterns.stream()
                .filter(expected -> !matchesAny(ignoredPatterns, expected))
                .toList();
    }

    // Normalizes a .gitignore pattern for fuzzy comparison:
    // strips wildcard (*) and path separator (/) characters, lowercases.
    // ".env", "/.env", ".env*" all normalize to ".env".
    // "target/", "target" both normalize to "target".
    private String normalize(String pattern) {
        return pattern.replaceAll("[*/]", "").toLowerCase().trim();
    }

    private boolean matchesAny(List<String> ignoredPatterns, String expected) {
        String normalizedExpected = normalize(expected);
        return ignoredPatterns.stream()
                .map(this::normalize)
                .anyMatch(normalizedExpected::equals);
    }

    private int computeHygieneScore(List<String> expected, List<String> missing) {
        if (expected.isEmpty()) {
            return 100;
        }
        int covered = expected.size() - missing.size();
        return (int) Math.round((double) covered / expected.size() * 100);
    }

    private String buildNotes(List<String> expected, List<String> missing) {
        if (expected.isEmpty()) {
            return "No technology-specific patterns expected.";
        }
        if (missing.isEmpty()) {
            return "All expected patterns covered.";
        }
        int covered = expected.size() - missing.size();
        return covered + " of " + expected.size() + " expected patterns covered.";
    }
}
