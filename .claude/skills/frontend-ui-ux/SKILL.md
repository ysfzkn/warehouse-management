---
name: frontend-ui-ux
description: React admin paneli ve mağaza arayüzü için UI/UX kuralları — tasarım token'ları, durum gösterimi (boş/hata/yükleniyor), form ve onay akışları, erişilebilirlik, Türkçe metin tonu. frontend/src altında .js/.jsx/.css dosyası yazarken veya düzenlerken kullan.
---

# Arayüz ve kullanıcı deneyimi

İki yüzey var: **admin/WMS paneli** (depo personeli, gün boyu kullanır) ve **mağaza**
(müşteri, bir kez uğrar). İhtiyaçları farklı; ortak olan tasarım token'ları.

## Tasarım token'ları — sabit renk yazma

`frontend/src/design-tokens.css` tek kaynak. Hem admin hem mağaza oradan besleniyor.

```css
/* YANLIŞ */
.badge { background: #2563eb; }

/* DOĞRU */
.badge { background: var(--color-primary-600); }
```

Marka rengi admin panelinden `primary_color` ayarıyla çalışma zamanında değişiyor. Sabit hex
yazmak o ayarı sessizce etkisiz kılar — değişiklik bazı yerlere uygular, bazılarına uygulamaz.

Yeni bir değer gerekiyorsa önce token'lara ekle, sonra kullan.

## Her ekranın dört hâli vardır

Bir liste yazarken dördünü de düşün. En sık atlanan üçüncüsü.

| Hâl | Ne gösterilir |
|---|---|
| Yükleniyor | Skeleton ya da spinner — içerik yerinde kalsın, zıplamasın |
| Dolu | Asıl içerik |
| **Boş** | `EmptyState` — ne olduğunu ve ne yapılacağını söyler |
| Hata | `ErrorState` — sebebi söyler, tekrar dene sunar |

Boş liste ile hata **aynı görünmemeli.** "Kayıt yok" yazan bir ekran, aslında istek 500 döndüyse
kullanıcıyı yanlış yönlendirir; veri yok sanıp beklemeye başlar.

```jsx
if (loading) return <Skeleton rows={5} />;
if (error)   return <ErrorState message={error} onRetry={load} />;
if (!items.length) return <EmptyState title="Henüz kayıt yok" action={...} />;
```

## Hata mesajı: sebebi söyle

Backend `message` alanı döndürüyorsa **onu göster**, sabit metin basma.

```jsx
// YANLIŞ — 403 iki farklı sebeple dönüyor, kullanıcı hangisi bilmiyor
if (status === 403) toast.error('Güvenlik şifresi hatalı.');

// DOĞRU
if (status === 403) toast.error(backendMsg || 'Güvenlik şifresi hatalı.');
```

Aynısı `success: false` dönen 200 yanıtlar için de geçerli: HTTP başarılı diye işlemi başarılı
sayma, gövdeye bak.

```jsx
if (data?.success === false) {
  toast.error(data.message || 'İşlem tamamlanamadı.');
  return;
}
```

## Yıkıcı ve geri alınamaz işlemler

- Silme, toplu güncelleme, dış sisteme kayıt → `ConfirmModal`.
- Onay metni **ne olacağını** yazar: "3 siparişi iptal et" ✓ / "Emin misiniz?" ✗
- Kimlik bilgisi değiştiren işlemler güvenlik şifresi ister (`X-ADMIN-SECURITY-CODE`).
- İşlem sırasında butonu kilitle; çift tıklama iki kayıt açar.

## Bir kez gösterilen değerler

Bir değer yalnızca bir kez gösteriliyorsa (dış servisin ürettiği anahtar, tek seferlik kod)
kullanıcıyı kaydetmeye **açıkça** yönlendir ve kaçırırsa ne olacağını söyle. Sessizce alana
yazıp geçmek, kullanıcının o değeri kaybetmesiyle sonuçlanır ve geri getirilemez.

## Form

- Etiket her alanda var (`<label htmlFor>`), placeholder etiket yerine geçmez.
- Doğrulama gönderimde değil, alandan çıkışta.
- Hata alanın yanında; formun tepesindeki genel uyarı tek başına yetmez.
- Gizli alanlar (token, secret, şifre) `type="password"` + göz ikonu.
- Kaydedilmemiş değişiklik varken sayfadan çıkışta uyar.

## Tablo ve liste

- Sütun başlığı sıralanabilirse bunu görsel olarak belli et.
- Uzun listede sayfalama; "hepsini çek" tablo büyüdükçe tarayıcıyı kilitler.
- Satır içi işlemler (düzenle/sil) sağda ve tutarlı sırada.
- Para, tarih ve sayı Türkçe biçimde: `toLocaleString('tr-TR')`.
- Kolon içeriği taşıyorsa `ExpandableText` kullan, kırpıp bırakma.

## Erişilebilirlik

- Tıklanabilir şey `<button>` ya da `<a>`. `onClick` verilmiş `<div>` klavyeyle erişilemez.
- İkon tek başına butonsa `aria-label` şart.
- Renk tek başına anlam taşımaz — kırmızı rozetin yanında metin de olsun.
- Modal açıkken odak içeride kalsın, `Esc` kapatsın.
- Kontrast en az 4.5:1.

## Türkçe metin tonu

- Kullanıcıya ne yapacağını söyle: "Token'ı Ayarlar → Kargo API'den girin."
- Teknik terimi gerektiğinde kullan ama yalnız bırakma: "HTTP 401 — token reddedildi."
- Sen/siz tutarlı olsun; bu projede **siz**.
- Panel metinleri Türkçe. Kod, değişken adı ve log İngilizce olabilir.

## React

- Bileşen tek iş yapar. `AdminSiteSettings.js` 2000 satırı aştıysa parçalanmalı.
- `useEffect` bağımlılıklarını eksiksiz yaz; `eslint-disable` ile susturma.
- Listeye `key` olarak index değil kayıt id'si ver.
- Veri çekme mantığını hook'a al (`hooks/` altında), bileşende bırakma.
- Yan etkili işlemden sonra yalnızca değişeni tazele — tüm sayfayı yeniden yüklemek açık
  modalleri kapatır ve kullanıcının yerini kaybettirir.

## Kontrol listesi

- [ ] Sabit renk yok, token var
- [ ] Dört hâl de var; boş ile hata ayrı
- [ ] Hata mesajı backend'in söylediğini gösteriyor
- [ ] `success: false` yakalanıyor
- [ ] Yıkıcı işlemde onay ve buton kilidi var
- [ ] Etiket, `aria-label`, klavye erişimi tamam
- [ ] Tarih/para Türkçe biçimde
- [ ] `key` index değil
