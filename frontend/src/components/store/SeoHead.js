import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildMetaTags } from '../../utils/seo';

/**
 * SEO meta tag enjeksiyonu için ortak bileşen.
 *
 * Props:
 *   - title: sayfa başlığı (siteName otomatik eklenir)
 *   - description: sayfa açıklaması (yoksa site default'u kullanılır)
 *   - path: canonical için path (örn: "/urun/iphone-15")
 *   - image: OG image URL'i (yoksa default logo kullanılır)
 *   - type: OG type (website | article | product)
 *   - jsonLd: Schema.org JSON-LD object veya array (opsiyonel)
 *   - noindex: true ise robots meta "noindex,nofollow"
 *   - children: ek meta tagleri için
 */
export default function SeoHead({
  title,
  description,
  path,
  image,
  type = 'website',
  jsonLd,
  noindex = false,
  children
}) {
  const { settings } = useSiteSettings();
  const settingsMap = toSettingsMap(settings);

  const meta = buildMetaTags({ title, description, path, image, type }, settingsMap);

  const jsonLdArray = jsonLd ? (Array.isArray(jsonLd) ? jsonLd : [jsonLd]).filter(Boolean) : [];

  return (
    <Helmet>
      {/* Basic meta */}
      <title>{meta.title}</title>
      {meta.description && <meta name="description" content={meta.description} />}
      {meta.canonicalUrl && <link rel="canonical" href={meta.canonicalUrl} />}

      {/* Robots */}
      {noindex && <meta name="robots" content="noindex,nofollow" />}

      {/* Open Graph */}
      <meta property="og:title" content={meta.title} />
      {meta.description && <meta property="og:description" content={meta.description} />}
      {meta.canonicalUrl && <meta property="og:url" content={meta.canonicalUrl} />}
      <meta property="og:type" content={meta.ogType} />
      <meta property="og:site_name" content={meta.ogSiteName} />
      <meta property="og:locale" content="tr_TR" />
      {meta.ogImage && <meta property="og:image" content={meta.ogImage} />}

      {/* Twitter Card */}
      <meta name="twitter:card" content={meta.twitterCard} />
      <meta name="twitter:title" content={meta.title} />
      {meta.description && <meta name="twitter:description" content={meta.description} />}
      {meta.ogImage && <meta name="twitter:image" content={meta.ogImage} />}
      {meta.twitterHandle && <meta name="twitter:site" content={meta.twitterHandle} />}

      {/* JSON-LD Structured Data */}
      {jsonLdArray.map((obj, idx) => (
        <script key={idx} type="application/ld+json">
          {JSON.stringify(obj)}
        </script>
      ))}

      {/* Custom children */}
      {children}
    </Helmet>
  );
}

/** useSiteSettings settings objesini olduğu gibi kullanır */
function toSettingsMap(settings) {
  if (!settings || typeof settings !== 'object') return {};
  return settings;
}
