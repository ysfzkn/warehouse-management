/**
 * i18n iskeleti (Faz 3 NICE-TO-HAVE).
 *
 * Default dil: TR. EN şu an stub — uluslararası satış başlayınca translation
 * key'ler doldurulur. Mevcut sayfaların hepsi hardcoded TR string'lerle yazılı;
 * tedrici migration:
 *   1. Yeni komponentler `useTranslation()` hook'unu kullansın
 *   2. Tekrar eden eski string'ler bir batch'te t('key') ile değiştirilsin
 *   3. tr.json + en.json'a aynı key eklensin
 *
 * Aktif etmek için `index.js`'te `import './i18n'` ekleyin.
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import tr from './locales/tr.json';
import en from './locales/en.json';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      tr: { translation: tr },
      en: { translation: en },
    },
    fallbackLng: 'tr',
    supportedLngs: ['tr', 'en'],
    interpolation: {
      escapeValue: false, // React zaten escape ediyor
    },
    detection: {
      // Sıralama: localStorage > navigator (browser) > htmlTag > fallback
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },
  });

export default i18n;
