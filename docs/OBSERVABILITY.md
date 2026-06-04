# Observability & Reliability Guide

Bu doküman lansman sonrası rasyonel ölçeklenme için planı içerir.

---

## 1. Status Page & Uptime Monitoring

### Uptime Monitoring (ücretsiz/düşük maliyetli)

| Sağlayıcı | Free Tier | Probe Aralık | Status Page |
|-----------|-----------|--------------|-------------|
| **UptimeRobot** | 50 monitor / 5dk aralık | 5dk | Ücretsiz status page subdomain |
| **BetterStack** | 10 monitor / 30sn | 30sn | Status page modern UI |
| **Instatus** | 25 monitor / 60sn | 60sn | En iyi UX |
| **Statuspage.io** (Atlassian) | ~$29/ay | 30sn | Enterprise |

**Önerilen:** UptimeRobot + Instatus combo (her ikisi de free tier yeterli).

### Probe URL'leri
```
https://siteniz.com/                                # Root sayfa
https://api.siteniz.com/actuator/health             # Backend health
https://wms.siteniz.com/                            # WMS subdomain
https://siteniz.com/sitemap.xml                     # SEO + DB connectivity
```

### Alert Eşikleri
- **Critical:** 2 dakika içinde 2 başarısız probe → SMS + email
- **Warning:** Response time > 3s → email
- **Info:** SSL sertifika < 7 gün kalmış → email

### Status Page Setup
```
1. UptimeRobot dashboard → Status Pages → Create
2. status.siteniz.com → DNS CNAME stats.uptimerobot.com
3. Monitor'leri seç → public visibility
4. Branding: logo, primary color, contact info
5. Incident template'leri hazır:
   - "Bakım nedeniyle X dakikalık planlı kesinti"
   - "Ödeme sağlayıcımız Iyzico'da yaşanan sorun..."
```

---

## 2. OpenTelemetry / Distributed Tracing

### Mevcut Durum

Logback'te `[%X{traceId:-}]` placeholder'ı **eklenmedi** (MDC filter yok). Faz 1
HIGH'da request-id filter planı vardı ama henüz uygulanmadı. OpenTelemetry
otomatik trace injection sağlar.

### Kurulum (deploy time)

```bash
# Backend OTEL agent (auto-instrumentation, kod değişikliği yok)
# Java agent jar'ı indir:
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Dockerfile'a ekle:
COPY opentelemetry-javaagent.jar /opt/otel/javaagent.jar

# Spring Boot startup:
JAVA_OPTS="-javaagent:/opt/otel/javaagent.jar \
           -Dotel.service.name=warehouse-backend \
           -Dotel.exporter.otlp.endpoint=$OTEL_ENDPOINT \
           -Dotel.exporter.otlp.headers=Authorization=Bearer\ $OTEL_TOKEN"
```

### Backend (free) Seçenekleri

| Backend | Tier | Limit |
|---------|------|-------|
| **Grafana Cloud** | Free | 14gün retention, 50GB trace |
| **Tempo** (self-hosted) | sıfır | kendi alt yapı |
| **Jaeger** (Railway addon) | düşük | ~$5/ay |
| **Honeycomb** | Free | 20M events/ay |
| **New Relic** | Free | 100GB/ay |

**Önerilen başlangıç:** Grafana Cloud free (logs+metrics+traces tek panelde).

### Frontend Tracing

```js
// frontend/src/index.js'e eklenecek (deploy zamanında)
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web';
// Init + OTLP exporter; user session span'lerini backend trace'ine bağlar.
```

### Properties placeholder (application.properties)

```properties
otel.enabled=${OTEL_ENABLED:false}
otel.service-name=${OTEL_SERVICE_NAME:warehouse-backend}
otel.endpoint=${OTEL_ENDPOINT:}
otel.token=${OTEL_TOKEN:}
otel.sampling-rate=${OTEL_SAMPLING:0.1}
```

---

## 3. Database Read Replica

### Mevcut Durum

Railway Postgres tek instance. Yazma + okuma aynı bağlantıda.

### Ne Zaman Replica Ekleyeceğiz?

- Read query'leri DB CPU'sunun %50'sini aşıyorsa
- Reporting/analytics sorguları transactional yükü etkiliyorsa
- 99.99% uptime gerektiğinde (replica = read failover)

### Strateji

```
Primary (R/W)
   ├── Logical replication
   └── Replica (read-only)
        ├── /api/store/products/*          → replica
        ├── /api/store/categories/*        → replica
        ├── /api/admin/analytics/*         → replica
        ├── /actuator/health DB ping       → primary (write-side)
        └── All writes (orders, payments)  → primary
```

### Spring Implementation Skeleton

```java
@Configuration
public class DataSourceRoutingConfig {
    @Bean
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    public DataSource replicaDataSource() { return DataSourceBuilder.create().build(); }

    @Bean
    @Primary
    public DataSource routingDataSource(...) {
        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager
                        .isCurrentTransactionReadOnly() ? "replica" : "primary";
            }
        };
        // map "primary" -> primaryDataSource, "replica" -> replicaDataSource
        return routing;
    }
}
```

### Migration Checklist

- [ ] Replica DB instance oluştur (Railway → Postgres → Add Replica)
- [ ] Bağlantı URL'ini al
- [ ] `spring.datasource.replica.url` env var olarak ekle
- [ ] Read-only sorgu metodlarına `@Transactional(readOnly = true)` ekle
  (zaten çoğu read repository'de var)
- [ ] Yük testi: replica trafik dağılımını k6 / Locust ile validate
- [ ] Failover testi: primary down → replica promote → app reconnect

### Replication Lag Monitoring

```sql
-- Primary'de
SELECT now() - pg_last_xact_replay_timestamp() AS lag;
-- 5 saniyenin üstüne çıkarsa alert
```

---

## 4. Log Aggregation

### Mevcut

Stdout logs → Railway dashboard. Search yok, alert yok.

### Önerilen

| Sağlayıcı | Free | Retention |
|-----------|------|-----------|
| **Grafana Loki Cloud** | 50 GB | 14gün |
| **Logtail (BetterStack)** | 5GB | 3 gün |
| **Papertrail** | 50MB/day | 7 gün |

Railway addon: Logtail tek tıkla bağlanır → tüm stdout otomatik forward.

---

## 5. Application Performance Monitoring (APM)

### Datadog APM (Free trial, ücretli)
Auto-instrumented Spring Boot — DB query'leri, external calls, slow endpoints.

### Self-hosted Alternatif
- **Grafana** (dashboards)
- **Prometheus** (metrics, `actuator/prometheus` endpoint ekstresi)
- **Loki** (logs)
- **Tempo** (traces)

### Hızlı Başlangıç

```bash
# Railway add-on: Grafana stack
# Otomatik backend metric'lerini Prometheus'a push, traces Tempo'ya
```

---

## 6. Alert Routing

```
Critical → SMS (Twilio) + PagerDuty
Warning  → Email + Slack #ops
Info     → Slack #monitoring
```

PagerDuty free tier 5 kullanıcı + temel rotasyon.

---

## 7. SLA Hedefleri

| Metric | Hedef | Mevcut |
|--------|-------|--------|
| Uptime | 99.5% | ? (probe et) |
| p95 latency (homepage) | < 1.5s | ? |
| p95 latency (PDP) | < 1.0s | ? |
| p95 latency (checkout) | < 2.0s | ? |
| Error rate (5xx) | < 0.5% | ? |

---

## 8. İlgili Dosyalar

- `src/main/resources/logback-spring.xml` — PII redaction + log format
- `src/main/resources/application-prod.properties` — `management.endpoints.web.exposure.include`
- `src/main/java/com/warehouse/security/SecurityConfig.java` — `/actuator/**` ADMIN-only
- `docs/RUNBOOK.md` — incident response
- `.github/workflows/railway-deploy.yml` — health probe post-deploy

---

## 9. Lansman Sonrası 30-Gün Plan

| Hafta | Hedef |
|-------|-------|
| 1 | UptimeRobot + 4 monitor + status page kur |
| 2 | Grafana Cloud connect (free) + tüm logs/metrics |
| 3 | OpenTelemetry agent + Tempo traces |
| 4 | SLO dashboard'u: error budget tracking, alert tuning |
