import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import Navbar from './components/Navbar';
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
  return (
    <div className="App">
      {authed && <Navbar />}
      <div className="container-fluid mt-4">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={authed && role === 'ADMIN' ? <Dashboard /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/warehouses" element={authed && role === 'ADMIN' ? <Warehouses /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/products" element={authed && role === 'ADMIN' ? <Products /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/categories" element={authed && role === 'ADMIN' ? <Categories /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/stock" element={authed && (role === 'ADMIN' || role === 'STOCK_IN' || role === 'STOCK_OUT') ? <Stock /> : <Navigate to="/login" replace />} />
          <Route path="/stock-imports" element={authed && role === 'ADMIN' ? <StockImportHistory /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/admin-settings" element={authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['users']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/admin/brands" element={authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['brand']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/admin/colors" element={authed && role === 'ADMIN' ? <AdminSettings allowedTabs={['color']} /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/desi" element={authed && role === 'ADMIN' ? <DesiCalculator /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/admin/audit/:entityType/:entityId" element={authed && role === 'ADMIN' ? <AdminAuditDetails /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/admin/notifications" element={authed && role === 'ADMIN' ? <AdminNotifications /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
          <Route path="/warehouses/:warehouseId/activity" element={authed && role === 'ADMIN' ? <WarehouseActivity /> : <Navigate to={authed ? '/stock' : '/login'} replace />} />
        </Routes>
      </div>
      {/* Global, fixed assistant widget (visible on all screens) */}
      <CezeriAssistantWidget />
    </div>
  );
}

export default App;
