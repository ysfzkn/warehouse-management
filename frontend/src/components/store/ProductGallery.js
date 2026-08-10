import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FiChevronLeft, FiChevronRight, FiZoomIn, FiX } from 'react-icons/fi';

export default function ProductGallery({ images, productName }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [zoomed, setZoomed] = useState(false);
  // URLs that failed to load. Kept in state so the fallback is applied DECLARATIVELY
  // — otherwise every re-render (e.g. from hover/zoom) would re-apply a broken src and
  // the image would reload-flicker on each mouse move.
  const [failedSrc, setFailedSrc] = useState(() => new Set());
  const [zoomPos, setZoomPos] = useState({ x: 50, y: 50 });
  const touchRef = useRef({ startX: 0, startY: 0 });
  const mouseDrag = useRef({ active: false, startX: 0, startY: 0, dx: 0, dy: 0, didDrag: false });
  const galleryRef = useRef(null);

  // Fullscreen lightbox with pinch / double-tap zoom (mobile-first, works on desktop too)
  const [lightbox, setLightbox] = useState(false);
  const [lbScale, setLbScale] = useState(1);
  const [lbTrans, setLbTrans] = useState({ x: 0, y: 0 });
  const pinchRef = useRef({ startDist: 0, startScale: 1, lastX: 0, lastY: 0, startX: 0, startY: 0 });
  const lastTapRef = useRef(0);
  const LB_MAX = 4;

  const sorted = images && images.length > 0 ? [...images].sort((a, b) => a.sortOrder - b.sortOrder) : [];

  const goTo = useCallback(
    (index) => {
      if (isTransitioning || sorted.length <= 1) return;
      setIsTransitioning(true);
      setActiveIndex((index + sorted.length) % sorted.length);
      setTimeout(() => setIsTransitioning(false), 350);
    },
    [isTransitioning, sorted.length]
  );

  const goPrev = useCallback(() => goTo(activeIndex - 1), [activeIndex, goTo]);
  const goNext = useCallback(() => goTo(activeIndex + 1), [activeIndex, goTo]);

  // Keyboard navigation
  useEffect(() => {
    const handler = (e) => {
      if (e.key === 'ArrowLeft') {
        e.preventDefault();
        goPrev();
      }
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        goNext();
      }
      if (e.key === 'Escape') setZoomed(false);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [goPrev, goNext]);

  // Touch swipe
  const onTouchStart = (e) => {
    touchRef.current.startX = e.touches[0].clientX;
    touchRef.current.startY = e.touches[0].clientY;
  };
  const onTouchEnd = (e) => {
    const dx = e.changedTouches[0].clientX - touchRef.current.startX;
    const dy = e.changedTouches[0].clientY - touchRef.current.startY;
    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 40) {
      dx > 0 ? goPrev() : goNext();
    }
  };

  // Masaüstü: fare ile tutup sürükleyerek görsel değiştirme (dokunmatik swipe
  // zaten var). Yatay sürükleme eşiği aşılınca yakınlaştırma kapatılır ki
  // hover-zoom ile çakışmasın.
  const onImgMouseDown = (e) => {
    if (e.button !== 0 || sorted.length <= 1) return;
    mouseDrag.current = {
      active: true,
      startX: e.clientX,
      startY: e.clientY,
      dx: 0,
      dy: 0,
      didDrag: false,
    };
  };
  const endMouseDrag = () => {
    const d = mouseDrag.current;
    if (!d.active) return;
    d.active = false;
    if (d.didDrag && Math.abs(d.dx) > 45 && Math.abs(d.dx) > Math.abs(d.dy)) {
      if (d.dx > 0) goPrev();
      else goNext();
    }
  };

  // Mouse zoom on hover + sürükleme algılama
  const handleMouseMove = (e) => {
    const d = mouseDrag.current;
    if (d.active) {
      d.dx = e.clientX - d.startX;
      d.dy = e.clientY - d.startY;
      if (!d.didDrag && Math.abs(d.dx) > 8 && Math.abs(d.dx) > Math.abs(d.dy)) {
        d.didDrag = true;
        if (zoomed) setZoomed(false);
      }
      return; // sürüklerken hover-zoom pan çalışmasın
    }
    if (!zoomed) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    setZoomPos({ x, y });
  };

  // ----- Lightbox (pinch / double-tap zoom) -----
  const resetLb = () => {
    setLbScale(1);
    setLbTrans({ x: 0, y: 0 });
  };
  const closeLightbox = () => {
    setLightbox(false);
    resetLb();
  };
  const lbPrev = () => {
    goPrev();
    resetLb();
  };
  const lbNext = () => {
    goNext();
    resetLb();
  };

  // Lock background scroll while the lightbox is open
  useEffect(() => {
    if (!lightbox) return undefined;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [lightbox]);

  const lbTouchStart = (e) => {
    if (e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      pinchRef.current.startDist = Math.hypot(dx, dy);
      pinchRef.current.startScale = lbScale;
    } else if (e.touches.length === 1) {
      const t = e.touches[0];
      pinchRef.current.lastX = t.clientX;
      pinchRef.current.lastY = t.clientY;
      pinchRef.current.startX = t.clientX;
      pinchRef.current.startY = t.clientY;
    }
  };
  const lbTouchMove = (e) => {
    if (e.touches.length === 2 && pinchRef.current.startDist > 0) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const dist = Math.hypot(dx, dy);
      const next = Math.min(
        LB_MAX,
        Math.max(1, pinchRef.current.startScale * (dist / pinchRef.current.startDist))
      );
      setLbScale(next);
      if (next === 1) setLbTrans({ x: 0, y: 0 });
    } else if (e.touches.length === 1 && lbScale > 1) {
      const t = e.touches[0];
      const tx = t.clientX - pinchRef.current.lastX;
      const ty = t.clientY - pinchRef.current.lastY;
      pinchRef.current.lastX = t.clientX;
      pinchRef.current.lastY = t.clientY;
      setLbTrans((p) => ({ x: p.x + tx, y: p.y + ty }));
    }
  };
  const lbTouchEnd = (e) => {
    if (e.touches.length === 0 && e.changedTouches.length === 1) {
      const c = e.changedTouches[0];
      const moved =
        Math.abs(c.clientX - pinchRef.current.startX) + Math.abs(c.clientY - pinchRef.current.startY);
      if (moved < 10) {
        const now = Date.now();
        if (now - lastTapRef.current < 300) {
          // double-tap toggles 1x / 2x
          if (lbScale > 1) resetLb();
          else setLbScale(2);
          lastTapRef.current = 0;
        } else {
          lastTapRef.current = now;
        }
      } else if (lbScale === 1) {
        // horizontal swipe changes image when not zoomed
        const dx = c.clientX - pinchRef.current.startX;
        const dy = c.clientY - pinchRef.current.startY;
        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 40) {
          if (dx > 0) lbPrev();
          else lbNext();
        }
      }
    }
    pinchRef.current.startDist = 0;
  };

  if (sorted.length === 0) {
    return (
      <div
        className="store-gallery-main"
        style={{
          height: 450,
          background: 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 16,
        }}
      >
        <div className="text-center text-muted">
          <FiZoomIn size={40} className="mb-2 opacity-25" />
          <p className="mb-0">Görsel Yok</p>
        </div>
      </div>
    );
  }

  const mainImage = sorted[activeIndex] || sorted[0];
  // Resolve the src declaratively: prefer the full image, fall back to the thumbnail
  // only once the full one has actually failed. Re-renders keep the resolved value, so
  // hovering/zooming never re-triggers a load of a broken URL (the source of the flicker).
  const primarySrc = mainImage.url || mainImage.thumbnailUrl;
  const fallbackSrc = mainImage.thumbnailUrl || mainImage.url;
  const mainSrc = primarySrc && !failedSrc.has(primarySrc) ? primarySrc : fallbackSrc;
  const mainHidden = failedSrc.has(primarySrc) && failedSrc.has(fallbackSrc);
  const onMainError = () => setFailedSrc((prev) => (prev.has(mainSrc) ? prev : new Set(prev).add(mainSrc)));

  return (
    <div className="store-gallery" ref={galleryRef}>
      {/* Main Image */}
      <div
        className="store-gallery-main position-relative"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        onMouseDown={onImgMouseDown}
        onMouseUp={endMouseDrag}
        onMouseMove={handleMouseMove}
        onMouseEnter={() => setZoomed(true)}
        onMouseLeave={() => {
          setZoomed(false);
          endMouseDrag();
        }}
        style={{ cursor: zoomed ? 'crosshair' : 'zoom-in', touchAction: 'pan-y' }}
      >
        <img
          src={mainSrc}
          alt={productName ? `${productName} - görsel ${activeIndex + 1}` : 'Ürün görseli'}
          onError={onMainError}
          draggable={false}
          style={{
            transition: 'opacity 0.35s ease, transform 0.35s ease',
            opacity: isTransitioning ? 0.6 : 1,
            transform: zoomed ? `scale(2)` : isTransitioning ? 'scale(0.97)' : 'scale(1)',
            transformOrigin: zoomed ? `${zoomPos.x}% ${zoomPos.y}%` : 'center',
            visibility: mainHidden ? 'hidden' : 'visible',
          }}
        />

        {/* Navigation Arrows */}
        {sorted.length > 1 && (
          <>
            <button
              className="store-gallery-arrow store-gallery-arrow-left"
              onClick={(e) => {
                e.stopPropagation();
                goPrev();
              }}
              aria-label="Önceki görsel"
            >
              <FiChevronLeft size={22} />
            </button>
            <button
              className="store-gallery-arrow store-gallery-arrow-right"
              onClick={(e) => {
                e.stopPropagation();
                goNext();
              }}
              aria-label="Sonraki görsel"
            >
              <FiChevronRight size={22} />
            </button>
          </>
        )}

        {/* Image counter */}
        {sorted.length > 1 && (
          <div className="store-gallery-counter">
            {activeIndex + 1} / {sorted.length}
          </div>
        )}

        {/* Fullscreen / pinch-zoom button */}
        <button
          className="store-gallery-zoom-btn"
          onClick={(e) => {
            e.stopPropagation();
            setLightbox(true);
          }}
          aria-label="Büyüt"
        >
          <FiZoomIn size={18} />
        </button>
      </div>

      {/* Thumbnails */}
      {sorted.length > 1 && (
        <div className="store-gallery-thumbs" role="tablist">
          {sorted.map((img, i) => (
            <button
              key={img.id || i}
              className={`store-gallery-thumb ${i === activeIndex ? 'active' : ''}`}
              onClick={() => goTo(i)}
              role="tab"
              aria-selected={i === activeIndex}
              aria-label={`Görsel ${i + 1}`}
            >
              <img
                src={img.thumbnailUrl || img.url}
                alt={productName ? `${productName} - görsel ${i + 1}` : `Ürün görseli ${i + 1}`}
                loading="lazy"
              />
            </button>
          ))}
        </div>
      )}

      {/* Fullscreen lightbox — pinch / double-tap zoom, swipe to change image */}
      {lightbox && (
        <div className="store-gallery-lightbox" onClick={closeLightbox} role="dialog" aria-modal="true">
          <button
            className="store-gallery-lb-close"
            onClick={(e) => {
              e.stopPropagation();
              closeLightbox();
            }}
            aria-label="Kapat"
          >
            <FiX size={26} />
          </button>
          <img
            src={mainImage.url || mainImage.thumbnailUrl}
            alt={productName ? `${productName} - büyük görsel` : 'Ürün görseli'}
            className="store-gallery-lb-img"
            draggable={false}
            onClick={(e) => e.stopPropagation()}
            onTouchStart={lbTouchStart}
            onTouchMove={lbTouchMove}
            onTouchEnd={lbTouchEnd}
            style={{
              transform: `translate(${lbTrans.x}px, ${lbTrans.y}px) scale(${lbScale})`,
              transition: pinchRef.current.startDist ? 'none' : 'transform 0.2s ease',
              touchAction: 'none',
              cursor: lbScale > 1 ? 'grab' : 'auto',
            }}
          />
          {sorted.length > 1 && lbScale === 1 && (
            <>
              <button
                className="store-gallery-arrow store-gallery-arrow-left"
                onClick={(e) => {
                  e.stopPropagation();
                  lbPrev();
                }}
                aria-label="Önceki görsel"
              >
                <FiChevronLeft size={24} />
              </button>
              <button
                className="store-gallery-arrow store-gallery-arrow-right"
                onClick={(e) => {
                  e.stopPropagation();
                  lbNext();
                }}
                aria-label="Sonraki görsel"
              >
                <FiChevronRight size={24} />
              </button>
            </>
          )}
          <div className="store-gallery-lb-hint">
            {sorted.length > 1 ? `${activeIndex + 1} / ${sorted.length} · ` : ''}
            Yakınlaştırmak için çift dokun veya parmaklarınızı kullan
          </div>
        </div>
      )}
    </div>
  );
}
