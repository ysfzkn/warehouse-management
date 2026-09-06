-- Kategori adlarındaki büyük/küçük harf tutarsızlığı.
--
-- Kategoriler yıllar içinde farklı ellerden girilmiş ve vitrinin ana sayfasındaki çip
-- satırında yan yana dizilince tutarsızlık göze batıyor: "Ankastre Fırın" ile "Ankastre
-- ocak", "Solo Fırın" ile "solo ocak", "Çamaşır Makinası" ile "Bulaşık makinası".
--
-- Kural bilerek dar tutuldu: yalnızca tamamı küçük harften oluşan kelimelerin ilk harfi
-- büyütülüyor. İçinde büyük harf geçen hiçbir kelimeye dokunulmuyor — "ZZ", "TV", "LED"
-- gibi kısaltmalar ve "iPhone" gibi markalar oldukları gibi kalsın diye. Türkçede küçük
-- yazılan bağlaçlar da listeden muaf ("Ev ve Bahçe" → "Ev Ve Bahçe" olmamalı), ama ilk
-- kelimeyse yine büyüyor.
--
-- Büyütme elle yapılıyor çünkü PostgreSQL'in upper() fonksiyonu veritabanının
-- karşılaştırma diline bağlı: Türkçe olmayan bir collation'da "işık" → "ISIK" oluyor ve
-- noktalı i kayboluyor. Doğrusu i→İ, ı→I.
--
-- Ad sütunu benzersiz. Yeni ad başka bir kategoride zaten varsa satır atlanıyor: iki
-- kategoriyi birleştirmek bir veri kararı, harf düzeltmesinin yan etkisi olamaz.
--
-- Slug'lara dokunulmuyor; adres çubuğundaki bağlantılar ve arama motoru kayıtları aynı
-- kalıyor.

DO $$
DECLARE
    stop_words CONSTANT TEXT[] := ARRAY['ve', 'ile', 'veya', 'için', 'de', 'da'];
    rec        RECORD;
    words      TEXT[];
    word       TEXT;
    head       TEXT;
    renamed    TEXT;
    i          INT;
BEGIN
    FOR rec IN SELECT id, name FROM categories WHERE name IS NOT NULL LOOP
        words := regexp_split_to_array(btrim(rec.name), '\s+');

        FOR i IN 1 .. COALESCE(array_length(words, 1), 0) LOOP
            word := words[i];

            -- İçinde büyük harf varsa kelimeye hiç dokunma.
            CONTINUE WHEN word ~ '[A-ZÇĞİÖŞÜ]';
            -- İlk kelime dışındaki bağlaçlar küçük kalır.
            CONTINUE WHEN i > 1 AND lower(word) = ANY (stop_words);

            head := left(word, 1);
            words[i] := CASE head
                            WHEN 'i' THEN 'İ'
                            WHEN 'ı' THEN 'I'
                            WHEN 'ç' THEN 'Ç'
                            WHEN 'ğ' THEN 'Ğ'
                            WHEN 'ö' THEN 'Ö'
                            WHEN 'ş' THEN 'Ş'
                            WHEN 'ü' THEN 'Ü'
                            ELSE upper(head)
                        END || substr(word, 2);
        END LOOP;

        renamed := array_to_string(words, ' ');

        CONTINUE WHEN renamed = rec.name;
        CONTINUE WHEN EXISTS (SELECT 1 FROM categories c
                               WHERE c.name = renamed AND c.id <> rec.id);

        UPDATE categories SET name = renamed WHERE id = rec.id;
        RAISE NOTICE 'Kategori adı düzeltildi: % -> %', rec.name, renamed;
    END LOOP;
END $$;
