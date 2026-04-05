import React, { useState, useEffect } from 'react';
import { FiUser, FiPhone, FiMapPin, FiHome, FiCreditCard, FiCheck, FiAlertCircle } from 'react-icons/fi';

// TC Kimlik No validation
function validateTcKimlik(tc) {
  if (!tc || tc.length !== 11 || !/^\d{11}$/.test(tc) || tc[0] === '0') return false;
  const d = tc.split('').map(Number);
  const oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
  const evenSum = d[1] + d[3] + d[5] + d[7];
  let d10 = (oddSum * 7 - evenSum) % 10;
  if (d10 < 0) d10 += 10;
  if (d10 !== d[9]) return false;
  let total = 0;
  for (let i = 0; i < 10; i++) total += d[i];
  return total % 10 === d[10];
}

// Phone format: 05XX XXX XX XX
function formatPhone(val) {
  const digits = val.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 4) return digits;
  if (digits.length <= 7) return digits.slice(0, 4) + ' ' + digits.slice(4);
  if (digits.length <= 9) return digits.slice(0, 4) + ' ' + digits.slice(4, 7) + ' ' + digits.slice(7);
  return digits.slice(0, 4) + ' ' + digits.slice(4, 7) + ' ' + digits.slice(7, 9) + ' ' + digits.slice(9);
}

function validatePhone(phone) {
  const digits = phone.replace(/\D/g, '');
  return /^(05)\d{9}$/.test(digits);
}

const CITIES = [
  'Adana','Adıyaman','Afyonkarahisar','Ağrı','Aksaray','Amasya','Ankara','Antalya','Ardahan','Artvin',
  'Aydın','Balıkesir','Bartın','Batman','Bayburt','Bilecik','Bingöl','Bitlis','Bolu','Burdur',
  'Bursa','Çanakkale','Çankırı','Çorum','Denizli','Diyarbakır','Düzce','Edirne','Elazığ','Erzincan',
  'Erzurum','Eskişehir','Gaziantep','Giresun','Gümüşhane','Hakkari','Hatay','Iğdır','Isparta','İstanbul',
  'İzmir','Kahramanmaraş','Karabük','Karaman','Kars','Kastamonu','Kayseri','Kırıkkale','Kırklareli','Kırşehir',
  'Kilis','Kocaeli','Konya','Kütahya','Malatya','Manisa','Mardin','Mersin','Muğla','Muş',
  'Nevşehir','Niğde','Ordu','Osmaniye','Rize','Sakarya','Samsun','Şanlıurfa','Siirt','Sinop',
  'Şırnak','Sivas','Tekirdağ','Tokat','Trabzon','Tunceli','Uşak','Van','Yalova','Yozgat','Zonguldak',
];

export default function AddressForm({ onSubmit, initialData, submitLabel, onCancel }) {
  const [form, setForm] = useState({
    title: '', firstName: '', lastName: '', phone: '',
    city: '', district: '', neighborhood: '', addressLine: '', postalCode: '',
    tcKimlikNo: '', companyName: '', taxOffice: '', taxNumber: '',
    ...(initialData || {}),
  });
  const [showBilling, setShowBilling] = useState(!!(initialData?.companyName || initialData?.taxNumber));
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});

  const f = (key, val) => {
    setForm(prev => ({ ...prev, [key]: val }));
    setErrors(prev => ({ ...prev, [key]: '' }));
  };

  const touch = (key) => setTouched(prev => ({ ...prev, [key]: true }));

  // Live validation
  useEffect(() => {
    const e = {};
    if (touched.firstName && !form.firstName.trim()) e.firstName = 'Ad zorunludur';
    if (touched.lastName && !form.lastName.trim()) e.lastName = 'Soyad zorunludur';
    if (touched.phone) {
      if (!form.phone.trim()) e.phone = 'Telefon zorunludur';
      else if (!validatePhone(form.phone)) e.phone = 'Geçerli bir telefon girin (05XX XXX XX XX)';
    }
    if (touched.city && !form.city) e.city = 'İl seçiniz';
    if (touched.district && !form.district.trim()) e.district = 'İlçe zorunludur';
    if (touched.addressLine) {
      if (!form.addressLine.trim()) e.addressLine = 'Adres zorunludur';
      else if (form.addressLine.trim().length < 10) e.addressLine = 'En az 10 karakter giriniz';
    }
    if (touched.tcKimlikNo && form.tcKimlikNo && !validateTcKimlik(form.tcKimlikNo)) {
      e.tcKimlikNo = 'Geçersiz TC Kimlik numarası';
    }
    if (showBilling && touched.taxNumber && form.taxNumber && form.taxNumber.length < 10) {
      e.taxNumber = 'Vergi no en az 10 hane olmalıdır';
    }
    setErrors(e);
  }, [form, touched, showBilling]);

  const validate = () => {
    const allTouched = {};
    ['firstName','lastName','phone','city','district','addressLine'].forEach(k => allTouched[k] = true);
    if (form.tcKimlikNo) allTouched.tcKimlikNo = true;
    if (showBilling && form.taxNumber) allTouched.taxNumber = true;
    setTouched(allTouched);

    if (!form.firstName.trim() || !form.lastName.trim() || !form.phone.trim() ||
        !form.city || !form.district.trim() || !form.addressLine.trim() ||
        form.addressLine.trim().length < 10 || !validatePhone(form.phone)) return false;
    if (form.tcKimlikNo && !validateTcKimlik(form.tcKimlikNo)) return false;
    return true;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;
    // Clean phone
    const cleanForm = { ...form, phone: form.phone.replace(/\s/g, '') };
    onSubmit(cleanForm);
  };

  const inputClass = (key) => {
    if (!touched[key]) return 'form-control';
    if (errors[key]) return 'form-control is-invalid';
    if (form[key]?.trim()) return 'form-control is-valid';
    return 'form-control';
  };

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="row g-3">

        {/* ── Adres Başlığı ── */}
        <div className="col-12">
          <label className="form-label small fw-semibold"><FiHome size={14} className="me-1 text-primary" />Adres Başlığı</label>
          <div className="d-flex gap-2 flex-wrap">
            {['Ev', 'İş', 'Diğer'].map(t => (
              <button key={t} type="button"
                className={`btn btn-sm ${form.title === t ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => f('title', t)}>
                {t === 'Ev' && <FiHome size={13} className="me-1" />}
                {t === 'İş' && <FiCreditCard size={13} className="me-1" />}
                {t}
              </button>
            ))}
            {!['Ev', 'İş', 'Diğer'].includes(form.title) && form.title && (
              <span className="btn btn-sm btn-primary">{form.title}</span>
            )}
            <input className="form-control form-control-sm" style={{ maxWidth: 180 }}
              placeholder="Özel başlık..." value={['Ev','İş','Diğer'].includes(form.title) ? '' : form.title}
              onChange={e => f('title', e.target.value)} />
          </div>
        </div>

        {/* ── Kişisel Bilgiler ── */}
        <div className="col-12"><hr className="my-1" /><div className="small fw-semibold text-muted"><FiUser size={13} className="me-1" />Kişisel Bilgiler</div></div>

        <div className="col-md-6">
          <label className="form-label small fw-medium">Ad <span className="text-danger">*</span></label>
          <input className={inputClass('firstName')} name="firstName" value={form.firstName}
            onChange={e => f('firstName', e.target.value)} onBlur={() => touch('firstName')}
            placeholder="Adınız" />
          {errors.firstName && <div className="invalid-feedback">{errors.firstName}</div>}
        </div>

        <div className="col-md-6">
          <label className="form-label small fw-medium">Soyad <span className="text-danger">*</span></label>
          <input className={inputClass('lastName')} name="lastName" value={form.lastName}
            onChange={e => f('lastName', e.target.value)} onBlur={() => touch('lastName')}
            placeholder="Soyadınız" />
          {errors.lastName && <div className="invalid-feedback">{errors.lastName}</div>}
        </div>

        <div className="col-md-6">
          <label className="form-label small fw-medium"><FiPhone size={13} className="me-1" />Telefon <span className="text-danger">*</span></label>
          <div className="input-group">
            <span className="input-group-text" style={{ fontSize: 13 }}>+90</span>
            <input className={inputClass('phone')} value={form.phone}
              onChange={e => f('phone', formatPhone(e.target.value))} onBlur={() => touch('phone')}
              placeholder="5XX XXX XX XX" inputMode="tel" maxLength={14} />
            {touched.phone && !errors.phone && form.phone && (
              <span className="input-group-text text-success bg-transparent border-success"><FiCheck size={14} /></span>
            )}
          </div>
          {errors.phone && <div className="text-danger" style={{ fontSize: '0.75rem', marginTop: 4 }}><FiAlertCircle size={12} className="me-1" />{errors.phone}</div>}
        </div>

        <div className="col-md-6">
          <label className="form-label small fw-medium">TC Kimlik No <span className="text-muted fw-normal">(opsiyonel)</span></label>
          <div className="input-group">
            <input className={`form-control ${touched.tcKimlikNo && form.tcKimlikNo ? (errors.tcKimlikNo ? 'is-invalid' : 'is-valid') : ''}`}
              value={form.tcKimlikNo}
              onChange={e => { const v = e.target.value.replace(/\D/g, '').slice(0, 11); f('tcKimlikNo', v); }}
              onBlur={() => touch('tcKimlikNo')}
              placeholder="XXXXXXXXXXX" inputMode="numeric" maxLength={11} />
            {form.tcKimlikNo && validateTcKimlik(form.tcKimlikNo) && (
              <span className="input-group-text text-success bg-transparent border-success"><FiCheck size={14} /></span>
            )}
          </div>
          {errors.tcKimlikNo && <div className="text-danger" style={{ fontSize: '0.75rem', marginTop: 4 }}>{errors.tcKimlikNo}</div>}
          <div className="text-muted" style={{ fontSize: '0.7rem', marginTop: 2 }}>Fatura ve kargo işlemleri için kullanılır</div>
        </div>

        {/* ── Adres Bilgileri ── */}
        <div className="col-12"><hr className="my-1" /><div className="small fw-semibold text-muted"><FiMapPin size={13} className="me-1" />Adres Bilgileri</div></div>

        <div className="col-md-4">
          <label className="form-label small fw-medium">İl <span className="text-danger">*</span></label>
          <select className={`form-select ${touched.city ? (errors.city ? 'is-invalid' : form.city ? 'is-valid' : '') : ''}`}
            value={form.city} onChange={e => f('city', e.target.value)} onBlur={() => touch('city')}>
            <option value="">İl seçiniz...</option>
            {CITIES.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
          {errors.city && <div className="invalid-feedback">{errors.city}</div>}
        </div>

        <div className="col-md-4">
          <label className="form-label small fw-medium">İlçe <span className="text-danger">*</span></label>
          <input className={inputClass('district')} value={form.district}
            onChange={e => f('district', e.target.value)} onBlur={() => touch('district')}
            placeholder="İlçe adı" />
          {errors.district && <div className="invalid-feedback">{errors.district}</div>}
        </div>

        <div className="col-md-4">
          <label className="form-label small fw-medium">Mahalle</label>
          <input className="form-control" value={form.neighborhood || ''}
            onChange={e => f('neighborhood', e.target.value)} placeholder="Mahalle (opsiyonel)" />
        </div>

        <div className="col-md-9">
          <label className="form-label small fw-medium">Açık Adres <span className="text-danger">*</span></label>
          <textarea className={inputClass('addressLine')} rows={2} value={form.addressLine}
            onChange={e => f('addressLine', e.target.value)} onBlur={() => touch('addressLine')}
            placeholder="Sokak, cadde, bina no, daire no..." />
          {errors.addressLine && <div className="invalid-feedback">{errors.addressLine}</div>}
          {form.addressLine && !errors.addressLine && (
            <div className="text-muted" style={{ fontSize: '0.7rem', marginTop: 2 }}>{form.addressLine.length} karakter</div>
          )}
        </div>

        <div className="col-md-3">
          <label className="form-label small fw-medium">Posta Kodu</label>
          <input className="form-control" value={form.postalCode || ''}
            onChange={e => f('postalCode', e.target.value.replace(/\D/g, '').slice(0, 5))}
            placeholder="34000" inputMode="numeric" maxLength={5} />
        </div>

        {/* ── Kurumsal Fatura ── */}
        <div className="col-12">
          <div className="d-flex align-items-center gap-2 p-2 rounded" style={{ background: showBilling ? '#eff6ff' : '#f8fafc', border: `1px solid ${showBilling ? '#bfdbfe' : '#e2e8f0'}`, cursor: 'pointer', transition: 'all 0.2s' }}
            onClick={() => setShowBilling(!showBilling)}>
            <input className="form-check-input m-0" type="checkbox" checked={showBilling} readOnly />
            <div>
              <div className="small fw-medium">Kurumsal fatura istiyorum</div>
              <div className="text-muted" style={{ fontSize: '0.7rem' }}>Şirket adına fatura kesilmesini istiyorsanız işaretleyin</div>
            </div>
            <FiCreditCard size={16} className="ms-auto text-muted" />
          </div>
        </div>

        {showBilling && (
          <>
            <div className="col-md-5">
              <label className="form-label small fw-medium">Şirket Unvanı</label>
              <input className="form-control" value={form.companyName || ''}
                onChange={e => f('companyName', e.target.value)} placeholder="ABC Ltd. Şti." />
            </div>
            <div className="col-md-3">
              <label className="form-label small fw-medium">Vergi Dairesi</label>
              <input className="form-control" value={form.taxOffice || ''}
                onChange={e => f('taxOffice', e.target.value)} placeholder="Kadıköy VD" />
            </div>
            <div className="col-md-4">
              <label className="form-label small fw-medium">Vergi No</label>
              <input className={`form-control ${touched.taxNumber && form.taxNumber ? (errors.taxNumber ? 'is-invalid' : 'is-valid') : ''}`}
                value={form.taxNumber || ''}
                onChange={e => f('taxNumber', e.target.value.replace(/\D/g, '').slice(0, 11))}
                onBlur={() => touch('taxNumber')}
                placeholder="XXXXXXXXXX" inputMode="numeric" maxLength={11} />
              {errors.taxNumber && <div className="invalid-feedback">{errors.taxNumber}</div>}
            </div>
          </>
        )}

        {/* ── Submit ── */}
        <div className="col-12 d-flex gap-2 mt-2">
          <button type="submit" className="btn btn-primary px-4">
            <FiCheck size={15} className="me-1" />{submitLabel || 'Adresi Kaydet'}
          </button>
          {onCancel && (
            <button type="button" className="btn btn-outline-secondary" onClick={onCancel}>İptal</button>
          )}
        </div>
      </div>
    </form>
  );
}
