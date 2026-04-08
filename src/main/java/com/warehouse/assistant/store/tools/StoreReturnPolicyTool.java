package com.warehouse.assistant.store.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Static fallback for the return / cancellation policy. Used when the
 * admin hasn't uploaded a return policy document yet. Once a real document
 * exists, {@code StoreFaqSearchTool} will take over and this tool becomes a
 * last-resort hint.
 */
@Component
public class StoreReturnPolicyTool {

    private static final String DEFAULT_POLICY = """
            İade ve değişim koşulları (genel kurallar):

            - Ürünü teslim aldıktan sonra **14 gün** içinde iade başvurusu yapabilirsiniz.
            - Ürün, orijinal ambalajında, kullanılmamış, yıkanmamış ve tüm etiketleri üzerinde olacak şekilde olmalıdır.
            - Beyaz eşya ürünlerinde fiziksel hasar bulunmamalı, kurulum yapılmamış olmalıdır.
            - İade kargo bedeli, anlaşmalı kargo firmamız kullanıldığında mağaza tarafından karşılanır.
            - Ürün bize ulaştıktan sonra 3-5 iş günü içinde incelenir; uygun bulunursa ödeme orijinal ödeme yöntemine iade edilir.
            - Banka/kart iadesi 5-10 iş günü içinde hesabınıza yansır (banka işlem süresine bağlı olarak değişebilir).

            Detaylı ve güncel politika için mağazanın 'İade ve Değişim' sayfasına yönlendirme yapmanı öneririm.
            """;

    @Tool(description = "STORE: Varsayılan iade ve değişim politikası metnini döndürür. "
            + "ÖNCE StoreFaqSearchTool'u dene; eğer SSS dokümanında iade politikası bulunamazsa bu tool'u çağır.")
    public String getDefaultReturnPolicy() {
        return DEFAULT_POLICY;
    }
}
