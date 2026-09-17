package com.warehouse.service;

import com.warehouse.dto.DeliveryReminderMail;
import com.warehouse.dto.ServiceHandoverRequest;
import com.warehouse.dto.StockTransferFilter;
import com.warehouse.entity.Category;
import com.warehouse.entity.Product;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.Warehouse;
import com.warehouse.enums.DeliveryReceiptKind;
import com.warehouse.enums.DeliveryReceiptStatus;
import com.warehouse.enums.DeliveryReminderStage;
import com.warehouse.enums.TransferStatus;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CategoryRepository;
import com.warehouse.repository.ProductRepository;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockTransferRepository;
import com.warehouse.repository.WarehouseRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * İleri tarihli depo çıkışı: makbuz bugün, mal ileri bir tarihte.
 *
 * <p>Burada korunan özellik, sıradan depo çıkışındakinin bir adım incelmişi: mal kitaptan
 * <em>tam bir kez</em> ve <em>doğru anda</em> düşmeli. Planlı çıkışta o an kâğıdın
 * basıldığı an değil, teslimatın kapatıldığı an; arada mal rezervede duruyor. Bu testler
 * o iki uçtan hiçbirinin kaymadığını — ne erken düşüm, ne de rezervede unutulmuş mal —
 * tutan yer.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScheduledDeliveryTest {

    @Autowired private ServiceHandoverService handoverService;
    @Autowired private StockTransferService transferService;
    @Autowired private DeliveryReceiptService receiptService;
    @Autowired private ScheduledDeliveryReminderService reminderService;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockTransferRepository transferRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SiteSettingService siteSettingService;

    /** Hatırlatmanın gerçekten postalandığını görmek için; SMTP'ye çıkmadan. */
    @MockBean private EmailService emailService;

    private Warehouse warehouse;
    private Product product;
    private Stock stock;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = new Category();
        category.setName("Planlı Kategori");
        category.setSlug("planli-kategori-" + System.nanoTime());
        category = categoryRepository.save(category);

        product = new Product();
        product.setName("Çamaşır Makinesi 9 kg");
        product.setSku("PLN-001");
        product.setSlug("planli-camasir-" + System.nanoTime());
        product.setCategory(category);
        product = productRepository.save(product);

        warehouse = new Warehouse();
        warehouse.setName("Merkez Depo");
        warehouse.setLocation("Niğde");
        warehouse = warehouseRepository.save(warehouse);

        stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        stock.setQuantity(40);
        stock = stockRepository.save(stock);

        siteSettingService.updateSettings(
                java.util.Map.of("delivery_reminder_enabled", "true",
                        "delivery_reminder_email", "depo@ornek.com"),
                "test");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reset(emailService);
    }

    private ServiceHandoverRequest request(int quantity, LocalDateTime scheduledAt) {
        ServiceHandoverRequest request = new ServiceHandoverRequest();
        request.setSourceWarehouseId(warehouse.getId());
        request.setHandoverToName("Işık Nakliyat");
        request.setHandedOverBy("Mehmet Güneş");
        request.setCustomerFullName("Ayşe Gültekin");
        request.setCustomerPhone("05559876543");
        request.setCustomerAddress("Kale Mah. Paşakapı Cad. No: 28 Niğde");
        request.setScheduledDeliveryAt(scheduledAt);

        ServiceHandoverRequest.Item item = new ServiceHandoverRequest.Item();
        item.setProductId(product.getId());
        item.setQuantity(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private Stock reloadStock() {
        return stockRepository.findById(stock.getId()).orElseThrow();
    }

    // ───────────────────────────── Plan kurulumu ─────────────────────────────

    @Test
    @DisplayName("Planlı çıkışta stok düşmez, rezerve edilir")
    void schedulingReservesInsteadOfDeducting() {
        LocalDateTime when = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0);
        var result = handoverService.handOver(request(4, when), "admin");

        Stock after = reloadStock();
        assertThat(after.getQuantity())
                .as("mal hâlâ depoda; kâğıt basıldı diye raf boşalmıyor")
                .isEqualTo(40);
        assertThat(after.getReservedQuantity())
                .as("başkasına satılamasın diye rezerve")
                .isEqualTo(4);

        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        assertThat(result.transfer().getScheduledDeliveryAt()).isEqualTo(when);
        assertThat(result.transfer().isCarrierPending()).isTrue();
    }

    @Test
    @DisplayName("Makbuz planlanan tarihi taşır ve kâğıda basar")
    void receiptCarriesThePlannedDate() throws Exception {
        LocalDateTime when = LocalDateTime.now().plusDays(5).withHour(14).withMinute(30);
        var result = handoverService.handOver(request(2, when), "admin");

        assertThat(result.receipt().getKind()).isEqualTo(DeliveryReceiptKind.SERVICE_HANDOVER);
        assertThat(result.receipt().getScheduledDeliveryAt()).isEqualTo(when);

        byte[] pdf = receiptService.renderPdf(result.transfer().getId(), "admin");
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("DEPO ÇIKIŞ MAKBUZU");
            assertThat(text).contains("PLANLI TESLİMAT");
            assertThat(text).contains("Planlanan Teslim");
            // Kapanış paragrafı "teslim alınmıştır" dememeli: mal hâlâ depoda ve o cümle,
            // olmamış bir teslimi imzalatan bir belge üretirdi.
            assertThat(text).doesNotContain("eksiksiz ve hasarsız olarak, belirtilen müşteriye");
            assertThat(text).contains("teslim edilmek üzere ayrılmış");
        }
    }

    @Test
    @DisplayName("Geçmiş tarih planlanamaz")
    void aPlanCannotBeInThePast() {
        assertThatThrownBy(() ->
                handoverService.handOver(request(1, LocalDateTime.now().minusHours(1)), "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("gelecekte olmalıdır");
    }

    @Test
    @DisplayName("Tarih verilmediğinde eski akış korunuyor: stok hemen düşer")
    void withoutAPlanTheOldFlowIsUntouched() {
        var result = handoverService.handOver(request(3, null), "admin");

        assertThat(reloadStock().getQuantity()).isEqualTo(37);
        assertThat(reloadStock().getReservedQuantity()).isZero();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.transfer().getScheduledDeliveryAt()).isNull();
    }

    // ───────────────────────────── Teslimat kapanışı ─────────────────────────

    @Test
    @DisplayName("Stok teslimat tamamlandığında düşer, rezervasyon kapanır")
    void stockLeavesOnlyWhenTheDeliveryIsCompleted() {
        var planned = handoverService.handOver(
                request(6, LocalDateTime.now().plusDays(2)), "admin");
        assertThat(reloadStock().getQuantity()).isEqualTo(40);

        var done = handoverService.completeScheduledDelivery(planned.transfer().getId(),
                "Mehmet Güneş", "Ayşe Gültekin", LocalDateTime.now(), "Kapıda teslim", "admin");

        Stock after = reloadStock();
        assertThat(after.getQuantity()).as("düşüm tam burada").isEqualTo(34);
        assertThat(after.getReservedQuantity()).as("rezervasyon kapandı").isZero();

        assertThat(done.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(done.receipt().getStatus()).isEqualTo(DeliveryReceiptStatus.DELIVERED);
        assertThat(done.receipt().getReceivedByName()).isEqualTo("Ayşe Gültekin");
    }

    @Test
    @DisplayName("Teslim alan yazılmadan teslimat kapatılamaz — stok da hareket etmez")
    void completingWithoutARecipientMovesNothing() {
        var planned = handoverService.handOver(
                request(5, LocalDateTime.now().plusDays(2)), "admin");

        assertThatThrownBy(() -> handoverService.completeScheduledDelivery(
                planned.transfer().getId(), "Mehmet", "  ", LocalDateTime.now(), null, "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("Teslim alan");

        assertThat(reloadStock().getQuantity()).isEqualTo(40);
        assertThat(reloadStock().getReservedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Aynı teslimat iki kez kapatılamaz: stok ikinci kez düşmez")
    void completingTwiceIsRefused() {
        var planned = handoverService.handOver(
                request(5, LocalDateTime.now().plusDays(1)), "admin");
        handoverService.completeScheduledDelivery(planned.transfer().getId(),
                null, "Ayşe Gültekin", null, null, "admin");
        assertThat(reloadStock().getQuantity()).isEqualTo(35);

        assertThatThrownBy(() -> handoverService.completeScheduledDelivery(
                planned.transfer().getId(), null, "Ayşe Gültekin", null, null, "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("zaten tamamlanmış");

        assertThat(reloadStock().getQuantity()).isEqualTo(35);
    }

    @Test
    @DisplayName("Açık bir plan sıradan teslim onayıyla kapatılamaz")
    void theOrdinaryConfirmEndpointRefusesAnOpenPlan() {
        var planned = handoverService.handOver(
                request(3, LocalDateTime.now().plusDays(2)), "admin");

        // Bu uç nokta kâğıda bilgi işler, stoğa dokunmaz. Açık bir planda kullanılsaydı
        // makbuz "teslim edildi" derken mal rezervede kalırdı.
        assertThatThrownBy(() -> receiptService.confirmDelivery(planned.transfer().getId(),
                null, "Ayşe Gültekin", LocalDateTime.now(), null, "admin"))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("Teslimatı Tamamla");

        assertThat(reloadStock().getQuantity()).isEqualTo(40);
        assertThat(reloadStock().getReservedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Teslimat kapandıktan sonra teslim bilgisi düzeltilebilir")
    void afterCompletionTheRecipientCanStillBeCorrected() {
        var planned = handoverService.handOver(
                request(3, LocalDateTime.now().plusDays(2)), "admin");
        handoverService.completeScheduledDelivery(planned.transfer().getId(),
                null, "Yanlış İsim", null, null, "admin");

        var fixed = receiptService.confirmDelivery(planned.transfer().getId(),
                null, "Ayşe Gültekin", LocalDateTime.now(), "İsim düzeltildi", "admin");

        assertThat(fixed.getReceivedByName()).isEqualTo("Ayşe Gültekin");
        assertThat(reloadStock().getQuantity())
                .as("düzeltme ikinci bir stok hareketi değil")
                .isEqualTo(37);
    }

    @Test
    @DisplayName("Planlı sevkiyat iptal edilince rezervasyon geri bırakılır")
    void cancellingAPlanReleasesTheReservation() {
        var planned = handoverService.handOver(
                request(7, LocalDateTime.now().plusDays(4)), "admin");
        assertThat(reloadStock().getReservedQuantity()).isEqualTo(7);

        transferService.cancelTransfer(planned.transfer().getId(), "Müşteri vazgeçti");

        Stock after = reloadStock();
        assertThat(after.getQuantity()).isEqualTo(40);
        assertThat(after.getReservedQuantity()).isZero();
    }

    // ───────────────────────────── Erteleme ──────────────────────────────────

    @Test
    @DisplayName("Erteleme tarihi taşır, stoğa dokunmaz, hatırlatmaları sıfırlar")
    void reschedulingKeepsTheGoodsReservedAndReopensTheReminders() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        var planned = handoverService.handOver(request(3, tomorrow), "admin");
        Long transferId = planned.transfer().getId();

        // "Yarın teslim" aşaması gönderilip damgalanıyor.
        reminderService.sweepAll();
        assertThat(transferRepository.findById(transferId).orElseThrow()
                .getReminderDayBeforeAt()).isNotNull();

        LocalDateTime later = LocalDateTime.now().plusDays(6).withHour(11).withMinute(0);
        transferService.rescheduleDelivery(transferId, later, "Araç yok");

        StockTransfer moved = transferRepository.findById(transferId).orElseThrow();
        assertThat(moved.getScheduledDeliveryAt()).isEqualTo(later);
        assertThat(moved.getReminderDayBeforeAt())
                .as("yeni tarih kendi uyarısını hak ediyor")
                .isNull();
        assertThat(moved.getReminderDueDayAt()).isNull();
        assertThat(moved.getReminderOverdueAt()).isNull();

        Stock after = reloadStock();
        assertThat(after.getQuantity()).isEqualTo(40);
        assertThat(after.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Planlı olmayan sevkiyat ertelenemez")
    void anUnplannedShipmentCannotBeRescheduled() {
        var immediate = handoverService.handOver(request(1, null), "admin");
        assertThatThrownBy(() -> transferService.rescheduleDelivery(
                immediate.transfer().getId(), LocalDateTime.now().plusDays(2), null))
                .isInstanceOf(WarehouseManagementException.class)
                .hasMessageContaining("planlı bir teslimat değil");
    }

    // ───────────────────────────── Liste ve sayaçlar ─────────────────────────

    @Test
    @DisplayName("Planlı teslimat 'Yolda' sayacında görünmez, kendi sayacında toplanır")
    void aPlannedDeliveryIsCountedAsPlannedNotInTransit() {
        handoverService.handOver(request(2, LocalDateTime.now().plusDays(3)), "admin");

        var summary = transferService.getTransferSummary(new StockTransferFilter(), false);

        assertThat(summary.getStatusCounts().getOrDefault("SCHEDULED", 0L))
                .as("planlı sevkiyat kendi kovasında")
                .isEqualTo(1L);
        assertThat(summary.getStatusCounts().getOrDefault("IN_TRANSIT", 0L))
                .as("listede \"Planlandı\" yazan kayıt sayaçta \"Yolda\" sayılmamalı")
                .isZero();
    }

    @Test
    @DisplayName("Planlı filtresi yalnızca açık planları getirir")
    void theScheduledFilterReturnsOnlyOpenPlans() {
        var planned = handoverService.handOver(
                request(2, LocalDateTime.now().plusDays(3)), "admin");
        handoverService.handOver(request(1, null), "admin");   // anında çıkış
        var toComplete = handoverService.handOver(
                request(1, LocalDateTime.now().plusDays(2)), "admin");
        handoverService.completeScheduledDelivery(toComplete.transfer().getId(),
                null, "Ayşe Gültekin", null, null, "admin");

        StockTransferFilter filter = new StockTransferFilter();
        filter.setScheduledOnly(true);
        var page = transferService.getTransfersPaged(filter, PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(StockTransfer::getId)
                .as("kapanan plan ve planı olmayan çıkış kuyrukta durmamalı")
                .containsExactly(planned.transfer().getId());
    }

    // ───────────────────────────── Hatırlatmalar ─────────────────────────────

    @Test
    @DisplayName("Teslimden bir gün önce hatırlatma gider, ikinci taramada tekrarlamaz")
    void theDayBeforeReminderIsSentExactlyOnce() {
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        var planned = handoverService.handOver(request(2, tomorrow), "admin");

        reminderService.sweepAll();

        ArgumentCaptor<DeliveryReminderMail> captor =
                ArgumentCaptor.forClass(DeliveryReminderMail.class);
        verify(emailService).sendDeliveryReminder(anyString(), captor.capture());
        DeliveryReminderMail mail = captor.getValue();
        assertThat(mail.stage()).isEqualTo(DeliveryReminderStage.DAY_BEFORE);
        assertThat(mail.daysLabel()).isEqualTo("Yarın");
        assertThat(mail.customerFullName()).isEqualTo("Ayşe Gültekin");
        assertThat(mail.customerAddress()).contains("Paşakapı");
        assertThat(mail.totalQuantity()).isEqualTo(2);
        assertThat(mail.items()).singleElement()
                .satisfies(line -> assertThat(line.sku()).isEqualTo("PLN-001"));
        assertThat(mail.receiptNo()).isEqualTo(planned.receipt().getReceiptNo());

        // Job iki kez koşsa, elle tetiklense ya da iki instance aynı anda çalışsa bile
        // aynı hatırlatma ikinci kez gitmemeli.
        reminderService.sweepAll();
        verify(emailService, times(1)).sendDeliveryReminder(anyString(), any());
    }

    @Test
    @DisplayName("Teslim günü gelmişken 'yarın teslim' maili atılmaz, teslim günü maili atılır")
    void aStageWhoseMomentHasPassedIsClosedWithoutSending() {
        LocalDateTime today = LocalDateTime.now().plusMinutes(5);
        var planned = handoverService.handOver(request(2, today), "admin");

        reminderService.sweepAll();

        ArgumentCaptor<DeliveryReminderMail> captor =
                ArgumentCaptor.forClass(DeliveryReminderMail.class);
        verify(emailService, times(1)).sendDeliveryReminder(anyString(), captor.capture());
        assertThat(captor.getValue().stage()).isEqualTo(DeliveryReminderStage.DUE_TODAY);

        StockTransfer transfer = transferRepository.findById(planned.transfer().getId()).orElseThrow();
        assertThat(transfer.getReminderDayBeforeAt())
                .as("kaçırılan aşama postalanmadan kapatılır, yoksa her taramada denenirdi")
                .isNotNull();
        assertThat(transfer.getReminderDueDayAt()).isNotNull();
    }

    @Test
    @DisplayName("Tamamlanan teslimat hatırlatma taramasının dışında kalır")
    void aCompletedDeliveryStopsReminding() {
        var planned = handoverService.handOver(
                request(2, LocalDateTime.now().plusDays(1)), "admin");
        handoverService.completeScheduledDelivery(planned.transfer().getId(),
                null, "Ayşe Gültekin", null, null, "admin");

        reminderService.sweepAll();

        verify(emailService, never()).sendDeliveryReminder(anyString(), any());
    }

    @Test
    @DisplayName("Ayar kapalıyken hatırlatma gönderilmez ama aşama da yakılmaz")
    void remindersCanBeTurnedOffWithoutLosingThem() {
        siteSettingService.updateSettings(
                java.util.Map.of("delivery_reminder_enabled", "false"), "test");
        var planned = handoverService.handOver(
                request(2, LocalDateTime.now().plusDays(1)), "admin");

        reminderService.sweepAll();
        verify(emailService, never()).sendDeliveryReminder(anyString(), any());
        assertThat(transferRepository.findById(planned.transfer().getId()).orElseThrow()
                .getReminderDayBeforeAt())
                .as("kapalıyken damgalansaydı, ayarı açan kişi birikmiş hatırlatmaları kaybederdi")
                .isNull();

        // Ayar açılınca vakti gelmiş aşama hâlâ bekliyor olmalı.
        siteSettingService.updateSettings(
                java.util.Map.of("delivery_reminder_enabled", "true"), "test");
        reminderService.sweepAll();
        verify(emailService, times(1)).sendDeliveryReminder(anyString(), any());
    }
}
