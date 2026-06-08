/**
 * Lightweight haptic feedback helper.
 *
 * Uses the Vibration API where available (Android Chrome, etc.). iOS Safari
 * ignores it silently, which is fine — this is a progressive enhancement and
 * must never throw. Pass a duration (ms) or a pattern array.
 */
export function haptic(pattern = 10) {
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      navigator.vibrate(pattern);
    }
  } catch {
    /* no-op — vibration is a non-critical enhancement */
  }
}

/** Short tap — for taps like add-to-cart, quantity change. */
export const hapticTap = () => haptic(12);

/** Success buzz — for confirmations (added to cart, order placed). */
export const hapticSuccess = () => haptic([10, 30, 14]);
