# Gömülü fontlar

`NotoSans-Regular.ttf` / `NotoSans-Bold.ttf` — SIL Open Font License 1.1
(https://github.com/googlefonts/noto-fonts)

PDF üretimi için zorunlu. PDFBox'ın yerleşik Helvetica'sı WinAnsi (CP1252) ile
sınırlıdır ve Türkçe'nin `ı ğ ş İ Ğ Ş` harflerini **içermez** — teslimat makbuzunda
müşteri adı "Işık" yerine "Isik" ya da boş kutu olarak çıkardı. Çalışma zamanı
ortamının fontuna da güvenilemez: prod imajı `eclipse-temurin:21-jre-alpine`
üzerinde hiç font yüklü değil, dolayısıyla font uygulamayla birlikte gelmek zorunda.
