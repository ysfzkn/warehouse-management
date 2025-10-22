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
} from 'chart.js';

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement
);

const Dashboard = () => {
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    totalWarehouses: 0,
    totalProducts: 0,
    totalCategories: 0,
    lowStockItems: 0,
    outOfStockItems: 0,
    totalStockValue: 0
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lowStocks, setLowStocks] = useState([]);
  const [outStocks, setOutStocks] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);

  const getTotalQuantityByProduct = useCallback(async (productId) => {
    try {
      const response = await axios.get(`/api/stocks/product/${productId}/total-quantity`);
      return response.data || 0;
    } catch (error) {
      return 0;
    }
  }, []);

  const fetchDashboardData = useCallback(async () => {
    try {
      setLoading(true);
      const [warehousesRes, productsRes, categoriesRes, lowStockRes, outOfStockRes] = await Promise.all([
        axios.get('/api/warehouses'),
        axios.get('/api/products'),
        axios.get('/api/categories'),
        axios.get('/api/stocks/low-stock'),
        axios.get('/api/stocks/out-of-stock')
      ]);

      const warehouses = warehousesRes.data;
      const products = productsRes.data;
      const categories = categoriesRes.data;
      const lowStockItems = lowStockRes.data || [];
      const outOfStockItems = outOfStockRes.data || [];
      setLowStocks(lowStockItems);
      setOutStocks(outOfStockItems);

      // Calculate total stock value
      let totalStockValue = 0;
      for (const product of products) {
        if (!product?.id) continue;
        const totalQuantity = await getTotalQuantityByProduct(product.id);
        totalStockValue += product.price * totalQuantity;
      }

      setStats({
        totalWarehouses: warehouses.length,
        totalProducts: products.length,
        totalCategories: categories.length,
        lowStockItems: lowStockItems.length,
        outOfStockItems: outOfStockItems.length,
        totalStockValue: totalStockValue
      });

    } catch (error) {
      console.error('Error fetching dashboard data:', error);
      setError('Dashboard verileri yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [getTotalQuantityByProduct]);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  const filterStockList = useCallback((list) => {
    const q = (searchTerm || '').toLowerCase();
    return (list || []).filter(s => {
      const matchesSearch = !q ||
        (s.product?.name || '').toLowerCase().includes(q) ||
        (s.product?.sku || '').toLowerCase().includes(q) ||
        (s.warehouse?.name || '').toLowerCase().includes(q);
      const matchesBrand = !brandId || (s.product?.brand?.id === brandId);
      const matchesColor = !colorId || (s.product?.color?.id === colorId);
      return matchesSearch && matchesBrand && matchesColor;
    });
  }, [searchTerm, brandId, colorId]);

  const filteredLow = useMemo(() => filterStockList(lowStocks), [lowStocks, filterStockList]);
  const filteredOut = useMemo(() => filterStockList(outStocks), [outStocks, filterStockList]);

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
      <h2>Dashboard</h2>

      {/* Stats Cards */}
      <div className="row mb-4">
        <div className="col-md-3 mb-3">
          <div className="card bg-primary text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title">Toplam Depo</h5>
                  <h2>{stats.totalWarehouses}</h2>
                </div>
                <div className="align-self-center">
                  <i className="fas fa-building fa-2x"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-3 mb-3">
          <div className="card bg-success text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title">Toplam Ürün</h5>
                  <h2>{stats.totalProducts}</h2>
                </div>
                <div className="align-self-center">
                  <i className="fas fa-box fa-2x"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-3 mb-3">
          <div className="card bg-info text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title">Kategoriler</h5>
                  <h2>{stats.totalCategories}</h2>
                </div>
                <div className="align-self-center">
                  <i className="fas fa-tags fa-2x"></i>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-3 mb-3">
          <div className="card bg-warning text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between">
                <div>
                  <h5 className="card-title">Stok Değeri</h5>
                  <h2>₺{stats.totalStockValue.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</h2>
                </div>
                <div className="align-self-center">
                  <i className="fas fa-money-bill-wave fa-2x"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Filters */}
      <div className="card mb-4">
        <div className="card-body">
          <div className="row g-3 align-items-end">
            <div className="col-md-6">
              <div className="input-group">
                <span className="input-group-text"><i className="fas fa-search"></i></span>
                <input type="text" className="form-control" placeholder="Ürün adı, SKU veya depo ara..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
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
          {(searchTerm || brandId || colorId) && (
            <FilterChips
              className="mt-3"
              chips={[
                searchTerm ? { icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') } : null,
                brandId ? { icon: 'fas fa-copyright', label: `Marka: ${brandOpt?.name || brandId}`, onClear: () => { setBrandId(null); setBrandOpt(null); } } : null,
                colorId ? { icon: 'fas fa-palette', label: `Renk: ${colorOpt?.name || colorId}`, onClear: () => { setColorId(null); setColorOpt(null); } } : null,
              ].filter(Boolean)}
              onClearAll={() => { setSearchTerm(''); setBrandId(null); setColorId(null); setBrandOpt(null); setColorOpt(null); }}
            />
          )}
        </div>
      </div>

      {/* Alert Cards */}
      <div className="row mb-4">
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

      {/* Charts */}
      <div className="row">
        <div className="col-md-8 mb-4">
          <div className="card">
            <div className="card-header">
              <h5 className="card-title mb-0">Genel İstatistikler</h5>
            </div>
            <div className="card-body">
              <div style={{ height: '300px' }}>
                <Bar data={barChartData} options={chartOptions} />
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-4 mb-4">
          <div className="card">
            <div className="card-header">
              <h5 className="card-title mb-0">Stok Durumu Dağılımı</h5>
            </div>
            <div className="card-body">
              <div style={{ height: '300px' }}>
                <Pie data={pieChartData} options={chartOptions} />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
