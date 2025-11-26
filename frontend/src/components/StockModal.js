import React, { useState, useEffect, useCallback, useMemo } from 'react';
import axios from 'axios';
import PaginationControls from './PaginationControls';

const StockModal = ({ warehouse, onClose }) => {
  const [stocks, setStocks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 12;

  const fetchStocks = useCallback(async () => {
    if (!warehouse?.id) return;
    try {
      setLoading(true);
      const response = await axios.get(`/api/stocks/warehouse/${warehouse.id}`);
      let fetchedStocksRaw = response.data;
      if (Array.isArray(fetchedStocksRaw?.content)) {
        fetchedStocksRaw = fetchedStocksRaw.content;
      }
      let fetchedStocks = Array.isArray(fetchedStocksRaw) ? fetchedStocksRaw : [];

      const stocksMissingCategory = fetchedStocks.filter(
        (stock) => stock?.product && !stock.product.category
      );

      if (stocksMissingCategory.length > 0) {
        try {
          const productsResponse = await axios.get('/api/products');
          const productsPayload = Array.isArray(productsResponse.data?.content)
            ? productsResponse.data.content
            : Array.isArray(productsResponse.data)
              ? productsResponse.data
              : [];
          const productMap = new Map(productsPayload.map((product) => [product.id, product]));

          fetchedStocks = fetchedStocks.map((stock) => {
            if (stock?.product && !stock.product.category) {
              const productDetail = productMap.get(stock.product.id);
              if (productDetail?.category) {
                return {
                  ...stock,
                  product: {
                    ...stock.product,
                    category: productDetail.category
                  }
                };
              }
            }
            return stock;
          });
        } catch (categoryError) {
          console.error('Error fetching product categories for stocks:', categoryError);
        }
      }

      setStocks(fetchedStocks);
      setSearchTerm('');
      setPage(0);
    } catch (error) {
      console.error('Error fetching stocks:', error);
      setError('Stok bilgileri yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [warehouse?.id]);

  useEffect(() => {
    if (warehouse) {
      fetchStocks();
    }
  }, [warehouse, fetchStocks]);

  const getStockStatus = (stock) => {
    if (stock.quantity === 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (stock.quantity <= stock.minStockLevel) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  const getAvailableQuantity = (stock) => {
    if (typeof stock.availableQuantity === 'number') {
      return stock.availableQuantity;
    }
    const reserved = stock.reservedQuantity || 0;
    const consigned = stock.consignedQuantity || 0;
    return Math.max(0, (stock.quantity || 0) - reserved - consigned);
  };

  const filteredStocks = useMemo(() => {
    const term = searchTerm.trim().toLocaleLowerCase('tr-TR');
    if (!term) return stocks;
    return stocks.filter((stock) => {
      const fields = [
        stock.product?.name,
        stock.product?.sku,
        stock.product?.category?.name,
        stock.product?.category?.parentName,
        stock.product?.brand?.name
      ]
        .filter(Boolean)
        .map((field) => field.toLocaleLowerCase('tr-TR'));
      return fields.some((field) => field.includes(term));
    });
  }, [stocks, searchTerm]);

  const totalPages = Math.max(1, Math.ceil(filteredStocks.length / PAGE_SIZE));
  const paginatedStocks = useMemo(
    () => filteredStocks.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE),
    [filteredStocks, page]
  );

  useEffect(() => {
    setPage(0);
  }, [searchTerm, stocks]);

  if (loading) {
    return (
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-xl">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body text-center">
              <div className="spinner-border" role="status">
                <span className="visually-hidden">Yükleniyor...</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-xl">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>
            <div className="modal-body">
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-xl">
        <div className="modal-content">
          <style>{`
            .stock-mobile-card .stat-tile {
              min-height: 96px;
              border-radius: 12px;
              padding: 0.75rem;
              border: 1px solid rgba(15,23,42,0.08);
              background: #f8fafc;
              display: flex;
              flex-direction: column;
              justify-content: center;
            }
          `}</style>
          <div className="modal-header">
            <h5 className="modal-title">{warehouse?.name} - Stok Bilgileri</h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {stocks.length === 0 ? (
              <div className="text-center py-4">
                <i className="fas fa-cubes fa-3x text-muted mb-3"></i>
                <h5 className="text-muted">Bu depoda stok bulunmuyor</h5>
              </div>
            ) : (
              <div>
                <div className="d-flex flex-column flex-md-row justify-content-between align-items-md-center gap-2 mb-3">
                  <div className="flex-grow-1">
                    <div className="input-group input-group-sm">
                      <span className="input-group-text bg-transparent border-end-0">
                        <i className="fas fa-search text-muted"></i>
                      </span>
                      <input
                        type="text"
                        className="form-control border-start-0"
                        placeholder="Ürün adı, SKU veya kategori ara..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                      />
                      {searchTerm && (
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => setSearchTerm('')}
                        >
                          Temizle
                        </button>
                      )}
                    </div>
                    <small className="text-muted d-block mt-1">
                      {filteredStocks.length} kayıt listeleniyor
                    </small>
                  </div>
                </div>
                <div className="d-none d-xl-block table-responsive">
                  <table className="table table-striped table-hover">
                    <thead>
                      <tr>
                        <th>Ürün</th>
                        <th>Stok Kodu</th>
                        <th>Kategori</th>
                        <th>Miktar</th>
                        <th>Kullanılabilir</th>
                        <th>Emanet</th>
                        <th>Min. Stok</th>
                        <th>Durum</th>
                        <th>Not</th>
                        <th>Son Güncelleme</th>
                      </tr>
                    </thead>
                    <tbody>
                      {paginatedStocks.map((stock) => {
                        const stockStatus = getStockStatus(stock);
                        const available = getAvailableQuantity(stock);
                        return (
                          <tr key={stock.id}>
                            <td>{stock.product.name}</td>
                            <td>{stock.product.sku}</td>
                            <td>{stock.product.category?.name || '-'}</td>
                            <td>
                              <span className="fw-bold">{stock.quantity}</span>
                            </td>
                            <td>
                              <span className={available < stock.minStockLevel ? 'text-danger' : 'text-success'}>
                                {available}
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
                              {stock.additionNote ? (
                                <small className="text-muted text-wrap d-block" style={{ maxWidth: '220px' }}>
                                  <i className="fas fa-sticky-note me-1"></i>
                                  {stock.additionNote}
                                </small>
                              ) : (
                                <span className="text-muted">-</span>
                              )}
                            </td>
                            <td>
                              {new Date(stock.lastUpdated).toLocaleDateString('tr-TR')}
                            </td>
                          </tr>
                        );
                      })}
                      {paginatedStocks.length === 0 && (
                        <tr>
                          <td colSpan="10" className="text-center py-4 text-muted">
                            Arama kriterine uygun stok bulunamadı.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>

                <div className="d-xl-none d-flex flex-column gap-3">
                  {paginatedStocks.length === 0 ? (
                    <div className="text-center text-muted py-4">
                      Arama kriterine uygun stok bulunamadı.
                    </div>
                  ) : (
                    paginatedStocks.map((stock) => {
                      const stockStatus = getStockStatus(stock);
                      const available = getAvailableQuantity(stock);
                      return (
                        <div key={stock.id} className="stock-mobile-card card border shadow-sm">
                          <div className="card-body p-3">
                            <div className="d-flex justify-content-between align-items-start mb-2">
                              <div>
                                <div className="fw-bold">{stock.product.name}</div>
                                <div className="text-muted small">
                                  <i className="fas fa-barcode me-1"></i>
                                  {stock.product.sku}
                                </div>
                                {stock.product.category?.name && (
                                  <small className="badge bg-info bg-opacity-10 text-info border border-info mt-1">
                                    <i className="fas fa-tag me-1"></i>
                                    {stock.product.category?.parentName
                                      ? `${stock.product.category.parentName} > `
                                      : ''}
                                    {stock.product.category.name}
                                  </small>
                                )}
                              </div>
                              <span className={`badge bg-${stockStatus.class}`}>
                                {stockStatus.label}
                              </span>
                            </div>
                            <div className="row g-2 mb-2">
                              <div className="col-6 d-flex">
                                <div className="stat-tile w-100 text-center text-primary bg-primary bg-opacity-10">
                                  <div className="small text-muted text-uppercase mb-1">Miktar</div>
                                  <div className="fw-bold fs-5 text-primary">{stock.quantity}</div>
                                </div>
                              </div>
                              <div className="col-6 d-flex">
                                <div className={`stat-tile w-100 text-center ${available > 0 ? 'text-success bg-success bg-opacity-10' : 'text-danger bg-danger bg-opacity-10'}`}>
                                  <div className="small text-muted text-uppercase mb-1">Kullanılabilir</div>
                                  <div className="fw-bold fs-5">{available}</div>
                                </div>
                              </div>
                            </div>
                            <div className="row g-2 mb-2">
                              <div className="col-6 d-flex">
                                <div className="stat-tile w-100 text-center bg-light">
                                  <div className="small text-muted text-uppercase mb-1">Rezerve</div>
                                  <div className="fw-semibold">{stock.reservedQuantity || 0}</div>
                                </div>
                              </div>
                              <div className="col-6 d-flex">
                                <div className="stat-tile w-100 text-center bg-light">
                                  <div className="small text-muted text-uppercase mb-1">Emanet</div>
                                  <div className="fw-semibold text-muted">{stock.consignedQuantity || 0}</div>
                                </div>
                              </div>
                            </div>
                            <div className="row g-2 mb-2">
                              <div className="col-6 d-flex">
                                <div className="stat-tile w-100 text-center bg-light">
                                  <div className="small text-muted text-uppercase mb-1">Min. Stok</div>
                                  <div className="fw-semibold">{stock.minStockLevel}</div>
                                </div>
                              </div>
                              <div className="col-6 d-flex">
                                <div className="stat-tile w-100 text-center bg-light">
                                  <div className="small text-muted text-uppercase mb-1">Güncelleme</div>
                                  <div className="fw-semibold text-muted">
                                    {new Date(stock.lastUpdated).toLocaleDateString('tr-TR')}
                                  </div>
                                </div>
                              </div>
                            </div>
                            {stock.additionNote && (
                              <div className="alert alert-light border text-muted small mb-0">
                                <i className="fas fa-sticky-note me-1"></i>
                                {stock.additionNote}
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            )}
          </div>
          <div className="modal-footer flex-column flex-md-row justify-content-between align-items-center gap-2">
            {stocks.length > 0 && filteredStocks.length > 0 ? (
              <>
                <small className="text-muted">
                  Gösterilen: {Math.min(filteredStocks.length, (page + 1) * PAGE_SIZE)} / {filteredStocks.length}
                </small>
                <PaginationControls
                  page={page}
                  totalPages={totalPages}
                  onPageChange={setPage}
                />
              </>
            ) : (
              <div />
            )}
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Kapat
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StockModal;
