/**
 * SEO Utility Module
 *
 * Schema.org JSON-LD generators and SEO helper functions.
 * Page components use these functions to produce consistent structured data.
 *
 * Usage example:
 *   import { buildProductSchema, buildBreadcrumbSchema } from '../utils/seo';
 *   const productLd = buildProductSchema(product, siteSettings);
 */

import { getDefaultPhone } from './phones';

/** Builds the canonical URL. Uses the canonical domain from site settings if present, otherwise window.location.origin. */
export function getCanonicalUrl(path = '', siteSettings) {
  const canonicalDomain = siteSettings?.seo_canonical_domain?.trim();
  const origin = canonicalDomain || (typeof window !== 'undefined' ? window.location.origin : '');
  const cleanOrigin = origin.replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  return cleanOrigin + cleanPath;
}

/** Site name used across the whole site */
export function getSiteName(siteSettings) {
  return siteSettings?.site_name || siteSettings?.seo_organization_name || 'Mağaza';
}

/** Default OG image URL */
export function getDefaultOgImage(siteSettings) {
  return siteSettings?.seo_default_og_image || siteSettings?.site_logo_url || '';
}

/** Description fallback chain */
export function resolveDescription(customDescription, siteSettings) {
  if (customDescription && customDescription.trim()) return customDescription.trim();
  return siteSettings?.seo_default_meta_description || '';
}

/** Make a full URL: convert a relative path to an absolute one */
export function toAbsoluteUrl(url, siteSettings) {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  const canonicalDomain = siteSettings?.seo_canonical_domain?.trim();
  const origin = canonicalDomain || (typeof window !== 'undefined' ? window.location.origin : '');
  const cleanOrigin = origin.replace(/\/+$/, '');
  const cleanPath = url.startsWith('/') ? url : `/${url}`;
  return cleanOrigin + cleanPath;
}

// ============ SCHEMA.ORG JSON-LD GENERATORS ============

/**
 * Generates the Product Schema.org JSON-LD.
 * For Google rich snippets: price, stock, rating stars.
 */
export function buildProductSchema(product, siteSettings) {
  if (!product) return null;

  const url = getCanonicalUrl(`/urun/${product.slug}`, siteSettings);
  const currentPrice = product.salePrice && product.salePrice > 0 ? product.salePrice : product.price;
  const availability =
    (product.availableQuantity ?? 0) > 0 ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock';

  const images = (product.images || []).map((img) => toAbsoluteUrl(img.url, siteSettings)).filter(Boolean);
  if (images.length === 0 && product.primaryImageUrl) {
    images.push(toAbsoluteUrl(product.primaryImageUrl, siteSettings));
  }

  const schema = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description || product.shortDescription || product.metaDescription || '',
    sku: product.sku || undefined,
    image: images.length > 0 ? images : undefined,
    url,
    offers: {
      '@type': 'Offer',
      price: currentPrice?.toString(),
      priceCurrency: 'TRY',
      availability,
      url,
      itemCondition: 'https://schema.org/NewCondition',
      seller: {
        '@type': 'Organization',
        name: getSiteName(siteSettings),
      },
    },
  };

  if (product.brandName) {
    schema.brand = { '@type': 'Brand', name: product.brandName };
  }

  if (product.categoryName) {
    schema.category = product.categoryName;
  }

  if (product.colorName) {
    schema.color = product.colorName;
  }

  if (product.reviewCount && product.reviewCount > 0 && product.averageRating) {
    schema.aggregateRating = {
      '@type': 'AggregateRating',
      ratingValue: product.averageRating.toFixed(1),
      reviewCount: product.reviewCount,
      bestRating: '5',
      worstRating: '1',
    };
  }

  return schema;
}

/**
 * BreadcrumbList schema.
 * items: [{ name: 'Home', url: '/' }, { name: 'Electronics', url: '/kategori/elektronik' }, ...]
 */
export function buildBreadcrumbSchema(items, siteSettings) {
  if (!items || items.length === 0) return null;
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map((item, idx) => ({
      '@type': 'ListItem',
      position: idx + 1,
      name: item.name,
      item: toAbsoluteUrl(item.url, siteSettings),
    })),
  };
}

/**
 * Organization schema - used on the homepage.
 */
export function buildOrganizationSchema(siteSettings) {
  if (!siteSettings) return null;
  const name = siteSettings.seo_organization_name || siteSettings.site_name;
  if (!name) return null;

  const sameAs = [];
  if (siteSettings.social_instagram) sameAs.push(siteSettings.social_instagram);
  if (siteSettings.social_facebook) sameAs.push(siteSettings.social_facebook);
  if (siteSettings.social_twitter) sameAs.push(siteSettings.social_twitter);

  const schema = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name,
    url: getCanonicalUrl('/', siteSettings),
    logo: toAbsoluteUrl(siteSettings.site_logo_url, siteSettings) || undefined,
  };

  if (sameAs.length > 0) schema.sameAs = sameAs;

  // Contact information
  if (getDefaultPhone(siteSettings) || siteSettings.contact_email) {
    schema.contactPoint = {
      '@type': 'ContactPoint',
      contactType: 'customer service',
      telephone: getDefaultPhone(siteSettings) || undefined,
      email: siteSettings.contact_email || undefined,
      areaServed: 'TR',
      availableLanguage: ['Turkish'],
    };
  }

  // Address
  if (siteSettings.contact_address) {
    schema.address = {
      '@type': 'PostalAddress',
      addressCountry: 'TR',
      streetAddress: siteSettings.contact_address,
    };
  }

  return schema;
}

/**
 * WebSite schema with search action - for the Google sitelinks search box.
 */
export function buildWebSiteSchema(siteSettings) {
  const origin = getCanonicalUrl('/', siteSettings);
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: getSiteName(siteSettings),
    url: origin,
    potentialAction: {
      '@type': 'SearchAction',
      target: {
        '@type': 'EntryPoint',
        urlTemplate: `${origin}urun-ara?q={search_term_string}`,
      },
      'query-input': 'required name=search_term_string',
    },
  };
}

/**
 * CollectionPage schema - for category pages.
 */
export function buildCollectionPageSchema(category, siteSettings) {
  if (!category) return null;
  return {
    '@context': 'https://schema.org',
    '@type': 'CollectionPage',
    name: category.metaTitle || category.name,
    description: category.metaDescription || category.description || '',
    url: getCanonicalUrl(`/kategori/${category.slug}`, siteSettings),
  };
}

/**
 * Article schema - for CMS pages (blog, FAQ, etc.).
 */
export function buildArticleSchema(cmsPage, siteSettings) {
  if (!cmsPage) return null;
  return {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: cmsPage.metaTitle || cmsPage.title,
    description: cmsPage.metaDescription || '',
    url: getCanonicalUrl(`/sayfa/${cmsPage.slug}`, siteSettings),
    publisher: {
      '@type': 'Organization',
      name: getSiteName(siteSettings),
      logo: siteSettings?.site_logo_url
        ? {
            '@type': 'ImageObject',
            url: toAbsoluteUrl(siteSettings.site_logo_url, siteSettings),
          }
        : undefined,
    },
  };
}

/**
 * Generates a meta tag combination: title, description, OG, Twitter Card.
 * Returns an object to be mapped into <Helmet> in page components.
 *
 * @returns { title, description, canonicalUrl, ogImage, ogType, twitterCard }
 */
// ============ LOCAL SEO (city-targeted) ============

/** Split a comma-separated settings string into a clean array. */
function splitList(value) {
  if (!value) return [];
  return String(value)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/** The configured store city (e.g. "Niğde"), or empty if unset. */
export function getLocalCity(siteSettings) {
  return (siteSettings?.seo_local_city || '').trim();
}

/**
 * Prefix a title with the store city for local SEO ("Niğde Profilo Çamaşır Makinesi").
 * Skips if the city is already present or no city is configured.
 */
export function withCity(text, siteSettings) {
  const city = getLocalCity(siteSettings);
  const base = (text || '').trim();
  if (!city || !base) return base;
  if (base.toLowerCase().includes(city.toLowerCase())) return base;
  return `${city} ${base}`;
}

/**
 * LocalBusiness (Store) Schema.org JSON-LD — the strongest local-SEO signal.
 * Tells Google this is a physical store in {city} serving that area, so
 * "{city} {brand}" / "{city} {category}" searches surface it. All values are
 * configurable from the admin Site Settings (seo_local_* + contact_*).
 */
export function buildLocalBusinessSchema(siteSettings) {
  if (!siteSettings) return null;
  const name = siteSettings.seo_organization_name || siteSettings.site_name;
  const city = getLocalCity(siteSettings);
  if (!name || !city) return null; // need at least a name + city to be useful

  const region = (siteSettings.seo_local_region || city).trim();
  const schema = {
    '@context': 'https://schema.org',
    '@type': siteSettings.seo_local_business_type || 'HomeGoodsStore',
    name,
    url: getCanonicalUrl('/', siteSettings),
    image: toAbsoluteUrl(siteSettings.site_logo_url, siteSettings) || undefined,
    logo: toAbsoluteUrl(siteSettings.site_logo_url, siteSettings) || undefined,
    telephone: getDefaultPhone(siteSettings) || undefined,
    email: siteSettings.contact_email || undefined,
    priceRange: siteSettings.seo_local_price_range || '₺₺',
    address: {
      '@type': 'PostalAddress',
      streetAddress: siteSettings.contact_address || undefined,
      addressLocality: city,
      addressRegion: region,
      postalCode: siteSettings.seo_local_postal_code || undefined,
      addressCountry: 'TR',
    },
    areaServed: Array.from(new Set([city, region])).map((n) => ({ '@type': 'City', name: n })),
  };

  const lat = parseFloat(siteSettings.seo_geo_lat);
  const lng = parseFloat(siteSettings.seo_geo_lng);
  if (!Number.isNaN(lat) && !Number.isNaN(lng)) {
    schema.geo = { '@type': 'GeoCoordinates', latitude: lat, longitude: lng };
    schema.hasMap = `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
  }

  const sameAs = [];
  if (siteSettings.social_instagram) sameAs.push(siteSettings.social_instagram);
  if (siteSettings.social_facebook) sameAs.push(siteSettings.social_facebook);
  if (siteSettings.social_twitter) sameAs.push(siteSettings.social_twitter);
  if (sameAs.length) schema.sameAs = sameAs;

  // GEO / generative-engine enrichment: founding year, brand list, description,
  // alternate names (so a direct "ATS DTM" search resolves to this business),
  // and knowsAbout topics — all help AI/LLMs describe the store correctly.
  const foundingYear = (siteSettings.seo_local_founding_year || '').trim();
  if (foundingYear) schema.foundingDate = foundingYear;

  const brands = splitList(siteSettings.seo_local_primary_brands);
  if (brands.length) schema.brand = brands.map((b) => ({ '@type': 'Brand', name: b }));

  const description =
    (siteSettings.seo_local_description || '').trim() || resolveDescription(null, siteSettings);
  if (description) schema.description = description;

  if (siteSettings.seo_local_slogan) schema.slogan = siteSettings.seo_local_slogan;

  const aliases = splitList(siteSettings.seo_brand_aliases);
  if (aliases.length) schema.alternateName = aliases;

  const cats = splitList(siteSettings.seo_local_primary_categories);
  const knows = [...brands, ...cats, 'beyaz eşya', 'küçük ev aletleri', 'ankastre'];
  schema.knowsAbout = Array.from(new Set(knows.filter(Boolean)));

  if (siteSettings.seo_local_opening_hours) {
    schema.openingHours = siteSettings.seo_local_opening_hours; // e.g. "Mo-Sa 09:00-19:00"
  }
  return schema;
}

/**
 * City × brand × category keyword combinations for a single product/category.
 * e.g. "Niğde Profilo", "Niğde çamaşır makinesi", "Niğde Profilo çamaşır makinesi",
 * "Profilo çamaşır makinesi Niğde", "Niğde beyaz eşya".
 */
export function buildLocalKeywords(siteSettings, { brand, category, productName } = {}) {
  const city = getLocalCity(siteSettings);
  const out = new Set();
  const add = (...parts) => {
    const s = parts.filter(Boolean).join(' ').trim();
    if (s) out.add(s);
  };
  if (city) {
    add(city, brand);
    add(city, category);
    add(city, brand, category);
    add(brand, category, city);
    add(city, 'beyaz eşya');
    if (brand) add(city, brand, 'beyaz eşya');
    if (productName) add(city, productName);
  }
  add(brand, category);
  add(productName);
  splitList(siteSettings?.seo_local_keywords_extra).forEach((k) => out.add(k));
  return Array.from(out);
}

/**
 * Homepage keyword set: city × every configured primary brand × category.
 * Driven by seo_local_primary_brands / seo_local_primary_categories settings.
 */
export function buildHomeLocalKeywords(siteSettings) {
  const city = getLocalCity(siteSettings);
  const brands = splitList(siteSettings?.seo_local_primary_brands);
  const cats = splitList(siteSettings?.seo_local_primary_categories);
  const out = new Set();
  const add = (...parts) => {
    const s = parts.filter(Boolean).join(' ').trim();
    if (s) out.add(s);
  };
  if (city) {
    add(city, 'beyaz eşya');
    add(city, 'küçük ev aletleri');
    brands.forEach((b) => add(city, b));
    cats.forEach((c) => add(city, c));
  }
  brands.forEach((b) =>
    cats.forEach((c) => {
      if (city) add(city, b, c);
      add(b, c);
    })
  );
  // Brand-name signals so a direct "ATS DTM" search resolves to the store.
  const orgName = (siteSettings?.seo_organization_name || siteSettings?.site_name || '').trim();
  if (orgName) {
    out.add(orgName);
    if (city) add(city, orgName);
  }
  splitList(siteSettings?.seo_brand_aliases).forEach((a) => out.add(a));
  splitList(siteSettings?.seo_local_keywords_extra).forEach((k) => out.add(k));
  return Array.from(out);
}

export function buildMetaTags(opts, siteSettings) {
  const { title, description, path, image, type = 'website', keywords } = opts || {};

  const siteName = getSiteName(siteSettings);
  const fullTitle = title ? `${title} | ${siteName}` : siteName;
  const resolvedDescription = resolveDescription(description, siteSettings);
  const canonical = getCanonicalUrl(path || '/', siteSettings);
  const ogImage = toAbsoluteUrl(image || getDefaultOgImage(siteSettings), siteSettings);
  const kw = Array.isArray(keywords)
    ? keywords.join(', ')
    : keywords || siteSettings?.seo_default_keywords || '';

  return {
    title: fullTitle,
    description: resolvedDescription,
    keywords: kw,
    canonicalUrl: canonical,
    ogImage,
    ogType: type,
    ogSiteName: siteName,
    twitterCard: image ? 'summary_large_image' : 'summary',
    twitterHandle: siteSettings?.seo_twitter_handle || '',
  };
}
