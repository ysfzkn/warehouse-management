import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Sayfa rotası değiştiğinde otomatik olarak en üste scroll eder.
 *
 * React Router default davranışı: route değişiminde scroll position korunur.
 * Bu, modal açma/kapama gibi durumlar için doğru, ama tam sayfa geçişlerinde
 * (ürün listesinden detaya, kategoriden ürüne vb.) kötü UX yaratır — kullanıcı
 * önceki sayfanın aşağısında kalır ve yeni sayfanın başlığını görmek için
 * elle yukarı kaydırmak zorunda kalır.
 *
 * Davranış:
 *   - pathname değiştiğinde scroll Y → 0
 *   - search/hash değişiminde scroll ETKİLENMEZ (filtre değiştirme, anchor link)
 *   - state.preserveScroll === true ise scroll korunur (back button, modal close)
 *   - Reduced motion tercihi varsa instant; yoksa smooth scroll
 *
 * Uygulama: <BrowserRouter> içinde, <Routes>'tan önce sadece bir tane.
 */
export default function ScrollToTop() {
  const { pathname, state } = useLocation();

  useEffect(() => {
    // Modal kapatma, geri tuşu vb. durumlarda scroll korunsun
    if (state && state.preserveScroll) return;

    // Reduced motion tercihine saygı
    const reducedMotion =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // İçeriğin DOM'a girmesi için 1 frame bekle — Layout shift'i önler
    requestAnimationFrame(() => {
      try {
        window.scrollTo({
          top: 0,
          left: 0,
          behavior: reducedMotion ? 'auto' : 'smooth',
        });
      } catch {
        // Eski tarayıcı fallback
        window.scrollTo(0, 0);
      }
    });
  }, [pathname, state]);

  return null;
}
