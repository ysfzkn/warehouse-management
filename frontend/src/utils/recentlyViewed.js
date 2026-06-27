/**
 * Recently-viewed products — stored client-side in localStorage (no account or
 * server tracking needed, privacy-friendly). We keep an ordered list of product
 * ids (most-recent first) and hydrate them into cards on demand via the
 * POST /api/store/products/by-ids endpoint.
 */
const KEY = 'recentlyViewedProducts';
const MAX = 20;

/** Returns the stored product ids, most-recent first. */
export function getRecentlyViewedIds() {
  try {
    const raw = localStorage.getItem(KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr.filter((x) => Number.isFinite(x)) : [];
  } catch {
    return [];
  }
}

/**
 * Records a product id as just-viewed: moves it to the front, de-duplicates,
 * and caps the list length. Safe to call on every product-detail render.
 */
export function recordRecentlyViewed(productId) {
  const id = Number(productId);
  if (!Number.isFinite(id)) return;
  try {
    const next = [id, ...getRecentlyViewedIds().filter((x) => x !== id)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    /* storage unavailable (private mode / quota) — ignore */
  }
}

/** Recently-viewed ids excluding one product (e.g. the one currently open). */
export function getRecentlyViewedIdsExcluding(excludeId) {
  const ex = Number(excludeId);
  return getRecentlyViewedIds().filter((x) => x !== ex);
}
