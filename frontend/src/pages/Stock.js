import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import StockForm from '../components/StockForm';
import StockAdjustmentModal from '../components/StockAdjustmentModal';
import StockTransferModal from '../components/StockTransferModal';
import SearchableSelect from '../components/SearchableSelect';
import FilterChips from '../components/FilterChips';
import ConfirmModal from '../components/ConfirmModal';
import NotesModal from '../components/NotesModal';
import AuditTimelineModal from '../components/AuditTimelineModal';

// Helper function to format dates in Turkey timezone
const formatDateInTurkeyTimezone = (isoDateString, options = {}) => {
  if (!isoDateString) return '-';
  try {
    const date = new Date(isoDateString);
    // Force Turkey timezone display
    return date.toLocaleString('tr-TR', {
      timeZone: 'Europe/Istanbul',
      ...options
    });
  } catch (error) {
    console.error('Date formatting error:', error);
    return '-';
  }
};

// Stable filters bar component to prevent input remount/focus loss
const StockFiltersBar = ({
  searchTerm,
  setSearchTerm,
  selectedWarehouseId,
  setSelectedWarehouseId,
  selectedWarehouseOpt,
  setSelectedWarehouseOpt,
  brandId,
  setBrandId,
  brandOpt,
  setBrandOpt,
  colorId,
  setColorId,
  colorOpt,
  setColorOpt,
  categories,
  subcategories,
  setSubcategories,
  selectedCategory,
  setSelectedCategory,
  selectedSubcategory,
  setSelectedSubcategory,
  showReserved,
  setShowReserved,
  showConsigned,
  setShowConsigned,
  getWarehouseById
}) => {
  const searchInputRef = useRef(null);

  useEffect(() => {
    if (searchInputRef.current && document.activeElement !== searchInputRef.current) {
      searchInputRef.current.focus();
      const len = searchInputRef.current.value.length;
      try { searchInputRef.current.setSelectionRange(len, len); } catch {}
    }
  }, [searchTerm]);

  return (
    <>
      <div className="row mb-2 align-items-end">
        <div className="col-md-3">
          <div className="input-group">
            <span className="input-group-text"><i className="fas fa-search"></i></span>
            <input
              ref={searchInputRef}
              type="text"
              className="form-control"
            placeholder="Ürün adı, Stok Kodu veya depo ara..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>
        <div className="col-md-3">
          <SearchableSelect
            label="Depo"
            value={selectedWarehouseId}
            onChange={(id, opt) => { setSelectedWarehouseId(id); setSelectedWarehouseOpt(opt || null); }}
            searchEndpoint="/api/warehouses"
            placeholder="Depo ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
            renderOption={(w) => w.name}
          />
        </div>
        <div className="col-md-3">
          <SearchableSelect
            label="Marka"
            value={brandId}
            onChange={(id, opt) => { setBrandId(id); setBrandOpt(opt || null); }}
            searchEndpoint="/api/brands/search"
            placeholder="Marka ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
          />
        </div>
        <div className="col-md-3">
          <SearchableSelect
            label="Renk"
            value={colorId}
            onChange={(id, opt) => { setColorId(id); setColorOpt(opt || null); }}
            searchEndpoint="/api/colors/search"
            placeholder="Renk ara..."
            allowClear={true}
            clearText="Temizle"
            wrapperClassName="mb-0"
          />
        </div>
      </div>

      {/* Category filters */}
      <div className="row mb-2 align-items-end">
        <div className="col-md-6">
          <select
            className="form-select"
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
          >
            <option value="">Tüm Ana Kategoriler</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>
        <div className="col-md-6">
          <select
            className="form-select"
            value={selectedSubcategory}
            onChange={(e) => setSelectedSubcategory(e.target.value)}
            disabled={!selectedCategory}
          >
            <option value="">Tüm Alt Kategoriler</option>
            {subcategories.map((subcategory) => (
              <option key={subcategory.id} value={subcategory.id}>
                {subcategory.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Additional filters */}
      <div className="row mb-2">
        <div className="col-md-12">
          <div className="form-check form-check-inline">
            <input
              className="form-check-input"
              type="checkbox"
              id="showReserved"
              checked={showReserved}
              onChange={(e) => setShowReserved(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="showReserved">
              Sadece Rezerve Olanlar
            </label>
          </div>
          <div className="form-check form-check-inline">
            <input
              className="form-check-input"
              type="checkbox"
              id="showConsigned"
              checked={showConsigned}
              onChange={(e) => setShowConsigned(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="showConsigned">
              Sadece Emanet Olanlar
            </label>
          </div>
        </div>
      </div>

      <FilterChips
        className="mb-3"
        chips={[
          searchTerm ? { icon: 'fas fa-search', label: `Arama: "${searchTerm}"`, onClear: () => setSearchTerm('') } : null,
          selectedWarehouseId ? { icon: 'fas fa-warehouse', label: `Depo: ${selectedWarehouseOpt?.name || getWarehouseById(selectedWarehouseId)?.name || selectedWarehouseId}`, onClear: () => { setSelectedWarehouseId(null); setSelectedWarehouseOpt(null); } } : null,
          selectedCategory ? { icon: 'fas fa-tag', label: `Ana Kategori: ${categories.find(c => c.id.toString() === selectedCategory)?.name || selectedCategory}`, onClear: () => { setSelectedCategory(''); setSelectedSubcategory(''); setSubcategories([]); } } : null,
          selectedSubcategory ? { icon: 'fas fa-tags', label: `Alt Kategori: ${subcategories.find(c => c.id.toString() === selectedSubcategory)?.name || selectedSubcategory}`, onClear: () => setSelectedSubcategory('') } : null,
          brandId ? { icon: 'fas fa-copyright', label: `Marka: ${brandOpt?.name || brandId}`, onClear: () => { setBrandId(null); setBrandOpt(null); } } : null,
          colorId ? { icon: 'fas fa-palette', label: `Renk: ${colorOpt?.name || colorId}`, onClear: () => { setColorId(null); setColorOpt(null); } } : null,
          showReserved ? { icon: 'fas fa-lock', label: 'Rezerve Olanlar', onClear: () => setShowReserved(false) } : null,
          showConsigned ? { icon: 'fas fa-handshake', label: 'Emanet Olanlar', onClear: () => setShowConsigned(false) } : null,
        ].filter(Boolean)}
        onClearAll={() => {
          setSearchTerm('');
          setSelectedWarehouseId(null);
          setSelectedWarehouseOpt(null);
          setSelectedCategory('');
          setSelectedSubcategory('');
          setSubcategories([]);
          setBrandId(null);
          setColorId(null);
          setBrandOpt(null);
          setColorOpt(null);
          setShowReserved(false);
          setShowConsigned(false);
        }}
      />
    </>
  );
};

const Stock = () => {
  const location = useLocation();
  const role = (typeof window !== 'undefined' && localStorage.getItem('auth_role')) || 'ADMIN';
  const [stocks, setStocks] = useState([]);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [showAdjustmentModal, setShowAdjustmentModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [showTransferHistory, setShowTransferHistory] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [transfers, setTransfers] = useState([]);
  const [filter, setFilter] = useState('all'); // all, low-stock, out-of-stock
  const [brandId, setBrandId] = useState(null);
  const [colorId, setColorId] = useState(null);
  const [brandOpt, setBrandOpt] = useState(null);
  const [colorOpt, setColorOpt] = useState(null);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(null);
  const [selectedWarehouseOpt, setSelectedWarehouseOpt] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [showReserved, setShowReserved] = useState(false);
  const [showConsigned, setShowConsigned] = useState(false);
  const [transferStatusFilter, setTransferStatusFilter] = useState('ALL');
  // Transfer filters
  const [transferProductName, setTransferProductName] = useState('');
  const [transferSku, setTransferSku] = useState('');
  const [transferDriver, setTransferDriver] = useState('');
  const [transferSourceWarehouseId, setTransferSourceWarehouseId] = useState(null);
  const [transferDestinationWarehouseId, setTransferDestinationWarehouseId] = useState(null);
  // Category filters
  const [categories, setCategories] = useState([]);
  const [subcategories, setSubcategories] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedSubcategory, setSelectedSubcategory] = useState('');
  // Stock list sorting
  const [stockSortBy, setStockSortBy] = useState('default'); // 'default' | 'lastUpdated'
  const [stockSortDir, setStockSortDir] = useState('desc'); // 'asc' | 'desc'
  
  // Modal states
  const [confirmModal, setConfirmModal] = useState({ show: false, title: '', message: '', onConfirm: null });
  const [errorModal, setErrorModal] = useState({ show: false, title: '', message: '' });
  const [notesModal, setNotesModal] = useState({ show: false, notes: '', transferId: null });
  const [cancellationModal, setCancellationModal] = useState({ show: false, transferId: null, reason: '' });
  const [auditModal, setAuditModal] = useState({ show: false, entityType: null, entityId: null });
  const [pendingStockId, setPendingStockId] = useState(null);
  const [showExcelModal, setShowExcelModal] = useState(false);
  const [excelUploading, setExcelUploading] = useState(false);
  const [excelFile, setExcelFile] = useState(null);
  const [excelWarehouseId, setExcelWarehouseId] = useState(null);
  const [excelResult, setExcelResult] = useState(null);

  const fetchAllData = useCallback(async () => {
    try {
      setLoading(true);
      const [stocksRes, productsRes, warehousesRes] = await Promise.all([
        axios.get('/api/stocks', { params: { brandId, colorId, warehouseId: selectedWarehouseId || undefined } }),
        axios.get('/api/products'),
        axios.get('/api/warehouses')
      ]);

      setStocks(stocksRes.data);
      setProducts(productsRes.data);
      setWarehouses(warehousesRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
      setError('Veriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [brandId, colorId, selectedWarehouseId]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  // Fetch main categories on mount
  useEffect(() => {
    const fetchMainCategories = async () => {
      try {
        const response = await axios.get('/api/categories/top-level');
        setCategories(response.data);
      } catch (error) {
        // noop
      }
    };
    fetchMainCategories();
  }, []);

  // Fetch subcategories when a main category is selected
  useEffect(() => {
    const fetchSubs = async () => {
      if (!selectedCategory) { setSubcategories([]); setSelectedSubcategory(''); return; }
      try {
        const response = await axios.get(`/api/categories/${selectedCategory}/subcategories`);
        setSubcategories(response.data);
        setSelectedSubcategory('');
      } catch (error) {
        setSubcategories([]);
        setSelectedSubcategory('');
      }
    };
    fetchSubs();
  }, [selectedCategory]);

  // Initialize filters from query params
  // React to query string changes (navigation within app)
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const f = params.get('filter');
    const b = params.get('brandId');
    const c = params.get('colorId');
    const w = params.get('warehouseId');
    const stockIdParam = params.get('stockId');
    const transferIdParam = params.get('transferId');
    const auditStockIdParam = params.get('auditStockId');
    const auditTransferIdParam = params.get('auditTransferId');
    if (f === 'low-stock' || f === 'out-of-stock' || f === 'all') setFilter(f);
    if (b) setBrandId(Number(b));
    if (c) setColorId(Number(c));
    if (w) setSelectedWarehouseId(Number(w));
    if (stockIdParam) setPendingStockId(Number(stockIdParam));
    if (transferIdParam) {
      // Open transfer history and highlight by filtering to ALL
      setShowTransferHistory(true);
      // No direct single transfer view page exists; focus by opening history
      setTimeout(() => { fetchTransfers(); }, 0);
    }
    if (auditStockIdParam) {
      const idNum = Number(auditStockIdParam);
      if (!Number.isNaN(idNum)) {
        setAuditModal({ show: true, entityType: 'Stock', entityId: idNum });
      }
    }
    if (auditTransferIdParam) {
      const idNum = Number(auditTransferIdParam);
      if (!Number.isNaN(idNum)) {
        setAuditModal({ show: true, entityType: 'StockTransfer', entityId: idNum });
      }
    }
  }, [location.search]);

  // Listen to global event to open audit directly from Navbar without navigation
  useEffect(() => {
    const handler = (e) => {
      const detail = e?.detail || {};
      if (detail.entityType && detail.entityId) {
        setAuditModal({ show: true, entityType: detail.entityType, entityId: Number(detail.entityId) });
      }
    };
    window.addEventListener('open-audit', handler);
    return () => window.removeEventListener('open-audit', handler);
  }, []);

  // Open stock adjustment modal when stocks loaded and pendingStockId exists
  useEffect(() => {
    if (!pendingStockId || !Array.isArray(stocks) || stocks.length === 0) return;
    const s = stocks.find(x => x.id === pendingStockId);
    if (s) {
      setSelectedStock(s);
      setShowAdjustmentModal(true);
      setPendingStockId(null);
    }
  }, [stocks, pendingStockId]);

  const getEffectiveMin = useCallback((stock) => {
    const val = Number(stock?.minStockLevel);
    if (!Number.isFinite(val) || val <= 0) return 10;
    return val;
  }, []);

  const filteredStocks = useMemo(() => {
    let filtered = stocks;

    // status filter
    switch (filter) {
      case 'low-stock':
        filtered = filtered.filter(stock => stock.quantity <= getEffectiveMin(stock) && stock.quantity > 0);
        break;
      case 'out-of-stock':
        filtered = filtered.filter(stock => stock.quantity === 0);
        break;
      default:
        break;
    }

    // text search
    const q = (searchTerm || '').toLowerCase();
    if (q) {
      filtered = filtered.filter(s =>
        (s.product?.name || '').toLowerCase().includes(q) ||
        (s.product?.sku || '').toLowerCase().includes(q) ||
        (s.warehouse?.name || '').toLowerCase().includes(q)
      );
    }

    // category filters
    if (selectedCategory) {
      filtered = filtered.filter(s => {
        const prod = products.find(p => p.id === s.product?.id);
        if (!prod || !prod.category) return false;
        const categoryIdStr = prod.category?.id != null ? prod.category.id.toString() : '';
        const parentIdStr = prod.category?.parent?.id != null ? prod.category.parent.id.toString() : '';
        return categoryIdStr === selectedCategory || parentIdStr === selectedCategory;
      });
    }
    if (selectedSubcategory) {
      filtered = filtered.filter(s => {
        const prod = products.find(p => p.id === s.product?.id);
        if (!prod || !prod.category) return false;
        const categoryIdStr = prod.category?.id != null ? prod.category.id.toString() : '';
        return categoryIdStr === selectedSubcategory;
      });
    }

    // reserved/consigned filters
    if (showReserved) {
      filtered = filtered.filter(s => (s.reservedQuantity || 0) > 0);
    }
    if (showConsigned) {
      filtered = filtered.filter(s => (s.consignedQuantity || 0) > 0);
    }

    // Sorting
    const sorted = [...filtered];
    if (stockSortBy === 'lastUpdated') {
      const dir = stockSortDir === 'asc' ? 1 : -1;
      sorted.sort((a, b) => {
        const da = a.lastUpdated ? new Date(a.lastUpdated).getTime() : 0;
        const db = b.lastUpdated ? new Date(b.lastUpdated).getTime() : 0;
        if (da === db) return 0;
        return da < db ? -1 * dir : 1 * dir;
      });
    } else {
      // Default: by warehouse then product
      sorted.sort((a, b) => {
        const warehouseCompare = (a.warehouse?.name || '').localeCompare(b.warehouse?.name || '');
        if (warehouseCompare !== 0) return warehouseCompare;
        return (a.product?.name || '').localeCompare(b.product?.name || '');
      });
    }
    return sorted;
  }, [stocks, filter, searchTerm, showReserved, showConsigned, selectedCategory, selectedSubcategory, products, stockSortBy, stockSortDir, getEffectiveMin]);


  const handleCreateStock = () => {
    setSelectedStock(null);
    setShowForm(true);
  };

  const handleStockAdjustment = (stock) => {
    setSelectedStock(stock);
    setShowAdjustmentModal(true);
  };

  const handleDeleteStock = async (id) => {
    setConfirmModal({
      show: true,
      title: 'Stok Kaydını Sil',
      message: 'Bu stok kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete(`/api/stocks/${id}`);
          fetchAllData();
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Stok Silme Hatası',
            message: `Stok silinirken hata oluştu: ${msg}`
          });
        }
      }
    });
  };

  const handleFormSuccess = () => {
    setShowForm(false);
    fetchAllData();
  };

  const handleAdjustmentSuccess = () => {
    setShowAdjustmentModal(false);
    setSelectedStock(null);
    fetchAllData();
  };

  const handleStockTransfer = (stock) => {
    setSelectedStock(stock);
    setShowTransferModal(true);
  };

  const handleTransferSuccess = () => {
    setShowTransferModal(false);
    setSelectedStock(null);
    fetchAllData();
    if (showTransferHistory) {
      fetchTransfers();
    }
  };

  const handleShowTransferHistory = async () => {
    setShowTransferHistory(!showTransferHistory);
    if (!showTransferHistory) {
      await fetchTransfers();
    }
  };

  const fetchTransfers = async () => {
    try {
      const response = await axios.get('/api/stock-transfers');
      setTransfers(response.data);
    } catch (error) {
      console.error('Error fetching transfers:', error);
    }
  };

  const handleTransferStatusChange = async (transferId, action, cancellationReason = null) => {
    try {
      const payload = action === 'cancel' && cancellationReason ? { cancellationReason } : {};
      await axios.post(`/api/stock-transfers/${transferId}/${action}`, payload);
      fetchTransfers();
      fetchAllData();
    } catch (error) {
      const errorData = error?.response?.data;
      let errorMessage = 'Beklenmeyen bir durum oluştu';
      
      if (errorData) {
        if (errorData.message) {
          errorMessage = errorData.message;
        } else if (errorData.error) {
          errorMessage = errorData.error;
        } else if (typeof errorData === 'string') {
          errorMessage = errorData;
        }
      }
      
      const actionNames = {
        'cancel': 'iptal',
        'start': 'başlatma',
        'complete': 'tamamlama'
      };
      const actionName = actionNames[action] || action;
      
      setErrorModal({
        show: true,
        title: 'Transfer İşlemi Hatası',
        message: `Transfer ${actionName} işlemi sırasında hata oluştu: ${errorMessage}`
      });
    }
  };

  const handleDeleteTransfer = async (transferId) => {
    setConfirmModal({
      show: true,
      title: 'Transfer Kaydını Sil',
      message: 'Bu transfer kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.',
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete(`/api/stock-transfers/${transferId}`);
          fetchTransfers();
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Transfer Silme Hatası',
            message: `Transfer silinirken hata oluştu: ${msg}`
          });
        }
      }
    });
  };

  const getProductById = (id) => {
    return products.find(p => p.id === id);
  };

  const getWarehouseById = (id) => {
    return warehouses.find(w => w.id === id);
  };


  const getStockStatus = (stock) => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0);
    if (available <= 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (available <= getEffectiveMin(stock)) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  // filteredStocks now computed via useMemo above
  const filteredTransfers = useMemo(() => {
    let list = Array.isArray(transfers) ? transfers : [];
    // Status filter
    if (transferStatusFilter !== 'ALL') {
      list = list.filter(t => t.status === transferStatusFilter);
    }
    // Product name filter
    const nameQ = (transferProductName || '').toLowerCase();
    if (nameQ) {
      list = list.filter(t => (t.product?.name || '').toLowerCase().includes(nameQ));
    }
    // SKU filter
    const skuQ = (transferSku || '').toLowerCase();
    if (skuQ) {
      list = list.filter(t => (t.product?.sku || '').toLowerCase().includes(skuQ));
    }
    // Driver filter
    const driverQ = (transferDriver || '').toLowerCase();
    if (driverQ) {
      list = list.filter(t => (t.driverName || '').toLowerCase().includes(driverQ));
    }
    // Source/Destination warehouse filter
    if (transferSourceWarehouseId != null) {
      list = list.filter(t => Number(t.sourceWarehouse?.id) === Number(transferSourceWarehouseId));
    }
    if (transferDestinationWarehouseId != null) {
      list = list.filter(t => Number(t.destinationWarehouse?.id) === Number(transferDestinationWarehouseId));
    }
    return list;
  }, [transfers, transferStatusFilter, transferProductName, transferSku, transferDriver, transferSourceWarehouseId, transferDestinationWarehouseId]);

  if (loading) {
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
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2>Stok Yönetimi</h2>
        <div className="btn-group">
          <button className="btn btn-outline-success" onClick={async () => {
            try {
              const res = await axios.get('/api/stock-imports/template', { responseType: 'blob' });
              const url = window.URL.createObjectURL(new Blob([res.data]));
              const link = document.createElement('a');
              link.href = url;
              link.setAttribute('download', 'stok_sablon.xlsx');
              document.body.appendChild(link);
              link.click();
              link.remove();
              window.URL.revokeObjectURL(url);
            } catch (e) {
              setError('Excel şablonu indirilirken hata oluştu');
            }
          }}>
            <i className="fas fa-download me-2"></i>
            Excel Şablonunu İndir
          </button>
          <button className="btn btn-outline-primary" onClick={() => { setExcelResult(null); setExcelFile(null); setExcelWarehouseId(null); setShowExcelModal(true); }}>
            <i className="fas fa-file-import me-2"></i>
            Excel'den Yükle
          </button>
          <button className="btn btn-success" onClick={handleShowTransferHistory}>
            <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
            {showTransferHistory ? 'Stok Listesi' : 'Transfer Geçmişi'}
          </button>
          <button className="btn btn-primary" onClick={handleCreateStock}>
            <i className="fas fa-plus me-2"></i>
            Yeni Stok Kaydı
          </button>
        </div>
      </div>

      {/* Filters Bar - Only show when not in transfer history mode */}
      {!showTransferHistory && (
        <StockFiltersBar
          searchTerm={searchTerm}
          setSearchTerm={setSearchTerm}
          selectedWarehouseId={selectedWarehouseId}
          setSelectedWarehouseId={setSelectedWarehouseId}
          selectedWarehouseOpt={selectedWarehouseOpt}
          setSelectedWarehouseOpt={setSelectedWarehouseOpt}
          brandId={brandId}
          setBrandId={setBrandId}
          brandOpt={brandOpt}
          setBrandOpt={setBrandOpt}
          colorId={colorId}
          setColorId={setColorId}
          colorOpt={colorOpt}
          setColorOpt={setColorOpt}
          categories={categories}
          subcategories={subcategories}
          setSubcategories={setSubcategories}
          selectedCategory={selectedCategory}
          setSelectedCategory={setSelectedCategory}
          selectedSubcategory={selectedSubcategory}
          setSelectedSubcategory={setSelectedSubcategory}
          showReserved={showReserved}
          setShowReserved={setShowReserved}
          showConsigned={showConsigned}
          setShowConsigned={setShowConsigned}
          getWarehouseById={getWarehouseById}
        />
      )}

      {/* Filter Tabs */}
      {!showTransferHistory && (
      <div className="mb-4">
        <div className="btn-group" role="group">
          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="all"
            value="all"
            checked={filter === 'all'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-primary" htmlFor="all">
            Tüm Stok ({stocks.length})
          </label>

          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="low-stock"
            value="low-stock"
            checked={filter === 'low-stock'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-warning" htmlFor="low-stock">
            Düşük Stok ({stocks.filter(s => s.quantity <= getEffectiveMin(s) && s.quantity > 0).length})
          </label>

          <input
            type="radio"
            className="btn-check"
            name="filter"
            id="out-of-stock"
            value="out-of-stock"
            checked={filter === 'out-of-stock'}
            onChange={(e) => setFilter(e.target.value)}
          />
          <label className="btn btn-outline-danger" htmlFor="out-of-stock">
            Stok Dışı ({stocks.filter(s => s.quantity === 0).length})
          </label>
        </div>
      </div>
      )}

      {showExcelModal && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Excel'den Stok Yükle</h5>
                <button type="button" className="btn-close" onClick={() => setShowExcelModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">Depo Seç</label>
                  <select className="form-select" value={excelWarehouseId || ''} onChange={(e) => setExcelWarehouseId(e.target.value ? parseInt(e.target.value) : null)}>
                    <option value="">Depo seçiniz...</option>
                    {warehouses.map(w => (
                      <option key={w.id} value={w.id}>{w.name}</option>
                    ))}
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Excel Dosyası (.xlsx)</label>
                  <input type="file" accept=".xlsx" className="form-control" onChange={(e) => setExcelFile(e.target.files && e.target.files[0] ? e.target.files[0] : null)} />
                </div>
                {excelResult && (
                  <div className={`alert alert-${(excelResult.status === 'SUCCESS' || excelResult.status === 'BAŞARILI') ? 'success' : ((excelResult.status === 'FAILED' || excelResult.status === 'BAŞARISIZ') ? 'danger' : 'warning')}`}>
                    <div className="fw-bold mb-1">{(excelResult.status === 'SUCCESS' || excelResult.status === 'BAŞARILI') ? 'Aktarım başarılı' : ((excelResult.status === 'FAILED' || excelResult.status === 'BAŞARISIZ') ? 'Aktarım başarısız' : 'Kısmen başarılı')}</div>
                    <div>Satır: {excelResult.totalRows ?? '-'}</div>
                    <div>Ürün (Yeni): {(excelResult.createdProducts ?? 0)}</div>
                    <div>Stok (Yeni/Güncellenen): {(excelResult.createdStocks ?? 0)} / {(excelResult.updatedStocks ?? 0)}</div>
                    {excelResult.errorMessage && (<div className="mt-2 text-danger small">Hata: {excelResult.errorMessage}</div>)}
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowExcelModal(false)} disabled={excelUploading}>Kapat</button>
                <button type="button" className="btn btn-primary" disabled={!excelWarehouseId || !excelFile || excelUploading} onClick={async () => {
                  try {
                    setExcelUploading(true);
                    setExcelResult(null);
                    const form = new FormData();
                    form.append('warehouseId', String(excelWarehouseId));
                    form.append('file', excelFile);
                    const res = await axios.post('/api/stock-imports/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } });
                    setExcelResult(res.data);
                    // Başarılı yükleme sonrası tüm verileri yenile
                    if (res.data && (res.data.status === 'BAŞARILI' || res.data.status === 'SUCCESS' || res.data.status === 'KISMEN' || res.data.status === 'PARTIAL')) {
                      await fetchAllData();
                    }
                  } catch (e) {
                    const data = e?.response?.data;
                    setExcelResult({ status: 'FAILED', errorMessage: typeof data === 'string' ? data : (data?.message || 'Yükleme hatası') });
                  } finally {
                    setExcelUploading(false);
                  }
                }}>
                  {excelUploading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      Yükleniyor...
                    </>
                  ) : (
                    <>
                      <i className="fas fa-upload me-2"></i>
                      Yükle
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      {/* Stock Table */}
      {!showTransferHistory && (
      <div className="card">
        <div className="card-body">
          <div className="table-responsive">
            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th>Depo</th>
                  <th>Ürün</th>
                  <th>Stok Kodu</th>
                  <th>Miktar</th>
                  <th>Kullanılabilir</th>
                  <th>Rezerve</th>
                  <th>Emanet</th>
                  <th>Min. Stok</th>
                  <th>Durum</th>
                  <th>
                    <button
                      type="button"
                      className="btn btn-link p-0 text-decoration-none"
                      onClick={() => {
                        if (stockSortBy === 'lastUpdated') {
                          setStockSortDir(stockSortDir === 'asc' ? 'desc' : 'asc');
                        } else {
                          setStockSortBy('lastUpdated');
                          setStockSortDir('desc');
                        }
                      }}
                      title={`Son Güncelleme göre sırala (${stockSortBy === 'lastUpdated' ? (stockSortDir === 'asc' ? 'Artan' : 'Azalan') : 'Kapalı'})`}
                    >
                      Son Güncelleme
                      <i
                        className={`fas ms-1 ${stockSortBy === 'lastUpdated' ? (stockSortDir === 'asc' ? 'fa-sort-up' : 'fa-sort-down') : 'fa-sort'}`}
                        aria-hidden="true"
                      ></i>
                    </button>
                  </th>
                  <th>İşlemler</th>
                </tr>
              </thead>
              <tbody>
                {filteredStocks.map((stock) => {
                  const product = getProductById(stock.product.id);
                  const warehouse = getWarehouseById(stock.warehouse.id);
                  const stockStatus = getStockStatus(stock);
                  const categoryPath = product?.category ? `${product.category.parentName ? product.category.parentName + ' > ' : ''}${product.category.name}` : null;

                  return (
                    <tr key={stock.id}>
                      <td>{warehouse?.name}</td>
                      <td>
                        <div className="fw-semibold">{product?.name}</div>
                        {categoryPath && (
                          <small className="text-muted d-block">
                            <i className="fas fa-tag me-1"></i>
                            {categoryPath}
                          </small>
                        )}
                      </td>
                      <td>{product?.sku}</td>
                      <td>
                        <span className="fw-bold">{stock.quantity}</span>
                      </td>
                      <td>
                        <span className={stock.availableQuantity < getEffectiveMin(stock) ? 'text-danger' : 'text-success'}>
                          {stock.availableQuantity}
                        </span>
                      </td>
                      <td>
                        <span className={stock.reservedQuantity > 0 ? 'text-warning fw-bold' : 'text-muted'}>
                          <i className="fas fa-lock me-1" style={{fontSize: '0.75rem'}}></i>
                          {stock.reservedQuantity || 0}
                        </span>
                      </td>
                      <td>{stock.consignedQuantity || 0}</td>
                      <td>{stock.minStockLevel}</td>
                      <td>
                        <span className={`badge bg-${stockStatus.class}`}>
                          {stockStatus.label}
                        </span>
                      </td>
                      <td>
                        <div className="text-nowrap text-center">
                          {formatDateInTurkeyTimezone(stock.lastUpdated, { year: 'numeric', month: '2-digit', day: '2-digit' })}
                        </div>
                        <small className="text-muted text-nowrap d-block text-center">
                          {formatDateInTurkeyTimezone(stock.lastUpdated, { hour: '2-digit', minute: '2-digit' })}
                        </small>
                      </td>
                      <td>
                        <div className="btn-group" role="group">
                          <button
                            className="btn btn-sm btn-outline-success"
                            onClick={() => handleStockTransfer(stock)}
                            title="Transfer Yap"
                          >
                            <i className="fas fa-exchange-alt"></i>
                          </button>
                          <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => handleStockAdjustment(stock)}
                            title="Stok Ayarla"
                          >
                            <i className="fas fa-edit"></i>
                          </button>
                          <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => handleDeleteStock(stock.id)}
                            title="Stok Kaydını Sil"
                          >
                            <i className="fas fa-trash"></i>
                          </button>
                          {role === 'ADMIN' && (
                            <button
                              className="btn btn-sm btn-outline-secondary"
                              onClick={() => setAuditModal({ show: true, entityType: 'Stock', entityId: stock.id })}
                              title="Hareket Geçmişi"
                            >
                              <i className="fas fa-history"></i>
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {filteredStocks.length === 0 && (
            <div className="text-center py-4">
              <i className="fas fa-cubes fa-3x text-muted mb-3"></i>
              <h5 className="text-muted">
                {filter === 'all'
                  ? 'Henüz stok kaydı bulunmuyor'
                  : `Bu kategoride stok kaydı bulunmuyor`
                }
              </h5>
              <p className="text-muted">
                {filter === 'all'
                  ? 'İlk stok kaydını oluşturmak için "Yeni Stok Kaydı" butonuna tıklayın.'
                  : 'Farklı filtre seçeneği deneyin.'
                }
              </p>
            </div>
          )}
        </div>
      </div>
      )}

      {/* Stock Form Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">Yeni Stok Kaydı</h5>
                <button
                  type="button"
                  className="btn-close"
                  onClick={() => setShowForm(false)}
                ></button>
              </div>
              <div className="modal-body">
                <StockForm
                  products={products}
                  warehouses={warehouses}
                  onSuccess={handleFormSuccess}
                  onCancel={() => setShowForm(false)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stock Adjustment Modal */}
      {showAdjustmentModal && selectedStock && (
        <StockAdjustmentModal
          stock={selectedStock}
          onSuccess={handleAdjustmentSuccess}
          onClose={() => setShowAdjustmentModal(false)}
        />
      )}

      {/* Stock Transfer Modal */}
      {showTransferModal && (
        <StockTransferModal
          stock={selectedStock}
          onSuccess={handleTransferSuccess}
          onClose={() => {
            setShowTransferModal(false);
            setSelectedStock(null);
          }}
        />
      )}

      {/* Transfer History Section */}
      {showTransferHistory && (
        <div className="mt-4">
          {/* Statistics Cards */}
          <div className="row g-3 mb-4">
            <div className="col-md-3">
              <div className="card border-warning shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-clock fa-2x text-warning mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'PENDING').length}</h3>
                  <p className="text-muted mb-0 small">Beklemede</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-info shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-truck fa-2x text-info mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'IN_TRANSIT').length}</h3>
                  <p className="text-muted mb-0 small">Yolda</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-success shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'COMPLETED').length}</h3>
                  <p className="text-muted mb-0 small">Tamamlandı</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-danger shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                  <h3 className="mb-0">{transfers.filter(t => t.status === 'CANCELLED').length}</h3>
                  <p className="text-muted mb-0 small">İptal Edildi</p>
                </div>
              </div>
            </div>
          </div>

          {/* Filter Buttons */}
          <div className="card mb-3">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div className="btn-group" role="group">
                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-all"
                    value="ALL"
                    checked={transferStatusFilter === 'ALL'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-secondary" htmlFor="status-all">
                    <i className="fas fa-list me-1"></i>
                    Tümü ({transfers.length})
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-pending"
                    value="PENDING"
                    checked={transferStatusFilter === 'PENDING'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-warning" htmlFor="status-pending">
                    <i className="fas fa-clock me-1"></i>
                    Beklemede
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-transit"
                    value="IN_TRANSIT"
                    checked={transferStatusFilter === 'IN_TRANSIT'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-info" htmlFor="status-transit">
                    <i className="fas fa-truck me-1"></i>
                    Yolda
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-completed"
                    value="COMPLETED"
                    checked={transferStatusFilter === 'COMPLETED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-success" htmlFor="status-completed">
                    <i className="fas fa-check-circle me-1"></i>
                    Tamamlandı
                  </label>

                  <input
                    type="radio"
                    className="btn-check"
                    name="transferStatus"
                    id="status-cancelled"
                    value="CANCELLED"
                    checked={transferStatusFilter === 'CANCELLED'}
                    onChange={(e) => setTransferStatusFilter(e.target.value)}
                  />
                  <label className="btn btn-outline-danger" htmlFor="status-cancelled">
                    <i className="fas fa-times-circle me-1"></i>
                    İptal
                  </label>
                </div>
                
                <div className="text-muted small">
                  <i className="fas fa-info-circle me-1"></i>
                  Toplam {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).length} transfer
                </div>
              </div>

              {/* Advanced transfer filters */}
              <div className="row g-2 mt-3">
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-box"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Ürün adı ara..."
                      value={transferProductName}
                      onChange={(e) => setTransferProductName(e.target.value)}
                    />
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-barcode"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Stok kodu (SKU) ara..."
                      value={transferSku}
                      onChange={(e) => setTransferSku(e.target.value)}
                    />
                  </div>
                </div>
                <div className="col-md-4">
                  <div className="input-group">
                    <span className="input-group-text"><i className="fas fa-id-card"></i></span>
                    <input
                      type="text"
                      className="form-control"
                      placeholder="Şoför adı ara..."
                      value={transferDriver}
                      onChange={(e) => setTransferDriver(e.target.value)}
                    />
                  </div>
                </div>
              </div>
              <div className="row g-2 mt-2">
                <div className="col-md-6">
                  <SearchableSelect
                    value={transferSourceWarehouseId}
                    onChange={(id) => setTransferSourceWarehouseId(id != null ? Number(id) : null)}
                    searchEndpoint="/api/warehouses"
                    placeholder="Kaynak depo ara..."
                    allowClear={true}
                    clearText="Temizle"
                    wrapperClassName="mb-0"
                    renderOption={(w) => w.name}
                  />
                </div>
                <div className="col-md-6">
                  <SearchableSelect
                    value={transferDestinationWarehouseId}
                    onChange={(id) => setTransferDestinationWarehouseId(id != null ? Number(id) : null)}
                    searchEndpoint="/api/warehouses"
                    placeholder="Hedef depo ara..."
                    allowClear={true}
                    clearText="Temizle"
                    wrapperClassName="mb-0"
                    renderOption={(w) => w.name}
                  />
                </div>
              </div>
              <div className="col-md-12 d-flex flex-wrap gap-2 mt-1">
                {(transferProductName || transferSku || transferDriver || transferSourceWarehouseId || transferDestinationWarehouseId) && (
                  <button
                    className="btn btn-sm btn-outline-secondary"
                    onClick={() => {
                      setTransferProductName('');
                      setTransferSku('');
                      setTransferDriver('');
                      setTransferSourceWarehouseId(null);
                      setTransferDestinationWarehouseId(null);
                    }}
                  >
                    <i className="fas fa-times me-1"></i>
                    Transfer filtrelerini temizle
                  </button>
                )}
              </div>
            </div>
          </div>

          <div className="card shadow-sm">
            <div className="card-header bg-gradient text-white" style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
              <div className="d-flex justify-content-between align-items-center">
                <h5 className="mb-0">
                  <i className="fas fa-history me-2"></i>
                  Transfer Geçmişi
                </h5>
                <span className="badge bg-white text-dark">
                  {transfers.filter(t => transferStatusFilter === 'ALL' || t.status === transferStatusFilter).length} kayıt
                </span>
              </div>
            </div>
            <div className="card-body p-0">
              {(() => {
                const filteredCount = filteredTransfers.length;
                return filteredCount === 0;
              })() ? (
                <div className="text-center py-5">
                  <i className="fas fa-inbox fa-4x text-muted mb-3"></i>
                  <h5 className="text-muted">
                    {transferStatusFilter === 'ALL' 
                      ? 'Henüz transfer kaydı bulunmuyor' 
                      : `${transferStatusFilter === 'PENDING' ? 'Beklemede' : transferStatusFilter === 'IN_TRANSIT' ? 'Yolda' : transferStatusFilter === 'COMPLETED' ? 'Tamamlanmış' : 'İptal edilmiş'} transfer bulunmuyor`
                    }
                  </h5>
                  <p className="text-muted">
                    {transferStatusFilter === 'ALL' && 'İlk transferi oluşturmak için stok listesinden "Transfer Yap" butonuna tıklayın.'}
                  </p>
                </div>
              ) : (
              <div className="table-responsive" style={{overflowX: 'auto'}}>
                <table className="table table-hover mb-0 align-middle" style={{minWidth: '1200px'}}>
                  {/* Desktop için fixed layout */}
                  <colgroup className="d-none d-xl-table-column-group">
                    <col style={{width: '70px'}} />      {/* No */}
                    <col style={{width: '130px'}} />     {/* Tarih */}
                    <col style={{width: '180px'}} />     {/* Ürün */}
                    <col style={{width: '180px'}} />     {/* Kaynak */}
                    <col style={{width: '180px'}} />     {/* Hedef */}
                    <col style={{width: '85px'}} />      {/* Miktar */}
                    <col style={{width: '150px'}} />     {/* Şoför */}
                    <col style={{width: '110px'}} />     {/* Plaka */}
                    <col style={{width: '130px'}} />     {/* Durum */}
                    <col style={{width: '180px'}} />     {/* İşlemler */}
                  </colgroup>
                  <thead className="table-light sticky-top" style={{position: 'sticky', top: 0, zIndex: 10}}>
                    <tr>
                      <th className="text-center align-middle" style={{minWidth: '60px'}}>
                        <i className="fas fa-hashtag d-none d-sm-inline me-1"></i>
                        <div className="small">No</div>
                      </th>
                      <th className="align-middle" style={{minWidth: '120px'}}>
                        <i className="fas fa-calendar d-none d-sm-inline me-1"></i>
                        <div className="small">Tarih</div>
                      </th>
                      <th className="align-middle" style={{minWidth: '150px'}}>
                        <i className="fas fa-box d-none d-sm-inline me-1"></i>
                        <div className="small">Ürün</div>
                      </th>
                      <th className="align-middle" style={{minWidth: '150px'}}>
                        <i className="fas fa-warehouse text-danger d-none d-sm-inline me-1"></i>
                        <div className="small">Kaynak</div>
                      </th>
                      <th className="align-middle" style={{minWidth: '150px'}}>
                        <i className="fas fa-warehouse text-success d-none d-sm-inline me-1"></i>
                        <div className="small">Hedef</div>
                      </th>
                      <th className="text-center align-middle" style={{minWidth: '75px'}}>
                        <i className="fas fa-boxes d-none d-sm-inline me-1"></i>
                        <div className="small">Adet</div>
                      </th>
                      {/* Şoför kolonu - tablet ve üstünde göster */}
                      <th className="align-middle d-none d-md-table-cell" style={{minWidth: '140px'}}>
                        <i className="fas fa-user me-1"></i>
                        <div className="small">Şoför</div>
                      </th>
                      {/* Plaka kolonu - tablet ve üstünde göster */}
                      <th className="text-center align-middle d-none d-lg-table-cell" style={{minWidth: '100px'}}>
                        <i className="fas fa-car me-1"></i>
                        <div className="small">Plaka</div>
                      </th>
                      <th className="text-center align-middle" style={{minWidth: '120px'}}>
                        <i className="fas fa-info-circle d-none d-sm-inline me-1"></i>
                        <div className="small">Durum</div>
                      </th>
                      <th className="text-center align-middle" style={{minWidth: '160px'}}>
                        <i className="fas fa-cog d-none d-sm-inline me-1"></i>
                        <div className="small">İşlemler</div>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredTransfers.map((transfer) => {
                      const statusConfig = {
                        PENDING: { label: 'Beklemede', class: 'warning', icon: 'clock' },
                        IN_TRANSIT: { label: 'Yolda', class: 'info', icon: 'truck' },
                        COMPLETED: { label: 'Tamamlandı', class: 'success', icon: 'check-circle' },
                        CANCELLED: { label: 'İptal Edildi', class: 'danger', icon: 'times-circle' }
                      };
                      const status = statusConfig[transfer.status] || statusConfig.PENDING;

                      return (
                        <tr key={transfer.id}>
                          <td className="text-center align-middle">
                            <span className="badge bg-dark">#{transfer.id}</span>
                          </td>
                          <td className="align-middle">
                            <div className="small">
                              <div className="fw-bold text-nowrap">
                                {formatDateInTurkeyTimezone(transfer.transferDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}
                              </div>
                              <div className="text-muted text-nowrap">
                                {formatDateInTurkeyTimezone(transfer.transferDate, {
                                  hour: '2-digit',
                                  minute: '2-digit'
                                })}
                              </div>
                            </div>
                          </td>
                          <td className="align-middle">
                            <div className="text-truncate" title={transfer.product?.name}>
                              <div className="fw-bold small">{transfer.product?.name}</div>
                              <small className="text-muted d-block text-truncate">Stok Kodu: {transfer.product?.sku}</small>
                            </div>
                          </td>
                          <td className="align-middle">
                            <div className="d-flex align-items-center">
                              <div className="bg-danger bg-opacity-10 rounded-circle p-1 me-1 me-sm-2 flex-shrink-0 d-none d-sm-flex" style={{width: '30px', height: '30px', alignItems: 'center', justifyContent: 'center'}}>
                                <i className="fas fa-warehouse text-danger fa-sm"></i>
                              </div>
                              <div className="small overflow-hidden w-100">
                                <div className="fw-bold text-truncate" title={transfer.sourceWarehouse?.name}>
                                  <i className="fas fa-warehouse text-danger fa-xs me-1 d-inline d-sm-none"></i>
                                  {transfer.sourceWarehouse?.name}
                                </div>
                                <small className="text-muted d-block text-truncate" title={transfer.sourceWarehouse?.location}>
                                  {transfer.sourceWarehouse?.location}
                                </small>
                              </div>
                            </div>
                          </td>
                          <td className="align-middle">
                            <div className="d-flex align-items-center">
                              <div className="bg-success bg-opacity-10 rounded-circle p-1 me-1 me-sm-2 flex-shrink-0 d-none d-sm-flex" style={{width: '30px', height: '30px', alignItems: 'center', justifyContent: 'center'}}>
                                <i className="fas fa-warehouse text-success fa-sm"></i>
                              </div>
                              <div className="small overflow-hidden w-100">
                                <div className="fw-bold text-truncate" title={transfer.destinationWarehouse?.name}>
                                  <i className="fas fa-warehouse text-success fa-xs me-1 d-inline d-sm-none"></i>
                                  {transfer.destinationWarehouse?.name}
                                </div>
                                <small className="text-muted d-block text-truncate" title={transfer.destinationWarehouse?.location}>
                                  {transfer.destinationWarehouse?.location}
                                </small>
                              </div>
                            </div>
                          </td>
                          <td className="text-center align-middle">
                            <span className="badge bg-primary rounded-pill">{transfer.quantity}</span>
                          </td>
                          {/* Şoför kolonu - tablet ve üstünde göster */}
                          <td className="align-middle d-none d-md-table-cell">
                            <div className="small overflow-hidden">
                              <div className="fw-bold text-truncate" title={transfer.driverName}>{transfer.driverName}</div>
                              <div className="text-muted text-truncate" title={transfer.driverPhone}>
                                <i className="fas fa-phone me-1"></i>
                                {transfer.driverPhone}
                              </div>
                            </div>
                          </td>
                          {/* Plaka kolonu - desktop'ta göster */}
                          <td className="text-center align-middle d-none d-lg-table-cell">
                            <span className="badge bg-secondary text-truncate d-block mx-auto" style={{maxWidth: '100%'}} title={transfer.vehiclePlate}>
                              {transfer.vehiclePlate}
                            </span>
                          </td>
                          <td className="text-center align-middle">
                            <span className={`badge bg-${status.class} d-block py-2 mb-1`}>
                              <i className={`fas fa-${status.icon} me-1`}></i>
                              <span className="small">{status.label}</span>
                            </span>
                            {transfer.completedDate && (
                              <small className="d-block text-success mt-1" title={`Tamamlanma Tarihi: ${formatDateInTurkeyTimezone(transfer.completedDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}`}>
                                <i className="fas fa-check-circle me-1"></i>
                                <span className="d-none d-md-inline">Tamamlandı: </span>
                                {formatDateInTurkeyTimezone(transfer.completedDate, {day: '2-digit', month: '2-digit'})}
                              </small>
                            )}
                            {transfer.cancelledDate && (
                              <small className="d-block text-danger mt-1" title={`İptal Tarihi: ${formatDateInTurkeyTimezone(transfer.cancelledDate, { year: 'numeric', month: '2-digit', day: '2-digit' })}`}>
                                <i className="fas fa-times-circle me-1"></i>
                                <span className="d-none d-md-inline">İptal: </span>
                                {formatDateInTurkeyTimezone(transfer.cancelledDate, {day: '2-digit', month: '2-digit'})}
                              </small>
                            )}
                          </td>
                          <td className="text-center align-middle" style={{padding: '6px'}}>
                            <div className="d-flex flex-column gap-1">
                              {transfer.status === 'PENDING' && (
                                <>
                                  <button
                                    className="btn btn-sm btn-info w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() => {
                                      setConfirmModal({
                                        show: true,
                                        title: 'Transferi Yola Çıkart',
                                        message: 'Transfer yola çıkartılacak ve stok rezerve edilecek. Onaylıyor musunuz?',
                                        confirmText: 'Evet, Yola Çıkar',
                                        confirmVariant: 'info',
                                        icon: 'truck',
                                        onConfirm: () => {
                                          setConfirmModal({ show: false });
                                          handleTransferStatusChange(transfer.id, 'start');
                                        }
                                      });
                                    }}
                                    title="Transfer yola çıkartılacak"
                                  >
                                    <i className="fas fa-truck me-1"></i>
                                    <span className="d-none d-sm-inline">Yola Çıkar</span>
                                    <span className="d-inline d-sm-none">Yola</span>
                                  </button>
                                  <button
                                    className="btn btn-sm btn-success w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() => {
                                      setConfirmModal({
                                        show: true,
                                        title: 'Transferi Tamamla',
                                        message: 'Transfer direkt tamamlanacak ve stok kaynak depodan hedef depoya taşınacak. Onaylıyor musunuz?',
                                        confirmText: 'Evet, Tamamla',
                                        confirmVariant: 'success',
                                        icon: 'check-circle',
                                        onConfirm: () => {
                                          setConfirmModal({ show: false });
                                          handleTransferStatusChange(transfer.id, 'complete');
                                        }
                                      });
                                    }}
                                    title="Transfer direkt tamamlanacak"
                                  >
                                    <i className="fas fa-check me-1"></i>
                                    Tamamla
                                  </button>
                                  <button
                                    className="btn btn-sm btn-danger w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() => {
                                      setCancellationModal({
                                        show: true,
                                        transferId: transfer.id,
                                        reason: ''
                                      });
                                    }}
                                    title="Transferi iptal et"
                                  >
                                    <i className="fas fa-ban me-1"></i>
                                    İptal
                                  </button>
                                </>
                              )}
                              {transfer.status === 'IN_TRANSIT' && (
                                <>
                                  <button
                                    className="btn btn-sm btn-success w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() => {
                                      setConfirmModal({
                                        show: true,
                                        title: 'Transferi Tamamla',
                                        message: 'Transfer tamamlanacak ve stok kaynak depodan hedef depoya taşınacak. Rezervasyon kaldırılacak. Onaylıyor musunuz?',
                                        confirmText: 'Evet, Tamamla',
                                        confirmVariant: 'success',
                                        icon: 'check-circle',
                                        onConfirm: () => {
                                          setConfirmModal({ show: false });
                                          handleTransferStatusChange(transfer.id, 'complete');
                                        }
                                      });
                                    }}
                                    title="Transferi tamamla ve stok taşı"
                                  >
                                    <i className="fas fa-check-double me-1"></i>
                                    Tamamla
                                  </button>
                                  <button
                                    className="btn btn-sm btn-warning w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() => {
                                      setCancellationModal({
                                        show: true,
                                        transferId: transfer.id,
                                        reason: ''
                                      });
                                    }}
                                    title="Transferi iptal et ve rezervasyonu kaldır"
                                  >
                                    <i className="fas fa-ban me-1"></i>
                                    <span className="d-none d-sm-inline">İptal Et</span>
                                    <span className="d-inline d-sm-none">İptal</span>
                                  </button>
                                </>
                              )}
                              {(transfer.status === 'CANCELLED' || transfer.status === 'PENDING') && (
                                <button
                                  className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => handleDeleteTransfer(transfer.id)}
                                  title="Transfer kaydını sil"
                                >
                                  <i className="fas fa-trash me-1"></i>
                                  Sil
                                </button>
                              )}
                              {transfer.status === 'CANCELLED' && transfer.cancellationReason && (
                                <button
                                  className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => setNotesModal({ 
                                    show: true, 
                                    notes: transfer.cancellationReason, 
                                    transferId: transfer.id,
                                    title: 'İptal Nedeni'
                                  })}
                                  title="İptal nedenini görüntüle"
                                >
                                  <i className="fas fa-exclamation-circle me-1"></i>
                                  <span className="d-none d-sm-inline">İptal Nedeni</span>
                                  <span className="d-inline d-sm-none">Neden</span>
                                </button>
                              )}
                              {transfer.notes && (
                                <button
                                  className="btn btn-sm btn-outline-secondary w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => setNotesModal({ 
                                    show: true, 
                                    notes: transfer.notes, 
                                    transferId: transfer.id,
                                    title: 'Transfer Notları' 
                                  })}
                                  title="Notları görüntüle"
                                >
                                  <i className="fas fa-sticky-note me-1"></i>
                                  Notlar
                                </button>
                              )}
                              {role === 'ADMIN' && (
                                <button
                                  className="btn btn-sm btn-outline-secondary w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => setAuditModal({ show: true, entityType: 'StockTransfer', entityId: transfer.id })}
                                  title="Hareket Geçmişi"
                                >
                                  <i className="fas fa-history me-1"></i>
                                  Hareketler
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal */}
      <ConfirmModal
        show={confirmModal.show}
        title={confirmModal.title}
        message={confirmModal.message}
        confirmText={confirmModal.confirmText}
        cancelText="İptal"
        confirmVariant={confirmModal.confirmVariant}
        icon={confirmModal.icon}
        onConfirm={confirmModal.onConfirm}
        onCancel={() => setConfirmModal({ show: false })}
      />

      {/* Error Modal */}
      <ConfirmModal
        show={errorModal.show}
        title={errorModal.title}
        message={errorModal.message}
        confirmText="Tamam"
        cancelText={null}
        confirmVariant="danger"
        icon="exclamation-triangle"
        onConfirm={() => setErrorModal({ show: false, title: '', message: '' })}
        onCancel={() => setErrorModal({ show: false, title: '', message: '' })}
      />

      {/* Notes Modal */}
      <NotesModal
        show={notesModal.show}
        notes={notesModal.notes}
        title={notesModal.title}
        transferId={notesModal.transferId}
        onClose={() => setNotesModal({ show: false, notes: '', transferId: null, title: '' })}
      />

      {/* Cancellation Reason Modal */}
      {cancellationModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h5 className="modal-title">
                  <i className="fas fa-ban me-2"></i>
                  Transferi İptal Et
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setCancellationModal({ show: false, transferId: null, reason: '' })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-warning">
                  <i className="fas fa-exclamation-triangle me-2"></i>
                  Transfer iptal edilecek. Bu işlem geri alınamaz.
                </div>
                <div className="mb-3">
                  <label htmlFor="cancellationReason" className="form-label">
                    <i className="fas fa-comment-alt me-1"></i>
                    İptal Nedeni <span className="text-muted">(Opsiyonel)</span>
                  </label>
                  <textarea
                    id="cancellationReason"
                    className="form-control"
                    rows="4"
                    value={cancellationModal.reason}
                    onChange={(e) => setCancellationModal({ ...cancellationModal, reason: e.target.value })}
                    placeholder="İptal nedenini buraya yazabilirsiniz..."
                    maxLength="500"
                  />
                  <small className="text-muted">
                    {cancellationModal.reason.length}/500 karakter
                  </small>
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setCancellationModal({ show: false, transferId: null, reason: '' })}
                >
                  <i className="fas fa-times me-2"></i>
                  Vazgeç
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={() => {
                    handleTransferStatusChange(
                      cancellationModal.transferId,
                      'cancel',
                      cancellationModal.reason.trim() || null
                    );
                    setCancellationModal({ show: false, transferId: null, reason: '' });
                  }}
                >
                  <i className="fas fa-ban me-2"></i>
                  İptal Et
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Audit Timeline Modal */}
      {auditModal.show && (
        <AuditTimelineModal
          entityType={auditModal.entityType}
          entityId={auditModal.entityId}
          onClose={() => setAuditModal({ show: false, entityType: null, entityId: null })}
        />
      )}
    </div>
  );
};

export default Stock;
