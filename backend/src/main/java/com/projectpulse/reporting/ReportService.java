package com.projectpulse.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectpulse.model.DependencyHealthAssessment;
import com.projectpulse.model.ProjectModule;
import com.projectpulse.model.ProjectWorkspace;
import com.projectpulse.model.ReportResponse;
import com.projectpulse.model.RuleFinding;
import com.projectpulse.model.ScanRequest;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.TestAnalysis;
import com.projectpulse.scanner.ScanService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ScanService scanService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path reportsDirectory;

    @Autowired
    public ReportService(ScanService scanService, ObjectMapper objectMapper) {
        this(scanService, objectMapper, Clock.systemDefaultZone(), Path.of("reports"));
    }

    ReportService(ScanService scanService, ObjectMapper objectMapper, Clock clock, Path reportsDirectory) {
        this.scanService = scanService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.reportsDirectory = reportsDirectory;
    }

    public ReportResponse generateJsonReport(ScanRequest request) {
        ScanResponse scanResponse = scanService.scan(request);
        Instant generatedAt = Instant.now(clock);
        Path reportPath = reportPath(generatedAt, "json");

        try {
            Files.createDirectories(reportsDirectory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), scanResponse);
            return new ReportResponse(reportPath.toAbsolutePath().normalize().toString(), generatedAt, "json");
        } catch (IOException exception) {
            throw new ReportGenerationException("Unable to generate JSON report.", exception);
        }
    }

    public ReportResponse generateMarkdownReport(ScanRequest request) {
        ScanResponse scanResponse = scanService.scan(request);
        Instant generatedAt = Instant.now(clock);
        Path reportPath = reportPath(generatedAt, "md");

        try {
            Files.createDirectories(reportsDirectory);
            Files.writeString(reportPath, renderMarkdown(scanResponse, generatedAt), StandardCharsets.UTF_8);
            return new ReportResponse(reportPath.toAbsolutePath().normalize().toString(), generatedAt, "markdown");
        } catch (IOException exception) {
            throw new ReportGenerationException("Unable to generate Markdown report.", exception);
        }
    }

    String renderMarkdown(ScanResponse scanResponse, Instant generatedAt) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ProjectPulse Scan Report\n\n");
        markdown.append("Generated:\n");
        markdown.append(generatedAt).append("\n\n");

        for (ProjectWorkspace workspace : scanResponse.workspaces()) {
            markdown.append("## Workspace: ").append(workspace.name()).append("\n\n");
            appendStringSection(markdown, "Technologies", workspace.technologies());
            appendTestAnalysisSection(markdown, workspace.testAnalysis());
            appendFindingsSection(markdown, "Strengths", workspace.strengths());
            appendFindingsSection(markdown, "Improvements", workspace.improvements());
            appendFindingsSection(markdown, "Risks", workspace.riskFlags());
            appendDependencyHealthSection(markdown, "Dependency health", workspace.dependencyHealthAssessments());

            for (ProjectModule module : workspace.modules()) {
                markdown.append("### Module: ").append(module.name()).append("\n\n");
                appendStringSection(markdown, "Technologies", module.technologies());
                appendTestAnalysisSection(markdown, module.testAnalysis());
                appendFindingsSection(markdown, "Strengths", module.strengths());
                appendFindingsSection(markdown, "Improvements", module.improvements());
                appendFindingsSection(markdown, "Risks", module.riskFlags());
                appendDependencyHealthSection(markdown, "Dependency health", module.dependencyHealthAssessments());
            }
        }

        return markdown.toString();
    }

    private Path reportPath(Instant generatedAt, String extension) {
        String timestamp = FILE_TIMESTAMP_FORMATTER
                .withZone(clock.getZone())
                .format(generatedAt);
        return reportsDirectory.resolve("projectpulse-scan-%s.%s".formatted(timestamp, extension));
    }

    private void appendStringSection(StringBuilder markdown, String heading, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        markdown.append(heading).append(":\n");
        values.forEach(value -> markdown.append("- ").append(value).append("\n"));
        markdown.append("\n");
    }

    private void appendFindingsSection(StringBuilder markdown, String heading, List<RuleFinding> findings) {
        if (findings.isEmpty()) {
            return;
        }
        markdown.append(heading).append(":\n");
        findings.forEach(finding -> markdown.append("- ").append(finding.message()).append("\n"));
        markdown.append("\n");
    }

    private void appendTestAnalysisSection(StringBuilder markdown, TestAnalysis analysis) {
        markdown.append("Test intelligence:\n");
        markdown.append("- Test files: ").append(analysis.testFileCount()).append("\n");
        markdown.append("- Integration test files: ").append(analysis.integrationTestFileCount()).append("\n");
        markdown.append("- Test script: ").append(analysis.hasTestScript() ? "yes" : "no").append("\n");
        if (!analysis.detectedFrameworks().isEmpty()) {
            markdown.append("- Frameworks: ").append(String.join(", ", analysis.detectedFrameworks())).append("\n");
        }
        markdown.append("\n");
    }

    private void appendDependencyHealthSection(
            StringBuilder markdown,
            String heading,
            List<DependencyHealthAssessment> assessments
    ) {
        if (assessments.isEmpty()) {
            return;
        }
        markdown.append(heading).append(":\n");
        assessments.forEach(assessment -> markdown
                .append("- ")
                .append(assessment.dependencyName())
                .append(" ")
                .append(assessment.detectedVersion().isBlank() ? "unversioned" : assessment.detectedVersion())
                .append(" -> ")
                .append(assessment.healthStatus())
                .append("\n"));
        markdown.append("\n");
    }
}
