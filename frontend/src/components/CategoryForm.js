import React, { useState, useEffect } from 'react';
import axios from 'axios';

const CategoryForm = ({ category, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    parentId: ''
  });
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [showSubcategoryModal, setShowSubcategoryModal] = useState(false);
  const [createdCategoryId, setCreatedCategoryId] = useState(null);

  useEffect(() => {
    // Fetch categories in edit mode
    if (category && category.parentId) {
      fetchCategories();
    }
  }, [category]);

  useEffect(() => {
    if (category) {
      setFormData({
        name: category.name || '',
        description: category.description || '',
        parentId: category.parentId || ''
      });
    } else {
      // Leave parentId empty when creating a new category
      setFormData(prev => ({ ...prev, parentId: '' }));
    }
  }, [category]);

  const fetchCategories = async () => {
    try {
      const response = await axios.get('/api/categories/top-level');
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
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
      newErrors.name = 'Kategori adı gereklidir';
    } else if (formData.name.trim().length < 2) {
      newErrors.name = 'Kategori adı en az 2 karakter olmalıdır';
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
        name: formData.name.trim(),
        description: formData.description.trim()
      };

      if (category) {
        // Edit mode
        await axios.put(`/api/categories/${category.id}`, dataToSend);

        // Parent change (only for subcategories)
        if (category.parentId && formData.parentId !== category.parentId) {
          await axios.put(`/api/categories/${category.id}/parent?parentId=${formData.parentId || ''}`);
        }

        onSuccess();
      } else {
        // Create a new category - top-level only
        const response = await axios.post('/api/categories', dataToSend);
        setCreatedCategoryId(response.data.id);
        setShowSubcategoryModal(true);
      }
    } catch (error) {
      console.error('Error saving category:', error);
      if (error.response?.data) {
        setErrors({ general: error.response.data });
      } else {
        setErrors({ general: 'Kategori kaydedilirken hata oluştu' });
      }
    } finally {
      setLoading(false);
    }
  };

  if (showSubcategoryModal) {
    return (
      <SubcategoryModal
        parentCategoryId={createdCategoryId}
        onSuccess={() => {
          setShowSubcategoryModal(false);
          onSuccess();
        }}
        onSkip={() => {
          setShowSubcategoryModal(false);
          onSuccess();
        }}
      />
    );
  }

  return (
    <form onSubmit={handleSubmit}>
      {errors.general && (
        <div className="alert alert-danger" role="alert">
          {errors.general}
        </div>
      )}

      <div className="mb-3">
        <label htmlFor="name" className="form-label">
          Kategori Adı <span className="text-danger">*</span>
        </label>
        <input
          type="text"
          className={`form-control ${errors.name ? 'is-invalid' : ''}`}
          id="name"
          name="name"
          value={formData.name}
          onChange={handleChange}
          placeholder="Beyaz Eşya"
          required
        />
        {errors.name && <div className="invalid-feedback">{errors.name}</div>}
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">
          Açıklama
        </label>
        <textarea
          className="form-control"
          id="description"
          name="description"
          rows="3"
          value={formData.description}
          onChange={handleChange}
          placeholder="Bu kategorideki ürünler için açıklama..."
        />
      </div>

      {/* Parent Category Selection - Only when editing a subcategory */}
      {category && category.parentId && (
        <div className="mb-3">
          <label htmlFor="parentId" className="form-label">
            Üst Kategori
          </label>
          <select
            className="form-select"
            id="parentId"
            name="parentId"
            value={formData.parentId}
            onChange={handleChange}
          >
            <option value="">Ana Kategori (Üst kategori yok)</option>
            {categories
              .filter(cat => cat.id !== category.id) // Prevent selecting itself as parent
              .map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
          </select>
          <small className="text-muted">Bu kategorinin üst kategorisini değiştirebilirsiniz.</small>
        </div>
      )}

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
              {category ? 'Güncelle' : 'Kaydet'}
            </>
          )}
        </button>
      </div>
    </form>
  );
};

const SubcategoryModal = ({ parentCategoryId, onSuccess, onSkip }) => {
  const [subcategories, setSubcategories] = useState([{ name: '', description: '' }]);
  const [loading, setLoading] = useState(false);

  const handleSubcategoryChange = (index, field, value) => {
    const updated = [...subcategories];
    updated[index][field] = value;
    setSubcategories(updated);
  };

  const addSubcategory = () => {
    setSubcategories([...subcategories, { name: '', description: '' }]);
  };

  const removeSubcategory = (index) => {
    if (subcategories.length > 1) {
      setSubcategories(subcategories.filter((_, i) => i !== index));
    }
  };

  const handleSubmit = async () => {
    const validSubcategories = subcategories.filter(sub => sub.name.trim());
    if (validSubcategories.length === 0) {
      onSkip();
      return;
    }

    setLoading(true);
    try {
      await axios.post('/api/categories/batch', validSubcategories, {
        params: { parentId: parentCategoryId }
      });
      onSuccess();
    } catch (error) {
      console.error('Error creating subcategories:', error);
      alert('Alt kategoriler oluşturulurken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-lg">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">Alt Kategoriler Ekle</h5>
            <button
              type="button"
              className="btn-close"
              onClick={onSkip}
            ></button>
          </div>
          <div className="modal-body">
            <p>Bu ana kategoriye alt kategoriler eklemek ister misiniz?</p>

            {subcategories.map((subcategory, index) => (
              <div key={index} className="row mb-3">
                <div className="col-md-5">
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Alt kategori adı"
                    value={subcategory.name}
                    onChange={(e) => handleSubcategoryChange(index, 'name', e.target.value)}
                  />
                </div>
                <div className="col-md-5">
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Açıklama (opsiyonel)"
                    value={subcategory.description}
                    onChange={(e) => handleSubcategoryChange(index, 'description', e.target.value)}
                  />
                </div>
                <div className="col-md-2">
                  {subcategories.length > 1 && (
                    <button
                      type="button"
                      className="btn btn-outline-danger btn-sm"
                      onClick={() => removeSubcategory(index)}
                    >
                      <i className="fas fa-trash"></i>
                    </button>
                  )}
                </div>
              </div>
            ))}

            <button
              type="button"
              className="btn btn-outline-primary btn-sm mb-3"
              onClick={addSubcategory}
            >
              <i className="fas fa-plus me-1"></i>
              Alt Kategori Ekle
            </button>
          </div>
          <div className="modal-footer">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onSkip}
              disabled={loading}
            >
              Atla
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSubmit}
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                  Kaydediliyor...
                </>
              ) : (
                'Alt Kategorileri Kaydet'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CategoryForm;
