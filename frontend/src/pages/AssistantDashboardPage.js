import React, { useEffect, useState } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import confirmDialog from '../utils/confirmDialog';

/**
 * Admin dashboard for the Cezeri assistant platform. Shows per-profile
 * conversation counts, aggregate cost, and a "Reindex products"
 * action for the product embedding backfill.
 */
export default function AssistantDashboardPage() {
  const [kpis, setKpis] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reindexing, setReindexing] = useState(false);
  const [reindexResult, setReindexResult] = useState(null);
  const [error, setError] = useState(null);
  const [ragAvailable, setRagAvailable] = useState(null);
  const [initRagLoading, setInitRagLoading] = useState(false);
  const [initRagResult, setInitRagResult] = useState(null);
  const [flags, setFlags] = useState({ wmsEnabled: true, storeEnabled: true });
  const [flagSaving, setFlagSaving] = useState(false);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();

  const token = () => localStorage.getItem('auth_token');
  const authHeader = () => ({ headers: { Authorization: `Bearer ${token()}` } });

  const load = async () => {
    setLoading(true);
    try {
      const resp = await axios.get('/api/admin/assistant/dashboard', authHeader());
      setKpis(resp.data);
      if (typeof resp.data?.ragAvailable === 'boolean') {
        setRagAvailable(resp.data.ragAvailable);
      }
      if (resp.data?.flags) {
        setFlags({
          wmsEnabled: resp.data.flags.wmsEnabled !== false,
          storeEnabled: resp.data.flags.storeEnabled !== false,
        });
      }
      setError(null);
    } catch (e) {
      setError('KPI verileri alınamadı.');
    } finally {
      setLoading(false);
    }
  };

  const toggleFlag = async (key) => {
    const willEnable = !flags[key];
    const label = key === 'storeEnabled' ? 'Store Chatbot' : 'WMS Asistanı';
    console.log(
      `[AssistantFlags] toggleFlag called — key=${key}, current=${flags[key]}, willBe=${willEnable}`
    );

    const code = await askCode({
      description: `${label} için durumu "${willEnable ? 'AÇIK' : 'KAPALI'}" olarak değiştirmek istiyor musunuz? Güvenlik şifresini girin.`,
    });
    if (!code) {
      console.log('[AssistantFlags] toggle cancelled by user (no security code)');
      return;
    }

    const next = { ...flags, [key]: willEnable };
    setFlagSaving(true);
    try {
      console.log('[AssistantFlags] POST /api/admin/assistant/flags →', next);
      const resp = await axios.post('/api/admin/assistant/flags', next, {
        headers: {
          ...authHeader().headers,
          'X-ADMIN-SECURITY-CODE': code,
        },
      });
      console.log('[AssistantFlags] POST response:', resp.data);
      if (resp.data) {
        setFlags({
          wmsEnabled: resp.data.wmsEnabled !== false,
          storeEnabled: resp.data.storeEnabled !== false,
        });
      }
      // Broadcast so Admin layout re-fetches immediately.
      // Note: cross-tab (store tab) relies on focus/visibility handlers.
      window.dispatchEvent(new Event('assistant-flags-changed'));
      setError(null);
    } catch (e) {
      console.error('[AssistantFlags] toggle failed:', e);
      if (e?.response?.status === 403) {
        setError('Güvenlik şifresi hatalı.');
      } else {
        setError('Toggle kaydedilemedi: ' + (e?.response?.data?.message || e.message));
      }
    } finally {
      setFlagSaving(false);
    }
  };

  const initRag = async () => {
    const ok = await confirmDialog({
      title: 'RAG Şeması Oluşturulsun mu?',
      message:
        'pgvector şeması oluşturulmayı denenecek. Sunucuda pgvector eklentisinin yüklü olması gerekir.',
      confirmText: 'Evet, Dene',
      variant: 'primary',
      icon: 'fa-database',
    });
    if (!ok) return;
    setInitRagLoading(true);
    try {
      const resp = await axios.post('/api/admin/assistant/rag/init', {}, authHeader());
      setInitRagResult(resp.data);
      if (resp.data?.ragNowAvailable) setRagAvailable(true);
    } catch (e) {
      setInitRagResult({
        message: 'RAG şema başlatma başarısız: ' + (e?.response?.data?.message || e.message),
      });
    } finally {
      setInitRagLoading(false);
    }
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    load();
  }, []);

  const onReindexProducts = async () => {
    const ok = await confirmDialog({
      title: 'Yeniden İndekslensin mi?',
      message: 'Tüm ürünler için embedding yeniden hesaplanacak. Bu işlem birkaç dakika sürebilir.',
      confirmText: 'Evet, Başlat',
      variant: 'primary',
      icon: 'fa-sync',
    });
    if (!ok) return;
    setReindexing(true);
    try {
      const resp = await axios.post('/api/admin/assistant/products/reindex', {}, authHeader());
      setReindexResult(`İşlendi: ${resp.data.processed} ürün`);
    } catch (e) {
      setReindexResult('Hata: ' + (e?.response?.data?.message || e.message));
    } finally {
      setReindexing(false);
    }
  };

  const renderProfileCard = (profileKey, label, stats) => {
    if (!stats) return null;
    return (
      <div className="col-md-6 mb-3" key={profileKey}>
        <div className="card">
          <div className="card-header">
            <strong>{label}</strong>
          </div>
          <div className="card-body">
            <table className="table table-sm mb-0">
              <tbody>
                <tr>
                  <td>Bugün</td>
                  <td className="text-end">
                    <strong>{stats.today}</strong> sohbet
                  </td>
                  <td className="text-end text-muted">${formatCost(stats.costToday)}</td>
                </tr>
                <tr>
                  <td>Son 7 gün</td>
                  <td className="text-end">
                    <strong>{stats.last7Days}</strong> sohbet
                  </td>
                  <td className="text-end text-muted">${formatCost(stats.costLast7Days)}</td>
                </tr>
                <tr>
                  <td>Son 30 gün</td>
                  <td className="text-end">
                    <strong>{stats.last30Days}</strong> sohbet
                  </td>
                  <td className="text-end text-muted">${formatCost(stats.costLast30Days)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="container-fluid">
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h2>Cezeri Asistan Dashboard</h2>
        <div>
          <button className="btn btn-outline-secondary me-2" onClick={load} disabled={loading}>
            Yenile
          </button>
          <button className="btn btn-outline-primary" onClick={onReindexProducts} disabled={reindexing}>
            {reindexing ? 'İndeksleniyor…' : 'Ürünleri Yeniden İndeksle'}
          </button>
        </div>
      </div>

      {reindexResult && <div className="alert alert-info">{reindexResult}</div>}
      {error && <div className="alert alert-danger">{error}</div>}

      <div className="card mb-4">
        <div className="card-header">
          <strong>Asistan Görünürlük Kontrolü</strong>
        </div>
        <div className="card-body">
          <p className="text-muted mb-3" style={{ fontSize: 14 }}>
            Asistan widget'ını tek tıkla devre dışı bırakın veya tekrar açın. Değişiklik anında tüm
            kullanıcılarda etkili olur ve ilgili backend endpoint'ini de bloklar (defense-in-depth).
          </p>

          <div
            className="d-flex align-items-center justify-content-between mb-3 p-3"
            style={{ background: '#f8fafc', borderRadius: 8 }}
          >
            <div>
              <div style={{ fontWeight: 600 }}>WMS (Admin / Depo) Asistanı</div>
              <div className="text-muted" style={{ fontSize: 13 }}>
                Warehouse users için. Stok sorgulama, ürün arama, transfer logları, audit kayıtları.
              </div>
              <div className="mt-1" style={{ fontSize: 12 }}>
                Durum:{' '}
                <span style={{ fontWeight: 600, color: flags.wmsEnabled ? '#10b981' : '#ef4444' }}>
                  {flags.wmsEnabled ? 'AKTİF' : 'KAPALI'}
                </span>
              </div>
            </div>
            <div className="form-check form-switch" style={{ transform: 'scale(1.6)', marginRight: 20 }}>
              <input
                type="checkbox"
                className="form-check-input"
                id="flag-wms"
                checked={flags.wmsEnabled}
                onChange={() => toggleFlag('wmsEnabled')}
                disabled={flagSaving}
                role="switch"
              />
            </div>
          </div>

          <div
            className="d-flex align-items-center justify-content-between p-3"
            style={{ background: '#f8fafc', borderRadius: 8 }}
          >
            <div>
              <div style={{ fontWeight: 600 }}>Store (E-Ticaret) Asistanı</div>
              <div className="text-muted" style={{ fontSize: 13 }}>
                Müşteri tarafı. Ürün arama, karşılaştırma, sipariş takibi, SSS.
              </div>
              <div className="mt-1" style={{ fontSize: 12 }}>
                Durum:{' '}
                <span style={{ fontWeight: 600, color: flags.storeEnabled ? '#10b981' : '#ef4444' }}>
                  {flags.storeEnabled ? 'AKTİF' : 'KAPALI'}
                </span>
              </div>
            </div>
            <div className="form-check form-switch" style={{ transform: 'scale(1.6)', marginRight: 20 }}>
              <input
                type="checkbox"
                className="form-check-input"
                id="flag-store"
                checked={flags.storeEnabled}
                onChange={() => toggleFlag('storeEnabled')}
                disabled={flagSaving}
                role="switch"
              />
            </div>
          </div>
        </div>
      </div>

      {ragAvailable === false && (
        <div className="alert alert-warning d-flex justify-content-between align-items-center">
          <div>
            <strong>⚠️ RAG (pgvector) devre dışı.</strong> Ürün anlamsal arama ve SSS doküman araması şu an
            kullanılamıyor. Docker Postgres imajını <code>pgvector/pgvector:pg15</code> olarak güncelleyip
            servisi yeniden başlatın, sonra aşağıdaki butonla şemayı kurun.
          </div>
          <button className="btn btn-warning ms-3" onClick={initRag} disabled={initRagLoading}>
            {initRagLoading ? 'Başlatılıyor…' : 'RAG Şemayı Etkinleştir'}
          </button>
        </div>
      )}
      {ragAvailable === true && (
        <div className="alert alert-success py-2">✅ RAG etkin — pgvector ve vektör tabloları hazır.</div>
      )}
      {initRagResult && (
        <div className={`alert ${initRagResult.ragNowAvailable ? 'alert-success' : 'alert-info'}`}>
          {initRagResult.message}
        </div>
      )}

      {loading ? (
        <div className="text-muted">Yükleniyor…</div>
      ) : kpis ? (
        <div className="row">
          {renderProfileCard('STORE', 'E-ticaret (Store)', kpis.STORE)}
          {renderProfileCard('WMS', 'Depo Yönetimi (WMS)', kpis.WMS)}
        </div>
      ) : null}

      <div className="card mt-4">
        <div className="card-header">Notlar</div>
        <div className="card-body">
          <ul className="mb-0">
            <li>
              Maliyet tahminleri Azure OpenAI fiyat tablosuna göre hesaplanır. Gerçek fatura için Azure
              portalını kontrol edin.
            </li>
            <li>Ürün embedding backfill idempotenttir; içeriği değişmeyen ürünler atlanır.</li>
            <li>
              Sohbet detaylarını görmek için <strong>Loglar</strong> sayfasını kullanın.
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}

function formatCost(value) {
  if (value == null) return '0.0000';
  const n = typeof value === 'number' ? value : parseFloat(value);
  return isNaN(n) ? '0.0000' : n.toFixed(4);
}
