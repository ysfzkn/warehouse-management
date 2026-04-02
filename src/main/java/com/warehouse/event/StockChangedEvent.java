package com.warehouse.event;

import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import org.springframework.context.ApplicationEvent;

public class StockChangedEvent extends ApplicationEvent {

    private final Long stockId;
    private final Long productId;
    private final StockEventType eventType;
    private final Integer oldValue;
    private final Integer newValue;
    private final StockEventSource source;
    private final String sourceDetail;

    public StockChangedEvent(Object eventSource, Long stockId, Long productId,
                             StockEventType eventType, Integer oldValue, Integer newValue,
                             StockEventSource source, String sourceDetail) {
        super(eventSource);
        this.stockId = stockId;
        this.productId = productId;
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.source = source;
        this.sourceDetail = sourceDetail;
    }

    public Long getStockId() { return stockId; }
    public Long getProductId() { return productId; }
    public StockEventType getEventType() { return eventType; }
    public Integer getOldValue() { return oldValue; }
    public Integer getNewValue() { return newValue; }
    public StockEventSource getSource() { return source; }
    public String getSourceDetail() { return sourceDetail; }
}
