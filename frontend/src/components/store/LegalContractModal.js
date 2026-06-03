import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';

/**
 * Displays legal contracts (Distance Sales, Preliminary Information, KVKK) in an
 * inline modal during checkout. Under Turkish e-commerce law (Law 6502 on the
 * Protection of Consumers + Distance Contracts Regulation), it is not legal for
 * the customer to give consent **without seeing** the contract. This modal:
 *
 * 1. Fetches the CMS content via /api/store/pages/{slug}.
 * 2. Shows the contract inside a scrollable box.
 * 3. Keeps the "Accept" button disabled until the user scrolls to the bottom
 *    (legally compliant "proof of reading").
 * 4. On acceptance, calls the onConfirm callback — the parent checks the checkbox
 *    and records a timestamp.
 *
 * Props:
 *   slug      — CMS page slug (e.g. "mesafeli-satis-sozlesmesi")
 *   title     — Modal title
 *   open      — whether the modal is open/closed
 *   onClose   — close callback (without accepting)
 *   onConfirm — acceptance callback (with timestamp)
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

  // Scroll-to-end detection: has the user finished the contract?
  useEffect(() => {
    if (!open) return;
    const el = contentRef.current;
    if (!el) return;
    const handler = () => {
      // 8px tolerance — guards against sub-pixel scrolling
      const atEnd = el.scrollTop + el.clientHeight >= el.scrollHeight - 8;
      if (atEnd) setScrolledToEnd(true);
    };
    el.addEventListener('scroll', handler);
    // If the content is short, it counts as read as soon as the modal opens
    if (el.scrollHeight <= el.clientHeight + 8) setScrolledToEnd(true);
    return () => el.removeEventListener('scroll', handler);
  }, [open, content]);

  // Close on ESC + body scroll lock
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
