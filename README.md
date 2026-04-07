<div align="center">

# 🏬 Warehouse & E-Commerce Management Platform

**An enterprise-grade omnichannel platform that unifies warehouse operations with a full B2C e-commerce storefront**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[Features](#-features) • [Quick Start](#-quick-start) • [Tech Stack](#-tech-stack) • [Architecture](#-architecture) • [Deployment](#-deployment) • [Contributing](#-contributing)

</div>

---

## 🎯 Overview

This project began as a multi-warehouse inventory system and has evolved into a complete **omnichannel commerce platform**. It now combines back-office warehouse operations (stock control, transfers, multi-location inventory) with a public-facing **e-commerce storefront** (catalog, cart, checkout, payments, customer accounts) and a powerful **admin panel** for sales, CMS, coupons, and support.

### Key Highlights

- 🏭 **Multi-Warehouse Inventory** — Unlimited warehouses, real-time stock, transfers, low-stock alerts
- 🛒 **Full E-Commerce Storefront** — Catalog, cart, multi-step checkout, wishlist, reviews, returns
- 💳 **Multi-Gateway Payments** — iyzico, PayTR, NestPay/GVP, Bank Transfer & Cash on Delivery
- 👤 **Customer Accounts** — Email/password + Google OAuth, addresses, order history
- 📊 **Sales & Admin Dashboard** — Live analytics, order/cargo tracking, audit logs
- 📝 **CMS & Marketing** — Banners, pages, coupons, newsletter, support tickets
- 🔒 **Production-Ready** — JWT auth, Flyway migrations, centralized error handling, scheduled jobs
- 🚀 **Easy Deployment** — Docker, Docker Compose, Railway-ready

---

## ✨ Features

### 🏭 Warehouse Operations

| Feature | Description |
|---------|-------------|
| **Product Management** | Full CRUD with SKU, pricing, attributes, multiple images |
| **Inventory Control** | Real-time stock, reserved quantities, consignment tracking, low-stock alerts |
| **Multi-Warehouse** | Unlimited warehouses with per-location stock and capacity |
| **Stock Transfers** | Inter-warehouse transfers with `PENDING → IN_TRANSIT → COMPLETED` workflow |
| **Stock Events** | Auditable event log of every stock movement |
| **Catalog Taxonomy** | Categories, brands, colors with hierarchical filtering |

### 🛍️ E-Commerce Storefront

| Feature | Description |
|---------|-------------|
| **Customer Auth** | Email/password registration, JWT sessions, Google OAuth, refresh tokens |
| **Product Browsing** | Public catalog with filtering by category, brand, color, price |
| **Shopping Cart** | Persistent cart, quantity updates, real-time totals |
| **Checkout** | Multi-step checkout with addresses, shipping & billing |
| **Orders** | Order history, status timeline, cargo tracking |
| **Wishlist & Reviews** | Favorites and product ratings |
| **Returns & Refunds** | Return request workflow with status management |
| **Coupons** | Discount codes with validity windows and usage tracking |
| **CMS Pages** | Banners, About / Terms / Privacy pages, newsletter subscriptions |
| **Contact Form** | Public form with honeypot + rate limit, DB-logged & forwarded to operator inbox |
| **Support Tickets** | Customer support inbox with ticket lifecycle |

### 💳 Payment Gateways (Strategy Pattern)

The payment layer is gateway-agnostic and admin-configurable:

- **iyzico** — PCI-compliant Checkout Form
- **PayTR** — Virtual POS
- **NestPay / GVP** — Bank Virtual POS
- **Bank Transfer (Havale/EFT)** — IBAN-based with deadline tracking
- **Door Payment** — Cash on delivery

A scheduled `PaymentTimeoutJob` handles abandoned/timed-out transactions automatically.

### 🛠️ Admin Panel

Sales dashboard, customer management, order & cargo tracking, payment gateway configuration, CMS editor, coupon management, support ticket inbox, audit logs, and site-wide settings.

### ⚙️ Technical Features

- ✅ **RESTful API** with clean resource design and proper status codes
- ✅ **JWT Authentication** for both admin and customer scopes
- ✅ **Spring Security** with custom filters (customer status, admin security code)
- ✅ **Flyway Migrations** — 38+ versioned schema scripts
- ✅ **Centralized Exception Handling** with structured error codes
- ✅ **Virtual Threads (Java 21)** for I/O-bound payment, SMTP & outbound HTTP workloads
- ✅ **Caffeine Cache** for hot reference data
- ✅ **Event Publishing** for order/payment lifecycle
- ✅ **Scheduled Jobs** for payment timeouts and housekeeping
- ✅ **Excel Import/Export** via Apache POI
- ✅ **Email Notifications** via Spring Mail (SMTP)

---

## 🚀 Quick Start

> For a step-by-step setup walkthrough, see **[QUICK_START.md](QUICK_START.md)**.

### Prerequisites

- **Docker & Docker Compose** (recommended)
- **Java 21+** (for local backend dev)
- **Node.js 18+** (for local frontend dev)
- **PostgreSQL 15** (if not using Docker)

### Option 1 — Docker (recommended)

```bash
git clone https://github.com/yourusername/warehouse-management.git
cd warehouse-management
docker-compose up -d
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
- PostgreSQL: localhost:5432

### Option 2 — Manual

**Backend**
```bash
mvn clean install
mvn spring-boot:run
```

**Frontend**
```bash
cd frontend
npm install
npm start
```

> **Default admin credentials:** `admin / admin` — change them immediately in any non-dev environment, along with the JWT secret and admin security code.

---

## 🛠️ Tech Stack

### Backend
- **Spring Boot 3.3.5** (Web, Security, Data JPA, Validation, Cache, Mail, Actuator)
- **Java 21** (LTS) — with **virtual threads** enabled for I/O-bound workloads
- **Spring Security + JWT** (`jjwt 0.11.5`) — separate admin & customer scopes
- **Hibernate 6.5 / JPA** with **Caffeine** L2 cache
- **Flyway 10.x** — versioned migrations (`V1` … `V38`)
- **iyzipay-java** SDK for iyzico integration
- **Apache POI 5.2** for Excel import/export
- **Maven** build, **JUnit 5 + Mockito** for tests

### Frontend
- **React 18.2** + **React Router 6**
- **Bootstrap 5** + **React Bootstrap**
- **Axios** for API calls
- **Chart.js** + `react-chartjs-2` for analytics
- **React Helmet Async** for SEO

### Database & DevOps
- **PostgreSQL 15** (production), **H2** (dev convenience)
- **Docker** & **Docker Compose**
- **Nginx** reverse proxy (production)
- **Railway / Heroku / AWS / GCP / Azure / VPS** ready

---

## 🏗️ Architecture

```
        ┌────────────────────┐        ┌────────────────────┐
        │  Customer Storefront│       │    Admin Panel     │
        │     (React SPA)    │        │    (React SPA)     │
        └──────────┬─────────┘        └──────────┬─────────┘
                   │ HTTPS / REST + JWT          │
                   ▼                             ▼
        ┌──────────────────────────────────────────────────┐
        │              Spring Boot API (8080)              │
        │  ┌────────────────────────────────────────────┐  │
        │  │ Controllers   (store/* + admin/*)          │  │
        │  ├────────────────────────────────────────────┤  │
        │  │ Services  (Cart, Order, Payment, Catalog…) │  │
        │  ├────────────────────────────────────────────┤  │
        │  │ Payment Gateway Strategy                   │  │
        │  │  ├─ iyzico   ├─ PayTR   ├─ NestPay/GVP     │  │
        │  │  ├─ Bank Transfer        └─ Door Payment    │  │
        │  ├────────────────────────────────────────────┤  │
        │  │ Repositories (Spring Data JPA)             │  │
        │  └────────────────────────────────────────────┘  │
        └──────────┬─────────────────────────┬─────────────┘
                   │ JDBC                    │ HTTPS
                   ▼                         ▼
        ┌────────────────────┐   ┌────────────────────────┐
        │   PostgreSQL 15    │   │  Payment Providers     │
        │   (Flyway-managed) │   │  (iyzico / PayTR / …)  │
        └────────────────────┘   └────────────────────────┘
```

### Project Structure

```
warehouse-management/
├── src/main/java/com/warehouse/
│   ├── controller/
│   │   ├── store/         # Public storefront REST endpoints
│   │   ├── admin/         # Admin panel REST endpoints
│   │   └── ...            # Catalog, stock, warehouse controllers
│   ├── service/
│   │   ├── payment/       # Gateway strategy + implementations
│   │   └── ...            # Cart, Order, Customer, Catalog services
│   ├── entity/            # JPA entities (40+: catalog, orders, payments…)
│   ├── repository/        # Spring Data repositories
│   ├── dto/               # Request/response DTOs (admin/, store/, payment/)
│   ├── security/          # JWT filters, customer/admin guards
│   ├── job/               # Scheduled jobs (e.g. PaymentTimeoutJob)
│   ├── event/             # Domain events
│   ├── exception/         # Custom errors + global handler
│   └── config/
├── src/main/resources/
│   ├── db/migration/      # Flyway scripts V1 … V38
│   └── application*.properties
├── frontend/
│   └── src/
│       ├── components/store/   # Storefront UI
│       ├── components/admin/   # Admin panel UI
│       └── pages/
├── docs/                  # Payment & SEO guides
├── docker-compose.yml
└── Dockerfile
```

---

## 🧬 Database & Migrations

The schema is fully Flyway-managed under `src/main/resources/db/migration`:

- **`V1` – `V14`** — Core warehouse schema (products, stock, transfers, brands, colors)
- **`V15` – `V38`** — E-commerce extensions (customers, cart, orders, payments, coupons, CMS, cargo, support tickets)

Run migrations manually:

```bash
mvn -Dflyway.url=jdbc:postgresql://localhost:5432/warehouse_db \
    -Dflyway.user=warehouse_user \
    -Dflyway.password=warehouse_pass \
    flyway:migrate
```

Spring Boot also runs Flyway on startup. To adopt an existing populated database, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` (and optionally `SPRING_FLYWAY_BASELINE_VERSION=1`) **once**, then revert.

`./deploy.sh` runs Flyway before rebuilding the stack — pass `SKIP_FLYWAY=true` to skip.

---

## ⚙️ Configuration

### Environment Variables

```properties
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/warehouse_db
SPRING_DATASOURCE_USERNAME=warehouse_user
SPRING_DATASOURCE_PASSWORD=secret123

# Profile
SPRING_PROFILES_ACTIVE=prod    # dev | prod | docker

# JWT (CHANGE IN PRODUCTION — min 32 chars)
APP_JWT_SECRET=change-me-please-change-me-please
APP_JWT_EXPIRATION=86400000

# Payment (default provider + sandbox toggle)
PAYMENT_PROVIDER=IYZICO
PAYMENT_SANDBOX=true

# OAuth
GOOGLE_OAUTH_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_SECRET=...

# Flyway baseline (one-shot only)
SPRING_FLYWAY_BASELINE_ON_MIGRATE=false
```

### Payment Configuration

Payment gateways are configured at runtime from the **Admin Panel → Payment Settings** screen, so secrets do not need to live in source control. For deeper integration details, sandbox credentials and end-to-end test flows see:

- 📘 [`docs/PAYMENT_INTEGRATION_GUIDE.md`](docs/PAYMENT_INTEGRATION_GUIDE.md)
- 🧪 [`docs/PAYMENT_TEST_GUIDE.md`](docs/PAYMENT_TEST_GUIDE.md)

### Application Profiles

- **`dev`** — Local development (verbose logging, relaxed security, optional H2)
- **`prod`** — Production-grade settings (PostgreSQL, strict security)
- **`docker`** — Container-aware overrides

---

## 🚀 Deployment

### Docker

```bash
docker-compose up -d        # start
docker-compose logs -f      # tail logs
docker-compose down         # stop
```

> Use `./deploy.sh` for an opinionated flow that runs Flyway migrations before rebuilding the containers. Set `SKIP_FLYWAY=true` to only rebuild images.

### Cloud Targets

The app is ready for **Railway**, **Heroku**, **AWS / GCP / Azure**, or any **VPS** running Docker. The bundled `nginx/prod.conf` proxies `/` to the React frontend and `/api/**` (including `/api/stream` and `/api/actuator/health`) to Spring Boot.

#### Railway + Flyway pipeline

1. Install the Railway CLI (`npm install -g @railway/cli`) and log in.
2. `railway init`, then `railway add postgresql` for a managed database.
3. Expose `DATABASE_URL`, `PGUSER`, `PGPASSWORD` as env vars.
4. Run migrations before deploying:
   ```bash
   railway run "mvn -DskipTests -Dflyway.url=$DATABASE_URL \
       -Dflyway.user=$PGUSER \
       -Dflyway.password=$PGPASSWORD \
       flyway:migrate"
   ```
5. For an existing Railway database, temporarily set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` for a single baseline run, then revert.
6. Deploy with `railway up` — the platform builds from the root `Dockerfile`.

---

## 🧪 Testing

```bash
mvn test                              # all tests
mvn test -Dtest=ProductServiceTest    # single class
mvn test jacoco:report                # with coverage
```

Covered today:
- ✅ Service layer (catalog, stock, payment) — 30+ unit tests
- ✅ Mapper layer
- ✅ Exception handling

---

## 🗺️ Roadmap

- [ ] Multi-language support (i18n)
- [ ] Advanced reporting & BI exports
- [ ] Barcode / QR scanning for warehouse operators
- [ ] Mobile app (React Native)
- [ ] Real-time notifications (WebSocket / SSE)
- [ ] Granular role-based access control (RBAC)
- [ ] API rate limiting & audit dashboards
- [ ] AI assistant integration (see `docs/AI_ASISTAN_PLANI.md`)

---

## 🤝 Contributing

Contributions are very welcome!

1. Fork the repository
2. Create a feature branch — `git checkout -b feature/amazing-feature`
3. Commit your changes — `git commit -m 'feat: add amazing feature'`
4. Push to your branch — `git push origin feature/amazing-feature`
5. Open a Pull Request

### Code Standards

- Follow SOLID principles and keep changes scoped
- Add unit tests for new business logic
- Use conventional commit messages where possible
- Update documentation when behavior changes

---

## 📝 License

Licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

---

## 🆘 Support

- 📖 **Getting Started:** [QUICK_START.md](QUICK_START.md)
- 💳 **Payments:** [`docs/PAYMENT_INTEGRATION_GUIDE.md`](docs/PAYMENT_INTEGRATION_GUIDE.md)
- 🐛 **Issues:** [GitHub Issues](https://github.com/yourusername/warehouse-management/issues)

---

## 👥 Author

Developed by **Yusuf Ozkan** ([@ysfzkn](https://github.com/ysfzkn))

---

<div align="center">

**[⬆ Back to Top](#-warehouse--e-commerce-management-platform)**

Made with ☕ and lots of code

⭐ Star this repository if you find it helpful!

</div>
