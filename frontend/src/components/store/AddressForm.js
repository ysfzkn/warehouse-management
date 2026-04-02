import React, { useState } from 'react';

/**
 * TC Kimlik No validation (11 digit Turkish national ID)
 * Rules: 11 digits, first digit != 0, algorithm check on digits 10 & 11
 */
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

export default function AddressForm({ onSubmit, initialData }) {
  const [form, setForm] = useState(initialData || {
    title: '', firstName: '', lastName: '', phone: '',
    city: '', district: '', neighborhood: '', addressLine: '', postalCode: '',
    tcKimlikNo: '', companyName: '', taxOffice: '', taxNumber: '',
  });
  const [showBilling, setShowBilling] = useState(!!(initialData?.companyName || initialData?.taxNumber));
  const [tcError, setTcError] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(f => ({ ...f, [name]: value }));
    if (name === 'tcKimlikNo') setTcError('');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    // TC Kimlik validation (only if entered)
    if (form.tcKimlikNo && !validateTcKimlik(form.tcKimlikNo)) {
      setTcError('Geçersiz TC Kimlik numarası. Lütfen kontrol edin.');
      return;
    }
    onSubmit(form);
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="row g-3">
        {/* Basic Info */}
        <div className="col-12">
          <input className="form-control" name="title" placeholder="Adres Başlığı (ör: Ev, İş)" value={form.title} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="firstName" placeholder="Ad" value={form.firstName} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="lastName" placeholder="Soyad" value={form.lastName} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="phone" placeholder="Telefon (05XX XXX XX XX)" value={form.phone} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <div className="input-group">
            <input className={`form-control ${tcError ? 'is-invalid' : form.tcKimlikNo && validateTcKimlik(form.tcKimlikNo) ? 'is-valid' : ''}`}
              name="tcKimlikNo" placeholder="TC Kimlik No (11 haneli)" value={form.tcKimlikNo} onChange={handleChange}
              maxLength={11} inputMode="numeric" pattern="\d{11}" />
            {form.tcKimlikNo && validateTcKimlik(form.tcKimlikNo) && (
              <span className="input-group-text text-success bg-transparent border-success"><i className="fas fa-check" /></span>
            )}
          </div>
          {tcError && <small className="text-danger">{tcError}</small>}
          <small className="text-muted">Fatura ve kargo için gereklidir</small>
        </div>

        {/* Address */}
        <div className="col-md-6">
          <input className="form-control" name="city" placeholder="İl" value={form.city} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="district" placeholder="İlçe" value={form.district} onChange={handleChange} required />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="neighborhood" placeholder="Mahalle (opsiyonel)" value={form.neighborhood || ''} onChange={handleChange} />
        </div>
        <div className="col-md-6">
          <input className="form-control" name="postalCode" placeholder="Posta Kodu" value={form.postalCode} onChange={handleChange} />
        </div>
        <div className="col-12">
          <textarea className="form-control" name="addressLine" placeholder="Açık Adres (Sokak, Bina No, Daire No)" value={form.addressLine} onChange={handleChange} required rows={2} />
        </div>

        {/* Corporate Billing Toggle */}
        <div className="col-12">
          <div className="form-check">
            <input className="form-check-input" type="checkbox" id="showBilling" checked={showBilling} onChange={e => setShowBilling(e.target.checked)} />
            <label className="form-check-label small" htmlFor="showBilling">Kurumsal fatura istiyorum</label>
          </div>
        </div>

        {showBilling && (
          <>
            <div className="col-md-6">
              <input className="form-control" name="companyName" placeholder="Şirket Unvanı" value={form.companyName || ''} onChange={handleChange} />
            </div>
            <div className="col-md-3">
              <input className="form-control" name="taxOffice" placeholder="Vergi Dairesi" value={form.taxOffice || ''} onChange={handleChange} />
            </div>
            <div className="col-md-3">
              <input className="form-control" name="taxNumber" placeholder="Vergi No" value={form.taxNumber || ''} onChange={handleChange} maxLength={11} />
            </div>
          </>
        )}

        <div className="col-12">
          <button type="submit" className="btn btn-primary">
            <i className="fas fa-save me-2" />Adresi Kaydet ve Devam Et
          </button>
        </div>
      </div>
    </form>
  );
}
