# Log Observability — Grafana Loki + Promtail

Production loglarını merkezi olarak toplamak, filtrelemek ve hata izlemek için
**ücretsiz + self-hosted** stack. Tüm log volume'ünüzü tek bir Grafana ekranından
görüp arama yapabilirsiniz.

---

## Niye Bu Stack?

| Alternatif | Maliyet | Bizim için neden uygun değil |
|---|---|---|
| **Grafana Cloud (managed)** | Free 50GB/ay | Hız sınırlı, paid >50GB |
| **Datadog** | $0.10/GB ingest + retention | Pahalı, küçük projeler için fazla |
| **New Relic** | Free 100GB/ay | Vendor lock-in |
| **ELK (Elasticsearch)** | Self-hosted, ücretsiz | RAM'i şişirir (>4GB) |
| **Better Stack / Logtail** | Free 1GB/day | Yetersiz |
| **Loki + Promtail + Grafana ✅** | **0 $** | Düşük resource, S3 backend desteği var |

**Loki ne yapıyor?** Log satırlarını **label'larla** index'liyor (PostgreSQL gibi
full-text değil — bu yüzden çok hızlı + ucuz). Log içeriği gzip'lenmiş chunks
olarak local disk veya S3'te saklanır.

---

## 1. Kurulum (60 saniye)

```bash
# Tüm observability stack'i başlat
docker compose up -d loki promtail grafana

# Servisler hazır mı?
docker compose ps loki promtail grafana
```

Beklenen:
```
loki        running    0.0.0.0:3100->3100/tcp
promtail    running
grafana     running    0.0.0.0:3001->3000/tcp
```

---

## 2. İlk Açılış

1. **Grafana açar:** http://localhost:3001
2. **Login:** `admin` / `admin` (ilk girişte değiştir)
3. **Sol menü → Dashboards → Warehouse → "Live Logs"** dashboard'u açılır
   - Error rate (son 5dk)
   - Warning rate
   - Level bazlı log volume grafiği
   - Recent ERRORS panel'i (canlı tail)
   - All logs (live tail)

**Loki datasource otomatik tanımlı** (provisioning ile). Manuel kurulum yok.

---

## 3. JSON Structured Logging — Aktivasyon

Backend prod profile'inde **otomatik JSON output** verir (logback-spring.xml):

```json
{
  "@timestamp": "2026-05-15T10:30:00.123Z",
  "level": "ERROR",
  "logger_name": "com.warehouse.service.PaymentService",
  "thread_name": "http-nio-8080-exec-3",
  "message": "Payment failed: orderId=42",
  "service": "warehouse-backend",
  "requestId": "req-abc123",
  "remoteIp": "1.2.3.4",
  "userId": "12",
  "stack_trace": "java.lang.NullPointerException..."
}
```

**Aktivasyon:**
```bash
# Spring Boot'u prod profile ile başlat
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run

# Veya Docker'da
docker compose run -e SPRING_PROFILES_ACTIVE=prod backend
```

Dev profile'de plain text loglar yazılır (geliştirici dostu); prod profile'de
JSON (Loki için).

---

## 4. LogQL Sorguları (Grafana Explore)

### 4.1 Tüm error'lar
```logql
{service="warehouse-backend", level="ERROR"}
```

### 4.2 Belirli bir kullanıcının tüm istekleri (audit)
```logql
{service="warehouse-backend"} | json | userId = "42"
```

### 4.3 Tek bir request'in lifecycle'ı (correlation)
```logql
{service="warehouse-backend"} | json | requestId = "req-abc123"
```

### 4.4 Belirli bir endpoint'te hata
```logql
{service="warehouse-backend", level="ERROR"}
  | json
  | logger_name =~ "com.warehouse.service.payment.*"
```

### 4.5 Hata rate (alert için)
```logql
sum(rate({service="warehouse-backend", level="ERROR"}[5m]))
```

### 4.6 PII redaction çalışıyor mu? (saldırı tespiti)
```logql
{service="warehouse-backend"}
  |~ "(?i)(password|cvv|card_number|pan).*[\"'][^\"']+[\"']"
```
Sonuç olarak DOLU dönerse → bir yerde PII leak var, koda bak.

---

## 5. Alerting (Slack / Email)

Grafana'da:
1. **Alerting → Alert Rules → New Alert**
2. Query: `sum(rate({service="warehouse-backend", level="ERROR"}[5m])) > 0.5`
3. Folder: Warehouse
4. Notification: **Contact point** ekle (Slack webhook / SMTP)

Önerilen alarm kuralları:
| Alarm | Eşik | Severity |
|---|---|---|
| Backend error rate | > 0.5/sec son 5dk | Critical |
| Payment gateway hata | son 5dk içinde 3+ adet | High |
| PII leak detected | herhangi bir match | Critical (immediate) |
| MockInvoiceProvider in prod | 1+ match | Critical |
| HMAC verification FAILED | 1+ match | Critical (saldırı) |

---

## 6. Request Correlation (Trace)

Her HTTP isteği bir `requestId` alır (X-Request-Id header):
- `RequestIdFilter` (highest precedence) MDC'ye yazar
- Logback JSON encoder her log satırına dahil eder
- Response header'da geri yansır (debug: müşteri "şu hata mesajı geldi" derse,
  o ID ile log filter çalıştırılır)

Grafana'da:
1. Bir error log satırına tıkla
2. JSON detay → `requestId` value'sunu kopyala
3. Yeni query: `{service="warehouse-backend"} | json | requestId="..."`
4. O istek için **tüm** log satırlarını görürsün (filter → controller → service → DB)

---

## 7. Production'da Storage

Loki default olarak local diske yazıyor. Production'da:

### 7.1 S3 backend (en ücretsiz pratik)
Loki config'i (docker-compose.yml override):
```yaml
loki:
  command: -config.file=/etc/loki/s3-config.yaml
  volumes:
    - ./docs/observability/loki-s3-config.yml:/etc/loki/s3-config.yaml:ro
```
S3 config örnek:
```yaml
schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: s3
      schema: v13
storage_config:
  aws:
    s3: s3://access:secret@s3.amazonaws.com/loki-chunks
    region: eu-central-1
```

### 7.2 Retention
```yaml
limits_config:
  retention_period: 720h   # 30 gün
```

---

## 8. Resource Footprint

| Component | RAM | CPU | Disk |
|---|---|---|---|
| Loki | <100 MB idle | <2% | 1GB / 10M log lines |
| Promtail | <50 MB | <1% | yok |
| Grafana | ~150 MB | <2% | <100 MB |
| **Total** | **~300 MB** | **<5%** | scale to logs |

Bu yüzden Railway $5/ay plan'da bile yetiyor. Toplam +$5/ay maliyet, prod'a değer.

---

## 9. Cheat Sheet

```bash
# Loglara canlı tail (CLI)
docker compose logs -f backend

# Loki'de retention dolduğunda eski chunks sil
docker compose exec loki sh -c "find /loki -mtime +30 -delete"

# Grafana yedek
docker compose exec grafana grafana-cli admin export-dashboard

# Promtail config reload
docker compose restart promtail
```

---

## 10. Production-Ready Check

- [ ] `SPRING_PROFILES_ACTIVE=prod` set (JSON output için)
- [ ] Grafana admin şifresi değiştirildi (`GF_SECURITY_ADMIN_PASSWORD`)
- [ ] Loki S3 backend kuruldu (chunks kayıp olmasın)
- [ ] Retention policy 30+ gün
- [ ] Alert'lar Slack/Email'e bağlandı
- [ ] Dashboards backup edildi (`grafana-cli`)
- [ ] PII redaction patternleri test edildi (4.6 query)
- [ ] Request correlation çalışıyor (X-Request-Id header görünür)
