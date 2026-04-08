import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';

const PROTOCOLS = {
  NESTPAY: { label: 'NestPay (Asseco)', banks: ['ISBANK','AKBANK','HALKBANK','TEB','DENIZBANK','ING','ZIRAAT','KUVEYT','QNB','ANADOLU'], credentialType: 'pos' },
  GVP: { label: 'GVP (Garanti)', banks: ['GARANTI'], credentialType: 'pos' },
  PAYTR: { label: 'PayTR', banks: [], credentialType: 'paytr' },
  IYZICO: { label: 'iyzico', banks: [], credentialType: 'iyzico' },
};

const BANK_LABELS = {
  ISBANK: 'İş Bankası', AKBANK: 'Akbank', HALKBANK: 'Halkbank', TEB: 'TEB',
  DENIZBANK: 'DenizBank', ING: 'ING Bank', ZIRAAT: 'Ziraat Bankası', KUVEYT: 'Kuveyt Türk',
  QNB: 'QNB Finansbank', ANADOLU: 'Anadolubank', GARANTI: 'Garanti BBVA', YAPI_KREDI: 'Yapı Kredi',
};

const EMPTY_FORM = {
  code:'', displayName:'', gatewayProtocol:'NESTPAY', bankCode:'', merchantId:'', terminalId:'',
  storeKey:'', provisionPassword:'', apiKey:'', secretKey:'', baseUrl:'', threeDUrl:'', callbackUrl:'',
  active:false, defaultGateway:false, sandbox:true, priority:100, supportedCards:'VISA,MASTERCARD,TROY', maxInstallments:12,
};

export default function AdminPaymentGateways() {
  const [gateways, setGateways] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({...EMPTY_FORM});
  const [showStoreKey, setShowStoreKey] = useState(false);
  const [showProvPass, setShowProvPass] = useState(false);
  const [testResult, setTestResult] = useState(null);
  const { askCode, SecurityCodePrompt } = useSecurityCodePrompt();
  const toast = useAdminToast();

  const fetchGateways = useCallback(() => {
    setLoading(true);
    axios.get('/api/admin/payment-gateways').then(r => setGateways(r.data || []))
      .catch(() => {}).finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchGateways(); }, [fetchGateways]);

  const withCode = async (desc, fn) => {
    const code = await askCode({ description: desc });
    if (!code) return;
    try { await fn(code); } catch (e) {
      if (e.response?.status === 403) toast.error('Güvenlik şifresi hatalı.');
      else toast.error('İşlem sırasında hata oluştu.');
    }
  };

  const handleSave = () => withCode('Gateway ayarlarını kaydetmek için güvenlik şifresini girin.', async (code) => {
    const headers = { 'X-ADMIN-SECURITY-CODE': code };
    if (editing) {
      await axios.put(`/api/admin/payment-gateways/${editing}`, form, { headers });
    } else {
      await axios.post('/api/admin/payment-gateways', form, { headers });
    }
    setShowForm(false); setEditing(null); setForm({...EMPTY_FORM}); fetchGateways();
    toast.success('Başarıyla kaydedildi.');
  });

  const handleToggle = (gw) => withCode(`${gw.displayName} durumunu değiştirmek için güvenlik şifresini girin.`, async (code) => {
    const headers = { 'X-ADMIN-SECURITY-CODE': code };
    await axios.put(`/api/admin/payment-gateways/${gw.id}/${gw.active ? 'deactivate' : 'activate'}`, {}, { headers });
    fetchGateways();
  });

  const handleSetDefault = (gw) => withCode(`${gw.displayName} varsayılan yapmak için güvenlik şifresini girin.`, async (code) => {
    await axios.put(`/api/admin/payment-gateways/${gw.id}/set-default`, {}, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
    fetchGateways();
  });

  const handleDelete = (gw) => withCode(`${gw.displayName} silmek için güvenlik şifresini girin.`, async (code) => {
    await axios.delete(`/api/admin/payment-gateways/${gw.id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
    fetchGateways();
  });

  const handleTest = (gw) => withCode('Bağlantı testi için güvenlik şifresini girin.', async (code) => {
    const res = await axios.post(`/api/admin/payment-gateways/${gw.id}/test`, {}, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
    setTestResult({ id: gw.id, ...res.data });
    setTimeout(() => setTestResult(null), 5000);
  });

  const startEdit = (gw) => {
    setEditing(gw.id);
    setForm({ ...gw, storeKey:'', provisionPassword:'', apiKey:'', secretKey:'' }); // don't prefill masked secrets
    setShowForm(true);
  };

  const startCreate = () => { setEditing(null); setForm({...EMPTY_FORM}); setShowForm(true); };

  const protocolConfig = PROTOCOLS[form.gatewayProtocol] || {};
  const isNestPay = form.gatewayProtocol === 'NESTPAY';
  const isGvp = form.gatewayProtocol === 'GVP';
  const isIyzico = form.gatewayProtocol === 'IYZICO';
  const isPayTR = form.gatewayProtocol === 'PAYTR';

  // ── Payment method toggles & bank transfer config ──
  const [paymentToggles, setPaymentToggles] = useState({});
  const [bankConfig, setBankConfig] = useState(null);
  const [bankSaving, setBankSaving] = useState(false);

  useEffect(() => {
    axios.get('/api/admin/settings/site').then(r => {
      const map = {}; (r.data || []).forEach(s => { map[s.settingKey] = s.settingValue; }); setPaymentToggles(map);
    }).catch(() => {});
    axios.get('/api/admin/settings/payment/bank-transfer').then(r => setBankConfig(r.data)).catch(() => {});
  }, []);

  const handleToggleChange = async (key, value) => {
    const updated = { ...paymentToggles, [key]: value };
    setPaymentToggles(updated);
    try { await axios.put('/api/admin/settings/site', updated); } catch (e) { toast.error('İşlem başarısız.'); }
  };

  const handleSaveBankConfig = async () => {
    const code = await askCode({ description: 'Havale ayarlarını kaydetmek için güvenlik şifresini girin.' });
    if (!code) return;
    setBankSaving(true);
    try {
      await axios.put('/api/admin/settings/payment/bank-transfer', bankConfig, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      toast.success('Başarıyla kaydedildi.');
    } catch (e) { toast.error(e.response?.status === 403 ? 'Güvenlik şifresi hatalı.' : 'İşlem sırasında hata oluştu.'); }
    finally { setBankSaving(false); }
  };

  return (
    <div>
      {SecurityCodePrompt}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">Ödeme Ayarları</h2>
          <p className="text-muted small mb-0">Ödeme yöntemlerini, gateway'leri ve havale bilgilerini yönetin</p>
        </div>
        <button className="btn btn-primary" onClick={startCreate}><i className="fas fa-plus me-2" />Yeni Gateway Ekle</button>
      </div>

      {/* ── Payment Method Toggles ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-transparent"><h6 className="mb-0"><i className="fas fa-toggle-on me-2 text-primary" />Aktif Ödeme Yöntemleri</h6></div>
        <div className="card-body">
          <div className="row g-3">
            {[
              { key: 'payment_method_credit_card_enabled', label: 'Kredi Kartı ile Ödeme', icon: 'fa-credit-card', color: 'primary', desc: 'Kapatırsanız kredi kartı seçeneği müşterilere gösterilmez' },
              { key: 'payment_method_bank_transfer_enabled', label: 'Havale / EFT ile Ödeme', icon: 'fa-university', color: 'success', desc: 'Kapatırsanız havale/EFT seçeneği gösterilmez' },
              { key: 'payment_method_door_cash_enabled', label: 'Kapıda Ödeme', icon: 'fa-door-open', color: 'warning', desc: 'Kapatırsanız kapıda ödeme seçeneği gösterilmez' },
            ].map(m => {
              const isOn = paymentToggles[m.key] !== 'false';
              return (
                <div key={m.key} className="col-md-4">
                  <div className={`border rounded p-3 h-100 ${isOn ? `border-${m.color} bg-${m.color} bg-opacity-10` : 'border-light'}`}>
                    <div className="form-check form-switch">
                      <input className="form-check-input" type="checkbox" checked={isOn}
                        onChange={e => handleToggleChange(m.key, e.target.checked ? 'true' : 'false')} id={m.key} />
                      <label className="form-check-label small fw-semibold" htmlFor={m.key}>
                        <i className={`fas ${m.icon} me-1 text-${m.color}`} />{m.label}
                      </label>
                    </div>
                    <small className="text-muted d-block mt-1">{m.desc}</small>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* ── Bank Transfer Config ── */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
          <h6 className="mb-0"><i className="fas fa-university me-2 text-success" />Havale / EFT Bilgileri</h6>
          {bankConfig && <span className={`badge ${bankConfig.iban ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning'} border`}>
            <i className={`fas ${bankConfig.iban ? 'fa-check-circle' : 'fa-exclamation-circle'} me-1`} />{bankConfig.iban ? 'Yapılandırılmış' : 'IBAN girilmemiş'}
          </span>}
        </div>
        <div className="card-body">
          {!bankConfig ? <div className="text-center py-3"><span className="spinner-border spinner-border-sm" /></div> : (
            <div className="row g-3">
              <div className="col-md-6">
                <label className="form-label small fw-medium">Banka Adı</label>
                <input className="form-control" value={bankConfig.bankName||''} onChange={e => setBankConfig({...bankConfig, bankName: e.target.value})} placeholder="Ziraat Bankası" />
              </div>
              <div className="col-md-6">
                <label className="form-label small fw-medium">Hesap Sahibi</label>
                <input className="form-control" value={bankConfig.accountHolder||''} onChange={e => setBankConfig({...bankConfig, accountHolder: e.target.value})} placeholder="Ad Soyad / Şirket Unvanı" />
              </div>
              <div className="col-md-8">
                <label className="form-label small fw-medium">IBAN</label>
                <input className="form-control font-monospace" value={bankConfig.iban||''} onChange={e => setBankConfig({...bankConfig, iban: e.target.value.toUpperCase()})} placeholder="TR00 0000 0000 0000 0000 0000 00" maxLength={32} />
                <small className="text-muted">Müşterilere gösterilecek IBAN numarası</small>
              </div>
              <div className="col-md-4">
                <label className="form-label small fw-medium">Ödeme Süresi</label>
                <div className="input-group">
                  <input type="number" className="form-control" value={bankConfig.deadlineHours||48} onChange={e => setBankConfig({...bankConfig, deadlineHours: e.target.value})} min={1} max={168} />
                  <span className="input-group-text small">saat</span>
                </div>
              </div>
              <div className="col-12">
                <button className="btn btn-success px-4" onClick={handleSaveBankConfig} disabled={bankSaving}>
                  <i className={`fas ${bankSaving ? 'fa-spinner fa-spin' : 'fa-save'} me-2`} />{bankSaving ? 'Kaydediliyor...' : 'Havale Ayarlarını Kaydet'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Gateway Header ── */}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h5 className="mb-0"><i className="fas fa-plug me-2 text-primary" />Ödeme Gateway'leri</h5>
      </div>

      <div className="row g-4">
        {/* Form */}
        {showForm && (
          <div className="col-lg-5">
            <div className="card border-0 shadow-sm">
              <div className="card-header bg-transparent d-flex justify-content-between">
                <h6 className="mb-0">{editing ? 'Gateway Düzenle' : 'Yeni Gateway'}</h6>
                <button className="btn-close" onClick={() => setShowForm(false)} />
              </div>
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-12">
                    <label className="form-label small fw-medium">Protokol</label>
                    <select className="form-select" value={form.gatewayProtocol} onChange={e => setForm({...form, gatewayProtocol: e.target.value, bankCode:''})}>
                      {Object.entries(PROTOCOLS).map(([k,v]) => <option key={k} value={k}>{v.label}</option>)}
                    </select>
                  </div>
                  {protocolConfig.banks?.length > 0 && (
                    <div className="col-12">
                      <label className="form-label small fw-medium">Banka <span className="text-danger">*</span></label>
                      <select className="form-select" value={form.bankCode} onChange={e => setForm({...form, bankCode: e.target.value})}>
                        <option value="">Banka seçiniz...</option>
                        {protocolConfig.banks.map(b => <option key={b} value={b}>{BANK_LABELS[b] || b}</option>)}
                      </select>
                      <small className="text-muted">Bu protokolün bağlı olduğu banka. POS terminal bilgileriniz bu bankadan alınmalıdır.</small>
                    </div>
                  )}
                  {(isPayTR || isIyzico) && (
                    <div className="col-12">
                      <div className="alert alert-light small border mb-0">
                        <i className="fas fa-info-circle me-1 text-primary" />
                        {isPayTR ? 'PayTR tüm Türk bankalarını tek entegrasyonda destekler. Ayrıca banka seçimi gerekmez.' : 'iyzico tüm banka kartlarını tek entegrasyonda destekler. Ayrıca banka seçimi gerekmez.'}
                      </div>
                    </div>
                  )}
                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Kod (benzersiz)</label>
                    <input className="form-control font-monospace" value={form.code} onChange={e => setForm({...form, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g,'')})} placeholder="GARANTI_POS_1" disabled={!!editing} />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Görünen Ad</label>
                    <input className="form-control" value={form.displayName} onChange={e => setForm({...form, displayName: e.target.value})} placeholder="Garanti BBVA Sanal POS" />
                  </div>

                  {/* Protocol-specific credentials */}
                  {(isNestPay || isGvp) && (
                    <>
                      <div className="col-md-6">
                        <label className="form-label small fw-medium">Merchant ID</label>
                        <input className="form-control font-monospace" value={form.merchantId} onChange={e => setForm({...form, merchantId: e.target.value})} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label small fw-medium">Terminal ID</label>
                        <input className="form-control font-monospace" value={form.terminalId} onChange={e => setForm({...form, terminalId: e.target.value})} />
                      </div>
                      <div className="col-12">
                        <label className="form-label small fw-medium">Store Key</label>
                        <div className="input-group">
                          <input type={showStoreKey ? 'text' : 'password'} className="form-control font-monospace" value={form.storeKey} onChange={e => setForm({...form, storeKey: e.target.value})} placeholder={editing ? '(değiştirmek için yeni değer girin)' : ''} />
                          <button className="btn btn-outline-secondary" type="button" onClick={() => setShowStoreKey(!showStoreKey)}><i className={`fas fa-eye${showStoreKey ? '-slash' : ''}`} /></button>
                        </div>
                      </div>
                      {isGvp && (
                        <div className="col-12">
                          <label className="form-label small fw-medium">Provision Password</label>
                          <div className="input-group">
                            <input type={showProvPass ? 'text' : 'password'} className="form-control font-monospace" value={form.provisionPassword} onChange={e => setForm({...form, provisionPassword: e.target.value})} />
                            <button className="btn btn-outline-secondary" type="button" onClick={() => setShowProvPass(!showProvPass)}><i className={`fas fa-eye${showProvPass ? '-slash' : ''}`} /></button>
                          </div>
                        </div>
                      )}
                      <div className="col-12">
                        <label className="form-label small fw-medium">3D Secure URL</label>
                        <input className="form-control small" value={form.threeDUrl} onChange={e => setForm({...form, threeDUrl: e.target.value})} placeholder={isNestPay ? 'https://entegrasyon.asseco-see.com.tr/fim/est3Dgate' : 'https://sanalposprov.garanti.com.tr/servlet/gt3dengine'} />
                      </div>
                    </>
                  )}
                  {isIyzico && (
                    <>
                      <div className="col-md-6">
                        <label className="form-label small fw-medium">API Key</label>
                        <input className="form-control font-monospace" value={form.apiKey} onChange={e => setForm({...form, apiKey: e.target.value})} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label small fw-medium">Secret Key</label>
                        <input className="form-control font-monospace" value={form.secretKey} onChange={e => setForm({...form, secretKey: e.target.value})} />
                      </div>
                      <div className="col-12">
                        <label className="form-label small fw-medium">Base URL</label>
                        <input className="form-control small" value={form.baseUrl} onChange={e => setForm({...form, baseUrl: e.target.value})} />
                      </div>
                    </>
                  )}
                  {isPayTR && (
                    <>
                      <div className="col-12">
                        <div className="alert alert-info small mb-3">
                          <i className="fas fa-info-circle me-2" />
                          PayTR iFrame API entegrasyonu. Kart bilgileri PayTR'ın güvenli sayfasında işlenir.
                          Bilgilerinizi <a href="https://www.paytr.com" target="_blank" rel="noopener noreferrer">PayTR Mağaza Paneli</a> → Bilgi sayfasından alabilirsiniz.
                        </div>
                      </div>
                      <div className="col-md-4">
                        <label className="form-label small fw-medium">Mağaza No (merchant_id)</label>
                        <input className="form-control font-monospace" value={form.merchantId} onChange={e => setForm({...form, merchantId: e.target.value})} placeholder="123456" />
                      </div>
                      <div className="col-md-4">
                        <label className="form-label small fw-medium">Mağaza Parolası (merchant_key)</label>
                        <div className="input-group">
                          <input type={showStoreKey ? 'text' : 'password'} className="form-control font-monospace" value={form.apiKey} onChange={e => setForm({...form, apiKey: e.target.value})} placeholder={editing ? '(değiştirmek için yeni değer)' : ''} />
                          <button className="btn btn-outline-secondary" type="button" onClick={() => setShowStoreKey(!showStoreKey)}><i className={`fas fa-eye${showStoreKey ? '-slash' : ''}`} /></button>
                        </div>
                      </div>
                      <div className="col-md-4">
                        <label className="form-label small fw-medium">Gizli Anahtar (merchant_salt)</label>
                        <div className="input-group">
                          <input type={showProvPass ? 'text' : 'password'} className="form-control font-monospace" value={form.secretKey} onChange={e => setForm({...form, secretKey: e.target.value})} placeholder={editing ? '(değiştirmek için yeni değer)' : ''} />
                          <button className="btn btn-outline-secondary" type="button" onClick={() => setShowProvPass(!showProvPass)}><i className={`fas fa-eye${showProvPass ? '-slash' : ''}`} /></button>
                        </div>
                      </div>
                    </>
                  )}
                  <div className="col-12">
                    <label className="form-label small fw-medium">Callback URL {isPayTR && '(Bildirim URL)'}</label>
                    <input className="form-control small" value={form.callbackUrl} onChange={e => setForm({...form, callbackUrl: e.target.value})} placeholder={isPayTR ? `https://siteniz.com/api/store/payment/callback/paytr/${form.code || 'CODE'}` : `https://siteniz.com/api/store/payment/callback/pos/${form.code || 'CODE'}`} />
                    <small className="text-muted">Banka ödeme sonucu bu URL'ye POST yapar</small>
                  </div>

                  {/* Behavior */}
                  <div className="col-12">
                    <label className="form-label small fw-medium d-flex align-items-center gap-2">
                      Ortam
                      <span className="text-muted" title="Sandbox: Test ortamı — gerçek para çekilmez, test kart numaralarıyla deneyebilirsiniz. Production: Canlı ortam — gerçek ödemeler işlenir." style={{cursor:'help'}}>
                        <i className="fas fa-info-circle" />
                      </span>
                    </label>
                    <div className="d-flex gap-3">
                      <div className={`flex-fill border rounded p-3 text-center ${form.sandbox ? 'border-warning bg-warning bg-opacity-10' : 'border-light'}`} style={{cursor:'pointer'}} onClick={() => setForm({...form, sandbox: true})}>
                        <i className="fas fa-flask text-warning mb-1 d-block" />
                        <div className="small fw-medium">Sandbox (Test)</div>
                        <div className="text-muted small">Gerçek para çekilmez</div>
                      </div>
                      <div className={`flex-fill border rounded p-3 text-center ${!form.sandbox ? 'border-success bg-success bg-opacity-10' : 'border-light'}`} style={{cursor:'pointer'}} onClick={() => setForm({...form, sandbox: false})}>
                        <i className="fas fa-globe text-success mb-1 d-block" />
                        <div className="small fw-medium">Production (Canlı)</div>
                        <div className="text-muted small">Gerçek ödemeler işlenir</div>
                      </div>
                    </div>
                  </div>

                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Öncelik</label>
                    <input type="number" className="form-control" value={form.priority} onChange={e => setForm({...form, priority: parseInt(e.target.value)||100})} min={1} max={999} />
                    <small className="text-muted">Düşük sayı = yüksek öncelik. Birden fazla aktif gateway varsa önce düşük öncelikli denenir.</small>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-medium">Max Taksit Sayısı</label>
                    <input type="number" className="form-control" value={form.maxInstallments} onChange={e => setForm({...form, maxInstallments: parseInt(e.target.value)||12})} min={1} max={24} />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-medium">Desteklenen Kart Tipleri</label>
                    <div className="d-flex flex-wrap gap-2">
                      {['VISA', 'MASTERCARD', 'TROY', 'AMEX'].map(card => {
                        const cards = (form.supportedCards || '').split(',').map(c => c.trim());
                        const checked = cards.includes(card);
                        return (
                          <div key={card} className={`border rounded px-3 py-2 d-flex align-items-center gap-2 ${checked ? 'border-primary bg-primary bg-opacity-10' : 'border-light'}`}
                            style={{cursor:'pointer'}} onClick={() => {
                              const updated = checked ? cards.filter(c => c !== card) : [...cards.filter(c=>c), card];
                              setForm({...form, supportedCards: updated.join(',')});
                            }}>
                            <input type="checkbox" className="form-check-input m-0" checked={checked} readOnly />
                            <span className="small fw-medium">{card}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {!form.sandbox && (
                    <div className="col-12">
                      <div className="alert alert-danger small mb-0"><i className="fas fa-exclamation-triangle me-1" /><strong>Dikkat:</strong> Production (canlı) moddasınız. Bu gateway üzerinden gerçek ödemeler işlenecektir!</div>
                    </div>
                  )}

                  <div className="col-12 d-flex gap-2">
                    <button className="btn btn-primary" onClick={handleSave}><i className="fas fa-save me-1" />{editing ? 'Güncelle' : 'Oluştur'}</button>
                    <button className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>İptal</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Gateway List */}
        <div className={showForm ? 'col-lg-7' : 'col-12'}>
          <div className="card border-0 shadow-sm">
            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-5"><span className="spinner-border spinner-border-sm" /></div>
              ) : gateways.length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-plug text-muted fa-3x mb-3 d-block opacity-25" />
                  <p className="text-muted mb-2">Henüz ödeme gateway'i yapılandırılmamış.</p>
                  <p className="text-muted small mb-3">Sanal POS terminali ekleyerek doğrudan banka ile ödeme alabilirsiniz.</p>
                  <button className="btn btn-sm btn-primary" onClick={startCreate}><i className="fas fa-plus me-1" />İlk Gateway'i Ekleyin</button>
                </div>
              ) : (
                <table className="table table-hover align-middle mb-0">
                  <thead className="table-light">
                    <tr><th>Gateway</th><th>Protokol / Banka</th><th>Durum</th><th>Öncelik</th><th style={{width:180}}>İşlemler</th></tr>
                  </thead>
                  <tbody>
                    {gateways.map(gw => (
                      <tr key={gw.id}>
                        <td>
                          <div className="fw-medium">{gw.displayName}</div>
                          <code className="small text-muted">{gw.code}</code>
                        </td>
                        <td>
                          <span className="badge bg-primary bg-opacity-10 text-primary me-1">{PROTOCOLS[gw.gatewayProtocol]?.label || gw.gatewayProtocol}</span>
                          {gw.bankCode && <span className="badge bg-light text-dark">{BANK_LABELS[gw.bankCode] || gw.bankCode}</span>}
                        </td>
                        <td>
                          <div className="d-flex align-items-center gap-2">
                            <span className={`badge bg-${gw.active ? (gw.sandbox ? 'warning' : 'success') : 'secondary'}`}>
                              {gw.active ? (gw.sandbox ? '⚠️ Sandbox' : '✅ Aktif') : '❌ Pasif'}
                            </span>
                            {gw.defaultGateway && <span className="badge bg-info">Varsayılan</span>}
                            {testResult?.id === gw.id && (
                              <span className={`badge bg-${testResult.success ? 'success' : 'danger'}`}>
                                {testResult.success ? '✅ Test OK' : '❌ Test Başarısız'}
                              </span>
                            )}
                          </div>
                        </td>
                        <td><span className="badge bg-light text-dark">{gw.priority}</span></td>
                        <td>
                          <div className="btn-group btn-group-sm">
                            <button className="btn btn-outline-primary" onClick={() => startEdit(gw)} title="Düzenle"><i className="fas fa-edit" /></button>
                            <button className={`btn btn-outline-${gw.active ? 'warning' : 'success'}`} onClick={() => handleToggle(gw)} title={gw.active ? 'Deaktif Et' : 'Aktifleştir'}><i className={`fas fa-${gw.active ? 'pause' : 'play'}`} /></button>
                            {!gw.defaultGateway && gw.active && <button className="btn btn-outline-info" onClick={() => handleSetDefault(gw)} title="Varsayılan Yap"><i className="fas fa-star" /></button>}
                            <button className="btn btn-outline-secondary" onClick={() => handleTest(gw)} title="Test"><i className="fas fa-plug" /></button>
                            <button className="btn btn-outline-danger" onClick={() => { if (window.confirm(`"${gw.displayName}" silinecek. Emin misiniz?`)) handleDelete(gw); }} title="Sil"><i className="fas fa-trash" /></button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>

          {/* Security Info */}
          <div className="card border-0 shadow-sm mt-4">
            <div className="card-body">
              <h6 className="mb-3"><i className="fas fa-shield-alt me-2 text-success" />Güvenlik Bilgileri</h6>
              <div className="row g-2 small">
                <div className="col-md-6"><i className="fas fa-check text-success me-2" />3D Secure 2.0 zorunlu — kart bilgisi sunucumuzda saklanmaz</div>
                <div className="col-md-6"><i className="fas fa-check text-success me-2" />SHA-512 hash doğrulama — her callback'te kontrol edilir</div>
                <div className="col-md-6"><i className="fas fa-check text-success me-2" />PCI DSS SAQ-A uyumlu — kart verileri banka sayfasında işlenir</div>
                <div className="col-md-6"><i className="fas fa-check text-success me-2" />Hassas bilgiler maskelenmiş — API key/secret gizli tutulur</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
