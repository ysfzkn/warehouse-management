package com.warehouse.assistant.wms.prompt;

import com.warehouse.assistant.wms.policy.WmsRolePolicy;
import com.warehouse.assistant.wms.web.WmsUiContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Loads and composes the system prompt for the WMS assistant profile.
 * Markdown files live under {@code classpath:prompts/wms/}.
 */
@Service
public class WmsPromptService {

    private final ResourceLoader resourceLoader;

    public WmsPromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String buildSystemPrompt(String username, String role, WmsUiContext ui) {
        String base = read("classpath:prompts/wms/system.md");
        String dev = read("classpath:prompts/wms/developer.md");
        String uiBlock = buildUiBlock(ui);

        String roleDisplay = WmsRolePolicy.roleDisplayName(role);

        return """
                                %s

                                %s

                                ## Current user context
                                - username: %s
                - role (user-friendly): %s

                                %s
                """.formatted(base, dev, safe(username), safe(roleDisplay), uiBlock);
    }

    public String buildSystemPrompt(String username, String role, boolean allowMutations, WmsUiContext ui) {
        String prompt = buildSystemPrompt(username, role, ui);
        // IMPORTANT: Do not expose internal flags (like allowMutations) to end users.
        // We encode it as an instruction, not as a raw value to be echoed.
        String mutationInstruction = allowMutations
                ? """
                ## Session constraints
                - Mutating actions may be available, but require explicit confirmation.
                """
                : """
                ## Session constraints
                - Mutating actions are disabled in this chat session. Do NOT mention internal flags.
                - If the user asks to create/update/delete, politely explain you can guide them via the UI steps instead.
                """;

        return prompt + "\n" + mutationInstruction + "\n" + WmsRolePolicy.policyBlockForRole(role) + "\n";
    }

    private String buildUiBlock(WmsUiContext ui) {
        if (ui == null) {
            return "## Current UI context\n- route: (unknown)\n";
        }
        return """
                ## Current UI context
                - route: %s
                - warehouseId: %s
                - productId: %s
                - stockId: %s
                """.formatted(safe(ui.route), String.valueOf(ui.warehouseId), String.valueOf(ui.productId), String.valueOf(ui.stockId));
    }

    private String read(String location) {
        try {
            Resource res = resourceLoader.getResource(location);
            try (var is = res.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            // Fail soft: prompt should still work.
            return "";
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
