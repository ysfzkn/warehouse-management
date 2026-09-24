-- Rakip ve üretici sitelerinden kopyalanmış tanıtım cümleleri ürün açıklamalarından silinir.
--
-- Açıklamalar dışarıdan toplanırken pazaryeri şablonlarıyla birlikte gelmiş: "… ürününü idefix
-- kalitesiyle satın almak için hemen tıklayın! Tüm Buzdolapları ürünleri için idefix`i ziyaret
-- edin." Kısa açıklama, meta açıklama boşken Google'da ürünün altında görünen metin oluyor;
-- yani ürünler aramada rakibe yönlendiren bir cümleyle çıkıyordu. Metin başka sitelerde de
-- birebir bulunduğu için kopya içerik de sayılıyor.
--
-- Kural (cümle bazlı): içinde "idefix", "tıklayın", "…com.tr'de" ya da "yorumlarını inceleyin"
-- geçen cümle silinir, diğer cümleler aynen kalır. Hiç cümle kalmayan alan NULL olur; o zaman
-- meta açıklama site geneli açıklamaya düşer. Liste canlı veriden üretildi ve uygulanmadan önce
-- mağaza sahibince ürün ürün onaylandı (100 ürün, 150 alan, 91'i boşalıyor).
--
-- Her UPDATE yalnızca alan onaylanan eski metinle birebir aynıysa çalışır: bu arada panelden
-- düzeltilmiş bir açıklamanın üzerine yazılmaz. version artırılır ki o anda panelde açık kalmış
-- bir ürün formu eski metni geri yazamasın (@Version iyimser kilidi kaydı reddeder).
-- updated_at güncellenir; sitemap'teki lastmod değişir ve sayfalar yeniden taranır.

-- -9-fonksiyon-qs-siyah-cam-ankastre-firin-power-turbo-dijital-479
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 479
   AND short_description = 'Simfer 8208 0+8 Fonksiyon QS Siyah Cam Ankastre Fırın Power Turbo Dijital ürünü Simfer.com.tr''de. Simfer 8208 0+8 Fonksiyon QS Siyah Cam Ankastre Fırın Power Turbo Dijital ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 8208 0+8 Fonksiyon QS Siyah Cam Ankastre Fırın Power Turbo Dijital ürünü Simfer.com.tr''de. Simfer 8208 0+8 Fonksiyon QS Siyah Cam Ankastre Fırın Power Turbo Dijital ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- 80litre-buro-tipi-buzdolabi-beyaz-484
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 484
   AND short_description = 'Simfer SR 2513 E Enerji Sınıfı 80 Lt Statik Büro Tipi Buzdolabı ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Buzdolapları ürünleri için idefix`i ziyaret edin.'
   AND description = 'Simfer SR 2513 E Enerji Sınıfı 80 Lt Statik Büro Tipi Buzdolabı yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- 294litre-cift-kapili-statik-buzdolabi-beyaz-485
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 485
   AND short_description = 'Simfer 2515 294Litre Çift Kapılı Statik Buzdolabı Beyaz ürünü Simfer.com.tr''de. Simfer 2515 294Litre Çift Kapılı Statik Buzdolabı Beyaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- -9-fonksiyon-qs-gri-cam-ankastre-firin-power-turbo-pop-up-486
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 486
   AND short_description = 'Simfer 8222 0+8 Fonksiyon QS Gri Cam Ankastre Fırın Power Turbo Pop-up ürünü Simfer.com.tr''de. Simfer 8222 0+8 Fonksiyon QS Gri Cam Ankastre Fırın Power Turbo Pop-up ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 8222 0+8 Fonksiyon QS Gri Cam Ankastre Fırın Power Turbo Pop-up ürünü Simfer.com.tr''de. Simfer 8222 0+8 Fonksiyon QS Gri Cam Ankastre Fırın Power Turbo Pop-up ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- 60cm-premium-dokunmatik-beyaz-cam-dekorlu-davlumbaz-488
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 488
   AND short_description = 'Simfer 0+9 Fonksiyon Airfry Beyaz 3''lü Ankastre Set (8216 Fırın + 3507 Ocak + 8707 Davlumbaz) ürünü Simfer.com.tr''de. Simfer 0+9 Fonksiyon Airfry Beyaz 3''lü Ankastre Set (8216 Fırın + 3507 Ocak + 8707 Davlumbaz) ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın';

-- n-65cm-ankastre-beyaz-cam-ocak-4g-emaye-izgara-492
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 492
   AND short_description = 'Simfer 3654 65 Cm Beyaz Cam Ankastre Ocak ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Simfer 3654 65 Cm Beyaz Cam Ankastre Ocak yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- 60cm-ankastre-gri-cam-ocak-4g-emaye-izgara-494
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 494
   AND short_description = 'Simfer 3538-N 60CM Ankastre Gri Cam Ocak 4G Emaye Izgara ürünü  Simfer 3538-N 60CM Ankastre Gri Cam Ocak 4G Emaye Izgara ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- 60cm-ankastre-siyah-cam-ocak-4g-emaye-izgara-495
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 495
   AND short_description = 'Simfer Turbo Siyah Ankastre Set (7327 Fırın + 3500 Ocak + 8678 Davlumbaz) ürünü Simfer.com.tr''de. Simfer Turbo Siyah Ankastre Set (7327 Fırın + 3500 Ocak + 8678 Davlumbaz) ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- set-ustu-beyaz-emaye-ocak-4g-496
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 496
   AND short_description = 'Simfer 3010-N Set Üstü Beyaz Emaye Ocak 4G ürünü Simfer.com.tr''de. Simfer 3010-N Set Üstü Beyaz Emaye Ocak 4G ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- l-set-ustu-beyaz-emaye-ocak-4g-lpg-kurulumsuz-497
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 497
   AND short_description = 'Simfer Set Üstü Lpg Uyumlu Metal Ocak Beyaz 3011-L ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Simfer Set Üstü Lpg Uyumlu Metal Ocak Beyaz 3011-L yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- 20litre-mikrodalga-firin-dijital-500
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 500
   AND short_description = 'Simfer 4602 20Litre Mikrodalga Fırın Dijital ürünü Simfer.com.tr''de. Simfer 4602 20Litre Mikrodalga Fırın Dijital ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- 23litre-mikrodalga-firin-flotal-cam-dijital-501
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 501
   AND short_description = 'Simfer 4607 23Litre Mikrodalga Fırın Flotal Cam Dijital ürünü Simfer.com.tr''de. Simfer 4607 23Litre Mikrodalga Fırın Flotal Cam Dijital ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- siyah-vitroseramik-elektrikli-ankastre-ocak-504
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 504
   AND short_description = 'SİMFER VİTROSERAMİK ANKASTRE OCAK SİYAH 3903 ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'SİMFER VİTROSERAMİK ANKASTRE OCAK SİYAH 3903 yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- 1530-03-seramik-ankastre-d-g-ocak-ffd-beyaz-536
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 536
   AND short_description = 'İtimat 1530 Beyaz Seramik Cam Ankastre Ocak ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'İtimat 1530 Beyaz Seramik Cam Ankastre Ocak yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- seal-24000-btu-duvar-tipi-klima-540
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 540
   AND short_description = 'Rota Seal A 24000 Btu Inverter Duvar Tipi Klima ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Duvar Tipi Klimalar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Rota Seal A 24000 Btu Inverter Duvar Tipi Klima yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- seal-12000-btu-duvar-tipi-klima-542
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 542
   AND short_description = 'Rota Climate Seal A++ 12000 BTU Inverter Duvar Tipi Klima ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Duvar Tipi Klimalar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Rota Climate Seal A++ 12000 BTU Inverter Duvar Tipi Klima yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- smart-feel-12-000-btu-split-klima-545
UPDATE products
   SET short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 545
   AND short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-12-000-btu-split-klima-546
UPDATE products
   SET short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 546
   AND short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-18-000-btu-split-klima-547
UPDATE products
   SET short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 547
   AND short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-18-000-btu-split-klima-548
UPDATE products
   SET short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 548
   AND short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- fon-makinasi-sac-kurutma-557
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 557
   AND short_description = 'POWERTEC Tr 901 Saç Kurutma Fön Makinesi ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Saç Kurutma Makinesi ürünleri için idefix`i ziyaret edin.'
   AND description = 'POWERTEC Tr 901 Saç Kurutma Fön Makinesi yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- 11-dlm-rd-yagli-radyator-beyaz-559
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 559
   AND short_description = 'Altus AL 11 RD 2300 W 11 Dilim Yağlı Radyatör ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Fanlı / Radyatörlü Isıtıcı ürünleri için idefix`i ziyaret edin.'
   AND description = 'Altus AL 11 RD 2300 W 11 Dilim Yağlı Radyatör yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- tripper-seyahat-utusu-561
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 561
   AND short_description = 'Arzum Ar690 Tripper Seyahat Ütüsü Mor ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Seyahat Tipi Ütüler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Arzum Ar690 Tripper Seyahat Ütüsü Mor yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- smart-feel-12000-btu-klima-fs12invklmi-568
UPDATE products
   SET short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 568
   AND short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-18000-btu-klima-fs18invklmi-571
UPDATE products
   SET short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 571
   AND short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-18000-dis-unite-fs18invklmo-572
UPDATE products
   SET short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 572
   AND short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- spider-bl-6022-silver-furry-576
UPDATE products
   SET short_description = 'Spider BL 6022 Toz Torbasız Elektrikli Süpürge modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 576
   AND short_description = 'Spider BL 6022 Toz Torbasız Elektrikli Süpürge modelini inceleyin. Toz Torbasız Elektrikli Süpürge modelleri uygun fiyatlarla fakir.com.tr''de.';

-- spider-bl-6029-pearl-white-577
UPDATE products
   SET short_description = 'Spider BL 6029 Toz Torbasız Elektrikli Süpürge modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 577
   AND short_description = 'Spider BL 6029 Toz Torbasız Elektrikli Süpürge modelini inceleyin. Toz Torbasız Elektrikli Süpürge modelleri uygun fiyatlarla fakir.com.tr''de.';

-- adiva-ekmek-kizartma-makinesi-beyaz-578
UPDATE products
   SET short_description = 'Ladiva Buz Çözme Fonksiyonlu Ekmek Kızartma Makinesi Beyaz modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 578
   AND short_description = 'Ladiva Buz Çözme Fonksiyonlu Ekmek Kızartma Makinesi Beyaz modelini inceleyin. Ekmek Kızartma Makinesi modelleri uygun fiyatlarla fakir.com.tr''de.';

-- aromatic-kahve-ve-baharat-ogutucusu-579
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 579
   AND short_description = 'Fakir Aromatic Kahve Ve Baharat Öğütücüsü ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Kahve Değirmeni & Öğütücüler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir Aromatic Kahve Ve Baharat Öğütücüsü yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- atomic-rondo-rosie-580
UPDATE products
   SET short_description = 'Atomic Doğrayıcı & Rondo Rosie modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 580
   AND short_description = 'Atomic Doğrayıcı & Rondo Rosie modelini inceleyin. Doğrayıcı & Rondo modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- avanti-be-120-buharli-utu-bej-581
UPDATE products
   SET short_description = 'Pasteur Yoğurt Makinesi 6''lı Cam Kavanoz Seti modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 581
   AND short_description = 'Pasteur Yoğurt Makinesi 6''lı Cam Kavanoz Seti modelini inceleyin. Aksesuarlar modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- filter-pro-toztorbasiz-elek-sup-greypurp-592
UPDATE products
   SET short_description = 'Online sipariş vermek için hemen tıklayabilirsiniz. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 592
   AND short_description = 'Filter Pro Toz Torbasız Elektrikli Süpürge Greypurp ürünü Fakir.com.tr’de! Online sipariş vermek için hemen tıklayabilirsiniz. Şimdi inceleyin.';

-- filter-pro-toztorbasiz-elek-sup-bordeaux-593
UPDATE products
   SET short_description = 'Online sipariş vermek için hemen tıklayabilirsiniz. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 593
   AND short_description = 'Filter Pro Toz Torbasız Elektrikli Süpürge Bordeaux ürünü Fakir.com.tr’de! Online sipariş vermek için hemen tıklayabilirsiniz. Şimdi inceleyin.';

-- hobby-s-premium-594
UPDATE products
   SET short_description = 'Hobby S Fanlı Isıtıcı Açık Gri modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 594
   AND short_description = 'Hobby S Fanlı Isıtıcı Açık Gri modelini inceleyin. Fanlı Isıtıcı modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- kaave-plus-turk-kahve-makinesi-copper-596
UPDATE products
   SET short_description = 'Kaave Plus 4 Fincan Kapasiteli Türk Kahvesi Copper modelini inceleyin. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 596
   AND short_description = 'Kaave Plus 4 Fincan Kapasiteli Türk Kahvesi Copper modelini inceleyin. Türk Kahvesi Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.';

-- kaave-trio-kozde-sutlu-turk-kahve-mak-rosie-597
UPDATE products
   SET short_description = 'Online sipariş vermek için hemen tıklayabilirsiniz.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 597
   AND short_description = 'Kaave Trio 4 Fincan Kapasiteli Közde & Sütlü Türk Kahvesi Makinesi Rosie ürünü Fakir.com.tr’de! Online sipariş vermek için hemen tıklayabilirsiniz.';

-- kaave-trio-kozde-sutlu-turk-k-mk-rouge-598
UPDATE products
   SET short_description = 'Online sipariş vermek için hemen tıklayabilirsiniz.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 598
   AND short_description = 'Kaave Trio 4 Fincan Kapasiteli Közde & Sütlü Türk Kahvesi Makinesi Rouge ürünü Fakir.com.tr’de! Online sipariş vermek için hemen tıklayabilirsiniz.';

-- philips-65-4k-uhd-led-titan-os-164-cm-618
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 618
   AND short_description = 'Philips 65PUS8009 Uydu Alıcılı 4K Ultra HD Smart LED TV ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm LED Televizyon ürünleri için idefix`i ziyaret edin.'
   AND description = 'Philips 65PUS8009 Uydu Alıcılı 4K Ultra HD Smart LED TV yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- philips-43pus7607-4k-ultra-hd-43-109-ekran-uydu-alicili-smart-led-tv-687
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 687
   AND short_description = 'Philips 43PUS7607 43'''' 108 Ekran Ultra HD 4K Smart Wifi Led TV ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm LED Televizyon ürünleri için idefix`i ziyaret edin.'
   AND description = 'Philips 43PUS7607 43'''' 108 Ekran Ultra HD 4K Smart Wifi Led TV yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- paygo-sp630-ecr-yeni-nesil-mobil-pos-yazarkasa-690
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 690
   AND short_description = 'Paygo Sp630 Ecr Yeni Nesil Mobil Pos Yazarkasa Paygo ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Yazarkasa ve Pos ürünleri için idefix`i ziyaret edin.'
   AND description = 'Paygo Sp630 Ecr Yeni Nesil Mobil Pos Yazarkasa Paygo yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- kaave-4-fincan-kapasiteli-turk-kahvesi-makinesi-antrasit-693
UPDATE products
   SET short_description = 'Kaave Steel 4 Fincan Kapasiteli Türk Kahvesi Makinesi Rosie modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 693
   AND short_description = 'Kaave Steel 4 Fincan Kapasiteli Türk Kahvesi Makinesi Rosie modelini inceleyin. Türk Kahvesi Makinesi modelleri uygun fiyatlarla fakir.com.tr''de.';

-- speed-multi-blender-seti-black-silver-694
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 694
   AND short_description = 'Fakir Speed Multi Blender Seti Black & Silver ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Blender Çoklu Setler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir Speed Multi Blender Seti Black & Silver yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- press-pulp-narenciye-sikacagi-695
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 695
   AND short_description = 'Fakir Press Pulp Narenciye Sıkacağı Beyaz ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Narenciye Sıkacağı ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir Press Pulp Narenciye Sıkacağı Beyaz yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- mr-chef-quadro-blender-gray-697
UPDATE products
   SET short_description = 'Mr. Chef Quadro Blender Seti Gri modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 697
   AND short_description = 'Mr. Chef Quadro Blender Seti Gri modelini inceleyin. Blender Seti modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- stor-blender-seti-black-rosie-698
UPDATE products
   SET short_description = 'Stor Blender Seti Black & Rosie modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 698
   AND short_description = 'Stor Blender Seti Black & Rosie modelini inceleyin. Blender Seti modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- steel-n-more-cay-makinesi-inox-699
UPDATE products
   SET short_description = 'Steel N More Çay Makinesi Inox modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       description = 'Steel N More Çay Makinesi Inox modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 699
   AND short_description = 'Steel N More Çay Makinesi Inox modelini inceleyin. Çay Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.'
   AND description = 'Steel N More Çay Makinesi Inox modelini inceleyin. Çay Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- pasteur-cig-sutten-yogurt-yapma-makinesi-700
UPDATE products
   SET short_description = 'Pasteur Çiğ Sütten Yoğurt Yapma Makinesi modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 700
   AND short_description = 'Pasteur Çiğ Sütten Yoğurt Yapma Makinesi modelini inceleyin. Yoğurt Yapma Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- robert-rs-740-akilli-robot-supurge-701
UPDATE products
   SET short_description = 'Robert RS 740 Akıllı Robot Süpürge modelini inceleyin. Seni anlayan teknoloji.',
       description = 'Robert RS 740 Akıllı Robot Süpürge modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 701
   AND short_description = 'Robert RS 740 Akıllı Robot Süpürge modelini inceleyin. Akıllı Robot Süpürge modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.'
   AND description = 'Robert RS 740 Akıllı Robot Süpürge modelini inceleyin. Akıllı Robot Süpürge modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- mr-chef-quadro-blender-seti-silver-stone-702
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 702
   AND short_description = 'Fakir Mr Chef Quadro 1000 W Blender Seti Silverstone ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Sürahili / Smoothie Blender ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir Mr Chef Quadro 1000 W Blender Seti Silverstone yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- mr-chef-quadro-blender-set-rouge-704
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 704
   AND short_description = 'Fakir Mr Chef Quadro Blender Seti Rouge Premium Yüksek Performans Turbo Özellikli ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Blender Çoklu Setler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir Mr Chef Quadro Blender Seti Rouge Premium Yüksek Performans Turbo Özellikli yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- nosso-cok-amacli-3-litre-pisirici-707
UPDATE products
   SET short_description = 'Nosso Çok Amaçlı 3 Litre Pişirici modelini inceleyin. Seni anlayan teknoloji.',
       description = 'Nosso Çok Amaçlı 3 Litre Pişirici modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 707
   AND short_description = 'Nosso Çok Amaçlı 3 Litre Pişirici modelini inceleyin. Çok Amaçlı Pişirme Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.'
   AND description = 'Nosso Çok Amaçlı 3 Litre Pişirici modelini inceleyin. Çok Amaçlı Pişirme Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- ladiva-buz-cozme-fonksiyonlu-ekmek-kizartma-makinesi-beyaz-708
UPDATE products
   SET short_description = 'Ladiva Buz Çözme Fonksiyonlu Ekmek Kızartma Makinesi Beyaz modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 708
   AND short_description = 'Ladiva Buz Çözme Fonksiyonlu Ekmek Kızartma Makinesi Beyaz modelini inceleyin. Ekmek Kızartma Makinesi modelleri uygun fiyatlarla fakir.com.tr''de.';

-- onyx-buharli-utu-709
UPDATE products
   SET short_description = 'Onyx Buharlı Ütü modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 709
   AND short_description = 'Onyx Buharlı Ütü modelini inceleyin. Buharlı Ütü modelleri uygun fiyatlarla fakir.com.tr''de sizi bekliyor. Seni anlayan teknoloji. Şimdi inceleyin.';

-- robert-rs-760-akilli-robot-supurge-710
UPDATE products
   SET short_description = 'Robert RS 760 Akıllı Robot Süpürge modelini inceleyin. Seni anlayan teknoloji.',
       description = 'Robert RS 760 Akıllı Robot Süpürge modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 710
   AND short_description = 'Robert RS 760 Akıllı Robot Süpürge modelini inceleyin. Akıllı Robot Süpürge modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.'
   AND description = 'Robert RS 760 Akıllı Robot Süpürge modelini inceleyin. Akıllı Robot Süpürge modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- wd-6-pure-clean-2-in-1-islak-kuru-dikey-sarjli-supurge-712
UPDATE products
   SET short_description = 'WD 6 Pure Clean 2 IN 1 Islak Kuru Dikey Şarjlı Süpürge modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 712
   AND short_description = 'WD 6 Pure Clean 2 IN 1 Islak Kuru Dikey Şarjlı Süpürge modelini inceleyin. Kablosuz Şarjlı Dikey Süpürge modelleri uygun fiyatlarla fakir.com.tr''de.';

-- simfer-4608-gri-dijital-mikrodalga-firin-713
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 713
   AND short_description = 'Simfer Sk4608 Gri Dijital Mikrodalga Fırın ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Mikrodalga Fırınlar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Simfer Sk4608 Gri Dijital Mikrodalga Fırın yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- simfer-6706-5-5litre-airfry-silent-pro-mekanik-beyaz-714
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 714
   AND short_description = 'Simfer 6706 5.5Litre Airfry Silent Pro Mekanik Beyaz ürünü Simfer.com.tr''de. Simfer 6706 5.5Litre Airfry Silent Pro Mekanik Beyaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 6706 5.5Litre Airfry Silent Pro Mekanik Beyaz ürünü Simfer.com.tr''de. Simfer 6706 5.5Litre Airfry Silent Pro Mekanik Beyaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- neto-vc20-kirmizi-900-w-toz-torbasiz-supurge-717
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 717
   AND short_description = 'Awox Neto VC20 Kırmızı 900 W Toz Torbasız Süpürge ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Toz Torbasız Süpürgeler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Awox Neto VC20 Kırmızı 900 W Toz Torbasız Süpürge yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- lx-2832-siyah-1500-w-quartz-isitici-718
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 718
   AND short_description = 'Kumtel Lx-2832 E-T Soba Del.Siyah 1500W ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Elektrikli Soba ürünleri için idefix`i ziyaret edin.'
   AND description = 'Kumtel Lx-2832 E-T Soba Del.Siyah 1500W yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- luxell-lx-7021-gri-ikili-set-ustu-elektrikli-ocak-719
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 719
   AND short_description = 'Kumtel LX-7021 Gri Set Üstü Ocak ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Kumtel LX-7021 Gri Set Üstü Ocak yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- luxell-lx-7011-tekli-elektrikli-ocak-beyaz-720
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 720
   AND short_description = 'Luxell LX-7011 Tek Gözlü 1500W Ocak ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ocaklar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Luxell LX-7011 Tek Gözlü 1500W Ocak yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- luxell-hc-2947-2500-w-konvektor-isitici-721
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 721
   AND short_description = 'Luxell HC-2947 1000W Konvektör Konveksiyonel Isıtıcı Beyaz ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Konvektör Isıtıcılar ürünleri için idefix`i ziyaret edin.'
   AND description = 'Luxell HC-2947 1000W Konvektör Konveksiyonel Isıtıcı Beyaz yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- flora-celik-kettle-723
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 723
   AND short_description = 'Sunny Sn5Ktl38 Paslanmaz Çelik 1500W 1,7 Litre Kettle ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Su Isıtıcı & Kettle ürünleri için idefix`i ziyaret edin.'
   AND description = 'Sunny Sn5Ktl38 Paslanmaz Çelik 1500W 1,7 Litre Kettle yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- sunny-merlin-tekli-cubuk-blender-724
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 724
   AND short_description = 'Sunny Merli̇n Tekli̇ Çubuk Blender ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm El Tipi Blender ürünleri için idefix`i ziyaret edin.'
   AND description = 'Sunny Merli̇n Tekli̇ Çubuk Blender yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- lg-43lm6370pla-full-hd-43-109-ekran-uydu-alicili-smart-led-tv-726
UPDATE products
   SET short_description = 'Keşfedin: LG 43LM6370PLA.',
       description = 'Keşfedin: LG 43LM6370PLA.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 726
   AND short_description = 'Keşfedin: LG 43LM6370PLA. LG ile ilgili resimler, yorumlar ve teknik özellikler için tıklayın LG LM63 43 inç Full HD Smart TV.'
   AND description = 'Keşfedin: LG 43LM6370PLA. LG ile ilgili resimler, yorumlar ve teknik özellikler için tıklayın LG LM63 43 inç Full HD Smart TV.';

-- lorenzo-slim-3-in-1-dikey-kablo-vakum-supurgesi-melbourne-red-kl-4-738
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 738
   AND short_description = 'Lorenzo Slim Dikey / Pratik Elektrikli Süpürge Kablolu Hepa Filtre Kırmızı ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Dikey Süpürge ürünleri için idefix`i ziyaret edin.'
   AND description = 'Lorenzo Slim Dikey / Pratik Elektrikli Süpürge Kablolu Hepa Filtre Kırmızı yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- lorenzo-smart-tea-5in1-inox-konusan-celik-cay-makinesi-741
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 741
   AND short_description = 'LORENZO SMART TEA 5 in 1 KONUŞAN ÇAY MAKİNESİ İNOX ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Elektrikli Çay Makineleri ürünleri için idefix`i ziyaret edin.'
   AND description = 'LORENZO SMART TEA 5 in 1 KONUŞAN ÇAY MAKİNESİ İNOX yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- lorenzo-smart-tea-5in1-siyah-konusan-celik-cay-makinesi-742
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 742
   AND short_description = 'SMART TEA 5 in 1 KONUŞAN ÇAY MAKİNESİ SİYAH ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Elektrikli Çay Makineleri ürünleri için idefix`i ziyaret edin.'
   AND description = 'LORENZO SMART TEA 5 in 1 KONUŞAN ÇAY MAKİNESİ SİYAH yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- tefal-tw4835-extent-power-torbasiz-supurge-746
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 746
   AND short_description = 'TW4835 Extent Power Toz Torbasız Elektrikli Süpürge ürünümüzü incelemek ve tefal.com.tr güvencesi ile online satın almak için tıklayın!'
   AND description = 'TW4835 Extent Power Toz Torbasız Elektrikli Süpürge ürünümüzü incelemek ve tefal.com.tr güvencesi ile online satın almak için tıklayın!';

-- fakir-754
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 754
   AND short_description = 'FAKIR MR CHEF QUADRO BLENDER SET BEYAZ ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Blender Çoklu Setler ürünleri için idefix`i ziyaret edin.'
   AND description = 'Fakir FAKIR MR CHEF QUADRO BLENDER SET BEYAZ yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- fakir-lyric-760
UPDATE products
   SET short_description = 'Lyric Voice Saç Düzleştirici modelimizi inceleyin. Şimdi inceleyin.',
       description = 'Lyric Voice Saç Düzleştirici modelimizi inceleyin. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 760
   AND short_description = 'Lyric Voice Saç Düzleştirici modelimizi inceleyin. Lyric Voice Saç Düzleştirici modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.'
   AND description = 'Lyric Voice Saç Düzleştirici modelimizi inceleyin. Lyric Voice Saç Düzleştirici modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.';

-- ankastre-firin-762
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 762
   AND short_description = 'Simfer 8218 0+8 Fonksiyon Retro Bej Cam Ankastre Fırın Power Turbo ürünü Simfer.com.tr''de. Simfer 8218 0+8 Fonksiyon Retro Bej Cam Ankastre Fırın Power Turbo ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 8218 0+8 Fonksiyon Retro Bej Cam Ankastre Fırın Power Turbo ürünü Simfer.com.tr''de. Simfer 8218 0+8 Fonksiyon Retro Bej Cam Ankastre Fırın Power Turbo ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- ankastre-ocak--763
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 763
   AND short_description = 'Simfer 3326-N 60CM Retro Ankastre Bej Cam Ocak 4G Emaye Izgara ürünü Simfer.com.tr''de. Simfer 3326-N 60CM Retro Ankastre Bej Cam Ocak 4G Emaye Izgara ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- ankastre-davlumbaz-764
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 764
   AND short_description = 'Simfer 8716 60CM Retro Bej Push Button Piramit Davlumbaz ürünü Simfer.com.tr''de. Simfer 8716 60CM Retro Bej Push Button Piramit Davlumbaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 8716 60CM Retro Bej Push Button Piramit Davlumbaz ürünü Simfer.com.tr''de. Simfer 8716 60CM Retro Bej Push Button Piramit Davlumbaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- set-ustu-ocak-765
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 765
   AND short_description = 'Simfer 3019-N Set Üstü İnox Emaye Ocak 4G ürünü Simfer.com.tr''de. Simfer 3019-N Set Üstü İnox Emaye Ocak 4G ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- kaave-uno-pro-turk-kahve-makinesi-violet-768
UPDATE products
   SET short_description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 768
   AND short_description = 'Kaave Uno Pro 4 Fincan Kapasiteli Türk Kahvesi Makinesi Violet ürünü Fakir.com.tr’de! Online sipariş vermek için hemen tıklayın; alışverişe başlayın.';

-- bolt-x-plus-aqua-8472-dikey-sarjli-kablosuz-supurge-769
UPDATE products
   SET short_description = 'Bolt X Plus Aqua 8472 Dikey Şarjlı Kablosuz Süpürge Moon Gray modelini inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 769
   AND short_description = 'Bolt X Plus Aqua 8472 Dikey Şarjlı Kablosuz Süpürge Moon Gray modelini inceleyin. Kablosuz Şarjlı Dikey Süpürge modelleri uygun fiyatlarla fakir.com.tr''de.';

-- n-joy-tost-makinesi-rosie-770
UPDATE products
   SET short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Rosie modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 770
   AND short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Rosie modelini inceleyin. Tost Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- tastea-cay-makinesi-siyah-rose-771
UPDATE products
   SET short_description = 'Tastea Çay Makinesi Siyah Rose modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       description = 'Tastea Çay Makinesi Siyah Rose modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 771
   AND short_description = 'Tastea Çay Makinesi Siyah Rose modelini inceleyin. Çay Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.'
   AND description = 'Tastea Çay Makinesi Siyah Rose modelini inceleyin. Çay Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- simfer-8246-11-fonksiyon-steam-master-ankastre-firin-gri-cam-dijital-774
UPDATE products
   SET description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 774
   AND description = 'Simfer 8246 0+10 Fonksiyon Steam Master Ankastre Fırın Gri Cam Dijital ürünü  Simfer 8246 0+10 Fonksiyon Steam Master Ankastre Fırın Gri Cam Dijital ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- smart-feel-12-000-btu-split-klima-778
UPDATE products
   SET short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 778
   AND short_description = 'Smart Feel 12.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- smart-feel-18-000-btu-split-klima-779
UPDATE products
   SET short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 779
   AND short_description = 'Smart Feel 18.000 BTU Split Klima modelini inceleyin. Split Klima modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- air-fry-plus-ankastre-firin-10-fonksiyon-dijital-saat-grill-teleskobik-ray-siyah-781
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 781
   AND short_description = 'Simfer 8211 Air Fry Plus Ankastre Fırın 10 Fonksiyon Dijital Saat, Grill, Teleskobik Ray, Siyah ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Ankastre Fırın ürünleri için idefix`i ziyaret edin.'
   AND description = 'Simfer 8211 Air Fry Plus Ankastre Fırın 10 Fonksiyon Dijital Saat, Grill, Teleskobik Ray, Siyah yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- torque-profesyonel-1800-kiyma-makinasi-790
UPDATE products
   SET short_description = 'Torque 1800 Kıyma Makinesi modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 790
   AND short_description = 'Torque 1800 Kıyma Makinesi modelini inceleyin. Kıyma Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- fakir-max-steam-buharli-temizleyici-794
UPDATE products
   SET short_description = 'Max Steam Buharlı Temizleyici modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 794
   AND short_description = 'Max Steam Buharlı Temizleyici modelini inceleyin. Buharlı Yer ve Yüzey Temizleyici modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- fakir-max-steam-buharli-temizleyici-815
UPDATE products
   SET short_description = 'Max Steam Buharlı Temizleyici modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 815
   AND short_description = 'Max Steam Buharlı Temizleyici modelini inceleyin. Buharlı Yer ve Yüzey Temizleyici modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- fakir-arya-816
UPDATE products
   SET short_description = 'Arya Mutfak Şefi & Stand Mikser Silverstone modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 816
   AND short_description = 'Arya Mutfak Şefi & Stand Mikser Silverstone modelini inceleyin. Mutfak Şefi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- simfer-3751-n
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 824
   AND short_description = 'Simfer 3751-N 70CM Ankastre Beyaz Cam Ocak 4G+1Wok Emaye Izgara ürünü Simfer.com.tr''de. Simfer 3751-N 70CM Ankastre Beyaz Cam Ocak 4G+1Wok Emaye Izgara ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 3751-N 70CM Ankastre Beyaz Cam Ocak 4G+1Wok Emaye Izgara ürünü Simfer.com.tr''de. Simfer 3751-N 70CM Ankastre Beyaz Cam Ocak 4G+1Wok Emaye Izgara ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';

-- fakir-vion-core-sarjli-dikey-supurge-sarj-istasyonu
UPDATE products
   SET short_description = 'Vion Core Şarjlı Dikey Süpürge & Şarj İstasyonu modelimizi inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 894
   AND short_description = 'Vion Core Şarjlı Dikey Süpürge & Şarj İstasyonu modelimizi inceleyin. Vion Core modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- storchop-dorayac-crream
UPDATE products
   SET short_description = 'Storchop Doğrayıcı & Rondo Beyaz modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 906
   AND short_description = 'Storchop Doğrayıcı & Rondo Beyaz modelini inceleyin. Doğrayıcı & Rondo modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- ion-fusion-3lu-su-dalgas-maas
UPDATE products
   SET short_description = 'Ion Fusion 3''lü İyonlu Su Dalgası Saç Maşası modelini inceleyin. Seni anlayan teknoloji.',
       description = 'Ion Fusion 3''lü İyonlu Su Dalgası Saç Maşası modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 908
   AND short_description = 'Ion Fusion 3''lü İyonlu Su Dalgası Saç Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.'
   AND description = 'Ion Fusion 3''lü İyonlu Su Dalgası Saç Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- ion-fusion-sa-maas
UPDATE products
   SET short_description = 'Ion Fusion Argan Yağlı Katlanabilir Başlık Saç Maşası modelini inceleyin. Şimdi inceleyin.',
       description = 'Ion Fusion Argan Yağlı Katlanabilir Başlık Saç Maşası modelini inceleyin. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 909
   AND short_description = 'Ion Fusion Argan Yağlı Katlanabilir Başlık Saç Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.'
   AND description = 'Ion Fusion Argan Yağlı Katlanabilir Başlık Saç Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.';

-- ionfusion-dzletiric
UPDATE products
   SET short_description = 'Ion Fusion Düzleştirici Argan Yağlı Hafıza Özellikli modelini inceleyin. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 910
   AND short_description = 'Ion Fusion Düzleştirici Argan Yağlı Hafıza Özellikli modelini inceleyin. Saç Düzleştirici modelleri uygun fiyatlarla fakir.com.tr''de. Şimdi inceleyin.';

-- ion-fusion-eeach-waes-wag-maas
UPDATE products
   SET short_description = 'Ion Fusion Beach Waves Wag Maşası modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       description = 'Ion Fusion Beach Waves Wag Maşası modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 911
   AND short_description = 'Ion Fusion Beach Waves Wag Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.'
   AND description = 'Ion Fusion Beach Waves Wag Maşası modelini inceleyin. Saç Maşası modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- t-shot-sa-kurutma-makinas
UPDATE products
   SET short_description = 'T-Shot Saç Kurutma Makinesi modelini inceleyin. Seni anlayan teknoloji. Şimdi inceleyin.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 912
   AND short_description = 'T-Shot Saç Kurutma Makinesi modelini inceleyin. Saç Kurutma Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji. Şimdi inceleyin.';

-- n-joy-6-dilim-kapasiteli-tost-makinesirogue
UPDATE products
   SET short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Rouge modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 913
   AND short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Rouge modelini inceleyin. Tost Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- n-joy-6-dilim-kapasiteli-tost-makinesi-silver-stone
UPDATE products
   SET short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Silver Stone modelini inceleyin. Seni anlayan teknoloji.',
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 914
   AND short_description = 'N Joy 6 Dilim Kapasiteli Tost Makinesi Silver Stone modelini inceleyin. Tost Makinesi modelleri uygun fiyatlarla fakir.com.tr''de. Seni anlayan teknoloji.';

-- regal-140-lt-buro-tipi-buzdolabi
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 920
   AND short_description = 'Regal BT 14022 SK E Enerji Sınıfı 121 Lt Büro Tipi Mini Buzdolabı ürününü idefix kalitesiyle satın almak için hemen tıklayın! Tüm Buzdolapları ürünleri için idefix`i ziyaret edin.'
   AND description = 'Regal BT 14022 SK E Enerji Sınıfı 121 Lt Büro Tipi Mini Buzdolabı yorumlarını inceleyin, idefix’e özel indirimli fiyata satın alın.';

-- simfer-8738-60cm-premium-dokunmatik-inox-dekorlu-cam-davlumbaz
UPDATE products
   SET short_description = NULL,
       description = NULL,
       version = version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE id = 924
   AND short_description = 'Simfer 8738 60CM Premium Dokunmatik Inox Dekorlu Cam Davlumbaz ürünü Simfer.com.tr''de. Simfer 8738 60CM Premium Dokunmatik Inox Dekorlu Cam Davlumbaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!'
   AND description = 'Simfer 8738 60CM Premium Dokunmatik Inox Dekorlu Cam Davlumbaz ürünü Simfer.com.tr''de. Simfer 8738 60CM Premium Dokunmatik Inox Dekorlu Cam Davlumbaz ürününü ücretsiz kargo ve uygun fiyat avantajlarıyla satın almak için hemen tıklayın!';
