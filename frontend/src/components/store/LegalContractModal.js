import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

/**
 * Checkout sırasında yasal sözleşmeleri (Mesafeli Satış, Ön Bilgilendirme, KVKK)
 * inline modal içinde gösterir. Türkiye e-ticaret mevzuatı (6502 sayılı Tüketicinin
 * Korunması Kanunu + Mesafeli Sözleşmeler Yönetmeliği) gereği müşterinin sözleşmeyi
 * **görmeden** onay vermesi yasal değildir. Bu modal:
 *
 * 1. /api/store/pages/{slug} üzerinden CMS içeriğini çeker.
 * 2. Sözleşmeyi scroll'lu kutu içinde gösterir.
 * 3. Kullanıcı en alta scroll edene kadar "Kabul Et" butonu disabled kalır
 *    (mevzuata uygun "okuma kanıtı").
 * 4. Kabul edildiğinde onConfirm callback'i çağırır — parent checkbox'ı işaretler
 *    ve timestamp kaydeder.
 *
 * Props:
 *   slug      — CMS sayfa slug'ı (örn. "mesafeli-satis-sozlesmesi")
 *   title     — Modal başlığı
 *   open      — modal açık/kapalı
 *   onClose   — kapatma callback'i (kabul etmeden)
 *   onConfirm — kabul callback'i (timestamp ile birlikte)
 */
export default function LegalContractModal({ slug, title, open, onClose, onConfirm }) {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [scrolledToEnd, setScrolledToEnd] = useState(false);
  const contentRef = useRef(null);

  useEffect(() => {
    if (!open || !slug) return;
    let cancelled = false;
    setLoading(true);
    setError('');
    setScrolledToEnd(false);
    setContent('');
    axios.get(`/api/store/pages/${slug}`)
      .then(res => {
        if (cancelled) return;
        setContent(res.data?.content || '');
      })
      .catch(err => {
        if (cancelled) return;
        setError(err?.response?.data?.message || 'Sözleşme metni yüklenemedi.');
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [open, slug]);

  // Scroll-to-end detection: kullanıcı sözleşmeyi bitirdi mi?
  useEffect(() => {
    if (!open) return;
    const el = contentRef.current;
    if (!el) return;
    const handler = () => {
      // 8px tolerans — sub-pixel scroll'lara karşı
      const atEnd = el.scrollTop + el.clientHeight >= el.scrollHeight - 8;
      if (atEnd) setScrolledToEnd(true);
    };
    el.addEventListener('scroll', handler);
    // İçerik kısa ise modal açılır açılmaz zaten okunmuş sayılır
    if (el.scrollHeight <= el.clientHeight + 8) setScrolledToEnd(true);
    return () => el.removeEventListener('scroll', handler);
  }, [open, content]);

  // ESC ile kapatma + body scroll lock
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => { if (e.key === 'Escape') onClose && onClose(); };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [open, onClose]);

  if (!open) return null;

  const handleConfirm = () => {
    if (onConfirm) onConfirm(new Date().toISOString());
  };

  return (
    <div
      className="modal fade show d-block"
      tabIndex="-1"
      role="dialog"
      style={{ backgroundColor: 'rgba(0,0,0,0.55)', zIndex: 1060 }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose && onClose(); }}
    >
      <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable" role="document">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className="fas fa-file-contract me-2 text-primary" />
              {title}
            </h5>
            <button type="button" className="btn-close" aria-label="Kapat" onClick={onClose} />
          </div>
          <div
            ref={contentRef}
            className="modal-body"
            style={{ maxHeight: '60vh', overflowY: 'auto', fontSize: '0.9rem', lineHeight: 1.6 }}
          >
            {loading && (
              <div className="text-center text-muted py-4">
                <span className="spinner-border spinner-border-sm me-2" />
                Sözleşme yükleniyor...
              </div>
            )}
            {error && (
              <div className="alert alert-danger" role="alert">
                <i className="fas fa-exclamation-circle me-2" />{error}
              </div>
            )}
            {!loading && !error && (
              <div className="legal-content" dangerouslySetInnerHTML={{ __html: content }} />
            )}
          </div>
          <div className="modal-footer flex-column align-items-stretch">
            {!scrolledToEnd && !loading && !error && (
              <div className="alert alert-info small mb-2 py-2" role="alert">
                <i className="fas fa-info-circle me-1" />
                Kabul etmek için lütfen sözleşmeyi sonuna kadar okuyun.
              </div>
            )}
            <div className="d-flex gap-2">
              <button type="button" className="btn btn-outline-secondary flex-grow-1" onClick={onClose}>
                Vazgeç
              </button>
              <button
                type="button"
                className="btn btn-primary flex-grow-1"
                disabled={!scrolledToEnd || loading || !!error}
                onClick={handleConfirm}
              >
                <i className="fas fa-check me-1" />
                Okudum ve Kabul Ediyorum
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
