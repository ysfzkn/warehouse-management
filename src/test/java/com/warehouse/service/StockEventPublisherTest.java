package com.warehouse.service;

import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventSource;
import com.warehouse.enums.StockEventType;
import com.warehouse.event.StockChangedEvent;
import com.warehouse.event.StockEventPublisher;
import com.warehouse.repository.StockEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockEventPublisherTest {

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StockEventRepository stockEventRepo;

    @InjectMocks private StockEventPublisher publisher;

    @Test
    void should_persist_event_and_publish() {
        publisher.publishStockChanged(1L, 10L, StockEventType.QUANTITY_CHANGED, 5, 3, StockEventSource.ADMIN, "manual");

        verify(stockEventRepo).save(any(StockEvent.class));
        verify(eventPublisher).publishEvent(any(StockChangedEvent.class));
    }

    @Test
    void should_publish_with_correct_event_type() {
        publisher.publishStockChanged(2L, 20L, StockEventType.RESERVED, 0, 5, StockEventSource.ORDER, "checkout");

        ArgumentCaptor<StockEvent> captor = ArgumentCaptor.forClass(StockEvent.class);
        verify(stockEventRepo).save(captor.capture());

        StockEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(StockEventType.RESERVED);
        assertThat(saved.getSource()).isEqualTo(StockEventSource.ORDER);
        assertThat(saved.getOldValue()).isEqualTo(0);
        assertThat(saved.getNewValue()).isEqualTo(5);
    }
}
