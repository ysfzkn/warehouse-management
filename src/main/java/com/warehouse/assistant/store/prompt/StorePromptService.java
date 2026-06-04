package com.warehouse.assistant.store.prompt;

import com.warehouse.assistant.store.dto.StoreUiContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Loads and composes the store assistant system prompt. Mirrors the shape of
 * {@code WmsPromptService} — markdown files under {@code classpath:prompts/store/}
 * are read once per request, with a small session block appended at the end.
 */
@Service
public class StorePromptService {

    private final ResourceLoader resourceLoader;

    public StorePromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String buildSystemPrompt(boolean isGuest, String customerDisplayName, StoreUiContext ui) {
        return buildSystemPrompt(isGuest, customerDisplayName, ui, null);
    }

    public String buildSystemPrompt(boolean isGuest, String customerDisplayName, StoreUiContext ui, String siteName) {
        String safeSiteName = (siteName != null && !siteName.isBlank()) ? siteName.trim() : "mağazamızın";
        String base = read("classpath:prompts/store/system.md").replace("online white-goods storefront", safeSiteName + " online mağazası");
        String dev = read("classpath:prompts/store/developer.md");

        String actorBlock = isGuest
                ? """
                ## Current session
                - Kullanıcı durumu: **misafir** (üye girişi yapılmamış).
                - Kişisel işlemlerde (sipariş, adres, iade başvurusu) önce üye girişine yönlendir.
                """
                : """
                ## Current session
                - Kullanıcı durumu: **üye** (müşteri hesabı aktif)%s.
                - Kişisel sorgulamalarda doğrudan tool'ları kullanabilirsin.
                """.formatted(customerDisplayName != null && !customerDisplayName.isBlank()
                        ? " — görünen isim: " + customerDisplayName
                        : "");

        String uiBlock;
        if (ui == null) {
            uiBlock = "## Current UI context\n- route: (unknown)\n";
        } else {
            uiBlock = """
                    ## Current UI context
                    - route: %s
                    - productId: %s
                    - categoryId: %s
                    """.formatted(
                    safe(ui.route),
                    String.valueOf(ui.productId),
                    String.valueOf(ui.categoryId));
        }

        return base + "\n\n" + dev + "\n\n" + actorBlock + "\n" + uiBlock;
    }

    private String read(String location) {
        try {
            Resource res = resourceLoader.getResource(location);
            try (var is = res.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
