package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Two things that made the carrier look half-broken in a single readiness run: the balance check
 * passed while the province list came back empty, using the same token at the same moment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KargonomiGeoLookupTest {

    @Mock private SiteSettingService settingService;
    @Mock private RestTemplate restTemplate;

    private KargonomiGeoLookupService geoLookup;

    private static final Map<String, Object> TWO_PROVINCES = Map.of(
            "data", List.of(Map.of("id", 34, "name", "İSTANBUL"), Map.of("id", 6, "name", "ANKARA")));

    @BeforeEach
    void setUp() {
        geoLookup = new KargonomiGeoLookupService(settingService, restTemplate);
        when(settingService.getSetting("kargonomi_api_base_url")).thenReturn("");
        when(settingService.getSetting("kargonomi_app_key")).thenReturn("");
    }

    /**
     * The provider trimmed the token before sending it and this service did not, so a value
     * pasted with a trailing newline authenticated on one path and produced a malformed
     * Authorization header on the other.
     */
    @Test
    @DisplayName("Sonunda boşluk olan token kırpılarak gönderilir")
    void aTokenPastedWithTrailingWhitespaceIsStillSentCorrectly() {
        when(settingService.getSetting("kargonomi_api_token")).thenReturn("abc123\n");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(TWO_PROVINCES));

        geoLookup.states();

        var entity = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entity.capture(), eq(Map.class));
        assertThat(entity.getValue().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer abc123");
    }

    /**
     * Turkey has 81 provinces, so an empty list is a failure. Storing it pinned checkout address
     * validation to "broken" for the full 24-hour TTL, with nothing to do but wait it out.
     */
    @Test
    @DisplayName("Boş liste önbelleğe alınmaz, sonraki istekte tekrar denenir")
    void anEmptyListIsRetriedRatherThanRemembered() {
        when(settingService.getSetting("kargonomi_api_token")).thenReturn("abc123");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("data", List.of())))
                .thenReturn(ResponseEntity.ok(TWO_PROVINCES));

        assertThat(geoLookup.states()).isEmpty();

        // Second call must go back to the carrier rather than serving the remembered emptiness.
        assertThat(geoLookup.states()).hasSize(2);
        verify(restTemplate, times(2))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class));
    }

    @Test
    @DisplayName("Dolu liste önbelleğe alınır, tekrar sorulmaz")
    void aRealListIsCachedAndNotAskedForTwice() {
        when(settingService.getSetting("kargonomi_api_token")).thenReturn("abc123");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(TWO_PROVINCES));

        assertThat(geoLookup.states()).hasSize(2);
        assertThat(geoLookup.states()).hasSize(2);

        verify(restTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class));
    }
}
