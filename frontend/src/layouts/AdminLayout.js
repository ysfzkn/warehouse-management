import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { AdminToastProvider } from '../components/AdminToast';

export default function AdminLayout() {
  const token = localStorage.getItem('auth_token');

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <AdminToastProvider>
      <div>
        <Navbar />
        <div className="container-fluid px-4 py-3">
          <Outlet />
        </div>
      </div>
    </AdminToastProvider>
  );
}
