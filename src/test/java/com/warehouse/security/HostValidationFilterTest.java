package com.warehouse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User-Agent, reddedilen bir isteğin kaynağını tanımak için log'a yazılıyor — ama değeri
 * istemci belirliyor. Buradaki testler o değerin log'a girmeden önce zararsızlaştığını
 * sabitliyor: satır sonu içeren bir başlık log'a sahte satır uydurabilirdi.
 */
class HostValidationFilterTest {

    private final HostValidationFilter filter =
            new HostValidationFilter("admin.example.com", "example.com", new MockEnvironment());

    @Test
    @DisplayName("Satır sonu içeren User-Agent tek satıra indirilir")
    void collapsesNewlinesSoLogLinesCannotBeForged() {
        var request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0\r\n[HostValidation] sahte satır");

        String logged = filter.loggableUserAgent(request);

        assertThat(logged).doesNotContain("\r").doesNotContain("\n");
        assertThat(logged).isEqualTo("Mozilla/5.0  [HostValidation] sahte satır");
    }

    @Test
    @DisplayName("Aşırı uzun User-Agent kısaltılır")
    void truncatesOverlongUserAgent() {
        var request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(500));

        String logged = filter.loggableUserAgent(request);

        assertThat(logged).hasSize(121).endsWith("…");
    }

    @Test
    @DisplayName("Başlık yoksa ya da boşsa tire yazılır")
    void marksMissingUserAgent() {
        assertThat(filter.loggableUserAgent(new MockHttpServletRequest())).isEqualTo("-");

        var blank = new MockHttpServletRequest();
        blank.addHeader("User-Agent", "   ");
        assertThat(filter.loggableUserAgent(blank)).isEqualTo("-");
    }

    @Test
    @DisplayName("Sınırın altındaki değer olduğu gibi kalır")
    void keepsNormalUserAgentIntact() {
        var request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0");

        assertThat(filter.loggableUserAgent(request))
                .isEqualTo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0");
    }
}
