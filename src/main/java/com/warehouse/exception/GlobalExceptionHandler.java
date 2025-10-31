package com.warehouse.exception;

import com.warehouse.dto.ErrorResponse;
import com.warehouse.dto.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WarehouseManagementException.class)
    public ResponseEntity<ErrorResponse> handleWarehouseManagementException(
            WarehouseManagementException ex, HttpServletRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        String message = errorCode.getMessage();
        // Özel bazı doğrulama kodları için alan adı ekleyerek Türkçe mesaj üret
        if (errorCode == ErrorCode.REQUIRED_FIELD_MISSING && ex.getMessage() != null) {
            message = ex.getMessage() + " alanı zorunludur";
        } else if (errorCode == ErrorCode.VALUE_MUST_BE_POSITIVE && ex.getMessage() != null) {
            message = ex.getMessage() + " pozitif olmalıdır";
        } else if (errorCode == ErrorCode.VALUE_CANNOT_BE_NEGATIVE && ex.getMessage() != null) {
            message = ex.getMessage() + " negatif olamaz";
        }

        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.getCode(),
                errorCode.getHttpStatus().value(),
                errorCode.getHttpStatus().name(),
                message,
                request.getRequestURI()
        );
        
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
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        ErrorResponse errorResponse = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "Beklenmeyen bir hata oluştu.",
                request.getRequestURI()
        );
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
