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
  const [selectedCategories, setSelectedCategories] = useState([]);

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

  // Selection handlers
  const allVisibleCategoryIds = filteredCategories.map(c => c.id);
  const areAllVisibleSelected = filteredCategories.length > 0 && allVisibleCategoryIds.every(id => selectedCategories.includes(id));
  const selectedCategoryCount = selectedCategories.length;

  const toggleSelectAllVisible = () => {
    if (!filteredCategories.length) return;
    if (areAllVisibleSelected) {
      setSelectedCategories(prev => prev.filter(id => !allVisibleCategoryIds.includes(id)));
    } else {
      setSelectedCategories(prev => [...new Set([...prev, ...allVisibleCategoryIds])]);
    }
  };

  const toggleCategorySelection = (id) => {
    setSelectedCategories(prev =>
      prev.includes(id) ? prev.filter(existingId => existingId !== id) : [...prev, id]
    );
  };

  const clearSelectedCategories = () => {
    setSelectedCategories([]);
  };

  const handleBatchDeleteCategories = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    setConfirmModal({
      show: true,
      title: 'Toplu Kategori Silme',
      message: `${ids.length} kategoriyi silmek istediğinizden emin misiniz? Bu işlem geri alınamaz. Ürün veya alt kategori içeren kategoriler silinemez.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Evet, Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        try {
          const deletePromises = ids.map(id => axios.delete(`/api/categories/${id}`));
          const results = await Promise.allSettled(deletePromises);
          const successful = results.filter(r => r.status === 'fulfilled').length;
          const failed = results.filter(r => r.status === 'rejected').length;
          setSelectedCategories([]);
          fetchCategories();
          const toast = document.createElement('div');
          toast.className = `toast align-items-center text-bg-${failed > 0 ? 'warning' : 'success'} border-0 position-fixed top-0 end-0 m-3 show`;
          toast.setAttribute('role', 'alert');
          const message = failed > 0 
            ? `${successful} kategori silindi, ${failed} kategori silinemedi (ürün veya alt kategori içeriyor olabilir)`
            : `${successful} kategori başarıyla silindi`;
          toast.innerHTML = `<div class="d-flex"><div class="toast-body">${message}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
          document.body.appendChild(toast);
          setTimeout(() => { try { document.body.removeChild(toast); } catch {} }, 3500);
        } catch (error) {
          const msg = error.response?.data || 'Kategoriler silinirken hata oluştu';
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

  useEffect(() => {
    if (!selectedCategories.length) return;
    setSelectedCategories(prev => prev.filter(id => mainCategories.some(category => category.id === id)));
  }, [mainCategories, selectedCategories.length]);

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

      <div className="card">
        <div className="card-body">
          {selectedCategoryCount > 0 && (
            <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div className="fw-semibold">
                <i className="fas fa-check-square me-2"></i>
                {selectedCategoryCount} kategori seçildi
              </div>
              <div className="d-flex gap-2 flex-wrap">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={clearSelectedCategories}
                >
                  Seçimi Temizle
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={() => handleBatchDeleteCategories([...selectedCategories])}
                >
                  <i className="fas fa-trash me-1"></i>
                  Seçilileri Sil
                </button>
              </div>
            </div>
          )}
          {/* Desktop Table View */}
          <div className="d-none d-lg-block table-responsive">
            <table className="table table-hover align-middle">
              <thead className="table-light">
                <tr>
                  <th className="text-center" style={{ width: '40px' }}>
                    <div className="form-check mb-0">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={areAllVisibleSelected}
                        onChange={toggleSelectAllVisible}
                        disabled={filteredCategories.length === 0}
                        aria-label="Tümünü seç"
                      />
                    </div>
                  </th>
                  <th style={{ width: '30px' }}></th>
                  <th>Kategori Adı</th>
                  <th>Açıklama</th>
                  <th className="text-center">Ürün Sayısı</th>
                  <th className="text-center">Alt Kategori</th>
                  <th>Oluşturulma Tarihi</th>
                  <th className="text-center" style={{ width: '250px' }}>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {filteredCategories.map((category) => {
                  const isSelected = selectedCategories.includes(category.id);
                  return (
                  <React.Fragment key={category.id}>
                    <tr
                      className={isSelected ? 'table-active' : ''}
                      style={{ cursor: category.totalSubcategories > 0 ? 'pointer' : 'default' }}
                      onClick={(e) => {
                        if (category.totalSubcategories > 0 && !e.target.closest('button, .btn-group, .form-check')) {
                          toggleCategoryExpansion(category.id);
                        }
                      }}
                    >
                      <td className="text-center align-middle">
                        <div className="form-check mb-0" onClick={(e) => e.stopPropagation()}>
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleCategorySelection(category.id)}
                            aria-label="Kategori seç"
                          />
                        </div>
                      </td>
                      <td>
                        {category.totalSubcategories > 0 && (
                          <i className={`fas fa-chevron-${expandedCategories.includes(Number(category.id)) ? 'down' : 'right'} text-primary`}></i>
                        )}
                      </td>
                      <td>
                        <div className="fw-semibold">{category.name}</div>
                        {category.totalSubcategories > 0 && (
                          <small className="text-primary">
                            <i className="fas fa-sitemap me-1"></i>
                            {category.totalSubcategories} alt kategori
                          </small>
                        )}
                      </td>
                      <td>
                        <span className="text-muted small">
                          {category.description || '-'}
                        </span>
                      </td>
                      <td className="text-center">
                        <span className="badge bg-primary bg-opacity-10 text-primary">
                          <i className="fas fa-box me-1"></i>
                          {category.totalProductCount ?? category.productCount}
                        </span>
                      </td>
                      <td className="text-center">
                        {category.totalSubcategories > 0 ? (
                          <span className="badge bg-info bg-opacity-10 text-info">
                            <i className="fas fa-sitemap me-1"></i>
                            {category.totalSubcategories}
                          </span>
                        ) : (
                          <span className="text-muted">-</span>
                        )}
                      </td>
                      <td>
                        <small className="text-muted">
                          <i className="fas fa-calendar me-1"></i>
                          {new Date(category.createdAt).toLocaleDateString('tr-TR')}
                        </small>
                      </td>
                      <td className="text-center">
                        <div className="d-flex justify-content-center">
                          <div className="btn-group" role="group" onClick={(e) => e.stopPropagation()}>
                            <button
                              className="btn btn-outline-secondary"
                              onClick={() => handleEdit(category)}
                              title="Düzenle"
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-edit"></i>
                            </button>
                            <button
                              className="btn btn-outline-primary"
                              onClick={() => handleAddSubcategory(category)}
                              title="Alt Kategori Ekle"
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-plus"></i>
                            </button>
                            <button
                              className="btn btn-outline-danger"
                              onClick={() => handleDelete(category.id)}
                              disabled={(category.totalProductCount ?? category.productCount) > 0 || category.totalSubcategories > 0}
                              title={(category.totalProductCount ?? category.productCount) > 0 || category.totalSubcategories > 0 ? "Ürün veya alt kategori içeren kategoriler silinemez" : "Sil"}
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-trash"></i>
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                    {/* Alt Kategoriler - Expand edildiğinde göster */}
                    {expandedCategories.includes(Number(category.id)) && category.subcategories && category.subcategories.length > 0 && (
                      <tr className="table-light">
                        <td colSpan="7" className="p-0">
                          <div className="p-3 bg-light">
                            <div className="small mb-2 fw-bold text-primary">
                              <i className="fas fa-sitemap me-1"></i>
                              Alt Kategoriler:
                            </div>
                            <div className="table-responsive">
                              <table className="table table-sm table-bordered mb-0">
                                <thead>
                                  <tr>
                                    <th>Alt Kategori Adı</th>
                                    <th className="text-center">Ürün Sayısı</th>
                                    <th className="text-center" style={{ width: '180px' }}>İşlemler</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {category.subcategories.map((subcategory) => (
                                    <tr key={subcategory.id}>
                                      <td>
                                        <div className="fw-semibold">{subcategory.name}</div>
                                      </td>
                                      <td className="text-center">
                                        <span className="badge bg-primary bg-opacity-10 text-primary">
                                          <i className="fas fa-box me-1"></i>
                                          {subcategory.productCount || 0}
                                        </span>
                                      </td>
                                      <td className="text-center">
                                        <div className="d-flex justify-content-center">
                                          <div className="btn-group" role="group">
                                            <button
                                              className="btn btn-outline-secondary"
                                              onClick={() => handleEdit(subcategory)}
                                              title="Düzenle"
                                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                                            >
                                              <i className="fas fa-edit"></i>
                                            </button>
                                            <button
                                              className="btn btn-outline-danger"
                                              onClick={() => handleDelete(subcategory.id)}
                                              disabled={(subcategory.productCount || 0) > 0}
                                              title={(subcategory.productCount || 0) > 0 ? "Ürün içeren kategoriler silinemez" : "Sil"}
                                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                                            >
                                              <i className="fas fa-trash"></i>
                                            </button>
                                          </div>
                                        </div>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View */}
          <div className="d-lg-none">
            <div className="d-flex flex-column gap-3">
              {filteredCategories.map((category) => {
                const isSelected = selectedCategories.includes(category.id);
                const isExpanded = expandedCategories.includes(Number(category.id));
                
                return (
                  <div key={category.id}>
                    <div
                      className={`card border shadow-sm ${isSelected ? 'border-primary bg-primary bg-opacity-10' : ''}`}
                    >
                      <div className="card-body p-3">
                        {/* Header */}
                        <div className="d-flex justify-content-between align-items-start mb-3">
                          <div className="flex-grow-1">
                            <div className="d-flex align-items-center gap-2 mb-2">
                              <div className="form-check">
                                <input
                                  className="form-check-input"
                                  type="checkbox"
                                  checked={isSelected}
                                  onChange={() => toggleCategorySelection(category.id)}
                                  aria-label="Kategori seç"
                                />
                              </div>
                              <div>
                                <div className="fw-bold mb-1" style={{ fontSize: '1.05rem' }}>{category.name}</div>
                                {category.totalSubcategories > 0 && (
                                  <small className="text-primary">
                                    <i className="fas fa-sitemap me-1"></i>
                                    {category.totalSubcategories} alt kategori
                                  </small>
                                )}
                              </div>
                            </div>
                            {category.description && (
                              <small className="text-muted d-block mb-2">
                                {category.description.length > 60 ? `${category.description.substring(0, 60)}...` : category.description}
                              </small>
                            )}
                          </div>
                          {category.totalSubcategories > 0 && (
                            <button
                              className="btn btn-sm btn-outline-primary"
                              onClick={() => toggleCategoryExpansion(category.id)}
                            >
                              <i className={`fas fa-chevron-${isExpanded ? 'up' : 'down'}`}></i>
                            </button>
                          )}
                        </div>

                        {/* Info Grid */}
                        <div className="row g-2 mb-3">
                          <div className="col-6">
                            <div className="text-center p-2 bg-primary bg-opacity-10 rounded border border-primary">
                              <div className="small text-muted mb-1">
                                <i className="fas fa-box me-1"></i>
                                Ürün Sayısı
                              </div>
                              <div className="fw-bold text-primary" style={{ fontSize: '1.1rem' }}>
                                {category.totalProductCount ?? category.productCount}
                              </div>
                            </div>
                          </div>
                          <div className="col-6">
                            <div className="text-center p-2 bg-info bg-opacity-10 rounded border border-info">
                              <div className="small text-muted mb-1">
                                <i className="fas fa-sitemap me-1"></i>
                                Alt Kategori
                              </div>
                              <div className="fw-bold text-info" style={{ fontSize: '1.1rem' }}>
                                {category.totalSubcategories || 0}
                              </div>
                            </div>
                          </div>
                        </div>

                        {/* Date */}
                        <div className="mb-3">
                          <small className="text-muted">
                            <i className="fas fa-calendar me-1"></i>
                            Oluşturulma: {new Date(category.createdAt).toLocaleDateString('tr-TR')}
                          </small>
                        </div>

                        {/* Actions */}
                        <div className="d-flex flex-wrap gap-2">
                          <button
                            className="btn btn-sm btn-outline-secondary flex-fill"
                            onClick={() => handleEdit(category)}
                          >
                            <i className="fas fa-edit me-1"></i>
                            Düzenle
                          </button>
                          <button
                            className="btn btn-sm btn-outline-primary flex-fill"
                            onClick={() => handleAddSubcategory(category)}
                          >
                            <i className="fas fa-plus me-1"></i>
                            Alt Kategori
                          </button>
                          <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => handleDelete(category.id)}
                            disabled={(category.totalProductCount ?? category.productCount) > 0 || category.totalSubcategories > 0}
                            title={(category.totalProductCount ?? category.productCount) > 0 || category.totalSubcategories > 0 ? "Ürün veya alt kategori içeren kategoriler silinemez" : "Sil"}
                          >
                            <i className="fas fa-trash"></i>
                          </button>
                        </div>
                      </div>
                    </div>

                    {/* Subcategories - Mobile View */}
                    {isExpanded && category.subcategories && category.subcategories.length > 0 && (
                      <div className="mt-2 ms-3">
                        <div className="small fw-bold text-primary mb-2">
                          <i className="fas fa-sitemap me-1"></i>
                          Alt Kategoriler:
                        </div>
                        <div className="d-flex flex-column gap-2">
                          {category.subcategories.map((subcategory) => (
                            <div key={subcategory.id} className="card border border-info bg-info bg-opacity-5">
                              <div className="card-body p-2">
                                <div className="d-flex justify-content-between align-items-center">
                                  <div>
                                    <div className="fw-semibold small">{subcategory.name}</div>
                                    <span className="badge bg-primary bg-opacity-10 text-primary" style={{ fontSize: '0.7rem' }}>
                                      <i className="fas fa-box me-1"></i>
                                      {subcategory.productCount || 0} ürün
                                    </span>
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
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
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
