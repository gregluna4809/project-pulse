package com.projectpulse.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectpulse.ci.CiAnalysis;
import com.projectpulse.git.GitAnalysis;
import com.projectpulse.gitignore.GitignoreAnalysis;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.HealthStatus;
import com.projectpulse.model.HealthTier;
import com.projectpulse.model.ProjectHealthAssessment;
import com.projectpulse.model.ProjectModule;
import com.projectpulse.model.ProjectType;
import com.projectpulse.model.ProjectWorkspace;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.ScanRequest;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.Severity;
import com.projectpulse.model.TestAnalysis;
import com.projectpulse.scanner.ScanService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant fixedInstant = Instant.parse("2026-05-17T18:35:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    @Test
    void generateJsonReportCreatesReportsDirectoryAndSerializesFullScanResponse() throws Exception {
        ScanService scanService = org.mockito.Mockito.mock(ScanService.class);
        ScanRequest request = new ScanRequest("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects");
        when(scanService.scan(request)).thenReturn(sampleScanResponse());

        Path reportsDirectory = tempDir.resolve("reports");
        ReportService reportService = new ReportService(scanService, objectMapper, fixedClock, reportsDirectory);

        var response = reportService.generateJsonReport(request);

        Path reportPath = Path.of(response.reportPath());
        assertThat(response.format()).isEqualTo("json");
        assertThat(response.generatedAt()).isEqualTo(fixedInstant);
        assertThat(reportPath.getFileName().toString()).isEqualTo("projectpulse-scan-20260517-183500.json");
        assertThat(Files.exists(reportsDirectory)).isTrue();
        assertThat(Files.exists(reportPath)).isTrue();

        JsonNode json = objectMapper.readTree(reportPath.toFile());
        assertThat(json.path("workspacesFound").asInt()).isEqualTo(1);
        assertThat(json.path("workspaces").get(0).path("name").asText()).isEqualTo("DataForge");
        assertThat(json.path("workspaces").get(0).path("modules").get(0)
                .path("dependencyHealthAssessments").get(0).path("healthStatus").asText())
                .isEqualTo("ACCEPTABLE");
    }

    @Test
    void generateMarkdownReportCreatesExpectedReadableSections() throws Exception {
        ScanService scanService = org.mockito.Mockito.mock(ScanService.class);
        ScanRequest request = new ScanRequest("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects");
        when(scanService.scan(request)).thenReturn(sampleScanResponse());

        ReportService reportService = new ReportService(scanService, objectMapper, fixedClock, tempDir.resolve("reports"));

        var response = reportService.generateMarkdownReport(request);

        Path reportPath = Path.of(response.reportPath());
        String markdown = Files.readString(reportPath);

        assertThat(response.format()).isEqualTo("markdown");
        assertThat(reportPath.getFileName().toString()).isEqualTo("projectpulse-scan-20260517-183500.md");
        assertThat(markdown).contains("# ProjectPulse Scan Report");
        assertThat(markdown).contains("Generated:\n2026-05-17T18:35:00Z");
        assertThat(markdown).contains("## Workspace: DataForge");
        assertThat(markdown).contains("Technologies:\n- PostgreSQL");
        assertThat(markdown).contains("### Module: backend");
        assertThat(markdown).contains("Test intelligence:\n- Test files: 1");
        assertThat(markdown).contains("Strengths:\n- Automated tests detected.");
        assertThat(markdown).contains("Improvements:\n- Dockerfile not detected for backend/API project.");
        assertThat(markdown).contains("Risks:\n- application YAML detected without matching example file.");
        assertThat(markdown).contains("Dependency health:\n- spring-boot-starter-parent 3.3.6 -> ACCEPTABLE");
        assertThat(markdown).contains("- java 21 -> CURRENT");
    }

    private ScanResponse sampleScanResponse() {
        ProjectHealthAssessment moduleHealth =
                new ProjectHealthAssessment(72, HealthTier.FAIR, "Moderate engineering debt detected.");
        ProjectHealthAssessment workspaceHealth =
                new ProjectHealthAssessment(100, HealthTier.EXCELLENT, "Healthy engineering posture.");

        ProjectModule backend = new ProjectModule(
                "backend",
                "C:\\projects\\dataforge\\backend",
                List.of("pom.xml", "src/test", "src/main/resources/application.yml"),
                List.of(ProjectType.MAVEN, ProjectType.SPRING_BOOT),
                List.of("Spring Boot", "Maven", "PostgreSQL"),
                List.of(),
                List.of(new RuleFinding("automated-tests-detected", "Automated tests detected.", "1 test file(s)")),
                List.of(new RuleFinding("missing-backend-dockerfile",
                        "Dockerfile not detected for backend/API project.",
                        "Spring Boot")),
                List.of(new RuleFinding("application-yml-without-example",
                        "application YAML detected without matching example file.",
                        "src/main/resources/application.yml")),
                List.of(),
                List.of(
                        new DependencyHealthAssessment("spring-boot-starter-parent", "3.3.6", "3.5.x",
                                HealthStatus.ACCEPTABLE, Severity.LOW, "Version is acceptable."),
                        new DependencyHealthAssessment("java", "21", "21",
                                HealthStatus.CURRENT, Severity.INFO, "Version is current.")
                ),
                moduleHealth,
                GitAnalysis.noGit(),
                GitignoreAnalysis.noGitignore(),
                CiAnalysis.noCi(),
                new TestAnalysis(true, 1, 0, List.of("JUnit"), false, List.of("src/test"), null)
        );

        ProjectWorkspace workspace = new ProjectWorkspace(
                "DataForge",
                "C:\\projects\\dataforge",
                List.of(),
                List.of(),
                List.of("PostgreSQL"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(backend),
                workspaceHealth,
                GitAnalysis.noGit(),
                GitignoreAnalysis.noGitignore(),
                CiAnalysis.noCi(),
                TestAnalysis.none()
        );

        return new ScanResponse(1, List.of(workspace));
    }
}
