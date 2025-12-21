# Local Development - Ollama Kurulumu

Bu rehber, Cezeri AI asistanını local'de Ollama ile test etmek için gerekli adımları içerir.

## Hızlı Başlangıç

### 1. Ollama'yı İndirin ve Kurun

**Windows:**
1. https://ollama.ai adresine gidin
2. "Download" butonuna tıklayın
3. İndirilen `.exe` dosyasını çalıştırın ve kurulumu tamamlayın

**macOS:**
```bash
brew install ollama
```

**Linux:**
```bash
curl -fsSL https://ollama.ai/install.sh | sh
```

### 2. Ollama'yı Başlatın

Ollama kurulumundan sonra otomatik olarak başlar. Eğer başlamadıysa:

**Windows:**
- Ollama uygulamasını başlatın (Start Menu'den)

**macOS/Linux:**
```bash
ollama serve
```

Ollama varsayılan olarak `http://localhost:11434` adresinde çalışır.

### 3. Modeli İndirin

Terminal/PowerShell'de:

```bash
ollama pull llama3.1
```

Bu işlem birkaç dakika sürebilir (model ~4.7GB). İndirme tamamlandığında şu mesajı göreceksiniz:
```
pulling manifest
pulling 8f5a7b1556f3... 100% ▕████████████████▏ 4.7 GB
pulling 8e398af32171... 100% ▕████████████████▏ 1.0 KB
pulling 9d9a63c0b8c0... 100% ▕████████████████▏ 1.0 KB
pulling 1e83bfc5f5fa... 100% ▕████████████████▏ 1.0 KB
pulling 7c23fb36d801... 100% ▕████████_URL: http://localhost:11434
```

### 4. Modeli Test Edin

Modelin düzgün çalıştığını test edin:

```bash
ollama run llama3.1
```

Bir prompt yazın (örn: "Merhaba, nasılsın?") ve yanıt alın. Çıkmak için `Ctrl+C` veya `/bye` yazın.

### 5. Backend'i Ollama ile Çalıştırın

Backend'i başlatmadan önce, environment variable'ı ayarlayın:

**Windows PowerShell:**
```powershell
$env:AI_PROVIDER="ollama"
```

**Windows CMD:**
```cmd
set AI_PROVIDER=ollama
```

**macOS/Linux:**
```bash
export AI_PROVIDER=ollama
```

Ardından backend'i başlatın:

```bash
mvn spring-boot:run
```

veya IDE'nizden çalıştırın.

### 6. Test Edin

1. Backend'in başladığını kontrol edin: `http://localhost:8080/actuator/health`
2. Cezeri AI asistanını test edin - normal şekilde çalışmalıdır
3. Logları kontrol edin - "Initializing Cezeri ChatClient for users with provider: ollama" mesajını görmelisiniz

## Model Seçenekleri

### Llama 3.1 8B (Önerilen)
```bash
ollama pull llama3.1
```
- En iyi performans
- Tool calling desteği
- ~4.7GB disk alanı
- ~8GB RAM gerektirir

### Llama 3.1 3B (Daha Az Kaynak)
```bash
ollama pull llama3.1:3b
```
- Daha az bellek gerektirir
- Biraz daha düşük performans
- ~2GB disk alanı
- ~4GB RAM gerektirir

Modeli değiştirmek için `application-dev.properties` dosyasında veya environment variable ile:

```bash
export OLLAMA_MODEL=llama3.1:3b
```

## Sorun Giderme

### Ollama başlamıyor
- Ollama servisinin çalıştığından emin olun
- Port 11434'ün kullanılabilir olduğunu kontrol edin
- Windows'ta Ollama uygulamasını yönetici olarak çalıştırmayı deneyin

### Model bulunamıyor
- Modelin indirildiğinden emin olun: `ollama list`
- Model adını kontrol edin: `ollama show llama3.1`
- Modeli yeniden indirin: `ollama pull llama3.1`

### Backend Ollama'ya bağlanamıyor
- Ollama'nın çalıştığını kontrol edin: `curl http://localhost:11434/api/tags`
- `OLLAMA_BASE_URL` environment variable'ının doğru olduğundan emin olun
- Backend loglarını kontrol edin

### Yavaş yanıtlar
- Model büyüklüğüne göre yanıt süresi değişir
- 8B model için ilk yanıt 5-10 saniye sürebilir
- Sonraki yanıtlar genellikle daha hızlıdır
- Daha hızlı yanıt için 3B model'i deneyin

## Azure OpenAI'ye Geri Dönme

Local'de Azure OpenAI kullanmak isterseniz:

```bash
export AI_PROVIDER=azure
```

ve Azure OpenAI environment variable'larını ayarlayın:
- `AZURE_OPENAI_ENDPOINT`
- `AZURE_OPENAI_API_KEY`
- `AZURE_OPENAI_GPT51_DEPLOYMENT`

## Performans İpuçları

1. **GPU Kullanımı**: Ollama otomatik olarak GPU kullanır (eğer varsa)
2. **Model Önbelleği**: İlk kullanımdan sonra model bellekte kalır, daha hızlı yanıt verir
3. **Kaynak Kullanımı**: Model büyüklüğüne göre RAM kullanımı değişir
4. **Eşzamanlı İstekler**: Ollama varsayılan olarak tek seferde bir istek işler

## Faydalı Komutlar

```bash
# İndirilmiş modelleri listele
ollama list

# Model bilgilerini göster
ollama show llama3.1

# Modeli sil
ollama rm llama3.1

# Ollama servisini durdur
# Windows: Task Manager'dan kapatın
# macOS/Linux: Ctrl+C veya kill process
```

## Sonraki Adımlar

Local'de test ettikten sonra:
1. Railway'de Ollama servisini deploy edin
2. Backend'i Railway'de Ollama'ya bağlayın
3. Production'da test edin

Detaylar için `OLLAMA_DEPLOYMENT.md` dosyasına bakın.


