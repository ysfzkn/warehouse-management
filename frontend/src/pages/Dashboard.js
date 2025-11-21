import React, { useState, useEffect, useCallback, useMemo } from 'react';
import axios from 'axios';
import { Bar, Pie } from 'react-chartjs-2';
import SearchableSelect from '../components/SearchableSelect';
import FilterChips from '../components/FilterChips';
import { useNavigate } from 'react-router-dom';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  LineElement,
  PointElement,
} from 'chart.js';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  LineElement,
  PointElement
);

const Dashboard = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    totalWarehouses: 0,
    totalProducts: 0,
    totalCategories: 0,
    lowStockItems: 0,
    outOfStockItems: 0,
    totalStockValue: 0,
    totalStockQuantity: 0,
    totalReserved: 0,
    totalConsigned: 0,
    activeWarehouses: 0,
    totalBrands: 0,
    totalColors: 0,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [allStocks, setAllStocks] = useState([]);
  const [lowStocks, setLowStocks] = useState([]);
  const [outStocks, setOutStocks] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [selectedWarehouseOpt, setSelectedWarehouseOpt] = useState(null);
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);
  const [warehouseStats, setWarehouseStats] = useState([]);

  const fetchDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      const [warehousesRes, productsRes, categoriesRes, lowStockRes, outOfStockRes, allStocksRes, brandsRes, colorsRes] = await Promise.all([
        axios.get('/api/warehouses'),
        axios.get('/api/products'),
        axios.get('/api/categories'),
        axios.get('/api/stocks/low-stock'),
        axios.get('/api/stocks/out-of-stock'),
        axios.get('/api/stocks', { params: { size: 500, page: 0 } }),
        axios.get('/api/brands'),
        axios.get('/api/colors')
      ]);

      const warehousesData = warehousesRes.data;
      const products = productsRes.data;
      const categories = categoriesRes.data;
      const lowStockItems = Array.isArray(lowStockRes.data) ? lowStockRes.data : (lowStockRes.data?.content || []);
      const outOfStockItems = Array.isArray(outOfStockRes.data) ? outOfStockRes.data : (outOfStockRes.data?.content || []);
      const allStocksRaw = allStocksRes.data;
      const allStocksData = Array.isArray(allStocksRaw?.content) ? allStocksRaw.content : (Array.isArray(allStocksRaw) ? allStocksRaw : []);
      const brands = brandsRes.data || [];
      const colors = colorsRes.data || [];
      
      setLowStocks(lowStockItems);
      setOutStocks(outOfStockItems);
      setAllStocks(allStocksData);

      // Calculate warehouse statistics
      const warehouseStatsMap = {};
      allStocksData.forEach(stock => {
        const whId = stock.warehouse?.id;
        if (!whId) return;
        if (!warehouseStatsMap[whId]) {
          warehouseStatsMap[whId] = {
            id: whId,
            name: stock.warehouse?.name || '',
            location: stock.warehouse?.location || '',
            totalQuantity: 0,
            totalValue: 0,
            productCount: 0,
            reserved: 0,
            consigned: 0,
          };
        }
        warehouseStatsMap[whId].totalQuantity += stock.quantity || 0;
        warehouseStatsMap[whId].reserved += stock.reservedQuantity || 0;
        warehouseStatsMap[whId].consigned += stock.consignedQuantity || 0;
        warehouseStatsMap[whId].productCount += 1;
        
        const product = products.find(p => p.id === stock.product?.id);
        if (product) {
          warehouseStatsMap[whId].totalValue += (product.price || 0) * (stock.quantity || 0);
        }
      });
      setWarehouseStats(Object.values(warehouseStatsMap));

      // Calculate totals
      let totalStockValue = 0;
      let totalStockQuantity = 0;
      let totalReserved = 0;
      let totalConsigned = 0;

      allStocksData.forEach(stock => {
        totalStockQuantity += stock.quantity || 0;
        totalReserved += stock.reservedQuantity || 0;
        totalConsigned += stock.consignedQuantity || 0;
        
        const product = products.find(p => p.id === stock.product?.id);
        if (product) {
          totalStockValue += (product.price || 0) * (stock.quantity || 0);
        }
      });

      // Handle active/isActive field - could be boolean, 1/0, or "1"/"0"
      const activeWarehouses = warehousesData.filter(w => {
        const activeValue = w.active !== undefined ? w.active : w.isActive;
        return activeValue === true || activeValue === 1 || activeValue === '1' || activeValue === 'true';
      }).length;

      console.log('Warehouse active status check:', {
        total: warehousesData.length,
        active: activeWarehouses,
        sample: warehousesData.slice(0, 3).map(w => ({ 
          name: w.name, 
          active: w.active, 
          isActive: w.isActive, 
          activeType: typeof w.active,
          isActiveType: typeof w.isActive 
        }))
      });

      setStats({
        totalWarehouses: warehousesData.length,
        totalProducts: products.length,
        totalCategories: categories.length,
        lowStockItems: lowStockItems.length,
        outOfStockItems: outOfStockItems.length,
        totalStockValue,
        totalStockQuantity,
        totalReserved,
        totalConsigned,
        activeWarehouses,
        totalBrands: brands.length,
        totalColors: colors.length,
      });

    } catch (error) {
      console.error('Error fetching dashboard data:', error);
      setError('Dashboard verileri yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  const filterStockList = useCallback((list) => {
    const q = (searchTerm || '').toLowerCase();
    return (list || []).filter(s => {
      const matchesSearch = !q ||
        (s.product?.name || '').toLowerCase().includes(q) ||
        (s.product?.sku || '').toLowerCase().includes(q) ||
        (s.warehouse?.name || '').toLowerCase().includes(q) ||
        (s.warehouse?.location || '').toLowerCase().includes(q);
      const matchesBrand = !brandId || (s.product?.brand?.id === brandId);
      const matchesColor = !colorId || (s.product?.color?.id === colorId);
      const matchesWarehouse = !selectedWarehouseId || (s.warehouse?.id === selectedWarehouseId);
      return matchesSearch && matchesBrand && matchesColor && matchesWarehouse;
    });
  }, [searchTerm, brandId, colorId, selectedWarehouseId]);

  const filteredLow = useMemo(() => filterStockList(lowStocks), [lowStocks, filterStockList]);
  const filteredOut = useMemo(() => filterStockList(outStocks), [outStocks, filterStockList]);
  const filteredAllStocks = useMemo(() => filterStockList(allStocks), [allStocks, filterStockList]);

  // Filtered warehouse stats
  const filteredWarehouseStats = useMemo(() => {
    if (!selectedWarehouseId) return warehouseStats;
    return warehouseStats.filter(w => w.id === selectedWarehouseId);
  }, [warehouseStats, selectedWarehouseId]);

  // Computed stats based on filters
  const computedStats = useMemo(() => {
    if (!selectedWarehouseId && !brandId && !colorId && !searchTerm) {
      return stats;
    }
    
    const filtered = filteredAllStocks;
    return {
      ...stats,
      lowStockItems: filteredLow.length,
      outOfStockItems: filteredOut.length,
      totalStockQuantity: filtered.reduce((sum, s) => sum + (s.quantity || 0), 0),
      totalReserved: filtered.reduce((sum, s) => sum + (s.reservedQuantity || 0), 0),
      totalConsigned: filtered.reduce((sum, s) => sum + (s.consignedQuantity || 0), 0),
    };
  }, [stats, filteredAllStocks, filteredLow, filteredOut, selectedWarehouseId, brandId, colorId, searchTerm]);

  const barChartData = {
    labels: ['Aktif Depolar', 'Toplam Ürünler', 'Kategoriler', 'Düşük Stok', 'Stok Dışı'],
    datasets: [
      {
        label: 'Sayısal Veriler',
        data: [
          stats.totalWarehouses,
          stats.totalProducts,
          stats.totalCategories,
          stats.lowStockItems,
          stats.outOfStockItems
        ],
        backgroundColor: [
          'rgba(75, 192, 192, 0.6)',
          'rgba(255, 99, 132, 0.6)',
          'rgba(255, 205, 86, 0.6)',
          'rgba(255, 159, 64, 0.6)',
          'rgba(201, 203, 207, 0.6)'
        ],
        borderColor: [
          'rgb(75, 192, 192)',
          'rgb(255, 99, 132)',
          'rgb(255, 205, 86)',
          'rgb(255, 159, 64)',
          'rgb(201, 203, 207)'
        ],
        borderWidth: 1
      }
    ]
  };

  const pieChartData = {
    labels: ['Normal Stok', 'Düşük Stok', 'Stok Dışı'],
    datasets: [
      {
        data: [
          Math.max(0, stats.totalProducts - stats.lowStockItems - stats.outOfStockItems),
          stats.lowStockItems,
          stats.outOfStockItems
        ],
        backgroundColor: [
          'rgba(75, 192, 192, 0.6)',
          'rgba(255, 159, 64, 0.6)',
          'rgba(255, 99, 132, 0.6)'
        ],
        borderColor: [
          'rgb(75, 192, 192)',
          'rgb(255, 159, 64)',
          'rgb(255, 99, 132)'
        ],
        borderWidth: 1
      }
    ]
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
      },
    },
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{minHeight: '400px'}}>
        <div className="text-center">
          <div className="spinner-border text-primary" style={{width: '3rem', height: '3rem'}} role="status">
            <span className="visually-hidden">Yükleniyor...</span>
          </div>
          <p className="mt-3 text-muted">Dashboard verileri yükleniyor...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger d-flex align-items-center" role="alert">
        <i className="fas fa-exclamation-triangle fa-2x me-3"></i>
        <div>{error}</div>
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">
            <i className="fas fa-chart-line me-2 text-primary"></i>
            Panel
          </h2>
          <p className="text-muted mb-0">
            <i className="fas fa-clock me-1"></i>
            Gerçek zamanlı depo yönetimi ve stok takibi
          </p>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-primary" onClick={() => fetchDashboardData()}>
            <i className="fas fa-sync-alt me-2"></i>
            Yenile
          </button>
          <div className="btn-group">
            <button 
              className="btn btn-success"
              onClick={() => navigate('/stock')}
              title="Stok sayfasına git"
            >
              <i className="fas fa-boxes me-2"></i>
              <span className="d-none d-md-inline">Stok Yönetimi</span>
              <span className="d-inline d-md-none">Stok</span>
            </button>
            <button 
              className="btn btn-info"
              onClick={() => navigate('/products')}
              title="Ürün sayfasına git"
            >
              <i className="fas fa-box me-2"></i>
              <span className="d-none d-md-inline">Ürünler</span>
              <span className="d-inline d-md-none">Ürün</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main Stats Cards */}
      <div className="row g-3 mb-4">
        <div className="col-xl-3 col-md-6">
          <div className="card border-0 shadow-sm h-100" style={{borderLeft: '4px solid #0d6efd'}}>
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-start">
                <div className="flex-grow-1">
                  <p className="text-muted mb-1 small">Toplam Stok Miktarı</p>
                  <h3 className="mb-0 fw-bold">{computedStats.totalStockQuantity.toLocaleString('tr-TR')}</h3>
                  <small className="text-success">
                    <i className="fas fa-cubes me-1"></i>
                    Tüm depolarda
                  </small>
                </div>
                <div className="bg-primary bg-opacity-10 rounded-circle p-3">
                  <i className="fas fa-boxes text-primary fa-lg"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-xl-3 col-md-6">
          <div className="card border-0 shadow-sm h-100" style={{borderLeft: '4px solid #198754'}}>
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-start">
                <div className="flex-grow-1">
                  <p className="text-muted mb-1 small">Toplam Stok Değeri</p>
                  <h3 className="mb-0 fw-bold">{stats.totalStockValue.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} ₺</h3>
                  <small className="text-muted">
                    <i className="fas fa-chart-line me-1"></i>
                    Tahmini değer
                  </small>
                </div>
                <div className="bg-success bg-opacity-10 rounded-circle p-3">
                  <i className="fas fa-money-bill-wave text-success fa-lg"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-xl-3 col-md-6">
          <div className="card border-0 shadow-sm h-100" style={{borderLeft: '4px solid #ffc107'}}>
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-start">
                <div className="flex-grow-1">
                  <p className="text-muted mb-1 small">Düşük Stok</p>
                  <h3 className="mb-0 fw-bold text-warning">{computedStats.lowStockItems}</h3>
                  <small className="text-warning">
                    <i className="fas fa-exclamation-triangle me-1"></i>
                    İncelenmeli
                  </small>
                </div>
                <div className="bg-warning bg-opacity-10 rounded-circle p-3">
                  <i className="fas fa-box-open text-warning fa-lg"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-xl-3 col-md-6">
          <div className="card border-0 shadow-sm h-100" style={{borderLeft: '4px solid #dc3545'}}>
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-start">
                <div className="flex-grow-1">
                  <p className="text-muted mb-1 small">Stok Dışı</p>
                  <h3 className="mb-0 fw-bold text-danger">{computedStats.outOfStockItems}</h3>
                  <small className="text-danger">
                    <i className="fas fa-times-circle me-1"></i>
                    Acil tedarik
                  </small>
                </div>
                <div className="bg-danger bg-opacity-10 rounded-circle p-3">
                  <i className="fas fa-inbox text-danger fa-lg"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Secondary Stats Cards */}
      <div className="row g-3 mb-4">
        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-warehouse text-primary fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{stats.activeWarehouses}/{stats.totalWarehouses}</h4>
              <small className="text-muted">Aktif Depo</small>
            </div>
          </div>
        </div>

        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-box text-info fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{stats.totalProducts}</h4>
              <small className="text-muted">Ürün Çeşidi</small>
            </div>
          </div>
        </div>

        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-tags text-secondary fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{stats.totalCategories}</h4>
              <small className="text-muted">Kategori</small>
            </div>
          </div>
        </div>

        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-lock text-warning fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{computedStats.totalReserved.toLocaleString('tr-TR')}</h4>
              <small className="text-muted">Rezerve</small>
            </div>
          </div>
        </div>

        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-handshake text-info fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{computedStats.totalConsigned.toLocaleString('tr-TR')}</h4>
              <small className="text-muted">Emanet</small>
            </div>
          </div>
        </div>

        <div className="col-lg-2 col-md-4 col-sm-6">
          <div className="card border-0 shadow-sm text-center h-100">
            <div className="card-body py-3">
              <div className="mb-2">
                <i className="fas fa-copyright text-success fa-2x"></i>
              </div>
              <h4 className="mb-0 fw-bold">{stats.totalBrands}</h4>
              <small className="text-muted">Marka</small>
            </div>
          </div>
        </div>
      </div>

      {/* Advanced Search & Filters */}
      <div className="card border-0 shadow-sm mb-4" style={{position: 'relative', zIndex: 100}}>
        <div className="card-header bg-white border-bottom">
          <div className="d-flex justify-content-between align-items-center">
            <h5 className="mb-0">
              <i className="fas fa-filter me-2 text-primary"></i>
              Gelişmiş Arama ve Filtreleme
            </h5>
            {(searchTerm || brandId || colorId || selectedWarehouseId) && (
              <button 
                className="btn btn-sm btn-danger"
                onClick={() => {
                  setSearchTerm('');
                  setBrandId(null);
                  setColorId(null);
                  setBrandOpt(null);
                  setColorOpt(null);
                  setSelectedWarehouseId(null);
                  setSelectedWarehouseOpt(null);
                }}
                title="Tüm filtreleri temizle"
              >
                <i className="fas fa-broom me-2"></i>
                Filtreleri Temizle
              </button>
            )}
          </div>
        </div>
        <div className="card-body">
          <div className="row g-3 mb-3">
            {/* Main Search Bar */}
            <div className="col-12">
              <label className="form-label fw-semibold mb-2">
                <i className="fas fa-search me-2 text-primary"></i>
                Genel Arama
              </label>
              <div className="input-group input-group-lg">
                <span className="input-group-text bg-light">
                  <i className="fas fa-search text-muted"></i>
                </span>
                <input 
                  type="text" 
                  className="form-control" 
                  placeholder="Ürün adı, Stok Kodu, depo adı veya lokasyon bilgisi ile arama yapın..." 
                  value={searchTerm} 
                  onChange={(e) => setSearchTerm(e.target.value)} 
                />
                {searchTerm && (
                  <button 
                    className="btn btn-outline-danger"
                    type="button"
                    onClick={() => setSearchTerm('')}
                    title="Aramayı temizle"
                  >
                    <i className="fas fa-times-circle"></i>
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Filter Row */}
          <div className="row g-3">
            <div className="col-lg-3 col-md-6">
              <SearchableSelect
                label={<span className="fw-semibold"><i className="fas fa-warehouse me-2 text-primary"></i>Depo Filtresi</span>}
                value={selectedWarehouseId}
                onChange={(id, opt) => { 
                  setSelectedWarehouseId(id); 
                  setSelectedWarehouseOpt(opt || null); 
                }}
                searchEndpoint="/api/warehouses"
                placeholder="Tüm depolar..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
                renderOption={(w) => w.name}
              />
            </div>
            <div className="col-lg-3 col-md-6">
              <SearchableSelect
                label={<span className="fw-semibold"><i className="fas fa-copyright me-2 text-primary"></i>Marka Filtresi</span>}
                value={brandId}
                onChange={(id, opt) => { setBrandId(id); setBrandOpt(opt || null); }}
                searchEndpoint="/api/brands/search"
                placeholder="Tüm markalar..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
              />
            </div>
            <div className="col-lg-3 col-md-6">
              <SearchableSelect
                label={<span className="fw-semibold"><i className="fas fa-palette me-2 text-primary"></i>Renk Filtresi</span>}
                value={colorId}
                onChange={(id, opt) => { setColorId(id); setColorOpt(opt || null); }}
                searchEndpoint="/api/colors/search"
                placeholder="Tüm renkler..."
                allowClear={true}
                clearText="Temizle"
                wrapperClassName="mb-0"
              />
            </div>
            <div className="col-lg-3 col-md-6">
              <label className="form-label fw-semibold">
                <i className="fas fa-info-circle me-2 text-primary"></i>
                Filtreleme İpucu
              </label>
              <div className="alert alert-light border mb-0 py-2 px-3">
                <small className="text-muted d-block">
                  <i className="fas fa-lightbulb me-1 text-warning"></i>
                  Birden fazla filtre kombinlenebilir
                </small>
              </div>
            </div>
          </div>
          
          {/* Active Filters Display */}
          {(searchTerm || brandId || colorId || selectedWarehouseId) && (
            <div className="border-top pt-3">
              <FilterChips
                chips={[
                  searchTerm ? { 
                    icon: 'fas fa-search', 
                    label: `Arama: "${searchTerm}"`, 
                    onClear: () => setSearchTerm('') 
                  } : null,
                  selectedWarehouseId ? { 
                    icon: 'fas fa-warehouse', 
                    label: `Depo: ${selectedWarehouseOpt?.name || 'Seçili'}`, 
                    onClear: () => { 
                      setSelectedWarehouseId(null); 
                      setSelectedWarehouseOpt(null); 
                    } 
                  } : null,
                  brandId ? { 
                    icon: 'fas fa-copyright', 
                    label: `Marka: ${brandOpt?.name || brandId}`, 
                    onClear: () => { setBrandId(null); setBrandOpt(null); } 
                  } : null,
                  colorId ? { 
                    icon: 'fas fa-palette', 
                    label: `Renk: ${colorOpt?.name || colorId}`, 
                    onClear: () => { setColorId(null); setColorOpt(null); } 
                  } : null,
                ].filter(Boolean)}
                onClearAll={() => { 
                  setSearchTerm(''); 
                  setBrandId(null); 
                  setColorId(null); 
                  setBrandOpt(null); 
                  setColorOpt(null); 
                  setSelectedWarehouseId(null);
                  setSelectedWarehouseOpt(null);
                }}
              />
            </div>
          )}

          {/* Search Results Summary */}
          {(searchTerm || brandId || colorId || selectedWarehouseId) && (
            <div className="alert alert-info d-flex align-items-center mt-3 mb-0" role="alert">
              <i className="fas fa-info-circle fa-lg me-3"></i>
              <div>
                <strong>{filteredLow.length + filteredOut.length}</strong> kayıt filtreleme sonuçlarında bulundu
                {selectedWarehouseId && filteredWarehouseStats.length > 0 && (
                  <span className="ms-2">
                    ({filteredWarehouseStats[0].name} deposunda)
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Alert Cards */}
      <div className="row mb-4" style={{position: 'relative', zIndex: 1}}>
        <div className="col-md-6 mb-3">
          <div className="card border-warning">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title text-warning">
                    <i className="fas fa-exclamation-triangle me-2"></i>
                    Düşük Stok Uyarıları
                  </h5>
                  <h3 className="text-warning">{stats.lowStockItems}</h3>
                  <small className="text-muted">Dikkat edilmesi gereken ürünler</small>
                </div>
              </div>
              <div className="table-responsive mt-3" style={{ maxHeight: 240, overflowY: 'auto' }}>
                <table className="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Ürün</th>
                      <th>Depo</th>
                      <th>Miktar</th>
                      <th>Min</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredLow.slice(0, 10).map((s) => (
                      <tr key={s.id} style={{ cursor: 'pointer' }} onClick={() => {
                        const params = new URLSearchParams();
                        params.set('filter', 'low-stock');
                        if (s.product?.brand?.id) params.set('brandId', s.product.brand.id);
                        if (s.product?.color?.id) params.set('colorId', s.product.color.id);
                        navigate(`/stock?${params.toString()}`);
                      }}>
                        <td>{s.product?.name} <small className="text-muted">({s.product?.sku})</small></td>
                        <td style={{ textDecoration: 'underline' }} onClick={(e) => { e.stopPropagation();
                          if (s.warehouse?.id) {
                            const params = new URLSearchParams();
                            params.set('warehouseId', s.warehouse.id);
                            navigate(`/stock?${params.toString()}`);
                          }
                        }}>{s.warehouse?.name}</td>
                        <td>{s.quantity}</td>
                        <td>{s.minStockLevel}</td>
                      </tr>
                    ))}
                    {filteredLow.length === 0 && (
                      <tr><td colSpan={4} className="text-muted">Kayıt bulunamadı</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-6 mb-3">
          <div className="card border-danger">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title text-danger">
                    <i className="fas fa-times-circle me-2"></i>
                    Stok Dışı Ürünler
                  </h5>
                  <h3 className="text-danger">{stats.outOfStockItems}</h3>
                  <small className="text-muted">Acil tedarik edilmesi gereken ürünler</small>
                </div>
              </div>
              <div className="table-responsive mt-3" style={{ maxHeight: 240, overflowY: 'auto' }}>
                <table className="table table-sm align-middle">
                  <thead>
                    <tr>
                      <th>Ürün</th>
                      <th>Depo</th>
                      <th>Miktar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredOut.slice(0, 10).map((s) => (
                      <tr key={s.id} style={{ cursor: 'pointer' }} onClick={() => {
                        const params = new URLSearchParams();
                        params.set('filter', 'out-of-stock');
                        if (s.product?.brand?.id) params.set('brandId', s.product.brand.id);
                        if (s.product?.color?.id) params.set('colorId', s.product.color.id);
                        navigate(`/stock?${params.toString()}`);
                      }}>
                        <td>{s.product?.name} <small className="text-muted">({s.product?.sku})</small></td>
                        <td style={{ textDecoration: 'underline' }} onClick={(e) => { e.stopPropagation();
                          if (s.warehouse?.id) {
                            const params = new URLSearchParams();
                            params.set('warehouseId', s.warehouse.id);
                            navigate(`/stock?${params.toString()}`);
                          }
                        }}>{s.warehouse?.name}</td>
                        <td>{s.quantity}</td>
                      </tr>
                    ))}
                    {filteredOut.length === 0 && (
                      <tr><td colSpan={3} className="text-muted">Kayıt bulunamadı</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Warehouse Performance Table */}
      {filteredWarehouseStats.length > 0 && (
        <div className="card border-0 shadow-sm mb-4" style={{position: 'relative', zIndex: 1}}>
          <div className="card-header bg-white border-bottom">
            <div className="d-flex justify-content-between align-items-center">
              <h5 className="mb-0">
                <i className="fas fa-warehouse me-2 text-primary"></i>
                Depo Bazlı Performans Analizi
              </h5>
              <span className="badge bg-primary">{filteredWarehouseStats.length} Depo</span>
            </div>
          </div>
          <div className="card-body p-0">
            <div className="table-responsive">
              <table className="table table-hover mb-0 align-middle">
                <thead className="table-light">
                  <tr>
                    <th className="ps-4">
                      <i className="fas fa-warehouse me-2 text-primary"></i>
                      Depo Adı
                    </th>
                    <th>
                      <i className="fas fa-map-marker-alt me-2 text-secondary"></i>
                      Lokasyon
                    </th>
                    <th className="text-center">
                      <i className="fas fa-cubes me-2 text-info"></i>
                      Toplam Stok
                    </th>
                    <th className="text-center">
                      <i className="fas fa-lock me-2 text-warning"></i>
                      Rezerve
                    </th>
                    <th className="text-center">
                      <i className="fas fa-handshake me-2 text-info"></i>
                      Emanet
                    </th>
                    <th className="text-center">
                      <i className="fas fa-box me-2 text-success"></i>
                      Ürün Çeşidi
                    </th>
                    <th className="text-end pe-4">
                      <i className="fas fa-coins me-2 text-success"></i>
                      Toplam Değer (₺)
                    </th>
                    <th className="text-center">İşlem</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredWarehouseStats.map((wh, index) => (
                    <tr key={wh.id} style={{cursor: 'pointer'}} onClick={() => {
                      setSelectedWarehouseId(wh.id);
                      setSelectedWarehouseOpt({ id: wh.id, name: wh.name });
                    }}>
                      <td className="ps-4 fw-semibold">
                        <div className="d-flex align-items-center">
                          <div className="bg-primary bg-opacity-10 rounded-circle p-2 me-2" style={{width: '36px', height: '36px', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                            <span className="fw-bold text-primary">{index + 1}</span>
                          </div>
                          {wh.name}
                        </div>
                      </td>
                      <td>
                        <small className="text-muted">
                          <i className="fas fa-map-marker-alt me-1"></i>
                          {wh.location || '-'}
                        </small>
                      </td>
                      <td className="text-center">
                        <span className="badge rounded-pill px-3" style={{backgroundColor: '#0d6efd', color: '#fff'}}>
                          {wh.totalQuantity.toLocaleString('tr-TR')}
                        </span>
                      </td>
                      <td className="text-center">
                        <span className="badge bg-warning text-dark rounded-pill">
                          {wh.reserved.toLocaleString('tr-TR')}
                        </span>
                      </td>
                      <td className="text-center">
                        <span className="badge rounded-pill" style={{backgroundColor: '#6c757d', color: '#fff'}}>
                          {wh.consigned.toLocaleString('tr-TR')}
                        </span>
                      </td>
                      <td className="text-center">
                        <span className="badge bg-success rounded-pill">
                          {wh.productCount}
                        </span>
                      </td>
                      <td className="text-end pe-4 fw-bold" style={{color: '#1a1a1a'}}>
                        {wh.totalValue.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} ₺
                      </td>
                      <td className="text-center">
                        <button 
                          className="btn btn-sm btn-outline-primary"
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/stock?warehouseId=${wh.id}`);
                          }}
                          title="Detayları görüntüle"
                        >
                          <i className="fas fa-eye"></i>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="table-light border-top">
                  <tr className="fw-bold">
                    <td className="ps-4" colSpan="2">
                      <i className="fas fa-calculator me-2 text-primary"></i>
                      TOPLAM
                    </td>
                    <td className="text-center" style={{color: '#0d6efd'}}>
                      {filteredWarehouseStats.reduce((sum, w) => sum + w.totalQuantity, 0).toLocaleString('tr-TR')}
                    </td>
                    <td className="text-center text-warning">
                      {filteredWarehouseStats.reduce((sum, w) => sum + w.reserved, 0).toLocaleString('tr-TR')}
                    </td>
                    <td className="text-center" style={{color: '#6c757d'}}>
                      {filteredWarehouseStats.reduce((sum, w) => sum + w.consigned, 0).toLocaleString('tr-TR')}
                    </td>
                    <td className="text-center text-success">
                      {filteredWarehouseStats.reduce((sum, w) => sum + w.productCount, 0)}
                    </td>
                    <td className="text-end pe-4" style={{color: '#1a1a1a'}}>
                      {filteredWarehouseStats.reduce((sum, w) => sum + w.totalValue, 0).toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 0 })} ₺
                    </td>
                    <td></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
          <div className="card-footer bg-light border-top">
            <div className="d-flex justify-content-between align-items-center small text-muted">
              <div>
                <i className="fas fa-info-circle me-1"></i>
                Depo satırına tıklayarak filtreleme yapabilirsiniz
              </div>
              <div>
                <i className="fas fa-eye me-1"></i>
                Detay butonuna tıklayarak depo stok sayfasına gidebilirsiniz
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Charts */}
      <div className="row mb-4" style={{position: 'relative', zIndex: 1}}>
        <div className="col-lg-8 mb-4">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-header bg-white border-bottom">
              <h5 className="mb-0">
                <i className="fas fa-chart-bar me-2 text-primary"></i>
                Sistem Geneli İstatistikler
              </h5>
            </div>
            <div className="card-body">
              <div style={{ height: '350px' }}>
                <Bar data={barChartData} options={chartOptions} />
              </div>
            </div>
          </div>
        </div>

        <div className="col-lg-4 mb-4">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-header bg-white border-bottom">
              <h5 className="mb-0">
                <i className="fas fa-chart-pie me-2 text-primary"></i>
                Stok Sağlık Durumu
              </h5>
            </div>
            <div className="card-body">
              <div style={{ height: '350px' }}>
                <Pie data={pieChartData} options={chartOptions} />
              </div>
            </div>
            <div className="card-footer bg-light border-top">
              <div className="row text-center g-2">
                <div className="col-4">
                  <div className="d-flex align-items-center justify-content-center">
                    <div className="bg-success rounded-circle me-2" style={{width: '12px', height: '12px'}}></div>
                    <small className="text-muted">Normal</small>
                  </div>
                </div>
                <div className="col-4">
                  <div className="d-flex align-items-center justify-content-center">
                    <div className="bg-warning rounded-circle me-2" style={{width: '12px', height: '12px'}}></div>
                    <small className="text-muted">Düşük</small>
                  </div>
                </div>
                <div className="col-4">
                  <div className="d-flex align-items-center justify-content-center">
                    <div className="bg-danger rounded-circle me-2" style={{width: '12px', height: '12px'}}></div>
                    <small className="text-muted">Kritik</small>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Insights */}
      <div className="row mb-4" style={{position: 'relative', zIndex: 1}}>
        <div className="col-12">
          <div className="card border-0 shadow-lg" style={{
            background: 'linear-gradient(135deg, #4e54c8 0%, #8f94fb 100%)',
            borderRadius: '15px'
          }}>
            <div className="card-body text-white p-4">
              <div className="row align-items-center">
                <div className="col-md-8">
                  <h5 className="mb-3 fw-bold">
                    <i className="fas fa-lightbulb me-2" style={{color: '#ffd700'}}></i>
                    Hızlı İçgörüler
                  </h5>
                  <div className="row g-3">
                    <div className="col-md-4">
                      <div className="p-3 rounded" style={{backgroundColor: 'rgba(255, 255, 255, 0.15)', backdropFilter: 'blur(10px)'}}>
                        <div className="d-flex align-items-center">
                          <i className="fas fa-exclamation-triangle fa-2x me-3" style={{color: '#ffd700'}}></i>
                          <div>
                            <div className="h4 mb-0 fw-bold">{computedStats.lowStockItems}</div>
                            <small style={{color: '#f0f0f0'}}>Düşük stoklu ürün var</small>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="p-3 rounded" style={{backgroundColor: 'rgba(255, 255, 255, 0.15)', backdropFilter: 'blur(10px)'}}>
                        <div className="d-flex align-items-center">
                          <i className="fas fa-percentage fa-2x me-3" style={{color: '#4ade80'}}></i>
                          <div>
                            <div className="h4 mb-0 fw-bold">
                              {stats.totalProducts > 0 
                                ? Math.round((computedStats.totalStockQuantity / stats.totalProducts))
                                : 0}
                            </div>
                            <small style={{color: '#f0f0f0'}}>Ürün başına ort. stok</small>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="col-md-4">
                      <div className="p-3 rounded" style={{backgroundColor: 'rgba(255, 255, 255, 0.15)', backdropFilter: 'blur(10px)'}}>
                        <div className="d-flex align-items-center">
                          <i className="fas fa-chart-line fa-2x me-3" style={{color: '#60a5fa'}}></i>
                          <div>
                            <div className="h4 mb-0 fw-bold">
                              {computedStats.totalReserved > 0 && computedStats.totalStockQuantity > 0
                                ? Math.round((computedStats.totalReserved / computedStats.totalStockQuantity) * 100)
                                : 0}%
                            </div>
                            <small style={{color: '#f0f0f0'}}>Rezerve ürün oranı</small>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="col-md-4 text-md-end mt-3 mt-md-0">
                  <button 
                    className="btn btn-light btn-lg fw-bold shadow-sm"
                    onClick={() => navigate('/stock?filter=low-stock')}
                    style={{
                      borderRadius: '10px',
                      padding: '12px 24px'
                    }}
                  >
                    <i className="fas fa-arrow-right me-2"></i>
                    Düşük Stokları İncele
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
