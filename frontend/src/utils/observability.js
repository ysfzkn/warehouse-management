import * as Sentry from '@sentry/react';

/**
 * Frontend error tracking — env-gated, fully inert until a DSN is configured.
 *
 * Set REACT_APP_SENTRY_DSN (and optionally REACT_APP_SENTRY_ENV,
 * REACT_APP_SENTRY_TRACES_RATE) to turn it on. With no DSN, init() is a no-op
 * and captureError() silently does nothing — so local/dev builds and any
 * deployment without the env var pay zero cost and send nothing.
 *
 * Why Sentry: production runtime errors were previously invisible (only the
 * browser console saw them). This streams uncaught exceptions, unhandled
 * promise rejections and explicitly-captured API errors to one dashboard.
 */
let enabled = false;

export function initObservability() {
  const dsn = process.env.REACT_APP_SENTRY_DSN;
  if (!dsn) return; // inert without configuration

  try {
    Sentry.init({
      dsn,
      environment: process.env.REACT_APP_SENTRY_ENV || process.env.NODE_ENV || 'production',
      release: process.env.REACT_APP_VERSION || undefined,
      // Performance tracing is opt-in and cheap by default (off unless a rate is set).
      tracesSampleRate: Number(process.env.REACT_APP_SENTRY_TRACES_RATE || 0),
      // Don't capture noisy, expected client conditions.
      ignoreErrors: [
        'Network Error',
        'Request aborted',
        'ResizeObserver loop limit exceeded',
        'Non-Error promise rejection captured',
      ],
      beforeSend(event) {
        // Drop events that carry no useful signal.
        return event;
      },
    });
    enabled = true;
  } catch {
    enabled = false;
  }
}

/** Report a handled error with optional structured context. No-op when disabled. */
export function captureError(error, context) {
  if (!enabled) return;
  try {
    Sentry.captureException(error, context ? { extra: context } : undefined);
  } catch {
    /* never let telemetry break the app */
  }
}

/** Attach the signed-in user (or clear it on logout). No-op when disabled. */
export function setUserContext(user) {
  if (!enabled) return;
  try {
    Sentry.setUser(user || null);
  } catch {
    /* ignore */
  }
}

export function isObservabilityEnabled() {
  return enabled;
}
