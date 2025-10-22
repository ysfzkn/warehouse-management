import React, { useState, useEffect } from 'react';
import axios from 'axios';
import WarehouseForm from '../components/WarehouseForm';
import StockModal from '../components/StockModal';
import FilterChips from '../components/FilterChips';
import SearchableSelect from '../components/SearchableSelect';

const Warehouses = () => {
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingWarehouse, setEditingWarehouse] = useState(null);
  const [showStockModal, setShowStockModal] = useState(false);
  const [selectedWarehouse, setSelectedWarehouse] = useState(null);
  const [warehouseTotals, setWarehouseTotals] = useState({});
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    fetchWarehouses();
  }, []);

  const fetchWarehouses = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/warehouses');
      const list = response.data || [];
      setWarehouses(list);
      try {
        const totals = await Promise.all(
          list.map(async (w) => {
            try {
              const r = await axios.get(`/api/stocks/warehouse/${w.id}/total-quantity`);
              return { id: w.id, total: typeof r.data === 'number' ? r.data : 0 };
            } catch {
              return { id: w.id, total: 0 };
            }
          })
        );
        const map = totals.reduce((acc, t) => { acc[t.id] = t.total; return acc; }, {});
        setWarehouseTotals(map);
      } catch {}
    } catch (error) {
      console.error('Error fetching warehouses:', error);
      setError('Depolar yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

  const filteredWarehouses = React.useMemo(() => {
    const term = (searchTerm || '').toLowerCase();
    if (!term) return warehouses;
    return warehouses.filter(w =>
      (w.name || '').toLowerCase().includes(term) ||
      (w.location || '').toLowerCase().includes(term) ||
      (w.manager || '').toLowerCase().includes(term)
    );
  }, [warehouses, searchTerm]);

  const handleCreate = () => {
    setEditingWarehouse(null);
    setShowForm(true);
  };

  const handleEdit = (warehouse) => {
    setEditingWarehouse(warehouse);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Bu depoyu silmek istediğinizden emin misiniz?')) {
      try {
        await axios.delete(`/api/warehouses/${id}`);
        fetchWarehouses();
      } catch (error) {
        alert('Depo silinirken hata oluştu: ' + error.response?.data);
      }
    }
  };

  const handleToggleActive = async (id, active) => {
    try {
      if (active) {
        await axios.put(`/api/warehouses/${id}/deactivate`);
      } else {
        await axios.put(`/api/warehouses/${id}/activate`);
      }
      fetchWarehouses();
    } catch (error) {
      alert('Durum değiştirilirken hata oluştu: ' + error.response?.data);
    }
  };

  const handleViewStock = (warehouse) => {
    setSelectedWarehouse(warehouse);
    setShowStockModal(true);
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    setEditingWarehouse(null);
    fetchWarehouses();
  };

  const getTotalStockQuantity = (warehouse) => {
    return warehouse.stocks ? warehouse.stocks.reduce((total, stock) => total + stock.quantity, 0) : 0;
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
        <h2>Depolar</h2>
        <button className="btn btn-primary" onClick={handleCreate}>
          <i className="fas fa-plus me-2"></i>
          Yeni Depo
        </button>
      </div>

      {/* Filters */}
      <div className="row mb-3">
        <div className="col-md-8">
          <div className="input-group">
            <span className="input-group-text"><i className="fas fa-search"></i></span>
            <input type="text" className="form-control" placeholder="Depo adı, konum veya yetkili ara..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
          </div>
        </div>
      </div>
      {(searchTerm) && (
        <FilterChips
          className="mb-3"
          chips={[{ icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') }]}
          onClearAll={() => setSearchTerm('')}
        />
      )}

      <div className="row">
        {filteredWarehouses.map((warehouse) => (
          <div key={warehouse.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <h5 className="card-title">{warehouse.name}</h5>
                  <span className={`badge ${warehouse.active === false ? 'bg-secondary' : 'bg-success'}`}>
                    {warehouse.active === false ? 'Pasif' : 'Aktif'}
                  </span>
                </div>

                <p className="card-text text-muted">
                  <i className="fas fa-map-marker-alt me-2"></i>
                  {warehouse.location}
                </p>

                {warehouse.manager && (
                  <p className="card-text">
                    <i className="fas fa-user me-2"></i>
                    {warehouse.manager}
                  </p>
                )}

                {warehouse.phone && (
                  <p className="card-text">
                    <i className="fas fa-phone me-2"></i>
                    {warehouse.phone}
                  </p>
                )}

                {warehouse.capacitySqm && (
                  <p className="card-text">
                    <i className="fas fa-ruler-combined me-2"></i>
                    {warehouse.capacitySqm} m²
                  </p>
                )}

                <div className="d-flex justify-content-between align-items-center mt-3">
                  <span className="fw-bold">
                    <i className="fas fa-cubes me-1"></i>
                    Toplam Stok: {warehouseTotals[warehouse.id] ?? getTotalStockQuantity(warehouse)}
                  </span>
                </div>
              </div>

              <div className="card-footer">
                <div className="btn-group w-100" role="group">
                  <button
                    className="btn btn-outline-primary btn-sm"
                    onClick={() => handleViewStock(warehouse)}
                  >
                    <i className="fas fa-eye me-1"></i>
                    Stok Görüntüle
                  </button>
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => handleEdit(warehouse)}
                  >
                    <i className="fas fa-edit me-1"></i>
                    Düzenle
                  </button>
                  <button
                    className={`btn btn-sm ${(warehouse.active === false) ? 'btn-outline-success' : 'btn-outline-warning'}`}
                    onClick={() => handleToggleActive(warehouse.id, warehouse.active === false ? false : true)}
                  >
                    <i className={`fas ${(warehouse.active === false) ? 'fa-play' : 'fa-pause'} me-1`}></i>
                    {(warehouse.active === false) ? 'Aktifleştir' : 'Pasifleştir'}
                  </button>
                  <button
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => handleDelete(warehouse.id)}
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

      {warehouses.length === 0 && (
        <div className="text-center py-5">
          <i className="fas fa-building fa-3x text-muted mb-3"></i>
          <h4 className="text-muted">Henüz depo bulunmuyor</h4>
          <p className="text-muted">İlk depoyu oluşturmak için "Yeni Depo" butonuna tıklayın.</p>
        </div>
      )}

      {/* Warehouse Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  {editingWarehouse ? 'Depo Düzenle' : 'Yeni Depo'}
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <WarehouseForm
                  warehouse={editingWarehouse}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stock Modal */}
      {showStockModal && selectedWarehouse && (
        <StockModal
          warehouse={selectedWarehouse}
          onClose={() => setShowStockModal(false)}
        />
      )}
    </div>
  );
};

export default Warehouses;
