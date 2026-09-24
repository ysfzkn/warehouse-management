import { useState, useEffect } from 'react';
import axios from 'axios';

// One request per page load: the footer, the brand page and the filter sidebar all need the
// same list, and brands change far less often than a visitor navigates.
let brandsPromise = null;

function loadBrands() {
  if (!brandsPromise) {
    brandsPromise = axios
      .get('/api/store/brands')
      .then((r) => (r.data || []).filter((b) => b.name))
      .catch((err) => {
        brandsPromise = null; // let a later mount retry instead of caching the failure
        throw err;
      });
  }
  return brandsPromise;
}

/** Brands with at least one product on the storefront. `failed` separates "none" from "could not load". */
export function useStoreBrands() {
  const [brands, setBrands] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    loadBrands()
      .then((list) => alive && setBrands(list))
      .catch(() => alive && setFailed(true))
      .finally(() => alive && setLoaded(true));
    return () => {
      alive = false;
    };
  }, []);

  return { brands, loaded, failed };
}
