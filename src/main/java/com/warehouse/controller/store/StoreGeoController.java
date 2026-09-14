package com.warehouse.controller.store;

import com.warehouse.service.cargo.KargonomiGeoLookupService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provinces and districts for the checkout address form — the carrier's own list.
 *
 * <p>Customers used to type the address freely, and the name was only matched against the
 * carrier's records at dispatch, by which point a "Kadıköy/İst" was no longer something anyone
 * could fix quickly. Picking from this list means the address is known to be deliverable before
 * the order is placed.
 *
 * <p>Backed by the 24-hour geo cache, so this does not become a carrier call per keystroke;
 * the response also carries its own cache header for the browser.
 */
@RestController
@RequestMapping("/api/store/geo")
public class StoreGeoController {

    private final KargonomiGeoLookupService geoLookup;

    public StoreGeoController(KargonomiGeoLookupService geoLookup) {
        this.geoLookup = geoLookup;
    }

    /**
     * The 81 provinces. An empty list means the carrier is unreachable — the address form should
     * fall back to free text rather than blocking the customer.
     */
    @GetMapping("/states")
    public ResponseEntity<List<KargonomiGeoLookupService.GeoEntry>> states() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(geoLookup.states());
    }

    /** One province's districts. */
    @GetMapping("/cities/{stateId}")
    public ResponseEntity<List<KargonomiGeoLookupService.GeoEntry>> cities(@PathVariable int stateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(geoLookup.cities(stateId));
    }
}
