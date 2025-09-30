import React, { useState, useEffect } from 'react';
import axios from 'axios';
import WarehouseForm from '../components/WarehouseForm';
import StockModal from '../components/StockModal';

const Warehouses = () => {
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingWarehouse, setEditingWarehouse] = useState(null);
  const [showStockModal, setShowStockModal] = useState(false);
  const [selectedWarehouse, setSelectedWarehouse] = useState(null);

  useEffect(() => {
    fetchWarehouses();
  }, []);

  const fetchWarehouses = async () => {
    try {
      setLoading(true);
      const response = await axios.get('/api/warehouses');
      setWarehouses(response.data);
    } catch (error) {
      console.error('Error fetching warehouses:', error);
      setError('Depolar yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  };

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

  const handleToggleActive = async (id, isActive) => {
    try {
      if (isActive) {
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

      <div className="row">
        {warehouses.map((warehouse) => (
          <div key={warehouse.id} className="col-md-6 col-lg-4 mb-4">
            <div className="card h-100">
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start mb-2">
                  <h5 className="card-title">{warehouse.name}</h5>
                  <span className={`badge ${warehouse.isActive === false ? 'bg-secondary' : 'bg-success'}`}>
                    {warehouse.isActive === false ? 'Pasif' : 'Aktif'}
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
                    Toplam Stok: {getTotalStockQuantity(warehouse)}
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
                    className={`btn btn-sm ${(warehouse.isActive === false) ? 'btn-outline-success' : 'btn-outline-warning'}`}
                    onClick={() => handleToggleActive(warehouse.id, warehouse.isActive === false ? false : true)}
                  >
                    <i className={`fas ${(warehouse.isActive === false) ? 'fa-play' : 'fa-pause'} me-1`}></i>
                    {(warehouse.isActive === false) ? 'Aktifleştir' : 'Pasifleştir'}
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
