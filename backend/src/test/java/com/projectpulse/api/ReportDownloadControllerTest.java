package com.projectpulse.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportDownloadControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private Path reportsDirectory;

    @BeforeEach
    void setUp() throws Exception {
        reportsDirectory = tempDir.resolve("reports");
        Files.createDirectories(reportsDirectory);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportDownloadController(reportsDirectory))
                .build();
    }

    @Test
    void downloadsJsonReportAsAttachmentByDefault() throws Exception {
        Files.writeString(reportsDirectory.resolve("projectpulse-scan-20260527-024812.json"), "{\"ok\":true}");

        mockMvc.perform(get("/api/reports/projectpulse-scan-20260527-024812.json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("projectpulse-scan-20260527-024812.json")))
                .andExpect(content().json("{\"ok\":true}"));
    }

    @Test
    void opensJsonReportInlineWhenRequested() throws Exception {
        Files.writeString(reportsDirectory.resolve("projectpulse-scan-20260527-024812.json"), "{\"ok\":true}");

        mockMvc.perform(get("/api/reports/projectpulse-scan-20260527-024812.json?inline=true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("projectpulse-scan-20260527-024812.json")))
                .andExpect(content().json("{\"ok\":true}"));
    }

    @Test
    void downloadsMarkdownReportWithExpectedHeaders() throws Exception {
        Files.writeString(reportsDirectory.resolve("projectpulse-scan-20260527-024812.md"), "# Report");

        mockMvc.perform(get("/api/reports/projectpulse-scan-20260527-024812.md"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/markdown"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("projectpulse-scan-20260527-024812.md")))
                .andExpect(content().string("# Report"));
    }

    @Test
    void opensMarkdownReportInlineWhenRequested() throws Exception {
        Files.writeString(reportsDirectory.resolve("projectpulse-scan-20260527-024812.md"), "# Report");

        mockMvc.perform(get("/api/reports/projectpulse-scan-20260527-024812.md?inline=true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/markdown"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("projectpulse-scan-20260527-024812.md")))
                .andExpect(content().string("# Report"));
    }

    @Test
    void rejectsTraversalFilename() throws Exception {
        mockMvc.perform(get("/api/reports/projectpulse-scan-..-024812.json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNestedReportPath() throws Exception {
        mockMvc.perform(get("/api/reports/../../etc/passwd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingReport() throws Exception {
        mockMvc.perform(get("/api/reports/nonexistent.json"))
                .andExpect(status().isNotFound());
    }
}
