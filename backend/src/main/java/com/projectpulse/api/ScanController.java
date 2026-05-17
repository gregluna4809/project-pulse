package com.projectpulse.api;

import com.projectpulse.model.ReportResponse;
import com.projectpulse.model.ScanRequest;
import com.projectpulse.model.ScanResponse;
import com.projectpulse.model.WorkspaceDiscoveryRequest;
import com.projectpulse.model.WorkspaceDiscoveryResponse;
import com.projectpulse.reporting.ReportService;
import com.projectpulse.scanner.ScanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanService scanService;
    private final ReportService reportService;

    public ScanController(ScanService scanService, ReportService reportService) {
        this.scanService = scanService;
        this.reportService = reportService;
    }

    @PostMapping("/scan")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request) {
        return scanService.scan(request);
    }

    @PostMapping("/workspaces/discover")
    public WorkspaceDiscoveryResponse discoverWorkspaces(@Valid @RequestBody WorkspaceDiscoveryRequest request) {
        return scanService.discover(request.rootPath());
    }

    @PostMapping("/scan/report/json")
    public ReportResponse generateJsonReport(@Valid @RequestBody ScanRequest request) {
        return reportService.generateJsonReport(request);
    }

    @PostMapping("/scan/report/markdown")
    public ReportResponse generateMarkdownReport(@Valid @RequestBody ScanRequest request) {
        return reportService.generateMarkdownReport(request);
    }
}
