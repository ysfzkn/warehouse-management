import React, { useState } from 'react';

export default function ProductGallery({ images }) {
  const [activeIndex, setActiveIndex] = useState(0);

  if (!images || images.length === 0) {
    return <div className="store-gallery-main bg-light d-flex align-items-center justify-content-center" style={{height:400}}>
      <span className="text-muted">Görsel Yok</span>
    </div>;
  }

  const sorted = [...images].sort((a, b) => a.sortOrder - b.sortOrder);
  const mainImage = sorted[activeIndex] || sorted[0];

  return (
    <div className="store-gallery">
      <div className="store-gallery-main">
        <img src={mainImage.url || mainImage.thumbnailUrl} alt="Ürün görseli" />
      </div>
      {sorted.length > 1 && (
        <div className="store-gallery-thumbs" role="tablist">
          {sorted.map((img, i) => (
            <button key={img.id || i} className={`store-gallery-thumb ${i === activeIndex ? 'active' : ''}`}
              onClick={() => setActiveIndex(i)} role="tab" aria-selected={i === activeIndex}
              aria-label={`Görsel ${i+1}`}>
              <img src={img.thumbnailUrl || img.url} alt="" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
