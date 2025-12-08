package com.warehouse.controller;

import com.warehouse.config.ImportProperties;
import com.warehouse.entity.StockImportHistory;
import com.warehouse.repository.StockImportHistoryRepository;
import com.warehouse.service.StockImportService;
import com.warehouse.service.AdminSecurityService;
import com.warehouse.dto.StockImportHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.warehouse.constants.ImportMessages;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import com.warehouse.dto.BulkDeleteResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/stock-imports")
@RequiredArgsConstructor
@Slf4j
public class StockImportController {

    private final StockImportService stockImportService;
    private final StockImportHistoryRepository historyRepository;
    private final ImportProperties importProperties;
    private final AdminSecurityService adminSecurityService;

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        try (XSSFWorkbook workbook = stockImportService.generateTemplate();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            InputStreamResource resource = new InputStreamResource(bais);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stok_sablon.xlsx")
                    .contentType(MediaType.parseMediaType(ImportMessages.CONTENT_TYPE_XLSX))
                    .contentLength(baos.size())
                    .body(resource);
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StockImportHistoryDto> upload(@RequestParam("warehouseId") Long warehouseId,
                                                     @RequestParam("file") MultipartFile file) throws IOException {
        StockImportHistory history = stockImportService.importStocks(warehouseId, file);
        return ResponseEntity.ok(toDto(history));
    }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<StockImportHistoryDto>> list() {
        List<StockImportHistory> list = historyRepository.findAll();
        var result = list.stream().map(h -> {
            // Translate status safely on a copy (DTO)
            return toDto(h);
        }).toList();
        // Apply status TR mapping in DTOs
        for (var dto : result) {
            if (dto.status == null) continue;
            String s = dto.status.toUpperCase();
            if (ImportMessages.STATUS_SUCCESS.equals(s)) dto.status = ImportMessages.STATUS_TR_SUCCESS;
            else if (ImportMessages.STATUS_FAILED.equals(s)) dto.status = ImportMessages.STATUS_TR_FAILED;
            else if (ImportMessages.STATUS_PARTIAL.equals(s)) dto.status = ImportMessages.STATUS_TR_PARTIAL;
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImportHistory(@PathVariable Long id,
                                                    @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) throws IOException {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        StockImportHistory history = historyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        
        // Delete the stored file if it exists
        Path storageDir = Path.of(importProperties.getStorageDir());
        Path filePath = storageDir.resolve(history.getStoredFilename());
        deleteFileIfExists(filePath, history.getStoredFilename());
        
        // Delete the database record
        historyRepository.delete(history);
        log.info("Deleted import history record with id: {} and file: {}", id, history.getStoredFilename());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkDeleteResponse> deleteImportHistories(@RequestBody List<Long> ids,
                                                                     @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) throws IOException {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        
        List<BulkDeleteResponse.DeleteError> errors = new ArrayList<>();
        int successCount = 0;
        Path storageDir = Path.of(importProperties.getStorageDir());
        
        for (Long id : ids) {
            try {
                StockImportHistory history = historyRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
                
                // Delete the stored file if it exists
                Path filePath = storageDir.resolve(history.getStoredFilename());
                deleteFileIfExists(filePath, history.getStoredFilename());
                
                // Delete the database record
                historyRepository.delete(history);
                log.debug("Deleted import history record with id: {} and file: {}", id, history.getStoredFilename());
                successCount++;
            } catch (IllegalArgumentException e) {
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    "Kayıt #" + id,
                    null,
                    "NOT_FOUND",
                    "Kayıt bulunamadı: " + e.getMessage()
                ));
            } catch (Exception e) {
                errors.add(new BulkDeleteResponse.DeleteError(
                    id,
                    "Kayıt #" + id,
                    null,
                    "INTERNAL_ERROR",
                    "Silme işlemi sırasında hata oluştu: " + e.getMessage()
                ));
            }
        }
        
        BulkDeleteResponse response = new BulkDeleteResponse(successCount, errors.size(), errors);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        StockImportHistory history = historyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found: " + id));
        Path storageDir = Path.of(importProperties.getStorageDir());
        Path path = storageDir.resolve(history.getStoredFilename());
        if (!Files.exists(path)) {
            // Fallback: try to find by original filename suffix (for older inconsistent records)
            String sanitizedOriginal = history.getOriginalFilename() != null
                    ? history.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_")
                    : null;
            if (sanitizedOriginal != null && Files.isDirectory(storageDir)) {
                try (var stream = Files.list(storageDir)) {
                    var match = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(sanitizedOriginal))
                            .sorted((p1, p2) -> {
                                try {
                                    return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .findFirst();
                    if (match.isPresent()) {
                        path = match.get();
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                }
            } else {
                return ResponseEntity.notFound().build();
            }
        }
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + history.getOriginalFilename())
                .contentType(MediaType.parseMediaType(history.getContentType()))
                .contentLength(Files.size(path))
                .body(resource);
    }

    private StockImportHistoryDto toDto(StockImportHistory h) {
        StockImportHistoryDto dto = new StockImportHistoryDto();
        dto.id = h.getId();
        dto.originalFilename = h.getOriginalFilename();
        dto.storedFilename = h.getStoredFilename();
        dto.contentType = h.getContentType();
        dto.totalRows = h.getTotalRows();
        dto.createdProducts = h.getCreatedProducts();
        dto.updatedProducts = h.getUpdatedProducts();
        dto.createdStocks = h.getCreatedStocks();
        dto.updatedStocks = h.getUpdatedStocks();
        dto.status = h.getStatus();
        dto.errorMessage = h.getErrorMessage();
        dto.failedRows = h.getFailedRows();
        dto.createdAt = h.getCreatedAt();
        if (h.getWarehouse() != null) {
            dto.warehouseId = h.getWarehouse().getId();
            // Avoid lazy property access if detached
            try {
                dto.warehouseName = h.getWarehouse().getName();
            } catch (Exception ignored) {
                dto.warehouseName = null;
            }
        }
        return dto;
    }

    /**
     * Deletes the file if it exists. Logs success or failure but doesn't throw exceptions.
     * This ensures that database record deletion can proceed even if file deletion fails.
     */
    private void deleteFileIfExists(Path filePath, String storedFilename) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Successfully deleted import file: {}", storedFilename);
            } else {
                log.warn("Import file not found (may have been already deleted): {}", storedFilename);
            }
        } catch (IOException e) {
            // Log but don't fail if file deletion fails
            // This allows database record deletion to proceed even if file is missing or locked
            log.error("Failed to delete import file: {} - Error: {}", storedFilename, e.getMessage(), e);
        }
    }
}


