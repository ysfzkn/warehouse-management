import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, LineElement, PointElement, ArcElement, Title, Tooltip, Legend, Filler } from 'chart.js';
import { Bar, Doughnut, Line } from 'react-chartjs-2';

ChartJS.register(CategoryScale, LinearScale, BarElement, LineElement, PointElement, ArcElement, Title, Tooltip, Legend, Filler);

const fmt = (v) => v != null ? new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(v) : '₺0';
const fmtNum = (v) => v != null ? new Intl.NumberFormat('tr-TR').format(v) : '0';

const STATUS_COLORS = { PENDING_PAYMENT: '#f59e0b', PAID: '#3b82f6', PREPARING: '#8b5cf6', SHIPPED: '#6366f1', DELIVERED: '#10b981', CANCELLED: '#ef4444', RETURN_REQUESTED: '#d97706' };
const STATUS_LABELS = { PENDING_PAYMENT: 'Ödeme Bekliyor', PAID: 'Ödendi', PREPARING: 'Hazırlanıyor', SHIPPED: 'Kargoda', DELIVERED: 'Teslim Edildi', CANCELLED: 'İptal', RETURN_REQUESTED: 'İade Talebi' };
const PM_LABELS = { CREDIT_CARD: 'Kredi Kartı', BANK_TRANSFER: 'Havale/EFT', DOOR_CASH: 'Kapıda Nakit', DOOR_CARD: 'Kapıda Kart', VIRTUAL_POS: 'Sanal POS' };

function ChangeBadge({ value }) {
  if (value == null || value === 0) return null;
  const up = value > 0;
  return (
    <span className="d-inline-flex align-items-center gap-1 px-2 py-1 rounded-pill" style={{
      fontSize: 10, fontWeight: 600,
      background: up ? '#ecfdf5' : '#fef2f2',
      color: up ? '#059669' : '#dc2626',
    }}>
      <i className={`fas fa-arrow-${up ? 'up' : 'down'}`} style={{ fontSize: 8 }} />
      {Math.abs(value)}%
    </span>
  );
}

export default function AdminSalesDashboard() {
  const [summary, setSummary] = useState(null);
  const [dailyRevenue, setDailyRevenue] = useState([]);
  const [topProducts, setTopProducts] = useState([]);
  const [paymentBreakdown, setPaymentBreakdown] = useState([]);
  const [recentOrders, setRecentOrders] = useState([]);
  const [heatmapData, setHeatmapData] = useState([]);
  const [liveStats, setLiveStats] = useState(null);
  const [period, setPeriod] = useState('month');
  const [chartDays, setChartDays] = useState(30);
  const [loading, setLoading] = useState(true);
  const liveInterval = useRef(null);
  const navigate = useNavigate();

  const fetchData = useCallback(() => {
    setLoading(true);
    Promise.all([
      axios.get('/api/admin/sales-dashboard/summary', { params: { period } }),
      axios.get('/api/admin/sales-dashboard/daily-revenue', { params: { days: chartDays } }),
      axios.get('/api/admin/sales-dashboard/top-products'),
      axios.get('/api/admin/sales-dashboard/payment-breakdown'),
      axios.get('/api/admin/sales-dashboard/recent-orders'),
      axios.get('/api/admin/sales-dashboard/hourly-heatmap', { params: { days: chartDays } }),
    ]).then(([s, d, t, p, r, h]) => {
      setSummary(s.data); setDailyRevenue(d.data || []); setTopProducts(t.data || []);
      setPaymentBreakdown(p.data || []); setRecentOrders(r.data || []); setHeatmapData(h.data || []);
    }).catch(() => {}).finally(() => setLoading(false));
  }, [period, chartDays]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Live stats — poll every 30 seconds
  useEffect(() => {
    const fetchLive = () => axios.get('/api/admin/sales-dashboard/live').then(r => setLiveStats(r.data)).catch(() => {});
    fetchLive();
    liveInterval.current = setInterval(fetchLive, 30000);
    return () => clearInterval(liveInterval.current);
  }, []);

  if (loading && !summary) return <div className="text-center py-5"><span className="spinner-border" /></div>;

  const s = summary || {};

  // Revenue chart
  const revenueChartData = {
    labels: dailyRevenue.map(d => { const dt = new Date(d.date); return dt.toLocaleDateString('tr-TR', { day: '2-digit', month: 'short' }); }),
    datasets: [
      { label: 'Gelir (₺)', data: dailyRevenue.map(d => d.revenue), borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,0.08)', fill: true, tension: 0.4, pointRadius: 1, borderWidth: 2 },
      { label: 'Sipariş', data: dailyRevenue.map(d => d.orders), borderColor: '#10b981', backgroundColor: 'rgba(16,185,129,0.08)', fill: true, tension: 0.4, yAxisID: 'y1', pointRadius: 1, borderWidth: 2 },
    ],
  };
  const revenueChartOpts = {
    responsive: true, maintainAspectRatio: false, interaction: { intersect: false, mode: 'index' },
    plugins: { legend: { position: 'top', labels: { usePointStyle: true, padding: 16, font: { size: 11 } } } },
    scales: {
      y: { beginAtZero: true, ticks: { callback: v => fmt(v), font: { size: 10 } }, grid: { color: '#f1f5f9' } },
      y1: { position: 'right', beginAtZero: true, ticks: { font: { size: 10 } }, grid: { display: false } },
      x: { ticks: { font: { size: 9 }, maxRotation: 45 }, grid: { display: false } },
    },
  };

  // Payment doughnut
  const pmColors = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];
  const pmChartData = {
    labels: paymentBreakdown.map(p => p.label),
    datasets: [{ data: paymentBreakdown.map(p => p.count), backgroundColor: pmColors.slice(0, paymentBreakdown.length), borderWidth: 0 }],
  };

  // Top products bar
  const topProdData = {
    labels: topProducts.map(p => p.name.length > 20 ? p.name.substring(0, 20) + '...' : p.name),
    datasets: [{ label: 'Gelir (₺)', data: topProducts.map(p => p.revenue), backgroundColor: '#2563eb', borderRadius: 6, barPercentage: 0.6 }],
  };

  // Heatmap
  const maxHeat = Math.max(1, ...heatmapData.map(h => h.count));
  const dayNames = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <div>
          <h2 className="mb-1">Satış Dashboard</h2>
          <p className="text-muted small mb-0">E-ticaret satış performansı ve analitik</p>
        </div>
        <div className="d-flex gap-2 align-items-center">
          {/* Live indicator */}
          {liveStats && (
            <div className="d-flex align-items-center gap-2 px-3 py-2 rounded-pill" style={{ background: '#ecfdf5', border: '1px solid #a7f3d0' }}>
              <span className="d-inline-block rounded-circle" style={{ width: 8, height: 8, background: '#10b981', animation: 'pulse 2s infinite' }} />
              <span style={{ fontSize: 11, fontWeight: 600, color: '#059669' }}>Bugün: {fmt(liveStats.todayRevenue)} · {liveStats.todayOrders} sipariş</span>
            </div>
          )}
          <div className="btn-group btn-group-sm">
            {[['today','Bugün'],['week','Hafta'],['month','Ay'],['quarter','3 Ay'],['year','Yıl']].map(([p, label]) => (
              <button key={p} className={`btn ${period === p ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => { setPeriod(p); setChartDays(p === 'today' ? 1 : p === 'week' ? 7 : p === 'month' ? 30 : p === 'quarter' ? 90 : 365); }}>
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Live pulse animation */}
      <style>{`@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.4; } }`}</style>

      {/* KPI Cards with comparison */}
      <div className="row g-3 mb-4">
        {[
          { label: 'Toplam Gelir', value: fmt(s.totalRevenue), change: s.revenueChange, icon: 'fa-chart-line', color: '#2563eb', bg: '#eff6ff' },
          { label: 'Teslim Edilen', value: fmt(s.deliveredRevenue), icon: 'fa-check-double', color: '#059669', bg: '#ecfdf5' },
          { label: 'Sipariş Sayısı', value: fmtNum(s.periodOrders || s.totalOrders), change: s.ordersChange, icon: 'fa-shopping-cart', color: '#7c3aed', bg: '#f5f3ff' },
          { label: 'Ort. Sipariş', value: fmt(s.avgOrderValue), icon: 'fa-receipt', color: '#d97706', bg: '#fffbeb' },
          { label: 'Aktif Sipariş', value: fmtNum(s.activeOrders), icon: 'fa-truck', color: '#2563eb', bg: '#eff6ff' },
          { label: 'Müşteriler', value: fmtNum(s.totalCustomers), icon: 'fa-users', color: '#059669', bg: '#ecfdf5' },
        ].map((kpi, i) => (
          <div key={i} className="col-6 col-md-4 col-xl-2">
            <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
              <div className="card-body py-3">
                <div className="d-flex align-items-center gap-2 mb-2">
                  <div style={{ width: 32, height: 32, borderRadius: 8, background: kpi.bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <i className={`fas ${kpi.icon}`} style={{ color: kpi.color, fontSize: 13 }} />
                  </div>
                  <span className="text-muted" style={{ fontSize: 11 }}>{kpi.label}</span>
                </div>
                <div className="d-flex align-items-center gap-2">
                  <div className="fw-bold" style={{ fontSize: 18 }}>{kpi.value}</div>
                  {kpi.change != null && <ChangeBadge value={kpi.change} />}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Status breakdown */}
      <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
        <div className="card-body py-3">
          <div className="d-flex flex-wrap gap-3 justify-content-center">
            {[['PENDING_PAYMENT',s.pendingPayment],['PAID',s.paid],['PREPARING',s.preparing],['SHIPPED',s.shipped],['DELIVERED',s.delivered],['CANCELLED',s.cancelled],['RETURN_REQUESTED',s.returnRequested]].map(([status, count]) => (
              <div key={status} className="text-center px-3 py-2 rounded-3" style={{ background: STATUS_COLORS[status] + '15', cursor: 'pointer', minWidth: 90, transition: 'transform 0.15s' }}
                onClick={() => navigate('/admin/orders')}
                onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.05)'}
                onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}>
                <div className="fw-bold" style={{ color: STATUS_COLORS[status], fontSize: 20 }}>{count || 0}</div>
                <div style={{ fontSize: 10, color: STATUS_COLORS[status] }}>{STATUS_LABELS[status]}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Charts row */}
      <div className="row g-4 mb-4">
        <div className="col-lg-8">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-transparent border-0 d-flex justify-content-between align-items-center">
              <h6 className="mb-0 fw-bold"><i className="fas fa-chart-area me-2 text-primary" />Gelir Trendi</h6>
              <div className="btn-group btn-group-sm">
                {[7,30,90].map(d => (
                  <button key={d} className={`btn ${chartDays === d ? 'btn-primary' : 'btn-outline-secondary'}`}
                    onClick={() => setChartDays(d)}>{d}G</button>
                ))}
              </div>
            </div>
            <div className="card-body pt-0" style={{ height: 300 }}>
              {dailyRevenue.length > 0 ? <Line data={revenueChartData} options={revenueChartOpts} /> : <div className="text-center text-muted py-5">Veri yok</div>}
            </div>
          </div>
        </div>
        <div className="col-lg-4">
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-header bg-transparent border-0">
              <h6 className="mb-0 fw-bold"><i className="fas fa-credit-card me-2 text-success" />Ödeme Dağılımı</h6>
            </div>
            <div className="card-body d-flex flex-column align-items-center justify-content-center">
              {paymentBreakdown.length > 0 ? (
                <div style={{ maxWidth: 220 }}><Doughnut data={pmChartData} options={{ plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, padding: 12, font: { size: 11 } } } }, cutout: '65%' }} /></div>
              ) : <div className="text-muted small">Veri yok</div>}
            </div>
          </div>
        </div>
      </div>

      {/* Heatmap + Top products */}
      <div className="row g-4 mb-4">
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-transparent border-0">
              <h6 className="mb-0 fw-bold"><i className="fas fa-fire me-2 text-danger" />Sipariş Yoğunluk Haritası</h6>
              <small className="text-muted">Hangi gün ve saatte en çok sipariş geliyor</small>
            </div>
            <div className="card-body pt-0">
              {heatmapData.length > 0 ? (
                <div style={{ overflowX: 'auto' }}>
                  <table className="w-100" style={{ borderCollapse: 'separate', borderSpacing: 2 }}>
                    <thead>
                      <tr>
                        <th style={{ width: 36, fontSize: 9, color: '#94a3b8' }}></th>
                        {Array.from({ length: 24 }, (_, h) => (
                          <th key={h} style={{ fontSize: 8, color: '#94a3b8', textAlign: 'center', padding: 0, minWidth: 18 }}>
                            {h % 3 === 0 ? `${h}` : ''}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {dayNames.map((day, d) => (
                        <tr key={d}>
                          <td style={{ fontSize: 9, color: '#64748b', fontWeight: 600, paddingRight: 6 }}>{day}</td>
                          {Array.from({ length: 24 }, (_, h) => {
                            const cell = heatmapData.find(c => c.day === d && c.hour === h);
                            const count = cell ? cell.count : 0;
                            const intensity = maxHeat > 0 ? count / maxHeat : 0;
                            const bg = count === 0 ? '#f8fafc'
                              : intensity < 0.25 ? '#dbeafe'
                              : intensity < 0.5 ? '#93c5fd'
                              : intensity < 0.75 ? '#3b82f6'
                              : '#1d4ed8';
                            const textColor = intensity >= 0.5 ? '#fff' : '#64748b';
                            return (
                              <td key={h} title={`${day} ${h}:00 — ${count} sipariş`}
                                style={{
                                  background: bg, borderRadius: 3, textAlign: 'center',
                                  fontSize: 8, color: textColor, padding: '4px 0',
                                  cursor: 'default', transition: 'transform 0.1s',
                                }}>
                                {count > 0 ? count : ''}
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div className="d-flex align-items-center justify-content-end gap-1 mt-2">
                    <span style={{ fontSize: 9, color: '#94a3b8' }}>Az</span>
                    {['#f8fafc','#dbeafe','#93c5fd','#3b82f6','#1d4ed8'].map((c, i) => (
                      <div key={i} style={{ width: 12, height: 12, borderRadius: 2, background: c }} />
                    ))}
                    <span style={{ fontSize: 9, color: '#94a3b8' }}>Çok</span>
                  </div>
                </div>
              ) : <div className="text-muted text-center py-4 small">Yeterli veri yok.</div>}
            </div>
          </div>
        </div>
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-transparent border-0">
              <h6 className="mb-0 fw-bold"><i className="fas fa-trophy me-2 text-warning" />En Çok Satan Ürünler</h6>
            </div>
            <div className="card-body pt-0">
              {topProducts.length > 0 ? (
                <div style={{ height: 280 }}><Bar data={topProdData} options={{
                  indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                  plugins: { legend: { display: false } },
                  scales: { x: { ticks: { callback: v => fmt(v), font: { size: 10 } }, grid: { color: '#f1f5f9' } }, y: { ticks: { font: { size: 10 } } } },
                }} /></div>
              ) : <div className="text-muted text-center py-5">Henüz satış verisi yok.</div>}
            </div>
          </div>
        </div>
      </div>

      {/* Recent orders + Payment breakdown */}
      <div className="row g-4">
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-transparent border-0 d-flex justify-content-between align-items-center">
              <h6 className="mb-0 fw-bold"><i className="fas fa-clock me-2 text-info" />Son Siparişler</h6>
              <button className="btn btn-sm btn-outline-primary" onClick={() => navigate('/admin/orders')}>Tümü</button>
            </div>
            <div className="card-body p-0">
              {recentOrders.length > 0 ? (
                <div className="list-group list-group-flush">
                  {recentOrders.map(o => (
                    <div key={o.id} className="list-group-item d-flex justify-content-between align-items-center py-2 px-3" style={{ cursor: 'pointer' }} onClick={() => navigate('/admin/orders')}>
                      <div>
                        <div className="fw-semibold small text-primary">#{o.orderNumber}</div>
                        <div className="text-muted" style={{ fontSize: 10 }}>{o.customerName} · {PM_LABELS[o.paymentMethod] || o.paymentMethod}</div>
                      </div>
                      <div className="text-end">
                        <div className="fw-bold small">{fmt(o.grandTotal)}</div>
                        <span className="badge" style={{ fontSize: 9, background: (STATUS_COLORS[o.status] || '#6b7280') + '20', color: STATUS_COLORS[o.status] || '#6b7280' }}>
                          {STATUS_LABELS[o.status] || o.status}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              ) : <div className="text-muted text-center py-5 small">Henüz sipariş yok.</div>}
            </div>
          </div>
        </div>
        <div className="col-lg-6">
          {paymentBreakdown.length > 0 && (
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
              <div className="card-header bg-transparent border-0">
                <h6 className="mb-0 fw-bold"><i className="fas fa-chart-pie me-2" style={{ color: '#7c3aed' }} />Ödeme Detayları</h6>
              </div>
              <div className="card-body p-0">
                <table className="table table-hover mb-0 small">
                  <thead className="table-light"><tr><th>Yöntem</th><th className="text-center">Sipariş</th><th className="text-end">Toplam</th><th className="text-end">Oran</th></tr></thead>
                  <tbody>
                    {paymentBreakdown.map((p, i) => {
                      const totalPm = paymentBreakdown.reduce((a, b) => a + b.count, 0);
                      const pct = totalPm > 0 ? (p.count / totalPm * 100).toFixed(1) : 0;
                      return (
                        <tr key={i}>
                          <td><div className="d-flex align-items-center gap-2"><div style={{ width: 10, height: 10, borderRadius: '50%', background: pmColors[i] || '#6b7280' }} />{p.label}</div></td>
                          <td className="text-center">{p.count}</td>
                          <td className="text-end fw-medium">{fmt(p.revenue)}</td>
                          <td className="text-end">
                            <div className="d-flex align-items-center gap-2 justify-content-end">
                              <div style={{ width: 50, height: 5, borderRadius: 3, background: '#f1f5f9' }}>
                                <div style={{ width: pct + '%', height: '100%', borderRadius: 3, background: pmColors[i] || '#6b7280' }} />
                              </div>
                              <span className="text-muted">{pct}%</span>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
