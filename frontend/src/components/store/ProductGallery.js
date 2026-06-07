import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FiChevronLeft, FiChevronRight, FiZoomIn } from 'react-icons/fi';

export default function ProductGallery({ images, productName }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [zoomed, setZoomed] = useState(false);
  const [zoomPos, setZoomPos] = useState({ x: 50, y: 50 });
  const touchRef = useRef({ startX: 0, startY: 0 });
  const galleryRef = useRef(null);

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

  // Mouse zoom on hover
  const handleMouseMove = (e) => {
    if (!zoomed) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    setZoomPos({ x, y });
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

  return (
    <div className="store-gallery" ref={galleryRef}>
      {/* Main Image */}
      <div
        className="store-gallery-main position-relative"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        onMouseMove={handleMouseMove}
        onMouseEnter={() => setZoomed(true)}
        onMouseLeave={() => setZoomed(false)}
        style={{ cursor: zoomed ? 'crosshair' : 'zoom-in' }}
      >
        <img
          src={mainImage.url || mainImage.thumbnailUrl}
          alt={productName ? `${productName} - görsel ${activeIndex + 1}` : 'Ürün görseli'}
          onError={(e) => {
            // Fall back to the thumbnail variant, then to a neutral placeholder.
            if (mainImage.thumbnailUrl && e.target.src.indexOf(mainImage.thumbnailUrl) === -1) {
              e.target.src = mainImage.thumbnailUrl;
            } else {
              e.target.style.visibility = 'hidden';
            }
          }}
          style={{
            transition: 'opacity 0.35s ease, transform 0.35s ease',
            opacity: isTransitioning ? 0.6 : 1,
            transform: zoomed ? `scale(2)` : isTransitioning ? 'scale(0.97)' : 'scale(1)',
            transformOrigin: zoomed ? `${zoomPos.x}% ${zoomPos.y}%` : 'center',
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
    </div>
  );
}
