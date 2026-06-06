/**
 * Phone directory + manual-order helpers.
 *
 * Phone numbers are stored in a single `phone_directory` site setting as a JSON
 * string (array of entries). All functions here take the RAW settings map
 * (i.e. useSiteSettings().settings — NOT the hook object). Callers that hold the
 * hook object must pass `.settings`.
 *
 * Entry shape:
 *   { category: 'business'|'mobile', subType: 'merkez'|'sube'|null,
 *     label?: string, number: string, isDefault?: boolean }
 */

export const CATEGORY_LABELS = { business: 'İş Yeri Telefonu', mobile: 'Cep Telefonu' };
export const SUBTYPE_LABELS = { merkez: 'Merkez', sube: 'Şube' };

export const DEFAULT_WHATSAPP_TEMPLATE =
  'Merhaba, şu ürünle ilgileniyorum:\n\n{urun}\nFiyat: {fiyat}\nSKU: {sku}\n\n{link}';

/** Parse the phone_directory JSON setting into a clean array (never throws). */
export function parsePhoneDirectory(settings) {
  const raw = settings?.phone_directory;
  if (!raw || typeof raw !== 'string') return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .filter((e) => e && typeof e === 'object' && String(e.number || '').trim())
      .map((e) => ({
        category: e.category === 'mobile' ? 'mobile' : 'business',
        subType: e.category === 'mobile' ? null : e.subType === 'sube' ? 'sube' : 'merkez',
        label: typeof e.label === 'string' ? e.label : '',
        number: String(e.number).trim(),
        isDefault: !!e.isDefault,
      }));
  } catch {
    return [];
  }
}

/** The single default number: first isDefault, else first entry, else contact_phone. */
export function getDefaultPhone(settings) {
  const dir = parsePhoneDirectory(settings);
  const def = dir.find((e) => e.isDefault) || dir[0];
  return (def && def.number) || settings?.contact_phone || '';
}

/** Digits only (for wa.me). */
export function normalizePhone(number) {
  return String(number || '').replace(/\D/g, '');
}

/** tel: href — keep a leading '+', strip spaces/other separators. */
export function telHref(number) {
  const n = String(number || '').trim();
  const plus = n.startsWith('+') ? '+' : '';
  return `tel:${plus}${n.replace(/[^\d]/g, '')}`;
}

/**
 * Group the directory for display: ordered business (Merkez, Şube) then mobile.
 * Returns [{ key, title, groups: [{ key, title, rows: [...] }] }] with empties omitted.
 */
export function groupPhoneDirectory(settings) {
  const dir = parsePhoneDirectory(settings);
  if (dir.length === 0) return [];
  const out = [];

  const business = dir.filter((e) => e.category === 'business');
  if (business.length) {
    const sub = [];
    ['merkez', 'sube'].forEach((st) => {
      const rows = business.filter((e) => e.subType === st);
      if (rows.length) sub.push({ key: st, title: SUBTYPE_LABELS[st], rows });
    });
    if (sub.length) out.push({ key: 'business', title: CATEGORY_LABELS.business, groups: sub });
  }

  const mobile = dir.filter((e) => e.category === 'mobile');
  if (mobile.length) {
    out.push({
      key: 'mobile',
      title: CATEGORY_LABELS.mobile,
      groups: [{ key: null, title: null, rows: mobile }],
    });
  }
  return out;
}

// ─── Manual WhatsApp order (Feature 3) ───

/** Digits-only WhatsApp order number (falls back to social_whatsapp). */
export function getWhatsappOrderNumber(settings) {
  return normalizePhone(settings?.whatsapp_order_number || settings?.social_whatsapp || '');
}

/**
 * Build a wa.me URL with a pre-filled order message for a product.
 * Returns null when no WhatsApp number is configured (caller hides the button).
 */
export function buildWhatsappOrderUrl(settings, product, productUrl, currencySymbol) {
  const number = getWhatsappOrderNumber(settings);
  if (!number || !product) return null;
  const tpl = (settings?.whatsapp_order_template || '').trim() || DEFAULT_WHATSAPP_TEMPLATE;
  const price = product.salePrice && product.salePrice > 0 ? product.salePrice : product.price;
  const priceText = `${price ?? ''} ${currencySymbol || '₺'}`.trim();
  const msg = tpl
    .split('{urun}')
    .join(product.name || '')
    .split('{fiyat}')
    .join(priceText)
    .split('{sku}')
    .join(product.sku || '')
    .split('{link}')
    .join(productUrl || '')
    .split('{marka}')
    .join(product.brandName || '')
    .split('{kategori}')
    .join(product.categoryName || '');
  return `https://wa.me/${number}?text=${encodeURIComponent(msg)}`;
}
