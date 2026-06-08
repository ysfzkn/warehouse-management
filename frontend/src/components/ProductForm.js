import React, { useState, useEffect } from 'react';
import axios from 'axios';
import SearchableSelect from './SearchableSelect';
import ConfirmModal from './ConfirmModal';
import BundleMemberPicker from './BundleMemberPicker';
import './ProductForm.css';

/** Drop empty groups/items from the structured technical specs before saving. */
function cleanTechnicalSpecs(groups) {
  if (!Array.isArray(groups)) return [];
  return groups
    .map((g) => ({
      title: (g?.title || '').trim(),
      items: (Array.isArray(g?.items) ? g.items : [])
        .map((it) => ({ label: (it?.label || '').trim(), value: (it?.value || '').trim() }))
        .filter((it) => it.label || it.value),
    }))
    .filter((g) => g.title || g.items.length > 0);
}

/**
 * Crawler preview thumbnail: downloads via the backend proxy with a Referer
 * (for hotlink-protected CDNs) + attaches the JWT Bearer through axios.
 * <img src=...> does not work directly because the browser does not add an Authorization header.
 */
function CrawlThumbnail({ url, referer }) {
  const [blobUrl, setBlobUrl] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let cancelled = false;
    let createdBlobUrl = null;
    setBlobUrl(null);
    setFailed(false);
    (async () => {
      try {
        const res = await axios.get(`/api/admin/products/crawl-images/proxy`, {
          params: { url, referer },
          responseType: 'blob',
        });
        if (cancelled) return;
        createdBlobUrl = URL.createObjectURL(res.data);
        setBlobUrl(createdBlobUrl);
      } catch {
        if (!cancelled) setFailed(true);
      }
    })();
    return () => {
      cancelled = true;
      if (createdBlobUrl) URL.revokeObjectURL(createdBlobUrl);
    };
  }, [url, referer]);
  if (failed) return null;
  if (!blobUrl) {
    return (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#9ca3af',
          fontSize: '0.75rem',
        }}
      >
        Yükleniyor…
      </div>
    );
  }
  return <img src={blobUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />;
}

const ProductForm = ({ product, onSuccess, onCancel, setMode = false }) => {
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
    warrantyMonths: '',
    warrantyText: '',
    categoryId: '',
    subcategoryId: '',
    isActive: true,
  });
  const [productImages, setProductImages] = useState([]);
  const [imageUploading, setImageUploading] = useState(false);
  const [deleteImageId, setDeleteImageId] = useState(null);
  const [selectedImageIds, setSelectedImageIds] = useState(() => new Set());
  const [bulkDeletingImages, setBulkDeletingImages] = useState(false);
  const imageInputRef = React.useRef(null);
  // Structured technical specs: [{ title, items: [{ label, value }] }]
  const [technicalSpecs, setTechnicalSpecs] = useState([]);
  const [bundleMembers, setBundleMembers] = useState([]); // set mode: [{productId, name, sku, price, salePrice, quantity}]
  const [dragImageIndex, setDragImageIndex] = useState(null);
  const [dragOverIndex, setDragOverIndex] = useState(null);
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
      // If the entered price is VAT-inclusive, calculate backwards
      // totalPrice = priceWithSct * (1 + vatRate / 100)
      // priceWithSct = totalPrice / (1 + vatRate / 100)
      totalPrice = enteredPrice;
      priceWithSct = totalPrice / (1 + vatRate / 100);
      vatAmount = totalPrice - priceWithSct;

      // If there is SCT, calculate basePrice
      if (sctRate > 0) {
        // priceWithSct = basePrice * (1 + sctRate / 100)
        basePrice = priceWithSct / (1 + sctRate / 100);
        sctAmount = priceWithSct - basePrice;
      } else {
        basePrice = priceWithSct;
        sctAmount = 0;
      }
    } else {
      // If the entered price is VAT-exclusive, calculate forwards
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
      totalPrice,
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

    const leafCategoryIdRaw =
      product.category?.id ?? (product.categoryId != null ? Number(product.categoryId) : null);
    const parentCategoryIdRaw =
      product.category?.parent?.id ??
      (product.categoryParentId != null ? Number(product.categoryParentId) : null);

    const hasParent = parentCategoryIdRaw != null;
    const mainCategoryIdNumeric = hasParent ? parentCategoryIdRaw : leafCategoryIdRaw;
    const subcategoryIdNumeric = hasParent ? leafCategoryIdRaw : null;

    setFormData((prev) => ({
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
      warrantyMonths: product.warrantyMonths != null ? String(product.warrantyMonths) : '',
      warrantyText: product.warrantyText || '',
      categoryId: mainCategoryIdNumeric != null ? String(mainCategoryIdNumeric) : '',
      subcategoryId: subcategoryIdNumeric != null ? String(subcategoryIdNumeric) : '',
      isActive: product.isActive !== false,
      shortDescription: product.shortDescription || '',
      salePrice: product.salePrice || '',
      saleStart: product.saleStart ? product.saleStart.substring(0, 16) : '',
      saleEnd: product.saleEnd ? product.saleEnd.substring(0, 16) : '',
      isFeatured: !!product.featured || !!product.isFeatured,
      isNew: !!product.isNew,
      slug: product.slug || '',
      metaTitle: product.metaTitle || '',
      metaDescription: product.metaDescription || '',
    }));
    setTechnicalSpecs(Array.isArray(product.technicalSpecs) ? product.technicalSpecs : []);
    // Load product images
    if (product.id) {
      axios
        .get(`/api/products/${product.id}/images`)
        .then((r) => setProductImages(r.data || []))
        .catch(() => {});
    }
    // Set mode: load existing members (detail endpoint returns bundleItems)
    if (setMode && product.id) {
      if (Array.isArray(product.bundleItems) && product.bundleItems.length > 0) {
        setBundleMembers(
          product.bundleItems.map((b) => ({
            productId: b.productId,
            name: b.name,
            sku: b.sku,
            price: b.price,
            salePrice: b.salePrice,
            quantity: b.quantity || 1,
          }))
        );
      } else {
        axios
          .get(`/api/products/${product.id}`)
          .then((r) => {
            const items = Array.isArray(r.data?.bundleItems) ? r.data.bundleItems : [];
            setBundleMembers(
              items.map((b) => ({
                productId: b.productId,
                name: b.name,
                sku: b.sku,
                price: b.price,
                salePrice: b.salePrice,
                quantity: b.quantity || 1,
              }))
            );
          })
          .catch(() => {});
      }
    }
    const resolvedBrandId = product.brand?.id ?? (product.brandId != null ? Number(product.brandId) : null);
    const resolvedColorId = product.color?.id ?? (product.colorId != null ? Number(product.colorId) : null);

    setBrandId(resolvedBrandId);
    setColorId(resolvedColorId);

    if (mainCategoryIdNumeric != null) {
      (async () => {
        const subs = await fetchSubcategories(mainCategoryIdNumeric);
        if (
          subcategoryIdNumeric != null &&
          Array.isArray(subs) &&
          subs.some((s) => String(s.id) === String(subcategoryIdNumeric))
        ) {
          setFormData((prev) => ({ ...prev, subcategoryId: String(subcategoryIdNumeric) }));
        }
      })();
    } else {
      setSubcategories([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product]);

  const normalizeSubcategories = (subs = [], parentMeta = {}) => {
    return subs.map((sub) => ({
      ...sub,
      parentId: sub.parentId ?? parentMeta.id ?? null,
      parentName: sub.parentName ?? parentMeta.name ?? null,
      productCount: Number(sub.productCount ?? 0),
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
      const categoriesList = Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : [];
      const normalized = categoriesList.map((cat) => {
        const children = normalizeSubcategories(
          Array.isArray(cat.children)
            ? cat.children
            : Array.isArray(cat.subcategories)
              ? cat.subcategories
              : [],
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
      const parent = mainCategories.find((cat) => Number(cat.id) === parentIdNum);
      if (!forceRefresh && parent && Array.isArray(parent.children) && parent.children.length > 0) {
        const sorted = sortByName(parent.children);
        setSubcategories(sorted);
        return sorted;
      }
      const response = await axios.get(`/api/categories/${parentId}/subcategories`);
      const data = response.data || {};
      const subcategoriesList = Array.isArray(data.content) ? data.content : Array.isArray(data) ? data : [];
      const normalized = sortByName(
        normalizeSubcategories(subcategoriesList, { id: parentIdNum, name: parent?.name })
      );
      setSubcategories(normalized);
      if (parent) {
        setMainCategories((prev) =>
          prev.map((cat) => (cat.id === parentIdNum ? { ...cat, children: normalized } : cat))
        );
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
    const bgClass =
      type === 'success' ? 'text-bg-success' : type === 'warning' ? 'text-bg-warning' : 'text-bg-danger';
    const icon =
      type === 'success'
        ? 'fa-check-circle'
        : type === 'warning'
          ? 'fa-exclamation-triangle'
          : 'fa-times-circle';
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
    setTimeout(
      () => {
        try {
          toast.classList.remove('show');
          setTimeout(() => {
            try {
              document.body.removeChild(toast);
            } catch {}
          }, 300);
        } catch {}
      },
      type === 'success' ? 3000 : 5000
    );
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
          const existing = searchResponse.data?.find(
            (b) => b.name.toLowerCase() === trimmedName.toLowerCase()
          );
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
          const existing = searchResponse.data?.find(
            (c) => c.name.toLowerCase() === trimmedName.toLowerCase()
          );
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
          hexCode: createModalColorHex,
        });
        created = response.data;
        setColorId(created.id);
        showToast(`"${trimmedName}" rengi başarıyla oluşturuldu.`, 'success');
      } else if (createModalType === 'category') {
        // Check if category already exists
        try {
          const existing = mainCategories.find((c) => c.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setFormData((prev) => ({ ...prev, categoryId: String(existing.id) }));
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
          description: createModalDescription.trim() || null,
        });
        created = response.data;
        await fetchMainCategories();
        setFormData((prev) => ({ ...prev, categoryId: String(created.id) }));
        showToast(`"${trimmedName}" kategorisi başarıyla oluşturuldu.`, 'success');
      } else if (createModalType === 'subcategory') {
        if (!formData.categoryId) {
          showToast('Önce ana kategori seçiniz', 'warning');
          setCreateModalLoading(false);
          return;
        }

        // Check if subcategory already exists
        try {
          const existing = subcategories.find((s) => s.name.toLowerCase() === trimmedName.toLowerCase());
          if (existing) {
            setFormData((prev) => ({ ...prev, subcategoryId: String(existing.id) }));
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

        const response = await axios.post(
          '/api/categories/batch',
          [{ name: trimmedName, description: createModalDescription.trim() || null }],
          { params: { parentId: formData.categoryId } }
        );
        created = response.data[0];
        const updatedSubs = await fetchSubcategories(formData.categoryId, { forceRefresh: true });
        const createdMatch = updatedSubs.find((sub) => Number(sub.id) === Number(created?.id));
        const effectiveSub = createdMatch || {
          ...(created || {}),
          id: created?.id,
          name: created?.name ?? trimmedName,
          parentId: Number(formData.categoryId),
          parentName: mainCategories.find((cat) => cat.id?.toString() === formData.categoryId)?.name || null,
        };
        if (!createdMatch) {
          setSubcategories((prev) => sortByName([...(prev || []), effectiveSub]));
          setMainCategories((prev) =>
            prev.map((cat) => {
              if (cat.id?.toString() === formData.categoryId) {
                const updatedChildren = sortByName([...(cat.children || []), effectiveSub]);
                return { ...cat, children: updatedChildren };
              }
              return cat;
            })
          );
        }
        setFormData((prev) => ({ ...prev, subcategoryId: String(effectiveSub?.id ?? created?.id ?? '') }));
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
      if (
        error.response?.status === 409 ||
        errorMessage.toLowerCase().includes('zaten') ||
        errorMessage.toLowerCase().includes('mevcut') ||
        errorMessage.toLowerCase().includes('duplicate') ||
        errorMessage.toLowerCase().includes('exists')
      ) {
        const itemType =
          createModalType === 'brand'
            ? 'marka'
            : createModalType === 'color'
              ? 'renk'
              : createModalType === 'category'
                ? 'kategori'
                : 'alt kategori';
        showToast(`"${createModalName.trim()}" ${itemType} zaten mevcut.`, 'warning');
      } else {
        const itemType =
          createModalType === 'brand'
            ? 'Marka'
            : createModalType === 'color'
              ? 'Renk'
              : createModalType === 'category'
                ? 'Kategori'
                : 'Alt kategori';
        showToast(`${itemType} oluşturulurken hata: ${errorMessage}`, 'error');
      }
    } finally {
      setCreateModalLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;

    if (name === 'categoryId') {
      // Reset the subcategory when the main category changes
      setFormData((prev) => ({
        ...prev,
        [name]: value,
        subcategoryId: '', // Reset the subcategory
      }));
    } else {
      setFormData((prev) => ({
        ...prev,
        [name]: type === 'checkbox' ? checked : value,
      }));
    }

    // Clear error when user starts typing
    if (errors[name]) {
      setErrors((prev) => ({
        ...prev,
        [name]: '',
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

    // Price: parseFloat NaN check + must be positive (a price of 0 cannot be used in e-commerce)
    const priceNum = parseFloat(formData.price);
    if (!formData.price || isNaN(priceNum) || priceNum <= 0) {
      newErrors.price = 'Geçerli bir fiyat giriniz (sıfırdan büyük olmalı)';
    }

    if (!formData.categoryId) {
      newErrors.categoryId = 'Kategori seçiniz';
    }

    // Length validation aligned with backend constraints
    if (formData.description && formData.description.length > 5000) {
      newErrors.description = `Açıklama 5000 karakteri aşamaz (şu an ${formData.description.length}).`;
    }
    if (formData.shortDescription && formData.shortDescription.length > 1000) {
      newErrors.shortDescription = `Kısa açıklama 1000 karakteri aşamaz (şu an ${formData.shortDescription.length}).`;
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
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const res = await axios.get(`/api/products/${product.id}/images`);
      setProductImages(res.data || []);
    } catch (e) {
      setErrors({ general: e.response?.data?.message || 'Görsel yüklenemedi' });
    } finally {
      setImageUploading(false);
    }
  };

  // ─── Bulk image selection / delete ───
  const toggleImageSelect = (id) =>
    setSelectedImageIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  // ─── Drag-and-drop image ordering ───
  const persistImageOrder = async (orderedImages) => {
    setProductImages(orderedImages);
    if (!product?.id) return;
    try {
      await axios.put(
        `/api/products/${product.id}/images/reorder`,
        orderedImages.map((i) => i.id)
      );
    } catch {
      showToast('Sıralama kaydedilemedi, lütfen tekrar deneyin.', 'error');
    }
  };
  const handleImageDrop = (targetIndex) => {
    if (dragImageIndex === null || dragImageIndex === targetIndex) {
      setDragImageIndex(null);
      return;
    }
    const sorted = [...productImages].sort((a, b) => a.sortOrder - b.sortOrder);
    const [moved] = sorted.splice(dragImageIndex, 1);
    sorted.splice(targetIndex, 0, moved);
    const reindexed = sorted.map((img, i) => ({ ...img, sortOrder: i }));
    setDragImageIndex(null);
    persistImageOrder(reindexed);
  };
  // ─── Structured technical specs editor ───
  const addSpecGroup = () =>
    setTechnicalSpecs((p) => [...(p || []), { title: '', items: [{ label: '', value: '' }] }]);
  const removeSpecGroup = (gi) => setTechnicalSpecs((p) => (p || []).filter((_, i) => i !== gi));
  const updateSpecGroupTitle = (gi, title) =>
    setTechnicalSpecs((p) => (p || []).map((g, i) => (i === gi ? { ...g, title } : g)));
  const addSpecItem = (gi) =>
    setTechnicalSpecs((p) =>
      (p || []).map((g, i) =>
        i === gi ? { ...g, items: [...(g.items || []), { label: '', value: '' }] } : g
      )
    );
  const removeSpecItem = (gi, ii) =>
    setTechnicalSpecs((p) =>
      (p || []).map((g, i) => (i === gi ? { ...g, items: (g.items || []).filter((_, j) => j !== ii) } : g))
    );
  const updateSpecItem = (gi, ii, field, val) =>
    setTechnicalSpecs((p) =>
      (p || []).map((g, i) =>
        i === gi
          ? { ...g, items: (g.items || []).map((it, j) => (j === ii ? { ...it, [field]: val } : it)) }
          : g
      )
    );
  const toggleSelectAllImages = () =>
    setSelectedImageIds((prev) =>
      prev.size === productImages.length ? new Set() : new Set(productImages.map((i) => i.id))
    );
  const bulkDeleteImages = async () => {
    if (selectedImageIds.size === 0) return;
    if (!window.confirm(`${selectedImageIds.size} görsel kalıcı olarak silinecek. Emin misiniz?`)) return;
    setBulkDeletingImages(true);
    try {
      const ids = Array.from(selectedImageIds);
      await axios.delete('/api/products/images/bulk', { data: ids });
      setProductImages((prev) => prev.filter((i) => !selectedImageIds.has(i.id)));
      setSelectedImageIds(new Set());
    } catch (e) {
      setErrors({ general: e.response?.data?.message || 'Görseller silinemedi' });
    } finally {
      setBulkDeletingImages(false);
    }
  };

  // ─── Profilo / 3rd-party URL crawler ──────────────────────────────
  // Client-side check kept in sync with the backend allowlist (for UX).
  // Shows an instant warning when a wrong URL is entered, preventing an unnecessary API call.
  const SUPPORTED_DOMAINS = [
    'profilo.com',
    'profilo.com.tr',
    'siemens.com.tr',
    'siemens-home.com.tr',
    'siemens-home.bsh-group.com',
    'bosch-home.com',
    'bosch-home.com.tr',
    'lg.com',
    'lg.com.tr',
    'miele.com',
    'miele.com.tr',
    'haier.com',
    'haier.com.tr',
    'fakir.com.tr',
    'fakir.com',
    'simfer.com.tr',
    'simfer.com',
    'ferreturkiye.com',
    'kaercher.com',
    'kumtel.com',
    'tefal.com.tr',
    'tefal.com',
    'braunshop.com.tr',
  ];
  const isLikelySupportedUrl = (url) => {
    if (!url || typeof url !== 'string') return false;
    try {
      const u = new URL(url.trim());
      if (!/^https?:$/.test(u.protocol)) return false;
      const host = u.hostname.toLowerCase();
      return SUPPORTED_DOMAINS.some((d) => host === d || host.endsWith('.' + d));
    } catch {
      return false;
    }
  };

  const [crawlOpen, setCrawlOpen] = useState(false);
  const [crawlUrl, setCrawlUrl] = useState('');
  const [crawlLoading, setCrawlLoading] = useState(false);
  const [crawlError, setCrawlError] = useState('');
  const [crawlPreview, setCrawlPreview] = useState(null); // { url, title, images, description, shortDescription, specs, brand }
  const [crawlSelected, setCrawlSelected] = useState(new Set());
  const [crawlReplace, setCrawlReplace] = useState(false);
  const [crawlImporting, setCrawlImporting] = useState(false);
  const [crawlResult, setCrawlResult] = useState(null); // { success, total, errors: [] }
  // Description preview state — the admin can edit the text, then transfer it to ProductForm via "Apply"
  const [crawlEditableDesc, setCrawlEditableDesc] = useState('');
  const [crawlEditableShortDesc, setCrawlEditableShortDesc] = useState('');
  const [crawlEditableSpecs, setCrawlEditableSpecs] = useState([]); // [{key, value}]
  const [crawlActiveTab, setCrawlActiveTab] = useState('images'); // 'images' | 'description'
  const [crawlDescAppliedToast, setCrawlDescAppliedToast] = useState(false);

  const openCrawlModal = () => {
    setCrawlOpen(true);
    setCrawlUrl('');
    setCrawlError('');
    setCrawlPreview(null);
    setCrawlSelected(new Set());
    setCrawlReplace(false);
    setCrawlResult(null);
  };

  const fetchCrawlPreview = async () => {
    if (!product?.id) {
      setCrawlError('Önce ürünü kaydetmeniz gerekiyor.');
      return;
    }
    if (!crawlUrl.trim()) return;
    setCrawlLoading(true);
    setCrawlError('');
    setCrawlPreview(null);
    setCrawlResult(null);
    try {
      const res = await axios.post(`/api/admin/products/${product.id}/crawl-images/preview`, {
        url: crawlUrl.trim(),
      });
      const data = res.data || {};
      setCrawlPreview(data);
      // Default: select all
      setCrawlSelected(new Set(data.images || []));
      // Hydrate the description state (the admin can edit it)
      setCrawlEditableDesc(data.description || '');
      setCrawlEditableShortDesc(data.shortDescription || '');
      // Specs Map → editable array
      const specsObj = data.specs || {};
      setCrawlEditableSpecs(Object.entries(specsObj).map(([k, v]) => ({ key: k, value: v })));
      // If no description was fetched, stay on the images tab; if present, also suggest the description tab
      if (data.description || data.shortDescription) {
        setCrawlActiveTab('images'); // default start is still images
      }
    } catch (e) {
      setCrawlError(e.response?.data?.message || 'Görseller çekilemedi');
    } finally {
      setCrawlLoading(false);
    }
  };

  const toggleCrawlSelection = (url) => {
    setCrawlSelected((prev) => {
      const next = new Set(prev);
      if (next.has(url)) next.delete(url);
      else next.add(url);
      return next;
    });
  };

  const selectAllCrawl = () => {
    if (!crawlPreview) return;
    setCrawlSelected(new Set(crawlPreview.images || []));
  };

  const deselectAllCrawl = () => setCrawlSelected(new Set());

  /**
   * Transfers the edited description + specs into ProductForm's actual fields.
   *
   * Bug fixes:
   *   - Backend description @Size(max=5000) — truncate at 4800 chars (margin for HTML tags)
   *   - shortDescription @Column(length=1000) — truncate at 1000
   *   - asynchronous update guaranteed via the setFormData callback form
   *   - automatically close the modal and flash-highlight the form fields (visual feedback)
   *   - toast reminding to press Save (so the user does not think "was it auto-saved on transfer?")
   */
  const applyCrawlDescriptionToProduct = () => {
    const updates = {};

    // Short description (1000 char limit)
    if (crawlEditableShortDesc && crawlEditableShortDesc.trim()) {
      const trimmed = crawlEditableShortDesc.trim();
      updates.shortDescription = trimmed.length > 1000 ? trimmed.substring(0, 997) + '...' : trimmed;
    }

    // Long description (plain text; specs go to the STRUCTURED technical specs below).
    let fullDesc = (crawlEditableDesc || '').trim();
    if (fullDesc.length > 4800) {
      fullDesc = fullDesc.substring(0, 4800) + '\n\n... (açıklama kısaltıldı)';
    }
    if (fullDesc) {
      updates.description = fullDesc;
    }

    // Structured technical specs (replaces a previously auto-imported group).
    const validSpecs = crawlEditableSpecs.filter((s) => s.key.trim() && s.value.trim());
    let specsApplied = false;
    if (validSpecs.length > 0) {
      const importedGroup = {
        title: 'Teknik Özellikler',
        items: validSpecs.map((s) => ({ label: s.key.trim(), value: s.value.trim() })),
      };
      setTechnicalSpecs((prev) => {
        const others = (Array.isArray(prev) ? prev : []).filter(
          (g) => (g.title || '').trim() !== 'Teknik Özellikler'
        );
        return [...others, importedGroup];
      });
      specsApplied = true;
    }

    if (Object.keys(updates).length === 0 && !specsApplied) {
      showToast('Aktarılacak açıklama veya özellik yok', 'warning');
      return;
    }

    // CRITICAL: update state via the prev callback form — prevents a race condition
    setFormData((prev) => ({ ...prev, ...updates }));
    setCrawlDescAppliedToast(true);

    const fieldList = [];
    if (updates.shortDescription) fieldList.push('Kısa Açıklama');
    if (updates.description) fieldList.push('Açıklama');

    showToast(`✓ ${fieldList.join(' + ')} forma aktarıldı. KAYDET'e basmayı unutmayın!`, 'success');

    // Automatically close the modal and scroll to the form fields + flash highlight
    setTimeout(() => {
      setCrawlOpen(false);
      // Scroll to the form fields + a 2-second flash highlight
      const descField = document.getElementById('description');
      const shortField = document.getElementById('shortDescription');
      const target = descField || shortField;
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        [descField, shortField].filter(Boolean).forEach((el) => {
          el.style.transition = 'box-shadow 0.3s ease, background-color 0.3s ease';
          el.style.boxShadow = '0 0 0 3px rgba(34, 197, 94, 0.4)';
          el.style.backgroundColor = '#dcfce7';
          setTimeout(() => {
            el.style.boxShadow = '';
            el.style.backgroundColor = '';
          }, 2500);
        });
      }
      setCrawlDescAppliedToast(false);
    }, 400);
  };

  const updateCrawlSpec = (index, field, value) => {
    setCrawlEditableSpecs((prev) => prev.map((s, i) => (i === index ? { ...s, [field]: value } : s)));
  };

  const removeCrawlSpec = (index) => {
    setCrawlEditableSpecs((prev) => prev.filter((_, i) => i !== index));
  };

  const addCrawlSpec = () => {
    setCrawlEditableSpecs((prev) => [...prev, { key: '', value: '' }]);
  };

  const importCrawled = async () => {
    if (!product?.id || crawlSelected.size === 0) return;
    setCrawlImporting(true);
    setCrawlError('');
    try {
      const res = await axios.post(`/api/admin/products/${product.id}/crawl-images/import`, {
        imageUrls: Array.from(crawlSelected),
        replaceExisting: crawlReplace,
        markFirstAsPrimary: productImages.length === 0 || crawlReplace,
        pageUrl: crawlUrl.trim(),
      });
      setCrawlResult(res.data);
      // Reload product images
      const imgs = await axios.get(`/api/products/${product.id}/images`);
      setProductImages(imgs.data || []);
      // Toast notification
      const { success = 0, total = 0, errors = [] } = res.data || {};
      if (success > 0 && errors.length === 0) {
        showToast(`${success}/${total} görsel başarıyla yüklendi`, 'success');
      } else if (success > 0 && errors.length > 0) {
        showToast(`${success}/${total} yüklendi — ${errors.length} hata`, 'warning');
      } else {
        showToast(`Hiçbir görsel yüklenemedi (${errors.length} hata)`, 'error');
      }
    } catch (e) {
      const msg = e.response?.data?.message || 'İçe aktarma başarısız';
      setCrawlError(msg);
      showToast(msg, 'error');
    } finally {
      setCrawlImporting(false);
    }
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

    if (setMode && bundleMembers.length === 0) {
      setErrors((prev) => ({ ...prev, general: 'Bir set en az bir üye ürün içermelidir.' }));
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
        warrantyMonths: formData.warrantyMonths ? parseInt(formData.warrantyMonths, 10) : null,
        warrantyText: formData.warrantyText?.trim() || null,
        category: { id: parseInt(formData.subcategoryId || formData.categoryId) },
        brand: brandId ? { id: brandId } : null,
        color: colorId ? { id: colorId } : null,
        isActive: formData.isActive,
        slug: formData.slug || null,
        metaTitle: formData.metaTitle || null,
        metaDescription: formData.metaDescription || null,
        technicalSpecs: cleanTechnicalSpecs(technicalSpecs),
        productType: setMode ? 'BUNDLE' : 'SIMPLE',
        bundleMemberRefs: setMode
          ? bundleMembers.map((m, i) => ({
              productId: m.productId,
              quantity: Math.max(1, m.quantity || 1),
              sortOrder: i,
            }))
          : undefined,
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
      // Build friendly message from validation details or general message
      let friendlyMessage = '';
      if (errorData?.details && typeof errorData.details === 'object') {
        friendlyMessage = Object.values(errorData.details).join(', ');
      } else {
        friendlyMessage =
          errorData?.message ||
          errorData?.error ||
          (typeof errorData === 'string' ? errorData : null) ||
          'Ürün kaydedilirken beklenmeyen bir hata oluştu.';
      }
      setErrors({ general: friendlyMessage });
      // Show error toast
      const t = document.createElement('div');
      t.className = 'toast align-items-center text-bg-danger border-0 position-fixed top-0 end-0 m-3 show';
      t.style.cssText = 'min-width:340px;z-index:9999;animation:fadeInDown 0.3s ease';
      t.setAttribute('role', 'alert');
      t.innerHTML = `<div class="d-flex align-items-center px-3 py-2"><i class="fas fa-exclamation-circle me-2"></i><div class="toast-body fw-medium">${friendlyMessage}</div><button type="button" class="btn-close btn-close-white ms-auto" onclick="this.parentElement.parentElement.remove()"></button></div>`;
      document.body.appendChild(t);
      setTimeout(() => {
        try {
          document.body.removeChild(t);
        } catch {}
      }, 5000);
      // Do NOT call onSuccess on error — keep form open
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

        {setMode && (
          <BundleMemberPicker
            members={bundleMembers}
            onChange={setBundleMembers}
            excludeProductId={product?.id}
          />
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
          <label htmlFor="description" className="form-label d-flex justify-content-between">
            <span>Açıklama</span>
            <span
              className={`small ${(formData.description || '').length > 5000 ? 'text-danger fw-semibold' : 'text-muted'}`}
            >
              {(formData.description || '').length} / 5000
            </span>
          </label>
          <textarea
            className={`form-control ${errors.description ? 'is-invalid' : ''}`}
            id="description"
            name="description"
            rows="5"
            value={formData.description || ''}
            onChange={handleChange}
            placeholder="Detaylı ürün açıklaması..."
          />
          {errors.description && <div className="invalid-feedback">{errors.description}</div>}
        </div>
        <div className="mb-3">
          <label htmlFor="shortDescription" className="form-label d-flex justify-content-between">
            <span>
              Kısa Açıklama <small className="text-muted">(mağazada listede görünür)</small>
            </span>
            <span
              className={`small ${(formData.shortDescription || '').length > 1000 ? 'text-danger fw-semibold' : 'text-muted'}`}
            >
              {(formData.shortDescription || '').length} / 1000
            </span>
          </label>
          <textarea
            className={`form-control ${errors.shortDescription ? 'is-invalid' : ''}`}
            id="shortDescription"
            name="shortDescription"
            rows="2"
            value={formData.shortDescription || ''}
            onChange={handleChange}
            placeholder="Mağazada ürün kartında görünecek kısa açıklama..."
            maxLength={1000}
          />
          {errors.shortDescription && <div className="invalid-feedback">{errors.shortDescription}</div>}
        </div>

        {/* Technical Specifications (structured — shown in a clean section on the store) */}
        <div className="mb-3">
          <div className="d-flex align-items-center mb-2">
            <h6 className="text-muted mb-0">
              <i className="fas fa-list-ul me-2" />
              Teknik Özellikler
            </h6>
            <button type="button" className="btn btn-sm btn-outline-primary ms-auto" onClick={addSpecGroup}>
              <i className="fas fa-plus me-1" />
              Grup Ekle
            </button>
          </div>
          {(!technicalSpecs || technicalSpecs.length === 0) && (
            <div className="text-muted small mb-2">
              Henüz teknik özellik yok. "Grup Ekle" ile manuel girin veya yukarıdaki URL'den crawl ederek
              otomatik doldurun.
            </div>
          )}
          {(technicalSpecs || []).map((group, gi) => (
            <div key={gi} className="border rounded p-2 mb-2 bg-light">
              <div className="d-flex align-items-center gap-2 mb-2">
                <input
                  className="form-control form-control-sm fw-semibold"
                  placeholder="Grup başlığı (ör: Genel Özellikler, Boyutlar, Güvenlik)"
                  value={group.title || ''}
                  onChange={(e) => updateSpecGroupTitle(gi, e.target.value)}
                />
                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger"
                  title="Grubu sil"
                  onClick={() => removeSpecGroup(gi)}
                >
                  <i className="fas fa-trash" />
                </button>
              </div>
              {(group.items || []).map((it, ii) => (
                <div key={ii} className="row g-1 mb-1 align-items-center">
                  <div className="col-5">
                    <input
                      className="form-control form-control-sm"
                      placeholder="Etiket (ör: Enerji Sınıfı)"
                      value={it.label || ''}
                      onChange={(e) => updateSpecItem(gi, ii, 'label', e.target.value)}
                    />
                  </div>
                  <div className="col-6">
                    <input
                      className="form-control form-control-sm"
                      placeholder="Değer (ör: A++)"
                      value={it.value || ''}
                      onChange={(e) => updateSpecItem(gi, ii, 'value', e.target.value)}
                    />
                  </div>
                  <div className="col-1 text-end">
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary"
                      title="Satırı sil"
                      onClick={() => removeSpecItem(gi, ii)}
                    >
                      <i className="fas fa-times" />
                    </button>
                  </div>
                </div>
              ))}
              <button type="button" className="btn btn-sm btn-link p-0 mt-1" onClick={() => addSpecItem(gi)}>
                <i className="fas fa-plus me-1" />
                Satır Ekle
              </button>
            </div>
          ))}
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
                  <label
                    className="form-check-label ms-2"
                    htmlFor="priceIncludesVat"
                    style={{ cursor: 'pointer' }}
                  >
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
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, vatRate: '1' })}
                >
                  %1
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, vatRate: '10' })}
                >
                  %10
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, vatRate: '20' })}
                >
                  %20
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, vatRate: '0' })}
                >
                  Muaf
                </button>
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
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, sctRate: '0' })}
                >
                  Yok
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, sctRate: '10' })}
                >
                  %10
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setFormData({ ...formData, sctRate: '50' })}
                >
                  %50
                </button>
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
                  {Array.isArray(mainCategories) &&
                    mainCategories.map((category) => (
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
                  {Array.isArray(subcategories) &&
                    subcategories.map((subcategory) => (
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
              {!formData.categoryId && <small className="text-muted">Önce ana kategori seçin</small>}
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
                    flexShrink: 0,
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
                        <span
                          className="me-2"
                          style={{
                            display: 'inline-block',
                            width: 12,
                            height: 12,
                            backgroundColor: opt.hexCode || '#ccc',
                            border: '1px solid #ccc',
                          }}
                        ></span>
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
                    flexShrink: 0,
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
            <h6
              className="text-muted mb-4"
              style={{
                marginTop: '0.5rem',
                marginBottom: '1.5rem',
                fontSize: '1rem',
                fontWeight: '600',
                paddingBottom: '0.75rem',
                borderBottom: '1px solid #e9ecef',
              }}
            >
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

        {/* Warranty */}
        <div className="row mb-3">
          <div className="col-12">
            <h6 className="text-muted mb-3">
              <i className="fas fa-shield-alt me-2"></i>
              Garanti
            </h6>
          </div>
          <div className="col-md-4 col-12">
            <div className="mb-3">
              <label className="form-label" htmlFor="warrantyMonths">
                Garanti Süresi (Ay)
              </label>
              <input
                type="number"
                min="0"
                className="form-control"
                id="warrantyMonths"
                name="warrantyMonths"
                value={formData.warrantyMonths}
                onChange={handleChange}
                placeholder="ör. 24"
              />
            </div>
          </div>
          <div className="col-md-8 col-12">
            <div className="mb-3">
              <label className="form-label" htmlFor="warrantyText">
                Garanti Açıklaması
              </label>
              <input
                type="text"
                className="form-control"
                id="warrantyText"
                name="warrantyText"
                value={formData.warrantyText}
                onChange={handleChange}
                placeholder="ör. 24 ay üretici garantisi"
                maxLength={500}
              />
              <small className="text-muted">
                Boş bırakılırsa kategori garantisi geçerli olur (kategori → üst kategori).
              </small>
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
                    <strong className="text-primary">
                      ₺{parseFloat(formData.price).toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                    </strong>
                  </div>
                )}
                <div className="d-flex justify-content-between small">
                  <span className="text-muted">Ana Fiyat:</span>
                  <strong>
                    ₺{calculateTotalPrice().basePrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                  </strong>
                </div>
                {calculateTotalPrice().sctAmount > 0 && (
                  <>
                    <div className="d-flex justify-content-between small text-success">
                      <span>+ ÖTV (%{formData.sctRate}):</span>
                      <span>
                        ₺
                        {calculateTotalPrice().sctAmount.toLocaleString('tr-TR', {
                          minimumFractionDigits: 2,
                        })}
                      </span>
                    </div>
                    <div className="d-flex justify-content-between small">
                      <span className="text-muted">ÖTV'li Fiyat:</span>
                      <span>
                        ₺
                        {calculateTotalPrice().priceWithSct.toLocaleString('tr-TR', {
                          minimumFractionDigits: 2,
                        })}
                      </span>
                    </div>
                  </>
                )}
                {calculateTotalPrice().vatAmount > 0 && (
                  <div className="d-flex justify-content-between small text-success">
                    <span>+ KDV (%{formData.vatRate}):</span>
                    <span>
                      ₺{calculateTotalPrice().vatAmount.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                    </span>
                  </div>
                )}
                <hr className="my-1" />
                <div className="d-flex justify-content-between">
                  <strong className="text-dark">Toplam Fiyat:</strong>
                  <strong className="text-success fs-5">
                    ₺{calculateTotalPrice().totalPrice.toLocaleString('tr-TR', { minimumFractionDigits: 2 })}
                  </strong>
                </div>
              </div>
              <div className="col-12 col-md-6">
                <small className="text-muted d-block">
                  <i className="fas fa-lightbulb me-1 text-warning"></i>
                  <strong>Hesaplama Formülü:</strong>
                </small>
                {priceIncludesVat ? (
                  <>
                    <small className="text-muted d-block">
                      1. <strong>Girilen Fiyat = KDV Dahil Fiyat</strong>
                    </small>
                    <small className="text-muted d-block">
                      2. ÖTV'li Fiyat = KDV Dahil Fiyat ÷ (1 + KDV%)
                    </small>
                    {calculateTotalPrice().sctAmount > 0 && (
                      <small className="text-muted d-block">3. Ana Fiyat = ÖTV'li Fiyat ÷ (1 + ÖTV%)</small>
                    )}
                    <small className="text-muted d-block">
                      4. KDV Tutarı = KDV Dahil Fiyat - ÖTV'li Fiyat
                    </small>
                  </>
                ) : (
                  <>
                    <small className="text-muted d-block">1. ÖTV Tutarı = Ana Fiyat × ÖTV%</small>
                    <small className="text-muted d-block">2. ÖTV'li Fiyat = Ana Fiyat + ÖTV</small>
                    <small className="text-muted d-block">3. KDV Tutarı = ÖTV'li Fiyat × KDV%</small>
                    <small className="text-muted d-block">
                      4. <strong>Toplam = ÖTV'li Fiyat + KDV</strong>
                    </small>
                  </>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ===== Discount & Campaign ===== */}
        <div className="row mb-3 mt-4">
          <div className="col-12">
            <h6 className="text-muted mb-3">
              <i className="fas fa-percentage me-2" />
              İndirim & Kampanya
            </h6>
          </div>
        </div>
        <div className="row">
          <div className="col-md-6">
            <div className="mb-3">
              <label className="form-label fw-medium">İndirim Uygula</label>
              {(() => {
                const origPrice = parseFloat(formData.price) || 0;
                const salePrice = parseFloat(formData.salePrice) || 0;
                const hasDiscount = salePrice > 0 && origPrice > 0 && salePrice < origPrice;
                const discountPercent = hasDiscount ? (1 - salePrice / origPrice) * 100 : 0;

                const handleSalePriceChange = (e) => {
                  setFormData((f) => ({ ...f, salePrice: e.target.value }));
                };

                const handlePercentChange = (e) => {
                  const pct = parseFloat(e.target.value) || 0;
                  if (origPrice > 0 && pct > 0 && pct < 100) {
                    const calc = (origPrice * (1 - pct / 100)).toFixed(2);
                    setFormData((f) => ({ ...f, salePrice: calc }));
                  } else if (pct === 0 || e.target.value === '') {
                    setFormData((f) => ({ ...f, salePrice: '' }));
                  }
                };

                return (
                  <div className="border rounded p-3 bg-light">
                    <div className="row g-2 align-items-end">
                      <div className="col-6">
                        <label className="form-label small text-muted mb-1">Satış Fiyatı (₺)</label>
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          className="form-control"
                          value={formData.salePrice}
                          onChange={handleSalePriceChange}
                          placeholder={origPrice > 0 ? `Mevcut: ${origPrice}₺` : 'Fiyat girin'}
                        />
                      </div>
                      <div className="col-6">
                        <label className="form-label small text-muted mb-1">veya İndirim (%)</label>
                        <div className="input-group">
                          <span className="input-group-text">%</span>
                          <input
                            type="number"
                            step="1"
                            min="0"
                            max="99"
                            className="form-control"
                            value={hasDiscount ? discountPercent.toFixed(0) : ''}
                            onChange={handlePercentChange}
                            placeholder="0"
                          />
                        </div>
                      </div>
                    </div>
                    {hasDiscount && (
                      <div className="mt-2 d-flex align-items-center gap-2">
                        <span className="badge bg-danger">%{discountPercent.toFixed(0)} indirim</span>
                        <small className="text-muted">
                          <del>{origPrice.toFixed(2)}₺</del> →{' '}
                          <strong className="text-success">{salePrice.toFixed(2)}₺</strong>
                        </small>
                      </div>
                    )}
                    {formData.salePrice && origPrice > 0 && salePrice >= origPrice && (
                      <small className="text-danger mt-1 d-block">
                        <i className="fas fa-exclamation-triangle me-1" />
                        Satış fiyatı orijinal fiyattan düşük olmalıdır.
                      </small>
                    )}
                    <small className="text-muted d-block mt-2">
                      Satış fiyatı girildiğinde mağazada indirimli olarak gösterilir. Boş bırakırsanız indirim
                      uygulanmaz.
                    </small>
                  </div>
                );
              })()}
            </div>
          </div>
          <div className="col-md-3">
            <div className="mb-3">
              <label className="form-label fw-medium">İndirim Başlangıcı</label>
              <input
                type="datetime-local"
                className="form-control"
                name="saleStart"
                value={formData.saleStart}
                onChange={handleChange}
              />
            </div>
          </div>
          <div className="col-md-3">
            <div className="mb-3">
              <label className="form-label fw-medium">İndirim Bitişi</label>
              <input
                type="datetime-local"
                className="form-control"
                name="saleEnd"
                value={formData.saleEnd}
                onChange={handleChange}
              />
            </div>
          </div>
        </div>
        <div className="row mb-3">
          <div className="col-md-6">
            <div className="form-check form-switch">
              <input
                className="form-check-input"
                type="checkbox"
                id="isFeatured"
                checked={formData.isFeatured}
                onChange={(e) => setFormData((f) => ({ ...f, isFeatured: e.target.checked }))}
              />
              <label className="form-check-label" htmlFor="isFeatured">
                <i className="fas fa-star text-warning me-1" />
                Öne Çıkan Ürün
              </label>
            </div>
          </div>
          <div className="col-md-6">
            <div className="form-check form-switch">
              <input
                className="form-check-input"
                type="checkbox"
                id="isNew"
                checked={formData.isNew}
                onChange={(e) => setFormData((f) => ({ ...f, isNew: e.target.checked }))}
              />
              <label className="form-check-label" htmlFor="isNew">
                <i className="fas fa-sparkles text-info me-1" />
                Yeni Ürün Etiketi
              </label>
            </div>
          </div>
        </div>

        {/* ===== Product Images (edit mode only) ===== */}
        {product?.id && (
          <>
            <div className="row mb-3 mt-4">
              <div className="col-12">
                <h6 className="text-muted mb-3">
                  <i className="fas fa-images me-2" />
                  Ürün Görselleri
                </h6>
              </div>
            </div>
            <div className="mb-3">
              {/* Bulk selection toolbar */}
              {productImages.length > 0 && (
                <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={toggleSelectAllImages}
                  >
                    <i className="fas fa-check-double me-1" />
                    {selectedImageIds.size === productImages.length && productImages.length > 0
                      ? 'Seçimi Temizle'
                      : 'Tümünü Seç'}
                  </button>
                  {selectedImageIds.size > 0 && (
                    <button
                      type="button"
                      className="btn btn-sm btn-danger"
                      onClick={bulkDeleteImages}
                      disabled={bulkDeletingImages}
                    >
                      {bulkDeletingImages ? (
                        <span className="spinner-border spinner-border-sm me-1" />
                      ) : (
                        <i className="fas fa-trash me-1" />
                      )}
                      Seçilenleri Sil ({selectedImageIds.size})
                    </button>
                  )}
                  <small className="text-muted ms-auto">
                    <i className="fas fa-arrows-alt me-1" />
                    Sürükleyerek sırala · {productImages.length} görsel
                  </small>
                </div>
              )}
              {/* Image Grid */}
              {productImages.length > 0 && (
                <div className="row g-2 mb-3">
                  {[...productImages]
                    .sort((a, b) => a.sortOrder - b.sortOrder)
                    .map((img, index) => (
                      <div key={img.id} className="col-6 col-md-3">
                        <div
                          draggable
                          onDragStart={() => setDragImageIndex(index)}
                          onDragOver={(e) => {
                            e.preventDefault();
                            if (dragImageIndex !== null && dragOverIndex !== index) setDragOverIndex(index);
                          }}
                          onDragLeave={() => setDragOverIndex((cur) => (cur === index ? null : cur))}
                          onDrop={() => {
                            handleImageDrop(index);
                            setDragOverIndex(null);
                          }}
                          onDragEnd={() => {
                            setDragImageIndex(null);
                            setDragOverIndex(null);
                          }}
                          style={{
                            cursor: 'grab',
                            transition: 'box-shadow 0.15s ease, transform 0.15s ease',
                            ...(dragImageIndex !== null && dragOverIndex === index && dragImageIndex !== index
                              ? { boxShadow: '0 0 0 3px #2563eb', transform: 'scale(1.03)' }
                              : {}),
                          }}
                          title="Sürükleyerek sırala"
                          className={`border rounded overflow-hidden position-relative ${dragImageIndex === index ? 'opacity-50' : ''} ${selectedImageIds.has(img.id) ? 'border-danger border-2' : img.primary ? 'border-primary border-2' : ''}`}
                        >
                          {/* Drop-here indicator — shows where the dragged photo will land */}
                          {dragImageIndex !== null && dragOverIndex === index && dragImageIndex !== index && (
                            <>
                              <div
                                style={{
                                  position: 'absolute',
                                  left: -6,
                                  top: 0,
                                  bottom: 0,
                                  width: 4,
                                  background: '#2563eb',
                                  borderRadius: 4,
                                  zIndex: 4,
                                }}
                              />
                              <div
                                className="position-absolute top-50 start-50 translate-middle badge bg-primary"
                                style={{ zIndex: 4 }}
                              >
                                Buraya
                              </div>
                            </>
                          )}
                          <div className="position-absolute top-0 start-0 p-1" style={{ zIndex: 2 }}>
                            <input
                              type="checkbox"
                              className="form-check-input"
                              style={{ cursor: 'pointer', width: 18, height: 18 }}
                              checked={selectedImageIds.has(img.id)}
                              onChange={() => toggleImageSelect(img.id)}
                              title="Toplu silme için seç"
                            />
                          </div>
                          <img
                            src={`/api/admin/products/images/${img.id}/view?thumbnail=true`}
                            alt=""
                            style={{
                              width: '100%',
                              height: 140,
                              objectFit: 'contain',
                              background: '#f8f9fa',
                            }}
                            onError={(e) => {
                              e.target.style.display = 'none';
                            }}
                          />
                          <div className="position-absolute top-0 end-0 p-1 d-flex gap-1">
                            {!img.primary && (
                              <button
                                className="btn btn-sm btn-warning"
                                title="Birincil yap"
                                style={{ width: 24, height: 24, padding: 0, fontSize: 10 }}
                                onClick={() => {
                                  axios
                                    .put(`/api/products/images/${img.id}/set-primary`)
                                    .then(() => {
                                      axios
                                        .get(`/api/products/${product.id}/images`)
                                        .then((r) => setProductImages(r.data || []));
                                    })
                                    .catch(() => {});
                                }}
                              >
                                <i className="fas fa-star" />
                              </button>
                            )}
                            <button
                              className="btn btn-sm btn-danger"
                              title="Sil"
                              style={{ width: 24, height: 24, padding: 0, fontSize: 10 }}
                              onClick={() => setDeleteImageId(img.id)}
                            >
                              <i className="fas fa-times" />
                            </button>
                          </div>
                          {img.primary && (
                            <div
                              className="position-absolute bottom-0 start-0 w-100 text-center"
                              style={{ background: 'rgba(37,99,235,0.8)', padding: '2px 0' }}
                            >
                              <small className="text-white" style={{ fontSize: 10 }}>
                                Ana Görsel
                              </small>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                </div>
              )}
              {/* Upload Zone */}
              <div
                className="border-2 border-dashed rounded text-center"
                style={{ cursor: 'pointer', padding: '24px 16px' }}
                onClick={() => imageInputRef.current?.click()}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  const files = Array.from(e.dataTransfer.files).filter((f) => f.type.startsWith('image/'));
                  files.forEach((f) => uploadImage(f));
                }}
              >
                <input
                  type="file"
                  ref={imageInputRef}
                  className="d-none"
                  accept="image/*"
                  multiple
                  onChange={(e) => {
                    Array.from(e.target.files).forEach((f) => uploadImage(f));
                    e.target.value = '';
                  }}
                />
                {imageUploading ? (
                  <div className="py-2">
                    <span className="spinner-border spinner-border-sm text-primary me-2" />
                    Yükleniyor...
                  </div>
                ) : (
                  <div>
                    <div className="mb-2">
                      <i className="fas fa-cloud-upload-alt text-muted" style={{ fontSize: 28 }} />
                    </div>
                    <div className="small text-muted">
                      Görselleri sürükleyin veya <span className="text-primary fw-medium">dosya seçin</span>
                    </div>
                    <div className="text-muted mt-1" style={{ fontSize: 11 }}>
                      PNG, JPG, WebP — Birden fazla seçebilirsiniz
                    </div>
                  </div>
                )}
              </div>

              {/* Auto-fetch from URL */}
              <div className="mt-3 d-flex justify-content-center">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-info"
                  onClick={openCrawlModal}
                  disabled={!product?.id}
                >
                  <i className="fas fa-globe me-2" />
                  Üretici Sayfasından Görsel Çek (Profilo, Siemens, Bosch...)
                </button>
              </div>
              {!product?.id && (
                <div className="text-center small text-muted mt-1">
                  URL'den çekmek için önce ürünü kaydedin
                </div>
              )}
            </div>
          </>
        )}

        {/* ─── Crawl modal ─── */}
        {crawlOpen && (
          <div
            className="modal show d-block"
            tabIndex="-1"
            style={{ background: 'rgba(0,0,0,0.5)' }}
            onClick={() => !crawlLoading && !crawlImporting && setCrawlOpen(false)}
          >
            <div
              className="modal-dialog modal-lg modal-dialog-scrollable"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">
                    <i className="fas fa-globe text-info me-2" />
                    Üretici Sayfasından Görsel İndir
                  </h5>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setCrawlOpen(false)}
                    disabled={crawlLoading || crawlImporting}
                  ></button>
                </div>
                <div className="modal-body">
                  <div className="alert alert-info py-2 mb-3">
                    <div className="small mb-2">
                      <i className="fas fa-info-circle me-1" />
                      Ürün detay sayfasının URL'ini yapıştırın → görseller otomatik bulunup gösterilecek.
                    </div>
                    <div className="d-flex flex-wrap gap-1">
                      <small className="text-muted me-1" style={{ lineHeight: '24px' }}>
                        Desteklenen:
                      </small>
                      {[
                        'profilo.com',
                        'simfer.com.tr',
                        'ferreturkiye.com',
                        'kaercher.com',
                        'kumtel.com',
                        'tefal.com.tr',
                        'braunshop.com.tr',
                        'lg.com',
                        'siemens.com.tr',
                        'bosch-home.com.tr',
                        'miele.com',
                        'fakir.com.tr',
                      ].map((d) => (
                        <span key={d} className="badge bg-white text-dark border" style={{ fontWeight: 400 }}>
                          {d}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="mb-3">
                    <div className="input-group input-group-sm">
                      <span className="input-group-text">
                        <i className="fas fa-link" />
                      </span>
                      <input
                        type="url"
                        className={`form-control ${crawlUrl && !isLikelySupportedUrl(crawlUrl) ? 'is-invalid' : ''}`}
                        placeholder="https://www.profilo.com/tr/tr/product/..."
                        value={crawlUrl}
                        onChange={(e) => setCrawlUrl(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            fetchCrawlPreview();
                          }
                        }}
                        disabled={crawlLoading || crawlImporting}
                        autoFocus
                      />
                      <button
                        className="btn btn-primary"
                        onClick={fetchCrawlPreview}
                        disabled={crawlLoading || crawlImporting || !crawlUrl.trim()}
                      >
                        {crawlLoading ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-1" />
                            Çekiliyor
                          </>
                        ) : (
                          <>
                            <i className="fas fa-search me-1" />
                            Görselleri Bul
                          </>
                        )}
                      </button>
                    </div>
                    {crawlUrl && !isLikelySupportedUrl(crawlUrl) && (
                      <small className="text-danger d-block mt-1">
                        <i className="fas fa-exclamation-circle me-1" />
                        Bu domain desteklenmiyor görünüyor. Yukarıdaki listeden bir site kullanın.
                      </small>
                    )}
                    {crawlUrl && isLikelySupportedUrl(crawlUrl) && (
                      <small className="text-success d-block mt-1">
                        <i className="fas fa-check-circle me-1" />
                        Geçerli — "Görselleri Bul"a tıklayın.
                      </small>
                    )}
                  </div>

                  {crawlError && (
                    <div className="alert alert-danger d-flex align-items-start gap-2 mb-3">
                      <i className="fas fa-exclamation-triangle mt-1" style={{ fontSize: 18 }} />
                      <div className="flex-grow-1">
                        <strong className="d-block mb-1">URL kabul edilmedi</strong>
                        <div className="small">{crawlError}</div>
                        {crawlError.includes('Desteklenen') && (
                          <div className="mt-2 small">
                            <strong>İpucu:</strong> Yapıştırdığınız URL'in başlangıcı doğru mu? Profilo için:{' '}
                            <code className="bg-white px-1 rounded">https://www.profilo.com/...</code>
                          </div>
                        )}
                      </div>
                      <button
                        type="button"
                        className="btn-close btn-close-sm"
                        onClick={() => setCrawlError('')}
                      ></button>
                    </div>
                  )}

                  {crawlResult && (
                    <div
                      className={`alert ${crawlResult.success > 0 ? 'alert-success' : 'alert-warning'} py-2 small mb-3`}
                    >
                      <i className="fas fa-check-circle me-1" />
                      <strong>
                        {crawlResult.success}/{crawlResult.total}
                      </strong>{' '}
                      görsel başarıyla yüklendi.
                      {crawlResult.errors?.length > 0 && (
                        <ul className="mb-0 mt-2" style={{ fontSize: 11 }}>
                          {crawlResult.errors.slice(0, 5).map((e, i) => (
                            <li key={i}>{e}</li>
                          ))}
                          {crawlResult.errors.length > 5 && (
                            <li>...ve {crawlResult.errors.length - 5} daha</li>
                          )}
                        </ul>
                      )}
                    </div>
                  )}

                  {crawlPreview && (
                    <>
                      {/* ── Preview header: title + brand + tab navigation ── */}
                      <div className="border-bottom pb-2 mb-3">
                        {crawlPreview.title && (
                          <div className="small text-muted text-truncate mb-2" style={{ maxWidth: '100%' }}>
                            <i className="fas fa-tag me-1" />
                            {crawlPreview.title}
                            {crawlPreview.brand && (
                              <span className="badge bg-info bg-opacity-10 text-info ms-2">
                                <i className="fas fa-trademark me-1" />
                                {crawlPreview.brand}
                              </span>
                            )}
                          </div>
                        )}
                        {/* Tab nav */}
                        <ul className="nav nav-tabs nav-tabs-sm mb-0" style={{ borderBottom: 'none' }}>
                          <li className="nav-item">
                            <button
                              type="button"
                              className={`nav-link ${crawlActiveTab === 'images' ? 'active' : ''}`}
                              onClick={() => setCrawlActiveTab('images')}
                            >
                              <i className="fas fa-images me-1" />
                              Görseller
                              {crawlPreview.images?.length > 0 && (
                                <span className="badge bg-primary ms-2">{crawlPreview.images.length}</span>
                              )}
                            </button>
                          </li>
                          <li className="nav-item">
                            <button
                              type="button"
                              className={`nav-link ${crawlActiveTab === 'description' ? 'active' : ''}`}
                              onClick={() => setCrawlActiveTab('description')}
                            >
                              <i className="fas fa-align-left me-1" />
                              Açıklama & Özellikler
                              {(crawlPreview.description || crawlEditableSpecs.length > 0) && (
                                <span className="badge bg-success ms-2">
                                  <i className="fas fa-check" style={{ fontSize: 9 }} />
                                </span>
                              )}
                            </button>
                          </li>
                        </ul>
                      </div>

                      {/* ── Tab: Images ── */}
                      {crawlActiveTab === 'images' &&
                        crawlPreview.images &&
                        crawlPreview.images.length > 0 && (
                          <>
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <div>
                                <strong className="me-2">{crawlPreview.images.length} görsel bulundu</strong>
                                <span className="text-muted small">— {crawlSelected.size} seçili</span>
                              </div>
                              <div className="btn-group btn-group-sm">
                                <button
                                  type="button"
                                  className="btn btn-outline-secondary"
                                  onClick={selectAllCrawl}
                                >
                                  Tümünü Seç
                                </button>
                                <button
                                  type="button"
                                  className="btn btn-outline-secondary"
                                  onClick={deselectAllCrawl}
                                >
                                  Temizle
                                </button>
                              </div>
                            </div>

                            <div className="row g-2 mb-3" style={{ maxHeight: 400, overflowY: 'auto' }}>
                              {crawlPreview.images.map((u, idx) => {
                                const selected = crawlSelected.has(u);
                                return (
                                  <div key={u} className="col-4 col-md-3">
                                    <div
                                      className={`border rounded position-relative overflow-hidden ${selected ? 'border-primary border-2' : ''}`}
                                      style={{ cursor: 'pointer', aspectRatio: '1/1', background: '#f8f9fa' }}
                                      onClick={() => toggleCrawlSelection(u)}
                                      title={u}
                                    >
                                      <CrawlThumbnail url={u} referer={crawlUrl.trim()} />
                                      <div className="position-absolute top-0 start-0 m-1">
                                        <span className="badge bg-dark bg-opacity-75 small">#{idx + 1}</span>
                                      </div>
                                      {selected && (
                                        <div className="position-absolute top-0 end-0 m-1">
                                          <span className="badge bg-primary">
                                            <i className="fas fa-check" />
                                          </span>
                                        </div>
                                      )}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>

                            <div className="form-check mb-2">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                id="crawlReplaceCheck"
                                checked={crawlReplace}
                                onChange={(e) => setCrawlReplace(e.target.checked)}
                              />
                              <label className="form-check-label small" htmlFor="crawlReplaceCheck">
                                <strong>Mevcut ürün görsellerini sil</strong>
                                <span className="text-muted ms-1">
                                  (işaretlenmezse yenileri ek olarak yüklenir)
                                </span>
                              </label>
                            </div>
                          </>
                        )}

                      {/* ── Tab: Description & Specs ── */}
                      {crawlActiveTab === 'description' && (
                        <div>
                          {!crawlPreview.description &&
                          !crawlPreview.shortDescription &&
                          crawlEditableSpecs.length === 0 ? (
                            <div className="alert alert-warning small mb-3">
                              <i className="fas fa-exclamation-triangle me-1" />
                              Bu sayfadan açıklama veya teknik özellik çıkarılamadı. Manuel olarak ekleyebilir
                              veya ürün formuna kendiniz yazabilirsiniz.
                            </div>
                          ) : (
                            <div className="alert alert-info small mb-3 py-2">
                              <i className="fas fa-magic me-1" />
                              <strong>Açıklama bulundu!</strong> Aşağıda düzenleyip "Ürün Formuna Aktar"
                              diyerek doğrudan formdaki "Açıklama" ve "Kısa Açıklama" alanlarına
                              yazdırabilirsiniz.
                            </div>
                          )}

                          {/* Short description */}
                          <div className="mb-3">
                            <label
                              htmlFor="crawl-short-desc"
                              className="form-label small fw-semibold d-flex justify-content-between"
                            >
                              <span>Kısa Açıklama</span>
                              <span className="text-muted" style={{ fontSize: 11 }}>
                                {crawlEditableShortDesc.length} / 300
                              </span>
                            </label>
                            <textarea
                              id="crawl-short-desc"
                              className="form-control form-control-sm"
                              rows={2}
                              maxLength={300}
                              value={crawlEditableShortDesc}
                              onChange={(e) => setCrawlEditableShortDesc(e.target.value)}
                              placeholder="Listeleme sayfalarında ürün adının altında görünür..."
                            />
                          </div>

                          {/* Long description */}
                          <div className="mb-3">
                            <label
                              htmlFor="crawl-long-desc"
                              className="form-label small fw-semibold d-flex justify-content-between"
                            >
                              <span>Açıklama</span>
                              <span className="text-muted" style={{ fontSize: 11 }}>
                                {crawlEditableDesc.length} karakter
                              </span>
                            </label>
                            <textarea
                              id="crawl-long-desc"
                              className="form-control"
                              rows={6}
                              value={crawlEditableDesc}
                              onChange={(e) => setCrawlEditableDesc(e.target.value)}
                              placeholder="Ürün detay sayfasında gösterilecek tam açıklama (HTML/Markdown desteklenir)..."
                            />
                            <small className="text-muted">
                              HTML etiketleri korunur. "Ürün Formuna Aktar"a basınca formdaki zengin metin
                              editörüne yapıştırılır.
                            </small>
                          </div>

                          {/* Specs editor */}
                          <div className="mb-3">
                            <div className="d-flex justify-content-between align-items-center mb-2">
                              <label className="form-label small fw-semibold mb-0">
                                Teknik Özellikler ({crawlEditableSpecs.length})
                              </label>
                              <button
                                type="button"
                                className="btn btn-sm btn-outline-primary"
                                onClick={addCrawlSpec}
                              >
                                <i className="fas fa-plus me-1" />
                                Yeni Özellik
                              </button>
                            </div>
                            {crawlEditableSpecs.length === 0 ? (
                              <div className="text-muted small text-center py-3 border rounded bg-light">
                                Özellik çıkarılamadı. "Yeni Özellik" butonu ile elle ekleyebilirsiniz.
                              </div>
                            ) : (
                              <div style={{ maxHeight: 280, overflowY: 'auto' }}>
                                {crawlEditableSpecs.map((spec, idx) => (
                                  <div key={idx} className="d-flex gap-2 mb-2">
                                    <input
                                      type="text"
                                      className="form-control form-control-sm"
                                      placeholder="Özellik adı (örn. Kapasite)"
                                      value={spec.key}
                                      onChange={(e) => updateCrawlSpec(idx, 'key', e.target.value)}
                                      style={{ flexBasis: '40%' }}
                                    />
                                    <input
                                      type="text"
                                      className="form-control form-control-sm"
                                      placeholder="Değer (örn. 9 kg)"
                                      value={spec.value}
                                      onChange={(e) => updateCrawlSpec(idx, 'value', e.target.value)}
                                      style={{ flexBasis: '55%' }}
                                    />
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-outline-danger"
                                      onClick={() => removeCrawlSpec(idx)}
                                      title="Sil"
                                    >
                                      <i className="fas fa-trash" />
                                    </button>
                                  </div>
                                ))}
                              </div>
                            )}
                            <small className="text-muted">
                              Aktarınca açıklamanın altına "Teknik Özellikler" tablosu olarak eklenir.
                            </small>
                          </div>

                          {/* Apply button */}
                          <div className="d-flex gap-2 align-items-center">
                            <button
                              type="button"
                              className="btn btn-success"
                              onClick={applyCrawlDescriptionToProduct}
                              disabled={
                                !crawlEditableDesc &&
                                !crawlEditableShortDesc &&
                                crawlEditableSpecs.length === 0
                              }
                            >
                              <i className="fas fa-arrow-right me-1" />
                              Ürün Formuna Aktar
                            </button>
                            {crawlDescAppliedToast && (
                              <span className="badge bg-success">
                                <i className="fas fa-check me-1" />
                                Aktarıldı
                              </span>
                            )}
                            <small className="text-muted ms-auto">
                              <i className="fas fa-info-circle me-1" />
                              Formdaki mevcut değerler üzerine yazılır
                            </small>
                          </div>
                        </div>
                      )}
                    </>
                  )}

                  {crawlPreview &&
                    crawlActiveTab === 'images' &&
                    (!crawlPreview.images || crawlPreview.images.length === 0) && (
                      <div className="alert alert-warning py-2 small mb-0">
                        Bu sayfada otomatik bulunabilen görsel yok. "Açıklama & Özellikler" sekmesini
                        deneyebilir veya elle ekleyebilirsiniz.
                      </div>
                    )}
                </div>
                <div className="modal-footer">
                  {crawlResult && crawlResult.success > 0 ? (
                    <>
                      <button
                        type="button"
                        className="btn btn-outline-secondary"
                        onClick={() => setCrawlOpen(false)}
                      >
                        <i className="fas fa-images me-1" />
                        Daha Fazla Görsel Ekle
                      </button>
                      <button
                        type="button"
                        className="btn btn-success"
                        onClick={() => {
                          setCrawlOpen(false);
                          if (onSuccess) onSuccess({ id: product?.id });
                        }}
                      >
                        <i className="fas fa-arrow-right me-1" />
                        Ürün Detayına Dön
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => setCrawlOpen(false)}
                        disabled={crawlLoading || crawlImporting}
                      >
                        Kapat
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={!crawlPreview || crawlSelected.size === 0 || crawlImporting || crawlLoading}
                        onClick={importCrawled}
                      >
                        {crawlImporting ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-1" />
                            Yükleniyor...
                          </>
                        ) : (
                          <>
                            <i className="fas fa-download me-1" />
                            {crawlSelected.size} Görseli İndir
                          </>
                        )}
                      </button>
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── SEO Settings ── */}
        <div className="card border-0 bg-light mt-3">
          <div
            className="card-header bg-transparent border-bottom-0 d-flex align-items-center gap-2 py-2 px-3"
            style={{ cursor: 'pointer' }}
            onClick={() => setFormData((prev) => ({ ...prev, _seoOpen: !prev._seoOpen }))}
          >
            <i className="fas fa-search text-success" />
            <span className="small fw-semibold">SEO Ayarları</span>
            <i
              className={`fas fa-chevron-${formData._seoOpen ? 'up' : 'down'} ms-auto text-muted`}
              style={{ fontSize: 11 }}
            />
          </div>
          {formData._seoOpen && (
            <div className="card-body pt-0 px-3 pb-3">
              <div className="row g-2">
                <div className="col-12">
                  <label className="form-label small fw-medium mb-1">Slug (URL)</label>
                  <div className="input-group input-group-sm">
                    <span className="input-group-text text-muted" style={{ fontSize: 11 }}>
                      /store/urun/
                    </span>
                    <input
                      className="form-control font-monospace"
                      value={formData.slug || ''}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          slug: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''),
                        }))
                      }
                      placeholder="urun-adi-slug"
                    />
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      title="Ürün adından otomatik oluştur"
                      onClick={() => {
                        const s = (formData.name || '')
                          .toLowerCase()
                          .replace(/ş/g, 's')
                          .replace(/ç/g, 'c')
                          .replace(/ğ/g, 'g')
                          .replace(/ü/g, 'u')
                          .replace(/ö/g, 'o')
                          .replace(/ı/g, 'i')
                          .replace(/İ/g, 'i')
                          .replace(/[^a-z0-9\s-]/g, '')
                          .replace(/\s+/g, '-')
                          .replace(/-+/g, '-')
                          .replace(/^-|-$/g, '');
                        setFormData((prev) => ({ ...prev, slug: s }));
                      }}
                    >
                      <i className="fas fa-magic" />
                    </button>
                  </div>
                </div>
                <div className="col-12">
                  <label className="form-label small fw-medium mb-1">
                    Meta Başlık{' '}
                    <span className="text-muted fw-normal">({(formData.metaTitle || '').length}/200)</span>
                  </label>
                  <input
                    className="form-control form-control-sm"
                    value={formData.metaTitle || ''}
                    onChange={(e) => setFormData((prev) => ({ ...prev, metaTitle: e.target.value }))}
                    maxLength={200}
                    placeholder="Arama sonuçlarında görünecek başlık"
                  />
                </div>
                <div className="col-12">
                  <label className="form-label small fw-medium mb-1">
                    Meta Açıklama{' '}
                    <span className="text-muted fw-normal">
                      ({(formData.metaDescription || '').length}/500)
                    </span>
                  </label>
                  <textarea
                    className="form-control form-control-sm"
                    rows={2}
                    value={formData.metaDescription || ''}
                    onChange={(e) => setFormData((prev) => ({ ...prev, metaDescription: e.target.value }))}
                    maxLength={500}
                    placeholder="Arama sonuçlarında görünecek açıklama"
                  />
                </div>
                {formData.metaTitle && (
                  <div className="col-12">
                    <div className="border rounded p-2" style={{ fontSize: 12, background: '#fff' }}>
                      <div className="text-primary" style={{ fontSize: 14 }}>
                        {formData.metaTitle || formData.name}
                      </div>
                      <div className="text-success" style={{ fontSize: 11 }}>
                        siteniz.com/store/urun/{formData.slug || '...'}
                      </div>
                      <div className="text-muted">
                        {(formData.metaDescription || formData.shortDescription || '').substring(0, 160)}
                      </div>
                    </div>
                    <small className="text-muted">Google arama sonucu önizlemesi</small>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="d-flex justify-content-end gap-2 mt-3">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={loading}>
            İptal
          </button>
          <button type="button" className="btn btn-primary" disabled={loading} onClick={handleSubmit}>
            {loading ? (
              <>
                <span
                  className="spinner-border spinner-border-sm me-2"
                  role="status"
                  aria-hidden="true"
                ></span>
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
            axios
              .delete(`/api/products/images/${imgId}`)
              .then(() => {
                setProductImages((prev) => prev.filter((i) => i.id !== imgId));
              })
              .catch(() => {});
          }}
          onCancel={() => setDeleteImageId(null)}
        />

        {showCreateModal && (
          <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">
                    {createModalType === 'brand' && (
                      <>
                        <i className="fas fa-copyright me-2"></i>Yeni Marka
                      </>
                    )}
                    {createModalType === 'color' && (
                      <>
                        <i className="fas fa-palette me-2"></i>Yeni Renk
                      </>
                    )}
                    {createModalType === 'category' && (
                      <>
                        <i className="fas fa-folder me-2"></i>Yeni Kategori
                      </>
                    )}
                    {createModalType === 'subcategory' && (
                      <>
                        <i className="fas fa-folder-open me-2"></i>Yeni Alt Kategori
                      </>
                    )}
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
                        createModalType === 'brand'
                          ? 'Marka adı'
                          : createModalType === 'color'
                            ? 'Renk adı'
                            : createModalType === 'category'
                              ? 'Kategori adı'
                              : 'Alt kategori adı'
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
                        <span
                          className="spinner-border spinner-border-sm me-2"
                          role="status"
                          aria-hidden="true"
                        ></span>
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
