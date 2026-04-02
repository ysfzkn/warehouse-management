# Migration Plan: WMS to Admin Subdomain

## 1. Genel Bakis

Mevcut monolitik WMS uygulamasi `admin.domain.com` subdomain'ine tasinacak, ana domain (`www.domain.com`) yeni B2C e-ticaret vitrini icin ayrilacak. Backend tek Spring Boot uygulamasi olarak kalacak, API namespace'leri ile ayrim saglanacak.

---

## 2. API Namespace Ayirimi

### 2.1 Mevcut Durum
- Tum endpointler `/api/*` altinda
- `ApiPaths.java` tek `API_BASE = "/api"` sabiti kullaniyor
- 18 controller dogrudan `/api` prefix'i ile calisiyor

### 2.2 Hedef Yapi

```
/api/admin/*    -> Mevcut WMS endpointleri (admin paneli)
/api/store/*    -> Yeni storefront endpointleri (e-ticaret vitrini)
```

### 2.3 Degisiklikler

**`ApiPaths.java` Guncelleme:**
```java
public final class ApiPaths {
    public static final String API_ADMIN_BASE = "/api/admin";
    public static final String API_STORE_BASE = "/api/store";

    // Admin paths (mevcut)
    public static final String ADMIN_PRODUCTS = API_ADMIN_BASE + "/products";
    public static final String ADMIN_STOCKS = API_ADMIN_BASE + "/stocks";
    public static final String ADMIN_TRANSFERS = API_ADMIN_BASE + "/stock-transfers";
    public static final String ADMIN_WAREHOUSES = API_ADMIN_BASE + "/warehouses";
    // ... diger mevcut endpointler

    // Store paths (yeni)
    public static final String STORE_PRODUCTS = API_STORE_BASE + "/products";
    public static final String STORE_CATEGORIES = API_STORE_BASE + "/categories";
    public static final String STORE_CART = API_STORE_BASE + "/cart";
    public static final String STORE_ORDERS = API_STORE_BASE + "/orders";
    public static final String STORE_AUTH = API_STORE_BASE + "/auth";
    public static final String STORE_CHECKOUT = API_STORE_BASE + "/checkout";
}
```

**Mevcut Controller'larin Guncellenmesi:**
Her mevcut controller'in `@RequestMapping` annotation'i `/api/admin` prefix'i ile guncellenir:
- `ProductController`: `/api/products` -> `/api/admin/products`
- `StockController`: `/api/stocks` -> `/api/admin/stocks`
- `StockTransferController`: `/api/stock-transfers` -> `/api/admin/stock-transfers`
- `WarehouseController`: `/api/warehouses` -> `/api/admin/warehouses`
- `CategoryController`: `/api/categories` -> `/api/admin/categories`
- `BrandController`: `/api/brands` -> `/api/admin/brands`
- `ColorController`: `/api/colors` -> `/api/admin/colors`
- `DashboardController`: `/api/dashboard` -> `/api/admin/dashboard`
- `UserController`: `/api/users` -> `/api/admin/users`
- `AuditController`: `/api/audit` -> `/api/admin/audit`
- `NotificationController`: `/api/admin/notifications` (zaten prefix'li)
- `StockImportController`: `/api/stock-imports` -> `/api/admin/stock-imports`
- `StockRequestController`: `/api/stock-requests` -> `/api/admin/stock-requests`
- `StreamController`: `/api/stream` -> `/api/admin/stream`
- `AdminSecurityController`: `/api/admin/security-code` (zaten prefix'li)
- `AuthController`: `/api/auth` -> `/api/admin/auth`
- `InfoController`: `/api/info` -> `/api/admin/info`
- `StockTransferItemPhotoController`: path guncellenir

---

## 3. Auth Mekanizmasi Ayirimi

### 3.1 Mevcut Auth (Admin)
- `User` entity: username, passwordHash, role (ADMIN/STOCK_IN/STOCK_OUT)
- JWT token: `sub=username`, `role=ADMIN`, `exp=8h`
- `JwtAuthenticationFilter` ile Bearer token dogrulama

### 3.2 Yeni Auth (Customer)
- Ayri `Customer` entity: email, passwordHash, firstName, lastName, phone
- JWT token: `sub=email`, `userType=customer`, `customerId=<id>`, `exp=7d`
- Refresh token destegi

### 3.3 JwtService Genisletmesi

```java
// Mevcut admin token uretimi korunur
public String generateAdminToken(String username, UserRole role) {
    return Jwts.builder()
        .setSubject(username)
        .claim("role", role.name())
        .claim("userType", "admin")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + adminExpirationMs))
        .signWith(secretKey)
        .compact();
}

// Yeni customer token uretimi
public String generateCustomerToken(Long customerId, String email) {
    return Jwts.builder()
        .setSubject(email)
        .claim("customerId", customerId)
        .claim("userType", "customer")
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + customerExpirationMs))
        .signWith(secretKey)
        .compact();
}
```

### 3.4 SecurityConfig Bolunmesi

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/admin/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/auth/**").permitAll()
                .requestMatchers("/api/admin/info").permitAll()
                .anyRequest().hasAnyRole("ADMIN", "STOCK_IN", "STOCK_OUT")
            )
            .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain storeFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/store/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpointler
                .requestMatchers("/api/store/products/**").permitAll()
                .requestMatchers("/api/store/categories/**").permitAll()
                .requestMatchers("/api/store/brands/**").permitAll()
                .requestMatchers("/api/store/pages/**").permitAll()
                .requestMatchers("/api/store/auth/**").permitAll()
                .requestMatchers("/api/store/payment/callback").permitAll()
                // Customer auth gerektiren endpointler
                .anyRequest().hasRole("CUSTOMER")
            )
            .addFilterBefore(customerJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

---

## 4. Frontend Ayirimi

### 4.1 Admin Frontend (Mevcut React App)
- `frontend/` dizininde kalir
- `frontend/src/config.js` guncellenir:
  ```javascript
  const config = {
    apiBaseUrl: process.env.REACT_APP_API_URL || '/api/admin',
    // ...
  };
  ```
- Tum Axios cagrilari otomatik olarak yeni base URL'i kullanir (mevcut interceptor yapisi korunur)
- Route yapisi degismez (admin paneli icin ayni sayfalar)

### 4.2 Storefront Frontend (Yeni Next.js App)
- `storefront/` dizininde ayri proje
- Next.js 14 App Router
- Ayri `package.json`, ayri build pipeline
- API base: `/api/store`

---

## 5. Nginx / Reverse Proxy Konfigurasyonu

```nginx
# Admin Panel
server {
    listen 443 ssl;
    server_name admin.domain.com;

    ssl_certificate     /etc/ssl/certs/domain.crt;
    ssl_certificate_key /etc/ssl/private/domain.key;

    # Admin API -> Spring Boot backend
    location /api/admin/ {
        proxy_pass http://backend:8080/api/admin/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # SSE endpoint (admin)
    location /api/admin/stream {
        proxy_pass http://backend:8080/api/admin/stream;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
    }

    # Admin React SPA
    location / {
        root /var/www/admin-frontend/build;
        try_files $uri /index.html;
    }
}

# E-Commerce Storefront
server {
    listen 443 ssl;
    server_name www.domain.com domain.com;

    ssl_certificate     /etc/ssl/certs/domain.crt;
    ssl_certificate_key /etc/ssl/private/domain.key;

    # Store API -> Spring Boot backend
    location /api/store/ {
        proxy_pass http://backend:8080/api/store/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Next.js Storefront (SSR)
    location / {
        proxy_pass http://storefront:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 5.1 CORS Konfigurasyonu

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(
        "https://admin.domain.com",
        "https://www.domain.com",
        "https://domain.com",
        "http://localhost:3000",  // dev storefront
        "http://localhost:3001"   // dev admin
    ));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

---

## 6. Paylasilacak Servisler

Asagidaki mevcut servisler hem admin hem de storefront controller'lari tarafindan kullanilir:

| Servis | Admin Kullanimi | Store Kullanimi |
|--------|----------------|-----------------|
| ProductService | Tam CRUD | Read-only (aktif urunler, slug ile sorgulama) |
| CategoryService | Tam CRUD | Read-only (kategori agaci, slug ile) |
| BrandService | Tam CRUD | Read-only (aktif markalar) |
| ColorService | Tam CRUD | Read-only (aktif renkler) |
| StockService | Tam CRUD + import/export | getAvailableQuantity(), reserveStock(), releaseStock() |
| StockTransferService | Tam CRUD + approval | Auto-create from order (CUSTOMER_DELIVERY) |
| ProductImageService | Upload/delete | Read-only (gorsel servisi) |
| AuditService | View + log | Log (musteri islemleri) |
| NotificationService | View + create | Create (siparis bildirimi admin'e) |
| SsePushService | Push to admin clients | Push new order notification |

### 6.1 Store Controller Katmani

Yeni `com.warehouse.controller.store` package'i altinda storefront controller'lari olusturulur. Bu controller'lar mevcut servisleri kullanir ancak:
- Sadece gerekli alanlari expose eder (internal alanlar gizlenir)
- Read-only DTO'lar kullanir (StoreProductDto, StoreCategoryDto)
- Slug bazli sorgulama destekler

---

## 7. Docker Compose Guncelleme

```yaml
version: '3.8'
services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=jdbc:postgresql://db:5432/warehouse
      - JWT_SECRET=${JWT_SECRET}
      - ALLOWED_ORIGINS=https://admin.domain.com,https://www.domain.com
    depends_on:
      - db

  admin-frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    environment:
      - REACT_APP_API_URL=/api/admin

  storefront:
    build:
      context: ./storefront
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    environment:
      - NEXT_PUBLIC_API_URL=/api/store

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - backend
      - admin-frontend
      - storefront

  db:
    image: postgres:15
    environment:
      - POSTGRES_DB=warehouse
      - POSTGRES_USER=${DB_USER}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

---

## 8. Migrasyon Adimlari

### Adim 1: Hazirlik
- [ ] Feature branch olustur: `feature/api-namespace-split`
- [ ] Mevcut tum testlerin gectiginden emin ol

### Adim 2: Backend API Namespace Split
- [ ] `ApiPaths.java` guncelle (iki namespace)
- [ ] Tum 18 controller'in `@RequestMapping` prefix'ini `/api/admin` yap
- [ ] `SecurityConfig.java` iki `SecurityFilterChain` bean'e bol
- [ ] CORS konfigurasyonunu guncelle
- [ ] `AuthController` -> `/api/admin/auth/login` olarak guncelle
- [ ] SSE endpoint path'ini guncelle

### Adim 3: Frontend Guncelleme
- [ ] `frontend/src/config.js` base URL'i `/api/admin` yap
- [ ] Axios interceptor'larini kontrol et (base URL otomatik uygulanmali)
- [ ] Tum hardcoded API path'leri tara ve guncelle

### Adim 4: Yeni Store Auth Altyapisi
- [ ] `Customer` entity ve repository olustur
- [ ] `JwtService` genislet (customer token)
- [ ] `CustomerAuthController` olustur (`/api/store/auth/*`)
- [ ] Store SecurityFilterChain ekle

### Adim 5: Test ve Dogrulama
- [ ] Mevcut unit testleri guncelle (yeni path'ler)
- [ ] Admin paneli end-to-end test (login, CRUD islemleri)
- [ ] Store auth test (register, login, token dogrulama)
- [ ] CORS testi (her iki origin)

### Adim 6: Nginx ve Deploy
- [ ] Nginx konfigurasyonu hazirla
- [ ] Docker Compose guncelle
- [ ] Staging ortaminda test
- [ ] Production deploy

---

## 9. Rollback Stratejisi

Eger migrasyon basarisiz olursa:
1. Nginx'te `www.domain.com`'u eski konfigurasyona yonlendir
2. Backend'de git revert ile controller path'lerini eski haline getir
3. Frontend config.js'i eski base URL'e dondur
4. Deploy et

**Risk azaltma:** Tum degisiklikler tek bir feature branch'te yapilir. Atomik deploy icin backend + frontend + nginx degisiklikleri ayni anda canli alinir.

---

## 10. Zaman Cizelgesi

| Gun | Gorev |
|-----|-------|
| 1-2 | ApiPaths + Controller prefix degisiklikleri |
| 3 | SecurityConfig split + CORS |
| 4 | Frontend config guncelleme + test |
| 5 | Customer entity + auth altyapisi |
| 6-7 | Nginx konfigurasyonu + Docker Compose |
| 8 | Entegrasyon testleri + staging deploy |
| 9-10 | Production deploy + dogrulama |
