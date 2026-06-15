import React from 'react';
import ProductCard from './ProductCard';

/**
 * A titled grid of product cards used for recommendation rails
 * ("Birlikte Alınanlar", "Son Gezdikleriniz" / "Benzer Ürünler").
 *
 * Renders nothing when there are no products, so callers can mount it
 * unconditionally. Mirrors the storefront's existing card grid layout.
 *
 * @param {string} title    section heading
 * @param {React.ReactNode} icon  optional leading icon
 * @param {Array}  products  product card DTOs
 * @param {(id:number)=>void} onAddToCart  add-to-cart handler
 */
export default function ProductRail({ title, icon, products, onAddToCart }) {
  if (!Array.isArray(products) || products.length === 0) return null;
  return (
    <section className="my-5">
      <h5 className="fw-bold mb-3 d-flex align-items-center">
        {icon}
        {title}
      </h5>
      <div className="row g-3 row-cols-2 row-cols-md-3 row-cols-lg-4">
        {products.map((p) => (
          <div key={p.id} className="col">
            <ProductCard product={p} onAddToCart={onAddToCart} />
          </div>
        ))}
      </div>
    </section>
  );
}
