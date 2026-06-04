/**
 * i18n skeleton (Phase 3 NICE-TO-HAVE).
 *
 * Default language: TR. EN is currently a stub — translation keys will be filled in
 * once international sales begin. All existing pages are written with hardcoded TR strings;
 * gradual migration:
 *   1. New components should use the `useTranslation()` hook
 *   2. Repeated legacy strings should be replaced with t('key') in a batch
 *   3. Add the same key to both tr.json and en.json
 *
 * To enable, add `import './i18n'` in `index.js`.
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
      escapeValue: false, // React already escapes
    },
    detection: {
      // Order: localStorage > navigator (browser) > htmlTag > fallback
      order: ['localStorage', 'navigator', 'htmlTag'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },
  });

export default i18n;
