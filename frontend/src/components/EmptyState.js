import React from 'react';

/**
 * Boş durum (empty state) komponenti — tablo, liste, arama vb. veri yoksa gösterilir.
 *
 * Hem admin hem store tarafında kullanılabilir.
 *
 * Props:
 *   - icon: React node (örn. <FiInbox size={48} />) veya FontAwesome class string
 *   - title: ana başlık (zorunlu)
 *   - description: kısa açıklama
 *   - actionLabel + onAction: opsiyonel CTA butonu
 *   - secondaryAction: opsiyonel ikinci buton ({ label, onClick })
 *   - compact: true → küçük variant (modal/dropdown içi)
 */
export default function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  secondaryAction,
  compact = false,
}) {
  const iconEl = typeof icon === 'string'
    ? <i className={`${icon}`} style={{ fontSize: compact ? 28 : 48 }} aria-hidden="true" />
    : icon;

  return (
    <div
      role="status"
      className={`empty-state ${compact ? 'empty-state-compact' : ''}`}
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
      {iconEl && (
        <div
          aria-hidden="true"
          style={{
            color: 'var(--color-gray-400, #9ca3af)',
            marginBottom: compact ? 8 : 16,
            opacity: 0.8,
          }}
        >
          {iconEl}
        </div>
      )}
      <h3 style={{
        fontSize: compact ? '1rem' : '1.125rem',
        fontWeight: 600,
        margin: '0 0 0.5rem 0',
        color: 'var(--text-primary, #111827)',
      }}>
        {title}
      </h3>
      {description && (
        <p style={{
          maxWidth: 360,
          fontSize: compact ? '0.85rem' : '0.95rem',
          lineHeight: 1.5,
          margin: '0 0 1.25rem 0',
        }}>
          {description}
        </p>
      )}
      {(actionLabel || secondaryAction) && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
          {secondaryAction && (
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm"
              onClick={secondaryAction.onClick}
            >
              {secondaryAction.label}
            </button>
          )}
          {actionLabel && (
            <button
              type="button"
              className="btn btn-primary btn-sm"
              onClick={onAction}
            >
              {actionLabel}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
