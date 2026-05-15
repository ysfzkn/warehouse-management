import React, { useEffect, useState } from 'react';
import { Helmet } from 'react-helmet-async';

const STORAGE_KEY = 'cookie_consent';
const CONSENT_CHANGED_EVENT = 'cookie-consent-changed';

/**
 * Cookie consent durumunu localStorage'dan reactive olarak okur.
 * CookieBanner consent kaydedince `window.dispatchEvent(new CustomEvent('cookie-consent-changed'))`
 * tetiklemeli ki bu hook güncel kalabilsin (aynı sekme içi değişim).
 * Farklı sekmedeki değişim için 'storage' event'i otomatik gelir.
 */
function useCookieConsent() {
  const [consent, setConsent] = useState(() => readConsent());

  useEffect(() => {
    const refresh = () => setConsent(readConsent());
    window.addEventListener(CONSENT_CHANGED_EVENT, refresh);
    window.addEventListener('storage', (e) => {
      if (e.key === STORAGE_KEY) refresh();
    });
    return () => {
      window.removeEventListener(CONSENT_CHANGED_EVENT, refresh);
    };
  }, []);

  return consent;
}

function readConsent() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      necessary: true,
      analytics: !!parsed.analytics,
      marketing: !!parsed.marketing,
    };
  } catch {
    return null;
  }
}

/**
 * Google Analytics (GA4), Facebook Pixel, Hotjar ve Microsoft Clarity script enjeksiyonu.
 *
 * **KVKK & 6563 uyumu:** Script'ler **sadece** kullanıcı cookie banner üzerinden
 * ilgili kategoriye onay verdiyse yüklenir:
 *   - GA4 → "analytics" consent
 *   - Hotjar / MS Clarity → "analytics" consent
 *   - Facebook Pixel → "marketing" consent
 *
 * Kullanıcı henüz banner ile etkileşmediyse (consent = null) hiçbir script yüklenmez.
 *
 * Props:
 *   - googleAnalyticsId: "G-XXXXXXXX" format
 *   - facebookPixelId: numeric pixel ID
 *   - hotjarId: numeric Hotjar site ID (opsiyonel)
 *   - clarityId: Microsoft Clarity project ID (opsiyonel)
 */
export default function AnalyticsScripts({ googleAnalyticsId, facebookPixelId, hotjarId, clarityId }) {
  const consent = useCookieConsent();
  const gaId = (googleAnalyticsId || '').trim();
  const fbId = (facebookPixelId || '').trim();
  const hjId = (hotjarId || '').trim();
  const clId = (clarityId || '').trim();

  if (!consent) return null;            // kullanıcı henüz seçim yapmadı
  if (!gaId && !fbId && !hjId && !clId) return null;

  const allowAnalytics = !!consent.analytics && (gaId || hjId || clId);
  const allowMarketing = !!consent.marketing && !!fbId;

  if (!allowAnalytics && !allowMarketing) return null;

  // GA4 script (gtag.js) — sadece analytics consent varsa
  const gaScript = allowAnalytics
    ? `
      window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', '${gaId}', { anonymize_ip: true });
    `.trim()
    : null;

  // Facebook Pixel script — sadece marketing consent varsa
  const fbScript = allowMarketing
    ? `
      !function(f,b,e,v,n,t,s){if(f.fbq)return;n=f.fbq=function(){n.callMethod?
      n.callMethod.apply(n,arguments):n.queue.push(arguments)};if(!f._fbq)f._fbq=n;
      n.push=n;n.loaded=!0;n.version='2.0';n.queue=[];t=b.createElement(e);t.async=!0;
      t.src=v;s=b.getElementsByTagName(e)[0];s.parentNode.insertBefore(t,s)}(window,document,'script',
      'https://connect.facebook.net/en_US/fbevents.js');
      fbq('init', '${fbId}');
      fbq('track', 'PageView');
    `.trim()
    : null;

  // Hotjar — analytics consent
  const hotjarScript = consent.analytics && hjId
    ? `
      (function(h,o,t,j,a,r){
          h.hj=h.hj||function(){(h.hj.q=h.hj.q||[]).push(arguments)};
          h._hjSettings={hjid:${hjId},hjsv:6};
          a=o.getElementsByTagName('head')[0];
          r=o.createElement('script');r.async=1;
          r.src=t+h._hjSettings.hjid+j+h._hjSettings.hjsv;
          a.appendChild(r);
      })(window,document,'https://static.hotjar.com/c/hotjar-','.js?sv=');
    `.trim()
    : null;

  // Microsoft Clarity — analytics consent
  const clarityScript = consent.analytics && clId
    ? `
      (function(c,l,a,r,i,t,y){
          c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
          t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
          y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
      })(window, document, "clarity", "script", "${clId}");
    `.trim()
    : null;

  return (
    <Helmet>
      {/* Google Analytics 4 — analytics consent */}
      {allowAnalytics && <script async src={`https://www.googletagmanager.com/gtag/js?id=${gaId}`} />}
      {gaScript && <script>{gaScript}</script>}

      {/* Facebook Pixel — marketing consent */}
      {fbScript && <script>{fbScript}</script>}

      {/* Hotjar — analytics consent (heatmap + session recording) */}
      {hotjarScript && <script>{hotjarScript}</script>}

      {/* Microsoft Clarity — analytics consent (free heatmap alternative) */}
      {clarityScript && <script>{clarityScript}</script>}
    </Helmet>
  );
}
