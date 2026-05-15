# Image Storage Migration Guide (Local → S3/R2)

## Mevcut Durum

`storage.provider=local` (varsayılan) — tüm ürün görselleri, transfer fotoğrafları
ve site asset'leri sunucu disk'inde tutuluyor (Railway volume). Bu yaklaşım
**tek instance + küçük katalog** için yeterli.

## Ne Zaman S3/R2'ye Geçilir?

- Storage volume %70'i geçtiğinde
- 2+ uygulama instance'ı çalıştırmak gerektiğinde (volume single-writer)
- CDN ile global cache + edge delivery istendiğinde
- Backup-restore prosedürünü basitleştirmek için

## Önerilen Sağlayıcılar (Türkiye dostu)

| Sağlayıcı | Maliyet | Egress | Türkiye PoP |
|-----------|---------|--------|-------------|
| **Cloudflare R2** | ~$0.015/GB/ay storage | **Egress ücretsiz** | İstanbul PoP var |
| AWS S3 | ~$0.023/GB/ay | $0.09/GB | Yakın: Frankfurt/Bahreyn |
| Backblaze B2 | ~$0.005/GB/ay | $0.01/GB | EU-Central |
| MinIO (self-hosted) | sadece sunucu | sıfır | kendi alt yapı |

**Önerilen:** Cloudflare R2 (egress ücretsiz, S3-compatible API).

## Migration Adımları

### 1. R2 Bucket Oluştur
- Cloudflare dashboard → R2 → Create bucket: `magaza-images`
- API token üret (R2 Object Storage permissions: Read+Write)
- Custom domain bağla: `cdn.siteniz.com` → bucket

### 2. Mevcut Dosyaları Migrate Et
```bash
# Rclone ile bulk transfer
rclone copy /var/lib/uploads/ r2:magaza-images/ --progress
```

### 3. Backend Bağımlılığı Ekle (pom.xml)
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.27.0</version>
</dependency>
```

### 4. S3PhotoStorageService İmplementasyonu (yeni dosya)
```java
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3")
public class S3PhotoStorageService implements PhotoStorageService {
    // S3Client.builder().endpointOverride(URI.create(STORAGE_S3_ENDPOINT))...
    // bucket.putObject(...), bucket.getObject(...) wrap edilir.
    // Thumbnail Sharp/ImageMagick yerine on-the-fly Cloudflare Image Resizing.
}
```

### 5. Env Vars (Railway)
```
STORAGE_PROVIDER=s3
STORAGE_S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
STORAGE_S3_BUCKET=magaza-images
STORAGE_S3_ACCESS_KEY=<from R2 token>
STORAGE_S3_SECRET_KEY=<from R2 token>
STORAGE_S3_PUBLIC_BASE_URL=https://cdn.siteniz.com   # custom domain
```

### 6. Cutover Stratejisi
1. **Phase A — dual-write:** Her yeni image hem local hem R2'ye yazılır (kısa süre)
2. **Phase B — dual-read:** Önce R2'den oku, yoksa local fallback
3. **Phase C — read-only-R2:** Backfill tamamlandı, sadece R2 kullan
4. **Phase D — cleanup:** Local volume temizlenir

### 7. CDN Cache & Image Resizing
Cloudflare R2 + Cloudflare Image Resizing kombinasyonu ile:
- `cdn.siteniz.com/products/123.jpg?width=300&format=webp`
- Otomatik WebP/AVIF dönüşümü
- Lazy loading + responsive `srcset` (frontend zaten hazır)

## Geri Dönüş Planı

S3/R2 down olursa:
1. Local backup'tan son snapshot'ı restore et
2. `STORAGE_PROVIDER=local` set et
3. Restart
4. Yeni upload'lar local'e gider; mevcut R2 referansları 404 verir → fallback URL

## İlgili Dosyalar

- `src/main/java/com/warehouse/service/PhotoStorageService.java` — interface
- `src/main/java/com/warehouse/service/impl/LocalPhotoStorageService.java` — mevcut
- `src/main/java/com/warehouse/service/impl/S3PhotoStorageService.java` — **yapılacak**
- `src/main/resources/application.properties` — `storage.provider`, `storage.s3.*`
