package com.warehouse.controller;

import com.warehouse.service.seo.SeoFragmentRenderer;
import com.warehouse.service.seo.SeoPage;
import com.warehouse.service.seo.SeoRoute;
import com.warehouse.service.seo.StorefrontSeoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.function.Function;

/**
 * HTML fragments nginx includes (SSI) into the storefront's index.html.
 *
 * <p>{@code /seo/head/urun/buzdolabi-x} answers the head tags for {@code /urun/buzdolabi-x},
 * {@code /seo/body/...} the no-JavaScript page content. A 404 is the normal answer for any
 * URL the server cannot describe — nginx then falls back to the static defaults already
 * in index.html, so the page renders exactly as it did before this existed.</p>
 *
 * <p>Public and read-only: everything here is already served by the storefront API.</p>
 */
@RestController
@RequestMapping("/seo")
public class SeoFragmentController {

    private static final MediaType HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8);

    private final StorefrontSeoService seoService;

    public SeoFragmentController(StorefrontSeoService seoService) {
        this.seoService = seoService;
    }

    @GetMapping({"/{slot}", "/{slot}/"})
    public ResponseEntity<String> home(@PathVariable String slot) {
        return render(slot, Optional.of(SeoRoute.home()));
    }

    @GetMapping("/{slot}/{section}/{slug}")
    public ResponseEntity<String> page(@PathVariable String slot,
                                       @PathVariable String section,
                                       @PathVariable String slug) {
        return render(slot, SeoRoute.of(section, slug));
    }

    private ResponseEntity<String> render(String slot, Optional<SeoRoute> route) {
        Function<SeoPage, String> renderer = switch (slot) {
            case "head" -> SeoFragmentRenderer::head;
            case "body" -> SeoFragmentRenderer::body;
            default -> null;
        };
        if (renderer == null) {
            return ResponseEntity.notFound().build();
        }
        return route.flatMap(seoService::describe)
                .map(renderer)
                .map(html -> ResponseEntity.ok().contentType(HTML_UTF8).body(html))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
