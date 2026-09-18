---
name: secure-by-default
description: Güvenlik değişmezleri — yetkilendirme kuralları, dışarıya açık uçlar, imza doğrulama, gizli bilgi sızıntısı, CORS ve ters vekil yönlendirmesi. SecurityConfig, public uç, webhook alıcısı, kimlik bilgisi işleyen kod ya da nginx yapılandırması değiştirirken kullan.
---

# Varsayılan olarak güvenli

Bu projede gerçekten yaşanmış güvenlik ve yönlendirme hatalarından çıkan kurallar.

## Yetkilendirme iki katmanlıdır, biri diğerine güvenmez

URL kuralı (`requestMatchers`) ve metot güvenliği (`@PreAuthorize`) **ikisi birden** olmalı.

Yaşanan: `POST /api/admin/cargo/webhook/**` URL kurallarında `permitAll()` idi, gerekçe olarak
yorumda "controller içinde HMAC ile doğrulanıyor" yazıyordu. O yolda HMAC doğrulaması yoktu;
uç, yönetici güvenlik şifresi doğruluyordu ve o kontrol **admin rolü olmayan çağıran için başta
`return` ediyordu**. Ucu kapalı tutan tek şey sınıftaki `@PreAuthorize` idi.

Kural:
- `permitAll()` yazarken o yolun altındaki **her** ucu say. Tek bir admin işlemi varsa açma.
- Yorumdaki gerekçeyi doğrula. "İmza ile korunuyor" diyorsa, git imzayı doğrulayan kodu gör.
- Erken `return` eden bir yetki kontrolü, kimliksiz çağıranı **geçirir**. `CurrentUser.getRole()`
  null ise ne olduğunu kontrol et.

## Gelen ve giden yönü karıştırma

```
/api/public/**   → dışarıdan bize gelir (webhook, ödeme callback). Kimliksiz, imzayla korunur.
/api/admin/**    → panelden bize gelir. Oturumla korunur.
```

Bir admin işlemini "webhook" sandığın anda hem CORS hem yetki kuralını yanlış kurarsın. Yaşanan:
panelin kayıt butonu, taşıyıcı origin'lerine açık callback CORS yapılandırmasına düşüp
"Invalid CORS request" aldı.

## Webhook alıcısı

- **Fail-closed.** İmza anahtarı tanımlı değilse isteği işleme, 503 dön. "Anahtar yoksa doğrulama
  atla" demek, ucu internete açmaktır.
- İmza karşılaştırması sabit zamanlı olsun (`constantTimeEquals`), `equals` değil.
- Idempotency anahtarıyla tekrarları yakala; taşıyıcı aynı olayı 5 kez gönderebilir.
- Dışarıdan çağrılan erişilebilirlik sondası varsa **sabit** yanıt ver. Yapılandırmaya göre
  değişen bir cevap, ucun ne zaman savunmasız olduğunu saldırgana söyler.
- Yeniden deneme politikasını bil: 2xx dışındaki yanıtlar tekrar denenir ama 4xx'ler denenmez.
  İmza hatasına 401 dönmek olayı kalıcı olarak kaybettirir — bu doğru davranıştır, ama anahtarı
  yanlış kaydettiysen bedeli budur.

## Gizli bilgi

- Token, secret, şifre **log'a yazılmaz**. Gerekirse parmak izi: uzunluk + ilk/son 4 karakter.
- Public uçlardan sızmasın: `SiteSettingServiceImpl` segment bazlı denylist kullanıyor. Yeni
  anahtar eklerken adının o listeye takıldığını doğrula (`token`, `secret`, `key`, `password`…).
- Repoya commit'lenen dosyaya (Postman koleksiyonu, örnek yapılandırma) gerçek değer yazma;
  environment dosyası boş kalsın.
- Kullanıcıdan token/şifre isteme — kendi terminalinde çalıştıracağı komutu ver.
- Kimlik bilgisi `trim()` edilerek gönderilir, her istemcide aynı şekilde.

## CORS

- `allowedOriginPatterns` + `allowCredentials(true)` kombinasyonunda joker karakter, internetteki
  her siteye kimlikli istek hakkı verir. `sanitizedAllowedOrigins()` bunu eliyor; elemeyi kaldırma.
- Yol kalıbı kaydederken hangi ucun hangi yapılandırmaya düştüğünü kontrol et. `/webhook/**`
  kalıbı `/webhooks` yolunu **kapsamaz** — tekil/çoğul farkı sessizce ikiye bölünmüş davranış
  üretir.

## Ters vekil (nginx) da güvenlik yüzeyidir

Uygulama doğru olsa bile yönlendirme yanlışsa sonuç yanlıştır.

- `/api/X → /api/admin/X` yeniden yazma kuralının muafiyet listesini kontrol et. Muaf olmayan
  public yol admin zincirine düşer ve 403 alır.
- Muaf tutmak tek başına yetmez: kendi `location` bloğu olmayan yol `try_files ... /index.html`'e
  düşer ve çağırana **200 + HTML** döner. Bir webhook için bu, teslim edilmiş görünüp sessizce
  kaybolmak demektir — 403'ten daha kötüdür, çünkü kimse fark etmez.
- Host doğrulaması (`HostValidationFilter`) yalnızca `/api/admin/`, `/api/cezeri/`, `/api/store/`
  yollarını kapsıyor. Yeni bir korumalı önek eklersen filtreyi de güncelle.

## Denetlenebilirlik

- Reddedilen istek log'lanır ama gövdesi saklanmaz (kimliksiz çağrı depolama saldırısına açıktır).
- Kabul edilen her webhook `cargo_webhook_deliveries`'e yazılır — imza ya da ayrıştırma sorunu
  ancak orada teşhis edilebilir.
- Kimlik bilgisi değiştiren admin işlemi güvenlik şifresi ister ve log'lanır.

## Kontrol listesi

Güvenlikle ilgili dosyaya dokunduysan:

- [ ] `permitAll()` verdiğin yolun altındaki tüm uçları saydım
- [ ] Yorumdaki güvenlik gerekçesini kodda doğruladım
- [ ] Metot güvenliği URL kuralından bağımsız olarak da koruyor
- [ ] Gizli bilgi log'a ya da public yanıta sızmıyor
- [ ] Dışarı açılan uç sabit yanıt veriyor
- [ ] nginx yönlendirmesi yeni yolu doğru katmana taşıyor
- [ ] Testi yazdım ve düzeltmeyi geri alıp yakaladığını doğruladım
