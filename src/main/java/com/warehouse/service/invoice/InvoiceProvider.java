package com.warehouse.service.invoice;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;

import java.util.List;

/**
 * E-Fatura / E-Arşiv sağlayıcı arayüzü.
 * Her e-fatura sağlayıcısı (Paraşüt, Foriba, Logo vb.) bu arayüzü uygular.
 */
public interface InvoiceProvider {

    /**
     * Sağlayıcı adını döner (örn: "PARASUT", "FORIBA", "LOGO", "MOCK").
     */
    String getProviderName();

    /**
     * Bu sağlayıcı aktif ve yapılandırılmış mı?
     */
    default boolean isEnabled() { return true; }

    /**
     * Faturayı sağlayıcı üzerinden oluşturur ve GİB'e gönderir.
     *
     * @param invoice oluşturulacak fatura (DRAFT durumunda)
     * @param order   kaynak sipariş (satır detayları için)
     * @param items   sipariş kalemleri
     * @return güncellenen fatura (invoiceNumber, providerInvoiceId, status dolu)
     */
    InvoiceResult createInvoice(Invoice invoice, Order order, List<OrderItem> items);

    /**
     * Geriye uyumluluk için (eski implementasyonlar için): sadece invoice ile çağrı.
     * Yeni provider'lar createInvoice(invoice, order, items) tercih etmeli.
     */
    default InvoiceResult createInvoice(Invoice invoice) {
        return createInvoice(invoice, null, List.of());
    }

    /**
     * Fatura durumunu sağlayıcıdan sorgular.
     *
     * @param providerInvoiceId sağlayıcı tarafındaki fatura ID'si
     * @return güncel durum
     */
    InvoiceResult queryStatus(String providerInvoiceId);

    /**
     * Faturayı iptal eder.
     *
     * @param providerInvoiceId sağlayıcı tarafındaki fatura ID'si
     * @return iptal sonucu
     */
    InvoiceResult cancelInvoice(String providerInvoiceId);

    /**
     * Faturanın PDF'ini indirir.
     *
     * @param providerInvoiceId sağlayıcı tarafındaki fatura ID'si
     * @return PDF byte dizisi
     */
    byte[] downloadPdf(String providerInvoiceId);
}
