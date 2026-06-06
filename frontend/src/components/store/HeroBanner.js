import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { FiChevronLeft, FiChevronRight, FiPause, FiPlay } from 'react-icons/fi';

export default function HeroBanner() {
  const [banners, setBanners] = useState([]);
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const [progress, setProgress] = useState(0);
  const timerRef = useRef(null);
  const progressRef = useRef(null);
  const SLIDE_DURATION = 6000;

  useEffect(() => {
    axios
      .get('/api/store/pages/banners?position=HERO')
      .then((r) => setBanners(r.data || []))
      .catch(() => {});
  }, []);

  // Auto-slide with progress bar
  useEffect(() => {
    if (banners.length <= 1 || paused) {
      clearInterval(timerRef.current);
      clearInterval(progressRef.current);
      return;
    }

    setProgress(0);
    const startTime = Date.now();

    progressRef.current = setInterval(() => {
      const elapsed = Date.now() - startTime;
      setProgress(Math.min((elapsed / SLIDE_DURATION) * 100, 100));
    }, 50);

    timerRef.current = setInterval(() => {
      setCurrent((c) => (c + 1) % banners.length);
    }, SLIDE_DURATION);

    return () => {
      clearInterval(timerRef.current);
      clearInterval(progressRef.current);
    };
  }, [banners.length, paused, current]);

  const goTo = useCallback((index) => {
    setCurrent(index);
    setProgress(0);
  }, []);

  const prev = useCallback(() => {
    goTo(current === 0 ? banners.length - 1 : current - 1);
  }, [current, banners.length, goTo]);

  const next = useCallback(() => {
    goTo((current + 1) % banners.length);
  }, [current, banners.length, goTo]);

  // Keyboard navigation
  const handleKeyDown = useCallback(
    (e) => {
      if (e.key === 'ArrowLeft') prev();
      if (e.key === 'ArrowRight') next();
      if (e.key === ' ') {
        e.preventDefault();
        setPaused((p) => !p);
      }
    },
    [prev, next]
  );

  if (banners.length === 0) {
    // Default welcome banner when no banners configured
    return (
      <div className="theme-slider">
        <div className="theme-slide theme-slide-default">
          <div className="theme-slide-content">
            <span className="theme-slide-tag">Hoş Geldiniz</span>
            <h2 className="theme-slide-title">Mağazamıza Hoş Geldiniz</h2>
            <p className="theme-slide-desc">En kaliteli ürünleri en uygun fiyatlarla keşfedin</p>
            <Link to="/kategori/tumu" className="theme-slide-cta">
              Ürünleri Keşfet
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const banner = banners[current];

  return (
    <div
      className="theme-slider"
      role="region"
      aria-roledescription="carousel"
      aria-label="Kampanya Slaytları"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onKeyDown={handleKeyDown}
      tabIndex={0}
    >
      {/* Slides — real <img> so the full uploaded banner shows at its natural
          aspect ratio (full width, no cropping). Title/CTA overlay only when set. */}
      <div className={`theme-slide ${banner.imageUrl ? 'theme-slide--image' : ''}`}>
        {banner.imageUrl && (
          <img src={banner.imageUrl} alt={banner.title || 'Kampanya görseli'} className="theme-slide-img" />
        )}
        {banner.title && <div className="theme-slide-overlay" />}
        {banner.title && (
          <div className="theme-slide-content">
            <h2 className="theme-slide-title">{banner.title}</h2>
            {banner.link && (
              <a href={banner.link} className="theme-slide-cta">
                Detaylı İncele
              </a>
            )}
          </div>
        )}
      </div>

      {/* Navigation Arrows */}
      {banners.length > 1 && (
        <>
          <button
            className="theme-slider-arrow theme-slider-arrow-left"
            onClick={prev}
            aria-label="Önceki slayt"
          >
            <FiChevronLeft size={24} />
          </button>
          <button
            className="theme-slider-arrow theme-slider-arrow-right"
            onClick={next}
            aria-label="Sonraki slayt"
          >
            <FiChevronRight size={24} />
          </button>
        </>
      )}

      {/* Bottom Controls */}
      {banners.length > 1 && (
        <div className="theme-slider-controls">
          {/* Dots */}
          <div className="theme-slider-dots">
            {banners.map((_, i) => (
              <button
                key={i}
                className={`theme-slider-dot ${i === current ? 'active' : ''}`}
                onClick={() => goTo(i)}
                aria-label={`Slayt ${i + 1}`}
                aria-current={i === current ? 'true' : undefined}
              />
            ))}
          </div>

          {/* Pause/Play */}
          <button
            className="theme-slider-pause"
            onClick={() => setPaused((p) => !p)}
            aria-label={paused ? 'Oynat' : 'Duraklat'}
          >
            {paused ? <FiPlay size={14} /> : <FiPause size={14} />}
          </button>

          {/* Counter */}
          <span className="theme-slider-counter">
            {current + 1} / {banners.length}
          </span>
        </div>
      )}

      {/* Progress Bar */}
      {banners.length > 1 && !paused && (
        <div className="theme-slider-progress">
          <div className="theme-slider-progress-bar" style={{ width: `${progress}%` }} />
        </div>
      )}
    </div>
  );
}
