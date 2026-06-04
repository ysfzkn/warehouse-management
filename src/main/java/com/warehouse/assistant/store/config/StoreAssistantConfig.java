package com.warehouse.assistant.store.config;

import com.warehouse.assistant.core.config.DynamicChatModelProvider;
import com.warehouse.assistant.store.tools.StoreFaqSearchTool;
import com.warehouse.assistant.store.tools.StoreOrderTrackingTool;
import com.warehouse.assistant.store.tools.StorePriceInstallmentTool;
import com.warehouse.assistant.store.tools.StoreProductCompareTool;
import com.warehouse.assistant.store.tools.StoreProductSearchTool;
import com.warehouse.assistant.store.tools.StoreReturnPolicyTool;
import com.warehouse.assistant.store.tools.StoreStockCheckTool;
import com.warehouse.assistant.store.tools.StoreVectorSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Store chat-client provider backed by {@link DynamicChatModelProvider}.
 * Rebuilds the ChatClient transparently when the admin swaps provider/endpoint.
 */
@Component
@Profile("!test")
public class StoreAssistantConfig {

    private final DynamicChatModelProvider chatModelProvider;
    private final StoreProductSearchTool productSearchTool;
    private final StoreVectorSearchTool vectorSearchTool;
    private final StoreProductCompareTool compareTool;
    private final StoreStockCheckTool stockCheckTool;
    private final StorePriceInstallmentTool priceTool;
    private final StoreOrderTrackingTool orderTool;
    private final StoreFaqSearchTool faqTool;
    private final StoreReturnPolicyTool returnPolicyTool;

    private volatile ChatClient client;
    private volatile ChatModel lastModel;

    public StoreAssistantConfig(DynamicChatModelProvider chatModelProvider,
                                StoreProductSearchTool productSearchTool,
                                StoreVectorSearchTool vectorSearchTool,
                                StoreProductCompareTool compareTool,
                                StoreStockCheckTool stockCheckTool,
                                StorePriceInstallmentTool priceTool,
                                StoreOrderTrackingTool orderTool,
                                StoreFaqSearchTool faqTool,
                                StoreReturnPolicyTool returnPolicyTool) {
        this.chatModelProvider = chatModelProvider;
        this.productSearchTool = productSearchTool;
        this.vectorSearchTool = vectorSearchTool;
        this.compareTool = compareTool;
        this.stockCheckTool = stockCheckTool;
        this.priceTool = priceTool;
        this.orderTool = orderTool;
        this.faqTool = faqTool;
        this.returnPolicyTool = returnPolicyTool;
    }

    public ChatClient client() {
        ensureFresh();
        return client;
    }

    private synchronized void ensureFresh() {
        ChatModel currentModel = chatModelProvider.current();
        if (currentModel == null) {
            this.client = null;
            this.lastModel = null;
            return;
        }
        if (currentModel == lastModel && client != null) return;
        this.client = ChatClient.builder(currentModel)
                .defaultTools(productSearchTool, vectorSearchTool, compareTool, stockCheckTool,
                        priceTool, orderTool, faqTool, returnPolicyTool)
                .build();
        this.lastModel = currentModel;
    }
}
