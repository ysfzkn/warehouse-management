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
  const availability = (product.availableQuantity ?? 0) > 0
    ? 'https://schema.org/InStock'
    : 'https://schema.org/OutOfStock';

  const images = (product.images || [])
    .map(img => toAbsoluteUrl(img.url, siteSettings))
    .filter(Boolean);
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
        name: getSiteName(siteSettings)
      }
    }
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
      worstRating: '1'
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
      item: toAbsoluteUrl(item.url, siteSettings)
    }))
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
  if (siteSettings.contact_phone || siteSettings.contact_email) {
    schema.contactPoint = {
      '@type': 'ContactPoint',
      contactType: 'customer service',
      telephone: siteSettings.contact_phone || undefined,
      email: siteSettings.contact_email || undefined,
      areaServed: 'TR',
      availableLanguage: ['Turkish']
    };
  }

  // Address
  if (siteSettings.contact_address) {
    schema.address = {
      '@type': 'PostalAddress',
      addressCountry: 'TR',
      streetAddress: siteSettings.contact_address
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
        urlTemplate: `${origin}urun-ara?q={search_term_string}`
      },
      'query-input': 'required name=search_term_string'
    }
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
    url: getCanonicalUrl(`/kategori/${category.slug}`, siteSettings)
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
      logo: siteSettings?.site_logo_url ? {
        '@type': 'ImageObject',
        url: toAbsoluteUrl(siteSettings.site_logo_url, siteSettings)
      } : undefined
    }
  };
}

/**
 * Generates a meta tag combination: title, description, OG, Twitter Card.
 * Returns an object to be mapped into <Helmet> in page components.
 *
 * @returns { title, description, canonicalUrl, ogImage, ogType, twitterCard }
 */
export function buildMetaTags(opts, siteSettings) {
  const {
    title,
    description,
    path,
    image,
    type = 'website',
  } = opts || {};

  const siteName = getSiteName(siteSettings);
  const fullTitle = title ? `${title} | ${siteName}` : siteName;
  const resolvedDescription = resolveDescription(description, siteSettings);
  const canonical = getCanonicalUrl(path || '/', siteSettings);
  const ogImage = toAbsoluteUrl(image || getDefaultOgImage(siteSettings), siteSettings);

  return {
    title: fullTitle,
    description: resolvedDescription,
    canonicalUrl: canonical,
    ogImage,
    ogType: type,
    ogSiteName: siteName,
    twitterCard: image ? 'summary_large_image' : 'summary',
    twitterHandle: siteSettings?.seo_twitter_handle || ''
  };
}
