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
import './App.css';

function App() {
  const [authed, setAuthed] = useState(!!localStorage.getItem('auth_token'));

  useEffect(() => {
    const onStorage = () => setAuthed(!!localStorage.getItem('auth_token'));
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
          <Route path="/" element={authed ? <Dashboard /> : <Navigate to="/login" replace />} />
          <Route path="/warehouses" element={authed ? <Warehouses /> : <Navigate to="/login" replace />} />
          <Route path="/products" element={authed ? <Products /> : <Navigate to="/login" replace />} />
          <Route path="/categories" element={authed ? <Categories /> : <Navigate to="/login" replace />} />
          <Route path="/stock" element={authed ? <Stock /> : <Navigate to="/login" replace />} />
          <Route path="/admin-settings" element={authed ? <AdminSettings /> : <Navigate to="/login" replace />} />
          <Route path="/desi" element={authed ? <DesiCalculator /> : <Navigate to="/login" replace />} />
        </Routes>
      </div>
    </div>
  );
}

export default App;
