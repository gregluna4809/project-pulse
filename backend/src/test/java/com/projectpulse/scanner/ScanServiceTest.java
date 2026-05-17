package com.projectpulse.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectpulse.detector.DependencyHealthEvaluator;
import com.projectpulse.detector.DependencyParser;
import com.projectpulse.detector.ProjectDetector;
import com.projectpulse.git.GitActivityStatus;
import com.projectpulse.git.GitMetadataReader;
import com.projectpulse.git.GitRuleEvaluator;
import com.projectpulse.ci.CiRuleEvaluator;
import com.projectpulse.ci.CiWorkflowReader;
import com.projectpulse.gitignore.GitignoreReader;
import com.projectpulse.gitignore.GitignoreRuleEvaluator;
import com.projectpulse.model.DependencyFinding;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.HealthTier;
import com.projectpulse.model.ProjectModule;
import com.projectpulse.model.ProjectType;
import com.projectpulse.model.ProjectWorkspace;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.ScanRequest;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.WorkspaceDiscoveryResponse;
import com.projectpulse.rules.RuleEngine;
import com.projectpulse.scoring.HealthScoringService;
import com.projectpulse.tests.TestAnalysisReader;
import com.projectpulse.tests.TestRuleEvaluator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanServiceTest {

    @TempDir
    Path tempDir;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new ScanService(
                new ProjectDetector(new DependencyParser(), new DependencyHealthEvaluator()),
                new RuleEngine(),
                new HealthScoringService(),
                new GitMetadataReader(),
                new GitRuleEvaluator(),
                new GitignoreReader(),
                new GitignoreRuleEvaluator(),
                new CiWorkflowReader(),
                new CiRuleEvaluator(),
                new TestAnalysisReader(),
                new TestRuleEvaluator()
        );
    }

    @Test
    void scanReturnsStandaloneWorkspacesForImmediateChildProjectsWithEvidenceFiles() throws IOException {
        Path springProject = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(springProject.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");

        Path reactProject = Files.createDirectory(tempDir.resolve("react-ui"));
        Files.writeString(reactProject.resolve("package.json"), "{\"dependencies\":{\"react\":\"latest\"}}");

        // notes has a nested-project child, but "nested-project" is not a recognised module folder name.
        // notes itself has no project markers, so it produces no workspace.
        Path plainFolder = Files.createDirectory(tempDir.resolve("notes"));
        Files.createDirectory(plainFolder.resolve("nested-project"));
        Files.writeString(plainFolder.resolve("nested-project").resolve("package.json"), "{}");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(2);
        assertThat(response.workspaces())
                .extracting(ProjectWorkspace::name)
                .containsExactly("react-ui", "spring-api");

        ProjectWorkspace reactWorkspace = response.workspaces().getFirst();
        assertThat(reactWorkspace.modules()).isEmpty();
        assertThat(reactWorkspace.projectTypes()).containsExactly(ProjectType.NODE, ProjectType.REACT);
        assertThat(reactWorkspace.technologies()).containsExactly("Node.js", "React");
        assertThat(reactWorkspace.riskFlags())
                .extracting(RuleFinding::ruleId)
                .contains("missing-package-lock");

        ProjectWorkspace springWorkspace = response.workspaces().get(1);
        assertThat(springWorkspace.modules()).isEmpty();
        assertThat(springWorkspace.projectTypes()).containsExactly(ProjectType.MAVEN, ProjectType.SPRING_BOOT);
        assertThat(springWorkspace.technologies()).containsExactly("Maven", "Spring Boot");
        assertThat(springWorkspace.improvements())
                .extracting(RuleFinding::ruleId)
                .contains("missing-readme", "no-test-files-detected", "missing-backend-dockerfile")
                .doesNotContain("missing-backend-docker-compose");
    }

    @Test
    void scanEmitsShellWorkspaceContainingModuleForTargetNestedFolder() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pyproject.toml"), "[project]\nname = \"api\"");

        // tools has package.json but is not a recognised module folder name — excluded.
        Path ignoredFolder = Files.createDirectory(workspace.resolve("tools"));
        Files.writeString(ignoredFolder.resolve("package.json"), "{}");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(1);
        ProjectWorkspace result = response.workspaces().getFirst();
        assertThat(result.name()).isEqualTo("workspace");
        assertThat(result.modules())
                .extracting(ProjectModule::path)
                .contains(backend.toAbsolutePath().normalize().toString())
                .doesNotContain(ignoredFolder.toAbsolutePath().normalize().toString());
        assertThat(result.modules())
                .filteredOn(m -> m.path().equals(backend.toAbsolutePath().normalize().toString()))
                .singleElement()
                .satisfies(m -> assertThat(m.technologies()).contains("Python"));
    }

    @Test
    void scanDetectsNestedBackendAndFrontendAsModulesUnderParentWorkspace() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("dataforge"));

        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"),
                "<dependencies><dependency><artifactId>spring-boot-starter-web</artifactId></dependency></dependencies>");
        Files.createDirectories(backend.resolve("src/test/java"));
        Path resources = Files.createDirectories(backend.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.yml"),
                "spring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/db");

        Path frontend = Files.createDirectory(workspace.resolve("frontend"));
        Files.writeString(frontend.resolve("package.json"), "{\"dependencies\":{\"react\":\"^18.0.0\"}}");
        Files.writeString(frontend.resolve("package-lock.json"), "{}");
        Files.writeString(frontend.resolve(".env"), "REACT_APP_API_URL=http://localhost:8080");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(1);
        ProjectWorkspace dataforge = response.workspaces().getFirst();
        assertThat(dataforge.name()).isEqualTo("dataforge");
        assertThat(dataforge.modules()).hasSize(2);
        assertThat(dataforge.modules())
                .extracting(ProjectModule::name)
                .containsExactlyInAnyOrder("backend", "frontend");

        ProjectModule backendModule = findModule(dataforge, "backend");
        assertThat(backendModule.projectTypes()).contains(ProjectType.MAVEN, ProjectType.SPRING_BOOT);
        assertThat(backendModule.detectedFiles())
                .contains("pom.xml", "src/test", "src/main/resources/application.yml");
        assertThat(backendModule.technologies()).contains("Maven", "Spring Boot", "PostgreSQL");

        ProjectModule frontendModule = findModule(dataforge, "frontend");
        assertThat(frontendModule.projectTypes()).contains(ProjectType.NODE, ProjectType.REACT);
        assertThat(frontendModule.detectedFiles()).contains("package.json", "package-lock.json", ".env");
    }

    @Test
    void scanModulesInheritWorkspaceAssetsAndSuppressRedundantImprovements() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("dataforge"));
        Files.writeString(workspace.resolve("README.md"), "# DataForge");
        Files.writeString(workspace.resolve(".gitignore"), "target/");
        Files.createDirectories(workspace.resolve(".github/workflows"));
        Files.writeString(workspace.resolve(".env.example"), "DB_URL=");

        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"),
                "<dependencies><dependency><artifactId>spring-boot-starter-web</artifactId></dependency></dependencies>");

        Path frontend = Files.createDirectory(workspace.resolve("frontend"));
        Files.writeString(frontend.resolve("package.json"), "{\"dependencies\":{\"react\":\"^18.0.0\"}}");
        Files.writeString(frontend.resolve("package-lock.json"), "{}");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(1);
        ProjectWorkspace dataforge = response.workspaces().getFirst();
        assertThat(dataforge.name()).isEqualTo("dataforge");
        assertThat(dataforge.modules()).hasSize(2);

        ProjectModule backendModule = findModule(dataforge, "backend");
        assertThat(backendModule.improvements())
                .extracting(RuleFinding::ruleId)
                .doesNotContain("missing-readme", "missing-gitignore", "missing-ci-workflow", "missing-env-example");

        ProjectModule frontendModule = findModule(dataforge, "frontend");
        assertThat(frontendModule.improvements())
                .extracting(RuleFinding::ruleId)
                .doesNotContain("missing-readme", "missing-gitignore", "missing-ci-workflow", "missing-env-example");
    }

    @Test
    void scanStandaloneWorkspacesEvaluateAllImprovementsUnaffectedByInheritance() throws IOException {
        Path springProject = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(springProject.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(1);
        ProjectWorkspace workspace = response.workspaces().getFirst();
        assertThat(workspace.modules()).isEmpty();
        assertThat(workspace.improvements())
                .extracting(RuleFinding::ruleId)
                .contains("missing-readme", "no-test-files-detected", "missing-gitignore", "missing-env-example",
                        "missing-ci-workflow", "missing-backend-dockerfile");
    }

    @Test
    void scanModulesStillFlagMissingReadmeWhenWorkspaceLacksOne() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("noreadme"));

        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"),
                "<dependencies><dependency><artifactId>spring-boot-starter-web</artifactId></dependency></dependencies>");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspacesFound()).isEqualTo(1);
        ProjectWorkspace result = response.workspaces().getFirst();
        ProjectModule backendModule = findModule(result, "backend");
        assertThat(backendModule.improvements())
                .extracting(RuleFinding::ruleId)
                .contains("missing-readme");
    }

    @Test
    void scanIncludesDependencyFindingsOnStandaloneWorkspace() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.5</version>
                    </parent>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace workspace = response.workspaces().getFirst();
        assertThat(workspace.dependencyFindings())
                .extracting(DependencyFinding::name)
                .contains("spring-boot-starter-parent", "java", "spring-boot-starter-web", "postgresql");

        DependencyFinding parentFinding = workspace.dependencyFindings().stream()
                .filter(f -> f.name().equals("spring-boot-starter-parent"))
                .findFirst().orElseThrow();
        assertThat(parentFinding.version()).isEqualTo("3.3.5");
        assertThat(parentFinding.scope()).isEqualTo("parent");
        assertThat(parentFinding.ecosystem()).isEqualTo("Maven");

        DependencyFinding webStarter = workspace.dependencyFindings().stream()
                .filter(f -> f.name().equals("spring-boot-starter-web"))
                .findFirst().orElseThrow();
        assertThat(webStarter.version()).isEmpty();
        assertThat(webStarter.notes()).isEqualTo("version managed by parent");
        assertThat(webStarter.scope()).isEqualTo("compile");

        DependencyFinding postgresql = workspace.dependencyFindings().stream()
                .filter(f -> f.name().equals("postgresql"))
                .findFirst().orElseThrow();
        assertThat(postgresql.scope()).isEqualTo("runtime");

        assertThat(workspace.dependencyHealthAssessments())
                .extracting(DependencyHealthAssessment::dependencyName)
                .contains("spring-boot-starter-parent", "java");

        DependencyHealthAssessment springBootHealth = workspace.dependencyHealthAssessments().stream()
                .filter(a -> a.dependencyName().equals("spring-boot-starter-parent"))
                .findFirst().orElseThrow();
        assertThat(springBootHealth.healthStatus()).isEqualTo(HealthStatus.ACCEPTABLE);
        assertThat(springBootHealth.detectedVersion()).isEqualTo("3.3.5");

        DependencyHealthAssessment javaHealth = workspace.dependencyHealthAssessments().stream()
                .filter(a -> a.dependencyName().equals("java"))
                .findFirst().orElseThrow();
        assertThat(javaHealth.healthStatus()).isEqualTo(HealthStatus.CURRENT);
    }

    @Test
    void scanIncludesDependencyFindingsOnModules() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("dataforge"));

        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.3.5</version>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);

        Path frontend = Files.createDirectory(workspace.resolve("frontend"));
        Files.writeString(frontend.resolve("package.json"), """
                {
                    "dependencies": {"react": "^18.2.0", "axios": "^1.6.0"},
                    "devDependencies": {"typescript": "^5.3.0", "vite": "^5.0.0"}
                }
                """);
        Files.writeString(frontend.resolve("package-lock.json"), "{}");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace dataforge = response.workspaces().getFirst();

        ProjectModule backendModule = findModule(dataforge, "backend");
        assertThat(backendModule.dependencyFindings())
                .extracting(DependencyFinding::name)
                .contains("spring-boot-starter-parent", "spring-boot-starter-data-jpa", "lombok");

        ProjectModule frontendModule = findModule(dataforge, "frontend");
        assertThat(frontendModule.dependencyFindings())
                .extracting(DependencyFinding::name)
                .contains("react", "axios", "typescript", "vite");

        DependencyFinding reactFinding = frontendModule.dependencyFindings().stream()
                .filter(f -> f.name().equals("react"))
                .findFirst().orElseThrow();
        assertThat(reactFinding.version()).isEqualTo("^18.2.0");
        assertThat(reactFinding.scope()).isEqualTo("dependencies");
        assertThat(reactFinding.ecosystem()).isEqualTo("npm");

        DependencyFinding viteFinding = frontendModule.dependencyFindings().stream()
                .filter(f -> f.name().equals("vite"))
                .findFirst().orElseThrow();
        assertThat(viteFinding.scope()).isEqualTo("devDependencies");

        DependencyHealthAssessment viteHealth = frontendModule.dependencyHealthAssessments().stream()
                .filter(a -> a.dependencyName().equals("vite"))
                .findFirst().orElseThrow();
        assertThat(viteHealth.healthStatus()).isEqualTo(HealthStatus.UPGRADE_CANDIDATE);
    }

    @Test
    void scanAttachesHealthAssessmentToStandaloneWorkspace() throws IOException {
        // Minimal Spring Boot project: no README, tests, gitignore, env-example, CI, or Dockerfile.
        // pom.xml triggers maven-project-detected strength (+3); 6 improvements (-36) → score = 67 → FAIR.
        Path project = Files.createDirectory(tempDir.resolve("bare-spring-api"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace workspace = response.workspaces().getFirst();
        assertThat(workspace.healthAssessment()).isNotNull();
        assertThat(workspace.healthAssessment().score()).isBetween(0, 100);
        assertThat(workspace.healthAssessment().tier()).isNotNull();
        assertThat(workspace.healthAssessment().summary()).isNotBlank();
        assertThat(workspace.healthAssessment().tier()).isEqualTo(HealthTier.AT_RISK);
        assertThat(workspace.healthAssessment().score()).isEqualTo(46);
    }

    @Test
    void scanAttachesHealthAssessmentToModules() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("myapp"));
        Path backend = Files.createDirectory(workspace.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace result = response.workspaces().getFirst();
        ProjectModule backendModule = findModule(result, "backend");
        assertThat(backendModule.healthAssessment()).isNotNull();
        assertThat(backendModule.healthAssessment().score()).isBetween(0, 100);
        assertThat(backendModule.healthAssessment().tier()).isNotNull();
        assertThat(backendModule.healthAssessment().summary()).isNotBlank();
    }

    @Test
    void scanAttachesGitAnalysisToWorkspaceAndMergesGitFindings() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("git-project"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>test</artifactId>");

        // Minimal .git structure: HEAD pointing to main, one remote, one loose branch.
        Path gitDir = Files.createDirectory(project.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        Files.writeString(gitDir.resolve("config"), """
                [core]
                    repositoryformatversion = 0
                [remote "origin"]
                    url = https://github.com/example/git-project.git
                    fetch = +refs/heads/*:refs/remotes/origin/*
                """);
        Path headsDir = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
        Files.writeString(headsDir.resolve("main"), "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2\n");

        // Write a reflog entry for today so the repo is considered active.
        Path logsDir = Files.createDirectories(gitDir.resolve("logs"));
        long nowEpoch = java.time.Instant.now().getEpochSecond();
        Files.writeString(logsDir.resolve("HEAD"),
                "0000000000000000000000000000000000000000 a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
                        + " Dev User <dev@example.com> " + nowEpoch + " +0000\tcommit (initial): init\n");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace workspace = response.workspaces().getFirst();

        // Git analysis should be present and correct.
        assertThat(workspace.gitAnalysis().gitRepository()).isTrue();
        assertThat(workspace.gitAnalysis().currentBranch()).isEqualTo("main");
        assertThat(workspace.gitAnalysis().hasRemote()).isTrue();
        assertThat(workspace.gitAnalysis().remoteNames()).containsExactly("origin");
        assertThat(workspace.gitAnalysis().branchCount()).isEqualTo(1);
        assertThat(workspace.gitAnalysis().detachedHead()).isFalse();
        assertThat(workspace.gitAnalysis().activityStatus()).isEqualTo(GitActivityStatus.ACTIVE);

        // Git strength findings should be merged into workspace findings.
        assertThat(workspace.strengths())
                .extracting(RuleFinding::ruleId)
                .contains("git-repository-detected", "git-remote-configured", "git-recent-activity");
    }

    @Test
    void scanDeduplicatesDuplicateGitStrengthsByRuleId() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("git-project"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>test</artifactId>");
        Path gitDir = Files.createDirectory(project.resolve(".git"));
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        List<String> strengthRuleIds = response.workspaces().getFirst().strengths().stream()
                .map(RuleFinding::ruleId)
                .toList();
        assertThat(strengthRuleIds.stream().filter("git-repository-detected"::equals).count()).isEqualTo(1);
    }

    @Test
    void scanDeduplicatesDuplicateRisksByRuleId() throws IOException {
        ScanService service = scanServiceWithGitRuleEvaluator(new GitRuleEvaluator() {
            @Override
            public List<RuleFinding> riskFlags(com.projectpulse.git.GitAnalysis git) {
                return List.of(
                        new RuleFinding("duplicate-risk", "First duplicate risk.", "first"),
                        new RuleFinding("stable-second-risk", "Second risk.", "second"),
                        new RuleFinding("duplicate-risk", "Second duplicate risk.", "third")
                );
            }
        });
        createMavenWorkspace("risk-project");

        ScanResponse response = service.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspaces().getFirst().riskFlags())
                .extracting(RuleFinding::ruleId)
                .containsExactly("duplicate-risk", "stable-second-risk");
    }

    @Test
    void scanDeduplicationKeepsFirstOccurrenceOrderStable() throws IOException {
        ScanService service = scanServiceWithGitRuleEvaluator(new GitRuleEvaluator() {
            @Override
            public List<RuleFinding> strengths(com.projectpulse.git.GitAnalysis git) {
                return List.of(
                        new RuleFinding("duplicate-strength", "First duplicate strength.", "first"),
                        new RuleFinding("stable-second-strength", "Second strength.", "second"),
                        new RuleFinding("duplicate-strength", "Second duplicate strength.", "third")
                );
            }
        });
        createMavenWorkspace("stable-project");

        ScanResponse response = service.scan(new ScanRequest(tempDir.toString()));

        assertThat(response.workspaces().getFirst().strengths())
                .extracting(RuleFinding::ruleId)
                .containsExactly("maven-project-detected", "duplicate-strength", "stable-second-strength");
    }

    @Test
    void scanWorksaceWithNoGitDirectoryHasNoGitFindings() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("no-git-project"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>test</artifactId>");

        ScanResponse response = scanService.scan(new ScanRequest(tempDir.toString()));

        ProjectWorkspace workspace = response.workspaces().getFirst();
        assertThat(workspace.gitAnalysis().gitRepository()).isFalse();
        assertThat(workspace.strengths())
                .extracting(RuleFinding::ruleId)
                .doesNotContain("git-repository-detected", "git-remote-configured");
    }

    @Test
    void discoverReturnsImmediateDirectoriesOnly() throws IOException {
        Path alpha = Files.createDirectory(tempDir.resolve("alpha"));
        Path beta = Files.createDirectory(tempDir.resolve("beta"));
        Files.createDirectory(alpha.resolve("nested"));
        Files.writeString(tempDir.resolve("notes.txt"), "not a workspace");

        WorkspaceDiscoveryResponse response = scanService.discover(tempDir.toString());

        assertThat(response.rootPath()).isEqualTo(tempDir.toAbsolutePath().normalize().toString());
        assertThat(response.workspacesFound()).isEqualTo(2);
        assertThat(response.workspaces())
                .extracting("name")
                .containsExactly("alpha", "beta");
        assertThat(response.workspaces())
                .extracting("path")
                .containsExactly(
                        alpha.toAbsolutePath().normalize().toString(),
                        beta.toAbsolutePath().normalize().toString()
                );
    }

    @Test
    void scanIncludeProjectNamesScansOnlySelectedWorkspace() throws IOException {
        createMavenWorkspace("alpha");
        createMavenWorkspace("beta");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("beta"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.workspaces()).extracting(ProjectWorkspace::name).containsExactly("beta");
    }

    @Test
    void scanExcludeProjectNamesSkipsWorkspace() throws IOException {
        createMavenWorkspace("alpha");
        createMavenWorkspace("beta");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of(),
                List.of("alpha"),
                List.of(),
                List.of()
        ));

        assertThat(response.workspaces()).extracting(ProjectWorkspace::name).containsExactly("beta");
    }

    @Test
    void scanIncludePathsScansOnlySelectedWorkspace() throws IOException {
        Path alpha = createMavenWorkspace("alpha");
        Path beta = createMavenWorkspace("beta");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of(),
                List.of(),
                List.of(beta.toString()),
                List.of()
        ));

        assertThat(response.workspaces()).extracting(ProjectWorkspace::path)
                .containsExactly(beta.toAbsolutePath().normalize().toString())
                .doesNotContain(alpha.toAbsolutePath().normalize().toString());
    }

    @Test
    void scanExcludePathsSkipsWorkspace() throws IOException {
        Path alpha = createMavenWorkspace("alpha");
        Path beta = createMavenWorkspace("beta");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of(),
                List.of(),
                List.of(),
                List.of(alpha.toString())
        ));

        assertThat(response.workspaces()).extracting(ProjectWorkspace::path)
                .containsExactly(beta.toAbsolutePath().normalize().toString());
    }

    @Test
    void scanIgnoresPathOutsideRootSafely() throws IOException {
        createMavenWorkspace("inside");
        Path outside = Files.createTempDirectory("projectpulse-outside");
        try {
            Files.writeString(outside.resolve("pom.xml"), "<artifactId>outside</artifactId>");

            ScanResponse response = scanService.scan(new ScanRequest(
                    tempDir.toString(),
                    List.of(),
                    List.of(),
                    List.of(outside.toString()),
                    List.of()
            ));

            assertThat(response.workspaces()).isEmpty();
        } finally {
            Files.deleteIfExists(outside.resolve("pom.xml"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void scanScopeMatchingIsCaseInsensitive() throws IOException {
        createMavenWorkspace("ProjectPulse");
        createMavenWorkspace("other");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("projectpulse"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.workspaces()).extracting(ProjectWorkspace::name).containsExactly("ProjectPulse");
    }

    @Test
    void scanIncludesSelectedUnknownWorkspaceAsGenericFallback() throws IOException {
        Path unknown = Files.createDirectory(tempDir.resolve("cpp-test"));

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("cpp-test"),
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(response.workspaces()).hasSize(1);
        ProjectWorkspace workspace = response.workspaces().getFirst();
        assertThat(workspace.name()).isEqualTo("cpp-test");
        assertThat(workspace.path()).isEqualTo(unknown.toAbsolutePath().normalize().toString());
        assertThat(workspace.projectTypes()).contains(ProjectType.UNKNOWN, ProjectType.GENERIC, ProjectType.AD_HOC);
        assertThat(workspace.technologies()).containsExactly("Unknown");
        assertThat(workspace.improvements())
                .extracting(RuleFinding::message)
                .contains("No recognized build manifest detected.");
    }

    @Test
    void scanReturnsGenericCppWorkspaceForCppOnlySelectedFolder() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("cpp-test"));
        Path src = Files.createDirectory(workspace.resolve("src"));
        Files.writeString(src.resolve("main.cpp"), "int main() { return 0; }");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of(),
                List.of(),
                List.of(workspace.toString()),
                List.of()
        ));

        ProjectWorkspace result = response.workspaces().getFirst();
        assertThat(result.projectTypes()).contains(ProjectType.UNKNOWN, ProjectType.GENERIC, ProjectType.AD_HOC, ProjectType.CPP);
        assertThat(result.technologies()).containsExactly("C++");
        assertThat(result.detectedFiles()).contains("src/", Path.of("src", "main.cpp").toString());
    }

    @Test
    void scanGenericFallbackFlagsCompiledBinaries() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("binary-drop"));
        Files.writeString(workspace.resolve("tool.exe"), "binary");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("binary-drop"),
                List.of(),
                List.of(),
                List.of()
        ));

        ProjectWorkspace result = response.workspaces().getFirst();
        assertThat(result.riskFlags())
                .extracting(RuleFinding::ruleId)
                .contains("compiled-binaries-detected");
        assertThat(result.riskFlags())
                .extracting(RuleFinding::message)
                .contains("Compiled binaries detected in workspace.");
    }

    @Test
    void scanCppStyleAdHocWorkspaceDoesNotScoreGood() throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve("cpp-test"));
        Files.writeString(workspace.resolve("hello.cpp"), "int main() { return 0; }");
        Files.writeString(workspace.resolve("hello.exe"), "binary");

        ScanResponse response = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("cpp-test"),
                List.of(),
                List.of(),
                List.of()
        ));

        ProjectWorkspace result = response.workspaces().getFirst();
        assertThat(result.healthAssessment().tier()).isEqualTo(HealthTier.AT_RISK);
        assertThat(result.healthAssessment().score()).isLessThan(75);
    }

    @Test
    void scanDetectsJavaTestsAndJUnitFramework() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("java-api"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>java-api</artifactId>");
        Path testDir = Files.createDirectories(project.resolve("src/test/java/com/example"));
        Files.writeString(testDir.resolve("UserServiceTest.java"), """
                import org.junit.jupiter.api.Test;
                class UserServiceTest { @Test void works() {} }
                """);

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.testAnalysis().hasTests()).isTrue();
        assertThat(workspace.testAnalysis().testFileCount()).isEqualTo(1);
        assertThat(workspace.testAnalysis().testDirectories()).contains("src/test");
        assertThat(workspace.testAnalysis().detectedFrameworks()).contains("JUnit");
        assertThat(workspace.strengths()).extracting(RuleFinding::ruleId)
                .contains("automated-tests-detected", "known-test-framework-detected");
    }

    @Test
    void scanDetectsSpringBootTestAndIntegrationTests() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");
        Path testDir = Files.createDirectories(project.resolve("src/test/java/com/example"));
        Files.writeString(testDir.resolve("ApplicationIntegrationTest.java"), """
                import org.springframework.boot.test.context.SpringBootTest;
                @SpringBootTest class ApplicationIntegrationTest {}
                """);

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.testAnalysis().integrationTestFileCount()).isEqualTo(1);
        assertThat(workspace.testAnalysis().detectedFrameworks()).contains("SpringBootTest");
        assertThat(workspace.strengths()).extracting(RuleFinding::ruleId)
                .contains("integration-tests-detected");
    }

    @Test
    void scanDetectsNodeTestScriptAndVitestJestFrameworks() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("react-ui"));
        Files.writeString(project.resolve("package.json"), """
                {
                  "scripts": {"test": "vitest"},
                  "devDependencies": {"vitest": "^1.0.0", "jest": "^29.0.0", "@testing-library/react": "^14.0.0"}
                }
                """);
        Files.writeString(project.resolve("App.test.tsx"), "import { describe } from 'vitest';");

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.testAnalysis().hasTestScript()).isTrue();
        assertThat(workspace.testAnalysis().detectedFrameworks()).contains("vitest", "jest", "testing-library");
        assertThat(workspace.strengths()).extracting(RuleFinding::ruleId)
                .contains("test-script-detected", "known-test-framework-detected");
    }

    @Test
    void scanDetectsPythonPytest() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("python-tool"));
        Files.writeString(project.resolve("pyproject.toml"), "[project]\nname = \"python-tool\"\n[tool.pytest.ini_options]\n");
        Path tests = Files.createDirectory(project.resolve("tests"));
        Files.writeString(tests.resolve("test_app.py"), "import pytest\n");

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.testAnalysis().hasTests()).isTrue();
        assertThat(workspace.testAnalysis().detectedFrameworks()).contains("pytest");
    }

    @Test
    void scanDetectsCppTestFilesAndFrameworks() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("cpp-test"));
        Path tests = Files.createDirectory(project.resolve("tests"));
        Files.writeString(tests.resolve("calculator_test.cpp"), "#include <gtest/gtest.h>\n");

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(
                tempDir.toString(),
                List.of("cpp-test"),
                List.of(),
                List.of(),
                List.of()
        )).workspaces().getFirst();

        assertThat(workspace.testAnalysis().hasTests()).isTrue();
        assertThat(workspace.testAnalysis().testFileCount()).isEqualTo(1);
        assertThat(workspace.testAnalysis().detectedFrameworks()).contains("gtest");
        assertThat(workspace.strengths()).extracting(RuleFinding::ruleId)
                .contains("automated-tests-detected", "known-test-framework-detected");
    }

    @Test
    void scanFlagsCiWorkflowWithNoTestsRisk() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("ci-no-tests"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>ci-no-tests</artifactId>");
        Path workflows = Files.createDirectories(project.resolve(".github/workflows"));
        Files.writeString(workflows.resolve("ci.yml"), "name: CI\non: [push]\njobs:\n  build:\n    runs-on: ubuntu-latest\n");

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.ciAnalysis().hasCi()).isTrue();
        assertThat(workspace.riskFlags()).extracting(RuleFinding::ruleId)
                .contains("ci-without-tests");
    }

    @Test
    void scanFlagsBackendProjectWithNoTestsRisk() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-no-tests"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");

        ProjectWorkspace workspace = scanService.scan(new ScanRequest(tempDir.toString())).workspaces().getFirst();

        assertThat(workspace.riskFlags()).extracting(RuleFinding::ruleId)
                .contains("backend-api-without-tests");
    }

    @Test
    void scanRejectsMissingRootPath() {
        Path missingRoot = tempDir.resolve("missing");

        assertThatThrownBy(() -> scanService.scan(new ScanRequest(missingRoot.toString())))
                .isInstanceOf(InvalidScanRootException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void scanRejectsFileRootPath() throws IOException {
        Path file = Files.writeString(tempDir.resolve("file.txt"), "not a directory");

        assertThatThrownBy(() -> scanService.scan(new ScanRequest(file.toString())))
                .isInstanceOf(InvalidScanRootException.class)
                .hasMessageContaining("must be a directory");
    }

    private ProjectModule findModule(ProjectWorkspace workspace, String name) {
        return workspace.modules().stream()
                .filter(m -> m.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Module '" + name + "' not found in workspace '" + workspace.name() + "'. Modules: "
                                + workspace.modules().stream().map(ProjectModule::name).toList()));
    }

    private Path createMavenWorkspace(String name) throws IOException {
        Path workspace = Files.createDirectory(tempDir.resolve(name));
        Files.writeString(workspace.resolve("pom.xml"), "<artifactId>%s</artifactId>".formatted(name));
        return workspace;
    }

    private ScanService scanServiceWithGitRuleEvaluator(GitRuleEvaluator evaluator) {
        return new ScanService(
                new ProjectDetector(new DependencyParser(), new DependencyHealthEvaluator()),
                new RuleEngine(),
                new HealthScoringService(),
                new GitMetadataReader(),
                evaluator,
                new GitignoreReader(),
                new GitignoreRuleEvaluator(),
                new CiWorkflowReader(),
                new CiRuleEvaluator(),
                new TestAnalysisReader(),
                new TestRuleEvaluator()
        );
    }
}
