import React from 'react';
import { NavLink } from 'react-router-dom';
import { FiHome, FiGrid, FiShoppingCart, FiUser } from 'react-icons/fi';

export default function MobileNav({ cart }) {
  return (
    <nav className="store-mobile-nav" aria-label="Mobil navigasyon">
      <NavLink to="/" end className={({isActive}) => `store-mobile-nav-item ${isActive ? 'active' : ''}`}>
        <FiHome /><span>Anasayfa</span>
      </NavLink>
      <NavLink to="/kategori/tumu" className={({isActive}) => `store-mobile-nav-item ${isActive ? 'active' : ''}`}>
        <FiGrid /><span>Kategoriler</span>
      </NavLink>
      <NavLink to="/sepet" className={({isActive}) => `store-mobile-nav-item ${isActive ? 'active' : ''}`}>
        <div style={{position:'relative'}}>
          <FiShoppingCart />
          {cart.itemCount > 0 && <span className="store-cart-badge" style={{top:-6,right:-10,position:'absolute'}}>{cart.itemCount}</span>}
        </div>
        <span>Sepet</span>
      </NavLink>
      <NavLink to="/giris" className={({isActive}) => `store-mobile-nav-item ${isActive ? 'active' : ''}`}>
        <FiUser /><span>Hesap</span>
      </NavLink>
    </nav>
  );
}
