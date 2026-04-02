import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SearchableSelect from './SearchableSelect';
import ConfirmModal from './ConfirmModal';
import './ProductForm.css';

const ProductForm = ({ product, onSuccess, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    shortDescription: '',
    sku: '',
    price: '',
    salePrice: '',
    saleStart: '',
    saleEnd: '',
    isFeatured: false,
    isNew: false,
    weight: '',
    dimensions: '',
    lengthCm: '',
    widthCm: '',
    heightCm: '',
    shippingRate: '',
    vatRate: '20',
    sctRate: '',
    categoryId: '',
    subcategoryId: '',
    isActive: true
  });
  const [productImages, setProductImages] = useState([]);
  const [imageUploading, setImageUploading] = useState(false);
  const [deleteImageId, setDeleteImageId] = useState(null);
  const imageInputRef = React.useRef(null);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [mainCategories, setMainCategories] = useState([]);
  const [subcategories, setSubcategories] = useState([]);
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createModalType, setCreateModalType] = useState(null); // 'brand', 'color', 'category', 'subcategory'
  const [createModalName, setCreateModalName] = useState('');
  const [createModalColorHex, setCreateModalColorHex] = useState('#000000');
  const [createModalDescription, setCreateModalDescription] = useState('');
  const [createModalLoading, setCreateModalLoading] = useState(false);
  const [priceIncludesVat, setPriceIncludesVat] = useState(false);
  
  // Calculate total price with taxes
  const calculateTotalPrice = () => {
    const enteredPrice = parseFloat(formData.price) || 0;
    const sctRate = parseFloat(formData.sctRate) || 0;
    const vatRate = parseFloat(formData.vatRate) || 0;
    
    let basePrice, sctAmount, priceWithSct, vatAmount, totalPrice;
    
    if (priceIncludesVat && enteredPrice > 0) {
      // Girilen fiyat KDV dahil ise, geriye doğru hesaplama yap
      // totalPrice = priceWithSct * (1 + vatRate / 100)
      // priceWithSct = totalPrice / (1 + vatRate / 100)
      totalPrice = enteredPrice;
      priceWithSct = totalPrice / (1 + vatRate / 100);
      vatAmount = totalPrice - priceWithSct;
      
      // ÖTV varsa, basePrice'ı hesapla
      if (sctRate > 0) {
        // priceWithSct = basePrice * (1 + sctRate / 100)
        basePrice = priceWithSct / (1 + sctRate / 100);
        sctAmount = priceWithSct - basePrice;
      } else {
        basePrice = priceWithSct;
        sctAmount = 0;
      }
    } else {
      // Girilen fiyat KDV hariç ise, ileriye doğru hesaplama yap
      basePrice = enteredPrice;
      sctAmount = basePrice * (sctRate / 100);
      priceWithSct = basePrice + sctAmount;
      vatAmount = priceWithSct * (vatRate / 100);
      totalPrice = priceWithSct + vatAmount;
    }
    
    return {
      basePrice,
      sctAmount,
      priceWithSct,
      vatAmount,
      totalPrice
    };
  };

  useEffect(() => {
    fetchMainCategories();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (formData.categoryId) {
      fetchSubcategories(formData.categoryId);
    } else {
      setSubcategories([]);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [formData.categoryId]);

  useEffect(() => {
    if (!product) {
      return;
    }

    const leafCategoryIdRaw = product.category?.id
      ?? (product.categoryId != null ? Number(product.categoryId) : null);
    const parentCategoryIdRaw = product.category?.parent?.id
      ?? (product.categoryParentId != null ? Number(product.categoryParentId) : null);

    const hasParent = parentCategoryIdRaw != null;
    const mainCategoryIdNumeric = hasParent ? parentCategoryIdRaw : leafCategoryIdRaw;
    const subcategoryIdNumeric = hasParent ? leafCategoryIdRaw : null;

    setFormData(prev => ({
      ...prev,
      name: product.name || '',
      description: product.description || '',
      sku: product.sku || '',
      price: product.price || '',
      weight: product.weight || '',
      dimensions: product.dimensions || '',
      lengthCm: product.lengthCm || '',
      widthCm: product.widthCm || '',
      heightCm: product.heightCm || '',
      shippingRate: product.shippingRate || '',
      vatRate: product.vatRate || '',
      sctRate: product.sctRate || '',
      categoryId: mainCategoryIdNumeric != null ? String(mainCategoryIdNumeric) : '',
      subcategoryId: subcategoryIdNumeric != null ? String(subcategoryIdNumeric) : '',
      isActive: product.isActive !== false,
      shortDescription: product.shortDescription || '',
      salePrice: product.salePrice || '',
      saleStart: product.saleStart ? product.saleStart.substring(0, 16) : '',
      saleEnd: product.saleEnd ? product.saleEnd.substring(0, 16) : '',
      isFeatured: !!product.featured || !!product.isFeatured,
      isNew: !!product.isNew,
    }));
    // Load product images
    if (product.id) {
      axios.get(`/api/products/${product.id}/images`).then(r => setProductImages(r.data || [])).catch(() => {});
    }
    const resolvedBrandId = product.brand?.id
      ?? (product.brandId != null ? Number(product.brandId) : null);
    const resolvedColorId = product.color?.id
      ?? (product.colorId != null ? Number(product.colorId) : null);

    setBrandId(resolvedBrandId);
    setColorId(resolvedColorId);

    if (mainCategoryIdNumeric != null) {
      (async () => {
        const subs = await fetchSubcategories(mainCategoryIdNumeric);
        if (subcategoryIdNumeric != null && Array.isArray(subs) && subs.some(s => String(s.id) === String(subcategoryIdNumeric))) {
          setFormData(prev => ({ ...prev, subcategoryId: String(subcategoryIdNumeric) }));
        }
      })();
    } else {
      setSubcategories([]);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product]);

  const normalizeSubcategories = (subs = [], parentMeta = {}) => {
    return subs.map(sub => ({
      ...sub,
      parentId: sub.parentId ?? parentMeta.id ?? null,
      parentName: sub.parentName ?? parentMeta.name ?? null,
      productCount: Number(sub.productCount ?? 0)
    }));
  };

  const sortByName = (list = []) =>
    [...list].sort((a, b) => (a.name || '').localeCompare(b.name || '', 'tr'));

  const fetchMainCategories = async () => {
    try {
      // Fetch ALL categories (no pagination on backend)
      const response = await axios.get('/api/categories/top-level');
      const data = response.data || {};
      // Handle paginated response
      const categoriesList = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : []);
      const normalized = categoriesList.map(cat => {
        const children = normalizeSubcategories(
          Array.isArray(cat.children) ? cat.children : (Array.isArray(cat.subcategories) ? cat.subcategories : []),
          { id: cat.id, name: cat.name }
        );
        return { ...cat, children: sortByName(children) };
      });
      setMainCategories(sortByName(normalized));
    } catch (error) {
      console.error('Error fetching main categories:', error);
      setMainCategories([]);
    }
  };

  const fetchSubcategories = async (parentId, { forceRefresh = false } = {}) => {
    try {
      const parentIdNum = Number(parentId);
      const parent = mainCategories.find(cat => Number(cat.id) === parentIdNum);
      if (!forceRefresh && parent && Array.isArray(parent.children) && parent.children.length > 0) {
        const sorted = sortByName(parent.children);
        setSubcategories(sorted);
        return sorted;
      }
      const response = await axios.get(`/api/categories/${parentId}/subcategories`);
      const data = response.data || {};
      const subcategoriesList = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : []);
      const normalized = sortByName(normalizeSubcategories(subcategoriesList, { id: parentIdNum, name: parent?.name }));
      setSubcategories(normalized);
      if (parent) {
        setMainCategories(prev => prev.map(cat => cat.id === parentIdNum ? { ...cat, children: normalized } : cat));
      }
      return normalized;
    } catch (error) {
      console.error('Error fetching subcategories:', error);
      setSubcategories([]);
      return [];
    }
  };

  const handleCreateNew = (type) => {
    setCreateModalType(type);
    setCreateModalName('');
    setCreateModalColorHex('#000000');
    setCreateModalDescription('');
    setShowCreateModal(true);
  };

  const showToast = (message, type = 'success') => {
    const toast = document.createElement('div');
    const bgClass = type === 'success' ? 'text-bg-success' : type === 'warning' ? 'text-bg-warning' : 'text-bg-danger';
    const icon = type === 'success' ? 'fa-check-circle' : type === 'warning' ? 'fa-exclamation-triangle' : 'fa-times-circle';
    toast.className = `toast align-items-center ${bgClass} border-0 position-fixed top-0 end-0 m-3 show`;
    toast.setAttribute('role', 'alert');
    toast.style.zIndex = '9999';
    toast.innerHTML = `
      <div class="d-flex align-items-center">
        <div class="toast-body d-flex align-items-center">
          <i class="fas ${icon} me-2"></i>
          <span>${message}</span>
        </div>
        <button type="button" class="btn-close ${type === 'success' ? 'btn-close-white' : ''} me-2 m-auto" data-bs-dismiss="toast" aria-label="Kapat"></button>
      </div>
    `;
    document.body.appendChild(toast);
    setTimeout(() => {
      try {
        toast.classList.remove('show');
        setTimeout(() => {
          try { document.body.removeChild(toast); } catch {}
        }, 300);
      } catch {}
    }, type === 'success' ? 3000 : 5000);
  };

  const handleCreateSubmit = async () => {
    if (!createModalName.trim()) {
      showToast('Lütfen bir isim giriniz', 'warning');
      return;
    }

    setCreateModalLoading(true);
    try {
      let created;
      const trimmedName = createModalName.trim();
      
      if (createModalType === 'brand') {
        // Check if brand already exists
        try {
          const searchResponse = await axios.get('/api/brands/search', { params: { name: trimmedName } });
          const existing = searchResponse.data?.find(b => b.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setBrandId(existing.id);
            setShowCreateModal(false);
            setCreateModalName('');
            setCreateModalColorHex('#000000');
            setCreateModalDescription('');
            showToast(`"${trimmedName}" markası zaten mevcut. Seçildi.`, 'warning');
            setCreateModalLoading(false);
            return;
          }
        } catch (searchError) {
          // Continue with creation if search fails
        }
        
        const response = await axios.post('/api/brands', { name: trimmedName });
        created = response.data;
        setBrandId(created.id);
        showToast(`"${trimmedName}" markası başarıyla oluşturuldu.`, 'success');
      } else if (createModalType === 'color') {
        // Check if color already exists
        try {
          const searchResponse = await axios.get('/api/colors/search', { params: { name: trimmedName } });
          const existing = searchResponse.data?.find(c => c.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setColorId(existing.id);
            setShowCreateModal(false);
            setCreateModalName('');
            setCreateModalColorHex('#000000');
            setCreateModalDescription('');
            showToast(`"${trimmedName}" rengi zaten mevcut. Seçildi.`, 'warning');
            setCreateModalLoading(false);
            return;
          }
        } catch (searchError) {
          // Continue with creation if search fails
        }
        
        const response = await axios.post('/api/colors', { 
          name: trimmedName,
          hexCode: createModalColorHex
        });
        created = response.data;
        setColorId(created.id);
        showToast(`"${trimmedName}" rengi başarıyla oluşturuldu.`, 'success');
      } else if (createModalType === 'category') {
        // Check if category already exists
        try {
          const existing = mainCategories.find(c => c.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setFormData(prev => ({ ...prev, categoryId: String(existing.id) }));
            setShowCreateModal(false);
            setCreateModalName('');
            setCreateModalColorHex('#000000');
            setCreateModalDescription('');
            showToast(`"${trimmedName}" kategorisi zaten mevcut. Seçildi.`, 'warning');
            setCreateModalLoading(false);
            return;
          }
        } catch (searchError) {
          // Continue with creation if search fails
        }
        
        const response = await axios.post('/api/categories', { 
          name: trimmedName,
          description: createModalDescription.trim() || null
        });
        created = response.data;
        await fetchMainCategories();
        setFormData(prev => ({ ...prev, categoryId: String(created.id) }));
        showToast(`"${trimmedName}" kategorisi başarıyla oluşturuldu.`, 'success');
      } else if (createModalType === 'subcategory') {
        if (!formData.categoryId) {
          showToast('Önce ana kategori seçiniz', 'warning');
          setCreateModalLoading(false);
          return;
        }
        
        // Check if subcategory already exists
        try {
          const existing = subcategories.find(s => s.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setFormData(prev => ({ ...prev, subcategoryId: String(existing.id) }));
            setShowCreateModal(false);
            setCreateModalName('');
            setCreateModalColorHex('#000000');
            setCreateModalDescription('');
            showToast(`"${trimmedName}" alt kategorisi zaten mevcut. Seçildi.`, 'warning');
            setCreateModalLoading(false);
            return;
          }
        } catch (searchError) {
          // Continue with creation if search fails
        }
        
        const response = await axios.post('/api/categories/batch', 
          [{ name: trimmedName, description: createModalDescription.trim() || null }],
          { params: { parentId: formData.categoryId } }
        );
        created = response.data[0];
        const updatedSubs = await fetchSubcategories(formData.categoryId, { forceRefresh: true });
        const createdMatch = updatedSubs.find(sub => Number(sub.id) === Number(created?.id));
        const effectiveSub = createdMatch || {
          ...(created || {}),
          id: created?.id,
          name: created?.name ?? trimmedName,
          parentId: Number(formData.categoryId),
          parentName: mainCategories.find(cat => cat.id?.toString() === formData.categoryId)?.name || null
        };
        if (!createdMatch) {
          setSubcategories(prev => sortByName([...(prev || []), effectiveSub]));
          setMainCategories(prev => prev.map(cat => {
            if (cat.id?.toString() === formData.categoryId) {
              const updatedChildren = sortByName([...(cat.children || []), effectiveSub]);
              return { ...cat, children: updatedChildren };
            }
            return cat;
          }));
        }
        setFormData(prev => ({ ...prev, subcategoryId: String((effectiveSub?.id ?? created?.id) ?? '') }));
        showToast(`"${trimmedName}" alt kategorisi başarıyla oluşturuldu.`, 'success');
      }
      setShowCreateModal(false);
      setCreateModalName('');
      setCreateModalColorHex('#000000');
      setCreateModalDescription('');
    } catch (error) {
      console.error('Error creating:', error);
      const errorResponse = error.response?.data;
      let errorMessage = 'Oluşturma sırasında hata oluştu';
      
      if (errorResponse) {
        if (typeof errorResponse === 'string') {
          errorMessage = errorResponse;
        } else if (errorResponse.message) {
          errorMessage = errorResponse.message;
        } else if (errorResponse.error) {
          errorMessage = errorResponse.error;
        }
      }
      
      // Check for duplicate/conflict errors
      if (error.response?.status === 409 || errorMessage.toLowerCase().includes('zaten') || errorMessage.toLowerCase().includes('mevcut') || errorMessage.toLowerCase().includes('duplicate') || errorMessage.toLowerCase().includes('exists')) {
        const itemType = createModalType === 'brand' ? 'marka' : createModalType === 'color' ? 'renk' : createModalType === 'category' ? 'kategori' : 'alt kategori';
        showToast(`"${createModalName.trim()}" ${itemType} zaten mevcut.`, 'warning');
      } else {
        const itemType = createModalType === 'brand' ? 'Marka' : createModalType === 'color' ? 'Renk' : createModalType === 'category' ? 'Kategori' : 'Alt kategori';
        showToast(`${itemType} oluşturulurken hata: ${errorMessage}`, 'error');
      }
    } finally {
      setCreateModalLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;

    if (name === 'categoryId') {
      // Ana kategori değiştiğinde alt kategoriyi sıfırla
      setFormData(prev => ({
        ...prev,
        [name]: value,
        subcategoryId: '' // Alt kategoriyi sıfırla
      }));
    } else {
      setFormData(prev => ({
        ...prev,
        [name]: type === 'checkbox' ? checked : value
      }));
    }

    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.name.trim()) {
      newErrors.name = 'Ürün adı gereklidir';
    }

    if (!formData.sku.trim()) {
      newErrors.sku = 'Stok kodu gereklidir';
    }

    if (!formData.price || parseFloat(formData.price) < 0) {
      newErrors.price = 'Geçerli bir fiyat giriniz';
    }

    if (!formData.categoryId) {
      newErrors.categoryId = 'Kategori seçiniz';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const uploadImage = async (file) => {
    if (!product?.id || !file) return;
    setImageUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('primary', productImages.length === 0 ? 'true' : 'false');
      await axios.post(`/api/products/${product.id}/images`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      const res = await axios.get(`/api/products/${product.id}/images`);
      setProductImages(res.data || []);
    } catch (e) {
      setErrors({ general: e.response?.data?.message || 'Görsel yüklenemedi' });
    } finally { setImageUploading(false); }
  };

  const handleSubmit = async (e) => {
    if (e && typeof e.preventDefault === 'function') {
      e.preventDefault();
    }
    if (e && typeof e.stopPropagation === 'function') {
      e.stopPropagation();
    }

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      const dataToSend = {
        name: formData.name,
        description: formData.description,
        shortDescription: formData.shortDescription || null,
        sku: formData.sku,
        price: parseFloat(formData.price),
        salePrice: formData.salePrice ? parseFloat(formData.salePrice) : null,
        saleStart: formData.saleStart ? formData.saleStart + ':00' : null,
        saleEnd: formData.saleEnd ? formData.saleEnd + ':00' : null,
        featured: formData.isFeatured,
        isNew: formData.isNew,
        weight: formData.weight ? parseFloat(formData.weight) : null,
        dimensions: formData.dimensions,
        lengthCm: formData.lengthCm ? parseFloat(formData.lengthCm) : null,
        widthCm: formData.widthCm ? parseFloat(formData.widthCm) : null,
        heightCm: formData.heightCm ? parseFloat(formData.heightCm) : null,
        shippingRate: formData.shippingRate ? parseFloat(formData.shippingRate) : null,
        vatRate: formData.vatRate ? parseFloat(formData.vatRate) : null,
        sctRate: formData.sctRate ? parseFloat(formData.sctRate) : null,
        category: { id: parseInt(formData.subcategoryId || formData.categoryId) },
        brand: brandId ? { id: brandId } : null,
        color: colorId ? { id: colorId } : null,
        isActive: formData.isActive
      };

      let savedProductId = product?.id;

      let createdData = null;
      if (product) {
        const resp = await axios.put(`/api/products/${product.id}`, dataToSend);
        createdData = resp?.data;
      } else {
        const resp = await axios.post('/api/products', dataToSend);
        savedProductId = resp.data?.id;
        createdData = resp?.data;
      }

      if (onSuccess) onSuccess(createdData || { id: savedProductId });
    } catch (error) {
      console.error('Error saving product:', error);
      const errorData = error?.response?.data;
      const friendlyMessage = errorData?.message
        || errorData?.error
        || (typeof errorData === 'string' ? errorData : null)
        || 'Ürün kaydedilirken beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.';
      setErrors({ general: friendlyMessage });
      if (onSuccess) {
        // Bildirim için hata da iletilsin
        onSuccess({ error: true, message: friendlyMessage });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="product-form-container">
    <form onSubmit={(e) => e.preventDefault()}>
      {errors.general && (
        <div className="alert alert-danger" role="alert">
          {errors.general}
        </div>
      )}

      <div className="row">
        <div className="col-12 col-md-6">
          <div className="mb-3">
            <label htmlFor="name" className="form-label">
              Ürün Adı <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.name ? 'is-invalid' : ''}`}
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              placeholder="Samsung Buzdolabı"
              required
            />
            {errors.name && <div className="invalid-feedback">{errors.name}</div>}
          </div>
        </div>

        <div className="col-12 col-md-6">
          <div className="mb-3">
            <label htmlFor="sku" className="form-label">
              Stok Kodu <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className={`form-control ${errors.sku ? 'is-invalid' : ''}`}
              id="sku"
              name="sku"
              value={formData.sku}
              onChange={handleChange}
              placeholder="BD-001"
              required
            />
            {errors.sku && <div className="invalid-feedback">{errors.sku}</div>}
          </div>
        </div>
      </div>

      <div className="mb-3">
        <label htmlFor="description" className="form-label">Açıklama</label>
        <textarea className="form-control" id="description" name="description" rows="3"
          value={formData.description} onChange={handleChange} placeholder="Detaylı ürün açıklaması..." />
      </div>
      <div className="mb-3">
        <label htmlFor="shortDescription" className="form-label">Kısa Açıklama <small className="text-muted">(mağazada listede görünür)</small></label>
        <textarea className="form-control" id="shortDescription" name="shortDescription" rows="2"
          value={formData.shortDescription} onChange={handleChange} placeholder="Mağazada ürün kartında görünecek kısa açıklama..." maxLength={1000} />
      </div>

      {/* Price and Tax Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-money-bill-wave me-2"></i>
            Fiyatlandırma ve Vergiler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-4">
            <label htmlFor="price" className="form-label">
              <i className="fas fa-tag me-1"></i>
              Fiyat (₺) <span className="text-danger">*</span>
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className={`form-control ${errors.price ? 'is-invalid' : ''}`}
              id="price"
              name="price"
              value={formData.price}
              onChange={handleChange}
              placeholder="15000.00"
              required
            />
            {errors.price && <div className="invalid-feedback">{errors.price}</div>}
            <div className="mt-2">
              <div className="form-check form-switch d-flex align-items-center">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="priceIncludesVat"
                  checked={priceIncludesVat}
                  onChange={(e) => setPriceIncludesVat(e.target.checked)}
                  style={{ width: '48px', height: '24px', cursor: 'pointer' }}
                />
                <label className="form-check-label ms-2" htmlFor="priceIncludesVat" style={{ cursor: 'pointer' }}>
                  <span className={priceIncludesVat ? 'text-primary fw-semibold' : 'text-muted'}>
                    <i className={`fas ${priceIncludesVat ? 'fa-check-circle' : 'fa-circle'} me-1`}></i>
                    KDV Dahil
                  </span>
                </label>
              </div>
              {priceIncludesVat && formData.vatRate && (
                <small className="text-muted d-block mt-1 ms-5">
                  <i className="fas fa-info-circle me-1"></i>
                  Girilen fiyat %{formData.vatRate} KDV dahil olarak kabul edilecektir
                </small>
              )}
            </div>
          </div>
        </div>

        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-4">
            <label htmlFor="vatRate" className="form-label">
              <i className="fas fa-percentage me-1"></i>KDV Oranı (%)
              <span 
                className="ms-1 text-primary" 
                style={{ cursor: 'help' }}
                title="Katma Değer Vergisi oranı. Türkiye'de yaygın oranlar: %1, %10, %20"
              >
                <i className="fas fa-info-circle"></i>
              </span>
            </label>
            <div className="input-group">
              <input
                type="number"
                step="0.01"
                min="0"
                max="100"
                className="form-control"
                id="vatRate"
                name="vatRate"
                value={formData.vatRate}
                onChange={handleChange}
                placeholder="20"
              />
              <span className="input-group-text">%</span>
            </div>
            <div className="d-flex gap-1 mt-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '1'})}>%1</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '10'})}>%10</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '20'})}>%20</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, vatRate: '0'})}>Muaf</button>
            </div>
          </div>
        </div>

        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-4">
            <label htmlFor="sctRate" className="form-label">
              <i className="fas fa-percentage me-1"></i>ÖTV Oranı (%)
              <span 
                className="ms-1 text-primary" 
                style={{ cursor: 'help' }}
                title="Özel Tüketim Vergisi. Alkol, tütün, akaryakıt, otomobil gibi ürünlerde uygulanır."
              >
                <i className="fas fa-info-circle"></i>
              </span>
            </label>
            <div className="input-group">
              <input
                type="number"
                step="0.01"
                min="0"
                max="200"
                className="form-control"
                id="sctRate"
                name="sctRate"
                value={formData.sctRate}
                onChange={handleChange}
                placeholder="0"
              />
              <span className="input-group-text">%</span>
            </div>
            <div className="d-flex gap-1 mt-2">
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '0'})}>Yok</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '10'})}>%10</button>
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => setFormData({...formData, sctRate: '50'})}>%50</button>
            </div>
          </div>
        </div>
      </div>

      {/* Physical Properties Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-cube me-2"></i>
            Fiziksel Özellikler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-12 col-md-6">
          <div className="mb-3">
            <label className="form-label">
              <i className="fas fa-ruler-combined me-1"></i>
              Boyutlar (cm)
            </label>
            <div className="input-group">
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="widthCm"
                name="widthCm"
                value={formData.widthCm}
                onChange={handleChange}
                placeholder="En"
              />
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="lengthCm"
                name="lengthCm"
                value={formData.lengthCm}
                onChange={handleChange}
                placeholder="Boy"
              />
              <input
                type="number"
                min="0"
                step="0.01"
                className="form-control"
                id="heightCm"
                name="heightCm"
                value={formData.heightCm}
                onChange={handleChange}
                placeholder="Derinlik"
              />
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="mb-3">
            <label htmlFor="weight" className="form-label">
              <i className="fas fa-weight-hanging me-1"></i>
              Ağırlık (kg)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              className="form-control"
              id="weight"
              name="weight"
              value={formData.weight}
              onChange={handleChange}
              placeholder="50.5"
            />
          </div>
        </div>
      </div>

      {/* Category and Brand Section */}
      <div className="row mb-3">
        <div className="col-12">
          <h6 className="text-muted mb-3">
            <i className="fas fa-tags me-2"></i>
            Kategori ve Özellikler
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-3">
            <label htmlFor="categoryId" className="form-label">
              <i className="fas fa-folder me-1"></i>
              Ana Kategori <span className="text-danger">*</span>
            </label>
            <div className="input-group">
              <select
                className={`form-select ${errors.categoryId ? 'is-invalid' : ''}`}
                id="categoryId"
                name="categoryId"
                value={formData.categoryId}
                onChange={handleChange}
                required
              >
                <option value="">Ana kategori seçin</option>
                {Array.isArray(mainCategories) && mainCategories.map((category) => (
                  <option key={category.id} value={String(category.id)}>
                    {category.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="btn btn-outline-primary"
                onClick={() => handleCreateNew('category')}
                title="Yeni kategori oluştur"
                style={{ minWidth: '45px' }}
              >
                <i className="fas fa-plus"></i>
              </button>
            </div>
            {errors.categoryId && <div className="invalid-feedback">{errors.categoryId}</div>}
          </div>
        </div>

        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-3">
            <label htmlFor="subcategoryId" className="form-label">
              <i className="fas fa-folder-open me-1"></i>
              Alt Kategori (Opsiyonel)
            </label>
            <div className="input-group">
              <select
                className="form-select"
                id="subcategoryId"
                name="subcategoryId"
                value={formData.subcategoryId}
                onChange={handleChange}
                disabled={!formData.categoryId}
              >
                <option value="">Alt kategori seçin (opsiyonel)</option>
                {Array.isArray(subcategories) && subcategories.map((subcategory) => (
                  <option key={subcategory.id} value={String(subcategory.id)}>
                    {subcategory.name}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="btn btn-outline-primary"
                onClick={() => handleCreateNew('subcategory')}
                disabled={!formData.categoryId}
                title="Yeni alt kategori oluştur"
                style={{ minWidth: '45px' }}
              >
                <i className="fas fa-plus"></i>
              </button>
            </div>
            {!formData.categoryId && (
              <small className="text-muted">Önce ana kategori seçin</small>
            )}
          </div>
        </div>
        
        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-3">
            <label className="form-label">
              <i className="fas fa-copyright me-1"></i>
              Marka
            </label>
            <div className="d-flex" style={{ gap: '0.5rem' }}>
              <div style={{ flex: 1 }}>
                <SearchableSelect
                  value={brandId}
                  onChange={(id) => setBrandId(id)}
                  searchEndpoint="/api/brands/search"
                  placeholder="Marka ara..."
                  wrapperClassName=""
                />
              </div>
              <button
                type="button"
                className="btn btn-outline-primary d-flex align-items-center justify-content-center"
                onClick={() => handleCreateNew('brand')}
                title="Yeni marka oluştur"
                style={{ 
                  minWidth: '42px', 
                  width: '42px',
                  height: '38px',
                  padding: '0',
                  flexShrink: 0
                }}
              >
                <i className="fas fa-plus"></i>
              </button>
            </div>
          </div>
        </div>

        <div className="col-12 col-md-6 col-lg-4">
          <div className="mb-3">
            <label className="form-label">
              <i className="fas fa-palette me-1"></i>
              Renk
            </label>
            <div className="d-flex" style={{ gap: '0.5rem' }}>
              <div style={{ flex: 1 }}>
                <SearchableSelect
                  value={colorId}
                  onChange={(id) => setColorId(id)}
                  searchEndpoint="/api/colors/search"
                  placeholder="Renk ara..."
                  renderOption={(opt) => (
                    <span>
                      <span className="me-2" style={{ display: 'inline-block', width: 12, height: 12, backgroundColor: opt.hexCode || '#ccc', border: '1px solid #ccc' }}></span>
                      {opt.name}
                    </span>
                  )}
                  wrapperClassName=""
                />
              </div>
              <button
                type="button"
                className="btn btn-outline-primary d-flex align-items-center justify-content-center"
                onClick={() => handleCreateNew('color')}
                title="Yeni renk oluştur"
                style={{ 
                  minWidth: '42px', 
                  width: '42px',
                  height: '38px',
                  padding: '0',
                  flexShrink: 0
                }}
              >
                <i className="fas fa-plus"></i>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Shipping and Status Section */}
      <div className="row mt-4">
        <div className="col-12">
          <h6 className="text-muted mb-4" style={{ 
            marginTop: '0.5rem', 
            marginBottom: '1.5rem',
            fontSize: '1rem', 
            fontWeight: '600',
            paddingBottom: '0.75rem',
            borderBottom: '1px solid #e9ecef'
          }}>
            <i className="fas fa-shipping-fast me-2"></i>
            Kargo ve Durum
          </h6>
        </div>
      </div>

      <div className="row">
        <div className="col-md-6 col-12 mb-3 mb-md-0">
          <div className="mb-3">
            <label htmlFor="shippingRate" className="form-label">
              <i className="fas fa-truck me-1"></i>
              Kargo Ücreti (Desi Başına)
            </label>
            <div className="input-group">
              <span className="input-group-text">₺</span>
              <input
                type="number"
                step="0.01"
                min="0"
                className="form-control"
                id="shippingRate"
                name="shippingRate"
                value={formData.shippingRate}
                onChange={handleChange}
                placeholder="12.50"
              />
              <span className="input-group-text">/desi</span>
            </div>
            <small className="text-muted">Desi başına kargo ücreti tutarı</small>
          </div>
        </div>

        <div className="col-md-6 col-12">
          <div className="mb-3">
            <label className="form-label d-block">
              <i className="fas fa-toggle-on me-1"></i>
              Ürün Durumu
            </label>
            <div className="form-check form-switch mt-3">
              <input
                type="checkbox"
                className="form-check-input"
                id="isActive"
                name="isActive"
                checked={formData.isActive}
                onChange={handleChange}
                style={{ width: '48px', height: '24px', cursor: 'pointer' }}
              />
              <label className="form-check-label ms-2" htmlFor="isActive" style={{ cursor: 'pointer' }}>
                <strong className={formData.isActive ? 'text-success' : 'text-secondary'}>
                  {formData.isActive ? 'Aktif' : 'Pasif'}
                </strong>
              </label>
            </div>
          </div>
        </div>
      </div>

      {/* Live Price Calculation Preview */}
      {formData.price && parseFloat(formData.price) > 0 && (
        <div className="alert alert-info border-start border-primary border-4 mb-3">
          <h6 className="alert-heading mb-2">
            <i className="fas fa-calculator me-2"></i>
            Fiyat Hesaplama Önizlemesi
            {priceIncludesVat && (
              <span className="badge bg-primary ms-2">
                <i className="fas fa-info-circle me-1"></i>
                KDV Dahil Fiyat
              </span>
            )}
          </h6>
          <div className="row g-2">
            <div className="col-12 col-md-6">
              {priceIncludesVat && (
                <div className="d-flex justify-content-between small mb-2 p-2 bg-light rounded">
                  <span className="text-muted">Girilen Fiyat (KDV Dahil):</span>
                  <strong className="text-primary">₺{parseFloat(formData.price).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</strong>
                </div>
              )}
              <div className="d-flex justify-content-between small">
                <span className="text-muted">Ana Fiyat:</span>
                <strong>₺{calculateTotalPrice().basePrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</strong>
              </div>
              {calculateTotalPrice().sctAmount > 0 && (
                <>
                  <div className="d-flex justify-content-between small text-success">
                    <span>+ ÖTV (%{formData.sctRate}):</span>
                    <span>₺{calculateTotalPrice().sctAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="d-flex justify-content-between small">
                    <span className="text-muted">ÖTV'li Fiyat:</span>
                    <span>₺{calculateTotalPrice().priceWithSct.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                  </div>
                </>
              )}
              {calculateTotalPrice().vatAmount > 0 && (
                <div className="d-flex justify-content-between small text-success">
                  <span>+ KDV (%{formData.vatRate}):</span>
                  <span>₺{calculateTotalPrice().vatAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</span>
                </div>
              )}
              <hr className="my-1" />
              <div className="d-flex justify-content-between">
                <strong className="text-dark">Toplam Fiyat:</strong>
                <strong className="text-success fs-5">₺{calculateTotalPrice().totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}</strong>
              </div>
            </div>
            <div className="col-12 col-md-6">
              <small className="text-muted d-block">
                <i className="fas fa-lightbulb me-1 text-warning"></i>
                <strong>Hesaplama Formülü:</strong>
              </small>
              {priceIncludesVat ? (
                <>
                  <small className="text-muted d-block">1. <strong>Girilen Fiyat = KDV Dahil Fiyat</strong></small>
                  <small className="text-muted d-block">2. ÖTV'li Fiyat = KDV Dahil Fiyat ÷ (1 + KDV%)</small>
                  {calculateTotalPrice().sctAmount > 0 && (
                    <small className="text-muted d-block">3. Ana Fiyat = ÖTV'li Fiyat ÷ (1 + ÖTV%)</small>
                  )}
                  <small className="text-muted d-block">4. KDV Tutarı = KDV Dahil Fiyat - ÖTV'li Fiyat</small>
                </>
              ) : (
                <>
                  <small className="text-muted d-block">1. ÖTV Tutarı = Ana Fiyat × ÖTV%</small>
                  <small className="text-muted d-block">2. ÖTV'li Fiyat = Ana Fiyat + ÖTV</small>
                  <small className="text-muted d-block">3. KDV Tutarı = ÖTV'li Fiyat × KDV%</small>
                  <small className="text-muted d-block">4. <strong>Toplam = ÖTV'li Fiyat + KDV</strong></small>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ===== İndirim & Kampanya ===== */}
      <div className="row mb-3 mt-4">
        <div className="col-12">
          <h6 className="text-muted mb-3"><i className="fas fa-percentage me-2" />İndirim & Kampanya</h6>
        </div>
      </div>
      <div className="row">
        <div className="col-md-4">
          <div className="mb-3">
            <label className="form-label">İndirimli Fiyat (₺)</label>
            <input type="number" step="0.01" min="0" className="form-control" name="salePrice"
              value={formData.salePrice} onChange={handleChange} placeholder="İndirim yoksa boş bırakın" />
            {formData.salePrice && formData.price && parseFloat(formData.salePrice) < parseFloat(formData.price) && (
              <small className="text-success mt-1 d-block">
                <i className="fas fa-tag me-1" />%{((1 - parseFloat(formData.salePrice) / parseFloat(formData.price)) * 100).toFixed(0)} indirim
              </small>
            )}
          </div>
        </div>
        <div className="col-md-4">
          <div className="mb-3">
            <label className="form-label">İndirim Başlangıcı</label>
            <input type="datetime-local" className="form-control" name="saleStart"
              value={formData.saleStart} onChange={handleChange} />
          </div>
        </div>
        <div className="col-md-4">
          <div className="mb-3">
            <label className="form-label">İndirim Bitişi</label>
            <input type="datetime-local" className="form-control" name="saleEnd"
              value={formData.saleEnd} onChange={handleChange} />
          </div>
        </div>
      </div>
      <div className="row mb-3">
        <div className="col-md-6">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="isFeatured" checked={formData.isFeatured}
              onChange={e => setFormData(f => ({...f, isFeatured: e.target.checked}))} />
            <label className="form-check-label" htmlFor="isFeatured"><i className="fas fa-star text-warning me-1" />Öne Çıkan Ürün</label>
          </div>
        </div>
        <div className="col-md-6">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="isNew" checked={formData.isNew}
              onChange={e => setFormData(f => ({...f, isNew: e.target.checked}))} />
            <label className="form-check-label" htmlFor="isNew"><i className="fas fa-sparkles text-info me-1" />Yeni Ürün Etiketi</label>
          </div>
        </div>
      </div>

      {/* ===== Ürün Görselleri (sadece düzenleme modunda) ===== */}
      {product?.id && (
        <>
          <div className="row mb-3 mt-4">
            <div className="col-12">
              <h6 className="text-muted mb-3"><i className="fas fa-images me-2" />Ürün Görselleri</h6>
            </div>
          </div>
          <div className="mb-3">
            {/* Image Grid */}
            {productImages.length > 0 && (
              <div className="row g-2 mb-3">
                {productImages.sort((a,b) => a.sortOrder - b.sortOrder).map(img => (
                  <div key={img.id} className="col-6 col-md-3">
                    <div className={`border rounded overflow-hidden position-relative ${img.primary ? 'border-primary border-2' : ''}`}>
                      <img src={`/api/admin/products/images/${img.id}/view?thumbnail=true`}
                        alt="" style={{width:'100%', height:140, objectFit:'contain', background:'#f8f9fa'}}
                        onError={e => { e.target.style.display='none'; }} />
                      <div className="position-absolute top-0 end-0 p-1 d-flex gap-1">
                        {!img.primary && (
                          <button className="btn btn-sm btn-warning" title="Birincil yap"
                            style={{width:24,height:24,padding:0,fontSize:10}}
                            onClick={() => {
                              axios.put(`/api/products/images/${img.id}/set-primary`).then(() => {
                                axios.get(`/api/products/${product.id}/images`).then(r => setProductImages(r.data || []));
                              }).catch(() => {});
                            }}>
                            <i className="fas fa-star" />
                          </button>
                        )}
                        <button className="btn btn-sm btn-danger" title="Sil"
                          style={{width:24,height:24,padding:0,fontSize:10}}
                          onClick={() => setDeleteImageId(img.id)}>
                          <i className="fas fa-times" />
                        </button>
                      </div>
                      {img.primary && (
                        <div className="position-absolute bottom-0 start-0 w-100 text-center" style={{background:'rgba(37,99,235,0.8)', padding:'2px 0'}}>
                          <small className="text-white" style={{fontSize:10}}>Ana Görsel</small>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
            {/* Upload Zone */}
            <div className="border-2 border-dashed rounded p-3 text-center" style={{cursor:'pointer'}}
              onClick={() => imageInputRef.current?.click()}
              onDragOver={e => e.preventDefault()}
              onDrop={e => {
                e.preventDefault();
                const files = Array.from(e.dataTransfer.files).filter(f => f.type.startsWith('image/'));
                files.forEach(f => uploadImage(f));
              }}>
              <input type="file" ref={imageInputRef} className="d-none" accept="image/*" multiple
                onChange={e => { Array.from(e.target.files).forEach(f => uploadImage(f)); e.target.value=''; }} />
              {imageUploading ? (
                <div><span className="spinner-border spinner-border-sm me-2" />Yükleniyor...</div>
              ) : (
                <div>
                  <i className="fas fa-cloud-upload-alt text-muted fa-lg d-block mb-1" />
                  <div className="small text-muted">Görsel sürükleyin veya <span className="text-primary fw-medium">dosya seçin</span></div>
                  <div className="text-muted small">PNG, JPG, WebP — Birden fazla seçebilirsiniz</div>
                </div>
              )}
            </div>
          </div>
        </>
      )}

      <div className="d-flex justify-content-end gap-2">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onCancel}
          disabled={loading}
        >
          İptal
        </button>
        <button
          type="button"
          className="btn btn-primary"
          disabled={loading}
          onClick={handleSubmit}
        >
          {loading ? (
            <>
              <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
              Kaydediliyor...
            </>
          ) : (
            <>
              <i className="fas fa-save me-2"></i>
              {product ? 'Güncelle' : 'Kaydet'}
            </>
          )}
        </button>
      </div>

      {/* Create Modal */}
      <ConfirmModal
        show={!!deleteImageId}
        title="Gorseli Sil"
        message="Bu gorseli silmek istediginize emin misiniz? Bu islem geri alinamaz."
        icon="trash"
        confirmText="Sil"
        confirmVariant="danger"
        onConfirm={() => {
          const imgId = deleteImageId;
          setDeleteImageId(null);
          axios.delete(`/api/products/images/${imgId}`).then(() => {
            setProductImages(prev => prev.filter(i => i.id !== imgId));
          }).catch(() => {});
        }}
        onCancel={() => setDeleteImageId(null)}
      />

      {showCreateModal && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  {createModalType === 'brand' && <><i className="fas fa-copyright me-2"></i>Yeni Marka</>}
                  {createModalType === 'color' && <><i className="fas fa-palette me-2"></i>Yeni Renk</>}
                  {createModalType === 'category' && <><i className="fas fa-folder me-2"></i>Yeni Kategori</>}
                  {createModalType === 'subcategory' && <><i className="fas fa-folder-open me-2"></i>Yeni Alt Kategori</>}
                </h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowCreateModal(false)}
                  disabled={createModalLoading}
                ></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">
                    İsim <span className="text-danger">*</span>
                  </label>
                  <input
                    type="text"
                    className="form-control"
                    value={createModalName}
                    onChange={(e) => setCreateModalName(e.target.value)}
                    placeholder={
                      createModalType === 'brand' ? 'Marka adı' :
                      createModalType === 'color' ? 'Renk adı' :
                      createModalType === 'category' ? 'Kategori adı' :
                      'Alt kategori adı'
                    }
                    autoFocus
                    disabled={createModalLoading}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !createModalLoading) {
                        handleCreateSubmit();
                      }
                    }}
                  />
                </div>
                {createModalType === 'color' && (
                  <div className="mb-3">
                    <label className="form-label">Renk Kodu (Hex)</label>
                    <div className="input-group">
                      <input
                        type="color"
                        className="form-control form-control-color"
                        value={createModalColorHex}
                        onChange={(e) => setCreateModalColorHex(e.target.value)}
                        disabled={createModalLoading}
                        style={{ width: '60px', height: '38px' }}
                      />
                      <input
                        type="text"
                        className="form-control"
                        value={createModalColorHex}
                        onChange={(e) => setCreateModalColorHex(e.target.value)}
                        placeholder="#000000"
                        disabled={createModalLoading}
                        maxLength={7}
                      />
                    </div>
                  </div>
                )}
                {(createModalType === 'category' || createModalType === 'subcategory') && (
                  <div className="mb-3">
                    <label className="form-label">Açıklama (Opsiyonel)</label>
                    <textarea
                      className="form-control"
                      value={createModalDescription}
                      onChange={(e) => setCreateModalDescription(e.target.value)}
                      placeholder="Kategori açıklaması"
                      rows={3}
                      disabled={createModalLoading}
                    />
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setShowCreateModal(false)}
                  disabled={createModalLoading}
                >
                  İptal
                </button>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleCreateSubmit}
                  disabled={createModalLoading || !createModalName.trim()}
                >
                  {createModalLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Oluşturuluyor...
                    </>
                  ) : (
                    'Oluştur'
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </form>
    </div>
  );
};

export default ProductForm;
