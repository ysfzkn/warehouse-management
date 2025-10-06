import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import Warehouses from './pages/Warehouses';
import Products from './pages/Products';
import Categories from './pages/Categories';
import Stock from './pages/Stock';
import Login from './pages/Login';
import './App.css';

function App() {
  const isAuthed = !!localStorage.getItem('auth_token');
  return (
    <div className="App">
      {isAuthed && <Navbar />}
      <div className="container-fluid mt-4">
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={isAuthed ? <Dashboard /> : <Navigate to="/login" replace />} />
          <Route path="/warehouses" element={isAuthed ? <Warehouses /> : <Navigate to="/login" replace />} />
          <Route path="/products" element={isAuthed ? <Products /> : <Navigate to="/login" replace />} />
          <Route path="/categories" element={isAuthed ? <Categories /> : <Navigate to="/login" replace />} />
          <Route path="/stock" element={isAuthed ? <Stock /> : <Navigate to="/login" replace />} />
        </Routes>
      </div>
    </div>
  );
}

export default App;
