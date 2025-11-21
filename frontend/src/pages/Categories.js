import React, { useState, useEffect, useMemo, useCallback } from 'react';
import axios from 'axios';
import CategoryForm from '../components/CategoryForm';
import FilterChips from '../components/FilterChips';
import ConfirmModal from '../components/ConfirmModal';

const normalizeText = (text) => (text || '').toLocaleLowerCase('tr-TR');

const Categories = () => {
  const [mainCategories, setMainCategories] = useState([]);
  const [expandedCategories, setExpandedCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [showSubcategoryModal, setShowSubcategoryModal] = useState(false);
  const [selectedParentCategory, setSelectedParentCategory] = useState(null);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/categories/top-level');
      const categoriesWithSubInfo = await Promise.all(
        response.data.map(async (category) => {
          try {
            const subResponse = await axios.get(`/api/categories/${category.id}/subcategories`);
            const ownCount = Number(category.productCount ?? 0);
            const subTotalCount = (subResponse.data || []).reduce((sum, s) => sum + Number(s.productCount || 0), 0);
            return {
              ...category,
              subcategories: subResponse.data,
              productCount: ownCount,
              totalProductCount: ownCount + subTotalCount,
              totalSubcategories: subResponse.data.length
            };
          } catch (error) {
            console.error(`Error fetching subcategories for ${category.name}:`, error);
            return {
              ...category,
              subcategories: [],
              productCount: Number(category.productCount ?? 0),
              totalProductCount: Number(category.productCount ?? 0),
              totalSubcategories: 0
            };
          }
        })
      );
      setMainCategories(categoriesWithSubInfo);
    } catch (error) {
      console.error('Error fetching categories:', error);
      setError('Kategoriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const toggleCategoryExpansion = useCallback((categoryId) => {
    const id = Number(categoryId);
    setExpandedCategories(prev => {
      const isExpanded = prev.includes(id);
      if (isExpanded) {
        return prev.filter(expandedId => expandedId !== id);
      } else {
        return [...prev, id];
      }
    });
  }, []);


  const handleCreate = () => {
    setEditingCategory(null);
    setShowForm(true);
  };

  const handleEdit = (category) => {
    setEditingCategory(category);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    setConfirmModal({
      show: true,
      title: 'Kategori Silme',
      message: 'Bu kategoriyi silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        try {
          await axios.delete(`/api/categories/${id}`);
          fetchCategories();
        } catch (error) {
          const msg = error.response?.data || 'Kategori silinirken hata oluştu';
          const toast = document.createElement('div');
          toast.className = 'toast align-items-center text-bg-danger border-0 position-fixed top-0 end-0 m-3 show';
          toast.setAttribute('role', 'alert');
          toast.innerHTML = `<div class="d-flex"><div class="toast-body">${msg}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
          document.body.appendChild(toast);
          setTimeout(() => { try { document.body.removeChild(toast); } catch {} }, 3500);
        }
      }
    });
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setEditingCategory(null);
    fetchCategories();
  };

  const handleAddSubcategory = (parentCategory) => {
    setSelectedParentCategory(parentCategory);
    setShowSubcategoryModal(true);
  };

  const handleSubcategorySuccess = () => {
    setShowSubcategoryModal(false);
    setSelectedParentCategory(null);
    fetchCategories();
  };

  const filteredCategories = useMemo(() => {
    const q = normalizeText(searchTerm);
    if (!q) return mainCategories;
    return mainCategories.filter(c =>
      normalizeText(c.name).includes(q) ||
      normalizeText(c.description).includes(q) ||
      c.subcategories.some(sub => normalizeText(sub.name).includes(q))
    );
  }, [mainCategories, searchTerm]);

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

      {/* Filters */}
      <div className="row mb-3">
        <div className="col-md-8">
          <div className="input-group">
            <span className="input-group-text"><i className="fas fa-search"></i></span>
            <input
              type="text"
              className="form-control"
              placeholder="Kategori adı veya açıklama ara..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
      </div>

      {searchTerm && (
        <FilterChips
          className="mb-3"
          chips={[{ icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') }]}
          onClearAll={() => setSearchTerm('')}
        />
      )}

      <div className="row align-items-start">
        {filteredCategories.map((category) => (
          <div key={category.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <div
                    className="flex-grow-1"
                    style={{ cursor: category.totalSubcategories > 0 ? 'pointer' : 'default' }}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      if (category.totalSubcategories > 0) {
                        toggleCategoryExpansion(category.id);
                      }
                    }}
                  >
                    <div className="d-flex align-items-center justify-content-between">
                      <div className="d-flex align-items-center">
                        <h5 className="card-title mb-0">{category.name}</h5>
                        {category.totalSubcategories > 0 && (
                          <small className="text-primary ms-2">
                            ({category.totalSubcategories} alt kategori)
                          </small>
                        )}
                      </div>
                      {category.totalSubcategories > 0 && (
                        <div className="d-flex align-items-center">
                          <small className="text-muted me-2">
                            {expandedCategories.includes(Number(category.id)) ? 'Gizle' : 'Göster'}
                          </small>
                          <i className={`fas fa-chevron-${expandedCategories.includes(Number(category.id)) ? 'down' : 'right'} text-primary`}></i>
                        </div>
                      )}
                    </div>
                    {category.description && (
                      <p className="card-text text-muted small mt-1">
                        {category.description}
                      </p>
                    )}
                  </div>
                </div>

                <div className="d-flex justify-content-between align-items-center mt-3">
                  <div className="d-flex gap-3">
                    <span className="fw-bold text-primary">
                      <i className="fas fa-box me-1"></i>
                      {(category.totalProductCount ?? category.productCount)} Ürün
                    </span>
                    {category.totalSubcategories > 0 && (
                      <span className="fw-bold text-info">
                        <i className="fas fa-sitemap me-1"></i>
                        {category.totalSubcategories} Alt
                      </span>
                    )}
                  </div>
                </div>

                <div className="mt-2">
                  <small className="text-muted">
                    <i className="fas fa-calendar me-1"></i>
                    {new Date(category.createdAt).toLocaleDateString('tr-TR')}
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
                    className="btn btn-outline-primary btn-sm"
                    onClick={() => handleAddSubcategory(category)}
                  >
                    <i className="fas fa-plus me-1"></i>
                    Alt Kategori
                  </button>
                  <button
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => handleDelete(category.id)}
                    disabled={(category.totalProductCount ?? category.productCount) > 0 || category.totalSubcategories > 0}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Sil
                  </button>
                </div>
              </div>

              {/* Alt Kategoriler - Expand edildiğinde göster */}
              {expandedCategories.includes(Number(category.id)) && category.subcategories && category.subcategories.length > 0 && (

                <div className="card-footer bg-light border-top">
                  <div className="small mb-2 fw-bold text-primary">
                    <i className="fas fa-sitemap me-1"></i>
                    Alt Kategoriler:
                  </div>
                  {category.subcategories.map((subcategory) => (
                    <div key={subcategory.id} className="d-flex justify-content-between align-items-center py-1 border-bottom">
                      <div className="flex-grow-1">
                        <small className="fw-bold">{subcategory.name}</small>
                        <br />
                        <small className="text-muted">
                          <i className="fas fa-box me-1"></i>
                          {subcategory.productCount || 0} ürün
                        </small>
                      </div>
                      <div className="btn-group btn-group-sm">
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => handleEdit(subcategory)}
                          title="Düzenle"
                        >
                          <i className="fas fa-edit"></i>
                        </button>
                        <button
                          className="btn btn-outline-danger btn-sm"
                          onClick={() => handleDelete(subcategory.id)}
                          disabled={(subcategory.productCount || 0) > 0}
                          title={(subcategory.productCount || 0) > 0 ? "Ürün içeren kategoriler silinemez" : "Sil"}
                        >
                          <i className="fas fa-trash"></i>
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {mainCategories.length === 0 && (
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

      {/* Subcategory Modal */}
      {showSubcategoryModal && selectedParentCategory && (
        <SubcategoryModal
          parentCategoryId={selectedParentCategory.id}
          parentCategoryName={selectedParentCategory.name}
          onSuccess={handleSubcategorySuccess}
          onCancel={() => {
            setShowSubcategoryModal(false);
            setSelectedParentCategory(null);
          }}
        />
      )}

      {/* Confirm Modal */}
      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        icon={confirmModal.icon}
        confirmVariant={confirmModal.confirmVariant}
        confirmText={confirmModal.confirmText}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false, title: '', message: '', onConfirm: null })}
      />
    </div>
  );
};

const SubcategoryModal = ({ parentCategoryId, parentCategoryName, onSuccess, onCancel }) => {
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
      onCancel();
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
            <h5 className="modal-title">
              "{parentCategoryName}" kategorisine alt kategoriler ekle
            </h5>
            <button
              type="button"
              className="btn-close"
              onClick={onCancel}
            ></button>
          </div>
          <div className="modal-body">
            <p>Bu ana kategoriye alt kategoriler eklemek için aşağıdaki formu doldurun.</p>

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
              onClick={onCancel}
              disabled={loading}
            >
              İptal
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

export default Categories;
