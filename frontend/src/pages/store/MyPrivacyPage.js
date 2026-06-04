import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useToast } from '../../components/store/Toast';

/**
 * KVKK Article 11(e) — your personal data protection rights:
 *   • Download your data (data portability / GDPR Article 20)
 *   • Request account deletion (right to erasure / GDPR Article 17)
 *
 * Authenticated customers reach this page via `/hesabim/gizlilik`.
 */
export default function MyPrivacyPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [exportLoading, setExportLoading] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [deleteReason, setDeleteReason] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);

  const getAuthHeaders = () => {
    const t = localStorage.getItem('customer_token');
    return t ? { Authorization: `Bearer ${t}` } : {};
  };

  const handleExport = async () => {
    setExportLoading(true);
    try {
      const res = await axios.get('/api/store/account/data-export', {
        headers: getAuthHeaders(),
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/json' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `kisisel-verilerim-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Verileriniz indirildi.');
    } catch (e) {
      toast.error(e.response?.data?.message || 'Veri ihracı başarısız.');
    } finally {
      setExportLoading(false);
    }
  };

  const handleDelete = async () => {
    if (deleteConfirmText !== 'HESABIMI SİL') {
      toast.warning('Lütfen onay metnini tam olarak yazın: HESABIMI SİL');
      return;
    }
    setDeleteLoading(true);
    try {
      await axios.delete('/api/store/account', {
        headers: getAuthHeaders(),
        data: { confirmation: 'DELETE_MY_DATA', reason: deleteReason.trim() || null },
      });
      // Logout
      localStorage.removeItem('customer_token');
      localStorage.removeItem('customer_refresh_token');
      toast.success('Hesabınız anonimleştirildi. Çıkış yapılıyor...');
      setTimeout(() => navigate('/'), 1500);
    } catch (e) {
      const status = e.response?.status;
      const msg = e.response?.data?.message || 'Hesap silme başarısız.';
      if (status === 409) {
        toast.warning(msg);
      } else {
        toast.error(msg);
      }
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="container my-4" style={{ maxWidth: 760 }}>
      <h1 className="h4 fw-bold mb-1">Gizlilik & Verilerim</h1>
      <p className="text-muted small mb-4">
        KVKK Madde 11 ve GDPR kapsamında kişisel verileriniz üzerindeki haklarınızı buradan kullanabilirsiniz.
      </p>

      {/* ── Data Export ── */}
      <div className="card mb-3">
        <div className="card-body">
          <h2 className="h6 fw-semibold mb-2">
            <i className="fas fa-file-export text-primary me-2" />
            Kişisel Verilerimi İndir
          </h2>
          <p className="small text-muted mb-3">
            Hesabınıza ait tüm bilgileri (profil, adresler, siparişler, yorumlar, bildirim tercihleri)
            JSON formatında indirin. Bu dosyayı arşivleyebilir veya başka bir platforma taşıyabilirsiniz.
          </p>
          <button type="button" className="btn btn-outline-primary" onClick={handleExport} disabled={exportLoading}>
            {exportLoading ? <><span className="spinner-border spinner-border-sm me-2" />Hazırlanıyor...</> : <><i className="fas fa-download me-1" />Verilerimi İndir (JSON)</>}
          </button>
        </div>
      </div>

      {/* ── Account Deletion ── */}
      <div className="card border-danger">
        <div className="card-body">
          <h2 className="h6 fw-semibold mb-2 text-danger">
            <i className="fas fa-trash-alt me-2" />
            Hesabımı Sil
          </h2>
          <p className="small text-muted mb-2">
            Hesap silme talebiniz aşağıdaki şekilde işlenir:
          </p>
          <ul className="small text-muted mb-3 ps-3">
            <li>Adınız, e-postanız, telefonunuz ve adresleriniz kalıcı olarak silinir.</li>
            <li>Yorumlarınız "Anonim Kullanıcı" olarak görünmeye devam eder (ürün puanlamaları için).</li>
            <li>Sipariş ve fatura geçmişiniz yasal saklama gereği <strong>10 yıl</strong> korunur (Türk Vergi Mevzuatı).</li>
            <li>Aktif siparişiniz varsa silme talebi reddedilir — önce sipariş tamamlanmalı veya iptal edilmelidir.</li>
            <li>Bu işlem <strong>geri alınamaz</strong>.</li>
          </ul>
          {!deleteOpen ? (
            <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => setDeleteOpen(true)}>
              Hesap Silme Talebi Başlat
            </button>
          ) : (
            <div className="border border-danger rounded p-3 bg-light">
              <div className="mb-2">
                <label className="form-label small fw-semibold">Silme nedeni (opsiyonel)</label>
                <textarea className="form-control form-control-sm" rows="2"
                  value={deleteReason} onChange={e => setDeleteReason(e.target.value)}
                  placeholder="Örn. Bu siteyi artık kullanmıyorum" />
              </div>
              <div className="mb-3">
                <label className="form-label small fw-semibold text-danger">
                  Onaylamak için aşağıdaki kutuya <strong>HESABIMI SİL</strong> yazın
                </label>
                <input type="text" className="form-control form-control-sm"
                  value={deleteConfirmText} onChange={e => setDeleteConfirmText(e.target.value)}
                  placeholder="HESABIMI SİL" />
              </div>
              <div className="d-flex gap-2">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => { setDeleteOpen(false); setDeleteConfirmText(''); setDeleteReason(''); }} disabled={deleteLoading}>
                  Vazgeç
                </button>
                <button type="button" className="btn btn-danger btn-sm" onClick={handleDelete}
                  disabled={deleteLoading || deleteConfirmText !== 'HESABIMI SİL'}>
                  {deleteLoading ? <><span className="spinner-border spinner-border-sm me-1" />İşleniyor...</> : 'Hesabımı Kalıcı Olarak Sil'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
