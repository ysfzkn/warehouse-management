import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ProductForm from '../components/ProductForm';
import SearchableSelect from '../components/SearchableSelect';

const Products = () => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedBrand, setSelectedBrand] = useState(null);
  const [selectedColor, setSelectedColor] = useState(null);

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  useEffect(() => {
    const filteredProducts = products.filter(product => {
      const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                           product.sku.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = !selectedCategory || product.category.id.toString() === selectedCategory;
      const matchesBrand = !selectedBrand || product.brand?.id === selectedBrand;
      const matchesColor = !selectedColor || product.color?.id === selectedColor;
      return matchesSearch && matchesCategory && matchesBrand && matchesColor;
    });
    setFilteredProducts(filteredProducts);
  }, [products, searchTerm, selectedCategory]);
  
  useEffect(() => {
    const filteredProducts = products.filter(product => {
      const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                           product.sku.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = !selectedCategory || product.category.id.toString() === selectedCategory;
      const matchesBrand = !selectedBrand || product.brand?.id === selectedBrand;
      const matchesColor = !selectedColor || product.color?.id === selectedColor;
      return matchesSearch && matchesCategory && matchesBrand && matchesColor;
    });
    setFilteredProducts(filteredProducts);
  }, [selectedBrand, selectedColor]);

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

  const fetchCategories = async () => {
    try {
      const response = await axios.get('/api/categories');
      setCategories(response.data);
    } catch (error) {
      console.error('Error fetching categories:', error);
    }
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
    if (window.confirm('Bu ürünü silmek istediğinizden emin misiniz?')) {
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
      alert('Durum değiştirilirken hata oluştu: ' + error.response?.data);
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
        <button className="btn btn-primary" onClick={handleCreate}>
          <i className="fas fa-plus me-2"></i>
          Yeni Ürün
        </button>
      </div>

      {/* Filters */}
      <div className="row mb-4">
        <div className="col-md-6">
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
        <div className="col-md-6">
          <select
            className="form-select"
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
          >
            <option value="">Tüm Kategoriler</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
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
            onChange={(id) => setSelectedBrand(id)}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
          />
        </div>
        <div className="col-md-6">
          <SearchableSelect
            label="Renk Filtresi"
            value={selectedColor}
            onChange={(id) => setSelectedColor(id)}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
          />
        </div>
      </div>

      {/* Products Grid */}
      <div className="row">
        {filteredProducts.map((product) => (
          <div key={product.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <h5 className="card-title">{product.name}</h5>
                  <span className={`badge ${product.active === false ? 'bg-secondary' : 'bg-success'}`}>
                    {product.active === false ? 'Pasif' : 'Aktif'}
                  </span>
                </div>

                <p className="card-text">
                  <strong>Stok Kodu:</strong> {product.sku}
                </p>

                <p className="card-text">
                  <strong>Kategori:</strong> {product.category?.name}
                </p>

                <p className="card-text">
                  <strong>Fiyat:</strong> ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                </p>
                <p className="card-text">
                  <strong>Desi:</strong> {(() => {
                    const w = product.widthCm || 0; const l = product.lengthCm || 0; const h = product.heightCm || 0;
                    const desi = (h * w * l) / 3000;
                    return desi ? desi.toFixed(2) : '-';
                  })()}
                </p>
                <p className="card-text">
                  <strong>Kargo Ücreti:</strong> {(() => {
                    const w = product.widthCm || 0; const l = product.lengthCm || 0; const h = product.heightCm || 0;
                    const desi = (h * w * l) / 3000;
                    const rate = product.shippingRate || 0;
                    const total = desi * rate;
                    return isNaN(total) || total === 0 ? '-' : `₺${total.toFixed(2)}`;
                  })()}
                </p>

                {product.description && (
                  <p className="card-text text-muted small">
                    {product.description.length > 100
                      ? product.description.substring(0, 100) + '...'
                      : product.description}
                  </p>
                )}

                <div className="row mt-3">
                  <div className="col-6">
                    <span className="fw-bold">
                      <i className="fas fa-cubes me-1"></i>
                      Toplam Stok: {product.totalStock ?? getTotalStockQuantity(product)}
                    </span>
                  </div>
                  <div className="col-6">
                    {getLowStockCount(product) > 0 && (
                      <span className="badge bg-warning text-dark">
                        {getLowStockCount(product)} Düşük Stok
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="card-footer">
                <div className="btn-group w-100" role="group">
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => handleEdit(product)}
                  >
                    <i className="fas fa-edit me-1"></i>
                    Düzenle
                  </button>
                  <button
                    className={`btn btn-sm ${(product.active === false) ? 'btn-outline-success' : 'btn-outline-warning'}`}
                    onClick={() => handleToggleActive(product.id, product.active === false ? false : true)}
                  >
                    <i className={`fas ${(product.active === false) ? 'fa-play' : 'fa-pause'} me-1`}></i>
                    {(product.active === false) ? 'Aktifleştir' : 'Pasifleştir'}
                  </button>
                  <button
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => handleDelete(product.id)}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Sil
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
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
                  categories={categories}
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

export default Products;
