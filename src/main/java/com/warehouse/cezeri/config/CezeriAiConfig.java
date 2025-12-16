package com.warehouse.cezeri.config;

import com.warehouse.cezeri.tools.CezeriCatalogTools;
import com.warehouse.cezeri.tools.CezeriAdminTools;
import com.warehouse.cezeri.tools.CezeriStockTools;
import com.warehouse.cezeri.tools.CezeriUserTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;

/**
 * Central place for Cezeri AI wiring (ChatClient + tool registration).
 */
@Configuration
@Profile("!test")
public class CezeriAiConfig {

    @Bean
    public ChatClient cezeriChatClientUser(ChatClient.Builder builder,
                                           CezeriCatalogTools catalogTools,
                                           CezeriStockTools stockTools,
                                           CezeriUserTools userTools) {
        // Non-admin tool set
        return builder.defaultTools(catalogTools, stockTools, userTools).build();
    }

    @Bean
    public ChatClient cezeriChatClientAdmin(ChatClient.Builder builder,
                                            CezeriCatalogTools catalogTools,
                                            CezeriStockTools stockTools,
                                            CezeriUserTools userTools,
                                            CezeriAdminTools adminTools) {
        // Admin tool set (includes admin-only tools)
        return builder.defaultTools(catalogTools, stockTools, userTools, adminTools).build();
    }
}


