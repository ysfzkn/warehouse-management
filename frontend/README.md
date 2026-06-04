<div align="center">

# 🎨 Warehouse & E-Commerce Platform — Frontend

**React SPA powering both the admin warehouse panel and the customer-facing e-commerce storefront**

[![React](https://img.shields.io/badge/React-18.2.0-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org/)
[![React Router](https://img.shields.io/badge/React%20Router-6.11-CA4245?style=for-the-badge&logo=reactrouter&logoColor=white)](https://reactrouter.com/)
[![Bootstrap](https://img.shields.io/badge/Bootstrap-5.2.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![Chart.js](https://img.shields.io/badge/Chart.js-4.3.0-FF6384?style=for-the-badge&logo=chart.js&logoColor=white)](https://www.chartjs.org/)

</div>

---

## 🎯 Overview

This single React app serves **two distinct experiences** sharing the same codebase:

- 🛠️ **Admin Panel** — Warehouse, stock, product, order, customer, payment, CMS and support management
- 🛍️ **Customer Storefront** — Public catalog, cart, checkout, wishlist, account & order tracking

It talks to the Spring Boot backend (Java 21 / Spring Boot 3.3.5) via REST + JWT.

---

## ✨ Features

### 🛠️ Admin Panel
- 📊 **Sales & Stock Dashboard** — Live KPIs, low-stock alerts, interactive charts
- 📦 **Catalog Management** — Products, categories, brands, colors with SKU tracking
- 🏭 **Multi-Warehouse Inventory** — Stock CRUD, transfers, adjustments, reservations
- 🛒 **Order & Cargo Management** — Order list, status updates, cargo tracking
- 👤 **Customer Management** — Customer list, status control, address management
- 💳 **Payment Configuration** — Gateway selection (iyzico / PayTR / NestPay / Bank Transfer / Door)
- 🎟️ **Coupons & Discounts** — Validity windows, usage tracking
- 📝 **CMS & Banners** — Homepage banners, About / Terms / Privacy pages
- 🆘 **Support Tickets** — Customer support inbox
- 🔒 **Audit Logs** — Security & action history

### 🛍️ Customer Storefront
- 🏠 **Hero Banners & CMS Pages** — Editable from admin
- 🔎 **Product Browsing** — Category, brand, color, price filters
- 🖼️ **Product Gallery & Specs** — Multi-image gallery with stock badges
- 🛒 **Persistent Cart** — Add/remove, quantity updates, real-time totals
- ❤️ **Wishlist** — Favorites with persistent state
- 💳 **Multi-Step Checkout** — Address → Shipping → Payment
- 💰 **Multi-Gateway Payments** — iyzico Checkout Form, PayTR, Bank Transfer, Cash on Delivery
- 📦 **Order Tracking** — Order history with status timeline
- 👤 **Customer Auth** — Email/password + Google OAuth, address book

### 💎 Technical
- ✅ **Fully Responsive** — Mobile, tablet, desktop optimized
- ✅ **SEO-ready** — `react-helmet-async` for meta tags & Open Graph
- ✅ **Code Splitting** — Lazy-loaded routes
- ✅ **Reusable Components** — Skeletons, toasts, modals, breadcrumbs
- ✅ **Context API** — Wishlist & cart state

---

## 🚀 Quick Start

### Prerequisites
- **Node.js 18+**
- **npm** (or yarn)
- Backend running on `http://localhost:8080` (see [main README](../README.md))

### Installation

```bash
npm install
npm start              # dev server → http://localhost:3000
npm run build          # production build
```

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **React** | 18.2.0 | UI framework |
| **React Router** | 6.11.0 | Client-side routing |
| **Bootstrap** | 5.2.3 | CSS framework |
| **React Bootstrap** | 2.7.4 | Bootstrap components |
| **Axios** | 1.4.0 | HTTP client |
| **Chart.js** + `react-chartjs-2` | 4.3.0 / 5.2.0 | Dashboard analytics |
| **React Icons** | 4.10.1 | Icon library |
| **React Helmet Async** | 3.0.0 | SEO meta management |
| **TypeScript** | 4.9.5 | (dev tooling) |

---

## 📁 Project Structure

```
frontend/
├── public/
│   ├── index.html
│   └── company-logo.png
│
├── src/
│   ├── components/
│   │   ├── store/                  # 🛍️ Storefront UI
│   │   │   ├── StoreHeader.js
│   │   │   ├── StoreFooter.js
│   │   │   ├── MobileNav.js
│   │   │   ├── HeroBanner.js
│   │   │   ├── ProductCard.js
│   │   │   ├── ProductGrid.js
│   │   │   ├── ProductFilters.js
│   │   │   ├── ProductGallery.js
│   │   │   ├── ProductSpecs.js
│   │   │   ├── PriceDisplay.js
│   │   │   ├── StockBadge.js
│   │   │   ├── CartSidebar.js
│   │   │   ├── CheckoutStepper.js
│   │   │   ├── AddressForm.js
│   │   │   ├── IyzicoCheckoutForm.js
│   │   │   ├── BankTransferInfo.js
│   │   │   ├── WishlistContext.js
│   │   │   ├── Breadcrumb.js
│   │   │   ├── Skeleton.js
│   │   │   └── Toast.js
│   │   │
│   │   └── (admin)                 # 🛠️ Admin panel UI
│   │       ├── Navbar.js
│   │       ├── ProductForm.js
│   │       ├── StockForm.js
│   │       ├── WarehouseForm.js
│   │       ├── CategoryForm.js
│   │       ├── StockModal.js
│   │       ├── StockAdjustmentModal.js
│   │       ├── StockTransferModal.js
│   │       ├── ConfirmModal.js
│   │       ├── NotesModal.js
│   │       ├── FilterChips.js
│   │       └── SearchableSelect.js
│   │
│   ├── pages/
│   │   ├── store/                  # Storefront pages (catalog, cart, checkout, account…)
│   │   ├── admin/                  # Admin pages (orders, customers, payments, CMS…)
│   │   ├── Login.js                # Admin login
│   │   ├── Dashboard.js            # Admin dashboard
│   │   ├── Products.js
│   │   ├── Stock.js
│   │   ├── Warehouses.js
│   │   ├── Categories.js
│   │   ├── AdminSettings.js
│   │   └── DesiCalculator.js
│   │
│   ├── App.js
│   ├── config.js                   # API base URL
│   ├── App.css
│   └── index.js
│
├── package.json
├── Dockerfile                      # Multi-stage Nginx build
└── nginx.conf
```

---

## ⚙️ Configuration

### API Base URL

`src/config.js`:

```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
export const API_URL = `${API_BASE_URL}/api`;
```

### Environment Variables

Create `.env`:

```env
REACT_APP_API_URL=http://localhost:8080
```

### Dev Proxy

`package.json`:

```json
{ "proxy": "http://localhost:8080" }
```

---

## 📱 Responsive Design

| Breakpoint | Width |
|---|---|
| Mobile | < 576px |
| Tablet | 576 – 768px |
| Desktop | 768 – 992px |
| Large Desktop | > 992px |

Mobile features: touch-optimized buttons, collapsible nav (`MobileNav`), responsive tables, mobile-first checkout & forms.

---

## 🚀 Development

### Available Scripts

```bash
npm start         # dev server (http://localhost:3000)
npm test          # tests (React Testing Library + Jest)
npm run build     # production build → build/
npm run eject     # ⚠️ not recommended
```

### Tips
- Hot reload enabled by default
- Use React DevTools for component inspection
- Network tab → debug API calls
- Backend must be running on `:8080` (see main README)

---

## 🏗️ Build & Deployment

### Production Build

```bash
npm run build
```

Outputs minified JS, optimized CSS, compressed assets to `build/`.

### Docker

```bash
docker build -t warehouse-frontend .
docker run -p 80:80 warehouse-frontend
```

The bundled `Dockerfile` does a multi-stage build (Node 18 → Nginx Alpine) and the included `nginx.conf` proxies `/api/**` to the backend.

### Static Hosting

The `build/` folder can be deployed to Netlify, Vercel, AWS S3 + CloudFront, GitHub Pages, or any static host. Remember to set `REACT_APP_API_URL` at build time.

---

## 🧪 Testing

```bash
npm test                  # interactive watch
npm test -- --coverage    # with coverage
npm test -- --watchAll=false  # CI mode
```

Stack: `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`.

---

## 🔍 Troubleshooting

**Port already in use**
```bash
npx kill-port 3000
# or
PORT=3001 npm start
```

**Build errors**
```bash
rm -rf node_modules package-lock.json
npm install
```

**API connection errors**
- Backend running on `:8080`?
- CORS allowed origins includes `http://localhost:3000`?
- For admin endpoints: JWT token in `Authorization: Bearer <token>` header
- Check browser console & Network tab

**Slow performance**
- Clear browser cache
- `React.memo` for heavy components
- Optimize images (use the storefront's `<ProductGallery>` patterns)

---

## 🤝 Contributing

1. Create a feature branch
2. Make changes (run `npm test` & `npm run build`)
3. Submit a pull request

See [CONTRIBUTING.md](../CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

MIT License — see [LICENSE](../LICENSE) for details.

---

## 🆘 Support

- 📖 **Main Docs:** [../README.md](../README.md)
- 🚀 **Quick Start:** [../QUICK_START.md](../QUICK_START.md)
- 💳 **Payment Integration:** [../docs/PAYMENT_INTEGRATION_GUIDE.md](../docs/PAYMENT_INTEGRATION_GUIDE.md)
- 🐛 **Issues:** [GitHub Issues](https://github.com/yourusername/warehouse-management/issues)

---

<div align="center">

**Developed by Yusuf Ozkan ([@ysfzkn](https://github.com/ysfzkn))**

Built with ❤️ using React

[⬆ Back to Top](#-warehouse--e-commerce-platform--frontend)

</div>
