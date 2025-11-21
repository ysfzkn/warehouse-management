package com.warehouse.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.repository.NotificationRepository;
import com.warehouse.service.StockService;
import com.warehouse.service.SsePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Default implementation of {@link SsePushService} that keeps a set of emitters
 * and broadcasts JSON snapshots containing unread notification count and low stock count.
 */
@Service
public class SsePushServiceImpl implements SsePushService {

    private static final Logger logger = LoggerFactory.getLogger(SsePushServiceImpl.class);

    private final long connectionTimeoutMs;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final NotificationRepository notificationRepository;
    private final StockService stockService;
    private final ObjectMapper objectMapper;

    public SsePushServiceImpl(NotificationRepository notificationRepository,
                              @Lazy StockService stockService,
                              ObjectMapper objectMapper,
                              @Value("${app.sse.timeout-ms:3600000}") long connectionTimeoutMs) {
        this.notificationRepository = notificationRepository;
        this.stockService = stockService;
        this.objectMapper = objectMapper;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(connectionTimeoutMs);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        // Send initial snapshot
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(buildSnapshotJson()));
        } catch (IOException e) {
            logger.debug("Failed to send initial snapshot, removing emitter", e);
            emitters.remove(emitter);
            emitter.complete();
        }
        return emitter;
    }

    @Override
    public void broadcastCounts() {
        String payload = buildSnapshotJson();
        Iterator<SseEmitter> iterator = emitters.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            try {
                emitter.send(SseEmitter.event().name("update").data(payload));
            } catch (IOException e) {
                logger.debug("Emitter send failed, removing", e);
                iterator.remove();
                emitter.complete();
            }
        }
    }

    private String buildSnapshotJson() {
        try {
            int unread = (int) notificationRepository.countByReadFalse();
            long lowStock = stockService.countLowStockItems();
            return objectMapper.writeValueAsString(new Snapshot(unread, lowStock));
        } catch (Exception e) {
            logger.warn("Failed to build snapshot, sending empty counts", e);
            try {
                return objectMapper.writeValueAsString(new Snapshot(0, 0));
            } catch (Exception ignored) {
                return "{\"unread\":0,\"lowStock\":0}";
            }
        }
    }

    private record Snapshot(int unread, long lowStock) {}
}


