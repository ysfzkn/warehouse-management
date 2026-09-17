package com.warehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Planlı bir teslimatın kapatılması: kim teslim aldı, ne zaman.
 *
 * <p>Bu isteğin kaydedilmesi ürünlerin stoktan düştüğü andır. Teslimat makbuzunun sıradan
 * teslim onayından ayrı bir istek tipi olması bilinçli: orada kâğıda bir bilgi işleniyor,
 * burada stok hareketi oluyor. İkisini tek uç noktada toplamak, "sadece ismi düzeltiyorum"
 * diye açılan formun stok düşürmesine giden en kısa yol olurdu.</p>
 */
@Data
public class ScheduledDeliveryCompleteRequest {

    /** Malı götüren kişi. Boş bırakılabilir — makbuzdaki mevcut değer korunur. */
    @Size(max = 150, message = "Teslim eden adı 150 karakteri aşamaz")
    private String deliveredByName;

    /**
     * Teslim alan. Zorunlu, çünkü bu adım stok düşüren adım: malın kime verildiği
     * yazılmadan stoktan düşmüş bir kayıt, sayım farkının kaynağı belirsiz kalır.
     */
    @NotBlank(message = "Teslim alan kişinin adı soyadı zorunludur")
    @Size(max = 150, message = "Teslim alan adı 150 karakteri aşamaz")
    private String receivedByName;

    /** Malın fiilen teslim edildiği an. Boşsa "şimdi". İleri tarih kabul edilmez. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deliveredAt;

    @Size(max = 500, message = "Not 500 karakteri aşamaz")
    private String note;
}
