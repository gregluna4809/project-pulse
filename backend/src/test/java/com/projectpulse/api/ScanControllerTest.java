package com.projectpulse.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectpulse.model.ReportResponse;
import com.projectpulse.model.DiscoveredWorkspace;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.WorkspaceDiscoveryResponse;
import com.projectpulse.reporting.ReportService;
import com.projectpulse.scanner.ScanService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScanController.class)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScanService scanService;

    @MockBean
    private ReportService reportService;

    @Test
    void scanReturnsScanResponse() throws Exception {
        when(scanService.scan(any())).thenReturn(new ScanResponse(0, List.of()));

        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"C:\\\\Users\\\\gvl71\\\\OneDrive\\\\Desktop\\\\Projects\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspacesFound").value(0))
                .andExpect(jsonPath("$.workspaces").isArray());
    }

    @Test
    void scanRejectsBlankRootPath() throws Exception {
        mockMvc.perform(post("/api/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed."));
    }

    @Test
    void discoverWorkspacesReturnsImmediateDirectoryList() throws Exception {
        when(scanService.discover(any())).thenReturn(new WorkspaceDiscoveryResponse(
                "C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects",
                1,
                List.of(new DiscoveredWorkspace(
                        "project-pulse",
                        "C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse"
                ))
        ));

        mockMvc.perform(post("/api/workspaces/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"C:\\\\Users\\\\gvl71\\\\OneDrive\\\\Desktop\\\\Projects\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootPath").value("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects"))
                .andExpect(jsonPath("$.workspacesFound").value(1))
                .andExpect(jsonPath("$.workspaces[0].name").value("project-pulse"))
                .andExpect(jsonPath("$.workspaces[0].path").value("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse"));
    }

    @Test
    void scanJsonReportReturnsReportResponse() throws Exception {
        when(reportService.generateJsonReport(any())).thenReturn(new ReportResponse(
                "C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse\\backend\\reports\\projectpulse-scan-20260517-143500.json",
                Instant.parse("2026-05-17T18:35:00Z"),
                "json"
        ));

        mockMvc.perform(post("/api/scan/report/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"C:\\\\Users\\\\gvl71\\\\OneDrive\\\\Desktop\\\\Projects\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportPath").value("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse\\backend\\reports\\projectpulse-scan-20260517-143500.json"))
                .andExpect(jsonPath("$.generatedAt").value("2026-05-17T18:35:00Z"))
                .andExpect(jsonPath("$.format").value("json"));
    }

    @Test
    void scanMarkdownReportReturnsReportResponse() throws Exception {
        when(reportService.generateMarkdownReport(any())).thenReturn(new ReportResponse(
                "C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse\\backend\\reports\\projectpulse-scan-20260517-143500.md",
                Instant.parse("2026-05-17T18:35:00Z"),
                "markdown"
        ));

        mockMvc.perform(post("/api/scan/report/markdown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rootPath\":\"C:\\\\Users\\\\gvl71\\\\OneDrive\\\\Desktop\\\\Projects\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportPath").value("C:\\Users\\gvl71\\OneDrive\\Desktop\\Projects\\project-pulse\\backend\\reports\\projectpulse-scan-20260517-143500.md"))
                .andExpect(jsonPath("$.generatedAt").value("2026-05-17T18:35:00Z"))
                .andExpect(jsonPath("$.format").value("markdown"));
    }
}
