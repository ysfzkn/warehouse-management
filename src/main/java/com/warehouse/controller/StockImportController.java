package com.warehouse.controller;

import com.warehouse.entity.StockImportHistory;
import com.warehouse.repository.StockImportHistoryRepository;
import com.warehouse.service.StockImportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/stock-imports")
@RequiredArgsConstructor
public class StockImportController {

    private final StockImportService stockImportService;
    private final StockImportHistoryRepository historyRepository;

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        try (XSSFWorkbook workbook = stockImportService.generateTemplate();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            InputStreamResource resource = new InputStreamResource(bais);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stok_sablon.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(baos.size())
                    .body(resource);
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StockImportHistory> upload(@RequestParam("warehouseId") Long warehouseId,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        StockImportHistory history = stockImportService.importStocks(warehouseId, file);
        return ResponseEntity.ok(history);
    }

    @GetMapping
    public ResponseEntity<List<StockImportHistory>> list() {
        List<StockImportHistory> list = historyRepository.findAll();
        // Normalize status to Turkish for legacy records
        for (StockImportHistory h : list) {
            if (h.getStatus() == null) continue;
            String s = h.getStatus().toUpperCase();
            if ("SUCCESS".equals(s)) h.setStatus("BAŞARILI");
            else if ("FAILED".equals(s)) h.setStatus("BAŞARISIZ");
            else if ("PARTIAL".equals(s)) h.setStatus("KISMEN");
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        StockImportHistory history = historyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kayıt bulunamadı: " + id));
        Path path = Path.of("uploads/stock-imports").resolve(history.getStoredFilename());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + history.getOriginalFilename())
                .contentType(MediaType.parseMediaType(history.getContentType()))
                .contentLength(Files.size(path))
                .body(resource);
    }
}


