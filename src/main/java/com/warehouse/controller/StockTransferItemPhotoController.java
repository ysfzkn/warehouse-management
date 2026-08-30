package com.warehouse.controller;

import com.warehouse.security.UploadValidator;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.StockTransferItemPhoto;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.StockTransferItemPhotoRepository;
import com.warehouse.repository.StockTransferItemRepository;
import com.warehouse.service.PhotoStorageService;
import com.warehouse.service.AdminSecurityService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/stock-transfer-items")
@Slf4j
public class StockTransferItemPhotoController {

    /** Label bound into the signature so it cannot be replayed against another endpoint. */
    private static final String SIGNED_RESOURCE = "transfer-item-photo";

    private final StockTransferItemPhotoRepository photoRepository;
    private final StockTransferItemRepository itemRepository;
    private final PhotoStorageService photoStorageService;
    private final AdminSecurityService adminSecurityService;

    public StockTransferItemPhotoController(StockTransferItemPhotoRepository photoRepository,
                                            StockTransferItemRepository itemRepository,
                                            PhotoStorageService photoStorageService,
                                            AdminSecurityService adminSecurityService) {
        this.photoRepository = photoRepository;
        this.itemRepository = itemRepository;
        this.photoStorageService = photoStorageService;
        this.adminSecurityService = adminSecurityService;
    }

    @PostMapping("/{itemId}/photo")
    @Transactional
    public ResponseEntity<?> uploadPhoto(@PathVariable Long itemId,
                                         @RequestParam("file") @NotNull MultipartFile file) {
        if (file.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.REQUIRED_FIELD_MISSING, "Fotoğraf dosyası boş olamaz");
        }

        // Magic-byte identification: the declared Content-Type is caller-controlled.
        UploadValidator.ImageType imageType;
        try {
            imageType = UploadValidator.validateImage(file, 12L * 1024 * 1024);
        } catch (UploadValidator.InvalidUploadException e) {
            throw new WarehouseManagementException(ErrorCode.INVALID_VALUE, e.getMessage());
        }
        String contentType = imageType.contentType;

        StockTransferItem item = findItemOrThrow(itemId);

        try (InputStream is = file.getInputStream()) {
            // If a photo already exists, delete the old files and update the record
            Optional<StockTransferItemPhoto> existingOpt = photoRepository.findByItem(item);
            StockTransferItemPhoto photo;
            
            if (existingOpt.isPresent()) {
                // Update the existing record
                photo = existingOpt.get();
                // Delete the old files
                photoStorageService.deletePhotoFiles(photo.getRelativePath(), photo.getThumbnailPath());
            } else {
                // Create a new record
                photo = new StockTransferItemPhoto();
                photo.setItem(item);
            }

            PhotoStorageService.StoredPhoto stored = photoStorageService.storeItemPhoto(
                    item.getTransfer().getId(),
                    item.getId(),
                    "upload." + imageType.extension,
                    contentType,
                    is
            );

            // Update the photo info
            photo.setFileName(stored.fileName());
            photo.setRelativePath(stored.relativePath());
            photo.setThumbnailPath(stored.thumbnailPath());
            photo.setContentType(stored.contentType());
            photo.setSizeBytes(stored.sizeBytes());
            photo.setWidth(stored.width());
            photo.setHeight(stored.height());

            StockTransferItemPhoto saved = photoRepository.save(photo);
            item.setPhoto(saved);

            Map<String, Object> body = new HashMap<>();
            body.put("id", saved.getId());
            body.put("itemId", item.getId());
            body.put("contentType", saved.getContentType());
            body.put("sizeBytes", saved.getSizeBytes());
            body.put("width", saved.getWidth());
            body.put("height", saved.getHeight());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (Exception e) {
            log.error("Error uploading photo for stock transfer item {}: {}", itemId, e.getMessage(), e);
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR, "Fotoğraf yüklenirken hata oluştu: " + e.getMessage());
        }
    }

    @DeleteMapping("/{itemId}/photo")
    @Transactional
    public ResponseEntity<Void> deletePhoto(@PathVariable Long itemId,
                                            @RequestHeader(value = "X-ADMIN-SECURITY-CODE", required = false) String adminSecurityCode) {
        adminSecurityService.requireSecurityCodeForAdmin(adminSecurityCode);
        StockTransferItem item = findItemOrThrow(itemId);
        Optional<StockTransferItemPhoto> existingOpt = photoRepository.findByItem(item);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        StockTransferItemPhoto existing = existingOpt.get();
        photoStorageService.deletePhotoFiles(existing.getRelativePath(), existing.getThumbnailPath());
        photoRepository.delete(existing);
        item.setPhoto(null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{itemId}/photo")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPhotoMeta(@PathVariable Long itemId) {
        StockTransferItem item = findItemOrThrow(itemId);
        Optional<StockTransferItemPhoto> existingOpt = photoRepository.findByItem(item);
        Map<String, Object> body = new HashMap<>();

        if (existingOpt.isEmpty()) {
            body.put("itemId", item.getId());
            body.put("hasPhoto", false);
            return ResponseEntity.ok(body);
        }

        StockTransferItemPhoto photo = existingOpt.get();
        body.put("id", photo.getId());
        body.put("itemId", item.getId());
        body.put("hasPhoto", true);
        body.put("contentType", photo.getContentType());
        body.put("sizeBytes", photo.getSizeBytes());
        body.put("width", photo.getWidth());
        body.put("height", photo.getHeight());
        body.put("thumbnailUrl", buildViewUrl(itemId, true));
        body.put("viewUrl", buildViewUrl(itemId, false));
        return ResponseEntity.ok(body);
    }

    /**
     * Streams a warehouse delivery photo.
     *
     * <p>Reachable without a session because {@code <img>} cannot send the Bearer token,
     * but the URL must carry a signature this server issued for this item. Previously the
     * endpoint was {@code permitAll} and keyed on a sequential item id, so the whole
     * archive of transfer photos could be walked from outside.</p>
     */
    @GetMapping("/{itemId}/photo/view")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> viewPhoto(@PathVariable Long itemId,
                                            @RequestParam(name = "thumbnail", defaultValue = "false") boolean thumbnail,
                                            @RequestParam(value = "exp", required = false) String exp,
                                            @RequestParam(value = "sig", required = false) String sig) {
        if (!com.warehouse.security.SignedUrlService.valid(SIGNED_RESOURCE, itemId, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        StockTransferItem item = findItemOrThrow(itemId);
        StockTransferItemPhoto photo = photoRepository.findByItem(item)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.TRANSFER_NOT_FOUND, "Fotoğraf bulunamadı"));

        String path = thumbnail ? photo.getThumbnailPath() : photo.getRelativePath();
        log.debug("Viewing photo for itemId={}, thumbnail={}, path={}", itemId, thumbnail, path);
        
        try (InputStream is = thumbnail
                ? photoStorageService.openThumbnailStream(path)
                : photoStorageService.openPhotoStream(path)) {
            byte[] bytes = is.readAllBytes();
            if (bytes.length == 0) {
                log.warn("Photo file is empty for itemId={}, path={}", itemId, path);
                throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR, "Fotoğraf dosyası boş");
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(photo.getContentType()));
            headers.setContentLength(bytes.length);
            if (!thumbnail && StringUtils.hasText(photo.getFileName())) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + photo.getFileName() + "\"");
            }
            log.debug("Successfully read photo for itemId={}, size={} bytes", itemId, bytes.length);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            // If it's a WarehouseManagementException, rethrow it
            if (e instanceof WarehouseManagementException) {
                log.error("Error viewing photo for itemId={}, path={}: {}", itemId, path, e.getMessage(), e);
                throw e;
            }
            // Check if it's a "file does not exist" error
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                log.warn("Photo file not found for itemId={}, path={}. This may happen if the server was restarted and files were lost (Railway /tmp is ephemeral). Error: {}", 
                        itemId, path, e.getMessage());
                // Return 404 with a helpful message
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(("Fotoğraf dosyası bulunamadı. Dosya sunucu yeniden başlatıldığında kaybolmuş olabilir. " +
                                "Lütfen fotoğrafı yeniden yükleyin.").getBytes());
            }
            log.error("Error viewing photo for itemId={}, path={}: {}", itemId, path, e.getMessage(), e);
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR, "Fotoğraf okunamadı: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error viewing photo for itemId={}, path={}: {}", itemId, path, e.getMessage(), e);
            throw new WarehouseManagementException(ErrorCode.INTERNAL_SERVER_ERROR, "Fotoğraf okunamadı: " + e.getMessage());
        }
    }

    private StockTransferItem findItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new WarehouseManagementException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer satırı bulunamadı"));
    }

    /**
     * Warehouse delivery photos are served to {@code <img>} tags without an auth header,
     * so the URL itself carries the authorisation: a signature this server issued for
     * this item, valid for a few hours. Without it the sequential item ids let anyone
     * page through every transfer photo in the system.
     */
    private String buildViewUrl(Long itemId, boolean thumbnail) {
        return "/api/stock-transfer-items/" + itemId + "/photo/view"
                + com.warehouse.security.SignedUrlService.query(SIGNED_RESOURCE, itemId)
                + (thumbnail ? "&thumbnail=true" : "");
    }
}


