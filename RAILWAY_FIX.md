# Railway Deployment Hala Takılıyorsa

## Acil Çözüm Adımları

### 1. Mevcut Deployment'ı İptal Et
Railway Dashboard'da:
1. **Deployments** sekmesine git
2. **Çalışan (running/building) deployment**'a tıkla
3. **Sağ üstten "Cancel Deployment"** veya "Stop" butonuna bas
4. Onaylayıp bekle

### 2. Build Cache'i Temizle
Railway Dashboard'da:
1. **Settings** → **Builds** sekmesine git
2. **"Clear Build Cache"** butonuna bas
3. Onaylayıp bekle (30 saniye)

### 3. Environment Variables Kontrol
Railway Dashboard'da:
1. **Variables** sekmesine git
2. Şu değişkenlerin olduğundan emin ol:
```
DATABASE_URL=<postgresql url>
SPRING_PROFILES_ACTIVE=prod
PORT=8080
```

### 4. Builder Değiştir (İki Seçenek)

#### SEÇENEK A: NIXPACKS (Önerilen)
Railway Dashboard'da:
1. **Settings** → **Build & Deploy**
2. **Builder**: NIXPACKS seç
3. **Save**
4. Yeni deployment tetikle

#### SEÇENEK B: Simple Dockerfile
Railway Dashboard'da:
1. **Settings** → **Build & Deploy**
2. **Builder**: DOCKERFILE seç
3. **Dockerfile Path**: `Dockerfile.simple` yaz
4. **Save**
5. Yeni deployment tetikle

### 5. Manuel Trigger
```bash
git add .
git commit -m "Switch to NIXPACKS builder" --allow-empty
git push origin main
```

### 6. Railway CLI ile Debug (Opsiyonel)
```bash
# Railway CLI kur
npm i -g @railway/cli

# Login
railway login

# Link project
railway link

# Logs izle
railway logs -f
```

## Logs'ta Nelere Bakmalı

### Başarılı Build
```
✓ Building application with Maven
✓ Dependencies resolved
✓ Compilation successful
✓ Packaging JAR
✓ Build completed
✓ Starting container
✓ Application started on port 8080
```

### Sorunlu Build
```
✗ Maven timeout
✗ Out of memory
✗ Network error
✗ Dependency resolution failed
```

## Alternatif: Railway Volumes Sorunu

Eğer volumes mount sorunu varsa:
1. Railway Dashboard → **Volumes**
2. Tüm volume'ları **Unmount** et
3. Yeni deployment yap
4. Başarılı olduktan sonra volume'ları tekrar mount et

## Son Çare: Yeni Service Oluştur

Eğer hiçbiri işe yaramazsa:
1. Railway Dashboard'da **New** → **Empty Service**
2. Repository'yi bağla
3. Environment variables'ı kopyala
4. Database'i bağla
5. Deploy et
6. Eski service'i sil

## Hızlı Test

Build sorununu local'de test et:
```bash
# Docker build test
docker build -t test-app .

# Başarılı olursa container çalıştır
docker run -p 8080:8080 test-app
```

## Railway Limits Kontrol

Railway Dashboard → **Usage**:
- **Build time**: 30 dakika/build limit
- **Memory**: 512MB minimum (1GB önerilen)
- **CPU**: 0.5 vCPU minimum
- **Disk**: 10GB minimum

Plan limitlerini aşmış olabilirsiniz!

