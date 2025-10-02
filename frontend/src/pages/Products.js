import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ProductForm from '../components/ProductForm';

const Products = () => {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');

  useEffect(() => {
    fetchProducts();
    fetchCategories();
  }, []);

  useEffect(() => {
    const filteredProducts = products.filter(product => {
      const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                           product.sku.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesCategory = !selectedCategory || product.category.id.toString() === selectedCategory;
      return matchesSearch && matchesCategory;
    });
    setFilteredProducts(filteredProducts);
  }, [products, searchTerm, selectedCategory]);

  const [filteredProducts, setFilteredProducts] = useState([]);

  const fetchProducts = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/products');
      setProducts(response.data);
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
        alert('Ürün silinirken hata oluştu: ' + error.response?.data);
      }
    }
  };

  const handleToggleActive = async (id, isActive) => {
    try {
      if (isActive) {
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
              placeholder="Ürün adı veya SKU ara..."
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

      {/* Products Grid */}
      <div className="row">
        {filteredProducts.map((product) => (
          <div key={product.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <h5 className="card-title">{product.name}</h5>
                  <span className={`badge ${product.isActive === false ? 'bg-secondary' : 'bg-success'}`}>
                    {product.isActive === false ? 'Pasif' : 'Aktif'}
                  </span>
                </div>

                <p className="card-text">
                  <strong>SKU:</strong> {product.sku}
                </p>

                <p className="card-text">
                  <strong>Kategori:</strong> {product.category?.name}
                </p>

                <p className="card-text">
                  <strong>Fiyat:</strong> ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
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
                      Toplam Stok: {getTotalStockQuantity(product)}
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
                    className={`btn btn-sm ${(product.isActive === false) ? 'btn-outline-success' : 'btn-outline-warning'}`}
                    onClick={() => handleToggleActive(product.id, product.isActive === false ? false : true)}
                  >
                    <i className={`fas ${(product.isActive === false) ? 'fa-play' : 'fa-pause'} me-1`}></i>
                    {(product.isActive === false) ? 'Aktifleştir' : 'Pasifleştir'}
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
