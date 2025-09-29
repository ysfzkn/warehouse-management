# 🚀 Quick Start Guide

This guide provides step-by-step instructions to quickly get the Warehouse Management System up and running.

## 📋 Prerequisites

- **Docker & Docker Compose** (Recommended)
- **Git** (To clone the repository)

## 🛠️ Step 1: Prepare the Repository

```bash
# Clone the repository
git clone <repository-url>
cd warehouse-management

# Check file permissions
chmod +x deploy.sh
```

## 🚀 Step 2: Start the System

### Automatic Deployment (Recommended)

```bash
# Start the entire system with one command
./deploy.sh
```

This command:
- ✅ Builds Docker images
- ✅ Starts backend and frontend services
- ✅ Prepares the database
- ✅ Performs health checks
- ✅ Shows you access information

### Manual Installation (Alternative)

If you don't want to use Docker:

**Backend:**
```bash
# Install required dependencies
mvn clean install

# Start the application
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm start
```

## 🌐 Step 3: Access the System

Once deployment is successful, you can access these URLs:

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://localhost | Main application interface |
| **Backend API** | http://localhost/api | REST API endpoints |
| **H2 Console** | http://localhost:8080/h2-console | Database console |

## 🔑 Step 4: First Login

### Database Options

**Option 1: H2 (Development - Default):**
- **Console**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:mem:warehouse_db`
- **Username**: `sa`
- **Password**: `password`

**Option 2: PostgreSQL (Production):**
- **Host**: localhost
- **Port**: 5432
- **Database**: warehouse_db
- **Username**: warehouse_user
- **Password**: warehouse_pass
- **Management**: http://localhost:5050 (pgAdmin)

**pgAdmin ile Database Yönetimi:**
1. **pgAdmin'i Açın**: http://localhost:5050
2. **Giriş Yapın**:
   - Email: admin@warehouse.com
   - Password: admin123
3. **Server Ekleyin**:
   - "Add New Server" butonuna tıklayın
   - General tab: Name = "Warehouse DB"
   - Connection tab:
     - Host: localhost (veya db)
     - Port: 5432
     - Username: warehouse_user
     - Password: warehouse_pass
     - Database: warehouse_db
4. **Veritabanını İnceleyin**: Sol panelden tabloları görüntüleyin

### First Use

1. **Open** http://localhost in your browser
2. **View** general statistics on the Dashboard page
3. **Create** your first category in the Categories section
4. **Add** products in the Products section
5. **Define** warehouses in the Warehouses section
6. **Create** stock records in the Stock section

**Note:** The deploy script automatically builds the Maven project before starting Docker containers.

## 📱 Mobile Usage

The system is mobile-friendly:
- 📱 Open browser on your phone
- 🌐 Go to http://localhost
- 📱 Experience mobile interface with responsive design

## 🛑 Stopping the System

```bash
# Stop all services
docker-compose down

# Or just with the deployment script
./deploy.sh # (stops existing containers)
```

## 🔍 Troubleshooting

### Common Issues

**Port Conflict:**
```bash
# If port 8080 is in use
docker-compose down
# Change the port or close the other application
```

**Build Error:**
```bash
# Clean cache and rebuild
docker-compose build --no-cache
```

**API Connection Error:**
- Make sure backend service is running
- Check logs with `docker-compose logs backend`

### Viewing Logs

```bash
# View all service logs
docker-compose logs

# Only backend logs
docker-compose logs backend

# Only frontend logs
docker-compose logs frontend

# Real-time log tracking
docker-compose logs -f
```

## 🎯 Example Usage Scenarios

### Scenario 1: Adding a New Product

1. **Categories** → **New Category** → Create "White Goods"
2. **Products** → **New Product** → Add Samsung Refrigerator
3. **Warehouses** → **New Warehouse** → Create Main Warehouse
4. **Stock** → **New Stock Record** → Set up product-warehouse-stock relationship

### Scenario 2: Stock Tracking

1. **Dashboard** → View general statistics
2. **Stock** → Check low stock alerts
3. **Stock Adjustment** to update quantities

### Scenario 3: Reporting

1. **Dashboard** → Analyze overall status from charts
2. **Warehouses** → View stocks by warehouse
3. **Products** → Filter products by category

## 📊 Sample Data

The system uses H2 in-memory database so it starts clean each time. Sample data for testing:

```sql
-- Sample category
INSERT INTO categories (name, description) VALUES ('White Goods', 'Refrigerator, washing machine, etc.');

-- Sample warehouse
INSERT INTO warehouses (name, location, manager) VALUES ('Main Warehouse', 'Istanbul', 'Ahmet Yılmaz');

-- Sample product
INSERT INTO products (name, sku, price, category_id) VALUES ('Samsung Refrigerator', 'REF-001', 15000.00, 1);
```

## 🔧 Advanced Configuration

### Environment Variables

Edit environment variables in `docker-compose.yml`:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=production
  - LOG_LEVEL=DEBUG
```

### Custom Port

```bash
# Use different port
FRONTEND_PORT=3000 BACKEND_PORT=8081 docker-compose up
```

## 🚀 Production Deployment

### VPS/Dedicated Server

1. **Get SSL certificate** (Let's Encrypt recommended)
2. **Set up your domain**
3. **Edit** nginx/prod.conf file
4. **Run deploy script:**

```bash
# In production environment
ENV=production ./deploy.sh
```

## ☁️ Cloud Database Setup (Free Options)

### Option 1: Supabase (Recommended for Beginners)

**Free Tier:** 500MB database + 2GB bandwidth

1. **Create Account:**
   - Go to [supabase.com](https://supabase.com)
   - Sign up for free account

2. **Create Project:**
   - Click "New Project"
   - Choose your organization
   - Enter project name: "warehouse-management"
   - Set password (remember this!)
   - Choose region (closest to your users)

3. **Get Connection Details:**
   - Go to Settings → Database
   - Copy the connection string

4. **Update Configuration:**
   ```properties
   # In application-postgres.properties
   spring.datasource.url=jdbc:postgresql://[YOUR-DB-HOST]:5432/postgres
   spring.datasource.username=postgres
   spring.datasource.password=[YOUR-PASSWORD]
   ```

5. **Deploy:**
   ```bash
   # Update environment variables in docker-compose.yml
   docker-compose up -d
   ```

### Option 2: Neon (PostgreSQL)

**Free Tier:** 512MB storage

1. **Create Account:**
   - Go to [neon.tech](https://neon.tech)
   - Sign up for free account

2. **Create Database:**
   - Click "Create a project"
   - Choose free tier
   - Copy connection string

3. **Configure Application:**
   ```yaml
   # docker-compose.yml
   backend:
     environment:
       - SPRING_DATASOURCE_URL=jdbc:postgresql://[NEON-HOST]:5432/neondb
       - SPRING_DATASOURCE_USERNAME=[NEON-USER]
       - SPRING_DATASOURCE_PASSWORD=[NEON-PASSWORD]
   ```

### Cloud vs Local Comparison

| Feature | Local PostgreSQL | Cloud Database |
|---------|------------------|----------------|
| **Setup Time** | 30 minutes | 5 minutes |
| **Maintenance** | You handle updates/backups | Automatic |
| **Cost** | Free (your hardware) | Free tier available |
| **Backup** | Manual configuration | Automatic daily backups |
| **Scaling** | Limited to your server | Auto-scaling possible |
| **Security** | You configure | Enterprise-grade security |

**For Your White Goods Business:**
- **Start with**: Supabase (free, easy setup)
- **Scale up**: AWS RDS or Google Cloud SQL (when you need more power)
- **Backup**: Always enable automatic backups

## 🚀 Railway Deployment (Otomatik GitHub Integration)

### Neden Railway + GitHub?

Bu kombinasyonu seçtik çünkü:
- ✅ **Otomatik deployment** - Her push'ta otomatik deploy
- ✅ **$5 ücretsiz kredi** (küçük şirket için yeterli)
- ✅ **Built-in PostgreSQL** (ekstra kurulum gerektirmez)
- ✅ **Automatic HTTPS** (SSL sertifikası otomatik)
- ✅ **GitHub integration** (push-to-deploy)
- ✅ **Professional dashboard** (kolay yönetim)
- ✅ **Database backup** (otomatik yedekleme)

### ⚡ Otomatik Deployment Kurulumu

**GitHub Actions ile otomatik deployment için:**

1. **Bu repository'yi fork'layın** veya kendi GitHub repo'nuzu oluşturun
2. **Railway CLI ile bağlanın:**
   ```bash
   railway login
   railway link <github-repo-url>
   ```
3. **PostgreSQL ekleyin:**
   ```bash
   railway add postgresql
   ```
4. **GitHub Secrets ayarlayın:**
   - Repository Settings → Secrets and variables → Actions
   - `RAILWAY_TOKEN` secret'i ekleyin (Railway dashboard'dan alınacak)
5. **Environment variables'ı ayarlayın** (Railway dashboard'da)
6. **Push yapın** ve otomatik deployment'ı izleyin!

**GitHub Workflow Dosyası:**
- `.github/workflows/railway-deploy.yml` dosyası otomatik deployment'ı yönetir
- Her `main` branch push'unda çalışır
- Java ve Node.js build'lerini yapar
- Railway'e deploy eder

### Adım 1: Railway Hesabı Oluşturun

1. **Tarayıcıda** https://railway.app adresine gidin
2. **"Sign Up"** butonuna tıklayın
3. **GitHub** hesabınızla giriş yapın (önerilir)
   - Eğer GitHub hesabınız yoksa email ile de kaydolabilirsiniz
4. **Email** adresinizi doğrulayın
5. **Dashboard**'a yönlendirileceksiniz

### Adım 2: Railway CLI'yı Kurun

**Windows için:**
```bash
# PowerShell'de çalıştırın
npm install -g @railway/cli
```

**Veya alternatif yöntem:**
```bash
# Scoop ile (Windows package manager)
scoop install railway

# Veya Chocolatey ile
choco install railway
```

**Kurulumu test edin:**
```bash
railway --version
# Çıktı: railway/3.x.x
```

### Adım 3: CLI ile Giriş Yapın

```bash
# Bu komut tarayıcı açacak ve giriş yapmanızı isteyecek
railway login
```

**Giriş başarılı olursa:**
```
✓ Logged in to Railway
```

### Adım 4: Proje Klasörüne Gidin

```bash
# Proje klasörünüze gidin
cd "C:\Users\asus\Desktop\code\Out of work\warehouse-management\warehouse-management"

# Veya Windows Explorer'da klasöre sağ tıklayıp
# "PowerShell penceresi burada aç" seçin

# İçeriği kontrol edin
dir
```

### Adım 5: Railway Projesi Başlatın

```bash
# Railway projesi oluşturun
railway init

# Bu komut şunları soracak:
# - Proje adı? (örn: warehouse-management)
# - Hangi region? (US West önerilir)
```

**Beklenen çıktı:**
```
✓ Created project warehouse-management
✓ Opening project in browser...
```

### Adım 6: PostgreSQL Veritabanı Ekleyin

```bash
# PostgreSQL servisi ekleyin
railway add postgresql

# Bu komut otomatik olarak:
# - PostgreSQL database oluşturur
# - Database credentials'ını gösterir
# - Environment variables'ı ayarlar
```

**Beklenen çıktı:**
```
✓ Added PostgreSQL to project
✓ Database URL: postgresql://user:pass@host:5432/db
✓ Set as DATABASE_URL environment variable
```

### 🔗 Railway Networking Seçenekleri

Railway'de PostgreSQL kurduğunuzda iki networking seçeneği görürsünüz:

| Networking Type | Hostname | Kullanım | Güvenlik |
|-----------------|----------|----------|----------|
| **Public** | `yamanote.proxy.rlwy.net:16716` | External erişim (localhost'tan) | Daha az güvenli |
| **Private** | `postgres.railway.internal` | Railway içindeki servisler arası | Daha güvenli |

**✅ Doğru Seçim: Private Networking**
- Backend Railway'de çalışacağı için `postgres.railway.internal:5432` kullanın
- Public networking sadece external araçlarla bağlanmak için gereklidir

### Adım 7: Environment Variables'ları Ayarlayın

**Railway Dashboard'da (https://railway.app/dashboard):**

1. **Project** → **Variables** sekmesine gidin
2. **"Add Variable"** butonuna tıklayın
3. **Aşağıdaki değişkenleri ekleyin:**

```bash
# Railway PostgreSQL Configuration
DATABASE_URL=postgresql://postgres:[PASSWORD]@postgres.railway.internal:5432/railway
SPRING_PROFILES_ACTIVE=prod

# Optional: Manual configuration (if DATABASE_URL doesn't work)
# SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.railway.internal:5432/railway
# SPRING_DATASOURCE_USERNAME=postgres
# SPRING_DATASOURCE_PASSWORD=[GIVEN-PASSWORD]
```

**⚠️ Önemli Notlar:**

- **DATABASE_URL** environment variable'ını kullanın (Railway'in otomatik oluşturduğu)
- **Private Networking kullanın:** `postgres.railway.internal:5432`
- **SPRING_PROFILES_ACTIVE=prod** ile `application-prod.properties` dosyası aktif olur
- **Password'ü** Railway'in verdiği değerle değiştirin

### Adım 8: GitHub Workflow ile Otomatik Deployment

**Otomatik deployment için GitHub Actions kullanıyoruz:**

1. **Bu repository'yi GitHub'a push'layın**
2. **Railway otomatik olarak algılayacak** ve deploy edecek
3. **Her push'ta yeniden build** ve deploy edecek
4. **GitHub Actions logs'undan** süreci takip edin

### Alternatif: Manuel Deployment

```bash
# Manuel deploy için
railway up

# Bu komut şunları yapar:
# - Dockerfile'daki multi-stage build ile backend'i build eder
# - Frontend'i build eder (React)
# - PostgreSQL'e bağlar
# - Public URL'leri verir
# - Health check yapar (curl ile)
```

**Beklenen çıktı:**
```
✓ Built backend
✓ Built frontend
✓ Deployed to https://warehouse-management.up.railway.app
✓ Database connected
```

### Adım 9: Deployment'ı Doğrulayın

**Railway Dashboard'da:**

1. **Services** sekmesine gidin
2. **3 servis görmelisiniz:**
   - `warehouse-management` (Backend - Java)
   - `warehouse-management-frontend` (Frontend - React)
   - `postgresql-xxxxx` (Database)

3. **Her servisin durumu yeşil** olmalı (running)

### Adım 10: Public URL'leri Test Edin

**Tarayıcıda açın:**
- **Frontend**: https://warehouse-management.up.railway.app
- **Backend API**: https://warehouse-management.up.railway.app/api

**Test komutları:**
```bash
# API test edin
curl https://warehouse-management.up.railway.app/api/categories

# Health check
curl https://warehouse-management.up.railway.app/api/actuator/health
```

### Adım 11: Database Migration

Backend otomatik olarak tabloları oluşturacaktır. Eğer sorun yaşarsanız:

```bash
# Logs'ı kontrol edin
railway logs backend

# Database bağlantısını test edin
railway run backend java -jar app.jar --spring.datasource.url=$DATABASE_URL
```

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
3. **Connection** tabında:
   - Host: [RAILWAY-HOST-ADRESİ]
   - Port: 5432
   - Username: postgres
   - Password: [RAILWAY-PASSWORD]
   - Database: railway

### Adım 14: Monitoring

```bash
# Real-time logs
railway logs -f

# Spesifik servis
railway logs backend
railway logs frontend

# Database logs
railway logs postgresql-xxxxx
```

### Adım 15: Backup ve Recovery

Railway otomatik backup alır:
- **Dashboard** → **Backups** sekmesinde görebilirsiniz
- Manuel backup da alabilirsiniz

### Troubleshooting

**Yaygın Sorunlar:**

1. **Build Hatası:**
```bash
railway logs backend
# Java versiyonunu kontrol edin
railway run backend java -version
```

2. **Database Bağlantı Hatası:**
```bash
railway variables
# Environment variables'ları kontrol edin
```

3. **Port Çakışması:**
```bash
# Railway otomatik port atar, sorun olmaz
```

### Maliyet Kontrolü

**Dashboard** → **Usage** sekmesinde:
- Aylık kullanımınızı görün
- $5 krediniz ne kadar kaldı
- Gerekiyorsa upgrade yapın ($5/ay'dan başlayan planlar)

### Production Checklist

- ✅ HTTPS otomatik (Railway sağlar)
- ✅ Database backup otomatik
- ✅ Logs monitoring
- ✅ Environment variables güvenli
- ✅ Custom domain (isteğe bağlı)
- ✅ Database tabloları oluşturuldu

**Başarı Örneği:**
- Frontend: https://warehouse-management.up.railway.app
- API: https://warehouse-management.up.railway.app/api
- Database: PostgreSQL (Railway managed)
- Maliyet: $0-5/ay

### Sonraki Adımlar

1. **Test edin** - Tüm API endpoint'leri çalışmalı
2. **Veri girin** - Kategoriler, ürünler, depolar ekleyin
3. **Domain ekleyin** - Profesyonel görünüm için
4. **Monitor edin** - Logs ve usage'i takip edin
5. **Scale edin** - Büyüdüğünüzde Railway ile kolay scaling

**Destek:** Herhangi bir sorun yaşarsanız Railway Discord veya documentation'a bakın.

**Railway Dashboard**: https://railway.app/dashboard

### 2. Render (Alternatif)

1. **GitHub Repository** bağlayın
2. **Web Service** oluşturun (frontend için)
3. **Web Service** oluşturun (backend için)
4. **PostgreSQL** servisi ekleyin
5. **Environment Variables** ayarlayın

### 3. Local VPS (En Kontrollü)

**Gereksinim:** Eski bilgisayar veya $5/ay VPS

1. **Sunucuya Docker** kurun
2. **Repository'yi klonlayın**
3. **`docker-compose up -d`** çalıştırın
4. **Domain/reverse proxy** yapılandırın

### Maliyet Karşılaştırması

| Platform | Ücretsiz Tier | Aylık Maliyet (Küçük) | Özellikler |
|----------|---------------|----------------------|-----------|
| **Railway** | $5 kredi | $5-10 | Mükemmel dashboard, auto-deploy |
| **Render** | Sınırlı | $7-15 | Static siteler için iyi |
| **Fly.io** | Sınırlı | $5-10 | Global deployment |
| **Local VPS** | Donanımınız | $5 VPS | Tam kontrol |

## 📞 Support

If you encounter any issues:

1. **Check logs**: `docker-compose logs`
2. **View container status**: `docker-compose ps`
3. **Perform health check**: `curl http://localhost/health`
4. **Read documentation**: [README.md](README.md)
5. **Open an issue** or send email

## 🎉 Success!

You have successfully started the system! Now you can perform your warehouse management operations digitally.

**First Steps:**
- 🔍 Explore the Dashboard
- 📦 Add your first product
- 🏪 Define your warehouses
- 📊 Review reports

Happy using! 🚀
