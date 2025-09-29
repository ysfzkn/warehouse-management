# Warehouse Management System

A modern, mobile-friendly warehouse management system designed for white goods companies. This system allows you to manage your warehouse products categorically and numerically, view and update stock status.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-brightgreen)
![React](https://img.shields.io/badge/React-18.2.0-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 🌟 Features

- **Multi-Warehouse Support**: Manage multiple warehouses
- **Product Management**: Product categorization and detailed product information
- **Stock Tracking**: Real-time stock tracking and alerts
- **Category Management**: Flexible category system
- **Mobile Responsive**: Perfect experience on mobile devices
- **Modern UI**: Stylish and user-friendly interface with Bootstrap
- **RESTful API**: Standard API endpoints
- **Docker Ready**: Containerized deployment
- **Real-time Dashboard**: Comprehensive statistics and reports

## 📋 Requirements

- **Docker & Docker Compose** (Recommended for PostgreSQL)
- **Java 17+** (If not using Docker)
- **Node.js 18+** (If not using Docker)
- **PostgreSQL 15+** (For production database)
- **Git**

## 🚀 Quick Start

### With Docker (Recommended)

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd warehouse-management
   ```

2. **Run the deploy script:**
   ```bash
   ./deploy.sh
   ```

3. **Use the system:**
   - **Frontend**: http://localhost
   - **Backend API**: http://localhost/api
   - **PostgreSQL Database**: localhost:5432
   - **pgAdmin (Database Management)**: http://localhost:5050
   - **H2 Console**: http://localhost:8080/h2-console (if using H2)

### Manual Installation

1. **Start the backend:**
   ```bash
   # Download Maven dependencies
   mvn clean install

   # Run the application
   mvn spring-boot:run
   ```

2. **Start the frontend:**
   ```bash
   cd frontend
   npm install
   npm start
   ```

## 📊 System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   React Frontend │◄──►│  Spring Boot    │◄──►│   H2 Database   │
│                 │    │   Backend API   │    │  (In-Memory)    │
│ - Responsive UI │    │                 │    │                 │
│ - Modern Design │    │ - REST API      │    │ - JPA Entities  │
│ - Real-time     │    │ - Business      │    │ - Relationships │
│   Updates       │    │   Logic         │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🏗️ Database Schema

### Categories
- `id`: Primary Key (SERIAL)
- `name`: Category name (VARCHAR(50), UNIQUE, NOT NULL)
- `description`: Description (TEXT)
- `created_at`: Creation date (TIMESTAMP, NOT NULL)
- `updated_at`: Update date (TIMESTAMP, NOT NULL)

### Warehouses
- `id`: Primary Key (SERIAL)
- `name`: Warehouse name (VARCHAR(100), NOT NULL)
- `location`: Location (VARCHAR(255), NOT NULL)
- `phone`: Phone (VARCHAR(20))
- `manager`: Manager (VARCHAR(100))
- `capacity_sqm`: Capacity (m²) (DECIMAL(10,2))
- `is_active`: Active status (BOOLEAN, DEFAULT true)
- `created_at`: Creation date (TIMESTAMP, NOT NULL)
- `updated_at`: Update date (TIMESTAMP, NOT NULL)

### Products
- `id`: Primary Key (SERIAL)
- `name`: Product name (VARCHAR(100), NOT NULL)
- `description`: Description (TEXT)
- `sku`: Stock code (VARCHAR(50), UNIQUE, NOT NULL)
- `price`: Price (DECIMAL(10,2), NOT NULL)
- `weight`: Weight (DECIMAL(8,2))
- `dimensions`: Dimensions (VARCHAR(50))
- `category_id`: Category reference (BIGINT, FOREIGN KEY, NOT NULL)
- `is_active`: Active status (BOOLEAN, DEFAULT true)
- `created_at`: Creation date (TIMESTAMP, NOT NULL)
- `updated_at`: Update date (TIMESTAMP, NOT NULL)

### Stocks
- `id`: Primary Key (SERIAL)
- `product_id`: Product reference (BIGINT, FOREIGN KEY, NOT NULL)
- `warehouse_id`: Warehouse reference (BIGINT, FOREIGN KEY, NOT NULL)
- `quantity`: Quantity (INTEGER, NOT NULL, DEFAULT 0)
- `min_stock_level`: Minimum stock level (INTEGER, NOT NULL, DEFAULT 0)
- `reserved_quantity`: Reserved quantity (INTEGER, NOT NULL, DEFAULT 0)
- `last_updated`: Last update (TIMESTAMP, NOT NULL)

**Constraints:**
- Unique constraint on (product_id, warehouse_id) in stocks table
- Foreign key constraints with CASCADE DELETE
- Check constraints for non-negative quantities

## 🔌 API Endpoints

### Categories
- `GET /api/categories` - List all categories
- `GET /api/categories/{id}` - Get category details
- `POST /api/categories` - Create new category
- `PUT /api/categories/{id}` - Update category
- `DELETE /api/categories/{id}` - Delete category

### Warehouses
- `GET /api/warehouses` - List all warehouses
- `GET /api/warehouses/{id}` - Get warehouse details
- `POST /api/warehouses` - Create new warehouse
- `PUT /api/warehouses/{id}` - Update warehouse
- `DELETE /api/warehouses/{id}` - Delete warehouse
- `PUT /api/warehouses/{id}/activate` - Activate warehouse
- `PUT /api/warehouses/{id}/deactivate` - Deactivate warehouse

### Products
- `GET /api/products` - List all products
- `GET /api/products/{id}` - Get product details
- `GET /api/products/sku/{sku}` - Find product by SKU
- `POST /api/products` - Create new product
- `PUT /api/products/{id}` - Update product
- `DELETE /api/products/{id}` - Delete product

### Stocks
- `GET /api/stocks` - List all stocks
- `GET /api/stocks/{id}` - Get stock details
- `GET /api/stocks/product/{productId}` - Get stocks by product
- `GET /api/stocks/warehouse/{warehouseId}` - Get stocks by warehouse
- `POST /api/stocks` - Create new stock record
- `PUT /api/stocks/{id}` - Update stock
- `PUT /api/stocks/{id}/add` - Add to stock
- `PUT /api/stocks/{id}/remove` - Remove from stock

## 🎨 User Interface

### Dashboard
- General statistics
- Low stock alerts
- Charts and graphs
- Quick access buttons

### Warehouse Management
- Warehouse list and details
- Stock viewing by warehouse
- Warehouse activation/deactivation

### Product Management
- Product list and search
- Category-based filtering
- Add/edit products

### Category Management
- Category hierarchy
- Product counts
- Category-based reports

### Stock Management
- Real-time stock tracking
- Low stock alerts
- Stock movements

## 🔧 Configuration

### Environment Variables (Backend)
```properties
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:h2:mem:warehouse_db
spring.datasource.username=sa
spring.datasource.password=password

# H2 Console (Development)
spring.h2.console.enabled=true
```

### Docker Environment
```yaml
# docker-compose.yml
backend:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
```

## 🐳 Docker Usage

### Development Environment
```bash
# Start all services (including pgAdmin)
docker-compose up -d

# View logs
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f pgadmin

# Stop services
docker-compose down
```

### Database Management with pgAdmin

**Access pgAdmin:**
- URL: http://localhost:5050
- Email: admin@warehouse.com
- Password: admin123

**Add PostgreSQL Server:**
1. Click "Add New Server"
2. General tab: Name = "Warehouse DB"
3. Connection tab:
   - Host: db (or localhost if running locally)
   - Port: 5432
   - Username: warehouse_user
   - Password: warehouse_pass
   - Database: warehouse_db

### Production Deployment
```bash
# Production build
docker-compose -f docker-compose.prod.yml up -d

# With SSL certificate
# Edit nginx/prod.conf file
```

## 🚀 Production Deployment

### VPS/Dedicated Server
1. Clone repository to server
2. Get SSL certificate (Let's Encrypt recommended)
3. Update `nginx/prod.conf` file
4. Run `./deploy.sh production` command

## ☁️ Cloud Database Comparison

### Free Tier Options

| Provider | Database | Free Tier | Limitations | Setup Difficulty |
|----------|----------|-----------|-------------|------------------|
| **Supabase** | PostgreSQL | ✅ 500MB + 2GB bandwidth | 50MB file storage | ⭐⭐⭐ (Easy) |
| **Neon** | PostgreSQL | ✅ 512MB storage | 100 hours compute | ⭐⭐ (Medium) |
| **ElephantSQL** | PostgreSQL | ✅ 20MB storage | Tiny plan only | ⭐⭐⭐ (Easy) |
| **Railway** | PostgreSQL | ✅ $5/month credit | Shared resources | ⭐⭐ (Medium) |
| **Render** | PostgreSQL | ❌ $7/month minimum | No free tier | ⭐⭐ (Medium) |
| **Vercel Postgres** | PostgreSQL | ✅ 1GB storage | 1000 row limit | ⭐⭐⭐⭐ (Easy) |

### Local vs Cloud Database

| Aspect | Local PostgreSQL | Cloud Database |
|--------|------------------|----------------|
| **Cost** | Free (your server) | Free tier available |
| **Maintenance** | You manage everything | Provider handles it |
| **Backup** | Manual setup needed | Automatic backups |
| **Scaling** | Limited to your server | Auto-scaling possible |
| **Security** | You configure | Built-in security |
| **Performance** | Depends on your server | Optimized infrastructure |
| **Setup Time** | 30 minutes | 5 minutes |

### Recommended Setup for Your Project

**Development:**
```bash
# Use Docker with PostgreSQL (free)
docker-compose up -d
```

**Production Small Project:**
```bash
# Supabase (free tier)
# 1. Create account at supabase.com
# 2. Create new project
# 3. Get connection string
# 4. Update application-postgres.properties
```

**Production Enterprise:**
```bash
# AWS RDS or Google Cloud SQL
# Higher cost but better performance and support
```

## 🚀 Railway Deployment (Adım Adım Rehber)

### Ön Hazırlık

**Railway'i Neden Seçtik?**
- ✅ $5 ücretsiz kredi (küçük projeler için yeterli)
- ✅ Built-in PostgreSQL
- ✅ Automatic HTTPS
- ✅ Custom domains
- ✅ GitHub integration
- ✅ Real-time logs
- ✅ Database backup

### Adım 1: Railway Hesabı Oluşturun

1. **Tarayıcıda** https://railway.app adresine gidin
2. **"Sign Up"** butonuna tıklayın
3. **GitHub** hesabınızla giriş yapın (önerilir)
4. **Email** adresinizi doğrulayın
5. **Dashboard**'a yönlendirileceksiniz

### Adım 2: Railway CLI'yı Kurun

```bash
# npm ile global kurulum
npm install -g @railway/cli

# Veya yarn ile
yarn global add @railway/cli

# Veya curl ile
curl -fsSL https://railway.app/install.sh | sh
```

**Kurulumu Doğrulayın:**
```bash
railway --version
```

### Adım 3: CLI ile Giriş Yapın

```bash
# Tarayıcıda açılan sayfada login yapın
railway login
```

### Adım 4: Proje Dizininize Gidin

```bash
# Proje klasörünüze gidin
cd warehouse-management

# İçeriği kontrol edin
ls -la
```

### Adım 5: Railway Projesi Başlatın

```bash
# Railway projesi oluşturun
railway init

# Bu komut şunları yapar:
# - Yeni bir proje oluşturur
# - Proje adını sorar (örn: warehouse-management)
# - Proje için dashboard sayfası açar
```

### Adım 6: PostgreSQL Veritabanı Ekleyin

```bash
# PostgreSQL servisi ekleyin
railway add postgresql

# Bu komut:
# - PostgreSQL database oluşturur
# - Database credentials'ını gösterir
# - Environment variables'ı otomatik ayarlar
```

### Adım 7: Environment Variables'ları Yapılandırın

Railway dashboard'da (https://railway.app) şu variables'ları ayarlayın:

1. **Project Settings** → **Variables** sekmesine gidin
2. **Aşağıdaki değişkenleri ekleyin:**

```bash
# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://[DATABASE-HOST]:5432/railway
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=[DATABASE-PASSWORD]
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# Application Configuration
SPRING_PROFILES_ACTIVE=production
```

**Not:** Database credentials'ını Railway size verdiği değerlerle değiştirin.

### Adım 8: Uygulamayı Deploy Edin

```bash
# Tüm servisleri deploy edin
railway up

# Bu komut şunları yapar:
# - Dockerfile'ları build eder
# - Backend ve frontend'i deploy eder
# - PostgreSQL'i bağlar
# - Public URL'leri gösterir
```

### Adım 9: Deployment'ı Doğrulayın

Railway dashboard'da:
1. **Services** sekmesine gidin
2. **3 servis görmelisiniz:**
   - `warehouse-management` (Backend)
   - `warehouse-management-frontend` (Frontend)
   - `postgresql-xxxxx` (Database)

3. **Her servisin "Status"** durumunu kontrol edin (yeşil olmalı)

### Adım 10: Database Migration

Backend ilk çalıştığında otomatik olarak tabloları oluşturacaktır. Eğer sorun yaşarsanız:

```bash
# Railway CLI ile logs'ı kontrol edin
railway logs

# Veya dashboard'dan logs'a bakın
```

### Adım 11: Public URL'leri Alın

Railway otomatik olarak public URL'ler verecektir:

- **Frontend**: https://warehouse-management.up.railway.app
- **Backend API**: https://warehouse-management.up.railway.app/api
- **Database**: PostgreSQL instance

### Adım 12: Custom Domain Ekleyin (İsteğe Bağlı)

1. **Domain** satın alın (örn: warehouse-company.com)
2. **Railway Dashboard** → **Settings** → **Domains**
3. **"Add Domain"** butonuna tıklayın
4. **Domain** adınızı girin
5. **CNAME** kaydını DNS sağlayıcınızda ayarlayın

### Adım 13: Database Yönetimi

**pgAdmin ile Railway PostgreSQL'e bağlanın:**

1. **pgAdmin**'i açın: http://localhost:5050
2. **"Add New Server"** butonuna tıklayın
3. **Connection** tab:
   - Host: [RAILWAY-HOST-ADRESİ]
   - Port: 5432
   - Username: postgres
   - Password: [RAILWAY-PASSWORD]
   - Database: railway

### Adım 14: Monitoring ve Logs

```bash
# Real-time logs
railway logs -f

# Spesifik servis logs
railway logs backend
railway logs frontend

# Railway dashboard'da da logs'ı görebilirsiniz
```

### Adım 15: Database Backup (Otomatik)

Railway otomatik olarak günlük backup alır:
- **Settings** → **Backups** sekmesinde görebilirsiniz
- Manuel backup da alabilirsiniz

### Adım 16: Scaling (Gerektiğinde)

Küçük projeler için ücretsiz tier yeterli. Büyüdüğünüzde:

```bash
# Servisleri scale edin
railway scale backend=2
railway scale frontend=2
```

### Troubleshooting

**Yaygın Sorunlar:**

1. **Build Hatası:**
```bash
# Logs'ı kontrol edin
railway logs

# Clean deploy
railway up --force
```

2. **Database Bağlantı Hatası:**
```bash
# Environment variables'ları kontrol edin
railway variables
```

3. **Port Hatası:**
```bash
# Railway otomatik port atar, sorun olmaz
```

### Maliyet Kontrolü

**Railway Dashboard** → **Usage** sekmesinde:
- Aylık kullanımınızı görün
- $5 krediniz yeterli mi kontrol edin
- Gerekiyorsa upgrade yapın

### Production Checklist

- ✅ HTTPS otomatik (Railway sağlar)
- ✅ Database backup otomatik
- ✅ Logs monitoring
- ✅ Environment variables güvenli
- ✅ Domain (isteğe bağlı)
- ✅ Database migration tamamlandı

**Railway Dashboard:** https://railway.app/dashboard

### Option 2: Render

**Free Tier:** Limited but works for small apps

1. **Connect GitHub Repository**
2. **Create Web Service** for frontend
3. **Create Web Service** for backend
4. **Add PostgreSQL** service
5. **Set Environment Variables**

### Option 3: Fly.io

**Free Tier:** Limited resources

```bash
# Install flyctl
curl -L https://fly.io/install.sh | sh

# Deploy
fly launch
fly deploy
```

### Option 4: Local VPS (Most Control)

**Requirements:** Old computer or cheap VPS ($5/month)

1. **Install Docker on your server**
2. **Clone repository**
3. **Run:** `docker-compose up -d`
4. **Configure domain/reverse proxy**

### Cost Comparison

| Platform | Free Tier | Monthly Cost (Small) | Features |
|----------|-----------|---------------------|----------|
| **Railway** | $5 credit | $5-10 | Excellent dashboard, auto-deploy |
| **Render** | Limited | $7-15 | Good for static sites |
| **Fly.io** | Limited | $5-10 | Global deployment |
| **Local VPS** | Your hardware | $5 VPS | Full control |

### Database Management Tools

**For Local PostgreSQL:**
- **pgAdmin**: http://localhost:5050 (Docker)
- **DBeaver**: Free desktop tool
- **psql**: Command line tool

**For Cloud Databases:**
- **Supabase Dashboard**: Built-in management
- **Railway Dashboard**: Database management
- **pgAdmin**: Can connect to cloud databases

## 📱 Mobile Compatibility

- Responsive Bootstrap design
- Touch-friendly interface
- Progressive Web App (PWA) support
- Mobile-first approach

## 🔒 Security

- CORS configuration
- Input validation
- SQL injection protection (JPA)
- XSS protection
- Security headers

## 📊 Performance

- Lazy loading
- Caching strategies
- Database indexing
- Optimized queries
- CDN support

## 🧪 Testing

### Backend Tests
```bash
mvn test
```

### Frontend Tests
```bash
cd frontend
npm test
```

## 📝 Logging

- Structured logging (Spring Boot)
- Request/Response logging
- Error tracking
- Performance monitoring

## 🔄 Backup & Recovery

### Database Backup (H2)
```bash
# CSV export via H2 console
http://localhost:8080/h2-console
```

### Docker Volumes
```bash
# Volume backup
docker run --rm -v warehouse_postgres_data:/data -v $(pwd):/backup alpine tar czf /backup/postgres_backup.tar.gz -C /data .
```

## 🤝 Contributing

1. Fork the project
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 🆘 Support

If you encounter any issues:

1. Check the [Issues](https://github.com/your-repo/issues) page
2. Open a new issue
3. Contact via email

## 👥 Developers

- **Backend**: Spring Boot, Java 17, REST API
- **Frontend**: React 18, Bootstrap 5, Chart.js
- **Database**: H2 (Development), PostgreSQL (Production)
- **DevOps**: Docker, Docker Compose, Nginx

## 🗺️ Roadmap

- [ ] Multi-language support (TR/EN)
- [ ] Advanced reporting
- [ ] Barcode/QR code integration
- [ ] Mobile app (React Native)
- [ ] Real-time notifications
- [ ] Advanced analytics
- [ ] Multi-tenant support
- [ ] API rate limiting

---

⭐ If you like this project, don't forget to give it a star!
