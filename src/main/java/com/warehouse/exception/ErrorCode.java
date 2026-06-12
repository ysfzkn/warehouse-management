package com.warehouse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    
    // Resource Not Found (404)
    PRODUCT_NOT_FOUND("PRODUCT_001", "Ürün bulunamadı", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND("CATEGORY_001", "Kategori bulunamadı", HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_FOUND("WAREHOUSE_001", "Depo bulunamadı", HttpStatus.NOT_FOUND),
    BRAND_NOT_FOUND("BRAND_001", "Marka bulunamadı", HttpStatus.NOT_FOUND),
    COLOR_NOT_FOUND("COLOR_001", "Renk bulunamadı", HttpStatus.NOT_FOUND),
    STOCK_NOT_FOUND("STOCK_001", "Stok kaydı bulunamadı", HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND("TRANSFER_001", "Transfer kaydı bulunamadı", HttpStatus.NOT_FOUND),
    
    // Duplicate Record (409)
    PRODUCT_SKU_ALREADY_EXISTS("PRODUCT_002", "Bu stok kodu (SKU) zaten başka bir üründe kullanılıyor. Lütfen farklı bir SKU girin.", HttpStatus.CONFLICT),
    PRODUCT_NAME_ALREADY_EXISTS("PRODUCT_003", "Bu isimde bir ürün veya set zaten mevcut. Lütfen farklı bir ad seçin.", HttpStatus.CONFLICT),
    CATEGORY_NAME_ALREADY_EXISTS("CATEGORY_002", "Bu isimde kategori zaten mevcut", HttpStatus.CONFLICT),
    WAREHOUSE_NAME_ALREADY_EXISTS("WAREHOUSE_002", "Bu isimde depo zaten mevcut", HttpStatus.CONFLICT),
    BRAND_NAME_ALREADY_EXISTS("BRAND_002", "Bu isimde marka zaten mevcut", HttpStatus.CONFLICT),
    COLOR_NAME_ALREADY_EXISTS("COLOR_002", "Bu isimde renk zaten mevcut", HttpStatus.CONFLICT),
    STOCK_ALREADY_EXISTS("STOCK_002", "Bu ürün için bu depoda stok kaydı zaten mevcut. Lütfen Stok Yönetimi ekranından mevcut kaydı düzenleyiniz.", HttpStatus.CONFLICT),
    
    // Business Rule/Validation (400)
    REQUIRED_FIELD_MISSING("VALIDATION_001", "Zorunlu alan eksik", HttpStatus.BAD_REQUEST),
    INVALID_VALUE("VALIDATION_002", "Geçersiz değer", HttpStatus.BAD_REQUEST),
    VALUE_MUST_BE_POSITIVE("VALIDATION_003", "Değer pozitif olmalıdır", HttpStatus.BAD_REQUEST),
    VALUE_CANNOT_BE_NEGATIVE("VALIDATION_004", "Değer negatif olamaz", HttpStatus.BAD_REQUEST),
    
    // Stock Errors (400)
    INSUFFICIENT_STOCK("STOCK_003", "Yetersiz stok: çıkarılmak istenen miktar kullanılabilir miktardan fazladır", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_RESERVED_STOCK("STOCK_004", "Ayrılan miktardan fazla iade edilemez", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_IN_WAREHOUSE("STOCK_005", "Ürün bu depoda bulunamadı", HttpStatus.BAD_REQUEST),
    
    // Transfer Errors (400)
    SAME_SOURCE_DESTINATION("TRANSFER_002", "Kaynak ve hedef depolar farklı olmalıdır", HttpStatus.BAD_REQUEST),
    INVALID_TRANSFER_STATUS("TRANSFER_003", "Bu işlem için geçersiz transfer durumu", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_COMPLETED("TRANSFER_004", "Transfer zaten tamamlanmış", HttpStatus.BAD_REQUEST),
    TRANSFER_ALREADY_CANCELLED("TRANSFER_005", "Transfer zaten iptal edilmiş", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_COMPLETED("TRANSFER_006", "Tamamlanan transfer iptal edilemez", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_IN_TRANSIT("TRANSFER_007", "Yoldaki transfer silinemez", HttpStatus.BAD_REQUEST),

    // We now allow deleting completed transfers, so CANNOT_DELETE_COMPLETED is no longer used
    CANNOT_DELETE_COMPLETED("TRANSFER_008", "Tamamlanan transfer silinemez", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_UPDATED("TRANSFER_009", "Sadece beklemedeki transferler güncellenebilir", HttpStatus.BAD_REQUEST),
    ONLY_PENDING_CAN_BE_STARTED("TRANSFER_010", "Sadece beklemedeki transferler başlatılabilir", HttpStatus.BAD_REQUEST),
    
    // Stock Request Errors
    ONLY_PENDING_REQUESTS_CAN_BE_DELETED("REQUEST_001", "Sadece beklemedeki talepler silinebilir", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACTION("AUTH_001", "Bu işlem için yetkiniz bulunmuyor", HttpStatus.FORBIDDEN),
    ADMIN_SECURITY_CODE_REQUIRED("AUTH_002", "Yönetici güvenlik şifresi doğrulanamadı", HttpStatus.BAD_REQUEST),
    INVALID_ADMIN_SECURITY_CODE("AUTH_003", "Güvenlik şifresi hatalı", HttpStatus.FORBIDDEN),
    ADMIN_SECURITY_CODE_MISMATCH("AUTH_004", "Yeni güvenlik şifreleri uyuşmuyor", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_TOO_SHORT("AUTH_005", "Yeni güvenlik şifresi en az 5 karakter olmalıdır", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_CURRENT_REQUIRED("AUTH_006", "Mevcut güvenlik şifresi zorunludur", HttpStatus.BAD_REQUEST),
    ADMIN_SECURITY_CODE_NEW_REQUIRED("AUTH_007", "Yeni güvenlik şifresi zorunludur", HttpStatus.BAD_REQUEST),
    
    // Relationship Constraints (400)
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

    // Order (404)
    ORDER_NOT_FOUND("ORDER_001", "Sipariş bulunamadı", HttpStatus.NOT_FOUND),

    // Invoice / E-Fatura (400/404)
    INVOICE_NOT_FOUND("INV_001", "Fatura bulunamadı", HttpStatus.NOT_FOUND),
    INVOICE_CANNOT_REGENERATE("INV_002", "Sadece DRAFT veya ERROR durumundaki faturalar yeniden oluşturulabilir", HttpStatus.BAD_REQUEST),
    INVOICE_ALREADY_CANCELLED("INV_003", "Fatura zaten iptal edilmiş", HttpStatus.BAD_REQUEST),
    INVOICE_PDF_NOT_AVAILABLE("INV_004", "Fatura PDF'i henüz mevcut değil", HttpStatus.BAD_REQUEST),
    INVOICE_CREATION_FAILED("INV_005", "Fatura oluşturma başarısız", HttpStatus.BAD_REQUEST),

    // AI set cover generation (400/502/504)
    AI_COVER_NOT_A_BUNDLE("AICOVER_001", "Bu işlem yalnızca ürün setleri için kullanılabilir", HttpStatus.BAD_REQUEST),
    AI_COVER_NOT_A_MEMBER("AICOVER_002", "Seçilen ürün bu setin üyesi değil", HttpStatus.BAD_REQUEST),
    AI_COVER_IMAGE_NOT_OWNED("AICOVER_003", "Seçilen görsel bu üye ürüne ait değil", HttpStatus.BAD_REQUEST),
    AI_COVER_INPUT_MISSING("AICOVER_004", "Her set üyesi için bir giriş fotoğrafı seçilmelidir", HttpStatus.BAD_REQUEST),
    AI_COVER_API_KEY_MISSING("AICOVER_005", "Görsel üretimi için API anahtarı yapılandırılmamış. Asistan ayarlarından OpenAI API anahtarını girin.", HttpStatus.BAD_REQUEST),
    AI_COVER_GENERATION_FAILED("AICOVER_006", "Yapay zeka kapak fotoğrafı oluşturulamadı", HttpStatus.BAD_GATEWAY),
    AI_COVER_GENERATION_TIMEOUT("AICOVER_007", "Yapay zeka kapak fotoğrafı üretimi zaman aşımına uğradı. Lütfen tekrar deneyin.", HttpStatus.GATEWAY_TIMEOUT),
    AI_COVER_UNSUPPORTED_FORMAT("AICOVER_008", "Seçilen fotoğrafın formatı desteklenmiyor (ör. AVIF/HEIC). Lütfen JPEG, PNG veya WebP formatında bir fotoğraf seçin veya yükleyin.", HttpStatus.BAD_REQUEST),

    // General (500)
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

