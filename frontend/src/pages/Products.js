import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ProductForm from '../components/ProductForm';
import SearchableSelect from '../components/SearchableSelect';
import ConfirmModal from '../components/ConfirmModal';

const normalizeText = (text) => (text || '').toLocaleLowerCase('tr-TR');

const Products = () => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedSubcategory, setSelectedSubcategory] = useState('');
  const [subcategories, setSubcategories] = useState([]);
  const [selectedBrand, setSelectedBrand] = useState(null);
  const [selectedColor, setSelectedColor] = useState(null);
  const [selectedBrandOpt, setSelectedBrandOpt] = useState(null);
  const [selectedColorOpt, setSelectedColorOpt] = useState(null);
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [errorModal, setErrorModal] = useState({ show: false, title: '', message: '' });
  const [showDetailedPrice, setShowDetailedPrice] = useState(true);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [bulkMode, setBulkMode] = useState('PERCENTAGE');
  const [bulkDirection, setBulkDirection] = useState('INCREASE');
  const [bulkValue, setBulkValue] = useState('');
  const [bulkOnlyActive, setBulkOnlyActive] = useState(true);
  const [selectedProducts, setSelectedProducts] = useState([]);

  useEffect(() => {
    fetchProducts();
    fetchMainCategories();
  }, []);

  useEffect(() => {
    if (selectedCategory) {
      fetchSubcategories(selectedCategory);
      setSelectedSubcategory(''); // Ana kategori değiştiğinde alt kategoriyi sıfırla
    } else {
      setSubcategories([]);
      setSelectedSubcategory('');
    }
  }, [selectedCategory]);

  useEffect(() => {
    const normalizedSearch = normalizeText(searchTerm);
    const filteredProducts = products.filter(product => {
      const matchesSearch = !normalizedSearch ||
        normalizeText(product.name).includes(normalizedSearch) ||
        normalizeText(product.sku).includes(normalizedSearch);
      const categoryIdStr = product.category?.id != null ? product.category.id.toString() : '';
      const parentIdStr = product.category?.parent?.id != null ? product.category.parent.id.toString() : '';
      const matchesCategory = !selectedCategory || categoryIdStr === selectedCategory || parentIdStr === selectedCategory;
      const matchesSubcategory = !selectedSubcategory || categoryIdStr === selectedSubcategory;
      const matchesBrand = !selectedBrand || product.brand?.id === selectedBrand;
      const matchesColor = !selectedColor || product.color?.id === selectedColor;
      return matchesSearch && matchesCategory && matchesSubcategory && matchesBrand && matchesColor;
    });
    setFilteredProducts(filteredProducts);
  }, [products, searchTerm, selectedCategory, selectedSubcategory, selectedBrand, selectedColor]);

  const [filteredProducts, setFilteredProducts] = useState([]);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/products');
      const list = response.data || [];
      // Fetch total stock per product to avoid 0 when stocks are not included in product payload
      const totals = await Promise.all(
        list.map(async (p) => {
          try {
            const r = await axios.get(`/api/stocks/product/${p.id}/total-quantity`);
            return { id: p.id, total: typeof r.data === 'number' ? r.data : 0 };
          } catch {
            return { id: p.id, total: 0 };
          }
        })
      );
      const idToTotal = totals.reduce((acc, t) => { acc[t.id] = t.total; return acc; }, {});
      setProducts(list.map(p => ({ ...p, totalStock: idToTotal[p.id] ?? 0 })));
    } catch (error) {
      console.error('Error fetching products:', error);
      setError('Ürünler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const fetchMainCategories = async () => {
    try {
      const response = await axios.get('/api/categories/top-level');
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching main categories:', error);
    }
  };

  const fetchSubcategories = async (parentId) => {
    try {
      const response = await axios.get(`/api/categories/${parentId}/subcategories`);
      setSubcategories(response.data);
    } catch (error) {
      console.error('Error fetching subcategories:', error);
    }
  };

  const clearAllFilters = () => {
    setSearchTerm('');
    setSelectedCategory('');
    setSelectedSubcategory('');
    setSelectedBrand(null);
    setSelectedColor(null);
    setSelectedBrandOpt(null);
    setSelectedColorOpt(null);
  };

  const handleCreate = () => {
    setEditingProduct(null);
    setShowForm(true);
  };

  const handleEdit = (product) => {
    setEditingProduct(product);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    setConfirmModal({
      show: true,
      title: 'Ürün Silme',
      message: 'Bu ürünü silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        try {
          await axios.delete(`/api/products/${id}`);
          fetchProducts();
        } catch (error) {
          const msg = error.response?.data || 'Ürün silinirken hata oluştu';
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

  const handleToggleActive = async (id, active) => {
    try {
      if (active) {
        await axios.put(`/api/products/${id}/deactivate`);
      } else {
        await axios.put(`/api/products/${id}/activate`);
      }
      fetchProducts();
    } catch (error) {
      const errorData = error?.response?.data;
      const message = errorData?.message || errorData?.error || (typeof errorData === 'string' ? errorData : 'Durum değiştirilirken beklenmeyen bir hata oluştu.');
      setErrorModal({
        show: true,
        title: 'Durum Güncelleme Hatası',
        message
      });
    }
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setEditingProduct(null);
    fetchProducts();
  };

  const getTotalStockQuantity = (product) => {
    return product.stocks ? product.stocks.reduce((total, stock) => total + stock.quantity, 0) : 0;
  };

  const getLowStockCount = (product) => {
    return product.stocks ? product.stocks.filter(stock => stock.quantity <= stock.minStockLevel).length : 0;
  };

  // Selection handlers
  const allVisibleProductIds = filteredProducts.map(p => p.id);
  const areAllVisibleSelected = filteredProducts.length > 0 && allVisibleProductIds.every(id => selectedProducts.includes(id));
  const selectedProductCount = selectedProducts.length;

  const toggleSelectAllVisible = () => {
    if (!filteredProducts.length) return;
    if (areAllVisibleSelected) {
      setSelectedProducts(prev => prev.filter(id => !allVisibleProductIds.includes(id)));
    } else {
      setSelectedProducts(prev => [...new Set([...prev, ...allVisibleProductIds])]);
    }
  };

  const toggleProductSelection = (id) => {
    setSelectedProducts(prev =>
      prev.includes(id) ? prev.filter(existingId => existingId !== id) : [...prev, id]
    );
  };

  const clearSelectedProducts = () => {
    setSelectedProducts([]);
  };

  const handleBatchDeleteProducts = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    setConfirmModal({
      show: true,
      title: 'Toplu Ürün Silme',
      message: `${ids.length} ürünü silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Evet, Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        try {
          const deletePromises = ids.map(id => axios.delete(`/api/products/${id}`));
          await Promise.all(deletePromises);
          setSelectedProducts([]);
          fetchProducts();
          const toast = document.createElement('div');
          toast.className = 'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show';
          toast.setAttribute('role', 'alert');
          toast.innerHTML = `<div class="d-flex"><div class="toast-body">${ids.length} ürün başarıyla silindi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
          document.body.appendChild(toast);
          setTimeout(() => { try { document.body.removeChild(toast); } catch {} }, 3500);
        } catch (error) {
          const msg = error.response?.data || 'Ürünler silinirken hata oluştu';
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
    if (!selectedProducts.length) return;
    setSelectedProducts(prev => prev.filter(id => products.some(product => product.id === id)));
  }, [products, selectedProducts.length]);

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
        <h2>Ürünler</h2>
        <div className="d-flex gap-2">
          <button 
            className="btn btn-outline-secondary btn-sm"
            onClick={() => setShowBulkModal(true)}
            title="Toplu Fiyat Güncelle"
          >
            <i className="fas fa-percent me-2"></i>
            Toplu Fiyat
          </button>
          <button 
            className={`btn btn-sm ${showDetailedPrice ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => setShowDetailedPrice(!showDetailedPrice)}
            title={showDetailedPrice ? 'Basit Görünüm' : 'Detaylı Görünüm'}
          >
            <i className={`fas ${showDetailedPrice ? 'fa-eye-slash' : 'fa-calculator'} me-1`}></i>
            {showDetailedPrice ? 'Basit Fiyat' : 'Detaylı Fiyat'}
          </button>
          <button className="btn btn-primary" onClick={handleCreate}>
            <i className="fas fa-plus me-2"></i>
            Yeni Ürün
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="row mb-4">
        <div className="col-md-4">
          <div className="input-group">
            <span className="input-group-text">
              <i className="fas fa-search"></i>
            </span>
            <input
              type="text"
              className="form-control"
              placeholder="Ürün adı veya Stok Kodu ara..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
        <div className="col-md-4">
          <select
            className="form-select"
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
          >
            <option value="">Tüm Ana Kategoriler</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>
        <div className="col-md-4">
          <select
            className="form-select"
            value={selectedSubcategory}
            onChange={(e) => setSelectedSubcategory(e.target.value)}
            disabled={!selectedCategory}
          >
            <option value="">Tüm Alt Kategoriler</option>
            {subcategories.map((subcategory) => (
              <option key={subcategory.id} value={subcategory.id}>
                {subcategory.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="row mb-4">
        <div className="col-md-6">
          <SearchableSelect
            label="Marka Filtresi"
            value={selectedBrand}
            onChange={(id, opt) => { setSelectedBrand(id); setSelectedBrandOpt(opt || null); }}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
            allowClear={true}
            clearText="Temizle"
          />
        </div>
        <div className="col-md-6">
          <SearchableSelect
            label="Renk Filtresi"
            value={selectedColor}
            onChange={(id, opt) => { setSelectedColor(id); setSelectedColorOpt(opt || null); }}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
            allowClear={true}
            clearText="Temizle"
          />
        </div>
      </div>

      {/* Active Filters Chips */}
      {(searchTerm || selectedCategory || selectedSubcategory || selectedBrand || selectedColor) && (
        <div className="mb-3 d-flex flex-wrap align-items-center gap-2">
          <span className="text-muted me-1">Aktif filtreler:</span>
          {searchTerm && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-search me-1"></i> Arama: "{searchTerm}"
              <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={() => setSearchTerm('')}>
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedCategory && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-tag me-1"></i> Ana Kategori: {categories.find(c => c.id.toString() === selectedCategory)?.name || selectedCategory}
              <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={() => setSelectedCategory('')}>
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedSubcategory && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-tags me-1"></i> Alt Kategori: {subcategories.find(c => c.id.toString() === selectedSubcategory)?.name || selectedSubcategory}
              <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={() => setSelectedSubcategory('')}>
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedBrand && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-copyright me-1"></i> Marka: {selectedBrandOpt?.name || selectedBrand}
              <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={() => { setSelectedBrand(null); setSelectedBrandOpt(null); }}>
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedColor && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-palette me-1"></i> Renk: {selectedColorOpt?.name || selectedColor}
              <button type="button" className="btn btn-sm btn-link ms-2 p-0" onClick={() => { setSelectedColor(null); setSelectedColorOpt(null); }}>
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          <button type="button" className="btn btn-sm btn-outline-secondary ms-1" onClick={clearAllFilters}>
            Tümünü Temizle
          </button>
        </div>
      )}

      {/* Products List */}
      <div className="card">
        <div className="card-body">
          {selectedProductCount > 0 && (
            <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div className="fw-semibold">
                <i className="fas fa-check-square me-2"></i>
                {selectedProductCount} ürün seçildi
              </div>
              <div className="d-flex gap-2 flex-wrap">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={clearSelectedProducts}
                >
                  Seçimi Temizle
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={() => handleBatchDeleteProducts([...selectedProducts])}
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
                        disabled={filteredProducts.length === 0}
                        aria-label="Tümünü seç"
                      />
                    </div>
                  </th>
                  <th>Ürün Adı</th>
                  <th>Stok Kodu</th>
                  <th>Kategori</th>
                  <th>Marka</th>
                  <th>Renk</th>
                  <th className="text-end">Fiyat</th>
                  <th className="text-center">Stok</th>
                  <th className="text-center">Durum</th>
                  <th className="text-center" style={{ width: '280px' }}>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map((product) => {
                  const calculateTotalPrice = () => {
                    const sctAmount = product.price * (product.sctRate || 0) / 100;
                    const priceWithSct = product.price + sctAmount;
                    const vatAmount = priceWithSct * (product.vatRate || 0) / 100;
                    return priceWithSct + vatAmount;
                  };
                  const totalPrice = calculateTotalPrice();
                  const w = product.widthCm || 0;
                  const l = product.lengthCm || 0;
                  const h = product.heightCm || 0;
                  const desi = (h * w * l) / 3000;
                  const shippingCost = desi * (product.shippingRate || 0);

                  const isSelected = selectedProducts.includes(product.id);

                  return (
                    <tr key={product.id} className={isSelected ? 'table-active' : ''}>
                      <td className="text-center align-middle">
                        <div className="form-check mb-0">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleProductSelection(product.id)}
                            aria-label="Ürün seç"
                          />
                        </div>
                      </td>
                      <td>
                        <div className="fw-semibold">{product.name}</div>
                        {product.description && (
                          <small className="text-muted d-block mt-1" style={{ maxWidth: '300px' }}>
                            {product.description.length > 80
                              ? product.description.substring(0, 80) + '...'
                              : product.description}
                          </small>
                        )}
                      </td>
                      <td>
                        <span className="badge text-bg-light border">
                          <i className="fas fa-barcode me-1"></i>{product.sku}
                        </span>
                      </td>
                      <td>
                        {product.category?.name ? (
                          <span className="badge text-bg-light border">
                            <i className="fas fa-tag me-1"></i>
                            {product.category.parentName ? `${product.category.parentName} > ` : ''}
                            {product.category.name}
                          </span>
                        ) : (
                          <span className="text-muted">-</span>
                        )}
                      </td>
                      <td>
                        {product.brand?.name ? (
                          <span className="badge text-bg-light border">
                            <i className="fas fa-copyright me-1"></i>{product.brand.name}
                          </span>
                        ) : (
                          <span className="text-muted">-</span>
                        )}
                      </td>
                      <td>
                        {product.color?.name ? (
                          <span className="badge text-bg-light border">
                            <i className="fas fa-palette me-1"></i>{product.color.name}
                          </span>
                        ) : (
                          <span className="text-muted">-</span>
                        )}
                      </td>
                      <td className="text-end">
                        <div className="d-flex flex-column align-items-end">
                          <strong className="text-success">
                            ₺{totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                          </strong>
                          {showDetailedPrice && (product.vatRate > 0 || product.sctRate > 0) && (
                            <small className="text-muted">
                              Ana: ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                            </small>
                          )}
                          {(product.vatRate > 0 || product.sctRate > 0) && (
                            <small className="text-info">
                              {product.vatRate > 0 && `KDV %${product.vatRate}`}
                              {product.vatRate > 0 && product.sctRate > 0 && ' + '}
                              {product.sctRate > 0 && `ÖTV %${product.sctRate}`}
                            </small>
                          )}
                        </div>
                      </td>
                      <td className="text-center">
                        <div className="d-flex flex-column align-items-center gap-1">
                          <span className="fw-bold">
                            <i className="fas fa-cubes me-1"></i>
                            {product.totalStock ?? getTotalStockQuantity(product)}
                          </span>
                          {getLowStockCount(product) > 0 && (
                            <span className="badge bg-warning text-dark">
                              {getLowStockCount(product)} Düşük
                            </span>
                          )}
                          {desi > 0 && (
                            <small className="text-muted">
                              Desi: {desi.toFixed(2)}
                            </small>
                          )}
                          {shippingCost > 0 && (
                            <small className="text-muted">
                              Kargo: ₺{shippingCost.toFixed(2)}
                            </small>
                          )}
                        </div>
                      </td>
                      <td className="text-center">
                        <span className={`badge ${product.active === false ? 'bg-secondary' : 'bg-success'}`}>
                          {product.active === false ? 'Pasif' : 'Aktif'}
                        </span>
                      </td>
                      <td className="text-center">
                        <div className="d-flex justify-content-center">
                          <div className="btn-group" role="group">
                            <button
                              className="btn btn-outline-secondary"
                              onClick={() => handleEdit(product)}
                              title="Düzenle"
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-edit"></i>
                            </button>
                            <button
                              className={`btn ${(product.active === false) ? 'btn-outline-success' : 'btn-outline-warning'}`}
                              onClick={() => handleToggleActive(product.id, product.active === false ? false : true)}
                              title={(product.active === false) ? 'Aktifleştir' : 'Pasifleştir'}
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className={`fas ${(product.active === false) ? 'fa-play' : 'fa-pause'}`}></i>
                            </button>
                            <button
                              className="btn btn-outline-primary"
                              onClick={() => window.location.assign(`/desi?productId=${product.id}`)}
                              title="Desi Hesapla"
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-calculator"></i>
                            </button>
                            <button
                              className="btn btn-outline-danger"
                              onClick={() => handleDelete(product.id)}
                              title="Sil"
                              style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                            >
                              <i className="fas fa-trash"></i>
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View */}
          <div className="d-lg-none">
            <div className="d-flex flex-column gap-3">
              {filteredProducts.map((product) => {
                const calculateTotalPrice = () => {
                  const sctAmount = product.price * (product.sctRate || 0) / 100;
                  const priceWithSct = product.price + sctAmount;
                  const vatAmount = priceWithSct * (product.vatRate || 0) / 100;
                  return priceWithSct + vatAmount;
                };
                const totalPrice = calculateTotalPrice();
                const w = product.widthCm || 0;
                const l = product.lengthCm || 0;
                const h = product.heightCm || 0;
                const desi = (h * w * l) / 3000;
                const shippingCost = desi * (product.shippingRate || 0);
                const isSelected = selectedProducts.includes(product.id);

                return (
                  <div
                    key={product.id}
                    className={`card border shadow-sm ${isSelected ? 'border-primary bg-primary bg-opacity-10' : ''}`}
                  >
                    <div className="card-body p-3">
                      {/* Header with checkbox and name */}
                      <div className="d-flex justify-content-between align-items-start mb-3">
                        <div className="flex-grow-1">
                          <div className="d-flex align-items-center gap-2 mb-2">
                            <div className="form-check">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => toggleProductSelection(product.id)}
                                aria-label="Ürün seç"
                              />
                            </div>
                            <div>
                              <div className="fw-bold mb-1" style={{ fontSize: '1.05rem' }}>{product.name}</div>
                              <div className="d-flex flex-wrap gap-1 align-items-center">
                                <span className="badge bg-light text-dark border" style={{ fontSize: '0.75rem' }}>
                                  <i className="fas fa-barcode me-1"></i>
                                  {product.sku}
                                </span>
                                <span className={`badge ${product.active === false ? 'bg-secondary' : 'bg-success'}`}>
                                  {product.active === false ? 'Pasif' : 'Aktif'}
                                </span>
                              </div>
                            </div>
                          </div>
                          {product.description && (
                            <small className="text-muted d-block mb-2">
                              {product.description.length > 60 ? `${product.description.substring(0, 60)}...` : product.description}
                            </small>
                          )}
                          <div className="d-flex flex-wrap gap-1 align-items-center">
                            {product.category?.name && (
                              <span className="badge bg-info bg-opacity-10 text-info border border-info" style={{ fontSize: '0.7rem' }}>
                                <i className="fas fa-tag me-1"></i>
                                {product.category.parentName ? `${product.category.parentName.substring(0, 10)}${product.category.parentName.length > 10 ? '...' : ''} > ` : ''}
                                {product.category.name.length > 15 ? `${product.category.name.substring(0, 15)}...` : product.category.name}
                              </span>
                            )}
                            {product.brand?.name && (
                              <span className="badge bg-light text-dark border" style={{ fontSize: '0.7rem' }}>
                                <i className="fas fa-copyright me-1"></i>
                                {product.brand.name}
                              </span>
                            )}
                            {product.color?.name && (
                              <span className="badge bg-light text-dark border" style={{ fontSize: '0.7rem' }}>
                                <i className="fas fa-palette me-1"></i>
                                {product.color.name}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Info Grid */}
                      <div className="row g-2 mb-3">
                        <div className="col-6">
                          <div className="text-center p-2 bg-light rounded border">
                            <div className="small text-muted mb-1">
                              <i className="fas fa-tag me-1"></i>
                              Fiyat
                            </div>
                            <div className="fw-bold text-success" style={{ fontSize: '1.1rem' }}>
                              ₺{totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                            </div>
                            {showDetailedPrice && (product.vatRate > 0 || product.sctRate > 0) && (
                              <small className="text-muted d-block mt-1">
                                Ana: ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                              </small>
                            )}
                          </div>
                        </div>
                        <div className="col-6">
                          <div className="text-center p-2 bg-light rounded border">
                            <div className="small text-muted mb-1">
                              <i className="fas fa-cubes me-1"></i>
                              Stok
                            </div>
                            <div className="fw-bold" style={{ fontSize: '1.1rem' }}>
                              {product.totalStock ?? getTotalStockQuantity(product)}
                            </div>
                            {getLowStockCount(product) > 0 && (
                              <span className="badge bg-warning text-dark mt-1" style={{ fontSize: '0.65rem' }}>
                                {getLowStockCount(product)} Düşük
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Additional Info */}
                      {(desi > 0 || shippingCost > 0) && (
                        <div className="row g-2 mb-3">
                          {desi > 0 && (
                            <div className="col-6">
                              <div className="text-center p-2 bg-light rounded border">
                                <div className="small text-muted mb-1">Desi</div>
                                <div className="fw-semibold small">{desi.toFixed(2)}</div>
                              </div>
                            </div>
                          )}
                          {shippingCost > 0 && (
                            <div className="col-6">
                              <div className="text-center p-2 bg-light rounded border">
                                <div className="small text-muted mb-1">Kargo</div>
                                <div className="fw-semibold small">₺{shippingCost.toFixed(2)}</div>
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      {/* Actions */}
                      <div className="d-flex flex-wrap gap-2">
                        <button
                          className="btn btn-sm btn-outline-secondary flex-fill"
                          onClick={() => handleEdit(product)}
                        >
                          <i className="fas fa-edit me-1"></i>
                          Düzenle
                        </button>
                        <button
                          className={`btn btn-sm ${(product.active === false) ? 'btn-outline-success' : 'btn-outline-warning'} flex-fill`}
                          onClick={() => handleToggleActive(product.id, product.active === false ? false : true)}
                        >
                          <i className={`fas ${(product.active === false) ? 'fa-play' : 'fa-pause'} me-1`}></i>
                          {(product.active === false) ? 'Aktif' : 'Pasif'}
                        </button>
                        <button
                          className="btn btn-sm btn-outline-primary"
                          onClick={() => window.location.assign(`/desi?productId=${product.id}`)}
                          title="Desi Hesapla"
                        >
                          <i className="fas fa-calculator"></i>
                        </button>
                        <button
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => handleDelete(product.id)}
                          title="Sil"
                        >
                          <i className="fas fa-trash"></i>
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {filteredProducts.length === 0 && (
        <div className="text-center py-5">
          <i className="fas fa-box fa-3x text-muted mb-3"></i>
          <h4 className="text-muted">
            {products.length === 0 ? 'Henüz ürün bulunmuyor' : 'Arama kriterlerine uygun ürün bulunamadı'}
          </h4>
          <p className="text-muted">
            {products.length === 0
              ? 'İlk ürünü oluşturmak için "Yeni Ürün" butonuna tıklayın.'
              : 'Farklı arama terimleri deneyin.'}
          </p>
        </div>
      )}

      {/* Product Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  {editingProduct ? 'Ürün Düzenle' : 'Yeni Ürün'}
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <ProductForm
                  product={editingProduct}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
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

      <ConfirmModal
        show={errorModal.show}
        title={errorModal.title || 'Hata'}
        message={errorModal.message}
        icon="exclamation-triangle"
        confirmVariant="danger"
        confirmText="Tamam"
        cancelText={null}
        onConfirm={() => setErrorModal({ show: false, title: '', message: '' })}
        onCancel={() => setErrorModal({ show: false, title: '', message: '' })}
      />

      {/* Bulk Price Modal */}
      {showBulkModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Toplu Fiyat Güncelle</h5>
                <button type="button" className="btn-close" onClick={() => setShowBulkModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-info">
                  Bu işlem mevcut filtrelere göre uygulanır. Etkilenecek ürün: <strong>{filteredProducts.length}</strong>
                </div>
                <div className="row g-3">
                  <div className="col-12">
                    <label className="form-label">İşlem Türü</label>
                    <div className="input-group">
                      <select className="form-select" value={bulkMode} onChange={(e) => setBulkMode(e.target.value)}>
                        <option value="PERCENTAGE">Yüzde</option>
                        <option value="AMOUNT">Tutar</option>
                      </select>
                      <select className="form-select" value={bulkDirection} onChange={(e) => setBulkDirection(e.target.value)}>
                        <option value="INCREASE">Arttır</option>
                        <option value="DECREASE">Azalt</option>
                      </select>
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className="form-control"
                        placeholder={bulkMode === 'PERCENTAGE' ? 'Örn: 10 ( % )' : 'Örn: 50.00 (₺)'}
                        value={bulkValue}
                        onChange={(e) => setBulkValue(e.target.value)}
                      />
                      {bulkMode === 'PERCENTAGE' && (
                        <span className="input-group-text">%</span>
                      )}
                    </div>
                    <div className="form-text">Negatif değer girilmez; azaltma için yönü seçin.</div>
                  </div>
                  <div className="col-12 form-check">
                    <input className="form-check-input" type="checkbox" id="bulkOnlyActive" checked={bulkOnlyActive} onChange={(e) => setBulkOnlyActive(e.target.checked)} />
                    <label className="form-check-label" htmlFor="bulkOnlyActive">Sadece aktif ürünler</label>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowBulkModal(false)}>İptal</button>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={async () => {
                    const val = parseFloat(bulkValue);
                    if (!bulkValue || isNaN(val) || val <= 0) {
                      setErrorModal({
                        show: true,
                        title: 'Geçersiz Değer',
                        message: 'Lütfen geçerli bir değer girin.'
                      });
                      return;
                    }
                    try {
                      const request = {
                        mode: bulkMode,
                        direction: bulkDirection,
                        value: val,
                        onlyActive: bulkOnlyActive,
                        categoryId: selectedSubcategory ? Number(selectedSubcategory) : null,
                        brandId: selectedBrand || null,
                        colorId: selectedColor || null
                      };
                      const res = await axios.put('/api/products/bulk-price', request);
                      const affected = res.data?.affected ?? 0;
                      const toast = document.createElement('div');
                      toast.className = 'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show';
                      toast.setAttribute('role', 'alert');
                      toast.innerHTML = `<div class="d-flex"><div class="toast-body">${affected} ürün güncellendi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
                      document.body.appendChild(toast);
                      setTimeout(() => { try { document.body.removeChild(toast); } catch {} }, 3000);
                      setShowBulkModal(false);
                      setBulkValue('');
                      await fetchProducts();
                    } catch (error) {
                      const errorData = error?.response?.data;
                      const message = errorData?.message || errorData?.error || (typeof errorData === 'string' ? errorData : 'Toplu fiyat güncellemesi sırasında beklenmeyen bir hata oluştu.');
                      setErrorModal({
                        show: true,
                        title: 'Toplu Fiyat Güncelleme Hatası',
                        message
                      });
                    }
                  }}
                >
                  Uygula
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Products;
