import React, { useState } from 'react';
import { Link } from 'react-router-dom';

/**
 * Color-variant swatch row on the product detail page.
 *
 * `variants` is the backend `colorVariants` list — the same product in different colors,
 * including the one being viewed (`current: true`). Each swatch is the variant's color hex
 * (falling back to a neutral dot with the colour's initial when no hex is set). The current
 * colour is highlighted with a ring; out-of-stock variants are muted with a diagonal strike.
 * Hovering a swatch previews its colour name in the label; clicking navigates to that
 * product's page (`/urun/:slug`), which refetches the detail.
 */
const ProductColorVariants = ({ variants }) => {
  const [hovered, setHovered] = useState(null);

  if (!Array.isArray(variants) || variants.length < 2) return null;

  const current = variants.find((v) => v.current);
  const activeLabel = (hovered && hovered.colorName) || current?.colorName;

  const swatch = (v) => {
    const hex = v.colorHexCode;
    return (
      <span
        className="store-color-swatch"
        data-current={v.current ? 'true' : undefined}
        data-oos={v.inStock ? undefined : 'true'}
        style={{ backgroundColor: hex || '#f1f5f9' }}
      >
        {!hex && (
          <span className="store-color-swatch__initial">
            {v.colorName ? v.colorName.charAt(0).toUpperCase() : '?'}
          </span>
        )}
        {!v.inStock && <span className="store-color-swatch__strike" />}
      </span>
    );
  };

  return (
    <div className="border-top pt-3 pb-3">
      <style>{`
        .store-color-variants { display: flex; flex-wrap: wrap; gap: 14px; }
        .store-color-variants a,
        .store-color-variants .store-color-swatch-wrap {
          display: inline-flex; padding: 5px; border-radius: 50%;
          text-decoration: none; line-height: 0;
        }
        .store-color-swatch {
          position: relative; display: inline-flex; align-items: center; justify-content: center;
          width: 34px; height: 34px; border-radius: 50%;
          box-shadow: 0 0 0 1px rgba(0,0,0,0.14);
          transition: box-shadow .15s ease, transform .15s ease;
        }
        .store-color-variants a:hover .store-color-swatch {
          box-shadow: 0 0 0 1px rgba(0,0,0,0.14), 0 0 0 4px rgba(37,99,235,0.18);
          transform: translateY(-1px);
        }
        .store-color-swatch[data-current="true"] {
          box-shadow: 0 0 0 2px #fff, 0 0 0 4px var(--store-primary, #2563eb);
        }
        .store-color-swatch[data-oos="true"] { opacity: .45; }
        .store-color-swatch__initial { font-size: 12px; font-weight: 600; color: #64748b; }
        .store-color-swatch__strike {
          position: absolute; top: 50%; left: -2px; right: -2px; height: 1.5px;
          background: #94a3b8; transform: rotate(-45deg);
        }
      `}</style>

      <div className="d-flex align-items-center gap-2 mb-2">
        <span className="text-muted small">Renk:</span>
        {activeLabel && <span className="fw-semibold small">{activeLabel}</span>}
        <span className="text-muted small">· {variants.length} renk</span>
      </div>

      <div className="store-color-variants">
        {variants.map((v) => {
          const title = `${v.colorName || 'Renk'}${v.inStock ? '' : ' — Tükendi'}`;

          // The current colour isn't a link (we're already on it).
          if (v.current) {
            return (
              <span
                key={v.productId}
                className="store-color-swatch-wrap"
                title={title}
                style={{ cursor: 'default' }}
                onMouseEnter={() => setHovered(v)}
                onMouseLeave={() => setHovered(null)}
              >
                {swatch(v)}
              </span>
            );
          }

          return (
            <Link
              key={v.productId}
              to={`/urun/${v.slug}`}
              title={title}
              aria-label={title}
              onMouseEnter={() => setHovered(v)}
              onMouseLeave={() => setHovered(null)}
            >
              {swatch(v)}
            </Link>
          );
        })}
      </div>
    </div>
  );
};

export default ProductColorVariants;
