import React from 'react';
import { Helmet } from 'react-helmet-async';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { buildMetaTags } from '../../utils/seo';

/**
 * Shared component for injecting SEO meta tags.
 *
 * Props:
 *   - title: page title (siteName is appended automatically)
 *   - description: page description (falls back to the site default if absent)
 *   - path: path for the canonical URL (e.g. "/urun/iphone-15")
 *   - image: OG image URL (falls back to the default logo if absent)
 *   - type: OG type (website | article | product)
 *   - jsonLd: Schema.org JSON-LD object or array (optional)
 *   - noindex: if true, sets robots meta to "noindex,nofollow"
 *   - children: for additional meta tags
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

/** Uses the useSiteSettings settings object as-is */
function toSettingsMap(settings) {
  if (!settings || typeof settings !== 'object') return {};
  return settings;
}
