import React, { useCallback, useEffect, useState } from 'react';
import axios from 'axios';

const token = () => localStorage.getItem('auth_token');
const authHeader = () => ({ headers: { Authorization: `Bearer ${token()}` } });

/**
 * Admin page for tuning assistant safety, rate limits, RAG, provider (Azure / OpenAI)
 * and model parameters. Changes are persisted to site_settings and take effect on
 * the next chat/embedding request — no restart required.
 */

// Compatible models per provider (UI hints; free-text is still allowed)
const OPENAI_CHAT_MODELS = [
  { value: 'gpt-4o', label: 'gpt-4o — En güçlü (multimodal)', note: '128K context' },
  { value: 'gpt-4o-mini', label: 'gpt-4o-mini — Hızlı ve ucuz (önerilen)', note: '128K context' },
  { value: 'gpt-4.1', label: 'gpt-4.1 — Yüksek performans', note: '1M context' },
  { value: 'gpt-4.1-mini', label: 'gpt-4.1-mini — Dengeli', note: '1M context' },
  { value: 'gpt-4-turbo', label: 'gpt-4-turbo', note: '128K context' },
  { value: 'gpt-3.5-turbo', label: 'gpt-3.5-turbo — Çok ucuz', note: '16K context' },
  { value: 'o1-mini', label: 'o1-mini — Reasoning', note: 'Reasoning model' },
  { value: 'o3-mini', label: 'o3-mini — Reasoning (yeni)', note: 'Reasoning model' },
];

const OPENAI_EMBEDDING_MODELS = [
  { value: 'text-embedding-3-small', label: 'text-embedding-3-small — Önerilen', note: '1536 dim, ucuz' },
  { value: 'text-embedding-3-large', label: 'text-embedding-3-large — Yüksek kalite', note: '3072 dim' },
  { value: 'text-embedding-ada-002', label: 'text-embedding-ada-002 — Eski', note: '1536 dim, legacy' },
];

const OPENAI_IMAGE_MODELS = [
  {
    value: 'gpt-image-1',
    label: 'gpt-image-1 — Logo/yazı korumada en iyi (önerilen)',
    note: '~$0.07/görsel (medium + yüksek sadakat). Ürün üzerindeki yazı ve logoları en iyi koruyan mod: input_fidelity=high otomatik gönderilir.',
  },
  {
    value: 'gpt-image-1.5',
    label: 'gpt-image-1.5 — En yeni model',
    note: 'Daha yeni ve genel kalitesi yüksek; sadakat parametresi desteklemez.',
  },
  {
    value: 'gpt-image-1-mini',
    label: 'gpt-image-1-mini — En ucuz',
    note: '~$0.01/görsel (medium). Dikkat: ürün üzerindeki küçük yazı ve logoları bozabilir.',
  },
];

const IMAGE_QUALITIES = [
  { value: 'low', label: 'Düşük — en ucuz' },
  { value: 'medium', label: 'Orta — önerilen' },
  { value: 'high', label: 'Yüksek — en pahalı' },
];

const IMAGE_SIZES = [
  { value: '1024x1024', label: '1024×1024 — kare (önerilen)' },
  { value: '1536x1024', label: '1536×1024 — yatay' },
  { value: '1024x1536', label: '1024×1536 — dikey' },
];

export default function AssistantSettingsPage() {
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const resp = await axios.get('/api/admin/assistant/config', authHeader());
      setConfig(resp.data);
    } catch (e) {
      setToast({ type: 'error', msg: 'Ayarlar yüklenemedi.' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const save = async () => {
    setSaving(true);
    try {
      const resp = await axios.post('/api/admin/assistant/config', config, authHeader());
      setConfig(resp.data);
      setToast({
        type: 'success',
        msg: 'Ayarlar kaydedildi. Yeni sağlayıcı/model bir sonraki istekte devreye girecek.',
      });
    } catch (e) {
      setToast({ type: 'error', msg: 'Kayıt başarısız: ' + (e?.response?.data?.message || e.message) });
    } finally {
      setSaving(false);
    }
  };

  const set = (key, value) => setConfig((prev) => ({ ...prev, [key]: value }));

  if (loading)
    return (
      <div className="container-fluid">
        <div className="text-muted py-4">Yükleniyor…</div>
      </div>
    );
  if (!config)
    return (
      <div className="container-fluid">
        <div className="alert alert-danger">Ayarlar yüklenemedi.</div>
      </div>
    );

  const chatProvider = (config['chat.provider'] || 'AZURE').toUpperCase();
  const embProvider = (config['embedding.provider'] || 'AZURE').toUpperCase();

  return (
    <div className="container-fluid">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h2>
          <i className="fas fa-shield-alt me-2 text-primary"></i>
          AI Asistan Ayarları
        </h2>
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? (
            <>
              <span className="spinner-border spinner-border-sm me-1" /> Kaydediliyor…
            </>
          ) : (
            <>
              <i className="fas fa-save me-1"></i> Kaydet
            </>
          )}
        </button>
      </div>

      {toast && (
        <div
          className={`alert alert-${toast.type === 'success' ? 'success' : 'danger'} alert-dismissible fade show`}
        >
          {toast.msg}
          <button type="button" className="btn-close" onClick={() => setToast(null)}></button>
        </div>
      )}

      <p className="text-muted mb-4" style={{ fontSize: 14 }}>
        Değişiklikler anında uygulanır — uygulama yeniden başlatılmaz. Varsayılan değerler
        <code> application.properties</code>'ten okunur; burada değiştirdiğiniz değerler veritabanında
        saklanır ve öncelik alır.
      </p>

      <div className="row g-4">
        {/* ── Chat Connection (provider selectable) ── */}
        <div className="col-12">
          <div className="card border-0 shadow-sm border-start border-primary border-4">
            <div className="card-header bg-primary bg-opacity-10 d-flex justify-content-between align-items-center">
              <strong>
                <i className="fas fa-comment-dots me-1"></i> Chat Modeli (LLM)
              </strong>
              <span className={`badge bg-${chatProvider === 'OPENAI' ? 'success' : 'info'}`}>
                {chatProvider === 'OPENAI' ? 'OpenAI (direkt)' : 'Azure OpenAI'}
              </span>
            </div>
            <div className="card-body">
              <div className="row g-3">
                <div className="col-md-4">
                  <ProviderSelect
                    name="chat-provider"
                    label="Sağlayıcı"
                    value={chatProvider}
                    onChange={(v) => set('chat.provider', v)}
                    help="Azure OpenAI veya düz OpenAI API arasında seçim yapın."
                  />
                </div>
                <div className="col-md-8">
                  {chatProvider === 'AZURE' ? (
                    <TextField
                      label="Azure Endpoint"
                      value={config['chat.endpoint']}
                      onChange={(v) => set('chat.endpoint', v)}
                      placeholder="https://your-resource.openai.azure.com/"
                      help="Boş = application.properties'teki ana Azure endpoint kullanılır."
                    />
                  ) : (
                    <TextField
                      label="OpenAI Base URL (opsiyonel)"
                      value={config['chat.endpoint']}
                      onChange={(v) => set('chat.endpoint', v)}
                      placeholder="https://api.openai.com"
                      help="Varsayılan: api.openai.com — proxy veya uyumlu bir endpoint kullanıyorsanız girin."
                    />
                  )}
                </div>
                <div className="col-md-6">
                  <TextField
                    label={chatProvider === 'AZURE' ? 'API Key (Azure)' : 'OpenAI API Key (sk-...)'}
                    value={config['chat.apiKey']}
                    onChange={(v) => set('chat.apiKey', v)}
                    placeholder={chatProvider === 'AZURE' ? 'Boş = ana API key' : 'sk-proj-...'}
                    help="Değiştirmezseniz mevcut değer korunur (****maskelenir)."
                  />
                </div>
                <div className="col-md-6">
                  {chatProvider === 'OPENAI' ? (
                    <ModelSelect
                      label="Model"
                      value={config['chat.model']}
                      onChange={(v) => set('chat.model', v)}
                      options={OPENAI_CHAT_MODELS}
                      help="OpenAI public modelleri. Hesabınızın erişebildiği bir modeli seçin."
                    />
                  ) : (
                    <TextField
                      label="Azure Deployment Adı"
                      value={config['chat.model']}
                      onChange={(v) => set('chat.model', v)}
                      placeholder="gpt-4o-mini"
                      help="Azure portalında oluşturduğunuz deployment adı (model adı değil)."
                    />
                  )}
                </div>
              </div>
              {chatProvider === 'OPENAI' && (
                <div className="alert alert-info small mt-3 mb-0">
                  <i className="fas fa-info-circle me-1"></i>
                  <strong>OpenAI direkt bağlantı:</strong> Azure'a ihtiyaç yok.
                  <code className="ms-1">platform.openai.com</code>'dan aldığınız <code>sk-proj-...</code>{' '}
                  formatındaki API anahtarı ile çalışır.
                </div>
              )}
            </div>
          </div>
        </div>

        {/* ── Embedding Connection (provider selectable) ── */}
        <div className="col-12">
          <div className="card border-0 shadow-sm border-start border-success border-4">
            <div className="card-header bg-success bg-opacity-10 d-flex justify-content-between align-items-center">
              <strong>
                <i className="fas fa-link me-1"></i> Embedding Modeli (RAG / Vektör Arama)
              </strong>
              <span className={`badge bg-${embProvider === 'OPENAI' ? 'success' : 'info'}`}>
                {embProvider === 'OPENAI' ? 'OpenAI (direkt)' : 'Azure OpenAI'}
              </span>
            </div>
            <div className="card-body">
              <div className="row g-3">
                <div className="col-md-4">
                  <ProviderSelect
                    name="embedding-provider"
                    label="Sağlayıcı"
                    value={embProvider}
                    onChange={(v) => set('embedding.provider', v)}
                    help="Chat'ten farklı bir sağlayıcı seçebilirsiniz (örn. Chat=Azure, Embedding=OpenAI)."
                  />
                </div>
                <div className="col-md-8">
                  {embProvider === 'AZURE' ? (
                    <TextField
                      label="Azure Endpoint (opsiyonel)"
                      value={config['embedding.endpoint']}
                      onChange={(v) => set('embedding.endpoint', v)}
                      placeholder="https://your-resource.openai.azure.com/"
                      help="Boş = chat ile aynı Azure resource. Farklı region için ayrı endpoint girebilirsiniz."
                    />
                  ) : (
                    <TextField
                      label="OpenAI Base URL (opsiyonel)"
                      value={config['embedding.endpoint']}
                      onChange={(v) => set('embedding.endpoint', v)}
                      placeholder="https://api.openai.com"
                      help="Varsayılan: api.openai.com"
                    />
                  )}
                </div>
                <div className="col-md-6">
                  <TextField
                    label={embProvider === 'AZURE' ? 'Azure API Key' : 'OpenAI API Key'}
                    value={config['embedding.apiKey']}
                    onChange={(v) => set('embedding.apiKey', v)}
                    placeholder={embProvider === 'AZURE' ? 'Boş = chat ile aynı' : 'sk-proj-...'}
                    help="Değiştirmezseniz mevcut değer korunur (****maskelenir)."
                  />
                </div>
                <div className="col-md-6">
                  {embProvider === 'OPENAI' ? (
                    <ModelSelect
                      label="Embedding Model"
                      value={config['embedding.deploymentName']}
                      onChange={(v) => set('embedding.deploymentName', v)}
                      options={OPENAI_EMBEDDING_MODELS}
                      help="OpenAI embedding modelleri. text-embedding-3-small en iyi fiyat/performans."
                    />
                  ) : (
                    <TextField
                      label="Azure Deployment Adı"
                      value={config['embedding.deploymentName']}
                      onChange={(v) => set('embedding.deploymentName', v)}
                      placeholder="text-embedding-3-small"
                      help="Azure portalındaki embedding deployment adı."
                    />
                  )}
                </div>
              </div>
              <div className="alert alert-warning small mt-3 mb-0">
                <i className="fas fa-exclamation-triangle me-1"></i>
                <strong>Dikkat:</strong> Embedding modelini değiştirirseniz mevcut vektörler yeni modelle
                uyumsuz olabilir. Yeni indekslenecek dokümanlar için yeniden embedding oluşturulması gerekir.
              </div>
            </div>
          </div>
        </div>

        {/* ── Image generation (AI set cover) ── */}
        <div className="col-12">
          <div
            className="card border-0 shadow-sm border-start border-4"
            style={{ borderLeftColor: '#7c3aed' }}
          >
            <div
              className="card-header d-flex justify-content-between align-items-center"
              style={{ background: 'rgba(124,58,237,0.08)' }}
            >
              <strong>
                <i className="fas fa-magic me-1"></i> Görsel Üretimi — Set Kapak Fotoğrafı
              </strong>
              <span className="badge bg-success">OpenAI (direkt)</span>
            </div>
            <div className="card-body">
              <div className="row g-3">
                <div className="col-md-6">
                  <TextField
                    label="OpenAI API Key (sk-...)"
                    value={config['image.apiKey']}
                    onChange={(v) => set('image.apiKey', v)}
                    placeholder="sk-proj-..."
                    help="Değiştirmezseniz mevcut değer korunur (****maskelenir). Boşsa OPENAI_API_KEY env değişkeni kullanılır."
                  />
                </div>
                <div className="col-md-6">
                  <ModelSelect
                    label="Görsel Modeli"
                    value={config['image.model']}
                    onChange={(v) => set('image.model', v)}
                    options={OPENAI_IMAGE_MODELS}
                    help="Birden çok ürün fotoğrafını tek kapakta birleştirir (images/edits API). Ürün yazı/logolarının bozulmaması için gpt-image-1 önerilir."
                  />
                </div>
                <div className="col-md-6">
                  <SelectField
                    label="Kalite"
                    value={config['image.quality']}
                    onChange={(v) => set('image.quality', v)}
                    options={IMAGE_QUALITIES}
                    help="Kalite arttıkça üretim maliyeti artar."
                  />
                </div>
                <div className="col-md-6">
                  <SelectField
                    label="Boyut"
                    value={config['image.size']}
                    onChange={(v) => set('image.size', v)}
                    options={IMAGE_SIZES}
                    help="Üretilecek kapak fotoğrafının çözünürlüğü."
                  />
                </div>
                <div className="col-12">
                  <label className="form-label fw-semibold">Özel Prompt (opsiyonel)</label>
                  <textarea
                    className="form-control"
                    rows="3"
                    value={config['image.prompt'] ?? ''}
                    onChange={(e) => set('image.prompt', e.target.value)}
                    placeholder="Boş bırakılırsa optimize edilmiş varsayılan İngilizce prompt kullanılır (önerilen)."
                  />
                  <small className="text-muted">
                    Üretim talimatı. Varsayılan prompt; ürünleri aslına sadık şekilde, temiz stüdyo arka
                    planında, hiçbir ek öğe olmadan tek katalog fotoğrafında birleştirir.
                  </small>
                </div>
              </div>
              <div className="alert alert-info small mt-3 mb-0">
                <i className="fas fa-info-circle me-1"></i>
                Bu özellik <strong>Ürün Setleri</strong> düzenleme ekranındaki &quot;Yapay Zeka Kapak
                Fotoğrafı&quot; bölümünde kullanılır. Azure anahtarı geçerli değildir;{' '}
                <code>platform.openai.com</code> anahtarı gerekir.
              </div>
            </div>
          </div>
        </div>

        {/* ── Security: Input ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-danger bg-opacity-10">
              <strong>
                <i className="fas fa-shield-alt me-1"></i> Güvenlik — Girdi (Input)
              </strong>
            </div>
            <div className="card-body">
              <NumberField
                label="Maks. mesaj uzunluğu (karakter)"
                value={config['safety.input.maxLength']}
                onChange={(v) => set('safety.input.maxLength', v)}
                min={100}
                max={16000}
                help="Kullanıcı mesajı bu limiti aşarsa kırpılır. Token abuse ve DoS önlemi."
              />

              <ToggleField
                label="PII Tespit (TC Kimlik, Kart, IBAN, Telefon, Email)"
                checked={config['safety.input.piiDetection']}
                onChange={(v) => set('safety.input.piiDetection', v)}
                help="Kullanıcı mesajında kişisel veri regex ile taranır."
              />

              <SelectField
                label="PII Aksiyon"
                value={config['safety.input.piiAction']}
                onChange={(v) => set('safety.input.piiAction', v)}
                options={[
                  { value: 'REDACT', label: 'Maskele (önerilen) — [KART] olarak değiştirir' },
                  { value: 'WARN', label: 'Sadece logla — mesajı değiştirme' },
                  { value: 'BLOCK', label: 'Engelle — kullanıcıya uyarı göster' },
                ]}
                help="PII tespit edilince ne yapılsın?"
              />

              <ToggleField
                label="Jailbreak Tespit (TR + EN)"
                checked={config['safety.input.jailbreakDetection']}
                onChange={(v) => set('safety.input.jailbreakDetection', v)}
                help="'Ignore previous instructions' gibi saldırı kalıplarını tespit eder. Bloklamaz, sadece loglar."
              />
            </div>
          </div>
        </div>

        {/* ── Security: Output ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-warning bg-opacity-10">
              <strong>
                <i className="fas fa-eye me-1"></i> Güvenlik — Çıktı (Output)
              </strong>
            </div>
            <div className="card-body">
              <ToggleField
                label="Çıktıda PII Maskeleme"
                checked={config['safety.output.piiRedaction']}
                onChange={(v) => set('safety.output.piiRedaction', v)}
                help="LLM yanıtında yanlışlıkla PII varsa maskeleyerek savunma derinliği sağlar."
              />

              <ToggleField
                label="Halüsinasyon Uyarı (deneysel)"
                checked={config['safety.output.hallucinationCheck']}
                onChange={(v) => set('safety.output.hallucinationCheck', v)}
                help="LLM yanıtındaki sayıları tool çıktısıyla karşılaştırır. Sadece loglar, bloklamaz."
              />

              <ToggleField
                label="Log Sanitizasyonu (KVKK)"
                checked={config['safety.logSanitization']}
                onChange={(v) => set('safety.logSanitization', v)}
                help="Sohbet loglarına yazmadan önce PII maskelenir. KVKK uyumluluğu için önerilir."
              />

              <NumberField
                label="Maks. yanıt token"
                value={config.maxResponseTokens}
                onChange={(v) => set('maxResponseTokens', v)}
                min={200}
                max={4000}
                help="LLM'in üretebileceği maksimum token sayısı. 1500 ≈ 600 kelime."
              />
            </div>
          </div>
        </div>

        {/* ── Rate Limits ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-info bg-opacity-10">
              <strong>
                <i className="fas fa-tachometer-alt me-1"></i> Rate Limit
              </strong>
            </div>
            <div className="card-body">
              <NumberField
                label="Misafir — oturum başı maks."
                value={config['ratelimit.guestSessionMax']}
                onChange={(v) => set('ratelimit.guestSessionMax', v)}
                min={1}
                max={20}
                help="Misafir kullanıcı bu kadar mesajdan sonra giriş yapmaya yönlendirilir."
              />
              <NumberField
                label="Misafir — günlük IP limiti"
                value={config['ratelimit.guestIpDaily']}
                onChange={(v) => set('ratelimit.guestIpDaily', v)}
                min={1}
                max={100}
                help="Aynı IP'den günlük toplam misafir mesaj sayısı."
              />
              <NumberField
                label="Müşteri — saatlik"
                value={config['ratelimit.customerHourly']}
                onChange={(v) => set('ratelimit.customerHourly', v)}
                min={5}
                max={200}
              />
              <NumberField
                label="Müşteri — günlük"
                value={config['ratelimit.customerDaily']}
                onChange={(v) => set('ratelimit.customerDaily', v)}
                min={10}
                max={1000}
              />
              <NumberField
                label="WMS — saatlik"
                value={config['ratelimit.wmsHourly']}
                onChange={(v) => set('ratelimit.wmsHourly', v)}
                min={10}
                max={500}
              />
            </div>
          </div>
        </div>

        {/* ── RAG ── */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-success bg-opacity-10">
              <strong>
                <i className="fas fa-brain me-1"></i> RAG (Anlamsal Arama)
              </strong>
            </div>
            <div className="card-body">
              <NumberField
                label="Vector Top-K"
                value={config['rag.vectorTopK']}
                onChange={(v) => set('rag.vectorTopK', v)}
                min={1}
                max={20}
                help="Anlamsal aramada döndürülecek en yakın sonuç sayısı."
              />
              <NumberField
                label="Benzerlik eşiği (cosine distance)"
                value={config['rag.vectorDistanceThreshold']}
                onChange={(v) => set('rag.vectorDistanceThreshold', parseFloat(v))}
                min={0.1}
                max={1.0}
                step={0.05}
                help="0.35 = sadece çok benzer sonuçlar. 0.5 = daha geniş eşleşme. Düşük = daha hassas."
              />
              <NumberField
                label="Chunk boyutu (kelime)"
                value={config['rag.chunkSizeTokens']}
                onChange={(v) => set('rag.chunkSizeTokens', v)}
                min={100}
                max={2000}
                help="Doküman parçalama pencere boyutu. 500 kelime önerilen."
              />
              <NumberField
                label="Chunk overlap (kelime)"
                value={config['rag.chunkOverlapTokens']}
                onChange={(v) => set('rag.chunkOverlapTokens', v)}
                min={0}
                max={200}
                help="Ardışık parçalar arasındaki örtüşme. 50 kelime önerilen."
              />
              <NumberField
                label="Komşu pencere (neighbor window)"
                value={config['rag.neighborWindow']}
                onChange={(v) => set('rag.neighborWindow', v)}
                min={0}
                max={10}
                help="Match bulunan chunk'a ek olarak önce/sonra kaç chunk da LLM'e gönderilsin. 0=kapalı, 2=önerilen. Context loss'u önler (küçük chunk iyi match, ama yalıtık kalmasın)."
              />
              <ToggleField
                label="Hibrit Arama (Vector + BM25)"
                checked={config['rag.hybridEnabled']}
                onChange={(v) => set('rag.hybridEnabled', v)}
                help="Anlamsal (pgvector) + kelime-tabanlı (PostgreSQL full-text) aramayı Reciprocal Rank Fusion ile birleştirir. 'MADDE 6', ürün SKU'su gibi spesifik terimli sorgularda anlamsal aramanın kaçırdığı eşleşmeleri yakalar. Önerilen: AÇIK."
              />
              <NumberField
                label="RRF k sabiti"
                value={config['rag.hybridRrfK']}
                onChange={(v) => set('rag.hybridRrfK', v)}
                min={10}
                max={200}
                help="Reciprocal Rank Fusion sabiti. Standart değer 60. Yüksek = üst sıraları daha az önceliklendirir."
              />
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
        <input
          className="form-check-input"
          type="checkbox"
          role="switch"
          checked={!!checked}
          onChange={(e) => onChange(e.target.checked)}
        />
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
      <input
        type="number"
        className="form-control"
        value={value ?? ''}
        onChange={(e) =>
          onChange(step && step < 1 ? parseFloat(e.target.value) : parseInt(e.target.value, 10))
        }
        min={min}
        max={max}
        step={step || 1}
      />
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function SelectField({ label, value, onChange, options, help }) {
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <select className="form-select" value={value || ''} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function TextField({ label, value, onChange, placeholder, help }) {
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <input
        type="text"
        className="form-control"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder || ''}
      />
      {help && <small className="text-muted">{help}</small>}
    </div>
  );
}

function ProviderSelect({ label, value, onChange, help, name }) {
  // Unique group name so chat/embedding radios don't share state
  const groupName = name || `prov-${label}`;
  const azureId = `${groupName}-azure`;
  const openaiId = `${groupName}-openai`;
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <div className="btn-group w-100" role="group">
        <input
          type="radio"
          name={groupName}
          className="btn-check"
          id={azureId}
          checked={value === 'AZURE'}
          onChange={() => onChange('AZURE')}
        />
        <label className="btn btn-outline-primary" htmlFor={azureId}>
          <i className="fab fa-microsoft me-1"></i>Azure OpenAI
        </label>
        <input
          type="radio"
          name={groupName}
          className="btn-check"
          id={openaiId}
          checked={value === 'OPENAI'}
          onChange={() => onChange('OPENAI')}
        />
        <label className="btn btn-outline-success" htmlFor={openaiId}>
          <i className="fas fa-robot me-1"></i>OpenAI (direkt)
        </label>
      </div>
      {help && <small className="text-muted d-block mt-1">{help}</small>}
    </div>
  );
}

function ModelSelect({ label, value, onChange, options, help }) {
  const currentOption = options.find((o) => o.value === value);
  return (
    <div className="mb-3">
      <label className="form-label fw-semibold">{label}</label>
      <select className="form-select" value={value || ''} onChange={(e) => onChange(e.target.value)}>
        {!currentOption && value && <option value={value}>{value} (özel)</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {currentOption?.note && (
        <small className="text-muted d-block mt-1">
          <i className="fas fa-info-circle me-1"></i>
          {currentOption.note}
        </small>
      )}
      {help && <small className="text-muted d-block">{help}</small>}
    </div>
  );
}
