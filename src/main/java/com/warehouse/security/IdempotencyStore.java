package com.warehouse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency-Key store: aynı key ile gelen sipariş/ödeme isteklerinde
 * önceki sonucu döner, mutation'ı tekrarlamaz.
 *
 * <p>Tipik kullanım: müşteri "Siparişi Onayla" butonuna iki kez tıklarsa,
 * client tarafı aynı key'i gönderir, backend ikincisinde yeni order yaratmak
 * yerine ilk order'ın ID'sini döner.</p>
 *
 * <p>In-memory (Caffeine), 24h TTL. Multi-instance scale'de eski kayıt
 * Redis'e taşınabilir; tek-instance Railway için yeterli.</p>
 *
 * <p>Key format kontrolü: client'tan gelen Idempotency-Key UUID formatında
 * olmalı (sabit string'lerle her isteği aynı sayma riskini kaldırır).</p>
 */
@Component
public class IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);

    /** namespace + key → response payload (Map JSON-uyumlu) */
    private final Cache<String, Map<String, Object>> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(100_000)
            .build();

    /** In-flight key'leri tutar (aynı key'le concurrent isteklere collide-and-wait). */
    private final ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>();

    /**
     * Kayıtlı sonucu döner; yoksa null.
     * @param namespace "checkout" / "payment" gibi mantıksal bölme
     */
    public Map<String, Object> get(String namespace, String key) {
        if (!isValidKey(key)) return null;
        return cache.getIfPresent(namespace + ":" + key);
    }

    /** Sonucu kaydet (24 saat boyunca tekrar isteğinde aynı sonuç döner). */
    public void put(String namespace, String key, Map<String, Object> result) {
        if (!isValidKey(key) || result == null) return;
        cache.put(namespace + ":" + key, result);
    }

    /**
     * In-flight reservation — concurrent duplicate'ları engeller.
     * Returns true if reservation acquired (caller should proceed),
     * false if another in-flight request holds it.
     */
    public boolean tryAcquire(String namespace, String key) {
        if (!isValidKey(key)) return true; // key yoksa idempotency yok, geç
        return inFlight.putIfAbsent(namespace + ":" + key, Boolean.TRUE) == null;
    }

    public void release(String namespace, String key) {
        if (!isValidKey(key)) return;
        inFlight.remove(namespace + ":" + key);
    }

    /** UUID v4 benzeri format zorunluluğu (saldırgan rastgele string ile collision yapamasın). */
    private boolean isValidKey(String key) {
        if (key == null || key.isBlank() || key.length() < 16 || key.length() > 128) return false;
        // UUID veya en az 16 karakter rastgele alphanumeric/dash
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            // UUID değilse min length kontrolü yeterli
            return key.matches("[A-Za-z0-9_\\-]{16,128}");
        }
    }
}
