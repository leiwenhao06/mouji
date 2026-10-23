package com.eyefollowup.ledger.controller;

import com.eyefollowup.ledger.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 台账导出（CSV）。中文文件名按 RFC 5987 编码。
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/patients")
    public ResponseEntity<byte[]> patientSummary() {
        return csv(exportService.exportPatientSummary());
    }

    @GetMapping("/patients/{patientId}/ledger")
    public ResponseEntity<byte[]> patientLedger(@PathVariable String patientId) {
        return csv(exportService.exportPatientLedger(patientId));
    }

    private ResponseEntity<byte[]> csv(ExportService.CsvFile file) {
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .body(file.content());
    }
}
