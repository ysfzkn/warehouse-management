package com.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Depo çıkış makbuzunun içeriğinin düzeltilmesi: çıkış deposu ve kalemler.
 *
 * <p>İptal edip yeniden açmanın alternatifi. O yol aynı mal için ikinci bir DC numarası
 * üretir, iptal edilen numara da arşivde karşılıksız kalırdı. Burada sevkiyat ve makbuz
 * kimliği yerinde kalıyor; stok farkı tek işlemde düzeltiliyor.</p>
 */
@Data
public class HandoverContentsUpdateRequest {

    @NotNull(message = "Çıkış deposu zorunludur")
    private Long sourceWarehouseId;

    @Valid
    @NotEmpty(message = "En az bir ürün kalmalıdır")
    private List<ServiceHandoverRequest.Item> items;

    /**
     * Zorunlu, çünkü bu düzenleme geçmişe dönük stok hareketi yaratıyor. Sayımda çıkan bir
     * farkın sebebi aylar sonra sorulduğunda cevap denetim kaydında olmalı.
     */
    @NotBlank(message = "Düzenleme sebebi zorunludur")
    @Size(max = 300, message = "Sebep 300 karakteri aşamaz")
    private String reason;

    /**
     * Formun açıldığı andaki sevkiyat sürümü. Arada bir başkası kaydı değiştirdiyse düzenleme
     * reddedilir — eski görünüm üzerinden hesaplanan stok farkı yanlış satıra yazılırdı.
     * Biçim sabitlenmiyor: sevkiyat DTO'su bu alanı salise kesriyle döndürüyor ve form onu
     * olduğu gibi geri gönderiyor.
     */
    private LocalDateTime expectedUpdatedAt;
}
