package com.projectpulse.scanner;

import com.projectpulse.ci.CiAnalysis;
import com.projectpulse.ci.CiRuleEvaluator;
import com.projectpulse.ci.CiWorkflowReader;
import com.projectpulse.detector.ProjectDetector;
import com.projectpulse.git.GitAnalysis;
import com.projectpulse.git.GitMetadataReader;
import com.projectpulse.git.GitRuleEvaluator;
import com.projectpulse.gitignore.GitignoreAnalysis;
import com.projectpulse.gitignore.GitignoreReader;
import com.projectpulse.gitignore.GitignoreRuleEvaluator;
import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.ProjectHealthAssessment;
import com.projectpulse.model.ProjectModule;
import com.projectpulse.model.ProjectType;
import com.projectpulse.model.ProjectWorkspace;
import com.projectpulse.model.DiscoveredWorkspace;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.ScanRequest;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.TestAnalysis;
import com.projectpulse.model.WorkspaceDiscoveryResponse;
import com.projectpulse.rules.RuleEngine;
import com.projectpulse.rules.WorkspaceAssets;
import com.projectpulse.scoring.HealthScoringService;
import com.projectpulse.tests.TestAnalysisReader;
import com.projectpulse.tests.TestRuleEvaluator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ScanService {

    private static final Set<String> MODULE_FOLDER_NAMES = Set.of(
            "backend",
            "frontend",
            "app",
            "server",
            "client",
            "api"
    );

    private final ProjectDetector projectDetector;
    private final RuleEngine ruleEngine;
    private final HealthScoringService healthScoringService;
    private final GitMetadataReader gitMetadataReader;
    private final GitRuleEvaluator gitRuleEvaluator;
    private final GitignoreReader gitignoreReader;
    private final GitignoreRuleEvaluator gitignoreRuleEvaluator;
    private final CiWorkflowReader ciWorkflowReader;
    private final CiRuleEvaluator ciRuleEvaluator;
    private final TestAnalysisReader testAnalysisReader;
    private final TestRuleEvaluator testRuleEvaluator;

    public ScanService(
            ProjectDetector projectDetector,
            RuleEngine ruleEngine,
            HealthScoringService healthScoringService,
            GitMetadataReader gitMetadataReader,
            GitRuleEvaluator gitRuleEvaluator,
            GitignoreReader gitignoreReader,
            GitignoreRuleEvaluator gitignoreRuleEvaluator,
            CiWorkflowReader ciWorkflowReader,
            CiRuleEvaluator ciRuleEvaluator,
            TestAnalysisReader testAnalysisReader,
            TestRuleEvaluator testRuleEvaluator
    ) {
        this.projectDetector = projectDetector;
        this.ruleEngine = ruleEngine;
        this.healthScoringService = healthScoringService;
        this.gitMetadataReader = gitMetadataReader;
        this.gitRuleEvaluator = gitRuleEvaluator;
        this.gitignoreReader = gitignoreReader;
        this.gitignoreRuleEvaluator = gitignoreRuleEvaluator;
        this.ciWorkflowReader = ciWorkflowReader;
        this.ciRuleEvaluator = ciRuleEvaluator;
        this.testAnalysisReader = testAnalysisReader;
        this.testRuleEvaluator = testRuleEvaluator;
    }

    public ScanResponse scan(ScanRequest request) {
        Path root = resolveAndValidateRoot(request.rootPath());
        List<ProjectWorkspace> workspaces = buildWorkspaces(root, request);
        return new ScanResponse(workspaces.size(), workspaces);
    }

    public WorkspaceDiscoveryResponse discover(String rootPath) {
        Path root = resolveAndValidateRoot(rootPath);
        List<DiscoveredWorkspace> workspaces = listImmediateWorkspaceDirectories(root).stream()
                .map(path -> new DiscoveredWorkspace(
                        path.getFileName().toString(),
                        path.toAbsolutePath().normalize().toString()
                ))
                .toList();
        return new WorkspaceDiscoveryResponse(root.toString(), workspaces.size(), workspaces);
    }

    private Path resolveAndValidateRoot(String rootPath) {
        try {
            Path root = Path.of(rootPath).toAbsolutePath().normalize();

            if (!Files.exists(root)) {
                throw new InvalidScanRootException("Scan root does not exist: " + root);
            }

            if (!Files.isDirectory(root)) {
                throw new InvalidScanRootException("Scan root must be a directory: " + root);
            }

            return root;
        } catch (InvalidPathException exception) {
            throw new InvalidScanRootException("Scan root path is invalid: " + rootPath);
        }
    }

    private List<ProjectWorkspace> buildWorkspaces(Path root, ScanRequest request) {
        return filterWorkspaceCandidates(root, listImmediateWorkspaceDirectories(root), request).stream()
                .map(candidate -> buildWorkspace(candidate.path(), candidate.forceInclude()))
                .flatMap(Optional::stream)
                .toList();
    }

    private List<Path> listImmediateWorkspaceDirectories(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException exception) {
            throw new InvalidScanRootException("Unable to read scan root: " + root);
        }
    }

    private List<WorkspaceCandidate> filterWorkspaceCandidates(Path root, List<Path> candidates, ScanRequest request) {
        Set<String> includeNames = normalizedNameSet(request.includeProjectNames());
        Set<String> excludeNames = normalizedNameSet(request.excludeProjectNames());
        Set<String> includePaths = normalizedImmediateChildPathSet(root, request.includePaths());
        Set<String> excludePaths = normalizedImmediateChildPathSet(root, request.excludePaths());

        boolean hasIncludes = hasRequestedScope(request.includeProjectNames()) || hasRequestedScope(request.includePaths());

        return candidates.stream()
                .map(path -> {
                    String candidateName = normalizeName(path.getFileName().toString());
                    String candidatePath = normalizePathKey(path.toAbsolutePath().normalize());

                    boolean included = !hasIncludes
                            || includeNames.contains(candidateName)
                            || includePaths.contains(candidatePath);

                    boolean excluded = excludeNames.contains(candidateName) || excludePaths.contains(candidatePath);
                    return new WorkspaceCandidate(path, included && !excluded, hasIncludes && included && !excluded);
                })
                .filter(WorkspaceCandidate::included)
                .toList();
    }

    private Set<String> normalizedNameSet(List<String> names) {
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .map(this::normalizeName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean hasRequestedScope(List<String> values) {
        return values.stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private Set<String> normalizedImmediateChildPathSet(Path root, List<String> paths) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return paths.stream()
                .map(path -> normalizeRequestedWorkspacePath(normalizedRoot, path))
                .flatMap(Optional::stream)
                .map(this::normalizePathKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String normalizePathKey(Path path) {
        String normalizedPath = path.toAbsolutePath().normalize().toString();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return normalizedPath.toLowerCase(Locale.ROOT);
        }
        return normalizedPath;
    }

    private Optional<Path> normalizeRequestedWorkspacePath(Path root, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Optional.empty();
        }

        try {
            Path rawPath = Path.of(requestedPath.trim());
            Path normalizedPath = rawPath.isAbsolute()
                    ? rawPath.toAbsolutePath().normalize()
                    : root.resolve(rawPath).toAbsolutePath().normalize();

            if (!normalizedPath.startsWith(root) || !root.equals(normalizedPath.getParent())) {
                return Optional.empty();
            }

            return Optional.of(normalizedPath);
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    // A directory becomes a workspace if it has detectable project evidence at its root,
    // or if it contains at least one recognised module folder with project evidence.
    // Directories with neither are silently skipped.
    private Optional<ProjectWorkspace> buildWorkspace(Path workspacePath, boolean forceInclude) {
        List<ProjectModule> modules = buildModules(workspacePath);
        List<ProjectAnalysis> detections = projectDetector.detect(workspacePath);

        if (detections.isEmpty() && modules.isEmpty()) {
            if (forceInclude) {
                return Optional.of(genericWorkspace(workspacePath));
            }
            return Optional.empty();
        }

        if (detections.isEmpty()) {
            return Optional.of(shellWorkspace(workspacePath, modules));
        }

        ProjectAnalysis evaluated = ruleEngine.evaluate(detections.getFirst());
        return Optional.of(toWorkspace(evaluated, workspacePath, modules));
    }

    private ProjectWorkspace genericWorkspace(Path path) {
        GenericSignals signals = detectGenericSignals(path);
        List<String> technologies = signals.hasCppSignals() ? List.of("C++") : List.of("Unknown");
        List<ProjectType> projectTypes = new ArrayList<>(List.of(
                ProjectType.UNKNOWN,
                ProjectType.GENERIC,
                ProjectType.AD_HOC
        ));
        if (signals.hasCppSignals()) {
            projectTypes.add(ProjectType.CPP);
        }

        TestAnalysis testAnalysis = testAnalysisReader.read(path);
        GitAnalysis gitAnalysis = gitMetadataReader.read(path);
        GitignoreAnalysis gitignoreAnalysis = gitignoreReader.read(path, projectTypes);
        CiAnalysis ciAnalysis = ciWorkflowReader.read(path);
        List<RuleFinding> strengths = testRuleEvaluator.strengths(testAnalysis);
        List<RuleFinding> improvements = concatDedupedByRuleId(
                List.of(new RuleFinding(
                "no-recognized-build-manifest",
                "No recognized build manifest detected.",
                "Expected a known manifest such as pom.xml, package.json, pyproject.toml, requirements.txt, or CMakeLists.txt."
                )),
                testRuleEvaluator.improvements(testAnalysis, projectTypes)
        );
        List<RuleFinding> binaryRiskFlags = signals.hasCompiledBinaries()
                ? List.of(new RuleFinding(
                        "compiled-binaries-detected",
                        "Compiled binaries detected in workspace.",
                        "Detected executable artifacts: " + String.join(", ", signals.compiledBinaryFiles())
                ))
                : List.of();
        List<RuleFinding> riskFlags = concatDedupedByRuleId(binaryRiskFlags,
                testRuleEvaluator.riskFlags(testAnalysis, projectTypes, technologies, ciAnalysis));

        ProjectHealthAssessment health = healthScoringService.assess(
                strengths,
                improvements,
                riskFlags,
                List.of()
        );

        return new ProjectWorkspace(
                path.getFileName().toString(),
                path.toAbsolutePath().normalize().toString(),
                signals.detectedFiles(),
                projectTypes,
                technologies,
                concat(strengths, improvements, riskFlags),
                strengths,
                improvements,
                riskFlags,
                List.of(),
                List.of(),
                List.of(),
                health,
                gitAnalysis,
                gitignoreAnalysis,
                ciAnalysis,
                testAnalysis
        );
    }

    private GenericSignals detectGenericSignals(Path workspacePath) {
        Set<String> detectedFiles = new LinkedHashSet<>();
        Set<String> compiledBinaryFiles = new LinkedHashSet<>();

        addGenericDirectoryIfExists(workspacePath, "src", detectedFiles);
        addGenericDirectoryIfExists(workspacePath, "build", detectedFiles);
        addGenericDirectoryIfExists(workspacePath, "bin", detectedFiles);

        try (Stream<Path> stream = Files.walk(workspacePath, 3)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (hasAnySuffix(fileName, ".cpp", ".c", ".h", ".hpp", ".exe", ".bat", ".ps1")) {
                            String relativePath = workspacePath.relativize(file).toString();
                            detectedFiles.add(relativePath);
                            if (fileName.endsWith(".exe")) {
                                compiledBinaryFiles.add(relativePath);
                            }
                        }
                    });
        } catch (IOException ignored) {
            // Generic fallback should be best-effort and must not fail the selected workspace.
        }

        return new GenericSignals(
                detectedFiles.stream().sorted().toList(),
                compiledBinaryFiles.stream().sorted().toList()
        );
    }

    private void addGenericDirectoryIfExists(Path workspacePath, String directory, Set<String> detectedFiles) {
        if (Files.isDirectory(workspacePath.resolve(directory))) {
            detectedFiles.add(directory + "/");
        }
    }

    private boolean hasAnySuffix(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private List<ProjectModule> buildModules(Path workspacePath) {
        WorkspaceAssets assets = detectWorkspaceAssets(workspacePath);
        return listChildDirectories(workspacePath).stream()
                .filter(this::isModuleFolder)
                .map(dir -> buildModule(dir, assets))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ProjectModule> buildModule(Path modulePath, WorkspaceAssets workspaceAssets) {
        List<ProjectAnalysis> detections = projectDetector.detect(modulePath);
        if (detections.isEmpty()) {
            return Optional.empty();
        }
        ProjectAnalysis evaluated = ruleEngine.evaluateAsModule(detections.getFirst(), workspaceAssets);
        return Optional.of(toModule(evaluated, modulePath));
    }

    private WorkspaceAssets detectWorkspaceAssets(Path workspacePath) {
        return new WorkspaceAssets(
                pathHasAny(workspacePath, "README.md", "README.txt", "readme.md"),
                pathHasAny(workspacePath, ".gitignore"),
                pathHasAny(workspacePath, ".github/workflows"),
                pathHasAny(workspacePath, ".env.example", ".env.template", "env.example")
        );
    }

    private boolean pathHasAny(Path dir, String... fileNames) {
        for (String fileName : fileNames) {
            if (Files.exists(dir.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    private ProjectWorkspace toWorkspace(ProjectAnalysis analysis, Path workspacePath, List<ProjectModule> modules) {
        GitAnalysis gitAnalysis = gitMetadataReader.read(workspacePath);
        GitignoreAnalysis gitignoreAnalysis = gitignoreReader.read(workspacePath, analysis.projectTypes());
        CiAnalysis ciAnalysis = ciWorkflowReader.read(workspacePath);
        TestAnalysis testAnalysis = testAnalysisReader.read(workspacePath);

        List<RuleFinding> strengths = concatDedupedByRuleId(
                analysis.strengths(),
                gitRuleEvaluator.strengths(gitAnalysis),
                gitignoreRuleEvaluator.strengths(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.strengths(ciAnalysis),
                testRuleEvaluator.strengths(testAnalysis)
        );
        List<RuleFinding> improvements = concatDedupedByRuleId(
                analysis.improvements(),
                gitRuleEvaluator.improvements(gitAnalysis),
                gitignoreRuleEvaluator.improvements(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.improvements(ciAnalysis),
                testRuleEvaluator.improvements(testAnalysis, analysis.projectTypes())
        );
        List<RuleFinding> riskFlags = concatDedupedByRuleId(
                analysis.riskFlags(),
                gitRuleEvaluator.riskFlags(gitAnalysis),
                gitignoreRuleEvaluator.riskFlags(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.riskFlags(ciAnalysis),
                testRuleEvaluator.riskFlags(testAnalysis, analysis.projectTypes(), analysis.technologies(), ciAnalysis)
        );
        List<RuleFinding> findings = concat(strengths, improvements, riskFlags);

        ProjectHealthAssessment health = healthScoringService.assess(
                strengths,
                improvements,
                riskFlags,
                analysis.dependencyHealthAssessments()
        );

        return new ProjectWorkspace(
                analysis.name(),
                analysis.path(),
                analysis.detectedFiles(),
                analysis.projectTypes(),
                analysis.technologies(),
                findings,
                strengths,
                improvements,
                riskFlags,
                analysis.dependencyFindings(),
                analysis.dependencyHealthAssessments(),
                modules,
                health,
                gitAnalysis,
                gitignoreAnalysis,
                ciAnalysis,
                testAnalysis
        );
    }

    // Workspace container with no root-level project evidence; exists only to group modules.
    private ProjectWorkspace shellWorkspace(Path path, List<ProjectModule> modules) {
        GitAnalysis gitAnalysis = gitMetadataReader.read(path);
        GitignoreAnalysis gitignoreAnalysis = gitignoreReader.read(path, List.of());
        CiAnalysis ciAnalysis = ciWorkflowReader.read(path);
        TestAnalysis testAnalysis = testAnalysisReader.read(path);

        List<RuleFinding> strengths = concatDedupedByRuleId(
                gitRuleEvaluator.strengths(gitAnalysis),
                gitignoreRuleEvaluator.strengths(gitignoreAnalysis, List.of()),
                ciRuleEvaluator.strengths(ciAnalysis),
                testRuleEvaluator.strengths(testAnalysis)
        );
        List<RuleFinding> improvements = concatDedupedByRuleId(
                gitRuleEvaluator.improvements(gitAnalysis),
                gitignoreRuleEvaluator.improvements(gitignoreAnalysis, List.of()),
                ciRuleEvaluator.improvements(ciAnalysis),
                testRuleEvaluator.improvements(testAnalysis, List.of())
        );
        List<RuleFinding> riskFlags = concatDedupedByRuleId(
                gitRuleEvaluator.riskFlags(gitAnalysis),
                gitignoreRuleEvaluator.riskFlags(gitignoreAnalysis, List.of()),
                ciRuleEvaluator.riskFlags(ciAnalysis),
                testRuleEvaluator.riskFlags(testAnalysis, List.of(), List.of(), ciAnalysis)
        );
        List<RuleFinding> findings = concat(strengths, improvements, riskFlags);

        ProjectHealthAssessment health = healthScoringService.assess(
                strengths, improvements, riskFlags, List.of()
        );

        return new ProjectWorkspace(
                path.getFileName().toString(),
                path.toAbsolutePath().normalize().toString(),
                List.of(),
                List.of(),
                List.of(),
                findings,
                strengths,
                improvements,
                riskFlags,
                List.of(),
                List.of(),
                modules,
                health,
                gitAnalysis,
                gitignoreAnalysis,
                ciAnalysis,
                testAnalysis
        );
    }

    private ProjectModule toModule(ProjectAnalysis analysis, Path modulePath) {
        GitAnalysis gitAnalysis = gitMetadataReader.read(modulePath);
        GitignoreAnalysis gitignoreAnalysis = gitignoreReader.read(modulePath, analysis.projectTypes());
        CiAnalysis ciAnalysis = ciWorkflowReader.read(modulePath);
        TestAnalysis testAnalysis = testAnalysisReader.read(modulePath);

        List<RuleFinding> strengths = concatDedupedByRuleId(
                analysis.strengths(),
                gitRuleEvaluator.strengths(gitAnalysis),
                gitignoreRuleEvaluator.strengths(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.strengths(ciAnalysis),
                testRuleEvaluator.strengths(testAnalysis)
        );
        List<RuleFinding> improvements = concatDedupedByRuleId(
                analysis.improvements(),
                gitRuleEvaluator.improvements(gitAnalysis),
                gitignoreRuleEvaluator.improvements(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.improvements(ciAnalysis),
                testRuleEvaluator.improvements(testAnalysis, analysis.projectTypes())
        );
        List<RuleFinding> riskFlags = concatDedupedByRuleId(
                analysis.riskFlags(),
                gitRuleEvaluator.riskFlags(gitAnalysis),
                gitignoreRuleEvaluator.riskFlags(gitignoreAnalysis, analysis.projectTypes()),
                ciRuleEvaluator.riskFlags(ciAnalysis),
                testRuleEvaluator.riskFlags(testAnalysis, analysis.projectTypes(), analysis.technologies(), ciAnalysis)
        );
        List<RuleFinding> findings = concat(strengths, improvements, riskFlags);

        ProjectHealthAssessment health = healthScoringService.assess(
                strengths,
                improvements,
                riskFlags,
                analysis.dependencyHealthAssessments()
        );

        return new ProjectModule(
                analysis.name(),
                analysis.path(),
                analysis.detectedFiles(),
                analysis.projectTypes(),
                analysis.technologies(),
                findings,
                strengths,
                improvements,
                riskFlags,
                analysis.dependencyFindings(),
                analysis.dependencyHealthAssessments(),
                health,
                gitAnalysis,
                gitignoreAnalysis,
                ciAnalysis,
                testAnalysis
        );
    }

    private List<Path> listChildDirectories(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private boolean isModuleFolder(Path path) {
        return MODULE_FOLDER_NAMES.contains(path.getFileName().toString().toLowerCase());
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        int total = 0;
        for (List<T> list : lists) total += list.size();
        if (total == 0) return List.of();
        List<T> result = new ArrayList<>(total);
        for (List<T> list : lists) result.addAll(list);
        return List.copyOf(result);
    }

    @SafeVarargs
    private static List<RuleFinding> concatDedupedByRuleId(List<RuleFinding>... lists) {
        LinkedHashMap<String, RuleFinding> byRuleId = new LinkedHashMap<>();
        for (List<RuleFinding> list : lists) {
            for (RuleFinding finding : list) {
                byRuleId.putIfAbsent(finding.ruleId(), finding);
            }
        }
        return List.copyOf(byRuleId.values());
    }

    private record WorkspaceCandidate(Path path, boolean included, boolean forceInclude) {
    }

    private record GenericSignals(List<String> detectedFiles, List<String> compiledBinaryFiles) {
        private boolean hasCppSignals() {
            return detectedFiles.stream()
                    .map(file -> file.toLowerCase(Locale.ROOT))
                    .anyMatch(file -> hasCppSuffix(file));
        }

        private boolean hasCompiledBinaries() {
            return !compiledBinaryFiles.isEmpty();
        }

        private boolean hasCppSuffix(String file) {
            return file.endsWith(".cpp") || file.endsWith(".c") || file.endsWith(".h") || file.endsWith(".hpp");
        }
    }
}
