package com.warehouse.assistant.wms.config;

import com.warehouse.assistant.core.config.DynamicChatModelProvider;
import com.warehouse.assistant.wms.tools.WmsAdminTools;
import com.warehouse.assistant.wms.tools.WmsCatalogTools;
import com.warehouse.assistant.wms.tools.WmsFaqSearchTool;
import com.warehouse.assistant.wms.tools.WmsStockTools;
import com.warehouse.assistant.wms.tools.WmsUserTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * WMS chat-client provider backed by {@link DynamicChatModelProvider}.
 * <p>
 * Instead of exposing singleton @Bean ChatClients (which would lock-in the
 * chat model at boot time), this component builds and caches ChatClients
 * against the current dynamic ChatModel. When the admin switches
 * providers/endpoints via the UI, the cached clients are transparently
 * rebuilt on the next request.
 */
@Component
@Profile("!test")
public class WmsAssistantConfig {

    private final DynamicChatModelProvider chatModelProvider;
    private final WmsCatalogTools catalogTools;
    private final WmsStockTools stockTools;
    private final WmsUserTools userTools;
    private final WmsAdminTools adminTools;
    private final WmsFaqSearchTool faqSearchTool;

    private volatile ChatClient userClient;
    private volatile ChatClient adminClient;
    private volatile ChatModel lastModel;

    public WmsAssistantConfig(DynamicChatModelProvider chatModelProvider,
                              WmsCatalogTools catalogTools,
                              WmsStockTools stockTools,
                              WmsUserTools userTools,
                              WmsAdminTools adminTools,
                              WmsFaqSearchTool faqSearchTool) {
        this.chatModelProvider = chatModelProvider;
        this.catalogTools = catalogTools;
        this.stockTools = stockTools;
        this.userTools = userTools;
        this.adminTools = adminTools;
        this.faqSearchTool = faqSearchTool;
    }

    public ChatClient userClient() {
        ensureFresh();
        return userClient;
    }

    public ChatClient adminClient() {
        ensureFresh();
        return adminClient;
    }

    private synchronized void ensureFresh() {
        ChatModel currentModel = chatModelProvider.current();
        if (currentModel == null) {
            this.userClient = null;
            this.adminClient = null;
            this.lastModel = null;
            return;
        }
        if (currentModel == lastModel && userClient != null && adminClient != null) return;
        this.userClient = ChatClient.builder(currentModel)
                .defaultTools(catalogTools, stockTools, userTools, faqSearchTool)
                .build();
        this.adminClient = ChatClient.builder(currentModel)
                .defaultTools(catalogTools, stockTools, userTools, adminTools, faqSearchTool)
                .build();
        this.lastModel = currentModel;
    }
}
