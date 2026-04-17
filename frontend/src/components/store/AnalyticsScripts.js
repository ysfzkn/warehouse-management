import React from 'react';
import { Helmet } from 'react-helmet-async';

/**
 * Google Analytics (GA4) ve Facebook Pixel script enjeksiyonu.
 *
 * Props:
 *   - googleAnalyticsId: "G-XXXXXXXX" format
 *   - facebookPixelId: numeric pixel ID
 *
 * Script'ler Helmet aracılığıyla head'e inject edilir.
 * Scripts her ikisi de boşsa hiçbir şey render etmez (no-op).
 */
export default function AnalyticsScripts({ googleAnalyticsId, facebookPixelId }) {
  const gaId = (googleAnalyticsId || '').trim();
  const fbId = (facebookPixelId || '').trim();

  if (!gaId && !fbId) return null;

  // GA4 script (gtag.js)
  const gaScript = gaId
    ? `
      window.dataLayer = window.dataLayer || [];
      function gtag(){dataLayer.push(arguments);}
      gtag('js', new Date());
      gtag('config', '${gaId}', { anonymize_ip: true });
    `.trim()
    : null;

  // Facebook Pixel script
  const fbScript = fbId
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

  return (
    <Helmet>
      {/* Google Analytics 4 */}
      {gaId && <script async src={`https://www.googletagmanager.com/gtag/js?id=${gaId}`} />}
      {gaScript && <script>{gaScript}</script>}

      {/* Facebook Pixel */}
      {fbScript && <script>{fbScript}</script>}
    </Helmet>
  );
}
