---
name: java-code-quality
description: Java kod yazarken, gözden geçirirken veya refactor ederken uygulanacak kurallar — Clean Code, SonarQube uyumu, Java 21 deyimleri, tasarım desenlerinin yerinde kullanımı, sabitler ve hata yönetimi. .java dosyası oluşturulurken veya düzenlenirken kullan.
---

# Java kod kalitesi

Bu projenin kuralları. Genel tavsiye değil — her madde burada gerçekten yaşanmış bir hatadan geliyor.

## 1. Sihirli string yok

Tekrarlanan ya da bir sözleşmeyi temsil eden her string sabite taşınır.

Ayar anahtarları `SettingKeys`, API yolları `ApiPaths`, kullanıcıya görünen metinler
`BusinessMessages` / `ErrorMessages` / `ValidatorMessages`, kargo sabitleri `ShippingConstants`.

```java
// YANLIŞ — yazım hatası derlenir, fırlatmaz, sessizce boş döner
settingService.getSetting("kargonomi_api_taken");

// DOĞRU
settingService.getSetting(SettingKeys.KARGONOMI_API_TOKEN);
```

**Neden bu kadar önemli:** `getSetting` bilinmeyen anahtarda istisna atmaz, boş döner. Yazım
hatası "yönetici bu ayarı hiç girmemiş" ile birebir aynı davranır. Sessiz ve teşhis edilemez.

Sınırlı sayıda değeri olan bir alan string değil **enum** olur: `OrderStatus`,
`DeliveryPlanFilter`, `CargoBalance.State`. Bir alanın geçerli değerleri belliyse string
tutmak, derleyicinin yapabileceği kontrolü çalışma zamanına ertelemektir.

## 2. Java 21 kullanıyoruz

Kullanımdan kaldırılmış API bırakma. Derleyici uyarısı veriyorsa düzelt.

| Kullanma | Kullan |
|---|---|
| `new Locale("tr","TR")` (Java 19'dan beri deprecated) | `Locales.TR` |
| `new Integer(x)`, `new Boolean(x)` | `Integer.valueOf(x)`, kutulama |
| Uzun `if/else if` zinciri | `switch` ifadesi (`->`, pattern matching) |
| `instanceof` + cast | `instanceof Type t` pattern |
| Salt veri taşıyan sınıf | `record` |
| `Collections.unmodifiableList(new ArrayList<>(x))` | `List.copyOf(x)` |

Yerel değişkende `var`, tip sağdan bariz olduğunda kullanılır; okuyucuyu tipi aramaya
zorluyorsa kullanılmaz.

## 3. İstisna yutma

En sık yaptığımız hata bu ve en pahalıya mal olan bu.

```java
// YANLIŞ — 401, DNS hatası, timeout, 404 hepsi aynı görünür
catch (Exception e) {
    log.warn("hata: {}", e.getMessage());
    return null;
}
```

Farklı sebepler **farklı** sonuç üretmeli, çünkü çareleri farklı:

```java
catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
    return CargoBalance.rejected("HTTP " + e.getStatusCode().value());   // ayar sorunu
} catch (HttpStatusCodeException e) {
    return CargoBalance.unreachable("HTTP " + e.getStatusCode().value()); // karşı taraf
} catch (Exception e) {
    return CargoBalance.unreachable(e.getClass().getSimpleName());        // ağ
}
```

Kurallar:
- `catch (Exception)` yalnızca en dışta, daha dar catch'lerden **sonra**.
- `e.getMessage()` çoğu istisnada null'dır. Log'a `e.toString()` ya da istisnayı ikinci
  argüman olarak ver: `log.warn("...", e)`.
- Boş catch bloğu yasak. Bilerek yutuyorsan sebebini yaz.
- Hata sebebi kullanıcıya ulaşmalı. Sunucu logunda kalan sebep, yönetici için yok demektir.

## 4. Boolean dönüş yerine sonuç nesnesi

`boolean` bir işlemin neden başarısız olduğunu taşıyamaz.

```java
// YANLIŞ
public boolean registerWebhook(...)            // false — neden?

// DOĞRU
public CargoWebhookRegistration registerWebhook(...)   // success + reason + payload
```

İki durumdan fazlası varsa `record` ile modelle. `CargoBalance` bunun örneği: `OK`,
`NOT_REPORTED`, `REJECTED`, `UNREACHABLE`, `UNSUPPORTED` — beşi de `null` olarak dönüyordu
ve "para yok" ile "soramadım" birbirine karışıyordu.

## 5. Null yerine anlam

- Koleksiyon döndüren metot asla `null` dönmez: `List.of()`.
- "Değer yok olabilir" bir dönüş tipiyse `Optional<T>`. Alanda ve parametrede `Optional`
  kullanma.
- Parametre null olamıyorsa başta doğrula, sessizce devam etme.

## 6. Tasarım desenleri — yerinde

Desen, tekrar eden bir sorunu çözdüğü için kullanılır; kod "kurumsal" görünsün diye değil.

Bu projede karşılığı olanlar:
- **Strategy** — `CargoApiProvider` (Kargonomi / Mock), `InvoiceProvider`. Yeni sağlayıcı
  eklemek mevcut kodu değiştirmeden mümkün.
- **Outbox** — `CargoShipmentOutbox`. Dış çağrı başarısız olduğunda işi kaybetmemek için.
- **Template Method** — ortak akış üst sınıfta, değişen adım altta.

Kullanma:
- Tek gerçeklemesi olan ve olmayacak arayüz (`FooService` + `FooServiceImpl` refleksi).
- Üç satırlık nesne için factory.
- Spring'in zaten yaptığı şey için singleton.

## 7. Metot ve sınıf

- Metot tek iş yapar. `if` bloğu 3 seviye içeri girdiyse çıkar.
- İsim ne yaptığını söyler: `claimDelivery`, `isDeliverable`, `issuedSecretOf`. `process`,
  `handle`, `doWork` bir şey anlatmaz.
- Public metodun Javadoc'u **ne** yaptığını değil, **neden** öyle olduğunu anlatır. Koddan
  okunabilen şeyi tekrar yazma.
- Sınıf tek sorumluluk taşır. `CargoApiService` 14 bağımlılığa çıktıysa bölünmeli.

## 8. SonarQube'un en sık yakaladıkları

Kod yazarken bunlara baştan uy:

- **S1192** — aynı string 3+ kez tekrarlanmış → sabite al.
- **S112** — `throw new RuntimeException` → alana özgü istisna kullan
  (`WarehouseManagementException` + `ErrorCode`).
- **S1166** — istisna yutuldu ya da log'lanmadı.
- **S2259** — olası null dereference.
- **S3776** — bilişsel karmaşıklık yüksek → metodu böl.
- **S1172** — kullanılmayan parametre.
- **S106** — `System.out.println` → logger.
- **S2142** — `InterruptedException` yakalanıp yutulmuş → `Thread.currentThread().interrupt()`.
- **S4423 / S5542** — zayıf şifreleme; imza doğrulamada sabit zamanlı karşılaştırma kullan
  (`constantTimeEquals`).
- **S2245** — güvenlik bağlamında `Random` → `SecureRandom`.

## 9. Eşzamanlılık

- Paylaşılan değişebilir durum `volatile` ya da `Concurrent*` ile korunur.
- `synchronized` metodun içinde ağ çağrısı yapma — kilidi tutarak beklersin.
- `@Transactional` metodun içinde dış servis çağırma. İşlem, HTTP yanıtı boyunca açık kalır.
  Kargo çağrıları bu yüzden işlem dışına alındı.

## 10. Performans, ölçmeden değil

- N+1 sorgu: JPA'da liste çekerken `LEFT JOIN FETCH` kullan.
- Döngü içinde repository çağrısı yapma, tek sorguda çek.
- Erken optimizasyon yapma; önce doğru ve okunur yaz.
- Ama **sonsuz bekleyen dış çağrı** optimizasyon değil hatadır: `RestTemplate`'e zaman aşımı ver.

## Kontrol listesi

Java dosyası yazdıktan sonra:

- [ ] Tekrar eden string yok, sabitte
- [ ] Sınırlı değer kümesi enum
- [ ] Deprecated API yok, derleyici uyarısı temiz
- [ ] Her catch farklı bir şey söylüyor, hiçbiri sessiz değil
- [ ] Hata sebebi çağırana ulaşıyor
- [ ] Koleksiyon dönüşü null değil
- [ ] Javadoc "neden"i anlatıyor
- [ ] `@Transactional` içinde dış çağrı yok
- [ ] Test hatayı gerçekten yakalıyor (düzeltmeyi geri alıp doğrula)
