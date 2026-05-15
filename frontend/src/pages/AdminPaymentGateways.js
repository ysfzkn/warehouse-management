import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import { useAdminToast } from '../components/AdminToast';
import GatewayFormField from '../components/GatewayFormField';
import {
  PROTOCOLS,
  BANK_LABELS,
  emptyFormForProtocol,
  hydrateFormFromGateway,
  buildPayloadForProtocol,
  validateForm,
  suggestCallbackUrl,
} from './paymentGatewaySchema';

const EMPTY_FORM = emptyFormForProtocol('IYZICO');

export default function AdminPaymentGateways() {
  const [gateways, setGateways] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({...EMPTY_FORM});
  const [formError, setFormError] = useState(null);
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
      const status = e.response?.status;
      const msg = e.response?.data?.message || e.response?.data?.error;
      if (status === 403) toast.error('Güvenlik şifresi hatalı.');
      else if (status === 401) toast.error('Oturumunuz sona ermiş. Tekrar giriş yapın.');
      else if (status === 400 && msg) toast.error(msg);
      else if (status >= 500) toast.error(msg || `Sunucu hatası (${status}).`);
      else if (!e.response) toast.error('Sunucuya ulaşılamadı.');
      else toast.error(msg || `Hata (${status || 'unknown'})`);
      // eslint-disable-next-line no-console
      console.error('[AdminPaymentGateways] action failed:', { status, msg, error: e });
    }
  };

  const handleSave = () => {
    // Frontend validation — kullanıcı hata mesajını anında görür, backend round-trip gerekmez
    const err = validateForm(form, form.gatewayProtocol, !!editing);
    if (err) {
      setFormError(err);
      toast.error(err);
      return;
    }
    setFormError(null);
    withCode('Gateway ayarlarını kaydetmek için güvenlik şifresini girin.', async (code) => {
      const headers = { 'X-ADMIN-SECURITY-CODE': code };
      // Protokole özgü payload'u oluştur (gereksiz alanları kırp)
      const payload = buildPayloadForProtocol(form, form.gatewayProtocol);
      if (editing) {
        await axios.put(`/api/admin/payment-gateways/${editing}`, payload, { headers });
      } else {
        await axios.post('/api/admin/payment-gateways', payload, { headers });
      }
      setShowForm(false); setEditing(null); setForm({...EMPTY_FORM}); fetchGateways();
      toast.success('Başarıyla kaydedildi.');
    });
  };

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
    setForm(hydrateFormFromGateway(gw));
    setFormError(null);
    setShowForm(true);
  };

  const startCreate = () => {
    setEditing(null);
    setForm({...EMPTY_FORM});
    setFormError(null);
    setShowForm(true);
  };

  const protocolConfig = PROTOCOLS[form.gatewayProtocol] || {};

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

  /**
   * Ödeme yöntemi toggle'ı (kredi kartı / havale / kapıda ödeme aç/kapat).
   *
   * Backend admin endpoint'leri güvenlik şifresi (X-ADMIN-SECURITY-CODE) zorunlu kılar.
   * Önceki sürümde header gönderilmiyordu → her toggle "400 Güvenlik şifresi zorunludur" alıyordu.
   *
   * Şimdi:
   *   1. Toggle değişimi optimistic olarak UI'a yansıtılır
   *   2. Şifre prompt'u açılır
   *   3. Kullanıcı şifreyi girerse PUT atılır (success toast)
   *   4. Kullanıcı iptal eder veya hata olursa UI önceki haline geri alınır
   */
  const handleToggleChange = async (key, value) => {
    const previous = paymentToggles[key];
    const updated = { ...paymentToggles, [key]: value };
    setPaymentToggles(updated); // Optimistic UI

    const label = key.includes('credit_card') ? 'Kredi kartı'
                : key.includes('bank_transfer') ? 'Havale/EFT'
                : key.includes('door') ? 'Kapıda ödeme'
                : 'Ödeme yöntemi';
    const action = value === 'true' ? 'Aktif et' : 'Devre dışı bırak';

    const code = await askCode({
      description: `${action}: ${label.toLowerCase()} — onaylamak için güvenlik şifresini girin.`,
    });
    if (!code) {
      // Kullanıcı iptal etti — UI revert
      setPaymentToggles(p => ({ ...p, [key]: previous }));
      return;
    }
    try {
      await axios.put('/api/admin/settings/site', updated,
          { headers: { 'X-ADMIN-SECURITY-CODE': code } });
      toast.success(`${label} ${value === 'true' ? 'aktif edildi' : 'devre dışı bırakıldı'}.`);
    } catch (e) {
      // Hata → UI revert + açıklayıcı mesaj
      setPaymentToggles(p => ({ ...p, [key]: previous }));
      const status = e.response?.status;
      const msg = e.response?.data?.message || e.response?.data?.error;
      if (status === 403) toast.error('Güvenlik şifresi hatalı.');
      else if (status === 401) toast.error('Oturumunuz sona ermiş. Tekrar giriş yapın.');
      else if (status === 400 && msg) toast.error(msg);
      else if (status >= 500) toast.error(msg || `Sunucu hatası (${status}).`);
      else if (!e.response) toast.error('Sunucuya ulaşılamadı.');
      else toast.error(msg || `Hata (${status || 'unknown'})`);
      // eslint-disable-next-line no-console
      console.error('[AdminPaymentGateways] toggle failed:', { key, status, msg, error: e });
    }
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
          <div className="col-lg-6">
            <div className="card border-0 shadow-sm sticky-top" style={{top: 16}}>
              <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                <h6 className="mb-0">
                  <i className={`fas ${protocolConfig.icon || 'fa-plug'} me-2 text-${protocolConfig.color || 'primary'}`} />
                  {editing ? 'Gateway Düzenle' : 'Yeni Gateway Ekle'}
                </h6>
                <button className="btn-close" onClick={() => setShowForm(false)} aria-label="Kapat" />
              </div>
              <div className="card-body" style={{maxHeight: 'calc(100vh - 200px)', overflowY: 'auto'}}>
                <div className="row g-3">

                  {/* ── Protokol seçici (visual card picker) ── */}
                  <div className="col-12">
                    <label className="form-label small fw-semibold mb-2">Protokol Seçin <span className="text-danger" aria-hidden="true">*</span></label>
                    <div className="row g-2">
                      {Object.entries(PROTOCOLS).map(([code, p]) => {
                        const selected = form.gatewayProtocol === code;
                        return (
                          <div key={code} className="col-6">
                            <button type="button"
                              className={`w-100 h-100 border-2 rounded p-3 text-start position-relative ${selected ? `border-${p.color}` : 'border-light'}`}
                              style={{
                                cursor: editing ? 'not-allowed' : 'pointer',
                                opacity: editing ? 0.6 : 1,
                                background: '#ffffff',
                                borderWidth: selected ? '2px' : '1px',
                                boxShadow: selected ? `0 0 0 3px rgba(var(--bs-${p.color}-rgb, 13,110,253), 0.12)` : 'none',
                                transition: 'all 0.15s ease',
                              }}
                              disabled={!!editing}
                              onClick={() => setForm({...EMPTY_FORM,
                                gatewayProtocol: code,
                                code: form.code,
                                displayName: form.displayName,
                                sandbox: form.sandbox,
                                priority: form.priority,
                                supportedCards: form.supportedCards,
                                maxInstallments: form.maxInstallments,
                              })}>
                              {selected && (
                                <i className={`fas fa-check-circle text-${p.color} position-absolute top-0 end-0 m-2`}
                                   style={{fontSize: 16}} aria-hidden="true" />
                              )}
                              <div className="d-flex align-items-center gap-2 mb-1">
                                <i className={`fas ${p.icon} text-${p.color}`} style={{fontSize: 18}} />
                                <strong className="text-dark" style={{fontSize: 14}}>{p.label}</strong>
                              </div>
                              <div className="text-secondary" style={{fontSize: 12, lineHeight: 1.4, color: '#4b5563'}}>
                                {p.description}
                              </div>
                            </button>
                          </div>
                        );
                      })}
                    </div>
                    {editing && (
                      <small className="text-muted d-block mt-1">
                        <i className="fas fa-lock me-1" />Protokol değiştirilemez (mevcut gateway).
                      </small>
                    )}
                  </div>

                  {/* ── Protokol help info ── */}
                  {protocolConfig.helpText && (
                    <div className="col-12">
                      <div className={`alert alert-${protocolConfig.color === 'danger' ? 'warning' : protocolConfig.color || 'info'} small mb-0 py-2`}>
                        <i className="fas fa-info-circle me-1" />
                        {protocolConfig.helpText}
                        {protocolConfig.helpUrl && (
                          <a href={protocolConfig.helpUrl} target="_blank" rel="noopener noreferrer" className="ms-1">
                            Panel <i className="fas fa-external-link-alt" style={{fontSize: 10}} />
                          </a>
                        )}
                      </div>
                    </div>
                  )}

                  {/* ── Banka seçici (sadece NESTPAY/GVP) ── */}
                  {protocolConfig.bankSelect && (
                    <div className="col-12">
                      <label className="form-label small fw-semibold">
                        Banka <span className="text-danger" aria-hidden="true">*</span>
                      </label>
                      <select className="form-select" value={form.bankCode}
                        onChange={e => setForm({...form, bankCode: e.target.value})}>
                        <option value="">Banka seçiniz...</option>
                        {protocolConfig.banks.map(b => (
                          <option key={b} value={b}>{BANK_LABELS[b] || b}</option>
                        ))}
                      </select>
                      <small className="text-muted">POS terminal bilgilerini bu bankadan almış olmalısınız.</small>
                    </div>
                  )}

                  {/* ── Temel bilgiler ── */}
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">
                      Kod (benzersiz) <span className="text-danger" aria-hidden="true">*</span>
                    </label>
                    <input className="form-control font-monospace" value={form.code}
                      onChange={e => setForm({...form, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g,'')})}
                      placeholder="IYZICO_MAIN"
                      disabled={!!editing}
                      maxLength={50} />
                    <small className="text-muted">A-Z, 0-9, _ — callback URL'de görünür.</small>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">
                      Görünen Ad <span className="text-danger" aria-hidden="true">*</span>
                    </label>
                    <input className="form-control" value={form.displayName}
                      onChange={e => setForm({...form, displayName: e.target.value})}
                      placeholder="iyzico Ana" />
                    <small className="text-muted">Admin tablo görünümünde kullanılır.</small>
                  </div>

                  {/* ── Protokol-spesifik alanlar (dinamik) ── */}
                  {Object.entries(protocolConfig.fields || {}).map(([fieldKey, def]) => (
                    <div key={fieldKey} className="col-12">
                      <GatewayFormField
                        def={def}
                        value={form[fieldKey]}
                        onChange={(v) => setForm({...form, [fieldKey]: v})}
                        editingExistingSecret={!!editing && def.type === 'password'
                          && form['_' + fieldKey + 'Set']}
                      />
                      {/* Code → callback URL otomatik öneri */}
                      {fieldKey === 'callbackUrl' && form.code && !form.callbackUrl && (
                        <button type="button" className="btn btn-link btn-sm p-0 mt-1"
                          onClick={() => setForm({...form, callbackUrl: suggestCallbackUrl(form.code, form.gatewayProtocol)})}>
                          <i className="fas fa-magic me-1" />Otomatik öner
                        </button>
                      )}
                    </div>
                  ))}

                  {/* ── extraConfig alanları (PayTR için merchant_ok_url vb.) ── */}
                  {protocolConfig.extraFields && Object.keys(protocolConfig.extraFields).length > 0 && (
                    <>
                      <div className="col-12 mt-3">
                        <hr className="my-2" />
                        <h6 className="small fw-semibold mb-2 text-muted text-uppercase" style={{fontSize: 11, letterSpacing: 0.5}}>
                          Yönlendirme & Ek Ayarlar
                        </h6>
                      </div>
                      {Object.entries(protocolConfig.extraFields).map(([fieldKey, def]) => (
                        <div key={fieldKey} className="col-12">
                          <GatewayFormField
                            def={def}
                            value={form['extra_' + fieldKey]}
                            onChange={(v) => setForm({...form, ['extra_' + fieldKey]: v})}
                          />
                        </div>
                      ))}
                    </>
                  )}

                  {/* ── Ortam seçici (Sandbox / Production) ── */}
                  <div className="col-12 mt-3">
                    <hr className="my-2" />
                    <label className="form-label small fw-semibold">Ortam</label>
                    <div className="d-flex gap-2">
                      <button type="button"
                        className={`flex-fill rounded p-3 text-center position-relative ${form.sandbox ? 'border-warning' : 'border-light'}`}
                        style={{
                          cursor:'pointer', background: '#ffffff',
                          border: `${form.sandbox ? '2px' : '1px'} solid ${form.sandbox ? '#f59e0b' : '#e5e7eb'}`,
                          boxShadow: form.sandbox ? '0 0 0 3px rgba(245, 158, 11, 0.12)' : 'none',
                          transition: 'all 0.15s ease',
                        }}
                        onClick={() => setForm({...form, sandbox: true})}>
                        {form.sandbox && <i className="fas fa-check-circle text-warning position-absolute top-0 end-0 m-2" style={{fontSize: 16}} />}
                        <i className="fas fa-flask text-warning mb-1 d-block" style={{fontSize: 20}} />
                        <div className="fw-semibold text-dark" style={{fontSize: 13}}>Sandbox (Test)</div>
                        <div style={{fontSize: 11, color: '#6b7280'}}>Gerçek para çekilmez</div>
                      </button>
                      <button type="button"
                        className={`flex-fill rounded p-3 text-center position-relative ${!form.sandbox ? 'border-success' : 'border-light'}`}
                        style={{
                          cursor:'pointer', background: '#ffffff',
                          border: `${!form.sandbox ? '2px' : '1px'} solid ${!form.sandbox ? '#10b981' : '#e5e7eb'}`,
                          boxShadow: !form.sandbox ? '0 0 0 3px rgba(16, 185, 129, 0.12)' : 'none',
                          transition: 'all 0.15s ease',
                        }}
                        onClick={() => setForm({...form, sandbox: false})}>
                        {!form.sandbox && <i className="fas fa-check-circle text-success position-absolute top-0 end-0 m-2" style={{fontSize: 16}} />}
                        <i className="fas fa-globe text-success mb-1 d-block" style={{fontSize: 20}} />
                        <div className="fw-semibold text-dark" style={{fontSize: 13}}>Production (Canlı)</div>
                        <div style={{fontSize: 11, color: '#6b7280'}}>Gerçek ödemeler</div>
                      </button>
                    </div>
                  </div>

                  {/* ── Davranış parametreleri ── */}
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Öncelik</label>
                    <input type="number" className="form-control" value={form.priority}
                      onChange={e => setForm({...form, priority: parseInt(e.target.value)||100})}
                      min={1} max={999} />
                    <small className="text-muted">Düşük sayı = önce denenir.</small>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold">Max Taksit</label>
                    <input type="number" className="form-control" value={form.maxInstallments}
                      onChange={e => setForm({...form, maxInstallments: parseInt(e.target.value)||12})}
                      min={1} max={24} />
                  </div>

                  <div className="col-12">
                    <label className="form-label small fw-semibold">Desteklenen Kart Tipleri</label>
                    <div className="d-flex flex-wrap gap-2">
                      {['VISA', 'MASTERCARD', 'TROY', 'AMEX'].map(card => {
                        const cards = (form.supportedCards || '').split(',').map(c => c.trim()).filter(Boolean);
                        const checked = cards.includes(card);
                        return (
                          <button key={card} type="button"
                            className="rounded px-3 py-2 d-flex align-items-center gap-2"
                            style={{
                              cursor:'pointer',
                              background: '#ffffff',
                              border: `${checked ? '2px' : '1px'} solid ${checked ? '#2563eb' : '#e5e7eb'}`,
                              boxShadow: checked ? '0 0 0 2px rgba(37, 99, 235, 0.10)' : 'none',
                              transition: 'all 0.15s ease',
                              minWidth: 110,
                            }}
                            onClick={() => {
                              const updated = checked ? cards.filter(c => c !== card) : [...cards, card];
                              setForm({...form, supportedCards: updated.join(',')});
                            }}>
                            <input type="checkbox" className="form-check-input m-0" checked={checked} readOnly />
                            <span className="fw-semibold text-dark" style={{fontSize: 13}}>{card}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* Validation hatası göstergesi */}
                  {formError && (
                    <div className="col-12">
                      <div className="alert alert-danger small mb-0">
                        <i className="fas fa-exclamation-circle me-1" />{formError}
                      </div>
                    </div>
                  )}

                  {/* Production uyarısı */}
                  {!form.sandbox && (
                    <div className="col-12">
                      <div className="alert alert-danger small mb-0">
                        <i className="fas fa-exclamation-triangle me-1" />
                        <strong>Dikkat:</strong> Production modu — gerçek ödemeler işlenecek.
                      </div>
                    </div>
                  )}

                  <div className="col-12 d-flex gap-2 sticky-bottom bg-white pt-2 border-top">
                    <button className="btn btn-primary flex-grow-1" onClick={handleSave}>
                      <i className="fas fa-save me-1" />{editing ? 'Güncelle' : 'Oluştur'}
                    </button>
                    <button className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>İptal</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Gateway List */}
        <div className={showForm ? 'col-lg-6' : 'col-12'}>
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
