package com.warehouse.assistant.wms.policy;

import java.util.Locale;

/**
 * Role-based policy for the WMS (warehouse) assistant profile.
 * Unchanged from the original {@code CezeriRolePolicy} except for the package.
 */
public final class WmsRolePolicy {

    private WmsRolePolicy() {}

    public static boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(safe(role));
    }

    public static String roleDisplayName(String role) {
        role = safe(role).toUpperCase(Locale.ROOT);
        return switch (role) {
            case "ADMIN" -> "Yönetici";
            case "STOCK_IN" -> "Stok Ekleme Kullanıcısı";
            case "STOCK_OUT" -> "Stok Çıkarma Kullanıcısı";
            default -> "Kullanıcı";
        };
    }

    public static String policyBlockForRole(String role) {
        role = safe(role).toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return """
## Role policy (Admin)
- You can guide across all areas of the app.
- Still require explicit confirmation for irreversible/destructive actions.
- Never expose internal flags (e.g., allowMutations) or internal role codes to the user.
""";
        }
        return """
## Role policy (Non-admin)
- Help only with what the user is allowed to do.
- If the user requests an admin-only action, respond politely:
  - say they don't have permission
  - list what you *can* help with
  - suggest using the relevant screen in the UI or contacting an admin
- Never expose internal flags (e.g., allowMutations) or internal role codes to the user.
""";
    }

    public static String capabilitiesForRole(String role) {
        role = safe(role).toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(role)) {
            return """
Ben **Cezeri**. Bu sistemde **yönetici** rolüyle şunlarda yardımcı olabilirim:

- **Stok ekranı (/stock)**: stok arama ve filtreleme, düşük stok / stokta olmayan ürünler, depo+ürün bazında stok durumunu inceleme
- **Ürünler ekranı (/products)**: ürün adı veya stok kodu (SKU) ile ürün bulma, ürün detaylarına yönlendirme, toplam stokları yorumlama
- **Depolar ekranı (/warehouses)**: depo listesini inceleme, depo bazlı stok değerlendirmesi
- **Transferler**: transferleri arama/filtreleme, durum özetini yorumlama, onay süreçlerine yönlendirme
- **Stok talepleri**: talepleri listeleme, “bekleyen/onaylanan/reddedilen” durumlarına göre yönlendirme
- **Bildirimler**: okunmamış/son bildirimleri inceleme, filtreleyerek arama
- **Denetim kayıtları (audit)**: hareket kayıtlarını inceleme ve kısa özet çıkarma

Not: Sayısal veya liste içeren konularda sistemden veri çekmeden tahmin yürütmem.
""";
        }
        String displayRole = roleDisplayName(role);
        String requestHint = switch (role) {
            case "STOCK_IN" -> "Stok artırma talepleri oluşturma konusunda ekrandan adım adım yönlendirebilirim.";
            case "STOCK_OUT" -> "Stok azaltma talepleri oluşturma konusunda ekrandan adım adım yönlendirebilirim.";
            default -> "Stok talepleri konusunda ekrandan adım adım yönlendirebilirim.";
        };

        return """
Ben **Cezeri**. Bu sistemde **%s** rolüyle şunlarda yardımcı olabilirim:

- **Stok ekranı (/stock)**: stok arama ve filtreleme, düşük stok / stokta olmayan ürünler, depo+ürün bazında stok durumunu inceleme
- **Ürünler ekranı (/products)**: ürün adı veya stok kodu (SKU) ile ürün bulma, ürün detaylarına yönlendirme
- **Depolar ekranı (/warehouses)**: depo listesini görüntüleme
- **Kendi stok taleplerin**: “bekleyen / onaylanan / reddedilen” durumlarına göre listeleme ve yönlendirme
- **Talep oluşturma yönlendirmesi**: %s

Yönetici yetkisi gerektiren (bu rolde yapamayacağın) alanlar:
- Bildirimler
- Denetim kayıtları (audit)
- Kullanıcı yönetimi
- Yönetici ayarları

Not: Yetkin olmayan bir şey istersen, bunu kibarca belirtip sana uygun alternatifleri madde madde öneririm.
""".formatted(displayRole, requestHint);
    }

    public static boolean isCapabilitiesQuestion(String userText) {
        String t = safe(userText).toLowerCase(Locale.ROOT);
        return t.contains("neler yap") || t.contains("ne yapabili") || t.contains("yetkinlik")
                || t.contains("özellik") || t.contains("yardım edebil");
    }

    public static boolean looksAdminOnlyRequest(String userText) {
        String t = safe(userText).toLowerCase(Locale.ROOT);
        return t.contains("audit")
                || t.contains("bildirim")
                || t.contains("notification")
                || t.contains("kullanıcı")
                || t.contains("user ")
                || t.contains("admin ayar")
                || t.contains("admin settings")
                || t.contains("onayla")
                || t.contains("approve")
                || t.contains("reject")
                || t.contains("reddet");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
