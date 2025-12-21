# Resmi Ollama imajını kullan
FROM ollama/ollama:latest

# Scripti kopyala
COPY entrypoint.sh /entrypoint.sh

# Scripti çalıştırılabilir yap
RUN chmod +x /entrypoint.sh

# Ollama varsayılan portu
EXPOSE 11434

# Konteyner başladığında scripti çalıştır
ENTRYPOINT ["/entrypoint.sh"]


