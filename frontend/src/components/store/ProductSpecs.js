import React from 'react';

export default function ProductSpecs({ product }) {
  const specs = [
    product.brandName && { label: 'Marka', value: product.brandName },
    product.colorName && { label: 'Renk', value: product.colorName },
    product.weight && { label: 'Ağırlık', value: `${product.weight} kg` },
    product.lengthCm && product.widthCm && product.heightCm && {
      label: 'Boyutlar', value: `${product.lengthCm} x ${product.widthCm} x ${product.heightCm} cm`
    },
    product.sku && { label: 'Ürün Kodu', value: product.sku },
  ].filter(Boolean);

  if (specs.length === 0) return null;
  return (
    <table className="store-specs-table w-100">
      <tbody>
        {specs.map((spec, i) => (
          <tr key={i}><td>{spec.label}</td><td>{spec.value}</td></tr>
        ))}
      </tbody>
    </table>
  );
}
