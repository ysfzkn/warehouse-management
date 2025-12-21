# Ollama Deployment Rehberi

Bu rehber, Cezeri AI asistanını Railway'de Ollama ile çalıştırmak için gerekli adımları içerir.

**Not**: Backend artık hem Azure OpenAI hem de Ollama'yı destekler. `AI_PROVIDER` environment variable'ı ile seçim yapılır.

## Genel Bakış

- **Ollama Servisi**: Railway'de ayrı bir servis olarak çalışır (port 11434)
- **Backend**: Ollama servisine bağlanarak AI işlemlerini gerçekleştirir
- **Model**: Llama 3.1 (8B parametreli) - eğer çok büyük gelirse 3B'ye geçilebilir
- **Provider Seçimi**: `AI_PROVIDER=ollama` veya `AI_PROVIDER=azure`

## Adım 1: Ollama Servisini Railway'e Deploy Etme

1. Railway dashboard'a giriş yapın
2. Yeni bir servis oluşturun
3. GitHub repository'nizi bağlayın
4. Root directory olarak `ollama-service` klasörünü seçin
5. Railway otomatik olarak Dockerfile'ı kullanarak deploy edecek
6. Deploy tamamlandıktan sonra, servisin public URL'ini not edin (örn: `https://ollama-service-production.up.railway.app`)

### Model Boyutu Sorunu

Eğer 8B model çok büyük gelirse ve servis başlatılamazsa:

1. `ollama-service/entrypoint.sh` dosyasını düzenleyin
2. `MODEL_NAME="llama3.1"` satırını `MODEL_NAME="llama3.1:3b"` olarak değiştirin
3. Railway'de servisi yeniden deploy edin

## Adım 2: Backend'i Ollama'ya Bağlama

1. Railway'de backend servisinizin ayarlarına gidin
2. Environment Variables bölümüne gidin
3. Aşağıdaki environment variable'ları ekleyin:

```
AI_PROVIDER=ollama
OLLAMA_BASE_URL=https://ollama-service-production.up.railway.app
OLLAMA_MODEL=llama3.1
```

**Not**: 
- `OLLAMA_BASE_URL` değerini kendi Ollama servisinizin URL'i ile değiştirin
- `AI_PROVIDER=ollama` ile Ollama kullanılır, `AI_PROVIDER=azure` ile Azure OpenAI kullanılır

## Adım 3: Backend'i Yeniden Deploy Etme

Backend servisini yeniden deploy edin. Artık Ollama servisini kullanacaktır.

## Yerel Geliştirme

Yerel geliştirme için detaylı rehber: `LOCAL_OLLAMA_SETUP.md`

Kısa özet:
1. Ollama'yı yerel olarak kurun: https://ollama.ai
2. Modeli indirin: `ollama pull llama3.1`
3. Ollama'yı başlatın: `ollama serve`
4. Backend'i `AI_PROVIDER=ollama` ile başlatın
5. `application-dev.properties` dosyasında varsayılan değerler zaten `http://localhost:11434` olarak ayarlanmıştır

## Test Etme

1. Backend servisinin health check endpoint'ini kontrol edin: `/actuator/health`
2. Cezeri AI asistanını test edin - normal şekilde çalışmalıdır
3. Logları kontrol edin - Ollama bağlantı hataları varsa görünecektir

## Sorun Giderme

### Ollama servisi başlamıyor
- Railway'de servisin loglarını kontrol edin
- Model indirme işlemi uzun sürebilir, sabırlı olun
- Eğer model çok büyükse, 3B model'e geçin

### Backend Ollama'ya bağlanamıyor
- `OLLAMA_BASE_URL` environment variable'ının doğru olduğundan emin olun
- Ollama servisinin public URL'inin erişilebilir olduğunu kontrol edin
- Backend loglarını kontrol edin

### Model bulunamıyor
- Ollama servisinin loglarını kontrol edin
- Model adının doğru olduğundan emin olun (`llama3.1` veya `llama3.1:8b`)
- Manuel olarak modeli indirmeyi deneyin: `ollama pull llama3.1`

## Maliyet

Bu çözüm tamamen ücretsizdir:
- Railway free tier kullanılabilir
- Ollama açık kaynak ve ücretsizdir
- Model indirme ve kullanım ücretsizdir

## Model Performansı

- **Llama 3.1 8B**: Daha iyi performans, daha fazla bellek gerektirir
- **Llama 3.1 3B**: Daha az bellek gerektirir, biraz daha düşük performans

Tool calling özelliği için Llama 3.1 önerilir.

