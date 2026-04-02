import React, { useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import StoreHeader from '../components/store/StoreHeader';
import StoreFooter from '../components/store/StoreFooter';
import MobileNav from '../components/store/MobileNav';
import CartSidebar from '../components/store/CartSidebar';
import { ToastProvider } from '../components/store/Toast';
import { useCart } from '../hooks/useCart';
import { useSiteSettings } from '../hooks/useSiteSettings';

export default function StoreLayout() {
  const cart = useCart();
  const siteSettings = useSiteSettings();

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
      </div>
    </ToastProvider>
  );
}
