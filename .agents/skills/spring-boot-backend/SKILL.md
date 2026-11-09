---
name: spring-boot-backend
description: Spring Boot katman düzeni, JPA, işlem yönetimi, Flyway migration, dış servis entegrasyonu ve REST uç tasarımı kuralları. Controller, Service, Repository, Entity, migration ya da dış API istemcisi yazarken kullan.
---

# Spring Boot backend kuralları

Spring Boot 3 / Java 21 / PostgreSQL / Flyway. Aşağıdakiler bu projede yaşanmış sorunlardan çıktı.

## Katmanlar

```
Controller  → HTTP. Doğrulama, yetki, DTO dönüşümü. İş kuralı YOK.
Service     → İş kuralı. İşlem sınırı burada.
Repository  → Sorgu. Mantık YOK.
Entity      → Veri + değişmezlikler.
```

- Controller entity döndürmez, DTO döndürür. Entity döndürmek iç şemayı API sözleşmesine
  dönüştürür ve lazy alanlar serileştirmede patlar.
- Service controller çağırmaz, `HttpServletRequest` görmez.
- İki service birbirine bağımlıysa döngü var demektir; ortak parçayı üçüncü bir sınıfa al.

## Migration (Flyway)

**Sürüm numarası çakışması uygulamayı hiç başlatmaz.** Flyway taramada durur, hiçbir migration
uygulanmaz, healthcheck sebepsiz düşer. Yeni migration eklerken:

```bash
ls src/main/resources/db/migration/ | sed -n 's/^V\([0-9]*\)__.*/\1/p' | sort -n | tail -1
```

En yüksek numaranın bir üstünü al. `CodingStandardsTest` ve `FlywayMigrationVersionTest` bunu
kontrol ediyor ama numarayı baştan doğru seçmek daha iyi.

Diğer kurallar:
- Migration **değiştirilmez**. Uygulanmış bir dosyanın içeriğini düzenlemek checksum hatası verir.
  Düzeltme yeni migration ile yapılır.
- Test profilinde `spring.flyway.enabled=false`, şema entity'lerden üretiliyor. Yani **hiçbir test
  migration'ları çalıştırmaz.** SQL'in doğruluğunu test suite'i doğrulamaz.
- Prod `ddl-auto=validate`. Entity'ye alan eklerken migration'ı aynı commit'te ekle; yoksa
  uygulama açılmaz.
- Postgres sözdizimi kullan: `BIGSERIAL`, `TEXT`, `ADD COLUMN IF NOT EXISTS`, kısmi index `WHERE`,
  `ON CONFLICT`. `AUTO_INCREMENT`, `ENGINE=`, backtick MySQL'dir.
- Yeni ayar eklerken migration ve `SettingKeys` sabitini **birlikte** ekle.

## İşlem yönetimi

```java
// YANLIŞ — işlem, kargo firması cevap verene kadar açık kalır
@Transactional
public void ship(Order order) {
    orderRepository.save(order);
    cargoProvider.createShipment(...);   // saniyelerce sürebilir
}
```

- `@Transactional` metodun içinde HTTP çağrısı yapma. Bağlantı havuzu tükenir.
- Dış çağrı gerekiyorsa: işlemde veriyi yaz, işlem dışında çağır, sonucu ayrı işlemde işle.
  Kalıcılık gerekiyorsa **Outbox** kullan (`CargoShipmentOutbox` örneği).
- Salt okunur işlemlerde `@Transactional(readOnly = true)`.
- Aynı sınıf içinden `@Transactional` metot çağırmak proxy'yi atlar, işlem açılmaz.

## JPA

- Liste çekerken ilişkili veri gerekiyorsa `LEFT JOIN FETCH` — yoksa N+1.
- `spring.jpa.open-in-view=false` (zaten kapalı). Lazy alan service dışında açılamaz, DTO'ya
  service içinde çevir.
- Tarih aritmetiğini JPQL'de yapma. `FUNCTION('DATEDIFF', ...)` H2 ve Postgres'te farklı imzaya
  sahip; sorguyu sade tut, hesabı Java'da yap.
- Sayfalama gereken yerde `Pageable` kullan — `findAll()` çekip Java'da filtrelemek tablo
  büyüdükçe çöker.

## Dış servis entegrasyonu

Bu projenin en çok hata verdiği yer burası.

- **Accept başlığını gönder.** Laravel tabanlı API'ler `Accept: application/json` yoksa hata
  durumunda JSON değil HTML yönlendirme döner; istek alakasız bir sayfaya düşmüş gibi görünür.
- **Kimlik bilgisini `trim()` ile gönder** ve bunu her istemcide aynı şekilde yap. Bir serviste
  kırpıp diğerinde kırpmamak, aynı token'la bir çağrının çalışıp diğerinin 401 almasına yol açar
  — dışarıdan "karşı taraf yarı çalışıyor" gibi görünür.
- **Boş cevabı önbelleğe alma.** Boş liste bir yanıt değil, hatadır. TTL boyunca saklanırsa sorun
  çözüldükten sonra bile sistem bozuk kalır ve tazelemenin yolu olmaz.
- **Zaman aşımı ver.** `new RestTemplate()` sonsuz bekler.
- Hata sınıflandırması: 401/403 ayrı, diğer HTTP hataları ayrı, ağ hatası ayrı. Hepsini tek
  `catch (Exception)` içinde toplamak teşhisi imkânsız kılar.
- Yanıt gövdesini hata mesajına koy — karşı taraf hangi alanı beğenmediğini orada söyler.

## REST uç tasarımı

- Yol, kaynağı adlandırır: `/api/admin/cargo/webhooks/{id}`.
- Yönü karıştırma: `/api/public/**` **dışarıdan bize** gelen çağrılar (webhook, callback),
  `/api/admin/**` panelden **bize** gelen çağrılar. Bir admin işlemini public sanmak hem CORS
  hem yetki kuralını yanlış kurar.
- `success: false` dönüyorsan **sebebi de dön**. Sunucu logunda kalan sebep yönetici için yoktur.
- Durum kodu anlamlı olsun: doğrulama 400/422, yetki 401/403, bulunamadı 404.
- Hata gövdesi `WarehouseManagementException` + `ErrorCode` üzerinden tek biçimde dönsün.

## Güvenlik kuralı yazarken

- URL kuralı (`requestMatchers`) ile metot güvenliği (`@PreAuthorize`) **birbirini tekrarlamalı**,
  biri diğerine güvenmemeli. `permitAll()` yazıp "zaten @PreAuthorize var" demek, o anotasyon
  silindiğinde ucu internete açar.
- `permitAll()` yalnızca gerçekten kimliksiz çağrılan yollara. Yorumda "HMAC ile doğrulanıyor"
  yazıyorsa, o yolda gerçekten HMAC doğrulaması olduğunu kontrol et.
- Metot bazlı izin verirken (`HttpMethod.POST`) diğer metotların ne olacağını düşün — `denyAll()`
  altına düşen bir GET, dışarıdaki erişilebilirlik denetimini 403 ile kırar.

## Ayarlar

- Anahtar `SettingKeys` sabitinden gelir, elle yazılmaz.
- Gizli ayarlar (`*_token`, `*_secret`, `*_key`, `*_password`) public uçtan **sızmamalı**;
  `SiteSettingServiceImpl` segment bazlı denylist ile bunu yapıyor, yeni anahtar eklerken
  adlandırmanın o listeye takıldığını doğrula.

## Zamanlanmış işler

- `@Scheduled` + ShedLock. Kilitsiz iş çok örnekli ortamda aynı anda iki kez çalışır.
- İş içinde `@Transactional` kullanma; her kayıt için ayrı kısa işlem aç, biri patlayınca hepsi
  geri alınmasın.
