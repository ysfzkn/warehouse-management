# E-Commerce Architecture: B2C Storefront

## 1. Genel Mimari

```
                    +-------------------+
                    |      Nginx        |
                    |  (Reverse Proxy)  |
                    +--------+----------+
                             |
              +--------------+--------------+
              |                             |
    admin.domain.com              www.domain.com
              |                             |
    +---------+---------+      +------------+-----------+
    |  React CRA (SPA)  |      |  Next.js 14 (SSR/SSG) |
    |   Admin Panel      |      |   Storefront           |
    +---------+---------+      +------------+-----------+
              |                             |
              +-------------+---------------+
                            |
                   +--------+--------+
                   |  Spring Boot    |
                   |  Backend API    |
                   |  (Port 8080)    |
                   +--------+--------+
                            |
                   +--------+--------+
                   |   PostgreSQL    |
                   |   (Port 5432)  |
                   +----------------+
```

---

## 2. Storefront Tech Stack

| Katman | Teknoloji | Neden |
|--------|-----------|-------|
| Framework | Next.js 14 (App Router) | SSR/SSG ile SEO, React ekosistemi uyumlulugu |
| Styling | Tailwind CSS | Hizli UI gelistirme, responsive utility classes |
| State (Cart) | Zustand | Hafif, boilerplate'siz, localStorage persist |
| State (Auth) | NextAuth.js | JWT, OAuth, session yonetimi |
| State (UI) | React Context | Modal, toast, mobile menu |
| HTTP Client | fetch (native) + SWR | Next.js ile uyumlu, ISR/cache destegi |
| Icons | Lucide React | Hafif, tree-shakeable |
| Forms | React Hook Form + Zod | Performansli form yonetimi, type-safe validasyon |
| Image Opt. | Next.js Image | WebP, lazy loading, responsive srcset |
| Carousel | Embla Carousel | Hafif, touch destegi |
| Toast | Sonner | Minimal, animasyonlu bildirimler |

---

## 3. URL Yapisi ve Routing

### 3.1 Next.js App Router Dosya Yapisi

```
storefront/
├── app/
│   ├── layout.tsx                          # Root layout (Header + Footer)
│   ├── page.tsx                            # Anasayfa
│   ├── loading.tsx                         # Global loading skeleton
│   ├── not-found.tsx                       # 404 sayfasi
│   │
│   ├── kategori/
│   │   └── [slug]/
│   │       ├── page.tsx                    # /kategori/beyaz-esya
│   │       └── loading.tsx
│   │
│   ├── urun/
│   │   └── [slug]/
│   │       ├── page.tsx                    # /urun/arcelik-buzdolabi-xyz
│   │       └── loading.tsx
│   │
│   ├── uye-girisi/
│   │   └── page.tsx                        # /uye-girisi (login)
│   │
│   ├── uye-ol/
│   │   └── page.tsx                        # /uye-ol (register)
│   │
│   ├── sifremi-unuttum/
│   │   └── page.tsx                        # /sifremi-unuttum
│   │
│   ├── hesabim/
│   │   ├── layout.tsx                      # Account layout (sidebar nav)
│   │   ├── page.tsx                        # /hesabim (dashboard)
│   │   ├── siparisler/
│   │   │   ├── page.tsx                    # /hesabim/siparisler
│   │   │   └── [id]/
│   │   │       └── page.tsx               # /hesabim/siparisler/12345
│   │   ├── favorilerim/
│   │   │   └── page.tsx                    # /hesabim/favorilerim
│   │   ├── adreslerim/
│   │   │   └── page.tsx                    # /hesabim/adreslerim
│   │   ├── iade/
│   │   │   └── [id]/
│   │   │       └── page.tsx               # /hesabim/iade/12345
│   │   └── bilgilerim/
│   │       └── page.tsx                    # /hesabim/bilgilerim (profil)
│   │
│   ├── sepet/
│   │   └── page.tsx                        # /sepet
│   │
│   ├── odeme/
│   │   └── page.tsx                        # /odeme (checkout)
│   │
│   ├── siparis-takip/
│   │   └── page.tsx                        # /siparis-takip (guest tracking)
│   │
│   ├── iletisim/
│   │   └── page.tsx                        # /iletisim
│   │
│   ├── sayfa/
│   │   └── [slug]/
│   │       └── page.tsx                    # /sayfa/mesafeli-satis-sozlesmesi
│   │
│   ├── arama/
│   │   └── page.tsx                        # /arama?q=buzdolabi
│   │
│   └── api/
│       └── auth/
│           └── [...nextauth]/
│               └── route.ts               # NextAuth.js API route
│
├── components/
│   ├── layout/
│   │   ├── Header.tsx                      # Ust bar + logo + arama + icons
│   │   ├── MegaMenu.tsx                    # Kategori mega menu
│   │   ├── MobileNav.tsx                   # Mobil alt navigasyon
│   │   ├── Footer.tsx                      # Alt bilgi + linkler
│   │   └── Breadcrumb.tsx                  # Sayfa izi
│   │
│   ├── product/
│   │   ├── ProductGrid.tsx                 # Urun listesi grid
│   │   ├── ProductCard.tsx                 # Tek urun karti
│   │   ├── ProductDetail.tsx               # Urun detay ana component
│   │   ├── ProductGallery.tsx              # Gorsel galeri + zoom
│   │   ├── ProductSpecs.tsx                # Ozellik tablosu
│   │   ├── ProductPrice.tsx                # Fiyat gosterimi (KDV/OTV dahil)
│   │   ├── ProductReviews.tsx              # Degerlendirmeler
│   │   ├── ProductFilters.tsx              # Filtreleme sidebar
│   │   └── StockBadge.tsx                  # Stok durumu badge
│   │
│   ├── cart/
│   │   ├── CartSidebar.tsx                 # Slide-in sepet paneli
│   │   ├── CartPage.tsx                    # Tam sayfa sepet
│   │   ├── CartItem.tsx                    # Sepet kalem satirleri
│   │   ├── CartSummary.tsx                 # Ara toplam + kargo + toplam
│   │   └── CouponInput.tsx                 # Kupon kodu girisi
│   │
│   ├── checkout/
│   │   ├── CheckoutStepper.tsx             # 4 adimli progress bar
│   │   ├── AddressStep.tsx                 # Adres secimi/ekleme
│   │   ├── ShippingStep.tsx                # Kargo secimi
│   │   ├── PaymentStep.tsx                 # iyzico odeme formu
│   │   ├── ConfirmStep.tsx                 # Siparis onay
│   │   ├── AddressForm.tsx                 # Adres formu
│   │   └── InstallmentTable.tsx            # Taksit tablosu
│   │
│   ├── account/
│   │   ├── AccountSidebar.tsx              # Hesap menu
│   │   ├── OrderList.tsx                   # Siparis listesi
│   │   ├── OrderDetail.tsx                 # Siparis detay
│   │   ├── OrderStatusTimeline.tsx         # Siparis durum takibi
│   │   ├── AddressList.tsx                 # Adres yonetimi
│   │   ├── WishlistGrid.tsx                # Favori urunler
│   │   ├── ReturnRequestForm.tsx           # Iade talebi
│   │   └── ProfileForm.tsx                 # Profil duzenleme
│   │
│   ├── home/
│   │   ├── HeroSlider.tsx                  # Ana slider
│   │   ├── FeaturedProducts.tsx            # One cikan urunler
│   │   ├── CategoryShowcase.tsx            # Kategori vitrin
│   │   ├── CampaignBanner.tsx              # Kampanya banner
│   │   └── NewsletterSection.tsx           # Bulten kayit
│   │
│   └── ui/
│       ├── Button.tsx
│       ├── Input.tsx
│       ├── Select.tsx
│       ├── Modal.tsx
│       ├── Skeleton.tsx
│       ├── Badge.tsx
│       ├── Pagination.tsx
│       └── Toast.tsx
│
├── lib/
│   ├── api.ts                              # API client (fetch wrapper)
│   ├── auth.ts                             # NextAuth configuration
│   ├── utils.ts                            # Yardimci fonksiyonlar
│   └── constants.ts                        # Sabitler
│
├── stores/
│   ├── cart-store.ts                       # Zustand cart store
│   ├── wishlist-store.ts                   # Zustand wishlist store
│   └── ui-store.ts                         # UI state (modals, sidebar)
│
├── hooks/
│   ├── use-cart.ts                         # Cart islemleri hook
│   ├── use-wishlist.ts                     # Wishlist hook
│   ├── use-auth.ts                         # Auth helper hook
│   └── use-debounce.ts                     # Debounce hook
│
├── types/
│   ├── product.ts                          # Product, Category, Brand DTOs
│   ├── cart.ts                             # Cart, CartItem types
│   ├── order.ts                            # Order, OrderItem types
│   ├── customer.ts                         # Customer, Address types
│   └── api.ts                              # API response types
│
├── public/
│   ├── images/                             # Statik gorseller
│   └── icons/                              # Favicon, PWA ikonlari
│
├── next.config.js
├── tailwind.config.js
├── tsconfig.json
└── package.json
```

---

## 4. Component Mimarisi Detaylari

### 4.1 Header ve MegaMenu

```
+-----------------------------------------------------------------------+
| Telefon: (312) 226 26 21  |  Instagram  WhatsApp  |  Giris | Kayit   |
+-----------------------------------------------------------------------+
| [LOGO]  | [========= Arama =========] | [Favori] [Sepet(3)]          |
+-----------------------------------------------------------------------+
| Beyaz Esya | Pisirme | Kucuk Ev Aletleri | Elektronik | Kampanyalar  |
+-----------------------------------------------------------------------+
         |
         v (hover)
+--------------------------------------------------+
| Buzdolaplari       | Camasir Makineleri          |
| Derin Dondurucular | Kurutma Makineleri          |
| Buzdolabi Aksesuarlari | Camasir Kurutucular    |
+--------------------------------------------------+
```

- MegaMenu verisi: `GET /api/store/categories/tree` (kategorilerin parent-child hiyerarsisi)
- Mevcut `Category.parent` ve `Category.children` iliskileri kullanilir
- Cache: 30 dakika ISR + Caffeine backend cache

### 4.2 Urun Listesi Sayfasi (`/kategori/[slug]`)

```
+-------------------------------------------------------------------+
| Anasayfa > Beyaz Esya > Buzdolaplari                    [Breadcrumb]
+-------------------------------------------------------------------+
| [Filtreler]          | [Siralama: Fiyat ↑↓ | Yeni | Populer]     |
|                      |                                             |
| Marka                | +----------+  +----------+  +----------+  |
| [x] Arcelik          | | [Gorsel] |  | [Gorsel] |  | [Gorsel] |  |
| [ ] Beko             | | Urun Adi |  | Urun Adi |  | Urun Adi |  |
| [ ] Bosch            | | 12.999 TL|  | 8.499 TL |  | 15.999 TL|  |
|                      | | [Sepete] |  | [Sepete] |  | [Sepete] |  |
| Fiyat                | +----------+  +----------+  +----------+  |
| [1000] - [20000] TL  |                                            |
|                      | +----------+  +----------+  +----------+  |
| Renk                 | | ...      |  | ...      |  | ...      |  |
| [x] Beyaz            | +----------+  +----------+  +----------+  |
| [ ] Gri              |                                            |
|                      | [< 1 2 3 4 5 ... >]                       |
+-------------------------------------------------------------------+
```

- Filtreler URL query params olarak yansir: `/kategori/buzdolaplari?marka=arcelik&minFiyat=5000`
- Server-side rendering ile SEO uyumlu
- Mevcut `Brand`, `Color` entity'leri filtre opsiyonlari icin kullanilir

### 4.3 Urun Detay Sayfasi (`/urun/[slug]`)

```
+-------------------------------------------------------------------+
| Anasayfa > Beyaz Esya > Buzdolaplari > Arcelik 583lt            |
+-------------------------------------------------------------------+
| +------------------+  |  Arcelik 583lt No-Frost Buzdolabi         |
| |                  |  |  SKU: ARC-583-NF                          |
| |   [Ana Gorsel]   |  |                                           |
| |                  |  |  12.999,00 TL (KDV Dahil)                 |
| +------------------+  |  Vergisiz: 10.999,15 TL                   |
| [o] [o] [o] [o]      |  KDV (%18): 1.979,85 TL                   |
|                       |                                           |
|                       |  Stok Durumu: [Stokta]                    |
|                       |  Kargo: Ucretsiz (50 TL ustu)            |
|                       |                                           |
|                       |  Miktar: [-] 1 [+]                       |
|                       |  [   SEPETE EKLE   ] [FAVORİ ♡]          |
+-------------------------------------------------------------------+
| [Ozellikler] | [Yorumlar (24)] | [Taksit Secenekleri]            |
+-------------------------------------------------------------------+
| Boyutlar: 185 x 70 x 65 cm                                       |
| Agirlik: 78 kg                                                    |
| Kapasite: 583 lt                                                  |
| Enerji Sinifi: A++                                                |
+-------------------------------------------------------------------+
```

- Gorsel galeri: Mevcut `ProductImage` entity'sindeki `sortOrder` ve `primary` alanlari
- Fiyat hesaplama: `Product.price`, `Product.vatRate`, `Product.sctRate`
- Stok durumu: `Stock.getAvailableQuantity()` sonucu
- Boyutlar: `Product.lengthCm`, `Product.widthCm`, `Product.heightCm`, `Product.weight`

### 4.4 Checkout Akisi (4 Adim)

```
[1. Adres] ──> [2. Kargo] ──> [3. Odeme] ──> [4. Onay]
```

**Adim 1 - Adres:**
- Kayitli adres secimi veya yeni adres ekleme
- Teslimat adresi + fatura adresi (ayni/farkli)
- Bireysel / kurumsal fatura secimi
- Turkiye il/ilce/mahalle secimi (cascading dropdown)

**Adim 2 - Kargo:**
- Kargo firmalari: Yurtici, Aras, MNG, PTT
- Her firma icin tahmini teslimat suresi ve ucret
- Desi bazli kargo hesaplama (mevcut Product boyut alanlari)

**Adim 3 - Odeme:**
- iyzico Checkout Form (embedded iframe veya redirect)
- Taksit secenekleri tablosu (BIN bazli)
- Kapida odeme secenegi (kredi karti / nakit)
- 3D Secure dogrulama

**Adim 4 - Onay:**
- Siparis ozeti (urunler, adres, kargo, odeme)
- Mesafeli satis sozlesmesi onay checkbox'i (zorunlu)
- "Siparisi Onayla" butonu

---

## 5. State Management Detaylari

### 5.1 Cart Store (Zustand)

```typescript
interface CartState {
  items: CartItem[];
  coupon: Coupon | null;
  isOpen: boolean; // sidebar visibility

  addItem: (product: Product, quantity: number) => void;
  removeItem: (productId: number) => void;
  updateQuantity: (productId: number, quantity: number) => void;
  applyCoupon: (code: string) => Promise<void>;
  removeCoupon: () => void;
  clearCart: () => void;
  syncWithServer: () => Promise<void>; // Login sonrasi server sync
  toggleSidebar: () => void;

  // Computed
  subtotal: () => number;
  shippingCost: () => number;
  discountAmount: () => number;
  vatTotal: () => number;
  grandTotal: () => number;
  itemCount: () => number;
}
```

- Guest kullanicilar: `localStorage` persist (Zustand persist middleware)
- Logged-in kullanicilar: Server-side `carts` tablosu + localStorage fallback
- Login aninda: `syncWithServer()` ile localStorage cart server'a merge edilir

### 5.2 Auth Flow (NextAuth.js)

```typescript
// lib/auth.ts
export const authOptions: NextAuthOptions = {
  providers: [
    CredentialsProvider({
      name: 'credentials',
      credentials: {
        email: { label: 'Email', type: 'email' },
        password: { label: 'Sifre', type: 'password' },
      },
      async authorize(credentials) {
        const res = await fetch(`${API_BASE}/store/auth/login`, {
          method: 'POST',
          body: JSON.stringify(credentials),
          headers: { 'Content-Type': 'application/json' },
        });
        const data = await res.json();
        if (res.ok && data.token) {
          return { id: data.customerId, email: data.email, token: data.token };
        }
        return null;
      },
    }),
    // Opsiyonel: GoogleProvider, FacebookProvider
  ],
  callbacks: {
    async jwt({ token, user }) {
      if (user) {
        token.accessToken = user.token;
        token.customerId = user.id;
      }
      return token;
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.customerId = token.customerId;
      return session;
    },
  },
  pages: {
    signIn: '/uye-girisi',
    newUser: '/uye-ol',
  },
};
```

---

## 6. API Tasarimi (Storefront Endpointleri)

### 6.1 Public Endpointler (Auth Gerektirmez)

```
GET  /api/store/products
     ?category={slug}
     &brand={id}
     &color={id}
     &minPrice={value}
     &maxPrice={value}
     &search={query}
     &sort={price_asc|price_desc|newest|popular}
     &page={0}
     &size={24}
     Response: PagedResponse<StoreProductDto>

GET  /api/store/products/{slug}
     Response: StoreProductDetailDto (fiyat, stok durumu, gorseller, specs)

GET  /api/store/products/{slug}/reviews
     ?page={0}&size={10}
     Response: PagedResponse<ReviewDto>

GET  /api/store/categories/tree
     Response: List<CategoryTreeDto> (hiyerarsik agac)

GET  /api/store/categories/{slug}
     Response: StoreCategoryDto (kategori bilgileri + alt kategoriler)

GET  /api/store/brands
     Response: List<StoreBrandDto>

GET  /api/store/pages/{slug}
     Response: CmsPageDto

GET  /api/store/search/suggest?q={query}
     Response: List<SearchSuggestionDto> (autocomplete)
```

### 6.2 Auth Endpointleri

```
POST /api/store/auth/register
     Body: { email, password, firstName, lastName, phone, kvkkConsent }
     Response: { customerId, email, token }

POST /api/store/auth/login
     Body: { email, password }
     Response: { customerId, email, firstName, token, refreshToken }

POST /api/store/auth/refresh
     Body: { refreshToken }
     Response: { token, refreshToken }

POST /api/store/auth/forgot-password
     Body: { email }
     Response: { message }

POST /api/store/auth/reset-password
     Body: { token, newPassword }
     Response: { message }

POST /api/store/auth/verify-email
     Body: { token }
     Response: { message }
```

### 6.3 Customer Endpointleri (ROLE_CUSTOMER)

```
# Sepet
GET    /api/store/cart
POST   /api/store/cart/items          Body: { productId, quantity }
PUT    /api/store/cart/items/{itemId}  Body: { quantity }
DELETE /api/store/cart/items/{itemId}
POST   /api/store/cart/coupon          Body: { code }
DELETE /api/store/cart/coupon

# Checkout
POST   /api/store/checkout/validate    -> Stok kontrol + fiyat dogrulama
POST   /api/store/checkout/place-order Body: { shippingAddressId, billingAddressId, cargoCompany, paymentMethod }
     Response: { orderId, orderNumber, paymentUrl? }

# Siparisler
GET    /api/store/orders
GET    /api/store/orders/{id}
POST   /api/store/orders/{id}/cancel   Body: { reason }

# Iade
POST   /api/store/orders/{id}/return   Body: { items: [{orderItemId, quantity, reason}], description }
GET    /api/store/returns/{id}

# Favoriler
GET    /api/store/wishlist
POST   /api/store/wishlist/{productId}
DELETE /api/store/wishlist/{productId}

# Adresler
GET    /api/store/addresses
POST   /api/store/addresses            Body: { title, addressType, firstName, lastName, phone, city, district, addressLine, ... }
PUT    /api/store/addresses/{id}
DELETE /api/store/addresses/{id}

# Hesap
GET    /api/store/account
PUT    /api/store/account              Body: { firstName, lastName, phone }
PUT    /api/store/account/password     Body: { currentPassword, newPassword }
DELETE /api/store/account              (hesap silme talebi)

# Yorumlar
POST   /api/store/reviews              Body: { productId, orderId?, rating, title?, comment }

# Bulten
POST   /api/store/newsletter/subscribe Body: { email }
POST   /api/store/newsletter/unsubscribe Body: { email, token }
```

---

## 7. SEO Stratejisi

### 7.1 Meta Tags (Server-Side)

```typescript
// app/urun/[slug]/page.tsx
export async function generateMetadata({ params }): Promise<Metadata> {
  const product = await getProduct(params.slug);
  return {
    title: `${product.name} | Domain.com`,
    description: product.shortDescription || product.description?.substring(0, 160),
    openGraph: {
      title: product.name,
      description: product.shortDescription,
      images: [{ url: product.primaryImage?.url }],
      type: 'product',
    },
  };
}
```

### 7.2 JSON-LD Structured Data

```typescript
// Urun sayfasinda
const productJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'Product',
  name: product.name,
  image: product.images.map(img => img.url),
  description: product.description,
  sku: product.sku,
  brand: { '@type': 'Brand', name: product.brand?.name },
  offers: {
    '@type': 'Offer',
    price: product.price,
    priceCurrency: 'TRY',
    availability: stock > 0
      ? 'https://schema.org/InStock'
      : 'https://schema.org/OutOfStock',
  },
  aggregateRating: reviews.length > 0 ? {
    '@type': 'AggregateRating',
    ratingValue: avgRating,
    reviewCount: reviews.length,
  } : undefined,
};
```

### 7.3 Sitemap

```typescript
// app/sitemap.ts
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const products = await getAllProductSlugs();
  const categories = await getAllCategorySlugs();

  return [
    { url: 'https://www.domain.com', lastModified: new Date(), changeFrequency: 'daily', priority: 1 },
    ...categories.map(cat => ({
      url: `https://www.domain.com/kategori/${cat.slug}`,
      lastModified: cat.updatedAt,
      changeFrequency: 'weekly' as const,
      priority: 0.8,
    })),
    ...products.map(prod => ({
      url: `https://www.domain.com/urun/${prod.slug}`,
      lastModified: prod.updatedAt,
      changeFrequency: 'weekly' as const,
      priority: 0.6,
    })),
  ];
}
```

### 7.4 Robots.txt

```
User-agent: *
Allow: /
Disallow: /hesabim/
Disallow: /sepet
Disallow: /odeme
Disallow: /api/
Sitemap: https://www.domain.com/sitemap.xml
```

---

## 8. Performance Optimizasyonu

### 8.1 Caching Stratejisi

| Veri | Yontem | TTL |
|------|--------|-----|
| Kategori agaci | ISR + Caffeine | 30 dk |
| Urun listesi | ISR | 5 dk |
| Urun detay | ISR | 60 sn |
| Stok durumu | Client-side fetch (no cache) | Real-time |
| Marka/Renk listesi | ISR + Caffeine | 60 dk |
| CMS sayfalari | ISR | 1 saat |
| Sepet | Zustand + localStorage | - |

### 8.2 Image Optimization

- Next.js `<Image>` component ile otomatik WebP donusumu
- Mevcut `ProductImage.thumbnailPath` kucuk gorseller icin kullanilir
- `ProductImage.width` ve `ProductImage.height` layout shift onlemi icin
- Lazy loading: Ekranin altindaki gorseller
- Responsive `srcset`: mobile (375w), tablet (768w), desktop (1280w)

### 8.3 Bundle Optimization

- Next.js App Router otomatik route-based code splitting
- Dynamic import: checkout, account sayfalari (auth sonrasi)
- Tree shaking: Lucide icons, Zustand
- Brotli/Gzip compression (Nginx'te)

---

## 9. Mobile Responsiveness

### 9.1 Breakpoints (Tailwind)

```
sm: 640px    -> Kucuk mobil
md: 768px    -> Tablet
lg: 1024px   -> Kucuk desktop
xl: 1280px   -> Desktop
2xl: 1536px  -> Genis ekran
```

### 9.2 Mobil Ozel Davranislar

- **Mega menu**: Tam ekran drawer (slide-in)
- **Urun grid**: 2 sutun (mobil) -> 3 sutun (tablet) -> 4 sutun (desktop)
- **Filtreler**: Overlay panel (mobil) -> sidebar (desktop)
- **Sepet**: Tam sayfa (mobil) -> slide-in sidebar (desktop)
- **Checkout**: Tek sutun (mobil), stepper dikey
- **Alt navigasyon**: Sticky bottom bar (mobil): Anasayfa, Kategoriler, Sepet, Hesabim
- **Urun galeri**: Swipe gesture ile gorsel degistirme
- **Touch targets**: Minimum 44x44px tap alani

### 9.3 PWA Destegi (Opsiyonel)

```json
// public/manifest.json
{
  "name": "Domain E-Ticaret",
  "short_name": "Domain",
  "start_url": "/",
  "display": "standalone",
  "theme_color": "#E5AE49",
  "background_color": "#FFFFFF",
  "icons": [...]
}
```

---

## 10. Error Handling

### 10.1 API Error Response Format

```json
{
  "error": true,
  "code": "INSUFFICIENT_STOCK",
  "message": "Bu urun icin yeterli stok bulunmamaktadir.",
  "details": {
    "productId": 123,
    "available": 2,
    "requested": 5
  }
}
```

### 10.2 Frontend Error Boundaries

- Root error boundary: Generic hata sayfasi
- Page-level error boundaries: Sayfa bazli hata yakalama
- Component-level: Cart, checkout formlarinda inline hata mesajlari
- Toast notifications: Islem basari/basarisizlik bildirimleri

### 10.3 Offline Handling

- Service worker ile basic offline sayfasi
- Cart: localStorage'dan yuklenebilir (offline goruntulenebilir)
- Checkout: Online baglanti zorunlu (kontrol yapilir)

---

## 11. Guvenlik

- **HTTPS zorunlu** (Nginx redirect)
- **JWT httpOnly cookie** (XSS korunmasi)
- **CSRF token** (Next.js ile otomatik, API istekleri icin)
- **Rate limiting** (login, register, checkout endpointleri)
- **Input sanitization** (XSS, SQL injection korunmasi - backend)
- **CSP headers** (Content-Security-Policy)
- **iyzico PCI DSS** (kart bilgileri backend'e ulasmaz)
- **KVKK uyumlulugu** (bkz. INTEGRATION_SPEC.md)
