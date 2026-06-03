package com.warehouse.service.invoice;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;

import java.util.List;

/**
 * E-Fatura / e-Arşiv provider interface.
 * Every e-invoice provider (Paraşüt, Foriba, Logo, etc.) implements this interface.
 */
public interface InvoiceProvider {

    /**
     * Returns the provider name (e.g. "PARASUT", "FORIBA", "LOGO", "MOCK").
     */
    String getProviderName();

    /**
     * Is this provider enabled and configured?
     */
    default boolean isEnabled() { return true; }

    /**
     * Creates the invoice through the provider and submits it to GİB.
     *
     * @param invoice the invoice to create (in DRAFT state)
     * @param order   the source order (for line item details)
     * @param items   the order items
     * @return the updated invoice (invoiceNumber, providerInvoiceId, status populated)
     */
    InvoiceResult createInvoice(Invoice invoice, Order order, List<OrderItem> items);

    /**
     * For backward compatibility (for older implementations): call with the invoice only.
     * New providers should prefer createInvoice(invoice, order, items).
     */
    default InvoiceResult createInvoice(Invoice invoice) {
        return createInvoice(invoice, null, List.of());
    }

    /**
     * Queries the invoice status from the provider.
     *
     * @param providerInvoiceId the invoice ID on the provider side
     * @return the current status
     */
    InvoiceResult queryStatus(String providerInvoiceId);

    /**
     * Cancels the invoice.
     *
     * @param providerInvoiceId the invoice ID on the provider side
     * @return the cancellation result
     */
    InvoiceResult cancelInvoice(String providerInvoiceId);

    /**
     * Downloads the invoice PDF.
     *
     * @param providerInvoiceId the invoice ID on the provider side
     * @return the PDF byte array
     */
    byte[] downloadPdf(String providerInvoiceId);

    /**
     * Checks whether a registered e-Fatura taxpayer exists in GİB for this VKN/TCKN.
     * For corporate customers, the E_FATURA vs E_ARSIV choice is made based on this result.
     *
     * @param taxId a 10-digit VKN or 11-digit TCKN
     * @return {@code true} = registered e-Fatura taxpayer in GİB → E_FATURA can be issued
     *         {@code false} = not registered → E_ARSIV must be issued.
     *         If the provider does not support this, the default {@code false} is returned (safe side).
     */
    default boolean isGibRegistered(String taxId) {
        return false;
    }
}
