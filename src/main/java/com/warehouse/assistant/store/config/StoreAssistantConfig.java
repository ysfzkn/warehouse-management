package com.warehouse.assistant.store.config;

import com.warehouse.assistant.store.tools.StoreFaqSearchTool;
import com.warehouse.assistant.store.tools.StoreOrderTrackingTool;
import com.warehouse.assistant.store.tools.StorePriceInstallmentTool;
import com.warehouse.assistant.store.tools.StoreProductCompareTool;
import com.warehouse.assistant.store.tools.StoreProductSearchTool;
import com.warehouse.assistant.store.tools.StoreReturnPolicyTool;
import com.warehouse.assistant.store.tools.StoreStockCheckTool;
import com.warehouse.assistant.store.tools.StoreVectorSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wiring for the Store assistant profile.
 * <p>
 * A single {@code storeChatClient} bean exposes all 8 storefront tools.
 * Unlike the WMS side there is no admin/non-admin split — tool-level checks
 * (e.g. {@link StoreOrderTrackingTool} refusing guest requests) enforce
 * privilege at the finest possible grain.
 */
@Configuration
@Profile("!test")
public class StoreAssistantConfig {

    @Bean
    public ChatClient storeChatClient(ChatClient.Builder builder,
                                      StoreProductSearchTool productSearchTool,
                                      StoreVectorSearchTool vectorSearchTool,
                                      StoreProductCompareTool compareTool,
                                      StoreStockCheckTool stockCheckTool,
                                      StorePriceInstallmentTool priceTool,
                                      StoreOrderTrackingTool orderTool,
                                      StoreFaqSearchTool faqTool,
                                      StoreReturnPolicyTool returnPolicyTool) {
        return builder.defaultTools(
                productSearchTool,
                vectorSearchTool,
                compareTool,
                stockCheckTool,
                priceTool,
                orderTool,
                faqTool,
                returnPolicyTool
        ).build();
    }
}
