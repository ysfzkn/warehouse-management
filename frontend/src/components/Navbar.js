import React from 'react';
import { Link, useLocation } from 'react-router-dom';

const Navbar = () => {
  const location = useLocation();

  const isActive = (path) => {
    return location.pathname === path ? 'active' : '';
  };

  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-primary">
      <div className="container-fluid">
        <Link className="navbar-brand" to="/">
          <i className="fas fa-warehouse me-2"></i>
          Depo Yönetim Sistemi
        </Link>

        <button
          className="navbar-toggler"
          type="button"
          data-bs-toggle="collapse"
          data-bs-target="#navbarNav"
          aria-controls="navbarNav"
          aria-expanded="false"
          aria-label="Toggle navigation"
        >
          <span className="navbar-toggler-icon"></span>
        </button>

        <div className="collapse navbar-collapse" id="navbarNav">
          <ul className="navbar-nav me-auto">
            <li className="nav-item">
              <Link className={`nav-link ${isActive('/')}`} to="/">
                <i className="fas fa-tachometer-alt me-1"></i>
                Dashboard
              </Link>
            </li>
            <li className="nav-item">
              <Link className={`nav-link ${isActive('/warehouses')}`} to="/warehouses">
                <i className="fas fa-building me-1"></i>
                Depolar
              </Link>
            </li>
            <li className="nav-item">
              <Link className={`nav-link ${isActive('/products')}`} to="/products">
                <i className="fas fa-box me-1"></i>
                Ürünler
              </Link>
            </li>
            <li className="nav-item">
              <Link className={`nav-link ${isActive('/categories')}`} to="/categories">
                <i className="fas fa-tags me-1"></i>
                Kategoriler
              </Link>
            </li>
            <li className="nav-item">
              <Link className={`nav-link ${isActive('/stock')}`} to="/stock">
                <i className="fas fa-cubes me-1"></i>
                Stok
              </Link>
            </li>
          </ul>

          <div className="d-flex">
            <span className="navbar-text me-3">
              <i className="fas fa-user me-1"></i>
              Admin
            </span>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
