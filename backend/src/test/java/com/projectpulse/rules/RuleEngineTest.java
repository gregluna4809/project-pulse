package com.projectpulse.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.DependencyFinding;
import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.ProjectType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final RuleEngine ruleEngine = new RuleEngine();

    @Test
    void evaluateAddsStrengthsForDetectedEngineeringPractices() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "api",
                "C:\\projects\\api",
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT, ProjectType.DOCKERIZED, ProjectType.DOCKER_COMPOSE, ProjectType.GITHUB_ACTIONS, ProjectType.GIT_REPOSITORY),
                List.of(".env.example", ".github/workflows", ".git", ".gitignore", "Dockerfile", "README.md", "docker-compose.yml", "pom.xml", "src/test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Maven", "Spring Boot", "Docker", "Docker Compose", "GitHub Actions"),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.strengths())
                .extracting(strength -> strength.ruleId())
                .contains(
                        "git-repository-detected",
                        "readme-detected",
                        "dockerfile-detected",
                        "docker-compose-detected",
                        "github-actions-detected",
                        "env-example-detected",
                        "maven-project-detected"
                );
        assertThat(evaluated.improvements()).isEmpty();
        assertThat(evaluated.riskFlags()).isEmpty();
        assertThat(evaluated.findings()).hasSize(evaluated.strengths().size());
    }

    @Test
    void evaluateAddsImprovementsAndRisksFromMissingOrRiskyEvidence() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "node-api",
                "C:\\projects\\node-api",
                List.of(ProjectType.NODE),
                List.of(".env", "package.json"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Node.js", "Express"),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .contains(
                        "missing-readme",
                        "missing-gitignore",
                        "missing-env-example",
                        "missing-ci-workflow",
                        "missing-backend-dockerfile"
                );
        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .doesNotContain("missing-backend-docker-compose");
        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .contains("missing-package-lock")
                .doesNotContain("env-file-present");
        assertThat(evaluated.findings())
                .hasSize(evaluated.strengths().size() + evaluated.improvements().size() + evaluated.riskFlags().size());
    }

    @Test
    void evaluateRecommendsDockerComposeOnlyWhenBackendProjectHasDatabaseEvidence() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "api",
                "C:\\projects\\api",
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT),
                List.of("pom.xml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Maven", "Spring Boot", "PostgreSQL"),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .contains("missing-backend-docker-compose");
    }

    @Test
    void evaluateFlagsConfigurationExampleAndManifestRisks() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "repo-shell",
                "C:\\projects\\repo-shell",
                List.of(ProjectType.GIT_REPOSITORY),
                List.of(".git", "application.properties", "application.yml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .contains(
                        "application-properties-without-example",
                        "application-yml-without-example",
                        "missing-build-or-dependency-manifest"
                );
    }

    @Test
    void evaluateFlagsMissingRequirementsForPyprojectPythonProject() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "python-tool",
                "C:\\projects\\python-tool",
                List.of(ProjectType.PYTHON),
                List.of("pyproject.toml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Python"),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .contains("missing-python-requirements");
    }

    @Test
    void evaluateFlagsMissingManifestForSourceOnlyPythonProject() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "flask-service",
                "C:\\projects\\flask-service",
                List.of(ProjectType.PYTHON),
                List.of("python-source-files"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Python"),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .contains("missing-python-requirements", "missing-build-or-dependency-manifest");
    }

    @Test
    void evaluateAsModuleDoesNotFlagWorkspaceAssetImprovementsWhenWorkspaceProvidesAll() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "backend",
                "C:\\projects\\dataforge\\backend",
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT),
                List.of("pom.xml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Maven", "Spring Boot"),
                List.of()
        );
        WorkspaceAssets workspaceAssets = new WorkspaceAssets(true, true, true, true);

        ProjectAnalysis evaluated = ruleEngine.evaluateAsModule(analysis, workspaceAssets);

        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .doesNotContain("missing-readme", "missing-gitignore", "missing-ci-workflow", "missing-env-example");
        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .contains("missing-backend-dockerfile");
    }

    @Test
    void evaluateAsModuleFlagsWorkspaceAssetImprovementsWhenWorkspaceProvidesNone() {
        ProjectAnalysis analysis = new ProjectAnalysis(
                "backend",
                "C:\\projects\\dataforge\\backend",
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT),
                List.of("pom.xml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Maven", "Spring Boot"),
                List.of()
        );
        WorkspaceAssets workspaceAssets = WorkspaceAssets.none();

        ProjectAnalysis evaluated = ruleEngine.evaluateAsModule(analysis, workspaceAssets);

        assertThat(evaluated.improvements())
                .extracting(improvement -> improvement.ruleId())
                .contains("missing-readme", "missing-gitignore", "missing-env-example",
                        "missing-ci-workflow", "missing-backend-dockerfile");
    }

    @Test
    void evaluateDoesNotFlagEnvWhenGitignoreExcludesEnvFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path project = java.nio.file.Files.createDirectory(tempDir.resolve("api"));
        java.nio.file.Files.createDirectory(project.resolve(".git"));
        java.nio.file.Files.writeString(project.resolve(".env"), "SECRET=value");
        java.nio.file.Files.writeString(project.resolve(".gitignore"), ".env");

        ProjectAnalysis analysis = new ProjectAnalysis(
                "api",
                project.toString(),
                List.of(ProjectType.GIT_REPOSITORY),
                List.of(".env", ".git", ".gitignore"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .doesNotContain("env-file-present");
    }

    @Test
    void evaluateFlagsEnvWhenGitRepoDoesNotIgnoreEnvFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        java.nio.file.Path project = java.nio.file.Files.createDirectory(tempDir.resolve("api"));
        java.nio.file.Files.createDirectory(project.resolve(".git"));
        java.nio.file.Files.writeString(project.resolve(".env"), "SECRET=value");
        java.nio.file.Files.writeString(project.resolve(".gitignore"), "target/");

        ProjectAnalysis analysis = new ProjectAnalysis(
                "api",
                project.toString(),
                List.of(ProjectType.GIT_REPOSITORY),
                List.of(".env", ".git", ".gitignore"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        ProjectAnalysis evaluated = ruleEngine.evaluate(analysis);

        assertThat(evaluated.riskFlags())
                .extracting(risk -> risk.ruleId())
                .contains("env-file-present");
    }
}
