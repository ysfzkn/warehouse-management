package com.warehouse.service.cargo;

import com.warehouse.service.SiteSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kargonomi expects city and district names as an {@code integer id} — whereas our
 * system stores them as text (e.g. "İstanbul", "Kadıköy"). This service is bidirectional:
 *
 * <ol>
 *   <li>{@code GET /states/1} (Turkey) → fetches the 81 provinces and caches them</li>
 *   <li>{@code GET /cities/{stateId}} → fetches that province's districts and caches them</li>
 *   <li>{@link #lookupStateId(String)} / {@link #lookupCityId(int, String)} — text → id</li>
 * </ol>
 *
 * <p>The cache is valid for 24 hours; the first lookup calls the API, subsequent lookups
 * return from memory. Variants such as "İstanbul", "istanbul", "İSTANBUL", "Istanbul" are
 * normalized down to the same key (including Turkish character folding).
 */
@Service
public class KargonomiGeoLookupService {

    private static final Logger log = LoggerFactory.getLogger(KargonomiGeoLookupService.class);
    private static final int TURKEY_COUNTRY_ID = 1;
    private static final long CACHE_TTL_MS = 24L * 60 * 60 * 1000;

    private final SiteSettingService settingService;
    private final RestTemplate restTemplate;

    // Map<normalized_name, id>
    private volatile Map<String, Integer> stateCache;
    private volatile long stateCacheExpireAt = 0;

    // Map<stateId, Map<normalized_district_name, id>>
    private final Map<Integer, Map<String, Integer>> cityCache = new ConcurrentHashMap<>();
    private final Map<Integer, Long> cityCacheExpiresAt = new ConcurrentHashMap<>();

    // The same data with its original spelling, for the address form. The lookup maps fold
    // Turkish characters away, which is right for matching and wrong for showing to a customer.
    private volatile List<GeoEntry> stateList = List.of();
    private final Map<Integer, List<GeoEntry>> cityList = new ConcurrentHashMap<>();

    /** One province or district exactly as Kargonomi spells it. */
    public record GeoEntry(int id, String name) {}

    /** The 81 provinces, for the checkout address form. Empty if Kargonomi cannot be reached. */
    public List<GeoEntry> states() {
        ensureStateCache();
        return stateList;
    }

    /** A province's districts. Empty if the province is unknown or Kargonomi cannot be reached. */
    public List<GeoEntry> cities(int stateId) {
        ensureCityCache(stateId);
        return cityList.getOrDefault(stateId, List.of());
    }

    /**
     * Would a shipment to this address get past the carrier's address check?
     *
     * <p>Answers {@code true} when we cannot tell — an unreachable Kargonomi must not block a
     * checkout. Being wrong in that direction costs a retry from the outbox; being wrong the
     * other way costs a sale.
     */
    public boolean isDeliverable(String city, String district) {
        if (city == null || city.isBlank() || district == null || district.isBlank()) return true;
        if (ensureStateCache().isEmpty()) return true;          // carrier unreachable — do not judge
        Integer stateId = lookupStateId(city);
        if (stateId == null) return false;
        if (ensureCityCache(stateId).isEmpty()) return true;    // same, one level down
        return lookupCityId(stateId, district) != null;
    }

    public KargonomiGeoLookupService(SiteSettingService settingService) {
        this.settingService = settingService;
        this.restTemplate = new RestTemplate();
    }

    /** Converts a text province name to a Kargonomi state_id. {@code null} if not found. */
    public Integer lookupStateId(String stateName) {
        if (stateName == null || stateName.isBlank()) return null;
        Map<String, Integer> cache = ensureStateCache();
        return cache.get(normalize(stateName));
    }

    /** city_id from a district name within a given province. {@code null} if not found. */
    public Integer lookupCityId(int stateId, String cityName) {
        if (cityName == null || cityName.isBlank()) return null;
        Map<String, Integer> cache = ensureCityCache(stateId);
        return cache.get(normalize(cityName));
    }

    /** Province name + district name → (stateId, cityId) pair. Returns null if either is missing. */
    public int[] lookupStateAndCity(String stateName, String cityName) {
        Integer stateId = lookupStateId(stateName);
        if (stateId == null) return null;
        Integer cityId = lookupCityId(stateId, cityName);
        if (cityId == null) return null;
        return new int[]{ stateId, cityId };
    }

    // ── Cache loading ──

    private synchronized Map<String, Integer> ensureStateCache() {
        if (stateCache != null && System.currentTimeMillis() < stateCacheExpireAt) {
            return stateCache;
        }
        try {
            String url = getBaseUrl() + "/states/" + TURKEY_COUNTRY_ID;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class);
            Map<String, Integer> built = parseIdNameList(response.getBody(), "name", "id");
            stateCache = built;
            stateList = parseDisplayList(response.getBody());
            stateCacheExpireAt = System.currentTimeMillis() + CACHE_TTL_MS;
            log.info("[KargonomiGeo] states cache loaded: {} entries", built.size());
            return stateCache;
        } catch (Exception e) {
            log.warn("[KargonomiGeo] state lookup failed: {}", e.getMessage());
            return stateCache != null ? stateCache : Map.of();
        }
    }

    private Map<String, Integer> ensureCityCache(int stateId) {
        Long expiry = cityCacheExpiresAt.get(stateId);
        if (expiry != null && System.currentTimeMillis() < expiry && cityCache.containsKey(stateId)) {
            return cityCache.get(stateId);
        }
        try {
            String url = getBaseUrl() + "/cities/" + stateId;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), Map.class);
            Map<String, Integer> built = parseIdNameList(response.getBody(), "name", "id");
            cityCache.put(stateId, built);
            cityList.put(stateId, parseDisplayList(response.getBody()));
            cityCacheExpiresAt.put(stateId, System.currentTimeMillis() + CACHE_TTL_MS);
            log.debug("[KargonomiGeo] cities for state {} loaded: {}", stateId, built.size());
            return built;
        } catch (Exception e) {
            log.warn("[KargonomiGeo] city lookup failed (state {}): {}", stateId, e.getMessage());
            return cityCache.getOrDefault(stateId, Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseIdNameList(Map<String, Object> body, String nameKey, String idKey) {
        if (body == null) return Map.of();
        Object data = body.getOrDefault("data", body);
        if (!(data instanceof List)) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (Object item : (List<Object>) data) {
            if (!(item instanceof Map<?,?> m)) continue;
            Object name = m.get(nameKey);
            Object id = m.get(idKey);
            if (name == null || id == null) continue;
            try {
                int idInt = id instanceof Number ? ((Number) id).intValue() : Integer.parseInt(id.toString());
                out.put(normalize(name.toString()), idInt);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    /** Same payload, original spelling, sorted the way a person reads a dropdown. */
    @SuppressWarnings("unchecked")
    private List<GeoEntry> parseDisplayList(Map<String, Object> body) {
        if (body == null) return List.of();
        Object data = body.getOrDefault("data", body);
        if (!(data instanceof List)) return List.of();

        List<GeoEntry> out = new java.util.ArrayList<>();
        for (Object item : (List<Object>) data) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Object name = m.get("name");
            Object id = m.get("id");
            if (name == null || id == null) continue;
            try {
                int idInt = id instanceof Number n ? n.intValue() : Integer.parseInt(id.toString());
                out.add(new GeoEntry(idInt, name.toString()));
            } catch (NumberFormatException ignored) {
                // skip malformed rows rather than dropping the whole list
            }
        }
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return List.copyOf(out);
    }

    private HttpHeaders buildHeaders() {
        String token = settingService.getSetting("kargonomi_api_token");
        String appKey = settingService.getSetting("kargonomi_app_key");
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) headers.setBearerAuth(token);
        if (appKey != null && !appKey.isBlank()) headers.set("X-App-Key", appKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String getBaseUrl() {
        String url = settingService.getSetting("kargonomi_api_base_url");
        if (url == null || url.isBlank()) url = "https://app.kargonomi.com.tr/api/v1";
        return url.replaceAll("/+$", "");
    }

    /**
     * "İstanbul" / "istanbul" / "Istanbul" / "  İSTANBUL " → "istanbul"
     * Turkish character folding + whitespace trim.
     */
    static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(new Locale("tr", "TR"));
        t = t.replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
             .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        // NFD normalize → remove combining marks
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t;
    }

    /** Dev/test helper — clears the caches. */
    public void clearCache() {
        stateCache = null;
        stateCacheExpireAt = 0;
        cityCache.clear();
        cityCacheExpiresAt.clear();
        stateList = List.of();
        cityList.clear();
    }
}
