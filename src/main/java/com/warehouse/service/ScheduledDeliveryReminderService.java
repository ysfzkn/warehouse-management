package com.warehouse.service;

import com.warehouse.constants.NotificationMessages;
import com.warehouse.dto.DeliveryReminderMail;
import com.warehouse.dto.NotificationRequest;
import com.warehouse.entity.DeliveryReceipt;
import com.warehouse.entity.StockTransfer;
import com.warehouse.entity.StockTransferItem;
import com.warehouse.enums.DeliveryReminderStage;
import com.warehouse.enums.DomainEntityType;
import com.warehouse.repository.DeliveryReceiptRepository;
import com.warehouse.repository.StockTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Planlı teslimatların hatırlatmaları: panele bildirim, ayarlardaki adrese mail.
 *
 * <p>Tek bir kural var ve her yerde aynı işliyor: <em>vakti gelmiş aşama gönderilir, vakti
 * geçmiş aşama gönderilmeden kapatılır.</em> Aşamaları hesaplayan yer burası; hem her sabah
 * koşan job hem de planı yeni kurulan/ertelenen sevkiyat aynı metodu çağırıyor. İki ayrı
 * gönderim yolu olsaydı biri diğerinden kaçınılmaz olarak ayrışır ve aynı teslimat için ya
 * iki mail ya hiç mail çıkardı.</p>
 *
 * <p>Tekrarı engelleyen şey bir bayrak değil, aşama başına damga: {@code reminderDayBeforeAt},
 * {@code reminderDueDayAt}, {@code reminderOverdueAt}. Job günde bir koşuyor ama iki instance
 * aynı anda koşsa, elle tetiklense ya da gün içinde plan ertelenip yeniden taransa bile
 * damgalı aşama ikinci kez gönderilmiyor.</p>
 *
 * <p>"Vakti geçmiş aşamayı gönderme" kısmı gürültü kontrolü: teslim günü sabahı "yarın
 * teslim edilecek" maili atmak hatırlatma değil, hatırlatmalara güveni bitiren şeydir.</p>
 */
@Service
public class ScheduledDeliveryReminderService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDeliveryReminderService.class);

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StockTransferRepository transferRepository;
    private final DeliveryReceiptRepository receiptRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SiteSettingService settings;

    public ScheduledDeliveryReminderService(StockTransferRepository transferRepository,
                                            DeliveryReceiptRepository receiptRepository,
                                            NotificationService notificationService,
                                            EmailService emailService,
                                            SiteSettingService settings) {
        this.transferRepository = transferRepository;
        this.receiptRepository = receiptRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.settings = settings;
    }

    /**
     * Açık bütün planları tarar. Job'ın çağırdığı giriş noktası.
     *
     * @return gönderilen hatırlatma sayısı
     */
    @Transactional
    public int sweepAll() {
        List<StockTransfer> open = transferRepository.findOpenScheduledDeliveries();
        if (open.isEmpty()) {
            log.debug("[TeslimatHatirlatma] Açık planlı teslimat yok.");
            return 0;
        }
        int sent = 0;
        for (StockTransfer transfer : open) {
            // Tek bir sevkiyatın hatası tüm taramayı düşürmemeli: kırk teslimatın
            // hatırlatması, biri yüzünden gitmeden kalırsa asıl zarar orada.
            try {
                sent += process(transfer);
            } catch (Exception e) {
                log.error("[TeslimatHatirlatma] Sevkiyat {} işlenemedi: {}",
                        transfer.getId(), e.getMessage(), e);
            }
        }
        log.info("[TeslimatHatirlatma] {} açık plan tarandı, {} hatırlatma gönderildi.",
                open.size(), sent);
        return sent;
    }

    /**
     * Tek bir sevkiyatı tarar — planı yeni kurulmuş ya da ertelenmiş kayıtlar için.
     *
     * <p>Sabah koşan job'ı beklemek, bugüne veya yarına planlanan bir teslimatın
     * hatırlatmasını tamamen kaçırmak demek: o günün taraması çoktan geçmiş olur.</p>
     */
    @Transactional
    public int sweepOne(Long transferId) {
        StockTransfer transfer = transferRepository.findById(transferId).orElse(null);
        if (transfer == null || transfer.getScheduledDeliveryAt() == null) {
            return 0;
        }
        return process(transfer);
    }

    // ───────────────────────────── Aşama hesabı ──────────────────────────────

    private int process(StockTransfer transfer) {
        // Kapalıyken hiçbir şey damgalanmıyor, sadece gönderim atlanmıyor. Damgalamak,
        // ayarı sonradan açan kişinin o güne kadar birikmiş bütün hatırlatmalarını sessizce
        // kaybetmesi demek olurdu — kapatma kararı geçmişi silmemeli.
        if (!settings.getBoolSetting("delivery_reminder_enabled", true)) {
            log.debug("[TeslimatHatirlatma] Ayar kapalı, tarama atlandı. transferId={}",
                    transfer.getId());
            return 0;
        }

        LocalDate due = transfer.getScheduledDeliveryAt().toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;

        // 1 gün önce. Günü geçtiyse damgalanır ama postalanmaz.
        if (transfer.getReminderDayBeforeAt() == null && !today.isBefore(due.minusDays(1))) {
            boolean onTime = today.isEqual(due.minusDays(1));
            transfer.setReminderDayBeforeAt(now);
            if (onTime && send(transfer, DeliveryReminderStage.DAY_BEFORE)) sent++;
        }

        // Teslim günü.
        if (transfer.getReminderDueDayAt() == null && !today.isBefore(due)) {
            boolean onTime = today.isEqual(due);
            transfer.setReminderDueDayAt(now);
            if (onTime && send(transfer, DeliveryReminderStage.DUE_TODAY)) sent++;
        }

        // Gecikme uyarısı. Tek sefer: her gün tekrarlayan bir uyarı okunmaz hâle gelir,
        // ve teslimat ertelendiğinde damgalar sıfırlandığı için yeni tarih kendi
        // uyarısını yeniden hak eder.
        if (transfer.getReminderOverdueAt() == null && today.isAfter(due)) {
            transfer.setReminderOverdueAt(now);
            if (send(transfer, DeliveryReminderStage.OVERDUE)) sent++;
        }

        transferRepository.save(transfer);
        return sent;
    }

    // ─────────────────────────────── Gönderim ────────────────────────────────

    /** @return hatırlatma en az bir kanaldan çıktıysa {@code true} */
    private boolean send(StockTransfer transfer, DeliveryReminderStage stage) {
        DeliveryReminderMail mail = buildMail(transfer, stage);

        // Panel bildirimi her hâlükârda düşer. Mail sunucusu yapılandırılmamış ya da
        // adres girilmemiş olabilir; hatırlatmanın hiç görünmemesi bundan çok daha kötü.
        notificationService.create(NotificationRequest.builder()
                .title(stage == DeliveryReminderStage.OVERDUE
                        ? NotificationMessages.DELIVERY_OVERDUE_TITLE
                        : NotificationMessages.DELIVERY_REMINDER_TITLE)
                .message(notificationText(mail))
                .entityType(DomainEntityType.StockTransfer.name())
                .entityId(transfer.getId())
                .actor("system")
                .sourceWarehouseId(transfer.getSourceWarehouse() != null
                        ? transfer.getSourceWarehouse().getId() : null)
                .sourceWarehouseName(mail.warehouseName())
                .quantity(mail.totalQuantity())
                .note(mail.customerAddress())
                .build());

        String recipient = resolveRecipient();
        if (recipient == null) {
            log.warn("[TeslimatHatirlatma] {} için alıcı e-posta yapılandırılmamış "
                            + "(delivery_reminder_email / invoice_admin_digest_email / contact_form_email). "
                            + "Bildirim panele düştü.",
                    mail.receiptNo() != null ? mail.receiptNo() : ("#" + transfer.getId()));
            return true;
        }
        emailService.sendDeliveryReminder(recipient, mail);
        log.info("[TeslimatHatirlatma] {} → {} ({})", stage, recipient,
                mail.receiptNo() != null ? mail.receiptNo() : ("#" + transfer.getId()));
        return true;
    }

    /**
     * Hatırlatmanın gideceği adres.
     *
     * <p>Kendi ayarı boşsa fatura özeti ve iletişim formu adreslerine düşülüyor. Ayarı hiç
     * açmamış bir kurulumda hatırlatmanın sessizce kaybolmaması için: adres bulunamazsa
     * özellik çalışıyor görünür ama kimseye bir şey ulaşmazdı.</p>
     */
    private String resolveRecipient() {
        for (String key : new String[]{
                "delivery_reminder_email", "invoice_admin_digest_email", "contact_form_email"}) {
            String value = settings.getSetting(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private DeliveryReminderMail buildMail(StockTransfer transfer, DeliveryReminderStage stage) {
        DeliveryReceipt receipt = receiptRepository.findByTransferId(transfer.getId()).orElse(null);

        List<DeliveryReminderMail.Line> lines = new ArrayList<>();
        int total = 0;
        List<StockTransferItem> items = transfer.getItems();
        if (items != null && !items.isEmpty()) {
            for (StockTransferItem item : items) {
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                total += quantity;
                lines.add(new DeliveryReminderMail.Line(
                        item.getProduct() != null ? item.getProduct().getSku() : null,
                        item.getProduct() != null ? item.getProduct().getName() : "-",
                        quantity));
            }
        } else if (transfer.getProduct() != null) {
            // Çok kalemli modelden önce açılmış kayıtlar tek ürün taşıyor.
            total = transfer.getQuantity() == null ? 0 : transfer.getQuantity();
            lines.add(new DeliveryReminderMail.Line(transfer.getProduct().getSku(),
                    transfer.getProduct().getName(), total));
        }

        return new DeliveryReminderMail(
                stage,
                transfer.getId(),
                receipt != null ? receipt.getReceiptNo() : null,
                transfer.getScheduledDeliveryAt().format(DATE_TIME),
                daysLabel(transfer.getScheduledDeliveryAt(), stage),
                transfer.getCustomerFullName(),
                transfer.getCustomerPhone(),
                transfer.getCustomerAddress(),
                transfer.getSourceWarehouse() != null ? transfer.getSourceWarehouse().getName() : null,
                transfer.getHandoverToName(),
                transfer.getOrderNumber(),
                transfer.getNotes(),
                total,
                lines);
    }

    /** "Yarın" / "Bugün" / "3 gün gecikti" — tek bakışta okunan ifade. */
    private static String daysLabel(LocalDateTime scheduledAt, DeliveryReminderStage stage) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), scheduledAt.toLocalDate());
        if (stage == DeliveryReminderStage.OVERDUE) {
            long late = Math.abs(days);
            return late <= 1 ? "1 gün gecikti" : late + " gün gecikti";
        }
        if (days <= 0) return "Bugün";
        if (days == 1) return "Yarın";
        return days + " gün kaldı";
    }

    /** Panel bildiriminin gövdesi — mailin kısa hâli. */
    private static String notificationText(DeliveryReminderMail mail) {
        StringBuilder sb = new StringBuilder();
        sb.append(mail.stage().getTitle()).append(": ")
          .append(mail.scheduledAt()).append(" · ")
          .append(mail.customerFullName() != null ? mail.customerFullName() : "-");
        if (mail.customerPhone() != null) {
            sb.append(" (").append(mail.customerPhone()).append(")");
        }
        if (mail.receiptNo() != null) {
            sb.append(" · Makbuz ").append(mail.receiptNo());
        }
        sb.append(" · ").append(mail.totalQuantity()).append(" adet");
        if (!mail.items().isEmpty()) {
            sb.append(" — ");
            // İlk üç kalem: bildirim gövdesi 2000 karakterle sınırlı ve kalabalık bir
            // döküm zaten okunmuyor; tamamı mailde ve sevkiyat detayında duruyor.
            List<DeliveryReminderMail.Line> preview = mail.items().subList(0,
                    Math.min(3, mail.items().size()));
            sb.append(String.join(", ", preview.stream()
                    .map(line -> line.name() + " ×" + line.quantity()).toList()));
            if (mail.items().size() > 3) {
                sb.append(" ve ").append(mail.items().size() - 3).append(" kalem daha");
            }
        }
        if (mail.customerAddress() != null) {
            sb.append(" · ").append(mail.customerAddress());
        }
        String text = sb.toString();
        return text.length() > 1990 ? text.substring(0, 1990) + "…" : text;
    }
}
