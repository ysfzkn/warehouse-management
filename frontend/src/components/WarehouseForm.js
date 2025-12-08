import React, { useState, useEffect } from 'react';
import axios from 'axios';
import {
  extractPhoneDigits,
  formatPhoneForSubmit,
  formatPhoneInputValue,
  isPhoneComplete,
  PHONE_PLACEHOLDER
} from '../utils/phone';

const WarehouseForm = ({ warehouse, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    location: '',
    phone: '',
    manager: '',
    capacitySqm: '',
    isActive: true,
    warehouseType: 'STANDART'
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const showToast = (message, type = 'success') => {
    const toast = document.createElement('div');
    const bgClass =
      type === 'success'
        ? 'text-bg-success'
        : type === 'warning'
        ? 'text-bg-warning'
        : 'text-bg-danger';
    const icon =
      type === 'success'
        ? 'fa-check-circle'
        : type === 'warning'
        ? 'fa-exclamation-triangle'
        : 'fa-times-circle';
    toast.className = `toast align-items-center ${bgClass} border-0 position-fixed top-0 end-0 m-3 show`;
    toast.setAttribute('role', 'alert');
    toast.style.zIndex = '9999';
    toast.innerHTML = `
      <div class="d-flex align-items-center">
        <div class="toast-body d-flex align-items-start">
          <i class="fas ${icon} me-2 mt-1"></i>
          <div class="flex-grow-1">${message}</div>
        </div>
        <button type="button" class="btn-close ${type === 'success' ? 'btn-close-white' : ''} me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => {
      try {
        toast.classList.remove('show');
        setTimeout(() => {
          try { document.body.removeChild(toast); } catch {}
        }, 300);
      } catch {}
    }, type === 'success' ? 4000 : 7000);
  };

  useEffect(() => {
    if (warehouse) {
      setFormData({
        name: warehouse.name || '',
        location: warehouse.location || '',
        phone: extractPhoneDigits(warehouse.phone || ''),
        manager: warehouse.manager || '',
        capacitySqm: warehouse.capacitySqm || '',
        isActive: warehouse.isActive !== false,
        warehouseType: warehouse.warehouseType || 'STANDART'
      });
    }
  }, [warehouse]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;

     if (name === 'phone') {
      const digits = extractPhoneDigits(value);
      setFormData(prev => ({
        ...prev,
        phone: digits
      }));
      if (errors.phone) {
        setErrors(prev => ({ ...prev, phone: '' }));
      }
      return;
    }

    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));

    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'Depo adı gereklidir';
    }

    const loc = formData.location.trim();
    if (!loc) {
      newErrors.location = 'Konum gereklidir';
    } else if (loc.length < 3 || loc.length > 255) {
      newErrors.location = 'Konum 3-255 karakter aralığında olmalıdır.';
    }

    if (formData.phone && !isPhoneComplete(formData.phone)) {
      newErrors.phone = 'Telefon numarası 10 haneli olmalıdır';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      const dataToSend = {
        ...formData,
        capacitySqm: formData.capacitySqm ? parseFloat(formData.capacitySqm) : null,
        phone: formData.phone ? formatPhoneForSubmit(formData.phone) : null
      };

      if (warehouse) {
        await axios.put(`/api/warehouses/${warehouse.id}`, dataToSend);
      } else {
        await axios.post('/api/warehouses', dataToSend);
      }

      showToast('Depo başarıyla kaydedildi.', 'success');
      onSuccess();
    } catch (error) {
      console.error('Error saving warehouse:', error);
      const errData = error?.response?.data;
      const nextErrors = {};
      let general = 'Depo kaydedilirken hata oluştu';

      // Field-level validation mapping
      if (errData && typeof errData === 'object' && errData.fieldErrors) {
        const fe = errData.fieldErrors;
        if (fe.location) {
          nextErrors.location = 'Konum 5-255 karakter aralığında olmalıdır.';
        }
        if (fe.name) {
          nextErrors.name = 'Depo adı geçersiz, lütfen kontrol edin.';
        }
        if (fe.phone) {
          nextErrors.phone = 'Telefon numarası 10 haneli olmalıdır.';
        }
        if (Object.keys(fe).length > 0) {
          general = 'Form doğrulaması başarısız. Lütfen alan hatalarını kontrol edin.';
        }
      }

      if (typeof errData === 'string') {
        general = errData;
      } else if (errData && typeof errData === 'object') {
        if (typeof errData.message === 'string') {
          general = errData.message;
        } else if (Array.isArray(errData.details) && errData.details.length) {
          general = errData.details.join(', ');
        }
      }

      setErrors({ general, ...nextErrors });
      showToast(general, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="name" className="form-label">
              Depo Adı <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.name ? 'is-invalid' : ''}`}
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Ana Depo"
              required
            />
            {errors.name && <div className="invalid-feedback">{errors.name}</div>}
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="location" className="form-label">
              Konum <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.location ? 'is-invalid' : ''}`}
              id="location"
              name="location"
              value={formData.location}
              onChange={handleChange}
              placeholder="İstanbul, Türkiye"
              required
            />
            {errors.location && <div className="invalid-feedback">{errors.location}</div>}
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="manager" className="form-label">
              Depo Müdürü
            </label>
            <input
              type="text"
              className="form-control"
              id="manager"
              name="manager"
              value={formData.manager}
              onChange={handleChange}
              placeholder="Ahmet Yılmaz"
            />
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="phone" className="form-label">
              Telefon
            </label>
            <div className="input-group phone-input-group">
              <span className="input-group-text">+90</span>
              <input
                type="tel"
                className={`form-control ${errors.phone ? 'is-invalid' : (formData.phone ? (isPhoneComplete(formData.phone) ? 'is-valid' : '') : '')}`}
                id="phone"
                name="phone"
                value={formatPhoneInputValue(formData.phone)}
                onChange={handleChange}
                placeholder={PHONE_PLACEHOLDER}
                maxLength="13"
                inputMode="numeric"
              />
            </div>
            {errors.phone && <div className="invalid-feedback d-block">{errors.phone}</div>}
            {!errors.phone && formData.phone && !isPhoneComplete(formData.phone) && (
              <small className="text-muted">Telefon 10 haneli olmalıdır</small>
            )}
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="capacitySqm" className="form-label">
              Kapasite (m²)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className="form-control"
              id="capacitySqm"
              name="capacitySqm"
              value={formData.capacitySqm}
              onChange={handleChange}
              placeholder="1000.50"
            />
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="warehouseType" className="form-label">
              Depo Tipi <span className="text-danger">*</span>
            </label>
            <select
              className="form-select"
              id="warehouseType"
              name="warehouseType"
              value={formData.warehouseType}
              onChange={handleChange}
              required
            >
              <option value="STANDART">Standart Depo</option>
              <option value="EMANET_DEPO">Emanet Depo</option>
            </select>
            <small className="text-muted d-block mt-1">
              Emanet depo seçildiğinde, stok eklerken müşteri bilgisi alınacaktır.
            </small>
          </div>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6">
          <div className="mb-3">
            <div className="form-check mt-4">
              <input
                type="checkbox"
                className="form-check-input"
                id="isActive"
                name="isActive"
                checked={formData.isActive}
                onChange={handleChange}
              />
              <label className="form-check-label" htmlFor="isActive">
                Aktif
              </label>
            </div>
          </div>
        </div>
      </div>

      <div className="d-flex justify-content-end gap-2">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onCancel}
          disabled={loading}
        >
          İptal
        </button>
        <button
          type="submit"
          className="btn btn-primary"
          disabled={loading}
        >
          {loading ? (
            <>
              <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              Kaydediliyor...
            </>
          ) : (
            <>
              <i className="fas fa-save me-2"></i>
              {warehouse ? 'Güncelle' : 'Kaydet'}
            </>
          )}
        </button>
      </div>
    </form>
  );
};

export default WarehouseForm;
