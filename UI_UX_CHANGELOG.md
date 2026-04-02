# UI/UX Changelog

## Faz 8: Design Tokens + Component Polish + Accessibility

Tarih: 2026-04-01
Backend Tests: 103 PASS | Frontend Build: SUCCESS

---

### 1. Design Token Sistemi

| Kategori | Detay |
|----------|-------|
| **Yeni dosya** | `design-tokens.css` — Tum uygulamada tutarlilik saglayan merkezi token dosyasi |
| **Color Palette** | Primary (50-900), Gray (50-900), Semantic (success/warning/error/info bg+text+border) |
| **Typography** | xs-4xl scale, tight/normal/relaxed line-height, normal-bold weight |
| **Spacing** | 4px base unit scale (0-16), tum margin/padding/gap icin |
| **Border & Shadow** | radius sm/md/lg/xl/full, shadow sm/md/lg/xl |
| **Transitions** | fast(150ms), normal(200ms), slow(300ms) — tum interactive elementlerde |
| **Z-Index** | dropdown(100), sticky(200), overlay(300), modal(400), toast(500) |
| **Focus Ring** | Global `*:focus-visible` — 2px primary ring, tum elementlerde |
| **Global States** | `button:active` scale(0.98), `button:disabled` opacity 0.5, `input:invalid` error ring |

---

### 2. store.css Token Migration

| Sorun | Yapilan |
|-------|---------|
| 17 hard-coded hex renk | Tumu `var(--color-*)` token'larina donusturuldu |
| 3 hard-coded border-radius | `var(--radius-sm/md/lg)` token'larina donusturuldu |
| Button `:focus-visible` eksik | `.btn-add-cart:focus-visible` focus ring eklendi |
| Button `:active` eksik | `.btn-add-cart:active` scale(0.97) eklendi |
| Quantity button states eksik | `:active` ve `:focus-visible` state'leri eklendi |
| Footer link transition yok | `transition: color var(--transition-fast)` eklendi |
| Cart sidebar overlay animasyon yok | `@keyframes overlay-fade-in` eklendi |
| Input focus ring tutarsiz | `.store-search-input:focus` → `var(--focus-ring)` |
| Empty state stili yok | `.store-empty-state` + ikon + heading stilleri eklendi |
| Scroll-to-top button yok | `.scroll-to-top` floating button stili eklendi |

---

### 3. Yeni Componentler

| Component | Detay |
|-----------|-------|
| **Skeleton.js** | `SkeletonBlock`, `SkeletonProductCard`, `SkeletonProductGrid`, `SkeletonProductDetail` — content-shape placeholder loading |
| **Toast.js** | `ToastProvider` + `useToast` hook — success/error/warning/info varyantlari, auto-dismiss (3-5s), slide animasyonu, `aria-live="polite"` |
| **NotFoundPage.js** | 404 sayfasi — buyuk "404" text, mesaj, Ana Sayfa + Urun Ara CTA butonlari |

---

### 4. Accessibility (a11y) Fixleri

| Component | Sorun | Fix |
|-----------|-------|-----|
| **StoreHeader** | Search form'da `role` yok | `role="search"` eklendi |
| **CartSidebar** | Dialog `aria-modal` ve `aria-labelledby` yok | `aria-modal="true"` + `aria-labelledby="cart-sidebar-title"` eklendi |
| **CheckoutStepper** | Semantic olmayan `<div>` yapisi | `<nav>` > `<ol>` > `<li>` yapisi + `aria-current="step"` |
| **ProductCard** | Badge'lerde `aria-label` yok | "Yeni urun" ve "Indirimli" `aria-label` eklendi |
| **ProductCard** | Disabled button'da `aria-disabled` yok | `aria-disabled="true"` eklendi |
| **HeroBanner** | Carousel role yok | `aria-roledescription="carousel"` + `aria-label` eklendi |
| **HeroBanner** | Auto-rotate durdurulamiyor | `onMouseEnter/Leave` ile pause/resume eklendi |
| **design-tokens.css** | Global focus visible yok | `*:focus-visible` → focus ring tum elementlerde |
| **design-tokens.css** | Button disabled state tutarsiz | Global `button:disabled` opacity + cursor |
| **design-tokens.css** | Input invalid state yok | `input:invalid:not(:placeholder-shown)` error ring |

---

### 5. Loading & Empty State Iyilestirmeleri

| Sayfa | Sorun | Fix |
|-------|-------|-----|
| **CategoryPage** | Spinner ile loading | Skeleton product grid (8 kart placeholder) |
| **ProductDetailPage** | Spinner ile loading | Skeleton galeri + bilgi alani placeholder |
| **CartPage** | Minimal bos sepet mesaji | Ikon (🛒) + baslik + aciklama + "Alisverise Basla" CTA |

---

### 6. Micro-interaction Altyapisi

| Ozellik | Detay |
|---------|-------|
| **Toast Notifications** | `useToast()` hook — `success()`, `error()`, `warning()`, `info()` metodlari |
| **Toast Animasyon** | `toast-slide-in` / `toast-slide-out` keyframe animasyonlari |
| **Skeleton Pulse** | `skeleton-pulse` keyframe — 1.5s ease-in-out pulse animasyonu |
| **Button Press** | Global `button:active` → `scale(0.98)` press efekti |
| **Card Hover** | Mevcut `translateY(-2px)` + shadow artisi korundu |
| **Overlay Fade** | Cart sidebar overlay `overlay-fade-in` animasyonu |

---

### 7. Routing Eklentileri

| Route | Component |
|-------|-----------|
| `/store/*` (catch-all) | `NotFoundPage` — 404 sayfasi |

---

## Dogrulama

| Kontrol | Sonuc |
|---------|-------|
| `mvn compile` | SUCCESS |
| `mvn test` (103 unit test) | 0 failure |
| `npm run build` | SUCCESS |
| Yeni dependency eklendi mi? | HAYIR (sifir ek dependency) |
| Bundle size artisi | Minimal (~2KB gzip — Skeleton + Toast + NotFound + tokens) |
