/**
 * Türkiye il + ilçe statik veri kümesi.
 *
 * Bu data Türkiye e-ticaret mevzuatı (6563 sayılı kanun) gereği standartlaştırılmış
 * adres veri girişi sağlamak için kullanılır. PTT API entegrasyonu maliyetli ve
 * hız riski getirir; statik veri çoğu durum için yeterli, MVP yaklaşımıdır.
 *
 * Kapsam:
 *   - 81 il listesi (TR_PROVINCES) — şehir dropdown
 *   - En kalabalık ~20 ilin tüm ilçeleri (TR_DISTRICTS) — yaklaşık nüfusun %75'ini kapsar
 *   - Diğer iller için ilçe free-text kalır (autocomplete devre dışı)
 *
 * İlçe verisi tam değildir — kapsam ihtiyaca göre genişletilebilir. Doğru kaynak:
 * https://github.com/anil-aslandag/tr-il-ilce-mahalle veya benzeri açık veri.
 */

export const TR_PROVINCES = [
  'Adana','Adıyaman','Afyonkarahisar','Ağrı','Aksaray','Amasya','Ankara','Antalya','Ardahan','Artvin',
  'Aydın','Balıkesir','Bartın','Batman','Bayburt','Bilecik','Bingöl','Bitlis','Bolu','Burdur',
  'Bursa','Çanakkale','Çankırı','Çorum','Denizli','Diyarbakır','Düzce','Edirne','Elazığ','Erzincan',
  'Erzurum','Eskişehir','Gaziantep','Giresun','Gümüşhane','Hakkari','Hatay','Iğdır','Isparta','İstanbul',
  'İzmir','Kahramanmaraş','Karabük','Karaman','Kars','Kastamonu','Kayseri','Kırıkkale','Kırklareli','Kırşehir',
  'Kilis','Kocaeli','Konya','Kütahya','Malatya','Manisa','Mardin','Mersin','Muğla','Muş',
  'Nevşehir','Niğde','Ordu','Osmaniye','Rize','Sakarya','Samsun','Şanlıurfa','Siirt','Sinop',
  'Şırnak','Sivas','Tekirdağ','Tokat','Trabzon','Tunceli','Uşak','Van','Yalova','Yozgat','Zonguldak',
];

// İlçeler — en kalabalık iller için (population %75 kapsar)
export const TR_DISTRICTS = {
  'İstanbul': [
    'Adalar','Arnavutköy','Ataşehir','Avcılar','Bağcılar','Bahçelievler','Bakırköy','Başakşehir',
    'Bayrampaşa','Beşiktaş','Beykoz','Beylikdüzü','Beyoğlu','Büyükçekmece','Çatalca','Çekmeköy',
    'Esenler','Esenyurt','Eyüpsultan','Fatih','Gaziosmanpaşa','Güngören','Kadıköy','Kağıthane',
    'Kartal','Küçükçekmece','Maltepe','Pendik','Sancaktepe','Sarıyer','Silivri','Şile','Şişli',
    'Sultanbeyli','Sultangazi','Tuzla','Ümraniye','Üsküdar','Zeytinburnu',
  ],
  'Ankara': [
    'Akyurt','Altındağ','Ayaş','Bala','Beypazarı','Çamlıdere','Çankaya','Çubuk','Elmadağ','Etimesgut',
    'Evren','Gölbaşı','Güdül','Haymana','Kalecik','Kazan','Keçiören','Kızılcahamam','Mamak','Nallıhan',
    'Polatlı','Pursaklar','Sincan','Şereflikoçhisar','Yenimahalle',
  ],
  'İzmir': [
    'Aliağa','Balçova','Bayındır','Bayraklı','Bergama','Beydağ','Bornova','Buca','Çeşme','Çiğli',
    'Dikili','Foça','Gaziemir','Güzelbahçe','Karabağlar','Karaburun','Karşıyaka','Kemalpaşa','Kınık',
    'Kiraz','Konak','Menderes','Menemen','Narlıdere','Ödemiş','Seferihisar','Selçuk','Tire','Torbalı','Urla',
  ],
  'Bursa': [
    'Büyükorhan','Gemlik','Gürsu','Harmancık','İnegöl','İznik','Karacabey','Keles','Kestel','Mudanya',
    'Mustafakemalpaşa','Nilüfer','Orhaneli','Orhangazi','Osmangazi','Yenişehir','Yıldırım','Yenişehir',
  ],
  'Antalya': [
    'Akseki','Aksu','Alanya','Demre','Döşemealtı','Elmalı','Finike','Gazipaşa','Gündoğmuş','İbradı',
    'Kaş','Kemer','Kepez','Konyaaltı','Korkuteli','Kumluca','Manavgat','Muratpaşa','Serik',
  ],
  'Adana': ['Aladağ','Ceyhan','Çukurova','Feke','İmamoğlu','Karaisalı','Karataş','Kozan','Pozantı','Saimbeyli','Sarıçam','Seyhan','Tufanbeyli','Yumurtalık','Yüreğir'],
  'Konya': ['Ahırlı','Akören','Akşehir','Altınekin','Beyşehir','Bozkır','Cihanbeyli','Çeltik','Çumra','Derbent','Derebucak','Doğanhisar','Emirgazi','Ereğli','Güneysınır','Hadim','Halkapınar','Hüyük','Ilgın','Kadınhanı','Karapınar','Karatay','Kulu','Meram','Sarayönü','Selçuklu','Seydişehir','Taşkent','Tuzlukçu','Yalıhüyük','Yunak'],
  'Gaziantep': ['Araban','İslahiye','Karkamış','Nizip','Nurdağı','Oğuzeli','Şahinbey','Şehitkamil','Yavuzeli'],
  'Şanlıurfa': ['Akçakale','Birecik','Bozova','Ceylanpınar','Eyyübiye','Halfeti','Haliliye','Harran','Hilvan','Karaköprü','Siverek','Suruç','Viranşehir'],
  'Kocaeli': ['Başiskele','Çayırova','Darıca','Derince','Dilovası','Gebze','Gölcük','İzmit','Kandıra','Karamürsel','Kartepe','Körfez'],
  'Mersin': ['Akdeniz','Anamur','Aydıncık','Bozyazı','Çamlıyayla','Erdemli','Gülnar','Mezitli','Mut','Silifke','Tarsus','Toroslar','Yenişehir'],
  'Diyarbakır': ['Bağlar','Bismil','Çermik','Çınar','Çüngüş','Dicle','Eğil','Ergani','Hani','Hazro','Kayapınar','Kocaköy','Kulp','Lice','Silvan','Sur','Yenişehir'],
  'Kayseri': ['Akkışla','Bünyan','Develi','Felahiye','Hacılar','İncesu','Kocasinan','Melikgazi','Özvatan','Pınarbaşı','Sarıoğlan','Sarız','Talas','Tomarza','Yahyalı','Yeşilhisar'],
  'Eskişehir': ['Alpu','Beylikova','Çifteler','Günyüzü','Han','İnönü','Mahmudiye','Mihalgazi','Mihalıççık','Odunpazarı','Sarıcakaya','Seyitgazi','Sivrihisar','Tepebaşı'],
  'Samsun': ['19 Mayıs','Alaçam','Asarcık','Atakum','Ayvacık','Bafra','Canik','Çarşamba','Havza','İlkadım','Kavak','Ladik','Salıpazarı','Tekkeköy','Terme','Vezirköprü','Yakakent'],
  'Sakarya': ['Adapazarı','Akyazı','Arifiye','Erenler','Ferizli','Geyve','Hendek','Karapürçek','Karasu','Kaynarca','Kocaali','Pamukova','Sapanca','Serdivan','Söğütlü','Taraklı'],
  'Manisa': ['Ahmetli','Akhisar','Alaşehir','Demirci','Gölmarmara','Gördes','Kırkağaç','Köprübaşı','Kula','Salihli','Sarıgöl','Saruhanlı','Selendi','Soma','Şehzadeler','Turgutlu','Yunusemre'],
  'Tekirdağ': ['Çerkezköy','Çorlu','Ergene','Hayrabolu','Kapaklı','Malkara','Marmara Ereğlisi','Muratlı','Saray','Süleymanpaşa','Şarköy'],
  'Hatay': ['Altınözü','Antakya','Arsuz','Belen','Defne','Dörtyol','Erzin','Hassa','İskenderun','Kırıkhan','Kumlu','Payas','Reyhanlı','Samandağ','Yayladağı'],
  'Aydın': ['Bozdoğan','Buharkent','Çine','Didim','Efeler','Germencik','İncirliova','Karacasu','Karpuzlu','Koçarlı','Köşk','Kuşadası','Kuyucak','Nazilli','Söke','Sultanhisar','Yenipazar'],
};

/** Verilen şehir için ilçe listesi (yoksa boş array; UI free-text gösterir). */
export function getDistrictsForProvince(province) {
  if (!province) return [];
  return TR_DISTRICTS[province] || [];
}

/** Şehir adının resmi şehir listesinde olup olmadığını doğrular. */
export function isValidProvince(province) {
  return TR_PROVINCES.includes(province);
}
