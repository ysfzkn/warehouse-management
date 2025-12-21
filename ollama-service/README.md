# Ollama Service

Bu servis Railway'de ayrı bir servis olarak çalışır ve Ollama sunucusunu sağlar.

## Özellikler

- Ollama sunucusunu otomatik başlatır
- Llama 3.1 modelini otomatik indirir (eğer yoksa)
- Port 11434'te servis sağlar

## Railway Deployment

1. Railway'de yeni bir servis oluştur
2. Bu klasörü root olarak seç
3. Railway otomatik olarak Dockerfile'ı kullanarak deploy edecek

## Model Değiştirme

Eğer model çok büyük gelirse, `entrypoint.sh` dosyasındaki `MODEL_NAME` değişkenini değiştirebilirsiniz:
- `llama3.1:8b` - 8B parametreli model
- `llama3.1:3b` - 3B parametreli model (daha küçük)

## Backend Bağlantısı

Backend, bu servise `OLLAMA_BASE_URL` environment variable'ı ile bağlanır.
Railway'de bu servisin public URL'ini backend'e environment variable olarak ekleyin.


