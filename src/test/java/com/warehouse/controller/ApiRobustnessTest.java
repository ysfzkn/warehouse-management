package com.warehouse.controller;

import com.warehouse.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kötü yazılmış isteklerin karşılığı.
 *
 * <p>Bu testlerin hepsi bir zamanlar 500 dönen gerçek vakalar. Yanlış durum kodu yalnızca
 * bir incelik meselesi değil: {@code ?page=abc} yazan bir bot 500 üretebildiği sürece hata
 * grafiği sürekli kırmızı kalıyor ve gerçek bir arıza o gürültünün içinde kayboluyor. Bu
 * uygulamada tam olarak öyle oldu.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiRobustnessTest {

    @Autowired private MockMvc mvc;
    @Autowired private OrderRepository orderRepository;

    @Test
    @DisplayName("Sayı beklenen parametreye metin gelirse 400")
    @WithMockUser(roles = "ADMIN")
    void typeMismatchIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/products").param("page", "abc").param("size", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/products/abc")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Zorunlu parametre eksikse 400")
    @WithMockUser(roles = "ADMIN")
    void missingRequiredParameterIsBadRequest() throws Exception {
        mvc.perform(get("/api/admin/brands/search")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Vitrin listesi tanınmayan sıralama alanında patlamaz")
    void unknownSortFieldFallsBack() throws Exception {
        // Kimlik doğrulamasız uç nokta. Sort.by'a giden serbest metin, var olmayan bir alan
        // adında PropertyReferenceException atıyordu: isteyen herkes sunucu hatası
        // üretebiliyordu. Hata değil, varsayılana dönüş — eski bağlantılar da kırılmasın.
        mvc.perform(get("/api/store/products").param("sortBy", ";DROP").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Vitrin listesinde sayfa boyutu sınırlı")
    void pageSizeIsCapped() throws Exception {
        // Sınırsızken tek istekle tüm katalog ilişkileriyle belleğe çekilebiliyordu.
        mvc.perform(get("/api/store/products").param("size", "200000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("Vitrin listesinde sayfa numarası sınırlı")
    void pageNumberIsCapped() throws Exception {
        // ?page=999999999 offset hesabında taşıp 500 üretiyordu.
        mvc.perform(get("/api/store/products").param("page", "999999999"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Oturumsuz sepet isteği boş sepet döner")
    void anonymousCartIsEmptyNotAnError() throws Exception {
        // Ne müşteri ne oturum başlığı: sahibi olmayan sepet veritabanına yazılamaz
        // (chk_cart_owner). Eskiden yine de INSERT deneniyor, her istek ERROR log
        // bırakıp "veri bütünlüğü hatası" diye anlamsız bir mesajla dönüyordu.
        mvc.perform(get("/api/store/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    @DisplayName("Tek satırlık toplama sorgusu sütunları doğru döndürür")
    void singleRowAggregateReturnsColumns() throws Exception {
        // Depo metodu Object[] döndürdüğünde Spring Data bunu "sütunlardan oluşan tek satır"
        // değil "satırlardan oluşan dizi" diye yorumluyor ve sonucu bir kat daha sarmalıyordu:
        // sonuc[0] sayı yerine iç dizinin kendisi oluyor, (Number) dönüşümü ClassCastException
        // atıyordu. Satış panosunun canlı kutusu bu yüzden hep 500 dönüyordu; derleme
        // sırasında hiçbir uyarı yok, hata yalnızca çalışırken çıkıyor.
        List<Object[]> rows = orderRepository.aggregateBetween(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row).hasSize(2);
        assertThat(row[0]).isInstanceOf(Number.class);
        assertThat(row[1]).isInstanceOf(Number.class);
    }

    @Test
    @DisplayName("Dosya bekleyen uca JSON gelirse 400")
    @WithMockUser(roles = "ADMIN")
    void multipartEndpointRejectsJsonBody() throws Exception {
        // Yanlış Content-Type istemcinin hatası; sunucuda arıza yok.
        mvc.perform(post("/api/admin/settings/site/logo")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Olmayan yoruma yanıt/onay 404")
    @WithMockUser(roles = "ADMIN")
    void missingReviewIsNotFound() throws Exception {
        // Üç uç da argümansız orElseThrow() kullanıyordu: NoSuchElementException catch-all'a
        // düşüp 500 dönüyordu. Silinmiş bir yoruma tıklamak sunucu arızası değil.
        mvc.perform(put("/api/admin/reviews/999999999/reply")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\":\"deneme\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/reviews/999999999/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("İade tutarı eksik ya da sayı değilse 400")
    @WithMockUser(roles = "ADMIN")
    void refundValidatesAmount() throws Exception {
        // Tutar gövdeden doğrudan okunuyordu: alan yoksa NullPointerException, sayı değilse
        // NumberFormatException — ikisi de 500. Para iade eden bir uçta "beklenmeyen hata"
        // demek, işlemin yapılıp yapılmadığını da belirsiz bırakır.
        mvc.perform(put("/api/admin/orders/999999999/refund")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/orders/999999999/refund")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fatura oluşturmada sipariş kimliği zorunlu")
    @WithMockUser(roles = "ADMIN")
    void invoiceRequiresOrderId() throws Exception {
        // orderId boşken findById(null) çağrılıyor ve Spring Data patlıyordu.
        mvc.perform(post("/api/admin/invoices")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Depo rolleri yönetici uçlarında yazma yapamaz")
    @WithMockUser(roles = "STOCK_IN")
    void warehouseRoleCannotMutateAdminResources() throws Exception {
        // Yetki taraması 194 mutasyon ucunun tamamında temiz çıktı; buradaki birkaç örnek
        // o taramanın yerini tutmuyor, yalnızca kuralın kazara gevşemesini yakalıyor.
        mvc.perform(post("/api/admin/invoices")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/orders/999999999/refund")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/reviews/999999999/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Satış panosunun canlı kutusu ve dönem karşılaştırması çalışır")
    @WithMockUser(roles = "ADMIN")
    void salesDashboardEndpointsRespond() throws Exception {
        mvc.perform(get("/api/admin/sales-dashboard/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayOrders").exists());
        // period verilince önceki dönem karşılaştırması devreye giriyor; kırık olan yol buydu.
        for (String period : new String[]{"today", "week", "month", "year"}) {
            mvc.perform(get("/api/admin/sales-dashboard/summary").param("period", period))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prevOrders").exists());
        }
    }
}
