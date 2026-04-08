import React from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import './design-tokens.css';
import './App.css';
import './store.css';

// Layouts
import StoreLayout from './layouts/StoreLayout';
import AdminLayout from './layouts/AdminLayout';

// Admin pages (existing)
import Dashboard from './pages/Dashboard';
import Warehouses from './pages/Warehouses';
import Products from './pages/Products';
import Categories from './pages/Categories';
import Stock from './pages/Stock';
import Login from './pages/Login';
import AdminSettings from './pages/AdminSettings';
import DesiCalculator from './pages/DesiCalculator';
import AdminAuditDetails from './pages/AdminAuditDetails';
import StockImportHistory from './pages/StockImportHistory';
import AdminNotifications from './pages/AdminNotifications';
import WarehouseActivity from './pages/WarehouseActivity';
import CezeriAssistantWidget from './components/CezeriAssistantWidget';
import './App.css';

// Admin e-commerce pages (new)
import AdminOrders from './pages/AdminOrders';
import AdminCms from './pages/AdminCms';
import AdminSiteSettings from './pages/AdminSiteSettings';
import AdminCustomers from './pages/AdminCustomers';
import AdminPayments from './pages/AdminPayments';
import AdminPaymentGateways from './pages/AdminPaymentGateways';
import AdminCoupons from './pages/AdminCoupons';
import AdminStockMovements from './pages/AdminStockMovements';
import AdminCargoProviders from './pages/AdminCargoProviders';
import AdminSupportTickets from './pages/AdminSupportTickets';
import AdminContactMessages from './pages/AdminContactMessages';
import AdminSalesDashboard from './pages/AdminSalesDashboard';
import AdminHelp from './pages/AdminHelp';

// Store pages (new)
import HomePage from './pages/store/HomePage';
import CategoryPage from './pages/store/CategoryPage';
import ProductDetailPage from './pages/store/ProductDetailPage';
import CartPage from './pages/store/CartPage';
import CheckoutPage from './pages/store/CheckoutPage';
import StoreCmsPage from './pages/store/StoreCmsPage';
import StoreLoginPage from './pages/store/StoreLoginPage';
import StoreRegisterPage from './pages/store/StoreRegisterPage';
import PaymentResultPage from './pages/store/PaymentResultPage';
import GoogleAuthCallback from './pages/store/GoogleAuthCallback';
import MyOrdersPage from './pages/store/MyOrdersPage';
import MyAddressesPage from './pages/store/MyAddressesPage';
import MyFavoritesPage from './pages/store/MyFavoritesPage';
import EmailVerifyPage from './pages/store/EmailVerifyPage';
import ForgotPasswordPage from './pages/store/ForgotPasswordPage';
import ResetPasswordPage from './pages/store/ResetPasswordPage';
import MySupportPage from './pages/store/MySupportPage';
import NotFoundPage from './pages/store/NotFoundPage';

function App() {
  const [authed, setAuthed] = useState(!!localStorage.getItem('auth_token'));
  const [role, setRole] = useState(localStorage.getItem('auth_role') || 'ADMIN');

  useEffect(() => {
    const onStorage = () => {
      setAuthed(!!localStorage.getItem('auth_token'));
      setRole(localStorage.getItem('auth_role') || 'ADMIN');
    };
    window.addEventListener('storage', onStorage);
    window.addEventListener('auth-changed', onStorage);
    return () => {
      window.removeEventListener('storage', onStorage);
      window.removeEventListener('auth-changed', onStorage);
    };
  }, []);

  // Host-aware routing: admin.* subdomain shows admin WMS, everything else shows storefront.
  // This lets us point both siteniz.com and admin.siteniz.com at the same frontend container.
  const isAdminHost = typeof window !== 'undefined' && window.location.hostname.startsWith('admin.');

  return (
    <div className="App">
      {isAdminHost ? (
        <AdminRoutes authed={authed} role={role} />
      ) : (
        <StoreRoutes />
      )}
        <CezeriAssistantWidget />
    </div>
  );
}

/**
 * When an old link like /store/urun/X reaches the store host, strip the /store
 * prefix and client-side redirect to the new clean URL. Nginx handles this at
 * the edge too, but keeping it here as a safety net.
 */
function StoreLegacyRedirect() {
  const location = useLocation();
  const newPath = location.pathname.replace(/^\/store/, '') || '/';
  return <Navigate to={newPath + location.search} replace />;
}

/**
 * When someone hits admin.siteniz.com/store/X (stale bookmark from the single-domain era),
 * bounce them across to the store domain. Full page navigation because the origin changes.
 */
function CrossDomainStoreRedirect() {
  useEffect(() => {
    const protocol = window.location.protocol;
    const storeHost = window.location.hostname.replace(/^admin\./, '');
    const newPath = window.location.pathname.replace(/^\/store/, '') || '/';
    window.location.replace(`${protocol}//${storeHost}${newPath}${window.location.search}`);
  }, []);
  return null;
}

function StoreRoutes() {
  return (
    <Routes>
      {/* ===== STOREFRONT (public, mounted at root) ===== */}
      <Route path="/" element={<StoreLayout />}>
        <Route index element={<HomePage />} />
        <Route path="kategori/:slug" element={<CategoryPage />} />
        <Route path="urun/:slug" element={<ProductDetailPage />} />
        <Route path="sepet" element={<CartPage />} />
        <Route path="odeme" element={<CheckoutPage />} />
        <Route path="odeme/sonuc" element={<PaymentResultPage />} />
        <Route path="sayfa/:slug" element={<StoreCmsPage />} />
        <Route path="giris" element={<StoreLoginPage />} />
        <Route path="kayit" element={<StoreRegisterPage />} />
        <Route path="siparislerim" element={<MyOrdersPage />} />
        <Route path="adreslerim" element={<MyAddressesPage />} />
        <Route path="favorilerim" element={<MyFavoritesPage />} />
        <Route path="destek" element={<MySupportPage />} />
        <Route path="hesap-dogrula" element={<EmailVerifyPage />} />
        <Route path="sifremi-unuttum" element={<ForgotPasswordPage />} />
        <Route path="sifre-sifirla" element={<ResetPasswordPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {/* Customer-facing Google OAuth callback */}
      <Route path="/auth/google/callback" element={<GoogleAuthCallback />} />

      {/* Legacy /store/* URL rescue (nginx handles this first, this is a safety net) */}
      <Route path="/store" element={<Navigate to="/" replace />} />
      <Route path="/store/*" element={<StoreLegacyRedirect />} />
    </Routes>
  );
}

function AdminRoutes({ authed, role }) {
  return (
    <Routes>
      {/* ===== ADMIN LOGIN ===== */}
      <Route path="/login" element={<Login />} />

        {/* ===== ADMIN (auth required, uses AdminLayout with Outlet) ===== */}
        <Route path="/" element={<AdminLayout />}>
          <Route index element={
            authed && role === 'ADMIN' ? <Dashboard /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="warehouses" element={
            authed && role === 'ADMIN' ? <Warehouses /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="products" element={
            authed && role === 'ADMIN' ? <Products /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="categories" element={
            authed && role === 'ADMIN' ? <Categories /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="stock" element={
            authed && ['ADMIN', 'STOCK_IN', 'STOCK_OUT'].includes(role) ? <Stock /> : <Navigate to="/login" replace />
          } />
          <Route path="stock-imports" element={
            authed && role === 'ADMIN' ? <StockImportHistory /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin-settings" element={
            authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['users']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/brands" element={
            authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['brand']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/colors" element={
            authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['color']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="desi" element={
            authed && role === 'ADMIN' ? <DesiCalculator /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/audit/:entityType/:entityId" element={
            authed && role === 'ADMIN' ? <AdminAuditDetails /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/notifications" element={
            authed && role === 'ADMIN' ? <AdminNotifications /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="warehouses/:warehouseId/activity" element={
            authed && role === 'ADMIN' ? <WarehouseActivity /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          {/* E-commerce admin */}
          <Route path="admin/orders" element={
            authed && role === 'ADMIN' ? <AdminOrders /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/cms" element={
            authed && role === 'ADMIN' ? <AdminCms /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/site-settings" element={
            authed && role === 'ADMIN' ? <AdminSiteSettings /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/customers" element={
            authed && role === 'ADMIN' ? <AdminCustomers /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/payments" element={
            authed && role === 'ADMIN' ? <AdminPayments /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/payment-gateways" element={
            authed && role === 'ADMIN' ? <AdminPaymentGateways /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/coupons" element={
            authed && role === 'ADMIN' ? <AdminCoupons /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/stock-movements" element={
            authed && role === 'ADMIN' ? <AdminStockMovements /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/cargo-providers" element={
            authed && role === 'ADMIN' ? <AdminCargoProviders /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/support-tickets" element={
            authed && role === 'ADMIN' ? <AdminSupportTickets /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/contact-messages" element={
            authed && role === 'ADMIN' ? <AdminContactMessages /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/sales-dashboard" element={
            authed && role === 'ADMIN' ? <AdminSalesDashboard /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
          <Route path="admin/help" element={
            authed && role === 'ADMIN' ? <AdminHelp /> : <Navigate to={authed ? '/stock' : '/login'} replace />
          } />
        </Route>

        {/* Someone hit admin.siteniz.com/store/... — bounce them to store host */}
        <Route path="/store/*" element={<CrossDomainStoreRedirect />} />
      </Routes>
  );
}

export default App;
