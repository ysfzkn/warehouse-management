import { useEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

/**
 * Automatically scrolls to the top when the page route changes.
 *
 * Default React Router behavior: scroll position is preserved on route change.
 * That is correct for cases like opening/closing a modal, but for full page
 * transitions (product list to detail, category to product, etc.) it creates
 * poor UX — the user stays at the bottom of the previous page and has to
 * manually scroll up to see the new page's heading.
 *
 * Behavior:
 *   - On pathname change, scroll Y → 0
 *   - Scroll is NOT affected by search/hash changes (filter change, anchor link)
 *   - Scroll is preserved when state.preserveScroll === true (modal close)
 *   - Scroll is preserved on back/forward, where the visitor expects to land where
 *     they left rather than at the top of a page they have already read
 *   - Instant if reduced-motion is preferred; otherwise smooth scroll
 *
 * Usage: a single instance inside <BrowserRouter>, before <Routes>.
 */
export default function ScrollToTop() {
  const { pathname, state } = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    // Preserve scroll for cases like closing a modal.
    if (state && state.preserveScroll) return;

    // Back and forward. The comment above promised this case was handled, but it only
    // ever worked when the app had pushed preserveScroll itself — a browser back button
    // carries whatever state the entry was pushed with, which for a product list is
    // nothing. So every "back" from a product landed at the top of the list.
    if (navigationType === 'POP') return;

    // Respect the reduced-motion preference
    const reducedMotion =
      typeof window !== 'undefined' &&
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // Wait 1 frame for the content to enter the DOM — prevents layout shift
    requestAnimationFrame(() => {
      try {
        window.scrollTo({
          top: 0,
          left: 0,
          behavior: reducedMotion ? 'auto' : 'smooth',
        });
      } catch {
        // Fallback for older browsers
        window.scrollTo(0, 0);
      }
    });
  }, [pathname, state, navigationType]);

  return null;
}
