import React, { useEffect, useState } from 'react';
import axios from 'axios';

/**
 * Admin page for tuning assistant safety, rate limits, RAG and response
 * parameters. Changes are persisted to site_settings and take effect on
 * the next chat request — no restart required.
 */
export default function AssistantSettingsPage() {
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);

  const token = () => localStorage.getItem('auth_token');
  const authHeader = () => ({ headers: { Authorization: `Bearer ${token()}` } });

  const load = async () => {
    setLoading(true);
    try {
      const resp = await axios.get('/api/admin/assistant/config', authHeader());
      setConfig(resp.data);
    } catch (e) {
      setToast({ type: 'error', msg: 'Ayarlar yüklenemedi.' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const save = async () => {
    setSaving(true);
    try {
      const resp = await axios.post('/api/admin/assistant/config', config, authHeader());
      setConfig(resp.data);
      setToast({ type: 'success', msg: 'Ayarlar kaydedildi.' });
    } catch (e) {
      setToast({ type: 'error', msg: 'Kayıt başarısız: ' + (e?.response?.data?.message || e.message) });
    } finally {
      setSaving(false);
    }
  };

  const set = (key, value) => setConfig(prev => ({ ...prev, [key]: value }));

  if (loading) return <div className="container-fluid"><div className="text-muted py-4">Yükleniyor…</div></div>;
  if (!config) return <div className="container-fluid"><div className="alert alert-danger">Ayarlar yüklenemedi.</div></div>;

  return (
    <div className="container-fluid">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h2>
          <i className="fas fa-shield-alt me-2 text-primary"></i>
          AI Asistan Ayarları
        </h2>
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? (
            <><span className="spinner-border spinner-border-sm me-1" /> Kaydediliyor…</>
          ) : (
            <><i className="fas fa-save me-1"></i> Kaydet</>
          )}
        </button>
      </div>

      {toast && (
        <div className={`alert alert-${toast.type === 'success' ? 'success' : 'danger'} alert-dismissible fade show`}>
          {toast.msg}
          <button type="button" className="btn-close" onClick={() => setToast(null)}></button>
        </div>
      )}

      <p className="text-muted mb-4" style={{ fontSize: 14 }}>
        Değişiklikler anında uygulanır — uygulama yeniden başlatılmaz. Varsayılan değerler
        <code> application.properties</code>'ten okunur; burada değiştirdiğiniz değerler veritabanında saklanır ve öncelik alır.
      </p>

      <div className="row g-4">
        {/* ── Güvenlik: Input ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-danger bg-opacity-10">
              <strong><i className="fas fa-shield-alt me-1"></i> Güvenlik — Girdi (Input)</strong>
            </div>
            <div className="card-body">
              <NumberField label="Maks. mesaj uzunluğu (karakter)" value={config['safety.input.maxLength']}
                onChange={v => set('safety.input.maxLength', v)} min={100} max={16000}
                help="Kullanıcı mesajı bu limiti aşarsa kırpılır. Token abuse ve DoS önlemi." />

              <ToggleField label="PII Tespit (TC Kimlik, Kart, IBAN, Telefon, Email)" checked={config['safety.input.piiDetection']}
                onChange={v => set('safety.input.piiDetection', v)}
                help="Kullanıcı mesajında kişisel veri regex ile taranır." />

              <SelectField label="PII Aksiyon" value={config['safety.input.piiAction']}
                onChange={v => set('safety.input.piiAction', v)}
                options={[
                  { value: 'REDACT', label: 'Maskele (önerilen) — [KART] olarak değiştirir' },
                  { value: 'WARN', label: 'Sadece logla — mesajı değiştirme' },
                  { value: 'BLOCK', label: 'Engelle — kullanıcıya uyarı göster' },
                ]}
                help="PII tespit edilince ne yapılsın?" />

              <ToggleField label="Jailbreak Tespit (TR + EN)" checked={config['safety.input.jailbreakDetection']}
                onChange={v => set('safety.input.jailbreakDetection', v)}
                help="'Ignore previous instructions' gibi saldırı kalıplarını tespit eder. Bloklamaz, sadece loglar." />
            </div>
          </div>
        </div>

        {/* ── Güvenlik: Output ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-warning bg-opacity-10">
              <strong><i className="fas fa-eye me-1"></i> Güvenlik — Çıktı (Output)</strong>
            </div>
            <div className="card-body">
              <ToggleField label="Çıktıda PII Maskeleme" checked={config['safety.output.piiRedaction']}
                onChange={v => set('safety.output.piiRedaction', v)}
                help="LLM yanıtında yanlışlıkla PII varsa maskeleyerek savunma derinliği sağlar." />

              <ToggleField label="Halüsinasyon Uyarı (deneysel)" checked={config['safety.output.hallucinationCheck']}
                onChange={v => set('safety.output.hallucinationCheck', v)}
                help="LLM yanıtındaki sayıları tool çıktısıyla karşılaştırır. Sadece loglar, bloklamaz." />

              <ToggleField label="Log Sanitizasyonu (KVKK)" checked={config['safety.logSanitization']}
                onChange={v => set('safety.logSanitization', v)}
                help="Sohbet loglarına yazmadan önce PII maskelenir. KVKK uyumluluğu için önerilir." />

              <NumberField label="Maks. yanıt token" value={config.maxResponseTokens}
                onChange={v => set('maxResponseTokens', v)} min={200} max={4000}
                help="LLM'in üretebileceği maksimum token sayısı. 1500 ≈ 600 kelime." />
            </div>
          </div>
        </div>

        {/* ── Rate Limits ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-info bg-opacity-10">
              <strong><i className="fas fa-tachometer-alt me-1"></i> Rate Limit</strong>
            </div>
            <div className="card-body">
              <NumberField label="Misafir — oturum başı maks." value={config['ratelimit.guestSessionMax']}
                onChange={v => set('ratelimit.guestSessionMax', v)} min={1} max={20}
                help="Misafir kullanıcı bu kadar mesajdan sonra giriş yapmaya yönlendirilir." />
              <NumberField label="Misafir — günlük IP limiti" value={config['ratelimit.guestIpDaily']}
                onChange={v => set('ratelimit.guestIpDaily', v)} min={1} max={100}
                help="Aynı IP'den günlük toplam misafir mesaj sayısı." />
              <NumberField label="Müşteri — saatlik" value={config['ratelimit.customerHourly']}
                onChange={v => set('ratelimit.customerHourly', v)} min={5} max={200} />
              <NumberField label="Müşteri — günlük" value={config['ratelimit.customerDaily']}
                onChange={v => set('ratelimit.customerDaily', v)} min={10} max={1000} />
              <NumberField label="WMS — saatlik" value={config['ratelimit.wmsHourly']}
                onChange={v => set('ratelimit.wmsHourly', v)} min={10} max={500} />
            </div>
          </div>
        </div>

        {/* ── RAG ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-success bg-opacity-10">
              <strong><i className="fas fa-brain me-1"></i> RAG (Anlamsal Arama)</strong>
            </div>
            <div className="card-body">
              <NumberField label="Vector Top-K" value={config['rag.vectorTopK']}
                onChange={v => set('rag.vectorTopK', v)} min={1} max={20}
                help="Anlamsal aramada döndürülecek en yakın sonuç sayısı." />
              <NumberField label="Benzerlik eşiği (cosine distance)" value={config['rag.vectorDistanceThreshold']}
                onChange={v => set('rag.vectorDistanceThreshold', parseFloat(v))} min={0.1} max={1.0} step={0.05}
                help="0.35 = sadece çok benzer sonuçlar. 0.5 = daha geniş eşleşme. Düşük = daha hassas." />
              <NumberField label="Chunk boyutu (kelime)" value={config['rag.chunkSizeTokens']}
                onChange={v => set('rag.chunkSizeTokens', v)} min={100} max={2000}
                help="Doküman parçalama pencere boyutu. 500 kelime önerilen." />
              <NumberField label="Chunk overlap (kelime)" value={config['rag.chunkOverlapTokens']}
                onChange={v => set('rag.chunkOverlapTokens', v)} min={0} max={200}
                help="Ardışık parçalar arasındaki örtüşme. 50 kelime önerilen." />
            </div>
          </div>
        </div>

        {/* ── Embedding Bağlantısı ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-primary bg-opacity-10">
              <strong><i className="fas fa-link me-1"></i> Embedding Bağlantısı</strong>
            </div>
            <div className="card-body">
              <p className="text-muted mb-3" style={{ fontSize: 13 }}>
                Boş bırakılırsa ana Azure OpenAI endpoint ve API key kullanılır.
                Farklı region/subscription için ayrı bir Azure resource girebilirsiniz.
              </p>
              <TextField label="Embedding Endpoint" value={config['embedding.endpoint']}
                onChange={v => set('embedding.endpoint', v)}
                placeholder="https://your-resource.openai.azure.com/"
                help="Boş = ana chat endpoint'i kullanılır." />
              <TextField label="Embedding API Key" value={config['embedding.apiKey']}
                onChange={v => set('embedding.apiKey', v)}
                placeholder="Boş = ana API key kullanılır"
                help="Değiştirmezseniz mevcut değer korunur (maskelenmiş görünür)." />
              <TextField label="Embedding Deployment Adı" value={config['embedding.deploymentName']}
                onChange={v => set('embedding.deploymentName', v)}
                placeholder="text-embedding-3-small"
                help="Azure'daki embedding model deployment adı." />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Reusable form components ──

function ToggleField({ label, checked, onChange, help }) {
  return (
    <div className="mb-3">
      <div className="form-check form-switch">
        <input className="form-check-input" type="checkbox" role="switch"
          checked={!!checked} onChange={e => onChange(e.target.checked)} />
        <label className="form-check-label">{label}</label>
      </div>
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function NumberField({ label, value, onChange, min, max, step, help }) {
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <input type="number" className="form-control" value={value ?? ''}
        onChange={e => onChange(step && step < 1 ? parseFloat(e.target.value) : parseInt(e.target.value, 10))}
        min={min} max={max} step={step || 1} />
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function SelectField({ label, value, onChange, options, help }) {
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <select className="form-select" value={value || ''}
        onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function TextField({ label, value, onChange, placeholder, help }) {
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <input type="text" className="form-control" value={value ?? ''}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder || ''} />
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}
