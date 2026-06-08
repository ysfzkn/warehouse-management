import React, { useState } from 'react';
import { FiStar } from 'react-icons/fi';

/**
 * Star rating — read-only (supports fractional fill) or interactive (form input).
 *
 * Props:
 *  - value: number 0..5
 *  - size: px (default 18)
 *  - interactive: when true, renders clickable stars and calls onChange(n)
 *  - onChange: (n) => void
 */
export default function RatingStars({ value = 0, size = 18, interactive = false, onChange }) {
  const [hover, setHover] = useState(0);

  if (interactive) {
    const active = hover || value;
    return (
      <div className="rating-stars-input" role="radiogroup" aria-label="Puan">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            className="rating-star-btn"
            aria-label={`${n} yıldız`}
            aria-checked={value === n}
            role="radio"
            onMouseEnter={() => setHover(n)}
            onMouseLeave={() => setHover(0)}
            onClick={() => onChange && onChange(n)}
          >
            <FiStar
              size={size}
              style={{
                fill: n <= active ? '#f59e0b' : 'none',
                color: n <= active ? '#f59e0b' : '#cbd5e1',
              }}
            />
          </button>
        ))}
      </div>
    );
  }

  const pct = Math.max(0, Math.min(100, (Number(value) / 5) * 100));
  return (
    <span
      className="rating-stars"
      style={{ position: 'relative', display: 'inline-flex', lineHeight: 0 }}
      aria-label={`${value} / 5`}
    >
      <span style={{ display: 'inline-flex', color: '#cbd5e1' }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <FiStar key={n} size={size} style={{ fill: '#e2e8f0' }} />
        ))}
      </span>
      <span
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: `${pct}%`,
          overflow: 'hidden',
          display: 'inline-flex',
          whiteSpace: 'nowrap',
          color: '#f59e0b',
        }}
        aria-hidden="true"
      >
        {[1, 2, 3, 4, 5].map((n) => (
          <FiStar key={n} size={size} style={{ fill: '#f59e0b', flexShrink: 0 }} />
        ))}
      </span>
    </span>
  );
}
