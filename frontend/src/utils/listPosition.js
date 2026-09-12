/**
 * Remembers where a visitor was in an endlessly scrolling list, so that opening a
 * product and pressing back puts them back among the same items.
 *
 * The list loads 24 products at a time and appends. Leaving the page unmounts it, so
 * coming back rebuilt it from the first 24 and scrolled to the top — someone who had
 * scrolled through two hundred phones to reach one lost every bit of that.
 *
 * Two things have to be restored: how far the list had been loaded, and where the page
 * was scrolled to. The page count is the important half — without it the item the
 * visitor came back to look at does not exist in the DOM to scroll to.
 *
 * Storage is sessionStorage: this is one visit's navigation history, meaningless in a
 * new tab and not worth keeping after the tab closes. Every access is wrapped, because
 * a browser in private mode can throw on the property itself.
 */

const PREFIX = 'list-pos:';
/** Restoring a huge list in one request would be slower than the scroll it saves. */
const MAX_RESTORE_PAGES = 10;

/**
 * The position saved for this list, or null when there is nothing to restore.
 *
 * @param key    identifies the list, filters included — a different filter is a
 *               different list and must not inherit the old one's position
 * @param isBack true only for browser back/forward; arriving fresh should start at
 *               the top of an unscrolled list, which is what a new visit expects
 */
export function readListPosition(key, isBack) {
  if (!isBack || !key) return null;
  try {
    const raw = sessionStorage.getItem(PREFIX + key);
    if (!raw) return null;
    const saved = JSON.parse(raw);
    const pages = Number(saved.pages);
    if (!Number.isFinite(pages) || pages < 2) return null;
    return {
      pages: Math.min(pages, MAX_RESTORE_PAGES),
      scrollY: Number(saved.scrollY) || 0,
    };
  } catch {
    return null;
  }
}

export function saveListPosition(key, pages, scrollY) {
  if (!key) return;
  try {
    // One page and no scrolling is the state a fresh visit already produces; writing it
    // would only add entries that can never change what the visitor sees.
    if (pages < 2 && scrollY < 200) {
      sessionStorage.removeItem(PREFIX + key);
      return;
    }
    sessionStorage.setItem(PREFIX + key, JSON.stringify({ pages, scrollY }));
  } catch {
    /* Private mode or a full quota: the list still works, it just starts at the top. */
  }
}
