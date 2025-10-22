import React, { useState, useEffect, useMemo, useCallback } from 'react';
import axios from 'axios';
import StockForm from '../components/StockForm';
import StockAdjustmentModal from '../components/StockAdjustmentModal';
import StockTransferModal from '../components/StockTransferModal';
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
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [showTransferHistory, setShowTransferHistory] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [transfers, setTransfers] = useState([]);
  const [filter, setFilter] = useState('all'); // all, low-stock, out-of-stock
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [selectedWarehouseOpt, setSelectedWarehouseOpt] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [showReserved, setShowReserved] = useState(false);
  const [showConsigned, setShowConsigned] = useState(false);
  const [transferStatusFilter, setTransferStatusFilter] = useState('ALL');

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

    // reserved/consigned filters
    if (showReserved) {
      filtered = filtered.filter(s => (s.reservedQuantity || 0) > 0);
    }
    if (showConsigned) {
      filtered = filtered.filter(s => (s.consignedQuantity || 0) > 0);
    }

    // Sort by warehouse name and product name
    return [...filtered].sort((a, b) => {
      const warehouseCompare = (a.warehouse?.name || '').localeCompare(b.warehouse?.name || '');
      if (warehouseCompare !== 0) return warehouseCompare;
      return (a.product?.name || '').localeCompare(b.product?.name || '');
    });
  }, [stocks, filter, searchTerm, showReserved, showConsigned]);

  const FiltersBar = () => (
    <>
      <div className="row mb-2 align-items-end">
        <div className="col-md-3">
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
            label="Depo"
            value={selectedWarehouseId}
            onChange={(id, opt) => { setSelectedWarehouseId(id); setSelectedWarehouseOpt(opt || null); }}
            searchEndpoint="/api/warehouses"
            placeholder="Depo ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
            renderOption={(w) => w.name}
          />
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

      {/* Additional filters */}
      <div className="row mb-2">
        <div className="col-md-12">
          <div className="form-check form-check-inline">
            <input
              className="form-check-input"
              type="checkbox"
              id="showReserved"
              checked={showReserved}
              onChange={(e) => setShowReserved(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="showReserved">
              Sadece Rezerve Olanlar
            </label>
          </div>
          <div className="form-check form-check-inline">
            <input
              className="form-check-input"
              type="checkbox"
              id="showConsigned"
              checked={showConsigned}
              onChange={(e) => setShowConsigned(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="showConsigned">
              Sadece Emanet Olanlar
            </label>
          </div>
        </div>
      </div>

      <FilterChips
        className="mb-3"
        chips={[
          searchTerm ? { icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') } : null,
          selectedWarehouseId ? { icon: 'fas fa-warehouse', label: `Depo: ${selectedWarehouseOpt?.name || getWarehouseById(selectedWarehouseId)?.name || selectedWarehouseId}`, onClear: () => { setSelectedWarehouseId(null); setSelectedWarehouseOpt(null); } } : null,
          brandId ? { icon: 'fas fa-copyright', label: `Marka: ${brandOpt?.name || brandId}`, onClear: () => { setBrandId(null); setBrandOpt(null); } } : null,
          colorId ? { icon: 'fas fa-palette', label: `Renk: ${colorOpt?.name || colorId}`, onClear: () => { setColorId(null); setColorOpt(null); } } : null,
          showReserved ? { icon: 'fas fa-lock', label: 'Rezerve Olanlar', onClear: () => setShowReserved(false) } : null,
          showConsigned ? { icon: 'fas fa-handshake', label: 'Emanet Olanlar', onClear: () => setShowConsigned(false) } : null,
        ].filter(Boolean)}
        onClearAll={() => {
          setSearchTerm('');
          setSelectedWarehouseId(null);
          setSelectedWarehouseOpt(null);
          setBrandId(null);
          setColorId(null);
          setBrandOpt(null);
          setColorOpt(null);
          setShowReserved(false);
          setShowConsigned(false);
        }}
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

  const handleStockTransfer = (stock) => {
    setSelectedStock(stock);
    setShowTransferModal(true);
  };

  const handleTransferSuccess = () => {
    setShowTransferModal(false);
    setSelectedStock(null);
    fetchAllData();
    if (showTransferHistory) {
      fetchTransfers();
    }
  };

  const handleShowTransferHistory = async () => {
    setShowTransferHistory(!showTransferHistory);
    if (!showTransferHistory) {
      await fetchTransfers();
    }
  };

  const fetchTransfers = async () => {
    try {
      const response = await axios.get('/api/stock-transfers');
      setTransfers(response.data);
    } catch (error) {
      console.error('Error fetching transfers:', error);
    }
  };

  const handleTransferStatusChange = async (transferId, action) => {
    try {
      await axios.post(`/api/stock-transfers/${transferId}/${action}`);
      fetchTransfers();
      fetchAllData();
    } catch (error) {
      alert(`Transfer ${action} işlemi sırasında hata: ` + (error.response?.data || error.message));
    }
  };

  const handleDeleteTransfer = async (transferId) => {
    if (window.confirm('Bu transfer kaydını silmek istediğinizden emin misiniz?')) {
      try {
        await axios.delete(`/api/stock-transfers/${transferId}`);
        fetchTransfers();
      } catch (error) {
        alert('Transfer silinirken hata: ' + (error.response?.data || error.message));
      }
    }
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
        <div className="btn-group">
          <button className="btn btn-success" onClick={handleShowTransferHistory}>
            <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
            {showTransferHistory ? 'Stok Listesi' : 'Transfer Geçmişi'}
          </button>
          <button className="btn btn-primary" onClick={handleCreateStock}>
            <i className="fas fa-plus me-2"></i>
            Yeni Stok Kaydı
          </button>
        </div>
      </div>

      {/* Filters Bar - Only show when not in transfer history mode */}
      {!showTransferHistory && <FiltersBar />}

      {/* Filter Tabs */}
      {!showTransferHistory && (
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
      )}

      {/* Stock Table */}
      {!showTransferHistory && (
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
                            className="btn btn-sm btn-outline-success"
                            onClick={() => handleStockTransfer(stock)}
                            title="Transfer Yap"
                          >
                            <i className="fas fa-exchange-alt"></i>
                          </button>
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
      )}

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

      {/* Stock Transfer Modal */}
      {showTransferModal && (
        <StockTransferModal
          stock={selectedStock}
          onSuccess={handleTransferSuccess}
          onClose={() => {
            setShowTransferModal(false);
            setSelectedStock(null);
          }}
        />
      )}

      {/* Transfer History Section */}
      {showTransferHistory && (
        <div className="mt-4">
          {/* Statistics Cards */}
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card border-warning shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'PENDING').length}</h3>
                  <p className="text-muted mb-0 small">Beklemede</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-info shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-truck fa-2x text-info mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'IN_TRANSIT').length}</h3>
                  <p className="text-muted mb-0 small">Yolda</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-success shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'COMPLETED').length}</h3>
                  <p className="text-muted mb-0 small">Tamamlandı</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-danger shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'CANCELLED').length}</h3>
                  <p className="text-muted mb-0 small">İptal Edildi</p>
                </div>
              </div>
            </div>
          </div>

          {/* Filter Buttons */}
          <div className="card mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div className="btn-group" role="group">
                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-all"
                    value="ALL"
                    checked={transferStatusFilter === 'ALL'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-secondary" htmlFor="status-all">
                    <i className="fas fa-list me-1"></i>
                    Tümü ({transfers.length})
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-pending"
                    value="PENDING"
                    checked={transferStatusFilter === 'PENDING'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-warning" htmlFor="status-pending">
                    <i className="fas fa-clock me-1"></i>
                    Beklemede
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-transit"
                    value="IN_TRANSIT"
                    checked={transferStatusFilter === 'IN_TRANSIT'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-info" htmlFor="status-transit">
                    <i className="fas fa-truck me-1"></i>
                    Yolda
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-completed"
                    value="COMPLETED"
                    checked={transferStatusFilter === 'COMPLETED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-success" htmlFor="status-completed">
                    <i className="fas fa-check-circle me-1"></i>
                    Tamamlandı
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-cancelled"
                    value="CANCELLED"
                    checked={transferStatusFilter === 'CANCELLED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-danger" htmlFor="status-cancelled">
                    <i className="fas fa-times-circle me-1"></i>
                    İptal
                  </label>
                </div>
                
                <div className="text-muted small">
                  <i className="fas fa-info-circle me-1"></i>
                  Toplam {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).length} transfer
                </div>
              </div>
            </div>
          </div>

          <div className="card shadow-sm">
            <div className="card-header bg-gradient text-white" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
              <div className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <i className="fas fa-history me-2"></i>
                  Transfer Geçmişi
                </h5>
                <span className="badge bg-white text-dark">
                  {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).length} kayıt
                </span>
              </div>
            </div>
            <div className="card-body p-0">
              {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).length === 0 ? (
                <div className="text-center py-5">
                  <i className="fas fa-inbox fa-4x text-muted mb-3"></i>
                  <h5 className="text-muted">
                    {transferStatusFilter === 'ALL' 
                      ? 'Henüz transfer kaydı bulunmuyor' 
                      : `${transferStatusFilter === 'PENDING' ? 'Beklemede' : transferStatusFilter === 'IN_TRANSIT' ? 'Yolda' : transferStatusFilter === 'COMPLETED' ? 'Tamamlanmış' : 'İptal edilmiş'} transfer bulunmuyor`
                    }
                  </h5>
                  <p className="text-muted">
                    {transferStatusFilter === 'ALL' && 'İlk transferi oluşturmak için stok listesinden "Transfer Yap" butonuna tıklayın.'}
                  </p>
                </div>
              ) : (
              <div className="table-responsive">
                <table className="table table-hover mb-0">
                  <thead className="table-light">
                    <tr>
                      <th className="text-center" style={{width: '80px'}}>
                        <i className="fas fa-hashtag me-1"></i>
                        No
                      </th>
                      <th style={{width: '140px'}}>
                        <i className="fas fa-calendar me-1"></i>
                        Tarih
                      </th>
                      <th>
                        <i className="fas fa-box me-1"></i>
                        Ürün
                      </th>
                      <th>
                        <i className="fas fa-warehouse text-danger me-1"></i>
                        Kaynak
                      </th>
                      <th>
                        <i className="fas fa-warehouse text-success me-1"></i>
                        Hedef
                      </th>
                      <th className="text-center" style={{width: '80px'}}>
                        <i className="fas fa-boxes me-1"></i>
                        Miktar
                      </th>
                      <th>
                        <i className="fas fa-user me-1"></i>
                        Şoför
                      </th>
                      <th style={{width: '100px'}}>
                        <i className="fas fa-car me-1"></i>
                        Plaka
                      </th>
                      <th className="text-center" style={{width: '120px'}}>
                        <i className="fas fa-info-circle me-1"></i>
                        Durum
                      </th>
                      <th className="text-center" style={{width: '180px'}}>
                        <i className="fas fa-cog me-1"></i>
                        İşlemler
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).map((transfer) => {
                      const statusConfig = {
                        PENDING: { label: 'Beklemede', class: 'warning', icon: 'clock' },
                        IN_TRANSIT: { label: 'Yolda', class: 'info', icon: 'truck' },
                        COMPLETED: { label: 'Tamamlandı', class: 'success', icon: 'check-circle' },
                        CANCELLED: { label: 'İptal Edildi', class: 'danger', icon: 'times-circle' }
                      };
                      const status = statusConfig[transfer.status] || statusConfig.PENDING;

                      return (
                        <tr key={transfer.id} className="align-middle">
                          <td className="text-center">
                            <span className="badge bg-dark fs-6">#{transfer.id}</span>
                          </td>
                          <td>
                            <div className="small">
                              <div className="fw-bold">
                                {new Date(transfer.transferDate).toLocaleDateString('tr-TR')}
                              </div>
                              <div className="text-muted">
                                {new Date(transfer.transferDate).toLocaleTimeString('tr-TR', {
                                  hour: '2-digit',
                                  minute: '2-digit'
                                })}
                              </div>
                            </div>
                          </td>
                          <td>
                            <div>
                              <div className="fw-bold">{transfer.product?.name}</div>
                              <small className="text-muted">SKU: {transfer.product?.sku}</small>
                            </div>
                          </td>
                          <td>
                            <div className="d-flex align-items-center">
                              <div className="bg-danger bg-opacity-10 rounded-circle p-2 me-2">
                                <i className="fas fa-warehouse text-danger"></i>
                              </div>
                              <div className="small">
                                <div className="fw-bold">{transfer.sourceWarehouse?.name}</div>
                                <small className="text-muted">{transfer.sourceWarehouse?.location}</small>
                              </div>
                            </div>
                          </td>
                          <td>
                            <div className="d-flex align-items-center">
                              <div className="bg-success bg-opacity-10 rounded-circle p-2 me-2">
                                <i className="fas fa-warehouse text-success"></i>
                              </div>
                              <div className="small">
                                <div className="fw-bold">{transfer.destinationWarehouse?.name}</div>
                                <small className="text-muted">{transfer.destinationWarehouse?.location}</small>
                              </div>
                            </div>
                          </td>
                          <td className="text-center">
                            <span className="badge bg-primary rounded-pill fs-6">{transfer.quantity}</span>
                          </td>
                          <td>
                            <div className="small">
                              <div className="fw-bold">{transfer.driverName}</div>
                              <div className="text-muted">
                                <i className="fas fa-phone me-1"></i>
                                {transfer.driverPhone}
                              </div>
                            </div>
                          </td>
                          <td>
                            <span className="badge bg-secondary">{transfer.vehiclePlate}</span>
                          </td>
                          <td className="text-center">
                            <span className={`badge bg-${status.class} w-100 py-2`}>
                              <i className={`fas fa-${status.icon} me-1`}></i>
                              {status.label}
                            </span>
                            {transfer.completedDate && (
                              <small className="d-block text-success mt-1">
                                <i className="fas fa-check me-1"></i>
                                {new Date(transfer.completedDate).toLocaleDateString('tr-TR')}
                              </small>
                            )}
                            {transfer.cancelledDate && (
                              <small className="d-block text-danger mt-1">
                                <i className="fas fa-times me-1"></i>
                                {new Date(transfer.cancelledDate).toLocaleDateString('tr-TR')}
                              </small>
                            )}
                          </td>
                          <td className="text-center">
                            <div className="d-flex flex-column gap-1">
                              {transfer.status === 'PENDING' && (
                                <>
                                  <button
                                    className="btn btn-sm btn-info w-100"
                                    onClick={() => {
                                      if (window.confirm('Transfer yola çıkartılacak. Onaylıyor musunuz?')) {
                                        handleTransferStatusChange(transfer.id, 'start');
                                      }
                                    }}
                                  >
                                    <i className="fas fa-truck me-1"></i>
                                    Yola Çıkar
                                  </button>
                                  <button
                                    className="btn btn-sm btn-success w-100"
                                    onClick={() => {
                                      if (window.confirm('Transfer direkt tamamlanacak. Onaylıyor musunuz?')) {
                                        handleTransferStatusChange(transfer.id, 'complete');
                                      }
                                    }}
                                  >
                                    <i className="fas fa-check me-1"></i>
                                    Tamamla
                                  </button>
                                  <button
                                    className="btn btn-sm btn-danger w-100"
                                    onClick={() => {
                                      if (window.confirm('Transfer iptal edilecek. Emin misiniz?')) {
                                        handleTransferStatusChange(transfer.id, 'cancel');
                                      }
                                    }}
                                  >
                                    <i className="fas fa-ban me-1"></i>
                                    İptal
                                  </button>
                                </>
                              )}
                              {transfer.status === 'IN_TRANSIT' && (
                                <>
                                  <button
                                    className="btn btn-sm btn-success w-100"
                                    onClick={() => {
                                      if (window.confirm('Transfer tamamlanacak ve stok taşınacak. Onaylıyor musunuz?')) {
                                        handleTransferStatusChange(transfer.id, 'complete');
                                      }
                                    }}
                                  >
                                    <i className="fas fa-check-double me-1"></i>
                                    Tamamla
                                  </button>
                                  <button
                                    className="btn btn-sm btn-warning w-100"
                                    onClick={() => {
                                      if (window.confirm('Transfer iptal edilecek ve rezervasyon kaldırılacak. Emin misiniz?')) {
                                        handleTransferStatusChange(transfer.id, 'cancel');
                                      }
                                    }}
                                  >
                                    <i className="fas fa-ban me-1"></i>
                                    İptal Et
                                  </button>
                                </>
                              )}
                              {(transfer.status === 'CANCELLED' || transfer.status === 'PENDING') && (
                                <button
                                  className="btn btn-sm btn-outline-danger w-100"
                                  onClick={() => handleDeleteTransfer(transfer.id)}
                                >
                                  <i className="fas fa-trash me-1"></i>
                                  Sil
                                </button>
                              )}
                              {transfer.notes && (
                                <button
                                  className="btn btn-sm btn-outline-secondary w-100"
                                  onClick={() => alert(`📝 Transfer Notları:\n\n${transfer.notes}`)}
                                >
                                  <i className="fas fa-sticky-note me-1"></i>
                                  Notlar
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Stock;
