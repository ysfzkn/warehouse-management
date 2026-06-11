import React, { useEffect } from 'react';
import { Outlet, Navigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { AdminToastProvider } from '../components/AdminToast';
import AssistantWidget from '../components/AssistantWidget';
import { useAssistantFlags } from '../hooks/useAssistantFlags';
import { useSessionGuard } from '../hooks/useSessionGuard';
import { useSiteSettings } from '../hooks/useSiteSettings';
import SessionWarningModal from '../components/SessionWarningModal';

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
  const { flags } = useAssistantFlags();
  const siteSettings = useSiteSettings();

  // Dynamic browser-tab icon + title from site settings. Prefer a dedicated
  // favicon; if none was uploaded, fall back to the brand logo so the tab icon
  // automatically matches the admin-uploaded logo.
  const faviconUrl = siteSettings.get('site_favicon_url', '') || siteSettings.get('site_logo_url', '');
  const siteName = siteSettings.get('site_name', '');
  useEffect(() => {
    if (faviconUrl) {
      document.querySelectorAll("link[rel*='icon']").forEach((el) => el.remove());
      const link = document.createElement('link');
      link.rel = 'icon';
      link.href = faviconUrl + (faviconUrl.includes('?') ? '&' : '?') + 'v=' + Date.now();
      document.head.appendChild(link);
    }
    if (siteName) {
      document.title = siteName;
    }
  }, [faviconUrl, siteName]);

  // Session guard — warn 2 min before JWT expiry, idle logout after 30 min inactivity
  const { warningOpen, secondsLeft, dismiss, logoutNow } = useSessionGuard({
    tokenKey: 'auth_token',
    loginPath: '/login',
    idleMinutes: 30,
    warnBeforeSeconds: 120,
    extraKeysToClear: ['auth_user', 'auth_role'],
  });

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
        {flags.wmsEnabled === true && <AssistantWidget config={wmsAssistantConfig} />}
        <SessionWarningModal
          open={warningOpen}
          secondsLeft={secondsLeft}
          onContinue={dismiss}
          onLogout={() => logoutNow('manual')}
        />
      </div>
    </AdminToastProvider>
  );
}
