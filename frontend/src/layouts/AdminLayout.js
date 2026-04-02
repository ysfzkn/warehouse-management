import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import Navbar from '../components/Navbar';

export default function AdminLayout() {
  const token = localStorage.getItem('auth_token');

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div>
      <Navbar />
      <div className="container-fluid px-4 py-3">
        <Outlet />
      </div>
    </div>
  );
}
