package com.warehouse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    
    // Kaynak Bulunamadı (404)
    PRODUCT_NOT_FOUND("PRODUCT_001", "Ürün bulunamadı", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND("CATEGORY_001", "Kategori bulunamadı", HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_FOUND("WAREHOUSE_001", "Depo bulunamadı", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND("BRAND_001", "Marka bulunamadı", HttpStatus.NOT_FOUND),
    COLOR_NOT_FOUND("COLOR_001", "Renk bulunamadı", HttpStatus.NOT_FOUND),
    STOCK_NOT_FOUND("STOCK_001", "Stok kaydı bulunamadı", HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND("TRANSFER_001", "Transfer kaydı bulunamadı", HttpStatus.NOT_FOUND),
    
    // Yinelenen Kayıt (409)
    PRODUCT_SKU_ALREADY_EXISTS("PRODUCT_002", "Bu SKU'ya sahip ürün zaten mevcut", HttpStatus.CONFLICT),
    CATEGORY_NAME_ALREADY_EXISTS("CATEGORY_002", "Bu isimde kategori zaten mevcut", HttpStatus.CONFLICT),
    WAREHOUSE_NAME_ALREADY_EXISTS("WAREHOUSE_002", "Bu isimde depo zaten mevcut", HttpStatus.CONFLICT),
    BRAND_NAME_ALREADY_EXISTS("BRAND_002", "Bu isimde marka zaten mevcut", HttpStatus.CONFLICT),
    COLOR_NAME_ALREADY_EXISTS("COLOR_002", "Bu isimde renk zaten mevcut", HttpStatus.CONFLICT),
    STOCK_ALREADY_EXISTS("STOCK_002", "Bu ürün için bu depoda stok kaydı zaten mevcut. Lütfen Stok Yönetimi ekranından mevcut kaydı düzenleyiniz.", HttpStatus.CONFLICT),
    
    // İş Kuralı/Doğrulama (400)
    REQUIRED_FIELD_MISSING("VALIDATION_001", "Zorunlu alan eksik", HttpStatus.BAD_REQUEST),
    INVALID_VALUE("VALIDATION_002", "Geçersiz değer", HttpStatus.BAD_REQUEST),
    VALUE_MUST_BE_POSITIVE("VALIDATION_003", "Değer pozitif olmalıdır", HttpStatus.BAD_REQUEST),
    VALUE_CANNOT_BE_NEGATIVE("VALIDATION_004", "Değer negatif olamaz", HttpStatus.BAD_REQUEST),
    
    // Stok Hataları (400)
    INSUFFICIENT_STOCK("STOCK_003", "Yetersiz stok: çıkarılmak istenen miktar kullanılabilir miktardan fazladır", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_RESERVED_STOCK("STOCK_004", "Ayrılan miktardan fazla iade edilemez", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_IN_WAREHOUSE("STOCK_005", "Ürün bu depoda bulunamadı", HttpStatus.BAD_REQUEST),
    
    // Transfer Hataları (400)
    SAME_SOURCE_DESTINATION("TRANSFER_002", "Kaynak ve hedef depolar farklı olmalıdır", HttpStatus.BAD_REQUEST),
    INVALID_TRANSFER_STATUS("TRANSFER_003", "Bu işlem için geçersiz transfer durumu", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_COMPLETED("TRANSFER_004", "Transfer zaten tamamlanmış", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_CANCELLED("TRANSFER_005", "Transfer zaten iptal edilmiş", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_COMPLETED("TRANSFER_006", "Tamamlanan transfer iptal edilemez", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_IN_TRANSIT("TRANSFER_007", "Yoldaki transfer silinemez", HttpStatus.BAD_REQUEST),

    // Tamamlanan transferlerin silinmesine artık izin veriyoruz, bu nedenle CANNOT_DELETE_COMPLETED kullanılmıyor
    CANNOT_DELETE_COMPLETED("TRANSFER_008", "Tamamlanan transfer silinemez", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_UPDATED("TRANSFER_009", "Sadece beklemedeki transferler güncellenebilir", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_STARTED("TRANSFER_010", "Sadece beklemedeki transferler başlatılabilir", HttpStatus.BAD_REQUEST),
    
    // Stok Talep Hataları
    ONLY_PENDING_REQUESTS_CAN_BE_DELETED("REQUEST_001", "Sadece beklemedeki talepler silinebilir", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACTION("AUTH_001", "Bu işlem için yetkiniz bulunmuyor", HttpStatus.FORBIDDEN),
    ADMIN_SECURITY_CODE_REQUIRED("AUTH_002", "Yönetici güvenlik şifresi doğrulanamadı", HttpStatus.BAD_REQUEST),
    INVALID_ADMIN_SECURITY_CODE("AUTH_003", "Güvenlik şifresi hatalı", HttpStatus.FORBIDDEN),
    ADMIN_SECURITY_CODE_MISMATCH("AUTH_004", "Yeni güvenlik şifreleri uyuşmuyor", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_TOO_SHORT("AUTH_005", "Yeni güvenlik şifresi en az 5 karakter olmalıdır", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_CURRENT_REQUIRED("AUTH_006", "Mevcut güvenlik şifresi zorunludur", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_NEW_REQUIRED("AUTH_007", "Yeni güvenlik şifresi zorunludur", HttpStatus.BAD_REQUEST),
    
    // İlişki Kısıtları (400)
    CANNOT_DELETE_WITH_STOCKS("RELATION_001", "İlişkili stoklar varken silme yapılamaz", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_WITH_PRODUCTS("RELATION_002", "İlişkili ürünler varken silme yapılamaz", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_WITH_SUBCATEGORIES("RELATION_003", "İlişkili alt kategoriler varken silme yapılamaz", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_PRODUCT_WITH_TRANSFERS("RELATION_004", "Aktif veya geçmiş stok transferlerinde kullanılan ürün silinemez", HttpStatus.BAD_REQUEST),
    CATEGORY_INVALID_PARENT("CATEGORY_003", "Geçersiz üst kategori", HttpStatus.BAD_REQUEST),
    
    // E-commerce Auth (400/401/409/429)
    AUTH_ERROR("AUTH_010", "Kimlik doğrulama hatası", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_011", "Hesap kilitli. Lütfen daha sonra tekrar deneyin.", HttpStatus.FORBIDDEN),
    ACCOUNT_BLACKLISTED("AUTH_012", "Hesabınız askıya alınmıştır.", HttpStatus.FORBIDDEN),
    RATE_LIMITED("AUTH_013", "Çok fazla istek gönderdiniz. Lütfen bekleyin.", HttpStatus.TOO_MANY_REQUESTS),
    GOOGLE_AUTH_FAILED("AUTH_014", "Google kimlik doğrulama başarısız.", HttpStatus.BAD_REQUEST),
    DUPLICATE_KEY("ECOM_001", "Bu kayıt zaten mevcut", HttpStatus.CONFLICT),
    VALIDATION_ERROR("ECOM_002", "Doğrulama hatası", HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_FOUND("ECOM_003", "Müşteri bulunamadı", HttpStatus.NOT_FOUND),

    // Payment (400/402/409)
    PAYMENT_INIT_FAILED("PAY_001", "Ödeme başlatma başarısız", HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_FOUND("PAY_002", "Ödeme işlem kaydı bulunamadı", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PROCESSED("PAY_003", "Bu ödeme zaten işlenmiş", HttpStatus.CONFLICT),
    PAYMENT_CALLBACK_INVALID("PAY_004", "Geçersiz ödeme callback", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_FAILED("PAY_005", "İade işlemi başarısız", HttpStatus.BAD_REQUEST),
    STOCK_RESERVATION_FAILED("PAY_006", "Stok rezervasyonu başarısız. Ürün tükenmiş olabilir.", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT("PAY_007", "Bu işlem zaten başlatılmış. Lütfen yeni işlem oluşturun.", HttpStatus.CONFLICT),
    ORDER_PAYMENT_EXPIRED("PAY_008", "Ödeme süresi dolmuş. Lütfen yeni sipariş oluşturun.", HttpStatus.BAD_REQUEST),

    // Genel (500)
    INTERNAL_SERVER_ERROR("SYSTEM_001", "Beklenmeyen bir hata oluştu", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

}

