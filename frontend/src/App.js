import React, { lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import './design-tokens.css';
import './App.css';
import './store.css';

// Layouts — eager (shell rendered on every route)
import StoreLayout from './layouts/StoreLayout';
import AdminLayout from './layouts/AdminLayout';
import ScrollToTop from './components/ScrollToTop';
import { WorkspaceProvider } from './components/WorkspaceContext';

// Code-splitting: every page is lazy-loaded so the storefront bundle never ships
// admin code (and vice versa), and each route only downloads what it needs. This
// keeps the initial bundle small and first paint fast. Webpack chunk names group
// admin vs. store for easier analysis.

// Admin pages
const Dashboard = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Dashboard'));
const Warehouses = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Warehouses'));
const Products = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Products'));
const ProductSets = lazy(() => import(/* webpackChunkName: "admin" */ './pages/ProductSets'));
const Categories = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Categories'));
const Stock = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Stock'));
const Login = lazy(() => import(/* webpackChunkName: "admin" */ './pages/Login'));
const AdminSettings = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminSettings'));
const DesiCalculator = lazy(() => import(/* webpackChunkName: "admin" */ './pages/DesiCalculator'));
const AdminAuditDetails = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminAuditDetails'));
const StockImportHistory = lazy(() => import(/* webpackChunkName: "admin" */ './pages/StockImportHistory'));
const AdminNotifications = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminNotifications'));
const WarehouseActivity = lazy(() => import(/* webpackChunkName: "admin" */ './pages/WarehouseActivity'));
const AdminOrders = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminOrders'));
const AdminReturns = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminReturns'));
const AdminCms = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminCms'));
const AdminSiteSettings = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminSiteSettings'));
const AdminCustomers = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminCustomers'));
const AdminPayments = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminPayments'));
const AdminPaymentGateways = lazy(
  () => import(/* webpackChunkName: "admin" */ './pages/AdminPaymentGateways')
);
const AdminCoupons = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminCoupons'));
const AdminInvoices = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminInvoices'));
const AdminStockMovements = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminStockMovements'));
const AdminCargoProviders = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminCargoProviders'));
const AdminSupportTickets = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminSupportTickets'));
const AdminReviews = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminReviews'));
const AdminContactMessages = lazy(
  () => import(/* webpackChunkName: "admin" */ './pages/AdminContactMessages')
);
const AdminSalesDashboard = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminSalesDashboard'));
const AdminHelp = lazy(() => import(/* webpackChunkName: "admin" */ './pages/AdminHelp'));
const AssistantDocumentsPage = lazy(
  () => import(/* webpackChunkName: "admin-assistant" */ './pages/AssistantDocumentsPage')
);
const AssistantDashboardPage = lazy(
  () => import(/* webpackChunkName: "admin-assistant" */ './pages/AssistantDashboardPage')
);
const AssistantLogsPage = lazy(
  () => import(/* webpackChunkName: "admin-assistant" */ './pages/AssistantLogsPage')
);
const AssistantSettingsPage = lazy(
  () => import(/* webpackChunkName: "admin-assistant" */ './pages/AssistantSettingsPage')
);
const AssistantDiagnosticsPage = lazy(
  () => import(/* webpackChunkName: "admin-assistant" */ './pages/AssistantDiagnosticsPage')
);

// Store pages
const HomePage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/HomePage'));
const CategoryPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/CategoryPage'));
const ProductDetailPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/ProductDetailPage')
);
const CartPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/CartPage'));
const CheckoutPage = lazy(
  () => import(/* webpackChunkName: "store-checkout" */ './pages/store/CheckoutPage')
);
const StoreCmsPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/StoreCmsPage'));
const StoreLoginPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/StoreLoginPage'));
const StoreRegisterPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/StoreRegisterPage')
);
const PaymentResultPage = lazy(
  () => import(/* webpackChunkName: "store-checkout" */ './pages/store/PaymentResultPage')
);
const BankTransferResumePage = lazy(
  () => import(/* webpackChunkName: "store-checkout" */ './pages/store/BankTransferResumePage')
);
const ManualOrderConfirmationPage = lazy(
  () => import(/* webpackChunkName: "store-checkout" */ './pages/store/ManualOrderConfirmationPage')
);
const GoogleAuthCallback = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/GoogleAuthCallback')
);
const MyOrdersPage = lazy(() => import(/* webpackChunkName: "store-account" */ './pages/store/MyOrdersPage'));
const MyAddressesPage = lazy(
  () => import(/* webpackChunkName: "store-account" */ './pages/store/MyAddressesPage')
);
const MyFavoritesPage = lazy(
  () => import(/* webpackChunkName: "store-account" */ './pages/store/MyFavoritesPage')
);
const NotificationPreferencesPage = lazy(
  () => import(/* webpackChunkName: "store-account" */ './pages/store/NotificationPreferencesPage')
);
const MyPrivacyPage = lazy(
  () => import(/* webpackChunkName: "store-account" */ './pages/store/MyPrivacyPage')
);
const EmailVerifyPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/EmailVerifyPage'));
const ForgotPasswordPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/ForgotPasswordPage')
);
const ResetPasswordPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/ResetPasswordPage')
);
const CompleteAccountPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/CompleteAccountPage')
);
const OrderTrackingPage = lazy(
  () => import(/* webpackChunkName: "store" */ './pages/store/OrderTrackingPage')
);
const MySupportPage = lazy(
  () => import(/* webpackChunkName: "store-account" */ './pages/store/MySupportPage')
);
const NotFoundPage = lazy(() => import(/* webpackChunkName: "store" */ './pages/store/NotFoundPage'));

/** Lightweight full-page fallback shown while a lazy route chunk loads. */
function RouteFallback() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        width: '100%',
      }}
    >
      <div className="spinner-border text-primary" role="status">
        <span className="visually-hidden">Yükleniyor…</span>
      </div>
    </div>
  );
}

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

  // Host-aware routing: admin.* or wms.* subdomain → admin WMS, everything else → storefront.
  // siteniz.com + admin.siteniz.com (legacy) + wms.siteniz.com (new launch) share the same frontend
  // container; only this host check determines which route tree gets rendered.
  // Customizable via the REACT_APP_ADMIN_HOSTS env variable (e.g. "admin,wms,panel").
  const ADMIN_HOST_PREFIXES = (process.env.REACT_APP_ADMIN_HOSTS || 'admin,wms')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const isAdminHost =
    typeof window !== 'undefined' &&
    (() => {
      const h = window.location.hostname || '';
      return ADMIN_HOST_PREFIXES.some((p) => h.startsWith(p + '.'));
    })();

  return (
    <div className="App">
      {/* Auto-scroll to top on page transitions — user always starts at the top of the page */}
      <ScrollToTop />
      {isAdminHost ? (
        <WorkspaceProvider>
          <AdminRoutes authed={authed} role={role} />
        </WorkspaceProvider>
      ) : (
        <StoreRoutes />
      )}
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
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        {/* ===== STOREFRONT (public, mounted at root) ===== */}
        <Route path="/" element={<StoreLayout />}>
          <Route index element={<HomePage />} />
          <Route path="kategori/:slug" element={<CategoryPage />} />
          <Route path="urun/:slug" element={<ProductDetailPage />} />
          <Route path="sepet" element={<CartPage />} />
          <Route path="odeme" element={<CheckoutPage />} />
          <Route path="odeme/sonuc" element={<PaymentResultPage />} />
          {/* Resume bank transfer payment — clicked from My Orders, IBAN/QR/reference shown again */}
          <Route path="odeme/havale/:orderId" element={<BankTransferResumePage />} />
          <Route path="siparis-onay/:token" element={<ManualOrderConfirmationPage />} />
          <Route path="sayfa/:slug" element={<StoreCmsPage />} />
          <Route path="giris" element={<StoreLoginPage />} />
          <Route path="kayit" element={<StoreRegisterPage />} />
          <Route path="siparislerim" element={<MyOrdersPage />} />
          <Route path="adreslerim" element={<MyAddressesPage />} />
          <Route path="favorilerim" element={<MyFavoritesPage />} />
          <Route path="hesabim/bildirimler" element={<NotificationPreferencesPage />} />
          <Route path="hesabim/gizlilik" element={<MyPrivacyPage />} />
          <Route path="destek" element={<MySupportPage />} />
          <Route path="hesap-dogrula" element={<EmailVerifyPage />} />
          <Route path="sifremi-unuttum" element={<ForgotPasswordPage />} />
          <Route path="sifre-sifirla" element={<ResetPasswordPage />} />
          <Route path="hesap-tamamla" element={<CompleteAccountPage />} />
          <Route path="siparis-takip" element={<OrderTrackingPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/* Customer-facing Google OAuth callback */}
        <Route path="/auth/google/callback" element={<GoogleAuthCallback />} />

        {/* Legacy /store/* URL rescue (nginx handles this first, this is a safety net) */}
        <Route path="/store" element={<Navigate to="/" replace />} />
        <Route path="/store/*" element={<StoreLegacyRedirect />} />
      </Routes>
    </Suspense>
  );
}

function AdminRoutes({ authed, role }) {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        {/* ===== ADMIN LOGIN ===== */}
        <Route path="/login" element={<Login />} />

        {/* ===== ADMIN (auth required, uses AdminLayout with Outlet) ===== */}
        <Route path="/" element={<AdminLayout />}>
          <Route
            index
            element={
              authed && role === 'ADMIN' ? (
                <Dashboard />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="warehouses"
            element={
              authed && role === 'ADMIN' ? (
                <Warehouses />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="products"
            element={
              authed && role === 'ADMIN' ? (
                <Products />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/product-sets"
            element={
              authed && role === 'ADMIN' ? (
                <ProductSets />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="categories"
            element={
              authed && role === 'ADMIN' ? (
                <Categories />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="stock"
            element={
              authed && ['ADMIN', 'STOCK_IN', 'STOCK_OUT'].includes(role) ? (
                <Stock />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="stock-imports"
            element={
              authed && role === 'ADMIN' ? (
                <StockImportHistory />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin-settings"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSettings allowedTabs={['users']} />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/brands"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSettings allowedTabs={['brand']} />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/colors"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSettings allowedTabs={['color']} />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="desi"
            element={
              authed && role === 'ADMIN' ? (
                <DesiCalculator />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/audit/:entityType/:entityId"
            element={
              authed && role === 'ADMIN' ? (
                <AdminAuditDetails />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/notifications"
            element={
              authed && role === 'ADMIN' ? (
                <AdminNotifications />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="warehouses/:warehouseId/activity"
            element={
              authed && role === 'ADMIN' ? (
                <WarehouseActivity />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          {/* E-commerce admin */}
          <Route
            path="admin/orders"
            element={
              authed && role === 'ADMIN' ? (
                <AdminOrders />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/returns"
            element={
              authed && role === 'ADMIN' ? (
                <AdminReturns />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/cms"
            element={
              authed && role === 'ADMIN' ? (
                <AdminCms />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/site-settings"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSiteSettings />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/customers"
            element={
              authed && role === 'ADMIN' ? (
                <AdminCustomers />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/payments"
            element={
              authed && role === 'ADMIN' ? (
                <AdminPayments />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/payment-gateways"
            element={
              authed && role === 'ADMIN' ? (
                <AdminPaymentGateways />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/coupons"
            element={
              authed && role === 'ADMIN' ? (
                <AdminCoupons />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/invoices"
            element={
              authed && role === 'ADMIN' ? (
                <AdminInvoices />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/stock-movements"
            element={
              authed && role === 'ADMIN' ? (
                <AdminStockMovements />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/cargo-providers"
            element={
              authed && role === 'ADMIN' ? (
                <AdminCargoProviders />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/support-tickets"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSupportTickets />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/reviews"
            element={
              authed && role === 'ADMIN' ? (
                <AdminReviews />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/contact-messages"
            element={
              authed && role === 'ADMIN' ? (
                <AdminContactMessages />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/sales-dashboard"
            element={
              authed && role === 'ADMIN' ? (
                <AdminSalesDashboard />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/help"
            element={
              authed && role === 'ADMIN' ? (
                <AdminHelp />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          {/* Cezeri v2: assistant management + observability */}
          <Route
            path="admin/assistant/documents"
            element={
              authed && role === 'ADMIN' ? (
                <AssistantDocumentsPage />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/assistant/dashboard"
            element={
              authed && role === 'ADMIN' ? (
                <AssistantDashboardPage />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/assistant/logs"
            element={
              authed && role === 'ADMIN' ? (
                <AssistantLogsPage />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/assistant/settings"
            element={
              authed && role === 'ADMIN' ? (
                <AssistantSettingsPage />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
          <Route
            path="admin/assistant/diagnostics"
            element={
              authed && role === 'ADMIN' ? (
                <AssistantDiagnosticsPage />
              ) : (
                <Navigate to={authed ? '/stock' : '/login'} replace />
              )
            }
          />
        </Route>

        {/* Someone hit admin.siteniz.com/store/... — bounce them to store host */}
        <Route path="/store/*" element={<CrossDomainStoreRedirect />} />
      </Routes>
    </Suspense>
  );
}

export default App;
