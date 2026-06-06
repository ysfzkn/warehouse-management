/**
 * Phone directory + manual-order helpers.
 *
 * Phone numbers are stored in a single `phone_directory` site setting as a JSON
 * string (array of entries). All functions here take the RAW settings map
 * (i.e. useSiteSettings().settings — NOT the hook object). Callers that hold the
 * hook object must pass `.settings`.
 *
 * Entry shape:
 *   { type: 'merkez'|'sube'|'whatsapp'|'mobile', label?: string,
 *     number: string, isDefault?: boolean }
 */

/** The selectable phone types (admin dropdown + display grouping). */
export const PHONE_TYPES = [
  { value: 'merkez', label: 'Merkez (İş Yeri)', group: 'İş Yeri Telefonu', isWhatsapp: false },
  { value: 'sube', label: 'Şube (İş Yeri)', group: 'İş Yeri Telefonu', isWhatsapp: false },
  { value: 'whatsapp', label: 'WhatsApp Kanalı (Business)', group: 'WhatsApp Hattı', isWhatsapp: true },
  { value: 'mobile', label: 'Cep Telefonu', group: 'Cep Telefonu', isWhatsapp: false },
];

const TYPE_MAP = Object.fromEntries(PHONE_TYPES.map((t) => [t.value, t]));
const GROUP_ORDER = ['İş Yeri Telefonu', 'WhatsApp Hattı', 'Cep Telefonu'];

export const DEFAULT_WHATSAPP_TEMPLATE =
  'Merhaba, şu ürünle ilgileniyorum:\n\n{urun}\nFiyat: {fiyat}\nSKU: {sku}\n\n{link}';

/** Resolve an entry's type, accepting the legacy {category, subType} shape too. */
function resolveType(e) {
  if (e && TYPE_MAP[e.type]) return e.type;
  // Legacy fallback (category/subType from the first iteration)
  if (e && e.category === 'mobile') return 'mobile';
  if (e && e.category === 'business') return e.subType === 'sube' ? 'sube' : 'merkez';
  return 'merkez';
}

function mapEntry(e, keepEmptyNumber) {
  const number = keepEmptyNumber ? String(e?.number ?? '') : String(e?.number ?? '').trim();
  return {
    type: resolveType(e),
    label: typeof e?.label === 'string' ? e.label : '',
    number,
    isDefault: !!e?.isDefault,
  };
}

/** Display parse: clean array, drops rows without a number (never throws). */
export function parsePhoneDirectory(settings) {
  const raw = settings?.phone_directory;
  if (!raw || typeof raw !== 'string') return [];
  try {
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .filter((e) => e && typeof e === 'object' && String(e.number || '').trim())
      .map((e) => mapEntry(e, false));
  } catch {
    return [];
  }
}

/** Editor parse: KEEP all rows (including empty ones being filled in). */
export function parsePhoneDirectoryEditable(value) {
  if (!value || typeof value !== 'string') return [];
  try {
    const arr = JSON.parse(value);
    if (!Array.isArray(arr)) return [];
    return arr.filter((e) => e && typeof e === 'object').map((e) => mapEntry(e, true));
  } catch {
    return [];
  }
}

/** Whether a type renders as a WhatsApp (chat) link rather than a tel: link. */
export function isWhatsappType(type) {
  return !!TYPE_MAP[type]?.isWhatsapp;
}

/** The single default callable number: first isDefault, else first, else contact_phone. */
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

/** wa.me chat href for a WhatsApp-channel number. */
export function waHref(number) {
  return `https://wa.me/${normalizePhone(number)}`;
}

/**
 * Group the directory for display, ordered: İş Yeri → WhatsApp → Cep.
 * Returns [{ key, title, rows: [{ type, label, number, isDefault, isWhatsapp }] }].
 */
export function groupPhoneDirectory(settings) {
  const dir = parsePhoneDirectory(settings);
  if (dir.length === 0) return [];
  const byGroup = {};
  dir.forEach((e) => {
    const meta = TYPE_MAP[e.type] || TYPE_MAP.merkez;
    (byGroup[meta.group] = byGroup[meta.group] || []).push({ ...e, isWhatsapp: meta.isWhatsapp });
  });
  return GROUP_ORDER.filter((g) => byGroup[g] && byGroup[g].length).map((g) => ({
    key: g,
    title: g,
    rows: byGroup[g],
  }));
}

// ─── Manual WhatsApp order (product page) ───

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
