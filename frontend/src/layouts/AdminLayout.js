import React from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { AdminToastProvider } from '../components/AdminToast';
import AssistantWidget from '../components/AssistantWidget';

/**
 * WMS assistant configuration. Driven by the generic AssistantWidget so the
 * storefront and warehouse sides can share one component with two flavours.
 */
const wmsAssistantConfig = {
  profile: 'wms',
  endpoint: '/api/cezeri/chat',
  storagePrefix: 'cezeri_chat_v2:',
  authTokenKey: 'auth_token',
  requiresAuth: true,
  sendAllowMutations: false,
  withCredentials: false,
  title: 'Cezeri',
  subtitle: 'Yapay Zekâ Asistanı',
  unauthSubtitle: 'Giriş gerekli',
  unauthHint: 'Lütfen önce giriş yapın; böylece depo verilerinize güvenli şekilde erişebilirim.',
  placeholder: 'Cezeri’ye sor…',
  welcomeMessage:
    'Merhaba, ben Cezeri.\n\nStok sorgulama, ürün (Stok Kodu/isim) arama, düşük stokları inceleme, müşteri/depo bazlı stok sorgulama, depo/stok hareketlerini inceleme gibi konularda size yardımcı olabilirim.\n\nNe yapmak istersiniz?',
  hideOnPaths: ['/login'],
  getUserKey: (prefix) => {
    const user = (localStorage.getItem('auth_user') || '').trim();
    const role = (localStorage.getItem('auth_role') || '').trim();
    if (!user) return `${prefix}anon`;
    return `${prefix}${user.toLowerCase()}|${role || ''}`;
  },
};

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
        {/*
         * WMS assistant widget — admin-only tools, requires warehouse role.
         * The storefront uses the same component with a different config.
         */}
        <AssistantWidget config={wmsAssistantConfig} />
      </div>
    </AdminToastProvider>
  );
}
