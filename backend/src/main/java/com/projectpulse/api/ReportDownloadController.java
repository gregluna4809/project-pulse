package com.projectpulse.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReportDownloadController {

    private static final MediaType MARKDOWN_MEDIA_TYPE = MediaType.parseMediaType("text/markdown");

    private final Path reportsDirectory;

    public ReportDownloadController() {
        this(Path.of("reports"));
    }

    ReportDownloadController(Path reportsDirectory) {
        this.reportsDirectory = reportsDirectory.toAbsolutePath().normalize();
    }

    @GetMapping("/reports/{filename:.+}")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable String filename,
            @RequestParam(defaultValue = "false") boolean inline
    ) throws IOException {
        if (isInvalidFilename(filename)) {
            return ResponseEntity.badRequest().build();
        }

        Path reportPath = reportsDirectory.resolve(filename).normalize();
        if (!reportPath.startsWith(reportsDirectory)) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.isRegularFile(reportPath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = new UrlResource(reportPath.toUri());
        return ResponseEntity.ok()
                .contentType(contentType(filename))
                .contentLength(Files.size(reportPath))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(filename, inline)
                        .filename(filename)
                        .build()
                        .toString())
                .body(resource);
    }

    @GetMapping("/reports/**")
    public ResponseEntity<Void> rejectNestedReportPaths() {
        return ResponseEntity.badRequest().build();
    }

    private ContentDisposition.Builder contentDisposition(String filename, boolean inline) {
        return inline ? ContentDisposition.inline() : ContentDisposition.attachment();
    }

    private boolean isInvalidFilename(String filename) {
        return filename == null
                || filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("..");
    }

    private MediaType contentType(String filename) {
        if (filename.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (filename.endsWith(".md")) {
            return MARKDOWN_MEDIA_TYPE;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
