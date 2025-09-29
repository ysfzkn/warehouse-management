import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { Bar, Pie } from 'react-chartjs-2';
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
      const lowStockItems = lowStockRes.data;
      const outOfStockItems = outOfStockRes.data;

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
