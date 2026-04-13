import React from 'react';
import { Link } from 'react-router-dom';
import { FiPhone, FiMail, FiMapPin, FiInstagram, FiMessageCircle, FiFacebook } from 'react-icons/fi';

export default function StoreFooter({ settings }) {
  return (
    <footer className="store-footer">
      <div className="container">
        <div className="row g-4 g-lg-5">
          {/* Company Info */}
          <div className="col-lg-4 col-md-6">
            <h6 className="store-footer-title">{settings.get('site_name', 'Mağaza')}</h6>
            <ul className="store-footer-contact">
              {settings.get('contact_address') && (
                <li><FiMapPin size={14} /> <span>{settings.get('contact_address')}</span></li>
              )}
              {settings.get('contact_phone') && (
                <li><FiPhone size={14} /> <a href={`tel:${settings.get('contact_phone')}`}>{settings.get('contact_phone')}</a></li>
              )}
              {settings.get('contact_email') && (
                <li><FiMail size={14} /> <a href={`mailto:${settings.get('contact_email')}`}>{settings.get('contact_email')}</a></li>
              )}
            </ul>
            {/* Social */}
            <div className="store-footer-social">
              {settings.get('social_instagram') && (
                <a href={settings.get('social_instagram')} target="_blank" rel="noopener noreferrer" aria-label="Instagram"><FiInstagram size={18} /></a>
              )}
              {settings.get('social_facebook') && (
                <a href={settings.get('social_facebook')} target="_blank" rel="noopener noreferrer" aria-label="Facebook"><FiFacebook size={18} /></a>
              )}
              {settings.get('social_whatsapp') && (
                <a href={`https://wa.me/${settings.get('social_whatsapp').replace(/\D/g,'')}`} target="_blank" rel="noopener noreferrer" aria-label="WhatsApp"><FiMessageCircle size={18} /></a>
              )}
            </div>
          </div>

          {/* Corporate */}
          <div className="col-lg-2 col-md-6 col-6">
            <h6 className="store-footer-title">Kurumsal</h6>
            <ul className="store-footer-links">
              <li><Link to="/sayfa/hakkimizda">Hakkımızda</Link></li>
              <li><Link to="/sayfa/iletisim">İletişim</Link></li>
            </ul>
          </div>

          {/* Customer Service */}
          <div className="col-lg-3 col-md-6 col-6">
            <h6 className="store-footer-title">Müşteri Hizmetleri</h6>
            <ul className="store-footer-links">
              <li><Link to="/sayfa/iptal-ve-iade-sartlari">İptal ve İade</Link></li>
              <li><Link to="/sayfa/mesafeli-satis-sozlesmesi">Mesafeli Satış Sözleşmesi</Link></li>
              <li><Link to="/sayfa/gizlilik-ve-guvenlik">Gizlilik Politikası</Link></li>
              <li><Link to="/sayfa/kvkk-aydinlatma-metni">KVKK</Link></li>
            </ul>
          </div>

          {/* Account */}
          <div className="col-lg-3 col-md-6">
            <h6 className="store-footer-title">Hesabım</h6>
            <ul className="store-footer-links">
              <li><Link to="/giris">Giriş Yap</Link></li>
              <li><Link to="/kayit">Üye Ol</Link></li>
              <li><Link to="/sepet">Sepetim</Link></li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="store-footer-bottom">
          <p>{settings.get('footer_text', '© 2026 Tüm hakları saklıdır.')}</p>
        </div>
      </div>
    </footer>
  );
}
