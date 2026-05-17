package com.projectpulse.detector;

import com.projectpulse.model.DependencyFinding;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.ProjectType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class ProjectDetector {

    private final DependencyParser dependencyParser;
    private final DependencyHealthEvaluator dependencyHealthEvaluator;

    public ProjectDetector(DependencyParser dependencyParser, DependencyHealthEvaluator dependencyHealthEvaluator) {
        this.dependencyParser = dependencyParser;
        this.dependencyHealthEvaluator = dependencyHealthEvaluator;
    }

    // Synthetic marker added to detectedFiles when .py source files exist but no Python manifest.
    // Not a real file path — filtered out before reading file contents.
    static final String PYTHON_SOURCE_MARKER = "python-source-files";

    private static final String POM_XML = "pom.xml";
    private static final String PACKAGE_JSON = "package.json";
    private static final String PACKAGE_LOCK_JSON = "package-lock.json";
    private static final String REQUIREMENTS_TXT = "requirements.txt";
    private static final String PYPROJECT_TOML = "pyproject.toml";
    private static final String PIPFILE = "Pipfile";
    private static final String POETRY_LOCK = "poetry.lock";
    private static final String SETUP_PY = "setup.py";
    private static final String ENVIRONMENT_YML = "environment.yml";
    private static final String ENVIRONMENT_YAML = "environment.yaml";
    private static final String CMAKE_LISTS = "CMakeLists.txt";
    private static final String DOCKERFILE = "Dockerfile";
    private static final String DOCKER_COMPOSE_YML = "docker-compose.yml";
    private static final String DOCKER_COMPOSE_YAML = "docker-compose.yaml";
    private static final String COMPOSE_YML = "compose.yml";
    private static final String COMPOSE_YAML = "compose.yaml";
    private static final String GIT_DIRECTORY = ".git";
    private static final String GIT_IGNORE = ".gitignore";
    private static final String GITHUB_WORKFLOWS = ".github/workflows";
    private static final String ENV_FILE = ".env";
    private static final String ENV_EXAMPLE = ".env.example";
    private static final String ENV_TEMPLATE = ".env.template";
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final String APPLICATION_EXAMPLE_PROPERTIES = "application-example.properties";
    private static final String APPLICATION_YML = "application.yml";
    private static final String APPLICATION_YAML = "application.yaml";
    private static final String APPLICATION_EXAMPLE_YML = "application-example.yml";
    private static final String APPLICATION_EXAMPLE_YAML = "application-example.yaml";

    public List<ProjectAnalysis> detect(Path projectPath) {
        List<String> detectedFiles = detectEvidenceFiles(projectPath);

        if (!hasProjectMarker(detectedFiles)) {
            return List.of();
        }

        List<ProjectType> projectTypes = detectProjectTypes(projectPath, detectedFiles);
        List<String> technologies = detectTechnologies(projectPath, detectedFiles);
        List<DependencyFinding> dependencyFindings = dependencyParser.parse(projectPath, detectedFiles);
        List<DependencyHealthAssessment> dependencyHealthAssessments =
                dependencyHealthEvaluator.evaluate(dependencyFindings);

        return List.of(new ProjectAnalysis(
                projectPath.getFileName().toString(),
                projectPath.toAbsolutePath().normalize().toString(),
                projectTypes,
                detectedFiles,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                technologies,
                dependencyFindings,
                dependencyHealthAssessments
        ));
    }

    private List<String> detectEvidenceFiles(Path projectPath) {
        List<String> evidenceFiles = new ArrayList<>();
        Set<Path> seenRealPaths = new HashSet<>();

        addIfExists(projectPath, POM_XML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, PACKAGE_JSON, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, PACKAGE_LOCK_JSON, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, REQUIREMENTS_TXT, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, PYPROJECT_TOML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, PIPFILE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, POETRY_LOCK, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, SETUP_PY, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, ENVIRONMENT_YML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, ENVIRONMENT_YAML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, CMAKE_LISTS, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, DOCKERFILE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, DOCKER_COMPOSE_YML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, DOCKER_COMPOSE_YAML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, COMPOSE_YML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, COMPOSE_YAML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, GIT_DIRECTORY, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, GIT_IGNORE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, GITHUB_WORKFLOWS, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, ENV_FILE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, ENV_EXAMPLE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, ENV_TEMPLATE, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "env.example", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "README.md", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "README.txt", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "readme.md", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "test", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "tests", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "__tests__", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/test", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_PROPERTIES, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_EXAMPLE_PROPERTIES, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_YML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_YAML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_EXAMPLE_YML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, APPLICATION_EXAMPLE_YAML, evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application.properties", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application-example.properties", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application.yml", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application.yaml", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application-example.yml", evidenceFiles, seenRealPaths);
        addIfExists(projectPath, "src/main/resources/application-example.yaml", evidenceFiles, seenRealPaths);

        // Python source fallback: detect .py files when no primary project type already identified.
        // Guarded against Maven and C++ to avoid false positives from incidental scripts.
        if (!evidenceFiles.contains(POM_XML) && !evidenceFiles.contains(CMAKE_LISTS)
                && hasPythonSourceFiles(projectPath)) {
            evidenceFiles.add(PYTHON_SOURCE_MARKER);
        }

        return evidenceFiles.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    // Uses canonical (real) paths to skip files that map to the same filesystem entry
    // on case-insensitive filesystems (e.g. README.md and readme.md on Windows).
    private void addIfExists(Path projectPath, String fileName, List<String> evidenceFiles, Set<Path> seenRealPaths) {
        Path resolved = projectPath.resolve(fileName);
        if (!Files.exists(resolved)) {
            return;
        }
        try {
            Path real = resolved.toRealPath();
            if (seenRealPaths.add(real)) {
                evidenceFiles.add(fileName);
            }
        } catch (IOException ignored) {
            // toRealPath failed (e.g. race condition) — add without deduplication
            evidenceFiles.add(fileName);
        }
    }

    private boolean hasPythonSourceFiles(Path projectPath) {
        try (Stream<Path> stream = Files.list(projectPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().endsWith(".py"));
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean hasProjectMarker(List<String> detectedFiles) {
        return detectedFiles.contains(POM_XML)
                || detectedFiles.contains(PACKAGE_JSON)
                || detectedFiles.contains(REQUIREMENTS_TXT)
                || detectedFiles.contains(PYPROJECT_TOML)
                || detectedFiles.contains(PIPFILE)
                || detectedFiles.contains(POETRY_LOCK)
                || detectedFiles.contains(SETUP_PY)
                || detectedFiles.contains(ENVIRONMENT_YML)
                || detectedFiles.contains(ENVIRONMENT_YAML)
                || detectedFiles.contains(CMAKE_LISTS)
                || detectedFiles.contains(DOCKERFILE)
                || hasDockerComposeFile(detectedFiles)
                || detectedFiles.contains(GIT_DIRECTORY)
                || detectedFiles.contains(PYTHON_SOURCE_MARKER);
    }

    private List<ProjectType> detectProjectTypes(Path projectPath, List<String> detectedFiles) {
        List<ProjectType> projectTypes = new ArrayList<>();

        if (detectedFiles.contains(POM_XML)) {
            projectTypes.add(ProjectType.MAVEN);

            if (fileContains(projectPath.resolve(POM_XML), "spring-boot")) {
                projectTypes.add(ProjectType.SPRING_BOOT);
            }
        }

        if (detectedFiles.contains(PACKAGE_JSON)) {
            projectTypes.add(ProjectType.NODE);

            Optional<String> packageJson = readFile(projectPath.resolve(PACKAGE_JSON));
            if (packageJson.filter(content -> content.contains("\"react\"")).isPresent()) {
                projectTypes.add(ProjectType.REACT);
            }
            if (packageJson.filter(content -> content.contains("\"vite\"") || content.contains("@vitejs/")).isPresent()) {
                projectTypes.add(ProjectType.VITE);
            }
        }

        if (hasPythonEvidence(detectedFiles)) {
            projectTypes.add(ProjectType.PYTHON);
        }

        if (detectedFiles.contains(CMAKE_LISTS)) {
            projectTypes.add(ProjectType.CMAKE);
            projectTypes.add(ProjectType.CPP);
        }

        if (detectedFiles.contains(DOCKERFILE)) {
            projectTypes.add(ProjectType.DOCKERIZED);
        }

        if (hasDockerComposeFile(detectedFiles)) {
            projectTypes.add(ProjectType.DOCKER_COMPOSE);
        }

        if (detectedFiles.contains(GITHUB_WORKFLOWS)) {
            projectTypes.add(ProjectType.GITHUB_ACTIONS);
        }

        if (detectedFiles.contains(GIT_DIRECTORY)) {
            projectTypes.add(ProjectType.GIT_REPOSITORY);
        }

        return List.copyOf(projectTypes);
    }

    private List<String> detectTechnologies(Path projectPath, List<String> detectedFiles) {
        List<String> technologies = new ArrayList<>();

        if (detectedFiles.contains(POM_XML)) {
            technologies.add("Maven");
            if (fileContains(projectPath.resolve(POM_XML), "spring-boot")) {
                technologies.add("Spring Boot");
            }
        }

        if (detectedFiles.contains(PACKAGE_JSON)) {
            technologies.add("Node.js");
            Optional<String> packageJson = readFile(projectPath.resolve(PACKAGE_JSON));
            packageJson.filter(content -> content.contains("\"react\""))
                    .ifPresent(content -> technologies.add("React"));
            packageJson.filter(content -> content.contains("\"vite\"") || content.contains("@vitejs/"))
                    .ifPresent(content -> technologies.add("Vite"));
            packageJson.filter(content -> content.contains("\"express\""))
                    .ifPresent(content -> technologies.add("Express"));
        }

        if (hasPythonEvidence(detectedFiles)) {
            technologies.add("Python");
        }

        if (detectedFiles.contains(CMAKE_LISTS)) {
            technologies.add("CMake");
            technologies.add("C++");
        }

        if (detectedFiles.contains(DOCKERFILE)) {
            technologies.add("Docker");
        }

        if (hasDockerComposeFile(detectedFiles)) {
            technologies.add("Docker Compose");
        }

        if (detectedFiles.contains(GITHUB_WORKFLOWS)) {
            technologies.add("GitHub Actions");
        }

        technologies.addAll(detectDatabaseTechnologies(projectPath, detectedFiles));

        return technologies.stream()
                .distinct()
                .toList();
    }

    private boolean hasPythonEvidence(List<String> detectedFiles) {
        return detectedFiles.contains(REQUIREMENTS_TXT)
                || detectedFiles.contains(PYPROJECT_TOML)
                || detectedFiles.contains(PIPFILE)
                || detectedFiles.contains(POETRY_LOCK)
                || detectedFiles.contains(SETUP_PY)
                || detectedFiles.contains(ENVIRONMENT_YML)
                || detectedFiles.contains(ENVIRONMENT_YAML)
                || detectedFiles.contains(PYTHON_SOURCE_MARKER);
    }

    private List<String> detectDatabaseTechnologies(Path projectPath, List<String> detectedFiles) {
        String evidence = detectedFiles.stream()
                .filter(f -> !f.equals(PYTHON_SOURCE_MARKER))
                .map(projectPath::resolve)
                .map(this::readFile)
                .flatMap(Optional::stream)
                .map(String::toLowerCase)
                .reduce("", (left, right) -> left + "\n" + right);

        List<String> databases = new ArrayList<>();

        if (containsAny(evidence, "postgresql", "postgres", "jdbc:postgresql", "org.postgresql", "psycopg")) {
            databases.add("PostgreSQL");
        }

        if (containsAny(evidence, "mysql", "jdbc:mysql", "mysqlclient", "pymysql")) {
            databases.add("MySQL");
        }

        if (containsAny(evidence, "mariadb", "jdbc:mariadb")) {
            databases.add("MariaDB");
        }

        if (containsAny(evidence, "jdbc:h2", "h2database", "com.h2database")) {
            databases.add("H2");
        }

        if (containsAny(evidence, "sqlite", "sqlite3", "jdbc:sqlite")) {
            databases.add("SQLite");
        }

        return databases;
    }

    private boolean containsAny(String content, String... tokens) {
        for (String token : tokens) {
            if (content.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDockerComposeFile(List<String> detectedFiles) {
        return detectedFiles.contains(DOCKER_COMPOSE_YML)
                || detectedFiles.contains(DOCKER_COMPOSE_YAML)
                || detectedFiles.contains(COMPOSE_YML)
                || detectedFiles.contains(COMPOSE_YAML);
    }

    private boolean fileContains(Path file, String token) {
        return readFile(file)
                .map(content -> content.contains(token))
                .orElse(false);
    }

    private Optional<String> readFile(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
