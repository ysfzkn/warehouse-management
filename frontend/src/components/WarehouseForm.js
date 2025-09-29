import React, { useState, useEffect } from 'react';
import axios from 'axios';

const WarehouseForm = ({ warehouse, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    location: '',
    phone: '',
    manager: '',
    capacitySqm: '',
    isActive: true
  });
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (warehouse) {
      setFormData({
        name: warehouse.name || '',
        location: warehouse.location || '',
        phone: warehouse.phone || '',
        manager: warehouse.manager || '',
        capacitySqm: warehouse.capacitySqm || '',
        isActive: warehouse.isActive !== false
      });
    }
  }, [warehouse]);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
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

    if (!formData.location.trim()) {
      newErrors.location = 'Konum gereklidir';
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
        capacitySqm: formData.capacitySqm ? parseFloat(formData.capacitySqm) : null
      };

      if (warehouse) {
        await axios.put(`/api/warehouses/${warehouse.id}`, dataToSend);
      } else {
        await axios.post('/api/warehouses', dataToSend);
      }

      onSuccess();
    } catch (error) {
      console.error('Error saving warehouse:', error);
      if (error.response?.data) {
        setErrors({ general: error.response.data });
      } else {
        setErrors({ general: 'Depo kaydedilirken hata oluştu' });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      {errors.general && (
        <div className="alert alert-danger" role="alert">
          {errors.general}
        </div>
      )}

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
            <input
              type="tel"
              className="form-control"
              id="phone"
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              placeholder="0212 555 0000"
            />
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
