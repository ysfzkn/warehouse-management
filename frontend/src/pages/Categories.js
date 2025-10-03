import React, { useState, useEffect } from 'react';
import axios from 'axios';
import CategoryForm from '../components/CategoryForm';

const Categories = () => {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/categories/with-counts');
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
      setError('Kategoriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingCategory(null);
    setShowForm(true);
  };

  const handleEdit = (category) => {
    setEditingCategory(category);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Bu kategoriyi silmek istediğinizden emin misiniz?')) {
      try {
        await axios.delete(`/api/categories/${id}`);
        fetchCategories();
      } catch (error) {
        alert('Kategori silinirken hata oluştu: ' + error.response?.data);
      }
    }
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setEditingCategory(null);
    fetchCategories();
  };

  if (loading) {
    return (
      <div className="text-center">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger" role="alert">
        {error}
      </div>
    );
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2>Kategoriler</h2>
        <button className="btn btn-primary" onClick={handleCreate}>
          <i className="fas fa-plus me-2"></i>
          Yeni Kategori
        </button>
      </div>

      <div className="row">
        {categories.map((category) => (
          <div key={category.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card h-100">
              <div className="card-body">
                <h5 className="card-title">{category.name}</h5>

                {category.description && (
                  <p className="card-text text-muted">
                    {category.description}
                  </p>
                )}

                <div className="d-flex justify-content-between align-items-center mt-3">
                  <span className="fw-bold">
                    <i className="fas fa-box me-1"></i>
                    Ürün Sayısı: {category.productCount}
                  </span>
                </div>

                <div className="mt-2">
                  <small className="text-muted">
                    <i className="fas fa-calendar me-1"></i>
                    Oluşturulma Tarihi: {new Date(category.createdAt).toLocaleDateString('tr-TR')}
                  </small>
                </div>
                <div className="mt-1">
                  <small className="text-muted">
                    <i className="fas fa-calendar me-1"></i>
                    Güncelleme Tarihi: {new Date(category.updatedAt).toLocaleDateString('tr-TR')}
                  </small>
                </div>
              </div>
              <div className="card-footer">
                <div className="btn-group w-100" role="group">
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => handleEdit(category)}
                  >
                    <i className="fas fa-edit me-1"></i>
                    Düzenle
                  </button>
                  <button
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => handleDelete(category.id)}
                    disabled={category.productCount > 0}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Sil
                  </button>
                </div>
                {category.productCount > 0 && (
                  <small className="text-muted mt-1 d-block">
                    <i className="fas fa-info-circle me-1"></i>
                    Ürün içeren kategoriler silinemez
                  </small>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {categories.length === 0 && (
        <div className="text-center py-5">
          <i className="fas fa-tags fa-3x text-muted mb-3"></i>
          <h4 className="text-muted">Henüz kategori bulunmuyor</h4>
          <p className="text-muted">İlk kategoriyi oluşturmak için "Yeni Kategori" butonuna tıklayın.</p>
        </div>
      )}

      {/* Category Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  {editingCategory ? 'Kategori Düzenle' : 'Yeni Kategori'}
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <CategoryForm
                  category={editingCategory}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Categories;
