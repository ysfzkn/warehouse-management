import React, { useState, useRef, useLayoutEffect, useEffect, useCallback } from 'react';

/**
 * Collapsible long text for tables and list rows.
 *
 * Clamps `text` to `lines` lines with a trailing ellipsis so a row keeps a
 * fixed, predictable height, and reveals a "Tümünü gör" / "Daha az" toggle only
 * when the text actually overflows the clamp. The element never grows
 * horizontally — width is owned by the parent cell — so column widths stay
 * stable no matter how long the description is.
 *
 * Use anywhere a user-supplied description/note is rendered inside a constrained
 * row (product tables, stock-entry lists, mobile cards).
 */
export default function ExpandableText({
  text,
  lines = 2,
  className = '',
  style,
  moreLabel = 'Tümünü gör',
  lessLabel = 'Daha az',
}) {
  const [expanded, setExpanded] = useState(false);
  const [overflowing, setOverflowing] = useState(false);
  const ref = useRef(null);

  const measure = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    // While clamped, an overflowing text reports scrollHeight > clientHeight.
    setOverflowing(el.scrollHeight - el.clientHeight > 1);
  }, []);

  // Re-measure whenever the content, clamp size, or expanded state changes.
  useLayoutEffect(() => {
    measure();
  }, [text, lines, expanded, measure]);

  // Re-measure on viewport resize (column width may change).
  useEffect(() => {
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [measure]);

  if (!text) return null;

  const clampStyle = expanded
    ? undefined
    : {
        display: '-webkit-box',
        WebkitBoxOrient: 'vertical',
        WebkitLineClamp: lines,
        overflow: 'hidden',
      };

  return (
    <div className={className} style={style}>
      <div ref={ref} style={{ ...clampStyle, wordBreak: 'break-word' }}>
        {text}
      </div>
      {(overflowing || expanded) && (
        <button
          type="button"
          className="btn btn-link btn-sm p-0 text-decoration-none align-baseline"
          style={{ fontSize: 'inherit', lineHeight: 1.2 }}
          onClick={(e) => {
            // Don't trigger row-level click handlers (e.g. "add product").
            e.stopPropagation();
            setExpanded((v) => !v);
          }}
          aria-expanded={expanded}
        >
          {expanded ? lessLabel : moreLabel}
        </button>
      )}
    </div>
  );
}
