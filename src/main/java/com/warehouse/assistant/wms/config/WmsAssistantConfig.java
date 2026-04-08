package com.warehouse.assistant.wms.config;

import com.warehouse.assistant.wms.tools.WmsAdminTools;
import com.warehouse.assistant.wms.tools.WmsCatalogTools;
import com.warehouse.assistant.wms.tools.WmsStockTools;
import com.warehouse.assistant.wms.tools.WmsUserTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Central place for WMS assistant wiring (ChatClient + tool registration).
 * <p>
 * Two ChatClient beans are exposed:
 * <ul>
 *   <li>{@code wmsChatClientUser} — non-admin tool set (catalog + stock + user).</li>
 *   <li>{@code wmsChatClientAdmin} — admin tool set (adds admin-only tools).</li>
 * </ul>
 * The {@code WmsAssistantChatService} picks between them based on the
 * current user's role.
 */
@Configuration
@Profile("!test")
public class WmsAssistantConfig {

    @Bean
    public ChatClient wmsChatClientUser(ChatClient.Builder builder,
                                        WmsCatalogTools catalogTools,
                                        WmsStockTools stockTools,
                                        WmsUserTools userTools) {
        return builder.defaultTools(catalogTools, stockTools, userTools).build();
    }

    @Bean
    public ChatClient wmsChatClientAdmin(ChatClient.Builder builder,
                                         WmsCatalogTools catalogTools,
                                         WmsStockTools stockTools,
                                         WmsUserTools userTools,
                                         WmsAdminTools adminTools) {
        return builder.defaultTools(catalogTools, stockTools, userTools, adminTools).build();
    }
}
