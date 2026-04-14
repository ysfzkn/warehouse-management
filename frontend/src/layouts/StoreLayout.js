import React, { useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import StoreHeader from '../components/store/StoreHeader';
import StoreFooter from '../components/store/StoreFooter';
import MobileNav from '../components/store/MobileNav';
import CartSidebar from '../components/store/CartSidebar';
import { ToastProvider } from '../components/store/Toast';
import { WishlistProvider } from '../components/store/WishlistContext';
import { useCart } from '../hooks/useCart';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { useAssistantFlags } from '../hooks/useAssistantFlags';
import AssistantWidget from '../components/AssistantWidget';
import CookieBanner from '../components/store/CookieBanner';

/**
 * Storefront assistant configuration. Guest-friendly: no Authorization
 * header by default (the backend resolves the guest session cookie or the
 * customer JWT automatically). Rate limits + login prompts are handled by
 * the backend guard and rendered inline by the widget.
 */
const storeAssistantConfig = {
  profile: 'store',
  endpoint: '/api/store/assistant/chat',
  storagePrefix: 'store_assistant_v1:',
  authTokenKey: 'customer_token',
  requiresAuth: false,
  sendAllowMutations: false,
  withCredentials: true,
  title: 'Cezeri',
  subtitle: 'Alışveriş Asistanınız',
  placeholder: 'Ürün arayın, soru sorun…',
  welcomeMessage:
    'Merhaba, ben **Cezeri**. Alışverişinizde size yardımcı olmak için buradayım.\n\nÜrün arama, karşılaştırma, stok ve fiyat bilgisi, sipariş takibi veya iade/kargo sorularınızda yardımcı olabilirim. Nasıl başlayalım?',
  hideOnPaths: ['/odeme', '/odeme/sonuc'],
  getUserKey: (prefix) => {
    const token = (localStorage.getItem('customer_token') || '').trim();
    if (!token) return `${prefix}guest`;
    return `${prefix}${token.slice(-12)}`;
  },
};

export default function StoreLayout() {
  const cart = useCart();
  const siteSettings = useSiteSettings();
  const { flags: assistantFlags } = useAssistantFlags();

  // Dynamic favicon and title from site settings
  useEffect(() => {
    const faviconUrl = siteSettings.get('site_favicon_url', '');
    if (faviconUrl) {
      // Remove ALL existing favicon links to prevent conflicts
      document.querySelectorAll("link[rel*='icon']").forEach(el => el.remove());
      // Create fresh link with cache-bust
      const link = document.createElement('link');
      link.rel = 'icon';
      link.href = faviconUrl + (faviconUrl.includes('?') ? '&' : '?') + 'v=' + Date.now();
      document.head.appendChild(link);
    }
    const siteName = siteSettings.get('site_name', '');
    if (siteName) { document.title = siteName; }
  }, [siteSettings]);

  return (
    <ToastProvider>
      <WishlistProvider>
      <div
        className="store-layout"
        style={{
          '--store-primary': siteSettings.get('primary_color', '#2563eb'),
          '--store-secondary': siteSettings.get('secondary_color', '#1e40af'),
        }}
      >
        <a href="#main-content" className="skip-to-content">
          Ana içeriğe atla
        </a>
        <StoreHeader cart={cart} settings={siteSettings} />
        <main id="main-content" className="store-main">
          <Outlet context={{ cart, siteSettings }} />
        </main>
        <StoreFooter settings={siteSettings} />
        <MobileNav cart={cart} />
        <CartSidebar cart={cart} />
        {/*
         * Storefront AI shopping assistant. Same component as AdminLayout
         * with a store-flavoured config — guest-friendly, product-focused.
         * Controlled by the `assistant_store_enabled` site setting.
         */}
        {assistantFlags.storeEnabled === true && <AssistantWidget config={storeAssistantConfig} siteName={siteSettings.get('site_name', '')} />}
        <CookieBanner />
      </div>
      </WishlistProvider>
    </ToastProvider>
  );
}
