import React, { useState, useEffect, useMemo, useCallback } from 'react';
import axios from 'axios';
import StockForm from '../components/StockForm';
import StockAdjustmentModal from '../components/StockAdjustmentModal';
import SearchableSelect from '../components/SearchableSelect';
import FilterChips from '../components/FilterChips';

const Stock = () => {
  const [stocks, setStocks] = useState([]);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [showAdjustmentModal, setShowAdjustmentModal] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [filter, setFilter] = useState('all'); // all, low-stock, out-of-stock
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');

  const fetchAllData = useCallback(async () => {
    try {
      setLoading(true);
      const [stocksRes, productsRes, warehousesRes] = await Promise.all([
        axios.get('/api/stocks', { params: { brandId, colorId, warehouseId: selectedWarehouseId || undefined } }),
        axios.get('/api/products'),
        axios.get('/api/warehouses')
      ]);

      setStocks(stocksRes.data);
      setProducts(productsRes.data);
      setWarehouses(warehousesRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
      setError('Veriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [brandId, colorId, selectedWarehouseId]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  // Initialize filters from query params
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const f = params.get('filter');
    const b = params.get('brandId');
    const c = params.get('colorId');
    const w = params.get('warehouseId');
    if (f === 'low-stock' || f === 'out-of-stock' || f === 'all') setFilter(f);
    if (b) setBrandId(Number(b));
    if (c) setColorId(Number(c));
    if (w) setSelectedWarehouseId(Number(w));
  }, []);

  const filteredStocks = useMemo(() => {
    let filtered = stocks;

    // status filter
    switch (filter) {
      case 'low-stock':
        filtered = filtered.filter(stock => stock.quantity <= stock.minStockLevel && stock.quantity > 0);
        break;
      case 'out-of-stock':
        filtered = filtered.filter(stock => stock.quantity === 0);
        break;
      default:
        break;
    }

    // text search
    const q = (searchTerm || '').toLowerCase();
    if (q) {
      filtered = filtered.filter(s =>
        (s.product?.name || '').toLowerCase().includes(q) ||
        (s.product?.sku || '').toLowerCase().includes(q) ||
        (s.warehouse?.name || '').toLowerCase().includes(q)
      );
    }

    // Sort by warehouse name and product name
    return [...filtered].sort((a, b) => {
      const warehouseCompare = (a.warehouse?.name || '').localeCompare(b.warehouse?.name || '');
      if (warehouseCompare !== 0) return warehouseCompare;
      return (a.product?.name || '').localeCompare(b.product?.name || '');
    });
  }, [stocks, filter, searchTerm]);

  const FiltersBar = () => (
    <>
      <div className="row mb-2 align-items-end">
        <div className="col-md-6">
          <div className="input-group">
            <span className="input-group-text"><i className="fas fa-search"></i></span>
            <input
              type="text"
              className="form-control"
              placeholder="Ürün adı, SKU veya depo ara..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
        <div className="col-md-3">
          <SearchableSelect
            label="Marka"
            value={brandId}
            onChange={(id, opt) => { setBrandId(id); setBrandOpt(opt || null); }}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
          />
        </div>
        <div className="col-md-3">
          <SearchableSelect
            label="Renk"
            value={colorId}
            onChange={(id, opt) => { setColorId(id); setColorOpt(opt || null); }}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
          />
        </div>
      </div>
      <FilterChips
        className="mb-3"
        chips={[
          searchTerm ? { icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') } : null,
          selectedWarehouseId ? { icon: 'fas fa-warehouse', label: `Depo: ${getWarehouseById(selectedWarehouseId)?.name || selectedWarehouseId}`, onClear: () => setSelectedWarehouseId(null) } : null,
          brandId ? { icon: 'fas fa-copyright', label: `Marka: ${brandOpt?.name || brandId}`, onClear: () => { setBrandId(null); setBrandOpt(null); } } : null,
          colorId ? { icon: 'fas fa-palette', label: `Renk: ${colorOpt?.name || colorId}`, onClear: () => { setColorId(null); setColorOpt(null); } } : null,
        ].filter(Boolean)}
        onClearAll={() => { setSearchTerm(''); setSelectedWarehouseId(null); setBrandId(null); setColorId(null); setBrandOpt(null); setColorOpt(null); }}
      />
    </>
  );

  const handleCreateStock = () => {
    setSelectedStock(null);
    setShowForm(true);
  };

  const handleStockAdjustment = (stock) => {
    setSelectedStock(stock);
    setShowAdjustmentModal(true);
  };

  const handleDeleteStock = async (id) => {
    if (window.confirm('Bu stok kaydını silmek istediğinizden emin misiniz?')) {
      try {
        await axios.delete(`/api/stocks/${id}`);
        fetchAllData();
      } catch (error) {
        alert('Stok silinirken hata oluştu: ' + error.response?.data);
      }
    }
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    fetchAllData();
  };

  const handleAdjustmentSuccess = () => {
    setShowAdjustmentModal(false);
    setSelectedStock(null);
    fetchAllData();
  };

  const getProductById = (id) => {
    return products.find(p => p.id === id);
  };

  const getWarehouseById = (id) => {
    return warehouses.find(w => w.id === id);
  };


  const getStockStatus = (stock) => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0);
    if (available <= 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (available <= stock.minStockLevel) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  // filteredStocks now computed via useMemo above

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
        <h2>Stok Yönetimi</h2>
        <button className="btn btn-primary" onClick={handleCreateStock}>
          <i className="fas fa-plus me-2"></i>
          Yeni Stok Kaydı
        </button>
      </div>

      {/* Filter Tabs */}
      <div className="mb-4">
        <div className="btn-group" role="group">
          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="all"
            value="all"
            checked={filter === 'all'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-primary" htmlFor="all">
            Tüm Stok ({stocks.length})
          </label>

          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="low-stock"
            value="low-stock"
            checked={filter === 'low-stock'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-warning" htmlFor="low-stock">
            Düşük Stok ({stocks.filter(s => s.quantity <= s.minStockLevel && s.quantity > 0).length})
          </label>

          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="out-of-stock"
            value="out-of-stock"
            checked={filter === 'out-of-stock'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-danger" htmlFor="out-of-stock">
            Stok Dışı ({stocks.filter(s => s.quantity === 0).length})
          </label>
        </div>
      </div>

      {/* Stock Table */}
      <div className="card">
        <div className="card-body">
          <div className="table-responsive">
            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th>Depo</th>
                  <th>Ürün</th>
                  <th>Stok Kodu</th>
                  <th>Miktar</th>
                  <th>Kullanılabilir</th>
                  <th>Emanet</th>
                  <th>Min. Stok</th>
                  <th>Durum</th>
                  <th>Son Güncelleme</th>
                  <th>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {filteredStocks.map((stock) => {
                  const product = getProductById(stock.product.id);
                  const warehouse = getWarehouseById(stock.warehouse.id);
                  const stockStatus = getStockStatus(stock);

                  return (
                    <tr key={stock.id}>
                      <td>{warehouse?.name}</td>
                      <td>{product?.name}</td>
                      <td>{product?.sku}</td>
                      <td>
                        <span className="fw-bold">{stock.quantity}</span>
                      </td>
                      <td>
                        <span className={stock.availableQuantity < stock.minStockLevel ? 'text-danger' : 'text-success'}>
                          {stock.availableQuantity}
                        </span>
                      </td>
                      <td>{stock.consignedQuantity || 0}</td>
                      <td>{stock.minStockLevel}</td>
                      <td>
                        <span className={`badge bg-${stockStatus.class}`}>
                          {stockStatus.label}
                        </span>
                      </td>
                      <td>
                        {new Date(stock.lastUpdated).toLocaleDateString('tr-TR')}
                      </td>
                      <td>
                        <div className="btn-group" role="group">
                          <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => handleStockAdjustment(stock)}
                            title="Stok Ayarla"
                          >
                            <i className="fas fa-edit"></i>
                          </button>
                          <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => handleDeleteStock(stock.id)}
                            title="Stok Kaydını Sil"
                          >
                            <i className="fas fa-trash"></i>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {filteredStocks.length === 0 && (
            <div className="text-center py-4">
              <i className="fas fa-cubes fa-3x text-muted mb-3"></i>
              <h5 className="text-muted">
                {filter === 'all'
                  ? 'Henüz stok kaydı bulunmuyor'
                  : `Bu kategoride stok kaydı bulunmuyor`
                }
              </h5>
              <p className="text-muted">
                {filter === 'all'
                  ? 'İlk stok kaydını oluşturmak için "Yeni Stok Kaydı" butonuna tıklayın.'
                  : 'Farklı filtre seçeneği deneyin.'
                }
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Stock Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yeni Stok Kaydı</h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <StockForm
                  products={products}
                  warehouses={warehouses}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stock Adjustment Modal */}
      {showAdjustmentModal && selectedStock && (
        <StockAdjustmentModal
          stock={selectedStock}
          onSuccess={handleAdjustmentSuccess}
          onClose={() => setShowAdjustmentModal(false)}
        />
      )}
    </div>
  );
};

export default Stock;
