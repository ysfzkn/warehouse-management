---
name: testing-discipline
description: Test yazma ve doğrulama kuralları — testin hatayı gerçekten yakaladığını kanıtlama, test profilinin kör noktaları, isimlendirme ve neyin test edilmeyeceği. Test yazarken, düzeltme yaptıktan sonra ya da bir hatanın neden yakalanmadığını araştırırken kullan.
---

# Test disiplini

571 test yeşilken üretim açılmadı. Test sayısı bir şey garanti etmez; **neyi** test ettiği eder.

## Testin hatayı yakaladığını kanıtla

Yeni bir test yazdıysan, düzeltmeyi geçici olarak geri al ve testin **kırmızıya döndüğünü gör**.
Geçen bir test, hatayı yakalayabildiği için mi geçiyor yoksa hiç bakmadığı için mi — tek ayırt
etme yolu budur.

```bash
# düzeltmeyi geri al, testi çalıştır, kırmızı olduğunu gör, düzeltmeyi geri koy
```

Çıktının hatayı adıyla söylemesi gerekir:

```
Expecting empty but was: ["V100 → V100__duplicate_probe.sql, V100__receipt_carrier_backfill.sql"]
```

"AssertionError: expected true" diyen bir test, kırıldığında kimseye yardım etmez.

## Test profilinin kör noktaları

Bunları test suite'i **hiç çalıştırmaz**. Buralarda değişiklik yaptıysan doğrulaman farklı olmalı.

| Kör nokta | Sebep | Ne yapmalı |
|---|---|---|
| Flyway migration'ları | `spring.flyway.enabled=false`, şema entity'lerden üretiliyor | Sürüm çakışması ve SQL sözdizimini ayrıca kontrol et |
| nginx yönlendirmesi | Uygulamanın dışında | Yapılandırmayı okuyan test yaz |
| `@Profile("!test")` sınıfları | Test bağlamına hiç kaydedilmez | Doğrudan örnekleyip birim test et |
| `ddl-auto=validate` uyumu | Testte `create-drop` | Entity alanı eklerken migration'ı aynı commit'te ekle |

Dosya okuyan test yazmak "hile" değil — uygulamanın dışındaki bir sözleşmeyi tutan tek yol o.
`FlywayMigrationVersionTest`, `PublicApiRoutingTest`, `CodingStandardsTest` böyle çalışıyor.

## Ne test edilir

**Edilir:** davranış, sınır durumu, hata yolu, geri dönüş (regression), değişmez (invariant).

**Edilmez:** getter/setter, framework'ün kendi işi, üçüncü parti kütüphane, mock'un mock
olduğunu doğrulayan test.

Bir hata bulduysan önce onu gösteren testi yaz. O test olmadan düzeltme "çalışıyor gibi görünen"
bir değişikliktir.

## İsimlendirme ve anlatım

Test adı ne olması gerektiğini söyler, metodun adını tekrarlamaz:

```java
// YANLIŞ
void testRegisterWebhook()

// DOĞRU
void aFailureCarriesItsReason()
void theInboundWebhookStaysClosedToBrowsers()
```

Sınıf Javadoc'u **neden** bu testlerin var olduğunu anlatır — hangi hata yaşandı, neyi kaçırdık.
Bu, testi silmek isteyen bir sonraki kişinin duracağı yerdir.

`@DisplayName` Türkçe olabilir; panel çıktısında okunur olması işe yarar.

## Assertion

- AssertJ kullan: `assertThat(x).isEqualTo(y)`.
- `as("...")` ile başarısızlık mesajına bağlam ekle — özellikle liste karşılaştırmalarında.
- Bir testte bir davranış doğrula. Beş assertion aynı davranışın parçalarıysa sorun yok;
  beş farklı şeyse beş test.
- Sayı değil anlam kontrol et: `hasSize(2)` yerine gerektiğinde içeriği de doğrula.

## Mock

- Sahte nesne dış sınırlar için: HTTP istemcisi, repository, saat.
- Kendi kodunu mock'lama — mock'ladığın şey testin konusuysa test bir şey ölçmüyordur.
- `verifyNoInteractions(...)` bir şeyin **yapılmadığını** kanıtlamak için güçlüdür: sondanın
  hiçbir ayara bakmadığını böyle gösteriyoruz, dolayısıyla hiçbir şey sızdıramaz.
- `@MockitoSettings(strictness = LENIENT)` yalnızca gerçekten gerekliyse; gereksiz stub'ları
  gizler.

## Eşzamanlı çalışma

Bu depoda başka oturumlar da olabilir. Test çalıştırmadan önce `git status` ile kimin neyi
değiştirdiğine bak. Başkasının yarım işinden gelen kırmızı testi kendi değişikliğin sanma —
ama düzeltmeye de kalkışma, haber ver.

## Kontrol listesi

- [ ] Testi yazdım ve düzeltmeyi geri alıp kırmızı olduğunu gördüm
- [ ] Başarısızlık mesajı sorunu adıyla söylüyor
- [ ] Kör noktadaysa (migration, nginx, `@Profile("!test")`) uygun yöntemi kullandım
- [ ] Sınıf Javadoc'u hangi hatayı önlediğini anlatıyor
- [ ] Tüm suite yeşil, kırmızı varsa kimin olduğunu biliyorum
