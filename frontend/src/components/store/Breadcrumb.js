import React from 'react';
import { Link } from 'react-router-dom';

export default function Breadcrumb({ items }) {
  return (
    <nav className="store-breadcrumb" aria-label="Sayfa izi">
      <Link to="/">Anasayfa</Link>
      {items.map((item, i) => (
        <React.Fragment key={i}>
          <span className="separator">/</span>
          {i < items.length - 1 ? (
            <Link to={item.href}>{item.label}</Link>
          ) : (
            <span className="current">{item.label}</span>
          )}
        </React.Fragment>
      ))}
    </nav>
  );
}
