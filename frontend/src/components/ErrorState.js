import React from 'react';

/**
 * Error state — API error, network down, load failure, etc.
 *
 * Difference from EmptyState: the "try again" CTA is the default and it takes a retry callback.
 */
export default function ErrorState({
  title = 'Bir sorun oluştu',
  description = 'İçerik yüklenirken bir hata ile karşılaştık. Lütfen tekrar deneyin.',
  onRetry,
  retryLabel = 'Tekrar Dene',
  compact = false,
  error, // detail for dev mode
}) {
  const showDetails = process.env.NODE_ENV !== 'production' && error;
  return (
    <div
      role="alert"
      className={`error-state ${compact ? 'error-state-compact' : ''}`}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: compact ? '1.5rem 1rem' : '3rem 1.5rem',
        color: 'var(--text-muted, #6b7280)',
      }}
    >
      <div
        aria-hidden="true"
        style={{
          fontSize: compact ? 28 : 48,
          color: 'var(--color-error, #ef4444)',
          marginBottom: compact ? 8 : 16,
        }}
      >
        <i className="fas fa-exclamation-circle" />
      </div>
      <h3 style={{
        fontSize: compact ? '1rem' : '1.125rem',
        fontWeight: 600,
        margin: '0 0 0.5rem 0',
        color: 'var(--text-primary, #111827)',
      }}>
        {title}
      </h3>
      <p style={{
        maxWidth: 420,
        fontSize: compact ? '0.85rem' : '0.95rem',
        lineHeight: 1.5,
        margin: '0 0 1.25rem 0',
      }}>
        {description}
      </p>
      {onRetry && (
        <button type="button" className="btn btn-primary btn-sm" onClick={onRetry}>
          <i className="fas fa-redo me-1" aria-hidden="true" />
          {retryLabel}
        </button>
      )}
      {showDetails && (
        <details style={{ marginTop: 16, fontSize: '0.75rem', maxWidth: 500 }}>
          <summary style={{ cursor: 'pointer', color: 'var(--color-error)' }}>
            Geliştirici detayları
          </summary>
          <pre style={{
            background: 'var(--color-error-bg, #fee2e2)',
            padding: 8,
            borderRadius: 4,
            textAlign: 'left',
            overflow: 'auto',
            marginTop: 8,
          }}>
            {String(error?.stack || error?.message || error)}
          </pre>
        </details>
      )}
    </div>
  );
}
