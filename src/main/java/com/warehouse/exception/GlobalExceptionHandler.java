package com.warehouse.exception;

import com.warehouse.dto.ErrorResponse;
import com.warehouse.dto.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.LazyInitializationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WarehouseManagementException.class)
    public ResponseEntity<ErrorResponse> handleWarehouseManagementException(
            WarehouseManagementException ex, HttpServletRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                && !ex.getMessage().equals(errorCode.getMessage())
                ? ex.getMessage()
                : errorCode.getMessage();
        List<String> details = null;
        // For certain custom validation codes, build a message that includes the field name
        if (errorCode == ErrorCode.REQUIRED_FIELD_MISSING && ex.getMessage() != null) {
            message = ex.getMessage() + " alanı zorunludur";
        } else if (errorCode == ErrorCode.VALUE_MUST_BE_POSITIVE && ex.getMessage() != null) {
            message = ex.getMessage() + " pozitif olmalıdır";
        } else if (errorCode == ErrorCode.VALUE_CANNOT_BE_NEGATIVE && ex.getMessage() != null) {
            message = ex.getMessage() + " negatif olamaz";
        }

        // For some business errors, include detailed info from exception args (e.g. related products)
        if (errorCode == ErrorCode.CANNOT_DELETE_WITH_PRODUCTS && ex.getArgs() != null && ex.getArgs().length > 0) {
            // User-friendly generic message + per-item details
            message = "Bu kayıt ilişkili ürünler tarafından kullanıldığı için silinemez. Detaylar listelenmiştir.";
            details = java.util.Arrays.stream(ex.getArgs())
                    .map(arg -> arg != null ? arg.toString() : "")
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.getCode(),
                errorCode.getHttpStatus().value(),
                errorCode.getHttpStatus().name(),
                message,
                request.getRequestURI(),
                details
        );
        // Log domain exception with context
        logger.warn("Domain error: code={}, path={}, message={}", errorCode.getCode(), request.getRequestURI(), message);
        return new ResponseEntity<>(errorResponse, errorCode.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse errorResponse = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Doğrulama Hatası",
                "Girdi doğrulaması başarısız. Lütfen alan hatalarını kontrol edin.",
                fieldErrors,
                request.getRequestURI()
        );
        logger.warn("Validation error at path={} details={}", request.getRequestURI(), fieldErrors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        String errorMessage = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        // Make the messages more understandable
        if (errorMessage.contains("Reserved quantity cannot be negative")) {
            errorMessage = "Rezerve miktarı negatif olamaz. Transfer iptal edilirken rezerve miktarı yetersiz olduğu için bu hata oluştu.";
        } else if (errorMessage.contains("cannot be negative")) {
            errorMessage = "Miktar negatif olamaz: " + errorMessage;
        }

        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.VALUE_CANNOT_BE_NEGATIVE.getCode(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                errorMessage,
                request.getRequestURI()
        );
        logger.warn("Constraint violation at path={} message={}", request.getRequestURI(), errorMessage);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String detailedMessage = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        String userMessage = mapIntegrityViolation(detailedMessage);

        ErrorResponse errorResponse = new ErrorResponse(
                "DB_CONSTRAINT_VIOLATION",
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                userMessage,
                request.getRequestURI()
        );

        logger.warn("Data integrity violation at path={} message={} detail={}",
                request.getRequestURI(), userMessage, detailedMessage, ex);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maps known DB constraint names to actionable Turkish messages. This is the
     * safety net — services should catch these cases proactively (e.g.
     * validateSkuUniqueness / validateSlugUniqueness), but anything that slips
     * through still reaches the admin as a meaningful sentence instead of
     * "veri bütünlüğü hatası".
     */
    private static String mapIntegrityViolation(String detail) {
        if (detail != null) {
            String d = detail.toLowerCase();
            if (d.contains("idx_products_slug")) {
                return "Bu isimde bir ürün veya set zaten mevcut. Lütfen farklı bir ad seçin.";
            }
            if (d.contains("products_sku") || (d.contains("products") && d.contains("sku"))) {
                return "Bu stok kodu (SKU) zaten başka bir üründe kullanılıyor. Lütfen farklı bir SKU girin.";
            }
            if (d.contains("uq_bundle_items")) {
                return "Aynı ürün sete birden fazla kez eklenemez.";
            }
            if (d.contains("fk_bundle_items_product") || d.contains("bundle_items")) {
                return "Bu ürün bir veya daha fazla sette kullanıldığı için silinemez. Önce ilgili setlerden çıkarın.";
            }
            if (d.contains("fk_transfer_items_product")) {
                return "Bu ürün geçmiş stok transferlerinde kullanıldığı için silinemez. "
                        + "İlgili stok transfer kayıtları silinmeden ürün silinemez.";
            }
            if (d.contains("categories") && (d.contains("name") || d.contains("uq") || d.contains("unique"))) {
                return "Bu isimde bir kategori zaten mevcut. Lütfen farklı bir ad seçin.";
            }
            if (d.contains("violates foreign key") || d.contains("foreign key constraint")) {
                return "Bu kayıt başka kayıtlarla ilişkili olduğu için işlem yapılamadı. Önce bağlı kayıtları kaldırın.";
            }
            if (d.contains("duplicate key") || d.contains("unique constraint") || d.contains("unique index")) {
                return "Aynı bilgilere sahip bir kayıt zaten mevcut. Lütfen girdiğiniz değerleri kontrol edin.";
            }
        }
        return "Veri bütünlüğü hatası oluştu. Lütfen bağlı kayıtları kontrol edin.";
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                "CONCURRENT_MODIFICATION",
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.name(),
                "Bu kayit baska bir islem tarafindan guncellendi. Lutfen sayfayi yenileyip tekrar deneyin.",
                request.getRequestURI()
        );
        logger.warn("Optimistic lock conflict at path={} entity={}", request.getRequestURI(), ex.getPersistentClassName());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<ErrorResponse> handleLazyInitializationException(
            LazyInitializationException ex, HttpServletRequest request) {
        
        logger.error("LazyInitializationException at path={} - This indicates a transaction management issue. " +
                "Ensure all entity relationships are eagerly fetched when needed.", request.getRequestURI(), ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "Veri yükleme hatası oluştu. Lütfen sayfayı yenileyip tekrar deneyin.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles timeout events for async/SSE requests.
     * For the SSE stream (/api/stream), this is considered a normal situation
     * (client closed tab, network idle, etc.), so we do not log the stack trace.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(
            AsyncRequestTimeoutException ex, HttpServletRequest request) {

        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/admin/stream")) {
            // Single-line, low-level log – should not look like an error
            logger.debug("SSE connection timeout/closed at path={}", uri);
            // No need to return a body since the SSE connection is already closed
            return ResponseEntity.noContent().build();
        }

        // Short warning-level log for other async requests
        logger.warn("Async request timed out at path={}", uri);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        // Avoid writing JSON on SSE endpoint which uses text/event-stream
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/admin/stream")) {
            logger.error("Unhandled exception on SSE stream at path={} : {}", uri, ex.getMessage(), ex);
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "Beklenmeyen bir hata oluştu.",
                request.getRequestURI()
        );
        // Log full stack trace for investigation
        logger.error("Unhandled exception at path={} : {}", request.getRequestURI(), ex.getMessage(), ex);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
