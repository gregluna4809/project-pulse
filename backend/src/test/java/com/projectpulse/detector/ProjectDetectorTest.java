package com.projectpulse.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectpulse.model.ProjectAnalysis;
import com.projectpulse.model.ProjectType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDetectorTest {

    @TempDir
    Path tempDir;

    private final ProjectDetector projectDetector = new ProjectDetector(new DependencyParser(), new DependencyHealthEvaluator());

    @Test
    void detectRecognizesExpandedPythonEcosystemFiles() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("python-app"));
        Files.writeString(project.resolve("Pipfile"), "[packages]");
        Files.writeString(project.resolve("setup.py"), "setup(name='python-app')");
        Files.writeString(project.resolve("environment.yml"), "name: python-app");

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.projectTypes()).contains(ProjectType.PYTHON);
        assertThat(analysis.technologies()).contains("Python");
        assertThat(analysis.detectedFiles()).contains("Pipfile", "setup.py", "environment.yml");
    }

    @Test
    void detectRecognizesDatabaseTechnologiesFromConfigEvidence() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), """
                <dependencies>
                  <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-web</artifactId>
                  </dependency>
                </dependencies>
                """);
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.properties"), "spring.datasource.url=jdbc:postgresql://localhost:5432/projectpulse");

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.technologies()).contains("Maven", "Spring Boot", "PostgreSQL");
    }

    @Test
    void detectRecognizesSqliteMysqlAndH2Evidence() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("mixed-config"));
        Files.writeString(project.resolve("package.json"), "{\"dependencies\":{\"sqlite3\":\"latest\",\"mysql2\":\"latest\"}}");
        Files.writeString(project.resolve("application.yml"), "spring:\n  datasource:\n    url: jdbc:h2:mem:testdb");

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.technologies()).contains("SQLite", "MySQL", "H2");
    }

    @Test
    void detectFindsPythonProjectFromSourceFilesAloneWithNoManifest() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("flask-service"));
        Files.writeString(project.resolve("app.py"), "from flask import Flask\napp = Flask(__name__)");

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.projectTypes()).contains(ProjectType.PYTHON);
        assertThat(analysis.technologies()).contains("Python");
        assertThat(analysis.detectedFiles()).contains("python-source-files");
    }

    @Test
    void detectDoesNotAddReadmeVariantWhenUppercaseAlreadyPresent() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("my-lib"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>my-lib</artifactId>");
        Files.writeString(project.resolve("README.md"), "# My Lib");

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.detectedFiles()).contains("README.md");
        assertThat(analysis.detectedFiles()).doesNotContain("readme.md");
        assertThat(analysis.detectedFiles()).doesNotHaveDuplicates();
    }

    @Test
    void detectFindsPostgreSqlFromDockerComposeYml() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("compose-stack"));
        Files.writeString(project.resolve("docker-compose.yml"), """
                services:
                  db:
                    image: postgres:16
                    environment:
                      POSTGRES_DB: appdb
                """);

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.technologies()).contains("PostgreSQL");
    }

    @Test
    void detectFindsPostgreSqlFromApplicationYml() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("spring-api"));
        Files.writeString(project.resolve("pom.xml"), "<artifactId>spring-boot-starter-web</artifactId>");
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("application.yml"), """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/appdb
                """);

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.technologies()).contains("PostgreSQL");
        assertThat(analysis.detectedFiles()).contains("src/main/resources/application.yml");
    }

    @Test
    void detectFindsMariaDbSeparatelyFromMySql() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("mixed-db"));
        Files.writeString(project.resolve("docker-compose.yml"), """
                services:
                  db:
                    image: mariadb:11
                """);

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.technologies()).contains("MariaDB");
        assertThat(analysis.technologies()).doesNotContain("MySQL");
    }

    @Test
    void detectIncludesDependencyFindingsForPythonRequirementsTxt() throws IOException {
        Path project = Files.createDirectory(tempDir.resolve("flask-api"));
        Files.writeString(project.resolve("requirements.txt"), """
                flask==2.3.0
                pandas==2.1.0
                requests>=2.28.0
                numpy
                some-other-package==1.0.0
                """);

        ProjectAnalysis analysis = singleDetection(project);

        assertThat(analysis.dependencyFindings()).extracting(com.projectpulse.model.DependencyFinding::name)
                .contains("flask", "pandas", "requests", "numpy")
                .doesNotContain("some-other-package");

        com.projectpulse.model.DependencyFinding flask = analysis.dependencyFindings().stream()
                .filter(f -> f.name().equals("flask")).findFirst().orElseThrow();
        assertThat(flask.version()).isEqualTo("2.3.0");
        assertThat(flask.ecosystem()).isEqualTo("Python");
        assertThat(flask.sourceFile()).isEqualTo("requirements.txt");
        assertThat(flask.scope()).isEqualTo("main");
    }

    private ProjectAnalysis singleDetection(Path project) {
        List<ProjectAnalysis> analyses = projectDetector.detect(project);
        assertThat(analyses).hasSize(1);
        return analyses.getFirst();
    }
}
