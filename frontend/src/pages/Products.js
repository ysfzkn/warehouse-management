import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import useSecurityCodePrompt from '../components/useSecurityCodePrompt';
import ProductForm from '../components/ProductForm';
import SearchableSelect from '../components/SearchableSelect';
import ConfirmModal from '../components/ConfirmModal';
import PaginationControls from '../components/PaginationControls';

const normalizeText = (text) => (text || '').toLocaleLowerCase('tr-TR');

const PAGE_SIZE_OPTIONS = [20, 50, 100, 250];

const Products = () => {
  const location = useLocation();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [initialLoad, setInitialLoad] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedSubcategory, setSelectedSubcategory] = useState('');
  const [subcategories, setSubcategories] = useState([]);
  const [selectedBrand, setSelectedBrand] = useState(null);
  const [selectedColor, setSelectedColor] = useState(null);
  const [selectedBrandOpt, setSelectedBrandOpt] = useState(null);
  const [selectedColorOpt, setSelectedColorOpt] = useState(null);
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [errorModal, setErrorModal] = useState({ show: false, title: '', message: '' });
  const [errorDetailsModal, setErrorDetailsModal] = useState({ show: false, title: '', errors: [] });
  const [showDetailedPrice, setShowDetailedPrice] = useState(true);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [bulkMode, setBulkMode] = useState('PERCENTAGE');
  const [bulkDirection, setBulkDirection] = useState('INCREASE');
  const [bulkValue, setBulkValue] = useState('');
  const [bulkOnlyActive, setBulkOnlyActive] = useState(true);
  const [selectedProducts, setSelectedProducts] = useState([]);
  const [productPage, setProductPage] = useState(0);
  const [productPageSize, setProductPageSize] = useState(100);
  const [productTotalPages, setProductTotalPages] = useState(0);
  const [productTotalCount, setProductTotalCount] = useState(0);
  const [isProductPaginated, setIsProductPaginated] = useState(false);
  const [filteredProducts, setFilteredProducts] = useState([]);
  const [productSortBy, setProductSortBy] = useState('name');
  const [productSortDir, setProductSortDir] = useState('asc');
  const handleProductSortChange = (value) => {
    setProductSortBy(value);
    if (value === 'updatedAt') {
      setProductSortDir('desc');
    }
    setProductPage(0);
  };
  const productSortOptions = [
    { value: 'name', label: 'İsme Göre', icon: 'fa-font' },
    { value: 'updatedAt', label: 'Son Güncellemeye Göre', icon: 'fa-clock' },
  ];
  const [categoryDropdownOpen, setCategoryDropdownOpen] = useState(false);
  const [subcategoryDropdownOpen, setSubcategoryDropdownOpen] = useState(false);
  const [categorySearch, setCategorySearch] = useState('');
  const [subcategorySearch, setSubcategorySearch] = useState('');
  const categoryDropdownRef = useRef(null);
  const subcategoryDropdownRef = useRef(null);
  const [highlightedProductId, setHighlightedProductId] = useState(null);
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const isAdmin = role === 'ADMIN';
  const {
    askCode: askSecurityCode,
    SecurityCodePrompt,
    closePrompt: closeSecurityPrompt,
  } = useSecurityCodePrompt();

  const adminSecurityErrorCodes = new Set([
    'AUTH_002',
    'AUTH_003',
    'AUTH_004',
    'AUTH_005',
    'AUTH_006',
    'AUTH_007',
    'ADMIN_SECURITY_CODE_REQUIRED',
    'INVALID_ADMIN_SECURITY_CODE',
    'ADMIN_SECURITY_CODE_MISMATCH',
  ]);

  const isAdminSecurityError = (code, status) =>
    adminSecurityErrorCodes.has(code) || status === 401 || status === 403;

  const parseSecurityError = (error) => {
    const data = error?.response?.data;
    const code = data?.code || data?.errorCode;
    const msg = data?.message || data?.error || error?.message || 'Beklenmeyen bir hata oluştu';
    const status = error?.response?.status;
    return { code, msg, status };
  };

  const requireAdminSecurityHeaders = async () => {
    if (!isAdmin) return {};
    const code = await askSecurityCode();
    if (code === null) {
      return null; // do not show an error when cancelled
    }
    return { 'X-ADMIN-SECURITY-CODE': code };
  };

  const fetchProducts = useCallback(
    async (pageOverride = 0, pageSizeOverride) => {
      try {
        setLoading(true);
        const size = pageSizeOverride ?? productPageSize;
        const params = {
          page: pageOverride,
          size,
          sortBy: productSortBy,
          sortDir: productSortDir,
          productType: 'SIMPLE', // sets (bundles) are managed on the Ürün Setleri page
        };

        const normalizedSearch = (searchTerm || '').trim();
        if (normalizedSearch) {
          params.search = normalizedSearch;
        }
        const categoryFilterId = selectedSubcategory || selectedCategory;
        if (categoryFilterId) {
          params.categoryId = categoryFilterId;
        }
        if (selectedBrand != null) {
          params.brandId = selectedBrand;
        }
        if (selectedColor != null) {
          params.colorId = selectedColor;
        }

        const response = await axios.get('/api/products', { params });
        const data = response.data || {};
        const list = Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : [];

        const productsWithStock = list.map((p) => ({
          ...p,
          totalStock: typeof p.totalQuantity === 'number' ? p.totalQuantity : Number(p.totalQuantity) || 0,
        }));
        setProducts(productsWithStock);

        // Update pagination state if response is paginated
        if (data.totalElements !== undefined) {
          setIsProductPaginated(true);
          setProductPage(data.page ?? pageOverride);
          setProductTotalCount(data.totalElements ?? list.length);
          setProductTotalPages(data.totalPages ?? 0);
        } else {
          // Non-paginated response - apply frontend filtering
          setIsProductPaginated(false);
          setProductPage(0);
          setProductTotalCount(list.length);
          setProductTotalPages(1);
        }
      } catch (error) {
        console.error('Error fetching products:', error);
        setError('Ürünler yüklenirken hata oluştu');
      } finally {
        setLoading(false);
        setInitialLoad(false);
      }
    },
    [
      productPageSize,
      productSortBy,
      productSortDir,
      searchTerm,
      selectedCategory,
      selectedSubcategory,
      selectedBrand,
      selectedColor,
    ]
  );

  useEffect(() => {
    fetchProducts(productPage, productPageSize);
  }, [fetchProducts, productPage, productPageSize]);

  useEffect(() => {
    fetchMainCategories();
  }, []);

  const handleProductPageSizeChange = async (e) => {
    const newSize = Number(e.target.value);
    setProductPageSize(newSize);
    setProductPage(0);
    try {
      await fetchProducts(0, newSize);
    } catch (error) {
      console.error('Error changing product page size:', error);
      setError('Ürün verileri yüklenirken hata oluştu');
    }
  };

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (categoryDropdownRef.current && !categoryDropdownRef.current.contains(event.target)) {
        setCategoryDropdownOpen(false);
      }
      if (subcategoryDropdownRef.current && !subcategoryDropdownRef.current.contains(event.target)) {
        setSubcategoryDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  // Reset to first page when filters or sort change
  useEffect(() => {
    if (productPage !== 0) {
      setProductPage(0);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    searchTerm,
    selectedCategory,
    selectedSubcategory,
    selectedBrand,
    selectedColor,
    productSortBy,
    productSortDir,
  ]);

  useEffect(() => {
    setSubcategoryDropdownOpen(false);
    setSubcategorySearch('');
  }, [selectedCategory]);

  useEffect(() => {
    if (selectedCategory) {
      fetchSubcategories(selectedCategory);
      setSelectedSubcategory(''); // Reset the subcategory when the main category changes
    } else {
      setSubcategories([]);
      setSelectedSubcategory('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCategory]);

  const selectedCategoryOption = categories.find((category) => category.id?.toString() === selectedCategory);
  const selectedCategoryLabel = selectedCategoryOption?.name || 'Tüm Ana Kategoriler';
  const normalizedCategoryQuery = normalizeText(categorySearch);
  const filteredCategoryOptions = categories.filter(
    (category) => !normalizedCategoryQuery || normalizeText(category.name).includes(normalizedCategoryQuery)
  );

  const selectedSubcategoryOption = subcategories.find((sub) => sub.id?.toString() === selectedSubcategory);
  const selectedSubcategoryLabel =
    selectedSubcategoryOption?.name || (selectedCategory ? 'Tüm Alt Kategoriler' : 'Önce ana kategori seçin');
  const normalizedSubcategoryQuery = normalizeText(subcategorySearch);
  const filteredSubcategoryOptions = subcategories.filter(
    (sub) => !normalizedSubcategoryQuery || normalizeText(sub.name).includes(normalizedSubcategoryQuery)
  );

  const handleCategorySelect = (value) => {
    setSelectedCategory(value);
    setSelectedSubcategory('');
    setCategoryDropdownOpen(false);
    setCategorySearch('');
  };

  const handleSubcategorySelect = (value) => {
    if (!selectedCategory) {
      return;
    }
    setSelectedSubcategory(value);
    setSubcategoryDropdownOpen(false);
    setSubcategorySearch('');
  };

  const clearCategorySelection = () => {
    handleCategorySelect('');
  };

  const clearSubcategorySelection = () => {
    handleSubcategorySelect('');
  };

  // Apply frontend filtering (since backend doesn't support all filters yet)
  useEffect(() => {
    if (isProductPaginated) {
      setFilteredProducts(products);
      return;
    }

    const normalizedSearch = normalizeText(searchTerm);
    const brandFilterId = selectedBrand != null ? Number(selectedBrand) : null;
    const colorFilterId = selectedColor != null ? Number(selectedColor) : null;
    const filtered = products.filter((product) => {
      const matchesSearch =
        !normalizedSearch ||
        normalizeText(product.name).includes(normalizedSearch) ||
        normalizeText(product.sku).includes(normalizedSearch);
      const categoryIdStr = product.category?.id != null ? product.category.id.toString() : '';
      const parentIdStr = product.category?.parent?.id != null ? product.category.parent.id.toString() : '';
      const matchesCategory =
        !selectedCategory || categoryIdStr === selectedCategory || parentIdStr === selectedCategory;
      const matchesSubcategory = !selectedSubcategory || categoryIdStr === selectedSubcategory;
      const matchesBrand = brandFilterId == null || Number(product.brand?.id) === brandFilterId;
      const matchesColor = colorFilterId == null || Number(product.color?.id) === colorFilterId;
      return matchesSearch && matchesCategory && matchesSubcategory && matchesBrand && matchesColor;
    });
    setFilteredProducts(filtered);
    // Update pagination based on filtered results
    if (filtered.length !== products.length) {
      setProductTotalCount(filtered.length);
      setProductTotalPages(Math.ceil(filtered.length / productPageSize));
    }
  }, [
    products,
    searchTerm,
    selectedCategory,
    selectedSubcategory,
    selectedBrand,
    selectedColor,
    productPageSize,
    isProductPaginated,
  ]);

  const fetchMainCategories = async () => {
    try {
      // Fetch ALL categories (no pagination on backend)
      const response = await axios.get('/api/categories/top-level');
      const data = response.data || {};
      // Handle paginated response
      const categoriesList = Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : [];
      const normalized = categoriesList.map((cat) => {
        const children = Array.isArray(cat.children)
          ? cat.children
          : Array.isArray(cat.subcategories)
            ? cat.subcategories
            : [];
        return { ...cat, children };
      });
      setCategories(normalized);
    } catch (error) {
      console.error('Error fetching main categories:', error);
      setCategories([]);
    }
  };

  const fetchSubcategories = async (parentId) => {
    try {
      const parent = categories.find((cat) => cat.id?.toString() === String(parentId));
      if (parent && Array.isArray(parent.children) && parent.children.length > 0) {
        setSubcategories(parent.children);
        return parent.children;
      }
      const response = await axios.get(`/api/categories/${parentId}/subcategories`);
      const data = response.data || {};
      const subcategoriesList = Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : [];
      setSubcategories(subcategoriesList);
      return subcategoriesList;
    } catch (error) {
      console.error('Error fetching subcategories:', error);
      setSubcategories([]);
      return [];
    }
  };

  const clearAllFilters = () => {
    setSearchTerm('');
    setSelectedCategory('');
    setSelectedSubcategory('');
    setSelectedBrand(null);
    setSelectedColor(null);
    setSelectedBrandOpt(null);
    setSelectedColorOpt(null);
  };

  const handleCreate = () => {
    setEditingProduct(null);
    setShowForm(true);
  };

  const handleEdit = (product) => {
    setEditingProduct(product);
    setShowForm(true);
  };

  // Deep link: /products?editId=NN opens that product's edit form directly
  // (used by the "Ürün Setleri" page to jump to a set member).
  const deepLinkRef = useRef(null);
  useEffect(() => {
    const editId = new URLSearchParams(location.search).get('editId');
    if (editId && deepLinkRef.current !== editId) {
      deepLinkRef.current = editId;
      axios
        .get(`/api/products/${editId}`)
        .then((r) => {
          if (r.data) {
            setEditingProduct(r.data);
            setShowForm(true);
          }
        })
        .catch(() => {});
    }
  }, [location.search]);

  const handleDelete = async (id) => {
    const product = products.find((p) => p.id === id);
    setConfirmModal({
      show: true,
      title: 'Ürün Silme',
      message: 'Bu ürünü silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });
        // Security code retry loop for admin
        if (isAdmin) {
          let lastCode = '';
          let lastErrorMsg = '';
          // eslint-disable-next-line no-constant-condition
          while (true) {
            const code = await askSecurityCode({
              prefill: lastCode,
              errorMessage: lastErrorMsg || (lastCode ? 'Güvenlik şifresi hatalı, tekrar deneyin.' : ''),
              persistOnResolve: true,
            });
            if (code === null) {
              closeSecurityPrompt();
              return;
            }
            lastCode = code;
            lastErrorMsg = '';

            try {
              await axios.delete(`/api/products/${id}`, { headers: { 'X-ADMIN-SECURITY-CODE': code } });
              closeSecurityPrompt();
              const toast = document.createElement('div');
              toast.className =
                'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show fs-6';
              toast.style.minWidth = '360px';
              toast.style.padding = '0.5rem 0.75rem';
              toast.setAttribute('role', 'alert');
              toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">Ürün başarıyla silindi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
              document.body.appendChild(toast);
              setTimeout(() => {
                try {
                  document.body.removeChild(toast);
                } catch {}
              }, 7000);
              fetchProducts(productPage, productPageSize);
              break;
            } catch (error) {
              const { code: errCode, msg, status } = parseSecurityError(error);
              lastErrorMsg = msg;

              const isSecurity = isAdminSecurityError(errCode, status);

              if (!isSecurity) {
                closeSecurityPrompt();
                // If the product is linked to a transfer, show a detailed warning
                if (msg.includes('transfer') || errCode === 'RELATION_004') {
                  setErrorModal({
                    show: true,
                    title: 'Ürün Silinemez',
                    message: product ? `${product.name} (SKU: ${product.sku}) - ${msg}` : msg,
                  });
                } else {
                  const toast = document.createElement('div');
                  toast.className =
                    'toast align-items-center text-bg-danger border-0 position-fixed top-0 end-0 m-3 show fs-6';
                  toast.style.minWidth = '360px';
                  toast.style.padding = '0.5rem 0.75rem';
                  toast.setAttribute('role', 'alert');
                  toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">${msg}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
                  document.body.appendChild(toast);
                  setTimeout(() => {
                    try {
                      document.body.removeChild(toast);
                    } catch {}
                  }, 7000);
                }
                fetchProducts(productPage, productPageSize);
                break;
              }
              // if it's a security error, keep the modal open, show the warning, and ask again
              const toast = document.createElement('div');
              toast.className =
                'toast align-items-center text-bg-danger border-0 position-fixed top-0 end-0 m-3 show fs-6';
              toast.style.minWidth = '360px';
              toast.style.padding = '0.5rem 0.75rem';
              toast.setAttribute('role', 'alert');
              toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">${msg || 'Güvenlik şifresi hatalı'}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
              document.body.appendChild(toast);
              setTimeout(() => {
                try {
                  document.body.removeChild(toast);
                } catch {}
              }, 7000);
              // if it's a security error, keep the modal open and let the loop continue
            }
          }
        } else {
          // If not admin, delete directly
          try {
            await axios.delete(`/api/products/${id}`);
            const toast = document.createElement('div');
            toast.className =
              'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show fs-6';
            toast.style.minWidth = '360px';
            toast.style.padding = '0.5rem 0.75rem';
            toast.setAttribute('role', 'alert');
            toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">Ürün başarıyla silindi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
            document.body.appendChild(toast);
            setTimeout(() => {
              try {
                document.body.removeChild(toast);
              } catch {}
            }, 7000);
          } catch (error) {
            const errorData = error?.response?.data;
            const msg =
              errorData?.message ||
              errorData?.error ||
              (typeof errorData === 'string' ? errorData : 'Ürün silinirken hata oluştu');
            if (msg.includes('transfer') || errorData?.code === 'RELATION_004') {
              setErrorModal({
                show: true,
                title: 'Ürün Silinemez',
                message: product ? `${product.name} (SKU: ${product.sku}) - ${msg}` : msg,
              });
            } else {
              const toast = document.createElement('div');
              toast.className =
                'toast align-items-center text-bg-danger border-0 position-fixed top-0 end-0 m-3 show fs-6';
              toast.style.minWidth = '360px';
              toast.style.padding = '0.5rem 0.75rem';
              toast.setAttribute('role', 'alert');
              toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">${msg}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
              document.body.appendChild(toast);
              setTimeout(() => {
                try {
                  document.body.removeChild(toast);
                } catch {}
              }, 7000);
            }
          } finally {
            fetchProducts(productPage, productPageSize);
          }
        }
      },
    });
  };

  const handleToggleActive = async (id, active) => {
    try {
      if (active) {
        await axios.put(`/api/products/${id}/deactivate`);
      } else {
        await axios.put(`/api/products/${id}/activate`);
      }
      fetchProducts();
    } catch (error) {
      const errorData = error?.response?.data;
      const message =
        errorData?.message ||
        errorData?.error ||
        (typeof errorData === 'string' ? errorData : 'Durum değiştirilirken beklenmeyen bir hata oluştu.');
      setErrorModal({
        show: true,
        title: 'Durum Güncelleme Hatası',
        message,
      });
    }
  };

  const handleFormSuccess = (data) => {
    setShowForm(false);
    setEditingProduct(null);
    fetchProducts();
    const msg = editingProduct ? 'Ürün başarıyla güncellendi.' : 'Ürün başarıyla oluşturuldu.';
    const t = document.createElement('div');
    t.className = 'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show';
    t.style.cssText = 'min-width:340px;z-index:9999;animation:fadeInDown 0.3s ease';
    t.setAttribute('role', 'alert');
    t.innerHTML = `<div class="d-flex align-items-center px-3 py-2"><i class="fas fa-check-circle me-2"></i><div class="toast-body fw-medium">${msg}</div><button type="button" class="btn-close btn-close-white ms-auto" onclick="this.parentElement.parentElement.remove()"></button></div>`;
    document.body.appendChild(t);
    setTimeout(() => {
      try {
        document.body.removeChild(t);
      } catch {}
    }, 4000);
  };

  const getTotalStockQuantity = (product) => {
    return product.stocks ? product.stocks.reduce((total, stock) => total + stock.quantity, 0) : 0;
  };

  const getLowStockCount = (product) => {
    return product.stocks
      ? product.stocks.filter((stock) => stock.quantity <= stock.minStockLevel).length
      : 0;
  };

  // Selection handlers
  const displayedProducts = isProductPaginated
    ? filteredProducts
    : filteredProducts.slice(productPage * productPageSize, (productPage + 1) * productPageSize);

  const allVisibleProductIds = displayedProducts.map((p) => p.id);
  const areAllVisibleSelected =
    displayedProducts.length > 0 && allVisibleProductIds.every((id) => selectedProducts.includes(id));
  const selectedProductCount = selectedProducts.length;

  const toggleSelectAllVisible = () => {
    if (!filteredProducts.length) return;
    if (areAllVisibleSelected) {
      setSelectedProducts((prev) => prev.filter((id) => !allVisibleProductIds.includes(id)));
    } else {
      setSelectedProducts((prev) => [...new Set([...prev, ...allVisibleProductIds])]);
    }
  };

  const toggleProductSelection = (id) => {
    setSelectedProducts((prev) =>
      prev.includes(id) ? prev.filter((existingId) => existingId !== id) : [...prev, id]
    );
  };

  const clearSelectedProducts = () => {
    setSelectedProducts([]);
  };

  // Handle highlightProduct parameter from URL (for direct navigation from stock audit)
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const highlightId = params.get('highlightProduct');
    if (!highlightId) return;

    const productId = parseInt(highlightId, 10);
    if (!Number.isFinite(productId)) return;

    let attempts = 0;
    const maxAttempts = 20;
    let clearHighlightTimeoutId = null;

    const applyHighlight = () => {
      setHighlightedProductId(productId);
      const el = document.getElementById(`product-${productId}`);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });

        // Clean URL once we successfully scrolled
        params.delete('highlightProduct');
        const newSearch = params.toString();
        const newUrl = newSearch ? `${location.pathname}?${newSearch}` : location.pathname;
        window.history.replaceState({}, '', newUrl);

        if (!clearHighlightTimeoutId) {
          clearHighlightTimeoutId = window.setTimeout(() => {
            setHighlightedProductId(null);
          }, 7000);
        }
        return true;
      }
      return false;
    };

    // Try immediately
    if (applyHighlight()) {
      return () => {
        if (clearHighlightTimeoutId) clearTimeout(clearHighlightTimeoutId);
      };
    }

    // Retry for a short period until row is rendered
    const intervalId = window.setInterval(() => {
      attempts += 1;
      const done = applyHighlight();
      if (done || attempts >= maxAttempts) {
        window.clearInterval(intervalId);
      }
    }, 300);

    return () => {
      window.clearInterval(intervalId);
      if (clearHighlightTimeoutId) clearTimeout(clearHighlightTimeoutId);
    };
  }, [location.pathname, location.search, filteredProducts.length]);

  const handleBatchDeleteProducts = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    setConfirmModal({
      show: true,
      title: 'Toplu Ürün Silme',
      message: `${ids.length} ürünü silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      icon: 'trash',
      confirmVariant: 'danger',
      confirmText: 'Evet, Sil',
      onConfirm: async () => {
        setConfirmModal({ show: false, title: '', message: '', onConfirm: null });

        try {
          // Use the backend bulk delete endpoint
          const headers = await requireAdminSecurityHeaders();
          const response = await axios.delete('/api/products/bulk', { data: ids, headers });
          const result = response.data;

          // Show a toast for successfully deleted products
          if (result.successCount > 0) {
            const toast = document.createElement('div');
            toast.className =
              'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show fs-6';
            toast.style.minWidth = '360px';
            toast.style.padding = '0.5rem 0.75rem';
            toast.setAttribute('role', 'alert');
            toast.innerHTML = `<div class="d-flex"><div class="toast-body fw-semibold">${result.successCount} ürün başarıyla silindi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
            document.body.appendChild(toast);
            setTimeout(() => {
              try {
                document.body.removeChild(toast);
              } catch {}
            }, 7000);
          }

          // If there are products that errored, show a detailed error list
          if (result.errors && result.errors.length > 0) {
            const formattedErrors = result.errors.map((err) => ({
              productName: err.name || 'Bilinmeyen Ürün',
              sku: err.sku || 'N/A',
              error: err.errorMessage || 'Bilinmeyen hata',
              errorCode: err.errorCode || null,
            }));

            setErrorDetailsModal({
              show: true,
              title: 'Silinemeyen Ürünler',
              errors: formattedErrors,
            });
          }

          // Clear selected products (remove only the successfully deleted ones, keep the errored ones selected)
          const errorIds = new Set((result.errors || []).map((err) => err.id));
          setSelectedProducts((prev) =>
            prev.filter((id) => {
              // If this ID is among the IDs we tried to delete
              if (ids.includes(id)) {
                // Keep errored products selected, remove the successfully deleted ones
                return errorIds.has(id);
              }
              // Keep other products as they are
              return true;
            })
          );
        } catch (error) {
          if (error?.message?.startsWith('ADMIN_SECURITY')) return;
          // Backend error (e.g. network error)
          const errorData = error?.response?.data;
          const msg =
            errorData?.message || errorData?.error || error.message || 'Ürünler silinirken hata oluştu';
          setErrorModal({
            show: true,
            title: 'Toplu Silme Hatası',
            message: msg,
          });
        } finally {
          // Refresh the list
          fetchProducts(productPage, productPageSize);
        }
      },
    });
  };

  useEffect(() => {
    if (!selectedProducts.length) return;
    setSelectedProducts((prev) => prev.filter((id) => products.some((product) => product.id === id)));
  }, [products, selectedProducts.length]);

  if (initialLoad && loading) {
    return (
      <div className="text-center">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger" role="alert">
        {error}
      </div>
    );
  }

  return (
    <div>
      <style>{`
        .stock-highlighted {
          animation: highlightPulse 2s ease-in-out infinite;
          transition: all 0.3s ease;
        }
        @keyframes highlightPulse {
          0%, 100% {
            background-color: rgba(59, 130, 246, 0.08);
          }
          50% {
            background-color: rgba(59, 130, 246, 0.18);
          }
        }
        .loading-overlay {
          position: fixed;
          inset: 0;
          background: rgba(255,255,255,0.6);
          backdrop-filter: blur(2px);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 2000;
        }
        .filter-dropdown-card {
          position: relative;
          border: 1px solid rgba(15, 23, 42, 0.05);
          transition: box-shadow 0.2s ease, border-color 0.2s ease;
        }
        .filter-dropdown-card:hover {
          border-color: rgba(59, 130, 246, 0.4);
          box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
        }
        .filter-dropdown-toggle {
          width: 100%;
          border: 1px solid rgba(15, 23, 42, 0.08);
          border-radius: 16px;
          background: linear-gradient(135deg, #f8fafc, #eef2ff);
          padding: 0.9rem 1rem;
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 0.5rem;
          transition: all 0.2s ease;
        }
        .filter-dropdown-toggle span.label {
          font-size: 0.7rem;
          letter-spacing: 0.08em;
          color: #94a3b8;
          text-transform: uppercase;
          margin-bottom: 0.1rem;
          display: block;
        }
        .filter-dropdown-toggle strong {
          font-size: 0.95rem;
          color: #0f172a;
        }
        .filter-dropdown-toggle:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }
        .filter-dropdown-toggle.active {
          border-color: #3b82f6;
          background: linear-gradient(135deg, #e0f2fe, #dbeafe);
        }
        .filter-dropdown-menu {
          position: absolute;
          top: calc(100% + 12px);
          left: 0;
          right: 0;
          background: #fff;
          border-radius: 18px;
          border: 1px solid rgba(15,23,42,0.08);
          box-shadow: 0 20px 45px rgba(15, 23, 42, 0.15);
          padding: 0.75rem;
          z-index: 1050;
        }
        .filter-dropdown-list {
          max-height: 260px;
          overflow-y: auto;
          padding-right: 4px;
        }
        .filter-dropdown-item {
          width: 100%;
          border: 1px solid transparent;
          background: transparent;
          border-radius: 12px;
          padding: 0.6rem 0.75rem;
          display: flex;
          align-items: center;
          justify-content: space-between;
          color: #0f172a;
          transition: all 0.15s ease;
        }
        .filter-dropdown-item:hover {
          background: #f1f5f9;
        }
        .filter-dropdown-item.active {
          background: #e0f2fe;
          border-color: #bae6fd;
          color: #0c4a6e;
          font-weight: 600;
        }
        .filter-dropdown-item .badge {
          font-size: 0.7rem;
        }
        @media (max-width: 768px) {
          .filter-dropdown-menu {
            top: calc(100% + 8px);
          }
        }
        .page-header {
          gap: 1.5rem;
        }
        .page-title h2 {
          font-weight: 600;
          letter-spacing: -0.02em;
        }
        .page-title p {
          color: #94a3b8;
          font-size: 0.9rem;
          margin-bottom: 0;
        }
        .page-actions {
          display: flex;
          flex-wrap: wrap;
          gap: 0.75rem;
        }
        @media (max-width: 767px) {
          .page-actions .btn {
            width: 100%;
          }
        }
        .mobile-selection-toolbar {
          border: 1px solid rgba(15, 23, 42, 0.08);
          border-radius: 18px;
          padding: 0.75rem 1rem;
          background: #fff;
          box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
        }
        .mobile-selection-toolbar .btn {
          min-width: 110px;
        }
        .product-mobile-card .mobile-product-status {
          font-size: 0.78rem;
          border-radius: 999px;
          padding: 0.3rem 0.9rem;
          min-width: 84px;
          text-align: center;
          line-height: 1.2;
        }
        .product-mobile-card .mobile-product-sku {
          font-size: 0.85rem;
          padding: 0.4rem 0.9rem;
          border-radius: 999px;
          font-weight: 600;
        }
        .product-mobile-card .stat-tile {
          min-height: 110px;
          display: flex;
          flex-direction: column;
          justify-content: center;
        }
      `}</style>
      <div className="page-header d-flex flex-column flex-lg-row align-items-lg-center justify-content-between mb-4">
        <div className="page-title">
          <h2 className="mb-1">Ürünler</h2>
          <p className="small">Envanterdeki tüm ürünleri izleyin ve yönetin.</p>
        </div>
        <div className="page-actions">
          <button
            className="btn btn-outline-secondary btn-sm"
            onClick={() => setShowBulkModal(true)}
            title="Toplu Fiyat Güncelle"
          >
            <i className="fas fa-percent me-2"></i>
            Toplu Fiyat
          </button>
          <button
            className={`btn btn-sm ${showDetailedPrice ? 'btn-primary' : 'btn-outline-primary'}`}
            onClick={() => setShowDetailedPrice(!showDetailedPrice)}
            title={showDetailedPrice ? 'Basit Görünüm' : 'Detaylı Görünüm'}
          >
            <i className={`fas ${showDetailedPrice ? 'fa-eye-slash' : 'fa-calculator'} me-1`}></i>
            {showDetailedPrice ? 'Basit Fiyat' : 'Detaylı Fiyat'}
          </button>
          <button className="btn btn-primary" onClick={handleCreate}>
            <i className="fas fa-plus me-2"></i>
            Yeni Ürün
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="row g-3 mb-4 align-items-stretch">
        <div className="col-12 col-md-6 col-xl-3">
          <div className="border rounded-3 p-3 h-100 bg-body">
            <div className="d-flex align-items-center justify-content-between mb-5">
              <div>
                <small className="text-uppercase text-muted fw-semibold">Arama</small>
                <div className="fw-semibold text-truncate">Ürün / SKU</div>
              </div>
              <span className="badge text-bg-light border d-inline-flex align-items-center">
                <i className="fas fa-search me-1 text-muted"></i>
                Ara
              </span>
            </div>
            <div className="input-group">
              <span className="input-group-text bg-transparent border-end-0">
                <i className="fas fa-search text-secondary"></i>
              </span>
              <input
                type="text"
                className="form-control border-start-0"
                placeholder="Ürün adı veya Stok Kodu ara..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
              />
            </div>
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div className="border rounded-3 p-3 h-100 bg-body filter-dropdown-card" ref={categoryDropdownRef}>
            <div className="d-flex align-items-center justify-content-between mb-4">
              <div>
                <small className="text-uppercase text-muted fw-semibold">Filtre</small>
                <div className="fw-semibold text-truncate">Ana kategori</div>
              </div>
              <i className="fas fa-layer-group text-muted"></i>
            </div>
            <button
              type="button"
              className={`filter-dropdown-toggle ${categoryDropdownOpen ? 'active' : ''}`}
              onClick={() => setCategoryDropdownOpen((prev) => !prev)}
            >
              <div className="text-start flex-grow-1">
                <span className="label">Seçim</span>
                <strong>{selectedCategoryLabel}</strong>
              </div>
              <div className="text-muted">
                <i className={`fas fa-chevron-${categoryDropdownOpen ? 'up' : 'down'}`}></i>
              </div>
            </button>
            {categoryDropdownOpen && (
              <div className="filter-dropdown-menu">
                <div className="input-group input-group-sm mb-4">
                  <span className="input-group-text border-end-0 bg-transparent">
                    <i className="fas fa-search text-muted"></i>
                  </span>
                  <input
                    type="text"
                    className="form-control border-start-0"
                    placeholder="Kategori ara..."
                    value={categorySearch}
                    onChange={(e) => setCategorySearch(e.target.value)}
                  />
                  {selectedCategory && (
                    <button
                      type="button"
                      className="btn btn-link text-decoration-none"
                      onClick={clearCategorySelection}
                    >
                      Temizle
                    </button>
                  )}
                </div>
                <div className="filter-dropdown-list">
                  <button
                    type="button"
                    className={`filter-dropdown-item ${!selectedCategory ? 'active' : ''}`}
                    onClick={clearCategorySelection}
                  >
                    <span>Tüm Ana Kategoriler</span>
                    {!selectedCategory && <i className="fas fa-check"></i>}
                  </button>
                  {filteredCategoryOptions.map((category) => (
                    <button
                      type="button"
                      key={category.id}
                      className={`filter-dropdown-item ${selectedCategory === String(category.id) ? 'active' : ''}`}
                      onClick={() => handleCategorySelect(String(category.id))}
                    >
                      <span className="text-truncate">{category.name}</span>
                      {selectedCategory === String(category.id) && <i className="fas fa-check"></i>}
                    </button>
                  ))}
                  {!filteredCategoryOptions.length && (
                    <div className="text-muted small px-1 py-2">Sonuç bulunamadı.</div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div
            className="border rounded-3 p-3 h-100 bg-body filter-dropdown-card"
            ref={subcategoryDropdownRef}
          >
            <div className="d-flex align-items-center justify-content-between mb-4">
              <div>
                <small className="text-uppercase text-muted fw-semibold">Filtre</small>
                <div className="fw-semibold text-truncate">Alt kategori</div>
              </div>
              <i className="fas fa-diagram-project text-muted"></i>
            </div>
            <button
              type="button"
              className={`filter-dropdown-toggle ${subcategoryDropdownOpen ? 'active' : ''}`}
              onClick={() => selectedCategory && setSubcategoryDropdownOpen((prev) => !prev)}
              disabled={!selectedCategory}
            >
              <div className="text-start flex-grow-1">
                <span className="label">Seçim</span>
                <strong>{selectedSubcategoryLabel}</strong>
              </div>
              <div className="text-muted">
                <i className={`fas fa-chevron-${subcategoryDropdownOpen ? 'up' : 'down'}`}></i>
              </div>
            </button>
            {subcategoryDropdownOpen && (
              <div className="filter-dropdown-menu">
                {selectedCategory ? (
                  <>
                    <div className="input-group input-group-sm mb-2">
                      <span className="input-group-text border-end-0 bg-transparent">
                        <i className="fas fa-search text-muted"></i>
                      </span>
                      <input
                        type="text"
                        className="form-control border-start-0"
                        placeholder="Alt kategori ara..."
                        value={subcategorySearch}
                        onChange={(e) => setSubcategorySearch(e.target.value)}
                      />
                      {selectedSubcategory && (
                        <button
                          type="button"
                          className="btn btn-link text-decoration-none"
                          onClick={clearSubcategorySelection}
                        >
                          Temizle
                        </button>
                      )}
                    </div>
                    <div className="filter-dropdown-list">
                      <button
                        type="button"
                        className={`filter-dropdown-item ${!selectedSubcategory ? 'active' : ''}`}
                        onClick={clearSubcategorySelection}
                      >
                        <span>Tüm Alt Kategoriler</span>
                        {!selectedSubcategory && <i className="fas fa-check"></i>}
                      </button>
                      {filteredSubcategoryOptions.map((subcategory) => (
                        <button
                          type="button"
                          key={subcategory.id}
                          className={`filter-dropdown-item ${selectedSubcategory === String(subcategory.id) ? 'active' : ''}`}
                          onClick={() => handleSubcategorySelect(String(subcategory.id))}
                        >
                          <span className="text-truncate">{subcategory.name}</span>
                          {selectedSubcategory === String(subcategory.id) && <i className="fas fa-check"></i>}
                        </button>
                      ))}
                      {!filteredSubcategoryOptions.length && (
                        <div className="text-muted small px-1 py-2">Alt kategori bulunamadı.</div>
                      )}
                    </div>
                  </>
                ) : (
                  <div className="text-muted small">Önce ana kategori seçiniz.</div>
                )}
              </div>
            )}
          </div>
        </div>
        <div className="col-12 col-md-6 col-xl-3">
          <div className="border rounded-3 p-3 h-100 bg-body">
            <div className="d-flex justify-content-between align-items-center mb-4">
              <div>
                <small className="text-uppercase text-muted fw-semibold">Sıralama</small>
                <div className="fw-semibold text-truncate">
                  {productSortOptions.find((opt) => opt.value === productSortBy)?.label}
                </div>
              </div>
              <button
                type="button"
                className="btn btn-sm btn-outline-primary rounded-circle"
                onClick={() => {
                  setProductSortDir(productSortDir === 'asc' ? 'desc' : 'asc');
                  setProductPage(0);
                }}
                title={productSortDir === 'asc' ? 'Artan sıralama' : 'Azalan sıralama'}
              >
                <i className={`fas fa-arrow-${productSortDir === 'asc' ? 'up' : 'down'}`}></i>
              </button>
            </div>
            <div className="d-flex flex-wrap gap-2">
              {productSortOptions.map((option) => (
                <div key={option.value} className="flex-grow-1">
                  <input
                    type="radio"
                    className="btn-check"
                    name="product-sort-option"
                    id={`product-sort-${option.value}`}
                    checked={productSortBy === option.value}
                    onChange={() => handleProductSortChange(option.value)}
                  />
                  <label
                    className={`btn btn-sm w-100 d-flex align-items-center justify-content-center gap-2 ${
                      productSortBy === option.value ? 'btn-primary text-white' : 'btn-outline-primary'
                    }`}
                    htmlFor={`product-sort-${option.value}`}
                    style={{ minHeight: '38px' }}
                  >
                    <i className={`fas ${option.icon}`}></i>
                    <span className="text-truncate">{option.label}</span>
                  </label>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {filteredProducts.length > 0 && (
        <div className="mobile-selection-toolbar d-lg-none mb-3">
          <div className="d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div className="fw-semibold">Seçili ürün: {selectedProductCount}</div>
            <div className="d-flex flex-wrap gap-2">
              <button
                type="button"
                className="btn btn-sm btn-outline-primary"
                onClick={toggleSelectAllVisible}
              >
                {areAllVisibleSelected ? 'Seçimi Kaldır' : 'Tümünü Seç'}
              </button>
              {selectedProductCount > 0 && (
                <>
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={clearSelectedProducts}
                  >
                    Seçimi Temizle
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger"
                    onClick={() => handleBatchDeleteProducts([...selectedProducts])}
                  >
                    Seçilileri Sil
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      <div className="row mb-4">
        <div className="col-md-6">
          <SearchableSelect
            label="Marka Filtresi"
            value={selectedBrand}
            onChange={(id, opt) => {
              const parsed = id != null ? Number(id) : null;
              setSelectedBrand(Number.isNaN(parsed) ? null : parsed);
              setSelectedBrandOpt(opt || null);
            }}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
            allowClear={true}
            clearText="Temizle"
          />
        </div>
        <div className="col-md-6">
          <SearchableSelect
            label="Renk Filtresi"
            value={selectedColor}
            onChange={(id, opt) => {
              const parsed = id != null ? Number(id) : null;
              setSelectedColor(Number.isNaN(parsed) ? null : parsed);
              setSelectedColorOpt(opt || null);
            }}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
            allowClear={true}
            clearText="Temizle"
          />
        </div>
      </div>

      {/* Active Filters Chips */}
      {(searchTerm || selectedCategory || selectedSubcategory || selectedBrand || selectedColor) && (
        <div className="mb-3 d-flex flex-wrap align-items-center gap-2">
          <span className="text-muted me-1">Aktif filtreler:</span>
          {searchTerm && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-search me-1"></i> Arama: "{searchTerm}"
              <button
                type="button"
                className="btn btn-sm btn-link ms-2 p-0"
                onClick={() => setSearchTerm('')}
              >
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedCategory && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-tag me-1"></i> Ana Kategori:{' '}
              {categories.find((c) => c.id.toString() === selectedCategory)?.name || selectedCategory}
              <button
                type="button"
                className="btn btn-sm btn-link ms-2 p-0"
                onClick={() => setSelectedCategory('')}
              >
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedSubcategory && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-tags me-1"></i> Alt Kategori:{' '}
              {subcategories.find((c) => c.id.toString() === selectedSubcategory)?.name ||
                selectedSubcategory}
              <button
                type="button"
                className="btn btn-sm btn-link ms-2 p-0"
                onClick={() => setSelectedSubcategory('')}
              >
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedBrand && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-copyright me-1"></i> Marka: {selectedBrandOpt?.name || selectedBrand}
              <button
                type="button"
                className="btn btn-sm btn-link ms-2 p-0"
                onClick={() => {
                  setSelectedBrand(null);
                  setSelectedBrandOpt(null);
                }}
              >
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          {selectedColor && (
            <span className="badge text-bg-light border d-flex align-items-center">
              <i className="fas fa-palette me-1"></i> Renk: {selectedColorOpt?.name || selectedColor}
              <button
                type="button"
                className="btn btn-sm btn-link ms-2 p-0"
                onClick={() => {
                  setSelectedColor(null);
                  setSelectedColorOpt(null);
                }}
              >
                <i className="fas fa-times"></i>
              </button>
            </span>
          )}
          <button type="button" className="btn btn-sm btn-outline-secondary ms-1" onClick={clearAllFilters}>
            Tümünü Temizle
          </button>
        </div>
      )}

      {/* Products List */}
      <div className="card">
        <div className="card-body">
          {selectedProductCount > 0 && (
            <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div className="fw-semibold">
                <i className="fas fa-check-square me-2"></i>
                {selectedProductCount} ürün seçildi
              </div>
              <div className="d-flex gap-2 flex-wrap">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={clearSelectedProducts}
                >
                  Seçimi Temizle
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={() => handleBatchDeleteProducts([...selectedProducts])}
                >
                  <i className="fas fa-trash me-1"></i>
                  Seçilileri Sil
                </button>
              </div>
            </div>
          )}
          {/* Desktop Table View */}
          <div className="d-none d-lg-block">
            <div className="d-flex justify-content-end align-items-center mb-2 gap-2 flex-wrap">
              <div className="page-size-control d-flex align-items-center flex-wrap">
                <span className="form-label mb-0 small text-muted">Sayfa Boyutu</span>
                <select
                  className="form-select form-select-sm page-size-select"
                  value={productPageSize}
                  onChange={handleProductPageSizeChange}
                >
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <option key={`product-page-${size}`} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead className="table-light">
                  <tr>
                    <th className="text-center" style={{ width: '40px' }}>
                      <div className="form-check mb-0">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          checked={areAllVisibleSelected}
                          onChange={toggleSelectAllVisible}
                          disabled={filteredProducts.length === 0}
                          aria-label="Tümünü seç"
                        />
                      </div>
                    </th>
                    <th>Ürün Adı</th>
                    <th>Stok Kodu</th>
                    <th>Kategori</th>
                    <th>Marka</th>
                    <th>Renk</th>
                    <th className="text-end">Fiyat</th>
                    <th className="text-center">Stok</th>
                    <th className="text-center">Durum</th>
                    <th className="text-center" style={{ width: '280px' }}>
                      İşlemler
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {displayedProducts.map((product) => {
                    const calculateTotalPrice = () => {
                      const sctAmount = (product.price * (product.sctRate || 0)) / 100;
                      const priceWithSct = product.price + sctAmount;
                      const vatAmount = (priceWithSct * (product.vatRate || 0)) / 100;
                      return priceWithSct + vatAmount;
                    };
                    const totalPrice = calculateTotalPrice();
                    const w = product.widthCm || 0;
                    const l = product.lengthCm || 0;
                    const h = product.heightCm || 0;
                    const desi = (h * w * l) / 3000;
                    const shippingCost = desi * (product.shippingRate || 0);

                    const isSelected = selectedProducts.includes(product.id);

                    return (
                      <tr
                        key={product.id}
                        id={`product-${product.id}`}
                        className={`${isSelected ? 'table-active' : ''} ${highlightedProductId === product.id ? 'stock-highlighted' : ''}`}
                      >
                        <td className="text-center align-middle">
                          <div className="form-check mb-0">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleProductSelection(product.id)}
                              aria-label="Ürün seç"
                            />
                          </div>
                        </td>
                        <td>
                          <div className="fw-semibold">{product.name}</div>
                          {product.description && (
                            <small className="text-muted d-block mt-1" style={{ maxWidth: '300px' }}>
                              {product.description.length > 80
                                ? product.description.substring(0, 80) + '...'
                                : product.description}
                            </small>
                          )}
                        </td>
                        <td>
                          <div className="d-flex flex-wrap align-items-center gap-2">
                            <span className="badge text-bg-light border">
                              <i className="fas fa-barcode me-1"></i>
                              {product.sku}
                            </span>
                            {product.sku && (
                              <button
                                type="button"
                                className="btn btn-sm btn-outline-secondary"
                                style={{ fontSize: '0.7rem', padding: '0.1rem 0.45rem' }}
                                onClick={() => {
                                  if (navigator.clipboard?.writeText) {
                                    navigator.clipboard.writeText(product.sku).catch(() => {});
                                  }
                                }}
                                title="Stok kodunu kopyala"
                              >
                                <i className="fas fa-copy me-1"></i>
                                Kopyala
                              </button>
                            )}
                          </div>
                        </td>
                        <td>
                          {product.category?.name ? (
                            <span className="badge text-bg-light border">
                              <i className="fas fa-tag me-1"></i>
                              {product.category.parentName ? `${product.category.parentName} > ` : ''}
                              {product.category.name}
                            </span>
                          ) : (
                            <span className="text-muted">-</span>
                          )}
                        </td>
                        <td>
                          {product.brand?.name ? (
                            <span className="badge text-bg-light border">
                              <i className="fas fa-copyright me-1"></i>
                              {product.brand.name}
                            </span>
                          ) : (
                            <span className="text-muted">-</span>
                          )}
                        </td>
                        <td>
                          {product.color?.name ? (
                            <span className="badge text-bg-light border">
                              <i className="fas fa-palette me-1"></i>
                              {product.color.name}
                            </span>
                          ) : (
                            <span className="text-muted">-</span>
                          )}
                        </td>
                        <td className="text-end">
                          <div className="d-flex flex-column align-items-end">
                            <strong className="text-success">
                              ₺{totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                            </strong>
                            {showDetailedPrice && (product.vatRate > 0 || product.sctRate > 0) && (
                              <small className="text-muted">
                                Ana: ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                              </small>
                            )}
                            {(product.vatRate > 0 || product.sctRate > 0) && (
                              <small className="text-info">
                                {product.vatRate > 0 && `KDV %${product.vatRate}`}
                                {product.vatRate > 0 && product.sctRate > 0 && ' + '}
                                {product.sctRate > 0 && `ÖTV %${product.sctRate}`}
                              </small>
                            )}
                          </div>
                        </td>
                        <td className="text-center">
                          <div className="d-flex flex-column align-items-center gap-1">
                            <span className="fw-bold">
                              <i className="fas fa-cubes me-1"></i>
                              {product.totalStock ?? getTotalStockQuantity(product)}
                            </span>
                            {getLowStockCount(product) > 0 && (
                              <span className="badge bg-warning text-dark">
                                {getLowStockCount(product)} Düşük
                              </span>
                            )}
                            {desi > 0 && <small className="text-muted">Desi: {desi.toFixed(2)}</small>}
                            {shippingCost > 0 && (
                              <small className="text-muted">Kargo: ₺{shippingCost.toFixed(2)}</small>
                            )}
                          </div>
                        </td>
                        <td className="text-center">
                          <span
                            className={`badge ${product.active === false ? 'bg-secondary' : 'bg-success'}`}
                          >
                            {product.active === false ? 'Pasif' : 'Aktif'}
                          </span>
                        </td>
                        <td className="text-center">
                          <div className="d-flex justify-content-center">
                            <div className="btn-group" role="group">
                              <button
                                className="btn btn-outline-secondary"
                                onClick={() => handleEdit(product)}
                                title="Düzenle"
                                style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                              >
                                <i className="fas fa-edit"></i>
                              </button>
                              <button
                                className={`btn ${product.active === false ? 'btn-outline-success' : 'btn-outline-warning'}`}
                                onClick={() =>
                                  handleToggleActive(product.id, product.active === false ? false : true)
                                }
                                title={product.active === false ? 'Aktifleştir' : 'Pasifleştir'}
                                style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                              >
                                <i className={`fas ${product.active === false ? 'fa-play' : 'fa-pause'}`}></i>
                              </button>
                              <button
                                className="btn btn-outline-primary"
                                onClick={() => window.location.assign(`/desi?productId=${product.id}`)}
                                title="Desi Hesapla"
                                style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                              >
                                <i className="fas fa-calculator"></i>
                              </button>
                              <button
                                className="btn btn-outline-danger"
                                onClick={() => handleDelete(product.id)}
                                title="Sil"
                                style={{ minWidth: '45px', padding: '0.5rem 0.75rem' }}
                              >
                                <i className="fas fa-trash"></i>
                              </button>
                            </div>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mobile Card View */}
          <div className="d-lg-none">
            <div className="d-flex justify-content-end align-items-center mb-2 gap-2 flex-wrap">
              <div className="page-size-control d-flex align-items-center flex-wrap">
                <span className="form-label mb-0 small text-muted">Sayfa Boyutu</span>
                <select
                  className="form-select form-select-sm page-size-select"
                  value={productPageSize}
                  onChange={handleProductPageSizeChange}
                >
                  {PAGE_SIZE_OPTIONS.map((size) => (
                    <option key={`product-page-mobile-${size}`} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="d-flex flex-column gap-3">
              {displayedProducts.map((product) => {
                const calculateTotalPrice = () => {
                  const sctAmount = (product.price * (product.sctRate || 0)) / 100;
                  const priceWithSct = product.price + sctAmount;
                  const vatAmount = (priceWithSct * (product.vatRate || 0)) / 100;
                  return priceWithSct + vatAmount;
                };
                const totalPrice = calculateTotalPrice();
                const w = product.widthCm || 0;
                const l = product.lengthCm || 0;
                const h = product.heightCm || 0;
                const desi = (h * w * l) / 3000;
                const shippingCost = desi * (product.shippingRate || 0);
                const isSelected = selectedProducts.includes(product.id);

                return (
                  <div
                    key={product.id}
                    className={`product-mobile-card card border shadow-sm ${isSelected ? 'border-primary bg-primary bg-opacity-10' : ''}`}
                  >
                    <div className="card-body p-3">
                      {/* Header with checkbox and name */}
                      <div className="d-flex justify-content-between align-items-start mb-3 gap-3">
                        <div className="d-flex align-items-start gap-2 flex-grow-1">
                          <div className="form-check mt-1">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => toggleProductSelection(product.id)}
                              aria-label="Ürün seç"
                            />
                          </div>
                          <div className="w-100">
                            <div className="d-flex justify-content-between align-items-start gap-2">
                              <div className="fw-bold mb-1" style={{ fontSize: '1.05rem' }}>
                                {product.name}
                              </div>
                              <span
                                className={`badge mobile-product-status ${
                                  product.active === false
                                    ? 'bg-secondary text-light'
                                    : 'bg-success text-white'
                                }`}
                              >
                                {product.active === false ? 'Pasif' : 'Aktif'}
                              </span>
                            </div>
                            <div className="d-flex flex-wrap gap-1 align-items-center mb-1">
                              <span className="badge bg-light text-dark border mobile-product-sku">
                                <i className="fas fa-barcode me-1"></i>
                                {product.sku}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                      {product.description && (
                        <small className="text-muted d-block mb-2">
                          {product.description.length > 60
                            ? `${product.description.substring(0, 60)}...`
                            : product.description}
                        </small>
                      )}
                      <div className="d-flex flex-wrap gap-1 align-items-center mb-2">
                        {product.category?.name && (
                          <span
                            className="badge bg-info bg-opacity-10 text-info border border-info"
                            style={{ fontSize: '0.7rem' }}
                          >
                            <i className="fas fa-tag me-1"></i>
                            {product.category.parentName
                              ? `${product.category.parentName.substring(0, 10)}${product.category.parentName.length > 10 ? '...' : ''} > `
                              : ''}
                            {product.category.name.length > 15
                              ? `${product.category.name.substring(0, 15)}...`
                              : product.category.name}
                          </span>
                        )}
                        {product.brand?.name && (
                          <span className="badge bg-light text-dark border" style={{ fontSize: '0.7rem' }}>
                            <i className="fas fa-copyright me-1"></i>
                            {product.brand.name}
                          </span>
                        )}
                        {product.color?.name && (
                          <span className="badge bg-light text-dark border" style={{ fontSize: '0.7rem' }}>
                            <i className="fas fa-palette me-1"></i>
                            {product.color.name}
                          </span>
                        )}
                      </div>

                      {/* Info Grid */}
                      <div className="row g-2 mb-3">
                        <div className="col-6 d-flex">
                          <div className="text-center p-2 bg-light rounded border stat-tile w-100">
                            <div className="small text-muted mb-1">
                              <i className="fas fa-tag me-1"></i>
                              Fiyat
                            </div>
                            <div className="fw-bold text-success" style={{ fontSize: '1.1rem' }}>
                              ₺{totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                            </div>
                            {showDetailedPrice && (product.vatRate > 0 || product.sctRate > 0) && (
                              <small className="text-muted d-block mt-1">
                                Ana: ₺{product.price?.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                              </small>
                            )}
                          </div>
                        </div>
                        <div className="col-6 d-flex">
                          <div className="text-center p-2 bg-light rounded border stat-tile w-100">
                            <div className="small text-muted mb-1">
                              <i className="fas fa-cubes me-1"></i>
                              Stok
                            </div>
                            <div className="fw-bold" style={{ fontSize: '1.1rem' }}>
                              {product.totalStock ?? getTotalStockQuantity(product)}
                            </div>
                            {getLowStockCount(product) > 0 && (
                              <span
                                className="badge bg-warning text-dark mt-1"
                                style={{ fontSize: '0.65rem' }}
                              >
                                {getLowStockCount(product)} Düşük
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Additional Info */}
                      {(desi > 0 || shippingCost > 0) && (
                        <div className="row g-2 mb-3">
                          {desi > 0 && (
                            <div className="col-6">
                              <div className="text-center p-2 bg-light rounded border stat-tile w-100">
                                <div className="small text-muted mb-1">Desi</div>
                                <div className="fw-semibold small">{desi.toFixed(2)}</div>
                              </div>
                            </div>
                          )}
                          {shippingCost > 0 && (
                            <div className="col-6">
                              <div className="text-center p-2 bg-light rounded border stat-tile w-100">
                                <div className="small text-muted mb-1">Kargo</div>
                                <div className="fw-semibold small">₺{shippingCost.toFixed(2)}</div>
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      {/* Actions */}
                      <div className="row g-2">
                        <div className="col-6 d-flex">
                          <button
                            className="btn btn-sm btn-outline-secondary flex-fill"
                            onClick={() => handleEdit(product)}
                          >
                            <i className="fas fa-edit me-1"></i>
                            Düzenle
                          </button>
                        </div>
                        <div className="col-6 d-flex flex-wrap gap-2">
                          <button
                            className={`btn btn-sm ${product.active === false ? 'btn-outline-success' : 'btn-outline-warning'} flex-fill`}
                            onClick={() =>
                              handleToggleActive(product.id, product.active === false ? false : true)
                            }
                          >
                            <i
                              className={`fas ${product.active === false ? 'fa-play' : 'fa-pause'} me-1`}
                            ></i>
                            {product.active === false ? 'Aktif' : 'Pasif'}
                          </button>
                          <div className="d-flex flex-fill gap-2">
                            <button
                              className="btn btn-sm btn-outline-primary flex-fill"
                              onClick={() => window.location.assign(`/desi?productId=${product.id}`)}
                              title="Desi Hesapla"
                            >
                              <i className="fas fa-calculator"></i>
                            </button>
                            <button
                              className="btn btn-sm btn-outline-danger flex-fill"
                              onClick={() => handleDelete(product.id)}
                              title="Sil"
                            >
                              <i className="fas fa-trash"></i>
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Pagination */}
          {productTotalPages > 1 && (
            <div className="d-flex justify-content-between align-items-center mt-3 flex-wrap gap-2">
              <div className="text-muted small">
                Toplam {productTotalCount} ürün, Sayfa {productPage + 1} / {productTotalPages}
              </div>
              <div className="d-flex align-items-center gap-2">
                <PaginationControls
                  page={productPage}
                  totalPages={productTotalPages}
                  onPageChange={(newPage) => {
                    setProductPage(newPage);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }}
                />
              </div>
            </div>
          )}
        </div>
      </div>

      {filteredProducts.length === 0 && (
        <div className="text-center py-5">
          <i className="fas fa-box fa-3x text-muted mb-3"></i>
          <h4 className="text-muted">
            {products.length === 0 ? 'Henüz ürün bulunmuyor' : 'Arama kriterlerine uygun ürün bulunamadı'}
          </h4>
          <p className="text-muted">
            {products.length === 0
              ? 'İlk ürünü oluşturmak için "Yeni Ürün" butonuna tıklayın.'
              : 'Farklı arama terimleri deneyin.'}
          </p>
        </div>
      )}

      {/* Product Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">{editingProduct ? 'Ürün Düzenle' : 'Yeni Ürün'}</h5>
                <button type="button" className="btn-close" onClick={() => setShowForm(false)}></button>
              </div>
              <div className="modal-body">
                <ProductForm
                  product={editingProduct}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Confirm Modal */}
      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        icon={confirmModal.icon}
        confirmVariant={confirmModal.confirmVariant}
        confirmText={confirmModal.confirmText}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false, title: '', message: '', onConfirm: null })}
      />

      <ConfirmModal
        show={errorModal.show}
        title={errorModal.title || 'Hata'}
        message={errorModal.message}
        icon="exclamation-triangle"
        confirmVariant="danger"
        confirmText="Tamam"
        cancelText={null}
        onConfirm={() => setErrorModal({ show: false, title: '', message: '' })}
        onCancel={() => setErrorModal({ show: false, title: '', message: '' })}
      />

      {/* Error Details Modal */}
      {errorDetailsModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h5 className="modal-title">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  {errorDetailsModal.title}
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setErrorDetailsModal({ show: false, title: '', errors: [] })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-warning">
                  <i className="fas fa-info-circle me-2"></i>
                  Aşağıdaki {errorDetailsModal.errors.length} ürün silinemedi. Bu ürünler transfer kayıtları
                  ile bağlantılı olabilir.
                </div>
                <div className="list-group" style={{ maxHeight: '400px', overflowY: 'auto' }}>
                  {errorDetailsModal.errors.map((error, index) => (
                    <div key={index} className="list-group-item border-start border-danger border-3">
                      <div className="d-flex flex-column gap-2">
                        <div className="fw-bold text-danger">
                          <i className="fas fa-box me-2"></i>
                          Ürün: {error.productName}
                        </div>
                        <div className="text-muted">
                          <strong>Stok Kodu:</strong> <span className="badge bg-secondary">{error.sku}</span>
                        </div>
                        <div className="text-danger mt-1">
                          <strong>Hata:</strong> {error.error}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setErrorDetailsModal({ show: false, title: '', errors: [] })}
                >
                  Kapat
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Bulk Price Modal */}
      {showBulkModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Toplu Fiyat Güncelle</h5>
                <button type="button" className="btn-close" onClick={() => setShowBulkModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-info">
                  Bu işlem mevcut filtrelere göre uygulanır. Etkilenecek ürün:{' '}
                  <strong>{filteredProducts.length}</strong>
                </div>
                <div className="row g-3">
                  <div className="col-12">
                    <label className="form-label">İşlem Türü</label>
                    <div className="input-group">
                      <select
                        className="form-select"
                        value={bulkMode}
                        onChange={(e) => setBulkMode(e.target.value)}
                      >
                        <option value="PERCENTAGE">Yüzde</option>
                        <option value="AMOUNT">Tutar</option>
                      </select>
                      <select
                        className="form-select"
                        value={bulkDirection}
                        onChange={(e) => setBulkDirection(e.target.value)}
                      >
                        <option value="INCREASE">Arttır</option>
                        <option value="DECREASE">Azalt</option>
                      </select>
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        className="form-control"
                        placeholder={bulkMode === 'PERCENTAGE' ? 'Örn: 10 ( % )' : 'Örn: 50.00 (₺)'}
                        value={bulkValue}
                        onChange={(e) => setBulkValue(e.target.value)}
                      />
                      {bulkMode === 'PERCENTAGE' && <span className="input-group-text">%</span>}
                    </div>
                    <div className="form-text">Negatif değer girilmez; azaltma için yönü seçin.</div>
                  </div>
                  <div className="col-12 form-check">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="bulkOnlyActive"
                      checked={bulkOnlyActive}
                      onChange={(e) => setBulkOnlyActive(e.target.checked)}
                    />
                    <label className="form-check-label" htmlFor="bulkOnlyActive">
                      Sadece aktif ürünler
                    </label>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowBulkModal(false)}>
                  İptal
                </button>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={async () => {
                    const val = parseFloat(bulkValue);
                    if (!bulkValue || isNaN(val) || val <= 0) {
                      setErrorModal({
                        show: true,
                        title: 'Geçersiz Değer',
                        message: 'Lütfen geçerli bir değer girin.',
                      });
                      return;
                    }
                    try {
                      const request = {
                        mode: bulkMode,
                        direction: bulkDirection,
                        value: val,
                        onlyActive: bulkOnlyActive,
                        categoryId: selectedSubcategory ? Number(selectedSubcategory) : null,
                        brandId: selectedBrand || null,
                        colorId: selectedColor || null,
                      };
                      const res = await axios.put('/api/products/bulk-price', request);
                      const affected = res.data?.affected ?? 0;
                      const toast = document.createElement('div');
                      toast.className =
                        'toast align-items-center text-bg-success border-0 position-fixed top-0 end-0 m-3 show';
                      toast.setAttribute('role', 'alert');
                      toast.innerHTML = `<div class="d-flex"><div class="toast-body">${affected} ürün güncellendi</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button></div>`;
                      document.body.appendChild(toast);
                      setTimeout(() => {
                        try {
                          document.body.removeChild(toast);
                        } catch {}
                      }, 3000);
                      setShowBulkModal(false);
                      setBulkValue('');
                      await fetchProducts();
                    } catch (error) {
                      const errorData = error?.response?.data;
                      const message =
                        errorData?.message ||
                        errorData?.error ||
                        (typeof errorData === 'string'
                          ? errorData
                          : 'Toplu fiyat güncellemesi sırasında beklenmeyen bir hata oluştu.');
                      setErrorModal({
                        show: true,
                        title: 'Toplu Fiyat Güncelleme Hatası',
                        message,
                      });
                    }
                  }}
                >
                  Uygula
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {SecurityCodePrompt}
    </div>
  );
};

export default Products;
