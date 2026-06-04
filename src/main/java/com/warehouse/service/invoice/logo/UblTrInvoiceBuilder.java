package com.warehouse.service.invoice.logo;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * e-Invoice XML generator in UBL-TR format.
 *
 * UBL-TR is the standard e-Invoice format defined by the Turkish Revenue Administration (GIB).
 * This builder produces the basic XML structure for the EARSIV (individual customer)
 * and EFATURA (corporate customer) formats.
 *
 * NOTE: A Logo Connect / eLogo design file (design_file) may be required for the
 * actual production XML. The Logo SendInvoice method requires a template field,
 * and the XML returned by this method is applied to that template.
 */
public class UblTrInvoiceBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Produces the complete UBL-TR Invoice XML.
     *
     * @param invoice   Our Invoice entity
     * @param order     Order (for line items)
     * @param items     Order items
     * @param companyInfo Seller company info (as a map from site_settings)
     * @return XML string in UBL-TR format
     */
    public static String build(Invoice invoice, Order order, List<OrderItem> items, Map<String, String> companyInfo) {
        String ettn = invoice.getEttn() != null ? invoice.getEttn() : UUID.randomUUID().toString();
        invoice.setEttn(ettn);

        LocalDate issueDate = LocalDate.now();
        String invoiceNumber = invoice.getInvoiceNumber() != null
                ? invoice.getInvoiceNumber()
                : "";

        // Recipient type: individual (TCKN 11 digits) or corporate (VKN 10 digits)?
        boolean individual = invoice.isIndividual();
        boolean isCreditNote = invoice.isCreditNote();

        // ProfileID selection:
        //   - Credit note (return invoice): "IADE"
        //   - Individual normal invoice: "EARSIVFATURA"
        //   - Corporate normal invoice: "TICARIFATURA"
        String profileId = isCreditNote ? "IADE" : (individual ? "EARSIVFATURA" : "TICARIFATURA");

        // InvoiceTypeCode:
        //   - Credit note: "IADE"
        //   - Normal: "SATIS"
        String invoiceTypeCode = isCreditNote ? "IADE" : "SATIS";

        String taxId = invoice.getRecipientTaxId() != null ? invoice.getRecipientTaxId() : "11111111111";
        String partyIdSchemeId = individual ? "TCKN" : "VKN";

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\" ");
        xml.append("xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\" ");
        xml.append("xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" ");
        xml.append("xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\" ");
        xml.append("xmlns:xades=\"http://uri.etsi.org/01903/v1.3.2#\" ");
        xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        // UBL Version
        xml.append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>TR1.2</cbc:CustomizationID>\n");
        xml.append("  <cbc:ProfileID>").append(profileId).append("</cbc:ProfileID>\n");

        // Invoice ID + UUID (ETTN)
        xml.append("  <cbc:ID>").append(escape(invoiceNumber)).append("</cbc:ID>\n");
        xml.append("  <cbc:CopyIndicator>false</cbc:CopyIndicator>\n");
        xml.append("  <cbc:UUID>").append(ettn).append("</cbc:UUID>\n");
        xml.append("  <cbc:IssueDate>").append(issueDate.format(DATE_FORMAT)).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:IssueTime>").append(java.time.LocalTime.now().format(TIME_FORMAT)).append("</cbc:IssueTime>\n");
        xml.append("  <cbc:InvoiceTypeCode>").append(invoiceTypeCode).append("</cbc:InvoiceTypeCode>\n");
        // Reason for credit note — written to the Note field
        if (isCreditNote && invoice.getCreditNoteReason() != null && !invoice.getCreditNoteReason().isBlank()) {
            xml.append("  <cbc:Note>").append(escape(invoice.getCreditNoteReason())).append("</cbc:Note>\n");
        }
        xml.append("  <cbc:DocumentCurrencyCode>TRY</cbc:DocumentCurrencyCode>\n");
        xml.append("  <cbc:LineCountNumeric>").append(items.size()).append("</cbc:LineCountNumeric>\n");

        // Order Reference (order number)
        if (order.getOrderNumber() != null) {
            xml.append("  <cac:OrderReference>\n");
            xml.append("    <cbc:ID>").append(escape(order.getOrderNumber())).append("</cbc:ID>\n");
            xml.append("    <cbc:IssueDate>").append(issueDate.format(DATE_FORMAT)).append("</cbc:IssueDate>\n");
            xml.append("  </cac:OrderReference>\n");
        }

        // BillingReference to the original invoice for credit notes (mandatory)
        if (isCreditNote && invoice.getCreditedInvoice() != null) {
            Invoice original = invoice.getCreditedInvoice();
            xml.append("  <cac:BillingReference>\n");
            xml.append("    <cac:InvoiceDocumentReference>\n");
            xml.append("      <cbc:ID>").append(escape(original.getInvoiceNumber() != null ? original.getInvoiceNumber() : "")).append("</cbc:ID>\n");
            xml.append("      <cbc:IssueDate>").append(
                    original.getIssuedAt() != null ? original.getIssuedAt().toLocalDate().format(DATE_FORMAT)
                                                    : issueDate.format(DATE_FORMAT)).append("</cbc:IssueDate>\n");
            if (original.getEttn() != null) {
                xml.append("      <cbc:UUID>").append(escape(original.getEttn())).append("</cbc:UUID>\n");
            }
            xml.append("      <cbc:DocumentTypeCode>SATIS</cbc:DocumentTypeCode>\n");
            xml.append("    </cac:InvoiceDocumentReference>\n");
            xml.append("  </cac:BillingReference>\n");
        }

        // Seller (Supplier)
        xml.append("  <cac:AccountingSupplierParty>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cbc:WebsiteURI>").append(escape(companyInfo.getOrDefault("logo_company_website", ""))).append("</cbc:WebsiteURI>\n");
        xml.append("      <cac:PartyIdentification>\n");
        xml.append("        <cbc:ID schemeID=\"VKN\">").append(escape(companyInfo.getOrDefault("logo_company_vkn", ""))).append("</cbc:ID>\n");
        xml.append("      </cac:PartyIdentification>\n");
        String mersis = companyInfo.get("logo_company_mersis_no");
        if (mersis != null && !mersis.isBlank()) {
            xml.append("      <cac:PartyIdentification>\n");
            xml.append("        <cbc:ID schemeID=\"MERSISNO\">").append(escape(mersis)).append("</cbc:ID>\n");
            xml.append("      </cac:PartyIdentification>\n");
        }
        xml.append("      <cac:PartyName>\n");
        xml.append("        <cbc:Name>").append(escape(companyInfo.getOrDefault("logo_company_title", ""))).append("</cbc:Name>\n");
        xml.append("      </cac:PartyName>\n");
        xml.append("      <cac:PostalAddress>\n");
        xml.append("        <cbc:StreetName>").append(escape(companyInfo.getOrDefault("logo_company_address", ""))).append("</cbc:StreetName>\n");
        xml.append("        <cbc:CitySubdivisionName>").append(escape(companyInfo.getOrDefault("logo_company_district", ""))).append("</cbc:CitySubdivisionName>\n");
        xml.append("        <cbc:CityName>").append(escape(companyInfo.getOrDefault("logo_company_city", ""))).append("</cbc:CityName>\n");
        xml.append("        <cbc:PostalZone>").append(escape(companyInfo.getOrDefault("logo_company_postal_code", ""))).append("</cbc:PostalZone>\n");
        xml.append("        <cac:Country>\n");
        xml.append("          <cbc:Name>").append(escape(companyInfo.getOrDefault("logo_company_country", "Türkiye"))).append("</cbc:Name>\n");
        xml.append("        </cac:Country>\n");
        xml.append("      </cac:PostalAddress>\n");
        xml.append("      <cac:PartyTaxScheme>\n");
        xml.append("        <cac:TaxScheme>\n");
        xml.append("          <cbc:Name>").append(escape(companyInfo.getOrDefault("logo_company_tax_office", ""))).append("</cbc:Name>\n");
        xml.append("        </cac:TaxScheme>\n");
        xml.append("      </cac:PartyTaxScheme>\n");
        xml.append("      <cac:Contact>\n");
        xml.append("        <cbc:Telephone>").append(escape(companyInfo.getOrDefault("logo_company_phone", ""))).append("</cbc:Telephone>\n");
        xml.append("        <cbc:ElectronicMail>").append(escape(companyInfo.getOrDefault("logo_company_email", ""))).append("</cbc:ElectronicMail>\n");
        xml.append("      </cac:Contact>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingSupplierParty>\n");

        // Recipient (Customer)
        xml.append("  <cac:AccountingCustomerParty>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyIdentification>\n");
        xml.append("        <cbc:ID schemeID=\"").append(partyIdSchemeId).append("\">").append(escape(taxId)).append("</cbc:ID>\n");
        xml.append("      </cac:PartyIdentification>\n");

        if (individual) {
            // Individual: PersonName (First name + Last name)
            String[] nameParts = splitName(invoice.getRecipientName());
            xml.append("      <cac:Person>\n");
            xml.append("        <cbc:FirstName>").append(escape(nameParts[0])).append("</cbc:FirstName>\n");
            xml.append("        <cbc:FamilyName>").append(escape(nameParts[1])).append("</cbc:FamilyName>\n");
            xml.append("      </cac:Person>\n");
        } else {
            // Corporate: PartyName
            xml.append("      <cac:PartyName>\n");
            xml.append("        <cbc:Name>").append(escape(invoice.getRecipientName())).append("</cbc:Name>\n");
            xml.append("      </cac:PartyName>\n");
        }

        xml.append("      <cac:PostalAddress>\n");
        xml.append("        <cbc:StreetName>").append(escape(nullToEmpty(invoice.getRecipientAddress()))).append("</cbc:StreetName>\n");
        if (invoice.getRecipientDistrict() != null) {
            xml.append("        <cbc:CitySubdivisionName>").append(escape(invoice.getRecipientDistrict())).append("</cbc:CitySubdivisionName>\n");
        }
        if (invoice.getRecipientCity() != null) {
            xml.append("        <cbc:CityName>").append(escape(invoice.getRecipientCity())).append("</cbc:CityName>\n");
        }
        if (invoice.getRecipientPostalCode() != null) {
            xml.append("        <cbc:PostalZone>").append(escape(invoice.getRecipientPostalCode())).append("</cbc:PostalZone>\n");
        }
        xml.append("        <cac:Country>\n");
        xml.append("          <cbc:Name>Türkiye</cbc:Name>\n");
        xml.append("        </cac:Country>\n");
        xml.append("      </cac:PostalAddress>\n");

        if (!individual && invoice.getRecipientTaxOffice() != null) {
            xml.append("      <cac:PartyTaxScheme>\n");
            xml.append("        <cac:TaxScheme>\n");
            xml.append("          <cbc:Name>").append(escape(invoice.getRecipientTaxOffice())).append("</cbc:Name>\n");
            xml.append("        </cac:TaxScheme>\n");
            xml.append("      </cac:PartyTaxScheme>\n");
        }

        if (invoice.getRecipientPhone() != null || invoice.getRecipientEmail() != null) {
            xml.append("      <cac:Contact>\n");
            if (invoice.getRecipientPhone() != null) {
                xml.append("        <cbc:Telephone>").append(escape(invoice.getRecipientPhone())).append("</cbc:Telephone>\n");
            }
            if (invoice.getRecipientEmail() != null) {
                xml.append("        <cbc:ElectronicMail>").append(escape(invoice.getRecipientEmail())).append("</cbc:ElectronicMail>\n");
            }
            xml.append("      </cac:Contact>\n");
        }
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingCustomerParty>\n");

        // Invoice lines (InvoiceLine)
        // Each line contains VAT + (if present) SCT blocks.
        // SCT is critical for white goods / electronics — TaxTypeCode=4080 in UBL-TR 1.2
        BigDecimal totalSctAmount = BigDecimal.ZERO;
        int lineId = 1;
        for (OrderItem item : items) {
            BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            BigDecimal lineTotal = unitPrice.multiply(qty);
            BigDecimal vatRate = item.getVatRate() != null ? item.getVatRate() : BigDecimal.ZERO;
            BigDecimal vatAmount = lineTotal.multiply(vatRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal sctRate = item.getSctRate() != null ? item.getSctRate() : BigDecimal.ZERO;
            BigDecimal sctAmount = sctRate.compareTo(BigDecimal.ZERO) > 0
                    ? lineTotal.multiply(sctRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            totalSctAmount = totalSctAmount.add(sctAmount);

            // Line total tax: VAT + SCT
            BigDecimal lineTaxTotal = vatAmount.add(sctAmount);

            xml.append("  <cac:InvoiceLine>\n");
            xml.append("    <cbc:ID>").append(lineId++).append("</cbc:ID>\n");
            xml.append("    <cbc:InvoicedQuantity unitCode=\"NIU\">").append(qty.toPlainString()).append("</cbc:InvoicedQuantity>\n");
            xml.append("    <cbc:LineExtensionAmount currencyID=\"TRY\">").append(lineTotal.toPlainString()).append("</cbc:LineExtensionAmount>\n");

            xml.append("    <cac:TaxTotal>\n");
            xml.append("      <cbc:TaxAmount currencyID=\"TRY\">").append(lineTaxTotal.toPlainString()).append("</cbc:TaxAmount>\n");

            // VAT (TaxTypeCode 0015)
            xml.append("      <cac:TaxSubtotal>\n");
            xml.append("        <cbc:TaxableAmount currencyID=\"TRY\">").append(lineTotal.toPlainString()).append("</cbc:TaxableAmount>\n");
            xml.append("        <cbc:TaxAmount currencyID=\"TRY\">").append(vatAmount.toPlainString()).append("</cbc:TaxAmount>\n");
            xml.append("        <cbc:Percent>").append(vatRate.toPlainString()).append("</cbc:Percent>\n");
            xml.append("        <cac:TaxCategory>\n");
            xml.append("          <cac:TaxScheme>\n");
            xml.append("            <cbc:Name>KDV</cbc:Name>\n");
            xml.append("            <cbc:TaxTypeCode>0015</cbc:TaxTypeCode>\n");
            xml.append("          </cac:TaxScheme>\n");
            xml.append("        </cac:TaxCategory>\n");
            xml.append("      </cac:TaxSubtotal>\n");

            // SCT (TaxTypeCode 4080) — if present
            if (sctRate.compareTo(BigDecimal.ZERO) > 0) {
                xml.append("      <cac:TaxSubtotal>\n");
                xml.append("        <cbc:TaxableAmount currencyID=\"TRY\">").append(lineTotal.toPlainString()).append("</cbc:TaxableAmount>\n");
                xml.append("        <cbc:TaxAmount currencyID=\"TRY\">").append(sctAmount.toPlainString()).append("</cbc:TaxAmount>\n");
                xml.append("        <cbc:Percent>").append(sctRate.toPlainString()).append("</cbc:Percent>\n");
                xml.append("        <cac:TaxCategory>\n");
                xml.append("          <cac:TaxScheme>\n");
                xml.append("            <cbc:Name>ÖTV</cbc:Name>\n");
                xml.append("            <cbc:TaxTypeCode>4080</cbc:TaxTypeCode>\n");
                xml.append("          </cac:TaxScheme>\n");
                xml.append("        </cac:TaxCategory>\n");
                xml.append("      </cac:TaxSubtotal>\n");
            }

            xml.append("    </cac:TaxTotal>\n");

            xml.append("    <cac:Item>\n");
            String productName = item.getProduct() != null ? item.getProduct().getName() : "Ürün";
            String sku = item.getProduct() != null ? item.getProduct().getSku() : "";
            xml.append("      <cbc:Name>").append(escape(productName)).append("</cbc:Name>\n");
            if (sku != null && !sku.isBlank()) {
                xml.append("      <cac:SellersItemIdentification>\n");
                xml.append("        <cbc:ID>").append(escape(sku)).append("</cbc:ID>\n");
                xml.append("      </cac:SellersItemIdentification>\n");
            }
            xml.append("    </cac:Item>\n");

            xml.append("    <cac:Price>\n");
            xml.append("      <cbc:PriceAmount currencyID=\"TRY\">").append(unitPrice.toPlainString()).append("</cbc:PriceAmount>\n");
            xml.append("    </cac:Price>\n");
            xml.append("  </cac:InvoiceLine>\n");
        }

        // Total taxes (VAT + SCT)
        BigDecimal vatTotal = invoice.getVatAmount() != null ? invoice.getVatAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = invoice.getSubtotal() != null ? invoice.getSubtotal() : BigDecimal.ZERO;
        BigDecimal grandTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        // Use the SCT total provided by the order if present, otherwise the one derived from the line totals
        BigDecimal sctTotal = order.getSctTotal() != null
                ? order.getSctTotal()
                : totalSctAmount;
        BigDecimal taxGrandTotal = vatTotal.add(sctTotal);

        xml.append("  <cac:TaxTotal>\n");
        xml.append("    <cbc:TaxAmount currencyID=\"TRY\">").append(taxGrandTotal.toPlainString()).append("</cbc:TaxAmount>\n");
        // Tax type breakdown (VAT)
        xml.append("    <cac:TaxSubtotal>\n");
        xml.append("      <cbc:TaxAmount currencyID=\"TRY\">").append(vatTotal.toPlainString()).append("</cbc:TaxAmount>\n");
        xml.append("      <cac:TaxCategory>\n");
        xml.append("        <cac:TaxScheme>\n");
        xml.append("          <cbc:Name>KDV</cbc:Name>\n");
        xml.append("          <cbc:TaxTypeCode>0015</cbc:TaxTypeCode>\n");
        xml.append("        </cac:TaxScheme>\n");
        xml.append("      </cac:TaxCategory>\n");
        xml.append("    </cac:TaxSubtotal>\n");
        // Tax type breakdown (SCT) — only if present
        if (sctTotal.compareTo(BigDecimal.ZERO) > 0) {
            xml.append("    <cac:TaxSubtotal>\n");
            xml.append("      <cbc:TaxAmount currencyID=\"TRY\">").append(sctTotal.toPlainString()).append("</cbc:TaxAmount>\n");
            xml.append("      <cac:TaxCategory>\n");
            xml.append("        <cac:TaxScheme>\n");
            xml.append("          <cbc:Name>ÖTV</cbc:Name>\n");
            xml.append("          <cbc:TaxTypeCode>4080</cbc:TaxTypeCode>\n");
            xml.append("        </cac:TaxScheme>\n");
            xml.append("      </cac:TaxCategory>\n");
            xml.append("    </cac:TaxSubtotal>\n");
        }
        xml.append("  </cac:TaxTotal>\n");

        // Monetary Totals
        xml.append("  <cac:LegalMonetaryTotal>\n");
        xml.append("    <cbc:LineExtensionAmount currencyID=\"TRY\">").append(subtotal.toPlainString()).append("</cbc:LineExtensionAmount>\n");
        xml.append("    <cbc:TaxExclusiveAmount currencyID=\"TRY\">").append(subtotal.toPlainString()).append("</cbc:TaxExclusiveAmount>\n");
        xml.append("    <cbc:TaxInclusiveAmount currencyID=\"TRY\">").append(grandTotal.toPlainString()).append("</cbc:TaxInclusiveAmount>\n");
        xml.append("    <cbc:PayableAmount currencyID=\"TRY\">").append(grandTotal.toPlainString()).append("</cbc:PayableAmount>\n");
        xml.append("  </cac:LegalMonetaryTotal>\n");

        xml.append("</Invoice>\n");

        return xml.toString();
    }

    // ===== Helpers =====

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** "Ahmet Yılmaz" -> ["Ahmet", "Yılmaz"] */
    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) return new String[]{"", ""};
        String[] parts = fullName.trim().split("\\s+", 2);
        return parts.length == 1 ? new String[]{parts[0], ""} : parts;
    }
}
