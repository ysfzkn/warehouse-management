#!/bin/bash

# Ollama sunucusunu arka planda başlat
/bin/ollama serve &

# Sunucunun uyanmasını bekle
pid=$!
sleep 5

# Modelin daha önce indirilip indirilmediğini kontrol et
# Llama 3.1 Tool Calling için en iyisidir
# Eğer 8B çok büyük gelirse, "llama3.1:3b" kullanabilirsiniz
MODEL_NAME="${OLLAMA_MODEL:-llama3.1}"

echo "🔴 Model kontrol ediliyor: $MODEL_NAME..."

if ollama list | grep -q "$MODEL_NAME"; then
    echo "🟢 Model zaten mevcut, indirme atlanıyor."
else
    echo "🟡 Model bulunamadı. İndiriliyor: $MODEL_NAME (Bu işlem biraz sürebilir)..."
    ollama pull $MODEL_NAME
    echo "🟢 Model başarıyla indirildi!"
fi

# Arka plandaki işlemi beklemeye devam et (Konteynerin kapanmaması için)
wait $pid

