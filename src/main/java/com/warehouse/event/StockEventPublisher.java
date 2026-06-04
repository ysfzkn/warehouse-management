package com.warehouse.event;

import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import com.warehouse.repository.StockEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class StockEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final StockEventRepository stockEventRepository;

    public StockEventPublisher(ApplicationEventPublisher eventPublisher,
                                StockEventRepository stockEventRepository) {
        this.eventPublisher = eventPublisher;
        this.stockEventRepository = stockEventRepository;
    }

    public void publishStockChanged(Long stockId, Long productId,
                                     StockEventType eventType,
                                     Integer oldValue, Integer newValue,
                                     StockEventSource source, String sourceDetail) {
        // Persist event to DB
        StockEvent event = new StockEvent();
        event.setStockId(stockId);
        event.setProductId(productId);
        event.setEventType(eventType);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setSource(source);
        event.setSourceDetail(sourceDetail);
        stockEventRepository.save(event);

        // Publish in-process event for cache invalidation
        eventPublisher.publishEvent(new StockChangedEvent(
            this, stockId, productId, eventType, oldValue, newValue, source, sourceDetail
        ));
    }
}
