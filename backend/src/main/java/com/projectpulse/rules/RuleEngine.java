package com.projectpulse.rules;

import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.ProjectType;
import com.projectpulse.model.RuleFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {

    public ProjectAnalysis evaluate(ProjectAnalysis analysis) {
        List<RuleFinding> strengths = buildStrengths(analysis);
        List<RuleFinding> improvements = buildImprovements(analysis);
        List<RuleFinding> riskFlags = buildRiskFlags(analysis);
        List<RuleFinding> findings = concat(strengths, improvements, riskFlags);

        return new ProjectAnalysis(
                analysis.name(),
                analysis.path(),
                analysis.projectTypes(),
                analysis.detectedFiles(),
                findings,
                strengths,
                improvements,
                riskFlags,
                analysis.technologies(),
                analysis.dependencyFindings(),
                analysis.dependencyHealthAssessments()
        );
    }

    public ProjectAnalysis evaluateAsModule(ProjectAnalysis analysis, WorkspaceAssets workspaceAssets) {
        List<RuleFinding> strengths = buildStrengths(analysis);
        List<RuleFinding> improvements = buildModuleImprovements(analysis, workspaceAssets);
        List<RuleFinding> riskFlags = buildRiskFlags(analysis);
        List<RuleFinding> findings = concat(strengths, improvements, riskFlags);

        return new ProjectAnalysis(
                analysis.name(),
                analysis.path(),
                analysis.projectTypes(),
                analysis.detectedFiles(),
                findings,
                strengths,
                improvements,
                riskFlags,
                analysis.technologies(),
                analysis.dependencyFindings(),
                analysis.dependencyHealthAssessments()
        );
    }

    private List<RuleFinding> buildStrengths(ProjectAnalysis analysis) {
        return List.of(
                        strengthIf(analysis, hasFile(analysis, ".git"), "git-repository-detected", "Git repository detected.", ".git"),
                        strengthIf(analysis, hasAnyFile(analysis, "README.md", "README.txt", "readme.md"), "readme-detected", "README detected.", firstDetected(analysis, "README.md", "README.txt", "readme.md")),
                        strengthIf(analysis, hasFile(analysis, "Dockerfile"), "dockerfile-detected", "Dockerfile detected.", "Dockerfile"),
                        strengthIf(analysis, hasDockerComposeFile(analysis), "docker-compose-detected", "Docker Compose file detected.", firstDetected(analysis, "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml")),
                        strengthIf(analysis, hasFile(analysis, ".github/workflows"), "github-actions-detected", "GitHub Actions workflow detected.", ".github/workflows"),
                        strengthIf(analysis, hasEnvExample(analysis), "env-example-detected", "Environment example file detected.", firstDetected(analysis, ".env.example", ".env.template", "env.example")),
                        strengthIf(analysis, hasFile(analysis, "pom.xml"), "maven-project-detected", "Maven project detected.", "pom.xml"),
                        strengthIf(analysis, hasFile(analysis, "package.json"), "npm-project-detected", "npm project detected.", "package.json"),
                        strengthIf(analysis, hasFile(analysis, "requirements.txt"), "python-requirements-detected", "Python requirements file detected.", "requirements.txt"),
                        strengthIf(analysis, hasFile(analysis, "CMakeLists.txt"), "cmake-project-detected", "CMake project detected.", "CMakeLists.txt")
                )
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<RuleFinding> buildImprovements(ProjectAnalysis analysis) {
        return List.of(
                        improvementIf(!hasAnyFile(analysis, "README.md", "README.txt", "readme.md"), "missing-readme", "README not detected.", "Expected README.md, README.txt, or readme.md in project root."),
                        improvementIf(!hasFile(analysis, ".gitignore"), "missing-gitignore", ".gitignore not detected.", "Expected .gitignore in project root."),
                        improvementIf(!hasEnvExample(analysis), "missing-env-example", "Environment example file not detected.", "Expected .env.example, .env.template, or env.example."),
                        improvementIf(!hasFile(analysis, ".github/workflows"), "missing-ci-workflow", "CI workflow not detected.", "Expected .github/workflows."),
                        improvementIf(isBackendOrApiProject(analysis) && !hasFile(analysis, "Dockerfile"), "missing-backend-dockerfile", "Dockerfile not detected for backend/API project.", "Backend/API evidence: " + backendEvidence(analysis)),
                        improvementIf(isBackendOrApiProject(analysis) && hasDatabaseEvidence(analysis) && !hasDockerComposeFile(analysis), "missing-backend-docker-compose", "Docker Compose file not detected for backend/API project with database evidence.", "Backend/API evidence: " + backendEvidence(analysis) + "; database evidence: " + databaseEvidence(analysis))
                )
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<RuleFinding> buildModuleImprovements(ProjectAnalysis analysis, WorkspaceAssets workspaceAssets) {
        return List.of(
                        improvementIf(!workspaceAssets.hasReadme() && !hasAnyFile(analysis, "README.md", "README.txt", "readme.md"), "missing-readme", "README not detected.", "Expected README.md, README.txt, or readme.md in project root."),
                        improvementIf(!workspaceAssets.hasGitignore() && !hasFile(analysis, ".gitignore"), "missing-gitignore", ".gitignore not detected.", "Expected .gitignore in project root."),
                        improvementIf(!workspaceAssets.hasEnvTemplate() && !hasEnvExample(analysis), "missing-env-example", "Environment example file not detected.", "Expected .env.example, .env.template, or env.example."),
                        improvementIf(!workspaceAssets.hasCiWorkflow() && !hasFile(analysis, ".github/workflows"), "missing-ci-workflow", "CI workflow not detected.", "Expected .github/workflows."),
                        improvementIf(isBackendOrApiProject(analysis) && !hasFile(analysis, "Dockerfile"), "missing-backend-dockerfile", "Dockerfile not detected for backend/API project.", "Backend/API evidence: " + backendEvidence(analysis)),
                        improvementIf(isBackendOrApiProject(analysis) && hasDatabaseEvidence(analysis) && !hasDockerComposeFile(analysis), "missing-backend-docker-compose", "Docker Compose file not detected for backend/API project with database evidence.", "Backend/API evidence: " + backendEvidence(analysis) + "; database evidence: " + databaseEvidence(analysis))
                )
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<RuleFinding> buildRiskFlags(ProjectAnalysis analysis) {
        return List.of(
                        riskIf(isTrackedEnvRisk(analysis), "env-file-present", ".env file present and not excluded by .gitignore.", ".env"),
                        riskIf(hasApplicationPropertiesWithoutExample(analysis), "application-properties-without-example", "application.properties detected without application-example.properties.", firstDetected(analysis, "application.properties", "src/main/resources/application.properties")),
                        riskIf(hasApplicationYamlWithoutExample(analysis), "application-yml-without-example", "application YAML detected without matching example file.", firstDetected(analysis, "application.yml", "application.yaml", "src/main/resources/application.yml", "src/main/resources/application.yaml")),
                        riskIf(hasFile(analysis, "package.json") && !hasFile(analysis, "package-lock.json"), "missing-package-lock", "package-lock.json not detected while package.json is present.", "package.json"),
                        riskIf(hasPythonProjectWithoutRequirements(analysis), "missing-python-requirements", "requirements.txt not detected for Python project.", pythonEvidence(analysis)),
                        riskIf(!hasBuildOrDependencyManifest(analysis), "missing-build-or-dependency-manifest", "No build or dependency manifest detected.", "Expected one of pom.xml, package.json, requirements.txt, pyproject.toml, Pipfile, poetry.lock, setup.py, environment.yml, or CMakeLists.txt.")
                )
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    @SafeVarargs
    private final List<RuleFinding> concat(List<RuleFinding>... groups) {
        return List.of(groups)
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private List<RuleFinding> strengthIf(ProjectAnalysis analysis, boolean condition, String ruleId, String message, String evidence) {
        return condition ? List.of(new RuleFinding(ruleId, message, evidence)) : List.of();
    }

    private List<RuleFinding> improvementIf(boolean condition, String ruleId, String message, String evidence) {
        return condition ? List.of(new RuleFinding(ruleId, message, evidence)) : List.of();
    }

    private List<RuleFinding> riskIf(boolean condition, String ruleId, String message, String evidence) {
        return condition ? List.of(new RuleFinding(ruleId, message, evidence)) : List.of();
    }

    private boolean hasFile(ProjectAnalysis analysis, String fileName) {
        return analysis.detectedFiles().contains(fileName);
    }

    private boolean hasAnyFile(ProjectAnalysis analysis, String... fileNames) {
        return firstDetected(analysis, fileNames) != null;
    }

    private String firstDetected(ProjectAnalysis analysis, String... fileNames) {
        for (String fileName : fileNames) {
            if (hasFile(analysis, fileName)) {
                return fileName;
            }
        }
        return null;
    }

    private boolean hasEnvExample(ProjectAnalysis analysis) {
        return hasAnyFile(analysis, ".env.example", ".env.template", "env.example");
    }

    private boolean hasDockerComposeFile(ProjectAnalysis analysis) {
        return hasAnyFile(analysis, "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml");
    }

    private boolean isBackendOrApiProject(ProjectAnalysis analysis) {
        return analysis.projectTypes().contains(ProjectType.SPRING_BOOT)
                || analysis.technologies().contains("Express");
    }

    private boolean hasDatabaseEvidence(ProjectAnalysis analysis) {
        return analysis.technologies().contains("PostgreSQL")
                || analysis.technologies().contains("MySQL")
                || analysis.technologies().contains("MariaDB")
                || analysis.technologies().contains("H2")
                || analysis.technologies().contains("SQLite");
    }

    private String backendEvidence(ProjectAnalysis analysis) {
        if (analysis.projectTypes().contains(ProjectType.SPRING_BOOT)) {
            return "Spring Boot";
        }
        if (analysis.technologies().contains("Express")) {
            return "Express";
        }
        return "backend/API technology";
    }

    private String databaseEvidence(ProjectAnalysis analysis) {
        return String.join(", ", analysis.technologies().stream()
                .filter(technology -> technology.equals("PostgreSQL")
                        || technology.equals("MySQL")
                        || technology.equals("MariaDB")
                        || technology.equals("H2")
                        || technology.equals("SQLite"))
                .toList());
    }

    private boolean hasApplicationPropertiesWithoutExample(ProjectAnalysis analysis) {
        boolean hasProperties = hasAnyFile(analysis, "application.properties", "src/main/resources/application.properties");
        boolean hasExample = hasAnyFile(analysis, "application-example.properties", "src/main/resources/application-example.properties");
        return hasProperties && !hasExample;
    }

    private boolean hasApplicationYamlWithoutExample(ProjectAnalysis analysis) {
        boolean hasYaml = hasAnyFile(analysis, "application.yml", "application.yaml", "src/main/resources/application.yml", "src/main/resources/application.yaml");
        boolean hasExample = hasAnyFile(analysis, "application-example.yml", "application-example.yaml", "src/main/resources/application-example.yml", "src/main/resources/application-example.yaml");
        return hasYaml && !hasExample;
    }

    private boolean hasBuildOrDependencyManifest(ProjectAnalysis analysis) {
        return hasAnyFile(analysis, "pom.xml", "package.json", "requirements.txt", "pyproject.toml", "Pipfile", "poetry.lock", "setup.py", "environment.yml", "environment.yaml", "CMakeLists.txt");
    }

    private boolean hasPythonProjectWithoutRequirements(ProjectAnalysis analysis) {
        return analysis.projectTypes().contains(ProjectType.PYTHON)
                && !hasFile(analysis, "requirements.txt")
                && (hasAnyFile(analysis, "pyproject.toml", "setup.py", "environment.yml", "environment.yaml", "Pipfile", "poetry.lock")
                        || hasFile(analysis, "python-source-files"));
    }

    private String pythonEvidence(ProjectAnalysis analysis) {
        String manifest = firstDetected(analysis, "pyproject.toml", "setup.py", "environment.yml", "environment.yaml", "Pipfile", "poetry.lock");
        return manifest != null ? manifest : "python-source-files";
    }

    private boolean isTrackedEnvRisk(ProjectAnalysis analysis) {
        return hasFile(analysis, ".git")
                && hasFile(analysis, ".env")
                && !gitIgnoreExcludesEnv(analysis);
    }

    private boolean gitIgnoreExcludesEnv(ProjectAnalysis analysis) {
        if (!hasFile(analysis, ".gitignore")) {
            return false;
        }

        Path gitIgnorePath = Path.of(analysis.path()).resolve(".gitignore");

        try {
            return Files.readAllLines(gitIgnorePath).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .anyMatch(this::excludesEnvFile);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean excludesEnvFile(String gitIgnorePattern) {
        return gitIgnorePattern.equals(".env")
                || gitIgnorePattern.equals("/.env")
                || gitIgnorePattern.equals(".env*")
                || gitIgnorePattern.equals("*.env")
                || gitIgnorePattern.equals(".env.local")
                || gitIgnorePattern.equals(".env.*");
    }
}
