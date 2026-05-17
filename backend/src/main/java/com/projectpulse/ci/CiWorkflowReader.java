package com.projectpulse.ci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class CiWorkflowReader {

    // Trigger detection — block form (e.g. "  push:" inside on: block) and inline form (e.g. on: [push])
    private static final Pattern PUSH_BLOCK    = Pattern.compile("^\\s+push\\s*:", Pattern.MULTILINE);
    private static final Pattern PR_BLOCK      = Pattern.compile("^\\s+pull_request\\s*:", Pattern.MULTILINE);
    private static final Pattern DISPATCH_BLOCK = Pattern.compile("^\\s+workflow_dispatch\\s*:", Pattern.MULTILINE);
    private static final Pattern PUSH_INLINE    = Pattern.compile("on:\\s*\\[[^\\]]*\\bpush\\b");
    private static final Pattern PR_INLINE      = Pattern.compile("on:\\s*\\[[^\\]]*\\bpull_request\\b");
    private static final Pattern DISPATCH_INLINE = Pattern.compile("on:\\s*\\[[^\\]]*\\bworkflow_dispatch\\b");

    // Job IDs are at exactly 2-space indent directly under the jobs: block.
    // Matches lines like "  build:" or "  deploy_production:".
    private static final Pattern JOB_ID = Pattern.compile("^  ([a-z][a-z0-9_-]*)\\s*:", Pattern.MULTILINE);

    // Run command evidence — these indicate what the workflow does in steps.
    private static final Pattern BUILD_RUN = Pattern.compile(
            "\\b(mvn[^\\n]*(install|compile|package|clean)|npm run build|gradle[^\\n]*build|cmake[^\\n]*--build)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEST_RUN = Pattern.compile(
            "\\b(mvn[^\\n]*test|npm[^\\n]*test|pytest|gradle[^\\n]*test|jest|go test|dotnet test)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEPLOY_RUN = Pattern.compile(
            "\\b(docker push|kubectl apply|helm (install|upgrade)|aws.*deploy|terraform apply|serverless deploy)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> BUILD_JOB_KEYWORDS = Set.of("build", "compile", "package", "assemble");
    private static final Set<String> TEST_JOB_KEYWORDS  = Set.of("test", "tests", "unit-test", "integration-test", "e2e");
    private static final Set<String> DEPLOY_JOB_KEYWORDS = Set.of("deploy", "release", "publish", "deployment", "cd");

    public CiAnalysis read(Path projectPath) {
        Path workflowsDir = projectPath.resolve(".github").resolve("workflows");
        if (!Files.isDirectory(workflowsDir)) {
            return CiAnalysis.noCi();
        }

        List<Path> yamlFiles = collectYamlFiles(workflowsDir);
        if (yamlFiles.isEmpty()) {
            return CiAnalysis.noCi();
        }

        List<String> fileNames = yamlFiles.stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();

        boolean hasPush = false;
        boolean hasPr = false;
        boolean hasManual = false;
        boolean hasBuild = false;
        boolean hasTest = false;
        boolean hasDeploy = false;
        Set<String> toolchains = new LinkedHashSet<>();

        for (Path file : yamlFiles) {
            String content = readFile(file);
            hasPush    |= matchesTrigger(content, PUSH_BLOCK, PUSH_INLINE);
            hasPr      |= matchesTrigger(content, PR_BLOCK, PR_INLINE);
            hasManual  |= matchesTrigger(content, DISPATCH_BLOCK, DISPATCH_INLINE);
            hasBuild   |= detectJobType(content, BUILD_JOB_KEYWORDS, BUILD_RUN);
            hasTest    |= detectJobType(content, TEST_JOB_KEYWORDS, TEST_RUN);
            hasDeploy  |= detectJobType(content, DEPLOY_JOB_KEYWORDS, DEPLOY_RUN);
            toolchains.addAll(detectToolchains(content));
        }

        String notes = buildNotes(hasBuild, hasTest, hasDeploy, hasPr);

        return new CiAnalysis(
                true, fileNames, fileNames.size(),
                hasPush, hasPr, hasManual,
                hasBuild, hasTest, hasDeploy,
                List.copyOf(toolchains), notes
        );
    }

    private List<Path> collectYamlFiles(Path workflowsDir) {
        try (Stream<Path> files = Files.list(workflowsDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean matchesTrigger(String content, Pattern blockForm, Pattern inlineForm) {
        return blockForm.matcher(content).find() || inlineForm.matcher(content).find();
    }

    private boolean detectJobType(String content, Set<String> jobKeywords, Pattern runPattern) {
        // Check job ID lines at 2-space indent
        var jobMatcher = JOB_ID.matcher(content);
        while (jobMatcher.find()) {
            String jobId = jobMatcher.group(1).toLowerCase();
            for (String keyword : jobKeywords) {
                if (jobId.equals(keyword) || jobId.contains(keyword)) {
                    return true;
                }
            }
        }
        // Check run: command content
        return runPattern.matcher(content).find();
    }

    private List<String> detectToolchains(String content) {
        List<String> found = new ArrayList<>();
        if (content.contains("actions/setup-java"))   found.add("actions/setup-java");
        if (content.contains("actions/setup-node"))   found.add("actions/setup-node");
        if (content.contains("actions/setup-python")) found.add("actions/setup-python");
        if (content.contains("docker"))               found.add("docker");
        if (content.contains("mvn") || content.toLowerCase().contains("maven")) found.add("maven");
        if (content.contains("npm"))                  found.add("npm");
        if (content.contains("pytest"))               found.add("pytest");
        return found;
    }

    private String buildNotes(boolean hasBuild, boolean hasTest, boolean hasDeploy, boolean hasPr) {
        List<String> features = new ArrayList<>();
        if (hasBuild) features.add("build");
        if (hasTest)  features.add("test");
        if (hasDeploy) features.add("deploy");
        if (hasPr)    features.add("PR validation");

        if (features.isEmpty()) {
            return "Workflow detected; no recognized job patterns found.";
        }
        return "Detected workflow jobs: " + String.join(", ", features) + ".";
    }
}
