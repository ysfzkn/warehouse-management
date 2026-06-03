package com.warehouse.service.invoice.logo;

import com.warehouse.entity.Invoice;
import com.warehouse.entity.Order;
import com.warehouse.entity.OrderItem;
import com.warehouse.enums.InvoiceStatus;
import com.warehouse.service.SiteSettingService;
import com.warehouse.service.invoice.InvoiceProvider;
import com.warehouse.service.invoice.InvoiceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Logo eLogo e-Invoice / e-Archive SOAP API integration.
 *
 * Sends invoices in UBL-TR format using the eLogo Postbox Service.
 *
 * API Endpoints:
 * - Test: https://pb-demo.elogo.com.tr/PostboxService.svc
 * - Production: https://pb.elogo.com.tr/PostboxService.svc
 *
 * SOAP Operations (WCF tempuri.org namespace):
 * - Login(login: {username, password}) → sessionID
 * - Logout(sessionID)
 * - SendDocument(sessionID, paramList, document) → sends e-Invoice/e-Archive
 * - getInvoiceStatus(sessionID, uuid) → invoice status
 * - GetDocumentData(sessionID, uuid, docType, dataType) → download PDF/XML
 * - CheckGibUser(sessionID, vknTcknList) → GIB customer check
 *
 * Invoice submission flow:
 * 1. UBL-TR XML → packaged inside a ZIP (xml file)
 * 2. ZIP → Base64
 * 3. SendDocument(paramList=[SIGNED=0, DOCUMENTTYPE=EINVOICE/EARCHIVE, ALIAS=...])
 * 4. Response: resultCode=1 success, resultMsg error
 *
 * Cancellation: SendDocument + paramList=[DOCUMENTTYPE=CANCELEARCHIVEINVOICE, UUID=ettn]
 *
 * Reference: https://github.com/Hasokeyk/elogo-php/blob/main/src/Elogo/Elogo.php
 */
@Component
public class LogoInvoiceProvider implements InvoiceProvider {

    private static final Logger logger = LoggerFactory.getLogger(LogoInvoiceProvider.class);

    // eLogo Postbox Service (WCF) actual endpoints
    private static final String TEST_ENDPOINT = "https://pb-demo.elogo.com.tr/PostboxService.svc";
    private static final String PROD_ENDPOINT = "https://pb.elogo.com.tr/PostboxService.svc";

    // WCF default namespaces
    private static final String SOAPENV_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SERVICE_NS = "http://tempuri.org/";
    private static final String ARRAYS_NS = "http://schemas.microsoft.com/2003/10/Serialization/Arrays";

    // Document types
    private static final String DOC_TYPE_EINVOICE = "EINVOICE";       // e-Invoice (corporate)
    private static final String DOC_TYPE_EARCHIVE = "EARCHIVE";       // e-Archive (individual)
    private static final String DOC_TYPE_CANCEL_EARCHIVE = "CANCELEARCHIVEINVOICE";

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate;
    private final AtomicReference<SessionCache> sessionCache = new AtomicReference<>();

    public LogoInvoiceProvider(SiteSettingService settingService) {
        this.settingService = settingService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "LOGO";
    }

    @Override
    public boolean isEnabled() {
        if (!"LOGO".equalsIgnoreCase(settingService.getSetting("invoice_provider"))) {
            return false;
        }
        String username = settingService.getSetting("logo_efatura_username");
        String password = settingService.getSetting("logo_efatura_password");
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    @Override
    public InvoiceResult createInvoice(Invoice invoice, Order order, List<OrderItem> items) {
        if (!isEnabled()) {
            return InvoiceResult.builder()
                    .success(false)
                    .errorMessage("Logo e-Fatura yapılandırılmamış")
                    .status(InvoiceStatus.ERROR)
                    .build();
        }

        try {
            String sessionId = getOrRefreshSession();
            if (sessionId == null) {
                return InvoiceResult.builder()
                        .success(false)
                        .errorMessage("Logo oturumu açılamadı - credential kontrolü gerekli")
                        .status(InvoiceStatus.ERROR)
                        .build();
            }

            // 1. Generate UBL-TR XML
            Map<String, String> companyInfo = getCompanyInfo();
            String ublXml = UblTrInvoiceBuilder.build(invoice, order,
                    items != null ? items : List.of(), companyInfo);

            // 2. Package the XML inside a ZIP and Base64-encode it
            String ettn = invoice.getEttn();
            byte[] zipBytes = buildZipWithXml(ublXml, ettn + ".xml");
            String base64Zip = Base64.getEncoder().encodeToString(zipBytes);

            // 3. SendDocument SOAP call
            String docType = invoice.isIndividual() ? DOC_TYPE_EARCHIVE : DOC_TYPE_EINVOICE;
            String alias = companyInfo.getOrDefault("logo_customer_alias", "");

            String soapRequest = buildSendDocumentSoap(sessionId, base64Zip,
                    ettn + ".zip", docType, alias);
            String response = callSoap(soapRequest, "SendDocument");

            // 4. Parse response
            return parseSendDocumentResponse(response, invoice, ettn);
        } catch (LogoAuthException e) {
            logger.warn("Logo session expired, retrying: {}", e.getMessage());
            invalidateSession();
            try {
                return createInvoice(invoice, order, items);
            } catch (Exception retryEx) {
                logger.error("Logo retry failed: {}", retryEx.getMessage(), retryEx);
                return InvoiceResult.builder()
                        .success(false)
                        .errorMessage("Logo retry hatası: " + retryEx.getMessage())
                        .status(InvoiceStatus.ERROR)
                        .build();
            }
        } catch (Exception e) {
            logger.error("Logo invoice create exception: {}", e.getMessage(), e);
            return InvoiceResult.builder()
                    .success(false)
                    .errorMessage("Logo hatası: " + e.getMessage())
                    .status(InvoiceStatus.ERROR)
                    .build();
        }
    }

    @Override
    public InvoiceResult queryStatus(String ettn) {
        if (!isEnabled() || ettn == null) {
            return InvoiceResult.builder().success(false).errorMessage("Geçersiz istek").build();
        }
        try {
            String sessionId = getOrRefreshSession();
            String soap = buildGetInvoiceStatusSoap(sessionId, ettn);
            String response = callSoap(soap, "getInvoiceStatus");
            return parseStatusResponse(response, ettn);
        } catch (LogoAuthException e) {
            invalidateSession();
            return queryStatus(ettn);
        } catch (Exception e) {
            logger.error("Logo status query hatası: {}", e.getMessage());
            return InvoiceResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public InvoiceResult cancelInvoice(String ettn) {
        if (!isEnabled() || ettn == null) {
            return InvoiceResult.builder().success(false).errorMessage("Geçersiz ETTN").build();
        }
        try {
            String sessionId = getOrRefreshSession();
            // Cancellation: SendDocument + paramList with DOCUMENTTYPE=CANCELEARCHIVEINVOICE + UUID=ettn
            String soap = buildCancelSoap(sessionId, ettn);
            String response = callSoap(soap, "SendDocument");

            String resultCode = extractXmlValue(response, "resultCode");
            String resultMsg = extractXmlValue(response, "resultMsg");

            boolean success = "1".equals(resultCode);
            return InvoiceResult.builder()
                    .success(success)
                    .status(success ? InvoiceStatus.CANCELLED : InvoiceStatus.ERROR)
                    .providerInvoiceId(ettn)
                    .gibResponse(resultMsg != null ? resultMsg : truncate(response, 500))
                    .errorMessage(success ? null : resultMsg)
                    .build();
        } catch (LogoAuthException e) {
            invalidateSession();
            return cancelInvoice(ettn);
        } catch (Exception e) {
            logger.error("Logo cancel hatası: {}", e.getMessage());
            return InvoiceResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    /**
     * Logo CheckGibUser → queries whether the party is registered for e-Invoice at GIB.
     * Used to avoid mistakenly issuing E_ARSIV instead of E_FATURA to corporate
     * customers (and vice versa). On invalid credentials / session problems it returns
     * {@code false} to stay on the safe side.
     */
    @Override
    public boolean isGibRegistered(String taxId) {
        if (!isEnabled() || taxId == null || taxId.isBlank()) return false;
        try {
            String sessionId = getOrRefreshSession();
            if (sessionId == null) return false;

            String soap = buildCheckGibUserSoap(sessionId, taxId.trim());
            String response = callSoap(soap, "CheckGibUser");

            // Response format: <item><vknTckn>...</vknTckn><isGibUser>true/false</isGibUser>...
            // Or a single item → extractXmlValue returns the first value.
            String flag = extractXmlValue(response, "isGibUser");
            if (flag == null) flag = extractXmlValue(response, "gibUser");
            if (flag == null) flag = extractXmlValue(response, "isRegistered");
            boolean registered = "true".equalsIgnoreCase(flag);
            logger.debug("Logo CheckGibUser[{}] → registered={}", taxId, registered);
            return registered;
        } catch (LogoAuthException e) {
            invalidateSession();
            return false;
        } catch (Exception e) {
            logger.warn("Logo CheckGibUser hatası ({}): {}", taxId, e.getMessage());
            return false;
        }
    }

    /**
     * CheckGibUser SOAP request — sent in the vknTcknList list.
     */
    private String buildCheckGibUserSoap(String sessionId, String taxId) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s" xmlns:arr="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:CheckGibUser>
                  <tem:sessionID>%s</tem:sessionID>
                  <tem:vknTcknList>
                    <arr:string>%s</arr:string>
                  </tem:vknTcknList>
                </tem:CheckGibUser>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS, ARRAYS_NS,
                xmlEscape(sessionId), xmlEscape(taxId));
    }

    @Override
    public byte[] downloadPdf(String ettn) {
        if (!isEnabled() || ettn == null) return new byte[0];
        try {
            String sessionId = getOrRefreshSession();
            // Since we don't know the invoice type, try EINVOICE first, then EARCHIVE on failure
            byte[] pdf = downloadPdfWithDocType(sessionId, ettn, DOC_TYPE_EINVOICE);
            if (pdf.length == 0) {
                pdf = downloadPdfWithDocType(sessionId, ettn, DOC_TYPE_EARCHIVE);
            }
            return pdf;
        } catch (LogoAuthException e) {
            invalidateSession();
            return downloadPdf(ettn);
        } catch (Exception e) {
            logger.error("Logo PDF download hatası: {}", e.getMessage());
            return new byte[0];
        }
    }

    private byte[] downloadPdfWithDocType(String sessionId, String ettn, String docType) {
        try {
            String soap = buildGetDocumentDataSoap(sessionId, ettn, docType, "PDF");
            String response = callSoap(soap, "GetDocumentData");

            // The response contains base64 PDF data
            // <binaryData><value>base64...</value></binaryData> or <GetDocumentDataResult>
            String base64 = extractXmlValue(response, "value");
            if (base64 == null) base64 = extractXmlValue(response, "binaryData");
            if (base64 == null) base64 = extractXmlValue(response, "GetDocumentDataResult");

            if (base64 != null && !base64.isBlank()) {
                try {
                    return Base64.getDecoder().decode(base64.trim().replaceAll("\\s+", ""));
                } catch (IllegalArgumentException ignored) {}
            }
            return new byte[0];
        } catch (LogoAuthException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("PDF download attempt failed (docType={}): {}", docType, e.getMessage());
            return new byte[0];
        }
    }

    // ===== Session Management =====

    private String getOrRefreshSession() {
        SessionCache cache = sessionCache.get();
        if (cache != null && cache.isValid()) {
            return cache.sessionId;
        }
        return doLogin();
    }

    private synchronized String doLogin() {
        SessionCache existing = sessionCache.get();
        if (existing != null && existing.isValid()) {
            return existing.sessionId;
        }

        try {
            String username = settingService.getSetting("logo_efatura_username");
            String password = settingService.getSetting("logo_efatura_password");
            String soap = buildLoginSoap(username, password);
            String response = callSoap(soap, "Login");

            // LoginResult: boolean (true=success), sessionID: string
            String loginResult = extractXmlValue(response, "LoginResult");
            String sessionId = extractXmlValue(response, "sessionID");

            if (!"true".equalsIgnoreCase(loginResult) || sessionId == null || sessionId.isBlank()) {
                logger.error("Logo login başarısız. LoginResult={}, response preview: {}",
                        loginResult, truncate(response, 500));
                return null;
            }

            // Session cache — 25 min safety margin (Logo default 30 min)
            sessionCache.set(new SessionCache(sessionId, System.currentTimeMillis() + 25 * 60 * 1000));
            logger.info("Logo eLogo login başarılı (session 25dk cache'lendi)");
            return sessionId;
        } catch (Exception e) {
            logger.error("Logo login exception: {}", e.getMessage(), e);
            return null;
        }
    }

    private void invalidateSession() {
        SessionCache cache = sessionCache.getAndSet(null);
        // Logout best-effort
        if (cache != null && cache.sessionId != null) {
            try {
                String soap = buildLogoutSoap(cache.sessionId);
                callSoap(soap, "Logout");
            } catch (Exception ignored) {}
        }
    }

    // ===== SOAP Call =====

    private String callSoap(String soapRequest, String soapAction) {
        String endpoint = resolveEndpoint();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/xml; charset=utf-8"));
        // WCF standard SOAPAction format: "http://tempuri.org/IPostboxService/<MethodName>"
        headers.set("SOAPAction", "\"" + SERVICE_NS + "IPostboxService/" + soapAction + "\"");

        HttpEntity<String> entity = new HttpEntity<>(soapRequest, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);
        String body = response.getBody() != null ? response.getBody() : "";

        // Session expired / invalid session check
        String lower = body.toLowerCase();
        if (lower.contains("sessionexception")
                || lower.contains("invalid session")
                || lower.contains("session expired")
                || lower.contains("session bulunamad")
                || lower.contains("oturum yok")
                || lower.contains("geçersiz oturum")) {
            throw new LogoAuthException("Session expired");
        }
        return body;
    }

    private String resolveEndpoint() {
        String custom = settingService.getSetting("logo_efatura_endpoint");
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        boolean testMode = !"false".equalsIgnoreCase(settingService.getSetting("logo_efatura_test_mode"));
        return testMode ? TEST_ENDPOINT : PROD_ENDPOINT;
    }

    // ===== SOAP Envelope Builders =====

    /**
     * Login SOAP request.
     *
     * PHP: $this->client->Login(['login' => ['username' => $u, 'password' => $p]])
     * Response: {LoginResult: true/false, sessionID: "..."}
     */
    private String buildLoginSoap(String username, String password) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:Login>
                  <tem:login>
                    <tem:userName>%s</tem:userName>
                    <tem:password>%s</tem:password>
                  </tem:login>
                </tem:Login>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS, xmlEscape(username), xmlEscape(password));
    }

    private String buildLogoutSoap(String sessionId) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:Logout>
                  <tem:sessionID>%s</tem:sessionID>
                </tem:Logout>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS, xmlEscape(sessionId));
    }

    /**
     * SendDocument SOAP request — the main method for invoice submission.
     *
     * PHP:
     *   $data = [
     *     'sessionID' => $session,
     *     'paramList' => ['SIGNED=0', 'DOCUMENTTYPE=EINVOICE', 'ALIAS=...'],
     *     'document'  => {
     *       binaryData: {value: base64_zip},
     *       currentDate: ISO8601,
     *       fileName: "fatura.zip",
     *       hash: md5(zip_data)
     *     }
     *   ]
     *
     * Response: SendDocumentResult{resultCode: 1=ok, resultMsg: error}
     */
    private String buildSendDocumentSoap(String sessionId, String base64Zip,
                                          String fileName, String docType, String alias) {
        StringBuilder paramListXml = new StringBuilder();
        paramListXml.append("            <arr:string>SIGNED=0</arr:string>\n");
        paramListXml.append("            <arr:string>DOCUMENTTYPE=").append(docType).append("</arr:string>\n");
        if (alias != null && !alias.isBlank()) {
            paramListXml.append("            <arr:string>ALIAS=").append(xmlEscape(alias)).append("</arr:string>\n");
        }

        // Logo hash expectation: MD5 hex of the ZIP file (before base64)
        String md5Hash = md5Hex(Base64.getDecoder().decode(base64Zip));
        String currentDate = java.time.OffsetDateTime.now()
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s" xmlns:arr="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:SendDocument>
                  <tem:sessionID>%s</tem:sessionID>
                  <tem:paramList>
            %s          </tem:paramList>
                  <tem:document>
                    <tem:binaryData>
                      <tem:value>%s</tem:value>
                    </tem:binaryData>
                    <tem:currentDate>%s</tem:currentDate>
                    <tem:fileName>%s</tem:fileName>
                    <tem:hash>%s</tem:hash>
                  </tem:document>
                </tem:SendDocument>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(
                SOAPENV_NS, SERVICE_NS, ARRAYS_NS,
                xmlEscape(sessionId),
                paramListXml.toString(),
                base64Zip,
                currentDate,
                xmlEscape(fileName),
                md5Hash
            );
    }

    /**
     * Cancellation: SendDocument + paramList=[DOCUMENTTYPE=CANCELEARCHIVEINVOICE, UUID=ettn]
     * (e-Archive invoices can be cancelled; e-Invoices are returned via the commercial rejection process.)
     */
    private String buildCancelSoap(String sessionId, String ettn) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s" xmlns:arr="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:SendDocument>
                  <tem:sessionID>%s</tem:sessionID>
                  <tem:paramList>
                    <arr:string>DOCUMENTTYPE=%s</arr:string>
                    <arr:string>UUID=%s</arr:string>
                  </tem:paramList>
                </tem:SendDocument>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS, ARRAYS_NS,
                xmlEscape(sessionId), DOC_TYPE_CANCEL_EARCHIVE, xmlEscape(ettn));
    }

    private String buildGetInvoiceStatusSoap(String sessionId, String ettn) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:getInvoiceStatus>
                  <tem:sessionID>%s</tem:sessionID>
                  <tem:uuid>%s</tem:uuid>
                </tem:getInvoiceStatus>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS, xmlEscape(sessionId), xmlEscape(ettn));
    }

    /**
     * GetDocumentData — for downloading PDF/XML.
     *
     * PHP: $this->client->GetDocumentData(['sessionID', 'uuid', 'docType', 'dataType' => 'PDF'])
     */
    private String buildGetDocumentDataSoap(String sessionId, String ettn, String docType, String dataType) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <soapenv:Envelope xmlns:soapenv="%s" xmlns:tem="%s">
              <soapenv:Header/>
              <soapenv:Body>
                <tem:GetDocumentData>
                  <tem:sessionID>%s</tem:sessionID>
                  <tem:uuid>%s</tem:uuid>
                  <tem:docType>%s</tem:docType>
                  <tem:dataType>%s</tem:dataType>
                </tem:GetDocumentData>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(SOAPENV_NS, SERVICE_NS,
                xmlEscape(sessionId), xmlEscape(ettn), docType, dataType);
    }

    // ===== Response Parsing =====

    /**
     * SendDocumentResult.resultCode == 1 means success; resultMsg holds the error detail.
     */
    private InvoiceResult parseSendDocumentResponse(String response, Invoice invoice, String ettn) {
        String resultCode = extractXmlValue(response, "resultCode");
        String resultMsg = extractXmlValue(response, "resultMsg");

        if (!"1".equals(resultCode)) {
            String errorDetail = resultMsg != null && !resultMsg.isBlank()
                    ? resultMsg : extractSoapFault(response);
            return InvoiceResult.builder()
                    .success(false)
                    .status(InvoiceStatus.ERROR)
                    .errorMessage("Logo hatası (code=" + resultCode + "): "
                            + (errorDetail != null ? errorDetail : "bilinmeyen"))
                    .gibResponse(truncate(response, 2000))
                    .providerInvoiceId(ettn)
                    .build();
        }

        // Success: Logo also returns the invoice number, but ETTN is the primary reference
        return InvoiceResult.builder()
                .success(true)
                .invoiceNumber(invoice.getInvoiceNumber())
                .providerInvoiceId(ettn)
                .status(InvoiceStatus.APPROVED)
                .gibResponse(truncate(response, 2000))
                .build();
    }

    private InvoiceResult parseStatusResponse(String response, String ettn) {
        String statusCode = extractXmlValue(response, "statusCode");
        String statusText = extractXmlValue(response, "statusText");
        if (statusText == null) statusText = extractXmlValue(response, "status");

        InvoiceStatus status = mapStatus(statusCode, statusText);
        return InvoiceResult.builder()
                .success(true)
                .providerInvoiceId(ettn)
                .status(status)
                .gibResponse(truncate(response, 2000))
                .build();
    }

    /**
     * Logo/GIB status code mapping.
     */
    private InvoiceStatus mapStatus(String statusCode, String statusText) {
        if (statusCode != null) {
            try {
                int code = Integer.parseInt(statusCode.trim());
                if (code >= 100 && code < 200) return InvoiceStatus.APPROVED;
                if (code >= 200 && code < 300) return InvoiceStatus.REJECTED;
                if (code == 0) return InvoiceStatus.PENDING;
            } catch (NumberFormatException ignored) {}
        }
        if (statusText != null) {
            String t = statusText.toUpperCase();
            if (t.contains("APPROVED") || t.contains("ONAYLANDI") || t.contains("BAŞARILI") || t.contains("BASARILI")) return InvoiceStatus.APPROVED;
            if (t.contains("REJECTED") || t.contains("REDDEDİLDİ") || t.contains("REDDEDILDI")) return InvoiceStatus.REJECTED;
            if (t.contains("CANCELLED") || t.contains("İPTAL") || t.contains("IPTAL")) return InvoiceStatus.CANCELLED;
            if (t.contains("ERROR") || t.contains("HATA")) return InvoiceStatus.ERROR;
        }
        return InvoiceStatus.PENDING;
    }

    // ===== Helpers =====

    private Map<String, String> getCompanyInfo() {
        String[] keys = {
                "logo_company_vkn", "logo_company_title", "logo_company_tax_office",
                "logo_company_mersis_no", "logo_company_trade_registry",
                "logo_company_address", "logo_company_city", "logo_company_district",
                "logo_company_postal_code", "logo_company_country",
                "logo_company_email", "logo_company_phone", "logo_company_website",
                "logo_company_bank_name", "logo_company_bank_iban",
                "logo_customer_alias"
        };
        Map<String, String> map = new HashMap<>();
        for (String k : keys) {
            map.put(k, settingService.getSetting(k));
        }
        return map;
    }

    /**
     * Packages the UBL XML inside a ZIP file (the format required by Logo eLogo).
     */
    private byte[] buildZipWithXml(String xml, String xmlFileName) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(xmlFileName);
            zos.putNextEntry(entry);
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        }
    }

    private String md5Hex(byte[] input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractXmlValue(String xml, String tagName) {
        if (xml == null || tagName == null) return null;
        Pattern pattern = Pattern.compile(
                "<(?:[a-zA-Z0-9]+:)?" + Pattern.quote(tagName) + "[^>]*>([^<]*)</(?:[a-zA-Z0-9]+:)?" + Pattern.quote(tagName) + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = pattern.matcher(xml);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractSoapFault(String response) {
        String fault = extractXmlValue(response, "faultstring");
        if (fault != null) return fault;
        fault = extractXmlValue(response, "Detail");
        if (fault != null) return fault;
        return extractXmlValue(response, "Message");
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    // ===== Inner Classes =====

    private static class SessionCache {
        final String sessionId;
        final long expiresAt;

        SessionCache(String sessionId, long expiresAt) {
            this.sessionId = sessionId;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return sessionId != null && System.currentTimeMillis() < expiresAt;
        }
    }

    private static class LogoAuthException extends RuntimeException {
        LogoAuthException(String message) { super(message); }
    }
}
