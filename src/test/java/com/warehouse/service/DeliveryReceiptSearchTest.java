package com.warehouse.service;

import com.warehouse.dto.DeliveryReceiptDto;
import com.warehouse.dto.DeliveryReceiptFilter;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.enums.TransferStatus;
import com.warehouse.enums.TransferType;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filters on the receipts screen.
 *
 * <p>These run on H2 and so <em>cannot</em> reproduce the bug that made this screen blank in
 * production: the old query used {@code (:param IS NULL OR ...)} per filter, which H2 accepts
 * and PostgreSQL rejects outright with {@code 42P18 could not determine data type of
 * parameter}. Nineteen green tests and an empty page. The fix — building the predicates as a
 * specification, so an absent filter contributes no parameter at all — is structural and
 * cannot regress silently while these tests still describe what each filter must select.</p>
 *
 * <p>Anyone tempted to fold this back into one JPQL statement: run it against a real
 * PostgreSQL before believing the test suite.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryReceiptSearchTest {

    @Autowired private DeliveryReceiptService receiptService;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "issuedAt");

    private Long isikTransferId;
    private Long ayseTransferId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("Arama Kategori");
        category.setSlug("arama-" + System.nanoTime());
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Buzdolabı");
        product.setSku("ARA-001");
        product.setSlug("ara-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        isikTransferId = shipment(warehouse, product, "Işık Şahin Mobilya Ltd. Şti.", "51 ATS 303");
        ayseTransferId = shipment(warehouse, product, "Ayşe Gültekin", "51 XYZ 909");

        receiptService.issue(isikTransferId, "admin");
        receiptService.issue(ayseTransferId, "admin");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Long shipment(Warehouse warehouse, Product product, String customer, String plate) {
        StockTransfer transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouse);
        transfer.setProduct(product);
        transfer.setQuantity(1);
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName(customer);
        transfer.setCustomerPhone("05551234567");
        transfer.setCustomerAddress("Kale Mah. No: 28 Niğde");
        transfer.setDriverName("Ahmet Yılmaz");
        transfer.setDriverTcId("12345678901");
        transfer.setDriverPhone("05551234567");
        transfer.setVehiclePlate(plate);
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setTransferDate(LocalDateTime.now());

        StockTransferItem item = new StockTransferItem();
        item.setProduct(product);
        item.setQuantity(1);
        transfer.addItem(item);

        return transferRepository.save(transfer).getId();
    }

    private List<DeliveryReceiptDto> search(DeliveryReceiptStatus status, Boolean signed,
                                            LocalDateTime from, LocalDateTime to, String q) {
        return receiptService.search(
                        DeliveryReceiptFilter.builder()
                                .status(status)
                                .hasSignedCopy(signed)
                                .from(from)
                                .to(to)
                                .search(q)
                                .build(),
                        PageRequest.of(0, 20, NEWEST))
                .getContent();
    }

    @Test
    @DisplayName("Filtresiz çağrı bütün makbuzları döndürür")
    void noFilterReturnsEverything() {
        assertThat(search(null, null, null, null, null)).hasSize(2);
        // Boş metin de filtre sayılmamalı: kullanıcı arama kutusunu temizlediğinde
        // liste boşalmamalı.
        assertThat(search(null, null, null, null, "   ")).hasSize(2);
    }

    @Test
    @DisplayName("Durum filtresi")
    void statusFilter() {
        assertThat(search(DeliveryReceiptStatus.ISSUED, null, null, null, null)).hasSize(2);
        assertThat(search(DeliveryReceiptStatus.DELIVERED, null, null, null, null)).isEmpty();

        receiptService.confirmDelivery(isikTransferId, "Ahmet Yılmaz", "Işık Şahin",
                LocalDateTime.now(), null, "admin");

        assertThat(search(DeliveryReceiptStatus.DELIVERED, null, null, null, null)).hasSize(1);
        assertThat(search(DeliveryReceiptStatus.ISSUED, null, null, null, null)).hasSize(1);
    }

    @Test
    @DisplayName("İmzalı nüsha filtresi eki olan ve olmayanı ayırır")
    void signedCopyFilter() {
        assertThat(search(null, false, null, null, null)).hasSize(2);
        assertThat(search(null, true, null, null, null)).isEmpty();

        receiptService.addAttachment(isikTransferId, new MockMultipartFile(
                "file", "imzali.png", "image/png", PNG), "admin");

        assertThat(search(null, true, null, null, null))
                .singleElement()
                .satisfies(r -> assertThat(r.getCustomerFullName()).contains("Işık"));
        assertThat(search(null, false, null, null, null))
                .singleElement()
                .satisfies(r -> assertThat(r.getCustomerFullName()).contains("Ayşe"));
    }

    @Test
    @DisplayName("Tarih aralığı filtresi")
    void dateRangeFilter() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(search(null, null, now.minusDays(1), now.plusDays(1), null)).hasSize(2);
        assertThat(search(null, null, now.plusDays(1), null, null)).isEmpty();
        assertThat(search(null, null, null, now.minusDays(1), null)).isEmpty();
    }

    @Test
    @DisplayName("Arama; müşteri, plaka ve makbuz numarasında çalışır")
    void searchAcrossFields() {
        assertThat(search(null, null, null, null, "Ayşe")).hasSize(1);
        assertThat(search(null, null, null, null, "51 ATS")).hasSize(1);
        assertThat(search(null, null, null, null, "TM-")).hasSize(2);
        assertThat(search(null, null, null, null, "bulunmayan")).isEmpty();
        // Noktalama katlamada ayırıcıya dönüşüyor, yani "TM-" ile "TM " aynı şey.
        assertThat(search(null, null, null, null, "tm")).hasSize(2);
    }

    @Test
    @DisplayName("Arama Türkçe harfe ve büyük/küçük harfe takılmaz")
    void searchFoldsTurkishLetters() {
        // Doğrudan LIKE ile bunların hiçbiri çalışmıyordu. Türkçe'de küçültme geri
        // döndürülemez: "I" hem "ı" hem "i"nin büyüğü, "İ" küçülünce birleşik noktalı bir
        // diziye dönüşüyor. Kayıt da arama da ASCII'ye katlandığı için artık hepsi aynı
        // satıra düşüyor.
        assertThat(search(null, null, null, null, "Işık")).hasSize(1);
        assertThat(search(null, null, null, null, "IŞIK")).hasSize(1);
        assertThat(search(null, null, null, null, "ışık")).hasSize(1);
        assertThat(search(null, null, null, null, "isik")).hasSize(1);

        assertThat(search(null, null, null, null, "şahin")).hasSize(1);
        assertThat(search(null, null, null, null, "ŞAHİN")).hasSize(1);
        assertThat(search(null, null, null, null, "sahin")).hasSize(1);

        // Türkçe klavyesi olmayan biri de bulabilmeli.
        assertThat(search(null, null, null, null, "AYSE")).hasSize(1);
        assertThat(search(null, null, null, null, "Ayşe")).hasSize(1);
        assertThat(search(null, null, null, null, "gultekin")).hasSize(1);
    }

    @Test
    @DisplayName("Arama; makbuz no, plaka ve bitişik yazılmış telefonu da bulur")
    void searchCoversTheAdvertisedFields() {
        assertThat(search(null, null, null, null, "TM-")).hasSize(2);
        assertThat(search(null, null, null, null, "51 ATS")).hasSize(1);
        assertThat(search(null, null, null, null, "ats 303")).hasSize(1);
        assertThat(search(null, null, null, null, "05551234567")).hasSize(2);
    }

    @Test
    @DisplayName("Kelimeler ayrı ayrı aranır: araya kelime girse de, sıra değişse de bulur")
    void searchMatchesTokensInAnyOrder() {
        // Bitişik arama olsaydı hiçbiri eşleşmezdi; kayıt "Işık Şahin Mobilya Ltd. Şti.".
        assertThat(search(null, null, null, null, "Işık Mobilya")).hasSize(1);   // araya kelime giriyor
        assertThat(search(null, null, null, null, "mobilya isik")).hasSize(1);   // sıra ters
        assertThat(search(null, null, null, null, "sti isik mobilya")).hasSize(1);

        // Her kelime bulunmak zorunda: ikisi ayrı kayıtlarda geçiyor, birlikte hiçbirinde.
        assertThat(search(null, null, null, null, "Işık Ayşe")).isEmpty();
    }

    @Test
    @DisplayName("Telefon hangi biçimde yazılırsa yazılsın bulunur")
    void searchNormalisesPhoneFormatting() {
        // Kayıtta "05551234567" duruyor. Kullanıcı ekrandaki hâliyle, boşluklu ya da ülke
        // koduyla yazabilir; karşılaştırma hepsinde ortak olan son on hane üzerinden.
        assertThat(search(null, null, null, null, "05551234567")).hasSize(2);
        assertThat(search(null, null, null, null, "0555 123 45 67")).hasSize(2);
        assertThat(search(null, null, null, null, "+90 555 123 45 67")).hasSize(2);
        assertThat(search(null, null, null, null, "5551234567")).hasSize(2);
    }

    @Test
    @DisplayName("Filtreler birlikte daraltır")
    void filtersCombine() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(search(DeliveryReceiptStatus.ISSUED, false, now.minusDays(1), now.plusDays(1), "Ayşe"))
                .hasSize(1);
        // Tek bir uyuşmazlık tümünü elemeye yeter.
        assertThat(search(DeliveryReceiptStatus.DELIVERED, false, now.minusDays(1), now.plusDays(1), "Ayşe"))
                .isEmpty();
    }

    // ───────────────────── Yeni yapının kesitleri ──────────────────────────

    /**
     * Planlı bir depo çıkışı kurar ve makbuzunu keser.
     *
     * <p>Sevkiyat doğrudan yazılıyor, {@code ServiceHandoverService} üzerinden değil: bu
     * sınıf filtreleri test ediyor, stok hareketini değil, ve akışın tamamını kurmak testi
     * ilgisiz bir sürü hazırlığa bağımlı kılardı.</p>
     */
    private Long scheduledHandover(String customer, LocalDateTime scheduledAt) {
        StockTransfer transfer = new StockTransfer();
        transfer.setSourceWarehouse(warehouseRepository.findAll().get(0));
        transfer.setProduct(productRepository.findAll().get(0));
        transfer.setQuantity(1);
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);
        transfer.setCustomerFullName(customer);
        transfer.setCustomerPhone("05551234567");
        transfer.setCustomerAddress("Kale Mah. No: 28 Niğde");
        transfer.setCarrierPending(true);
        transfer.setHandoverToName("Yıldız Nakliyat");
        transfer.setScheduledDeliveryAt(scheduledAt);
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        transfer.setTransferDate(LocalDateTime.now());

        StockTransferItem item = new StockTransferItem();
        item.setProduct(transfer.getProduct());
        item.setQuantity(1);
        transfer.addItem(item);

        Long id = transferRepository.save(transfer).getId();
        receiptService.issue(id, "admin", com.warehouse.enums.DeliveryReceiptKind.SERVICE_HANDOVER);
        return id;
    }

    private List<DeliveryReceiptDto> search(DeliveryReceiptFilter filter) {
        return receiptService.search(filter, PageRequest.of(0, 20, NEWEST)).getContent();
    }

    @Test
    @DisplayName("Belge tipi filtresi iki kâğıdı ayırır")
    void kindFilterSeparatesTheTwoPapers() {
        scheduledHandover("Planlı Müşteri", LocalDateTime.now().plusDays(2));

        assertThat(search(DeliveryReceiptFilter.builder()
                .kind(com.warehouse.enums.DeliveryReceiptKind.SERVICE_HANDOVER).build()))
                .hasSize(1);
        assertThat(search(DeliveryReceiptFilter.builder()
                .kind(com.warehouse.enums.DeliveryReceiptKind.DELIVERY).build()))
                .hasSize(2);
    }

    @Test
    @DisplayName("Teslim planı kesitleri: planlı, bugün, gecikmiş, plansız")
    void planFilterSlicesByDeliveryDate() {
        scheduledHandover("Yarınki", LocalDateTime.now().plusDays(1));
        scheduledHandover("Bugünkü", LocalDateTime.now().plusMinutes(30));
        scheduledHandover("Gecikmiş", LocalDateTime.now().minusDays(3));

        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.SCHEDULED).build()))
                .as("açık planların tamamı")
                .hasSize(3);
        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.DUE_TODAY).build()))
                .extracting(DeliveryReceiptDto::getCustomerFullName)
                .containsExactly("Bugünkü");
        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.OVERDUE).build()))
                .extracting(DeliveryReceiptDto::getCustomerFullName)
                .containsExactly("Gecikmiş");
        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.NONE).build()))
                .as("planı olmayan iki klasik makbuz")
                .hasSize(2);
    }

    @Test
    @DisplayName("Teslim edilen plan, açık planlar kesitinden düşer")
    void aDeliveredPlanLeavesTheOpenSlice() {
        Long transferId = scheduledHandover("Teslim Edilen", LocalDateTime.now().plusDays(1));
        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.SCHEDULED).build())).hasSize(1);

        // Sevkiyat önce kapanıyor, sonra kağıda imza işleniyor — gerçek sıra da bu.
        // Tersi zaten reddediliyor: açık bir planda teslim onayı malı rezervede bırakır.
        StockTransfer delivered = transferRepository.findById(transferId).orElseThrow();
        delivered.setStatus(TransferStatus.COMPLETED);
        delivered.setCompletedDate(LocalDateTime.now());
        transferRepository.save(delivered);

        receiptService.confirmDelivery(transferId, null, "Ayşe Gültekin",
                LocalDateTime.now(), null, "admin");

        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.SCHEDULED).build()))
                .as("teslim edilmiş bir plan artık kovalanacak iş değil")
                .isEmpty();
    }

    @Test
    @DisplayName("Taşıyıcısı girilmemiş depo çıkışları ayrı bir kesit")
    void carrierPendingIsItsOwnSlice() {
        scheduledHandover("Taşıyıcısız", LocalDateTime.now().plusDays(2));

        assertThat(search(DeliveryReceiptFilter.builder().carrierPending(true).build()))
                .extracting(DeliveryReceiptDto::getCustomerFullName)
                .containsExactly("Taşıyıcısız");
        assertThat(search(DeliveryReceiptFilter.builder().carrierPending(false).build()))
                .as("şoförü belli olan teslimat makbuzları")
                .hasSize(2);
    }

    @Test
    @DisplayName("Tarih aralığı seçilen tarih alanına uygulanır")
    void theDateRangeFollowsTheChosenField() {
        scheduledHandover("Gelecek Hafta", LocalDateTime.now().plusDays(7));
        LocalDateTime now = LocalDateTime.now();

        // Düzenleme tarihi bugün: üçü de bugün kesildi.
        assertThat(search(DeliveryReceiptFilter.builder()
                .dateField(com.warehouse.enums.ReceiptDateField.ISSUED)
                .from(now.minusHours(1)).to(now.plusHours(1)).build()))
                .hasSize(3);

        // Aynı aralık planlanan teslim tarihine uygulandığında hiçbiri düşmüyor: plan
        // gelecek hafta, ve planı olmayanlar bu kesitte zaten görünmüyor.
        assertThat(search(DeliveryReceiptFilter.builder()
                .dateField(com.warehouse.enums.ReceiptDateField.SCHEDULED)
                .from(now.minusHours(1)).to(now.plusHours(1)).build()))
                .isEmpty();
        assertThat(search(DeliveryReceiptFilter.builder()
                .dateField(com.warehouse.enums.ReceiptDateField.SCHEDULED)
                .from(now).to(now.plusDays(10)).build()))
                .extracting(DeliveryReceiptDto::getCustomerFullName)
                .containsExactly("Gelecek Hafta");
    }

    @Test
    @DisplayName("Sayaçlar listeyle aynı kesiti sayar")
    void theCountersAgreeWithTheList() {
        scheduledHandover("Bugünkü", LocalDateTime.now().plusMinutes(30));
        scheduledHandover("Gecikmiş", LocalDateTime.now().minusDays(2));

        var stats = receiptService.stats();
        assertThat(stats.get("scheduled")).isEqualTo(2L);
        assertThat(stats.get("dueToday")).isEqualTo(1L);
        assertThat(stats.get("overdue")).isEqualTo(1L);
        assertThat(stats.get("carrierPending")).isEqualTo(2L);

        // Karttaki sayı ile karta tıklayınca gelen liste aynı Specification'dan üretiliyor;
        // bu eşitlik bozulursa hangisinin doğru olduğu anlaşılamaz.
        assertThat(search(DeliveryReceiptFilter.builder()
                .plan(com.warehouse.enums.DeliveryPlanFilter.OVERDUE).build()))
                .hasSize(stats.get("overdue").intValue());
    }

    /** 1x1 PNG. */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
}
