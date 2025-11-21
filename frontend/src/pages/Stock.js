import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import axios from 'axios';
import StockForm from '../components/StockForm';
import QuickStockAdjustModal from '../components/QuickStockAdjustModal';
import StockSettingsModal from '../components/StockSettingsModal';
import StockTransferModal from '../components/StockTransferModal';
import StockRequestApprovalModal from '../components/StockRequestApprovalModal';
import SearchableSelect from '../components/SearchableSelect';
import FilterChips from '../components/FilterChips';
import ConfirmModal from '../components/ConfirmModal';
import NotesModal from '../components/NotesModal';
import AuditTimelineModal from '../components/AuditTimelineModal';
import MyStockRequestsModal from '../components/MyStockRequestsModal';
import PaginationControls from '../components/PaginationControls';

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

const PAGE_SIZE_OPTIONS = [10, 20, 50];

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
  const isAdmin = role === 'ADMIN';
  const canTransfer = isAdmin || role === 'STOCK_IN' || role === 'STOCK_OUT';
  const [stocks, setStocks] = useState([]);
  const [stockPage, setStockPage] = useState(0);
  const [stockPageSize, setStockPageSize] = useState(20);
  const [stockTotalPages, setStockTotalPages] = useState(0);
  const [totalStockCount, setTotalStockCount] = useState(0);
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [quickAdjustModal, setQuickAdjustModal] = useState({ show: false, stock: null, type: null });
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [showTransferHistory, setShowTransferHistory] = useState(false);
  const [selectedStock, setSelectedStock] = useState(null);
  const [transfers, setTransfers] = useState([]);
  const [transferPage, setTransferPage] = useState(0);
  const [transferPageSize, setTransferPageSize] = useState(20);
  const [transferTotalPages, setTransferTotalPages] = useState(0);
  const [transferTotalCount, setTransferTotalCount] = useState(0);
  const [transferStatusCounts, setTransferStatusCounts] = useState({});
  const [transferTypeCounts, setTransferTypeCounts] = useState({});
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
  const [transferTypeFilter, setTransferTypeFilter] = useState('ALL');
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
  const [notesModal, setNotesModal] = useState({ show: false, notes: '', transferId: null, title: '' });
  const [cancellationModal, setCancellationModal] = useState({ show: false, transferId: null, reason: '' });
  const [completionModal, setCompletionModal] = useState({ show: false, transferId: null, note: '', message: '', transfer: null });
  const [auditModal, setAuditModal] = useState({ show: false, entityType: null, entityId: null });
  const [pendingStockId, setPendingStockId] = useState(null);
  const [showExcelModal, setShowExcelModal] = useState(false);
  const [excelUploading, setExcelUploading] = useState(false);
  const [excelFile, setExcelFile] = useState(null);
  const [excelWarehouseId, setExcelWarehouseId] = useState(null);
  const [excelResult, setExcelResult] = useState(null);
  const [showApprovalModal, setShowApprovalModal] = useState(false);
  const [showMyRequestsModal, setShowMyRequestsModal] = useState(false);
  const [lockCustomerTransfer, setLockCustomerTransfer] = useState(false);
  const [pendingRequestsCount, setPendingRequestsCount] = useState(0);
  const [selectedStocks, setSelectedStocks] = useState([]);
  const [selectedTransfers, setSelectedTransfers] = useState([]);
  const [successToast, setSuccessToast] = useState({ show: false, message: '' });
  const toastTimeoutRef = useRef(null);

  const fetchStocks = useCallback(async (pageOverride = 0, pageSizeOverride) => {
    const size = pageSizeOverride ?? stockPageSize;
    const categoryId = selectedCategory ? Number(selectedCategory) : undefined;
    const subCategoryId = selectedSubcategory ? Number(selectedSubcategory) : undefined;
    const sortByParam = stockSortBy === 'lastUpdated'
      ? 'lastUpdated'
      : (stockSortBy === 'quantity' ? 'quantity' : 'warehouse');
    const params = {
      page: pageOverride,
      size,
      brandId: brandId || undefined,
      colorId: colorId || undefined,
      warehouseId: selectedWarehouseId || undefined,
      categoryId: Number.isNaN(categoryId) ? undefined : categoryId,
      subCategoryId: Number.isNaN(subCategoryId) ? undefined : subCategoryId,
      reservedOnly: showReserved || undefined,
      consignedOnly: showConsigned || undefined,
      status: filter,
      search: searchTerm ? searchTerm.trim() : undefined,
      sortBy: sortByParam,
      sortDir: stockSortDir
    };

    const response = await axios.get('/api/stocks', { params });
    const data = response.data || {};
    const content = Array.isArray(data.content) ? data.content : [];

    setStocks(content);
    setStockPage(data.page ?? pageOverride);
    setTotalStockCount(typeof data.totalElements === 'number' ? data.totalElements : content.length);
    setStockTotalPages(typeof data.totalPages === 'number' ? data.totalPages : 0);
    return data;
  }, [stockPageSize, brandId, colorId, selectedWarehouseId, selectedCategory, selectedSubcategory, showReserved, showConsigned, filter, searchTerm, stockSortBy, stockSortDir]);

  const fetchTransfers = useCallback(async (page = 0, append = false, pageSizeOverride) => {
    const size = pageSizeOverride ?? transferPageSize;
    try {
      const normalizedProductName = transferProductName ? transferProductName.toLocaleLowerCase('tr-TR') : undefined;
      const normalizedSku = transferSku ? transferSku.toLocaleLowerCase('tr-TR') : undefined;
      const normalizedDriver = transferDriver ? transferDriver.toLocaleLowerCase('tr-TR') : undefined;
      const params = {
        page,
        size,
        status: transferStatusFilter !== 'ALL' ? transferStatusFilter : undefined,
        transferType: transferTypeFilter !== 'ALL' ? transferTypeFilter : undefined,
        productName: normalizedProductName,
        sku: normalizedSku,
        driverName: normalizedDriver,
        sourceWarehouseId: transferSourceWarehouseId || undefined,
        destinationWarehouseId: transferDestinationWarehouseId || undefined
      };
      const endpoint = isAdmin ? '/api/stock-transfers' : '/api/stock-transfers/current-user';
      const response = await axios.get(endpoint, { params });
      const data = response.data || {};
      const content = Array.isArray(data.content)
        ? data.content
        : (Array.isArray(data) ? data : []);
      setTransfers(prev => append ? [...prev, ...content] : content);
      setTransferPage(data.page ?? page);
      setTransferTotalCount(prevCount => {
        if (typeof data.totalElements === 'number') {
          return data.totalElements;
        }
        return append ? prevCount + content.length : content.length;
      });
      setTransferTotalPages(typeof data.totalPages === 'number' ? data.totalPages : 0);

      const metadata = data.metadata || {};
      setTransferStatusCounts(metadata.statusCounts || {});
      setTransferTypeCounts(metadata.transferTypeCounts || {});
      return data;
    } catch (error) {
      console.error('Error fetching transfers:', error);
      throw error;
    }
  }, [isAdmin, transferStatusFilter, transferTypeFilter, transferProductName, transferSku, transferDriver, transferSourceWarehouseId, transferDestinationWarehouseId, transferPageSize]);

  const fetchAllData = useCallback(async () => {
    try {
      setLoading(true);
      const calls = [
        axios.get('/api/products'),
        axios.get('/api/warehouses')
      ];
      
      // Fetch pending requests count for admins
      if (role === 'ADMIN') {
        calls.push(
          axios.get('/api/stock-requests/pending/count'),
          axios.get('/api/stock-transfers/approvals/count')
        );
      }

      const results = await Promise.all(calls);

      let index = 0;
      setProducts(results[index++].data);
      setWarehouses(results[index++].data);
      
      if (role === 'ADMIN') {
        const stockPendingResult = results[index] || null;
        const transferPendingResult = results[index + 1] || null;
        index += 2;
        const stockPending = stockPendingResult?.data?.count || 0;
        const transferPending = transferPendingResult?.data?.count || 0;
        setPendingRequestsCount(stockPending + transferPending);
      }
    } catch (error) {
      console.error('Error fetching data:', error);
      setError('Veriler yüklenirken hata oluştu');
    } finally {
      setLoading(false);
    }
  }, [role]);
  const handleStockPageSizeChange = async (e) => {
    const newSize = Number(e.target.value);
    setStockPageSize(newSize);
    setStockPage(0);
    try {
      await fetchStocks(0, newSize);
    } catch (error) {
      console.error('Error changing stock page size:', error);
      setError('Stok verileri yüklenirken hata oluştu');
    }
  };

  const handleTransferPageSizeChange = async (e) => {
    const newSize = Number(e.target.value);
    setTransferPageSize(newSize);
    setTransferPage(0);
    try {
      await fetchTransfers(0, false, newSize);
    } catch (error) {
      console.error('Error changing transfer page size:', error);
      setError('Transfer verileri yüklenirken hata oluştu');
    }
  };

  const handleStockPageChange = (newPage) => {
    const total = stockTotalPages || 0;
    if (newPage < 0 || (total > 0 && newPage >= total) || newPage === stockPage) {
      return;
    }
    setStockPage(newPage);
  };

  const handleTransferPageChange = (newPage) => {
    const total = transferTotalPages || 0;
    if (newPage < 0 || (total > 0 && newPage >= total) || newPage === transferPage) {
      return;
    }
    setTransferPage(newPage);
  };


  useEffect(() => {
    fetchStocks(stockPage);
  }, [fetchStocks, stockPage]);

  useEffect(() => {
    fetchAllData();
  }, [fetchAllData]);

  useEffect(() => {
    if (showTransferHistory) {
      fetchTransfers(transferPage, false);
    }
  }, [showTransferHistory, fetchTransfers, transferPage]);

  useEffect(() => {
    setStockPage(0);
  }, [filter, searchTerm, selectedCategory, selectedSubcategory, showReserved, showConsigned, brandId, colorId, selectedWarehouseId, stockSortBy, stockSortDir]);

  useEffect(() => {
    setTransferPage(0);
  }, [transferStatusFilter, transferTypeFilter, transferProductName, transferSku, transferDriver, transferSourceWarehouseId, transferDestinationWarehouseId]);

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
      setTimeout(() => { fetchTransfers(0, false); }, 0);
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

  // Listen to global event to open stock approval modal from Navbar
  const [approvalModalTab, setApprovalModalTab] = useState('stock');

  useEffect(() => {
    const handler = (event) => {
      const tab = event.detail?.tab || 'stock';
      setApprovalModalTab(tab);
      setShowApprovalModal(true);
    };
    window.addEventListener('open-stock-approval', handler);
    return () => window.removeEventListener('open-stock-approval', handler);
  }, []);

  // Check URL parameter to open approval modal
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('openApproval') === 'true') {
      const tab = params.get('tab') || 'stock';
      setApprovalModalTab(tab);
      setShowApprovalModal(true);
      // Clean up URL parameter
      params.delete('openApproval');
      params.delete('tab');
      const newSearch = params.toString();
      const newUrl = newSearch ? `${location.pathname}?${newSearch}` : location.pathname;
      window.history.replaceState({}, '', newUrl);
    }
  }, [location.search]);

  // Open quick adjustment modal when stocks loaded and pendingStockId exists
  useEffect(() => {
    if (!pendingStockId || !Array.isArray(stocks) || stocks.length === 0) return;
    const s = stocks.find(x => x.id === pendingStockId);
    if (s) {
      setQuickAdjustModal({ show: true, stock: s, type: 'add' });
      setPendingStockId(null);
    }
  }, [stocks, pendingStockId]);

  useEffect(() => {
    if (!selectedStocks.length) return;
    setSelectedStocks(prev => prev.filter(id => stocks.some(stock => stock.id === id)));
  }, [stocks, selectedStocks.length]);

  const getEffectiveMin = useCallback((stock) => {
    const val = Number(stock?.minStockLevel);
    if (!Number.isFinite(val) || val <= 0) return 10;
    return val;
  }, []);

  const allVisibleStockIds = useMemo(() => stocks.map(stock => stock.id), [stocks]);
  const areAllVisibleSelected = stocks.length > 0 && allVisibleStockIds.every(id => selectedStocks.includes(id));
  const selectedStockCount = selectedStocks.length;

  const toggleSelectAllVisible = () => {
    if (!stocks.length) return;
    setSelectedStocks(prev => {
      if (areAllVisibleSelected) {
        return prev.filter(id => !allVisibleStockIds.includes(id));
      }
      const merged = new Set(prev);
      allVisibleStockIds.forEach(id => merged.add(id));
      return Array.from(merged);
    });
  };

  const toggleStockSelection = (id) => {
    setSelectedStocks(prev =>
      prev.includes(id) ? prev.filter(existingId => existingId !== id) : [...prev, id]
    );
  };

  const clearSelectedStocks = () => setSelectedStocks([]);
  const clearSelectedTransfers = () => setSelectedTransfers([]);
  
  const toggleTransferSelection = (id) => {
    setSelectedTransfers(prev => 
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    );
  };
  
  const toggleSelectAllVisibleTransfers = () => {
    const allVisibleTransferIds = transfers.map(t => t.id);
    const allSelected = allVisibleTransferIds.every(id => selectedTransfers.includes(id));
    if (allSelected) {
      setSelectedTransfers(prev => prev.filter(id => !allVisibleTransferIds.includes(id)));
    } else {
      setSelectedTransfers(prev => [...new Set([...prev, ...allVisibleTransferIds])]);
    }
  };
  
  const areAllVisibleTransfersSelected = transfers.length > 0 && transfers.every(t => selectedTransfers.includes(t.id));
  const selectedTransferCount = selectedTransfers.length;

  const showSuccessToast = (message) => {
    setSuccessToast({ show: true, message });
    if (toastTimeoutRef.current) {
      clearTimeout(toastTimeoutRef.current);
    }
    toastTimeoutRef.current = setTimeout(() => {
      setSuccessToast({ show: false, message: '' });
    }, 4000);
  };

  useEffect(() => {
    return () => {
      if (toastTimeoutRef.current) {
        clearTimeout(toastTimeoutRef.current);
      }
    };
  }, []);



  const handleCreateStock = () => {
    setSelectedStock(null);
    setShowForm(true);
  };

  const handleQuickAdd = (stock) => {
    setQuickAdjustModal({ show: true, stock, type: 'add' });
  };

  const handleQuickRemove = (stock) => {
    setQuickAdjustModal({ show: true, stock, type: 'remove' });
  };

  const handleStockSettings = (stock) => {
    setSelectedStock(stock);
    setShowSettingsModal(true);
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
          await fetchAllData();
          showSuccessToast('Stok kaydı silindi.');
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

  const handleBatchDeleteStocks = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    setConfirmModal({
      show: true,
      title: 'Seçili Stokları Sil',
      message: `${ids.length} stok kaydını silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete('/api/stocks/bulk', { data: ids });
          setSelectedStocks(prev => prev.filter(id => !ids.includes(id)));
          await fetchAllData();
          showSuccessToast(`${ids.length} stok kaydı silindi.`);
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Stok Silme Hatası',
            message: `Seçili stoklar silinirken hata oluştu: ${msg}`
          });
        }
      }
    });
  };

  const handleBatchDeleteTransfers = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }
    
    const canDelete = ids.every(id => {
      const transfer = transfers.find(t => t.id === id);
      return transfer && transfer.status !== 'IN_TRANSIT' && transfer.status !== 'COMPLETED';
    });
    
    if (!canDelete) {
      setErrorModal({
        show: true,
        title: 'Transfer Silme Hatası',
        message: 'Yolda veya tamamlanmış transferler silinemez.'
      });
      return;
    }
    
    setConfirmModal({
      show: true,
      title: 'Seçili Transferleri Sil',
      message: `${ids.length} transferi silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.`,
      confirmText: 'Evet, Sil',
      confirmVariant: 'danger',
      icon: 'trash',
      onConfirm: async () => {
        setConfirmModal({ show: false });
        try {
          await axios.delete('/api/stock-transfers/bulk', { data: ids });
          setSelectedTransfers(prev => prev.filter(id => !ids.includes(id)));
          await fetchTransfers(0, false);
          showSuccessToast(`${ids.length} transfer silindi.`);
        } catch (error) {
          const errorData = error?.response?.data;
          const msg = errorData?.message || errorData?.error || error.message || 'Beklenmeyen bir durum oluştu';
          setErrorModal({
            show: true,
            title: 'Transfer Silme Hatası',
            message: `Seçili transferler silinirken hata oluştu: ${msg}`
          });
        }
      }
    });
  };

  const handleFormSuccess = (options = {}) => {
    const shouldClose = options.close !== false;
    if (shouldClose) {
      setShowForm(false);
    }
    fetchAllData();
    if (options.message) {
      showSuccessToast(options.message);
    }
  };

  const handleQuickAdjustSuccess = () => {
    setQuickAdjustModal({ show: false, stock: null, type: null });
    fetchAllData();
  };

  const handleSettingsSuccess = () => {
    setShowSettingsModal(false);
    setSelectedStock(null);
    fetchAllData();
  };

  const handleStockTransfer = (stock, lockToCustomer = false) => {
    setSelectedStock(stock);
    setLockCustomerTransfer(lockToCustomer);
    setShowTransferModal(true);
  };

  const handleTransferSuccess = () => {
    setShowTransferModal(false);
    setSelectedStock(null);
    setLockCustomerTransfer(false);
    fetchAllData();
    if (showTransferHistory) {
      fetchTransfers(0, false);
    }
  };

  const handleShowTransferHistory = () => {
    setShowTransferHistory(!showTransferHistory);
  };

  const handleTransferStatusChange = async (transferId, action, payload = undefined) => {
    try {
      const body = payload && Object.keys(payload).length > 0 ? payload : undefined;
      const response = await axios.post(`/api/stock-transfers/${transferId}/${action}`, body);
      const updatedTransfer = response?.data;
      fetchTransfers(0, false);
      fetchAllData();
      if (action === 'start' && updatedTransfer?.approvalStatus === 'PENDING') {
        setErrorModal({
          show: true,
          title: 'Onay Talebi Oluşturuldu',
          message: 'Transferi başlatmak için yönetici onayı bekleniyor.'
        });
      }
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
          fetchTransfers(0, false);
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

  const openCompletionFlow = (transfer, message) => {
    if ((transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY') {
      setCompletionModal({
        show: true,
        transferId: transfer.id,
        note: '',
        message,
        transfer
      });
      return;
    }

    setConfirmModal({
      show: true,
      title: 'Transferi Tamamla',
      message,
      confirmText: 'Evet, Tamamla',
      confirmVariant: 'success',
      icon: 'check-circle',
      onConfirm: () => {
        setConfirmModal({ show: false });
        handleTransferStatusChange(transfer.id, 'complete');
      }
    });
  };

  const getProductById = (id) => {
    return products.find(p => p.id === id);
  };

  const getWarehouseById = (id) => {
    return warehouses.find(w => w.id === id);
  };

  const getTransferItemsList = (transfer) => {
    if (Array.isArray(transfer.items) && transfer.items.length > 0) {
      return transfer.items;
    }
    if (transfer.product) {
      return [{
        product: transfer.product,
        quantity: transfer.quantity
      }];
    }
    return [];
  };

  const getTransferTotalQuantity = (transfer) => {
    if (typeof transfer.totalQuantity === 'number' && transfer.totalQuantity > 0) {
      return transfer.totalQuantity;
    }
    return getTransferItemsList(transfer).reduce((sum, item) => sum + (item.quantity || 0), 0);
  };

  const getStatusCount = (status) => {
    if (transferStatusCounts && Object.prototype.hasOwnProperty.call(transferStatusCounts, status)) {
      return transferStatusCounts[status];
    }
    return transfers.filter(t => t.status === status).length;
  };

  const getTransferTypeCount = (type) => {
    if (transferTypeCounts && Object.prototype.hasOwnProperty.call(transferTypeCounts, type)) {
      return transferTypeCounts[type];
    }
    if (type === 'CUSTOMER_DELIVERY') {
      return transfers.filter(t => t.transferType === 'CUSTOMER_DELIVERY').length;
    }
    return transfers.filter(t => (t.transferType || 'WAREHOUSE') === 'WAREHOUSE').length;
  };

  const getStockStatus = (stock) => {
    const available = (stock.quantity || 0) - (stock.reservedQuantity || 0) - (stock.consignedQuantity || 0);
    if (available <= 0) return { status: 'out', label: 'Stok Dışı', class: 'danger' };
    if (available <= getEffectiveMin(stock)) return { status: 'low', label: 'Düşük Stok', class: 'warning' };
    return { status: 'normal', label: 'Normal', class: 'success' };
  };

  const totalTransfers = typeof transferTotalCount === 'number' ? transferTotalCount : transfers.length;
  const warehouseTransferCount = getTransferTypeCount('WAREHOUSE');
  const customerTransferCount = getTransferTypeCount('CUSTOMER_DELIVERY');

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
        <h2>
          <i className="fas fa-boxes me-2"></i>
          Stok Yönetimi
        </h2>
        <div className="d-flex flex-column flex-lg-row align-items-lg-center gap-2 w-100 justify-content-lg-end">
          {isAdmin ? (
            <div className="d-flex flex-wrap gap-2 justify-content-lg-end w-100">
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
              <button 
                className="btn btn-warning position-relative" 
                onClick={() => setShowApprovalModal(true)}
                title="Stok talep onayları"
                style={{ zIndex: 1 }}
              >
                <i className="fas fa-tasks me-2"></i>
                Onay Bekleyenler
                {pendingRequestsCount > 0 && (
                  <span 
                    className="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger"
                    style={{ zIndex: 10 }}
                  >
                    {pendingRequestsCount}
                    <span className="visually-hidden">bekleyen talepler</span>
                  </span>
                )}
              </button>
              <button className="btn btn-primary" onClick={handleCreateStock}>
                <i className="fas fa-plus me-2"></i>
                Yeni Stok Kaydı
              </button>
              <button className="btn btn-success" onClick={handleShowTransferHistory}>
                <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
                {showTransferHistory ? 'Stok Listesi' : 'Transfer Geçmişi'}
              </button>
            </div>
          ) : (
            <div className="d-flex flex-column flex-lg-row align-items-lg-center gap-2 w-100">
              <div className="alert alert-info mb-0 py-2 px-3 flex-grow-1">
                <i className="fas fa-info-circle me-2"></i>
                <span>
                  {role === 'STOCK_IN' && 'Stok ekleme talepleri oluşturabilir, müşteri sevkiyat transferlerinizi görüntüleyebilirsiniz.'}
                  {role === 'STOCK_OUT' && 'Stok çıkarma talepleri oluşturabilir, müşteri sevkiyat transferlerinizi görüntüleyebilirsiniz.'}
                </span>
              </div>
              <div className="d-flex justify-content-between align-items-center px-3 py-2 flex-wrap gap-2">
                <small className="text-muted">
                  Gösterilen kayıt: {transfers.length}/{totalTransfers || transfers.length}
                </small>
                <PaginationControls
                  page={transferPage}
                  totalPages={transferTotalPages}
                  onPageChange={handleTransferPageChange}
                />
              </div>
              <div className="d-flex flex-wrap gap-2 justify-content-lg-end">
                <button className="btn btn-success" onClick={handleShowTransferHistory}>
                  <i className={`fas fa-${showTransferHistory ? 'cubes' : 'exchange-alt'} me-2`}></i>
                  {showTransferHistory ? 'Stok Listesi' : 'Transferlerim'}
                </button>
                <button
                  className="btn btn-outline-primary"
                  onClick={() => setShowMyRequestsModal(true)}
                >
                  <i className="fas fa-clipboard-list me-2"></i>
                  Taleplerim
                </button>
              </div>
            </div>
          )}
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

      {successToast.show && (
        <div className="toast-container position-fixed top-0 end-0 p-3" style={{ zIndex: 2000 }}>
          <div className="toast show text-bg-success border-0 shadow" role="alert">
            <div className="d-flex align-items-center">
              <div className="toast-body">
                <i className="fas fa-check-circle me-2"></i>
                {successToast.message}
              </div>
              <button
                type="button"
                className="btn-close btn-close-white me-2 m-auto"
                onClick={() => setSuccessToast({ show: false, message: '' })}
              ></button>
            </div>
          </div>
        </div>
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
          <div className="d-flex justify-content-end align-items-center mb-2 gap-2 flex-wrap">
            <label className="form-label mb-0 small text-muted">
              Sayfa Boyutu:
              <select
                className="form-select form-select-sm d-inline-block ms-2"
                style={{ width: 'auto' }}
                value={stockPageSize}
                onChange={handleStockPageSizeChange}
              >
                {PAGE_SIZE_OPTIONS.map(size => (
                  <option key={`stock-page-${size}`} value={size}>{size}</option>
                ))}
              </select>
            </label>
          </div>
          {selectedStockCount > 0 && (
            <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
              <div className="fw-semibold">
                <i className="fas fa-check-square me-2"></i>
                {selectedStockCount} stok seçildi
              </div>
              <div className="d-flex gap-2 flex-wrap">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={clearSelectedStocks}
                >
                  Seçimi Temizle
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={() => handleBatchDeleteStocks([...selectedStocks])}
                >
                  <i className="fas fa-trash me-1"></i>
                  Seçilileri Sil
                </button>
              </div>
            </div>
          )}
          <div className="table-responsive">
            <table className="table table-striped table-hover">
              <thead>
                <tr>
                  <th className="text-center" style={{ width: '40px' }}>
                    <div className="form-check mb-0">
                      <input
                        className="form-check-input"
                        type="checkbox"
                        checked={areAllVisibleSelected}
                        onChange={toggleSelectAllVisible}
                        disabled={stocks.length === 0}
                        aria-label="Tümünü seç"
                      />
                    </div>
                  </th>
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
                {stocks.map((stock) => {
                  const product = getProductById(stock.product.id);
                  const warehouse = getWarehouseById(stock.warehouse.id);
                  const stockStatus = getStockStatus(stock);
                  const categoryPath = product?.category ? `${product.category.parentName ? product.category.parentName + ' > ' : ''}${product.category.name}` : null;
                  const isSelected = selectedStocks.includes(stock.id);

                  return (
                    <tr key={stock.id} className={isSelected ? 'table-active' : ''}>
                      <td className="text-center align-middle">
                        <div className="form-check mb-0">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleStockSelection(stock.id)}
                            aria-label="Stok seç"
                          />
                        </div>
                      </td>
                      <td>{warehouse?.name}</td>
                      <td>
                        <div className="fw-semibold">{product?.name}</div>
                        {categoryPath && (
                          <small className="text-muted d-block">
                            <i className="fas fa-tag me-1"></i>
                            {categoryPath}
                          </small>
                        )}
                        {stock.additionNote && (
                          <small
                            className="text-muted fst-italic d-block mt-1 text-truncate"
                            title={stock.additionNote}
                          >
                            <i className="fas fa-sticky-note me-1"></i>
                            {stock.additionNote}
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
                          {/* Quick Add Button - for ADMIN and STOCK_IN */}
                          {(role === 'ADMIN' || role === 'STOCK_IN') && (
                            <button
                              className="btn btn-sm btn-success"
                              onClick={() => handleQuickAdd(stock)}
                              title="Hızlı Stok Ekle"
                            >
                              <i className="fas fa-plus"></i>
                            </button>
                          )}
                          
                          {/* Quick Remove Button - for ADMIN and STOCK_OUT */}
                          {(role === 'ADMIN' || role === 'STOCK_OUT') && (
                            <button
                              className="btn btn-sm btn-danger"
                              onClick={() => handleQuickRemove(stock)}
                              title="Hızlı Stok Çıkar"
                            >
                              <i className="fas fa-minus"></i>
                            </button>
                          )}
                          
                          {/* Settings Button - for viewing (all), editing (ADMIN only) */}
                          <button
                            className="btn btn-sm btn-outline-primary"
                            onClick={() => handleStockSettings(stock)}
                            title={role === 'ADMIN' ? 'Ayarlar (Emanet, Min Stok)' : 'Stok Detayları'}
                          >
                            <i className="fas fa-cog"></i>
                          </button>
                          
                          {canTransfer && (
                              <button
                                className="btn btn-sm btn-outline-success"
                              onClick={() => handleStockTransfer(stock, role !== 'ADMIN')}
                                title="Transfer Yap"
                              >
                                <i className="fas fa-exchange-alt"></i>
                              </button>
                          )}
                          {role === 'ADMIN' && (
                            <>
                              <button
                                className="btn btn-sm btn-outline-danger"
                                onClick={() => handleDeleteStock(stock.id)}
                                title="Stok Kaydını Sil"
                              >
                                <i className="fas fa-trash"></i>
                              </button>
                              <button
                                className="btn btn-sm btn-outline-secondary"
                                onClick={() => setAuditModal({ show: true, entityType: 'Stock', entityId: stock.id })}
                                title="Hareket Geçmişi"
                              >
                                <i className="fas fa-history"></i>
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
            <small className="text-muted">
              Gösterilen kayıt: {stocks.length}/{totalStockCount || stocks.length}
            </small>
            <PaginationControls
              page={stockPage}
              totalPages={stockTotalPages}
              onPageChange={handleStockPageChange}
            />
          </div>

          {stocks.length === 0 && (
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

      {/* Quick Stock Adjustment Modal */}
      {quickAdjustModal.show && quickAdjustModal.stock && (
        <QuickStockAdjustModal
          stock={quickAdjustModal.stock}
          type={quickAdjustModal.type}
          onSuccess={handleQuickAdjustSuccess}
          onClose={() => setQuickAdjustModal({ show: false, stock: null, type: null })}
        />
      )}

      {/* Stock Settings Modal */}
      {showSettingsModal && selectedStock && (
        <StockSettingsModal
          stock={selectedStock}
          onSuccess={handleSettingsSuccess}
          onClose={() => {
            setShowSettingsModal(false);
            setSelectedStock(null);
          }}
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
            setLockCustomerTransfer(false);
          }}
          lockToCustomerDelivery={lockCustomerTransfer}
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
                  <h3 className="mb-0">{getStatusCount('PENDING')}</h3>
                  <p className="text-muted mb-0 small">Beklemede</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-info shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-truck fa-2x text-info mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('IN_TRANSIT')}</h3>
                  <p className="text-muted mb-0 small">Yolda</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-success shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-check-circle fa-2x text-success mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('COMPLETED')}</h3>
                  <p className="text-muted mb-0 small">Tamamlandı</p>
                </div>
              </div>
            </div>
            <div className="col-md-3">
              <div className="card border-danger shadow-sm">
                <div className="card-body text-center">
                  <i className="fas fa-times-circle fa-2x text-danger mb-2"></i>
                  <h3 className="mb-0">{getStatusCount('CANCELLED')}</h3>
                  <p className="text-muted mb-0 small">İptal Edildi</p>
                </div>
              </div>
            </div>
          </div>

          <div className="d-flex flex-wrap gap-2 mb-3">
            <span className="badge rounded-pill bg-primary bg-opacity-10 text-primary py-2 px-3">
              <i className="fas fa-warehouse me-1"></i>
              Depo Transferi: {warehouseTransferCount}
            </span>
            <span className="badge rounded-pill bg-info bg-opacity-10 text-info py-2 px-3">
              <i className="fas fa-shipping-fast me-1"></i>
              Müşteri Sevkiyatı: {customerTransferCount}
            </span>
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
                    Tümü ({totalTransfers})
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
                Toplam {totalTransfers} transfer
                </div>
              </div>

            <div className="d-flex flex-wrap gap-2 mt-3">
              <div className="btn-group btn-group-sm" role="group" aria-label="Transfer tipi filtresi">
                <input
                  type="radio"
                  className="btn-check"
                  name="transferType"
                  id="type-all"
                  value="ALL"
                  checked={transferTypeFilter === 'ALL'}
                  onChange={(e) => setTransferTypeFilter(e.target.value)}
                />
                <label className="btn btn-outline-secondary" htmlFor="type-all">
                  <i className="fas fa-stream me-1"></i>
                  Tüm Tipler
                </label>

                <input
                  type="radio"
                  className="btn-check"
                  name="transferType"
                  id="type-warehouse"
                  value="WAREHOUSE"
                  checked={transferTypeFilter === 'WAREHOUSE'}
                  onChange={(e) => setTransferTypeFilter(e.target.value)}
                />
                <label className="btn btn-outline-primary" htmlFor="type-warehouse">
                  <i className="fas fa-warehouse me-1"></i>
                  Depo
                </label>

                <input
                  type="radio"
                  className="btn-check"
                  name="transferType"
                  id="type-customer"
                  value="CUSTOMER_DELIVERY"
                  checked={transferTypeFilter === 'CUSTOMER_DELIVERY'}
                  onChange={(e) => setTransferTypeFilter(e.target.value)}
                />
                <label className="btn btn-outline-info" htmlFor="type-customer">
                  <i className="fas fa-shipping-fast me-1"></i>
                  Müşteri
                </label>
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
                  {transferStatusFilter === 'ALL' ? totalTransfers : getStatusCount(transferStatusFilter)} kayıt
                </span>
              </div>
              <div className="mt-2 d-flex justify-content-end">
                <label className="form-label mb-0 small text-white">
                  Sayfa Boyutu:
                  <select
                    className="form-select form-select-sm d-inline-block ms-2"
                    style={{ width: 'auto' }}
                    value={transferPageSize}
                    onChange={handleTransferPageSizeChange}
                  >
                    {PAGE_SIZE_OPTIONS.map(size => (
                      <option key={`transfer-page-${size}`} value={size}>{size}</option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
            {selectedTransferCount > 0 && isAdmin && (
              <div className="alert alert-warning d-flex justify-content-between align-items-center flex-wrap gap-2 m-3 mb-0">
                <div className="fw-semibold">
                  <i className="fas fa-check-square me-2"></i>
                  {selectedTransferCount} transfer seçildi
                </div>
                <div className="d-flex gap-2 flex-wrap">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary"
                    onClick={clearSelectedTransfers}
                  >
                    Seçimi Temizle
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm btn-danger"
                    onClick={() => handleBatchDeleteTransfers([...selectedTransfers])}
                  >
                    <i className="fas fa-trash me-1"></i>
                    Seçilileri Sil
                  </button>
                </div>
              </div>
            )}
            <div className="card-body p-0">
              {transfers.length === 0 ? (
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
              <>
              <div className="table-responsive" style={{overflowX: 'auto'}}>
                <table className="table table-hover mb-0 align-middle" style={{minWidth: '1200px'}}>
                  {/* Desktop için fixed layout */}
                  <colgroup className="d-none d-xl-table-column-group">
                    {isAdmin && <col style={{width: '40px'}} />}  {/* Checkbox */}
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
                      {isAdmin && (
                        <th className="text-center align-middle" style={{minWidth: '40px'}}>
                          <div className="form-check mb-0">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={areAllVisibleTransfersSelected}
                              onChange={toggleSelectAllVisibleTransfers}
                              disabled={transfers.length === 0}
                              aria-label="Tümünü seç"
                            />
                          </div>
                        </th>
                      )}
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
                    {transfers.map((transfer) => {
                      const statusConfig = {
                        PENDING: { label: 'Beklemede', class: 'warning', icon: 'clock' },
                        IN_TRANSIT: { label: 'Yolda', class: 'info', icon: 'truck' },
                        COMPLETED: { label: 'Tamamlandı', class: 'success', icon: 'check-circle' },
                        CANCELLED: { label: 'İptal Edildi', class: 'danger', icon: 'times-circle' }
                      };
                      const status = statusConfig[transfer.status] || statusConfig.PENDING;
                      const transferItemsPreview = getTransferItemsList(transfer);
                      const totalQuantity = getTransferTotalQuantity(transfer);
                      const awaitingApproval = (transfer.approvalStatus || '').toUpperCase() === 'PENDING';
                      const approvalRejected = (transfer.approvalStatus || '').toUpperCase() === 'REJECTED';

                      const isSelected = selectedTransfers.includes(transfer.id);
                      const canDelete = transfer.status !== 'IN_TRANSIT' && transfer.status !== 'COMPLETED';
                      
                      return (
                        <tr key={transfer.id} className={isSelected ? 'table-active' : ''}>
                          {isAdmin && (
                            <td className="text-center align-middle">
                              <div className="form-check mb-0">
                                <input
                                  className="form-check-input"
                                  type="checkbox"
                                  checked={isSelected}
                                  onChange={() => toggleTransferSelection(transfer.id)}
                                  disabled={!canDelete}
                                  aria-label="Transfer seç"
                                />
                              </div>
                            </td>
                          )}
                          <td className="text-center align-middle">
                            <span className="badge bg-dark d-block">#{transfer.id}</span>
                            <span
                              className={`badge d-block mt-1 ${
                                (transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY'
                                  ? 'bg-info text-dark'
                                  : 'bg-secondary'
                              }`}
                              title={(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'Müşteri Sevkiyatı' : 'Depo Transferi'}
                            >
                              {(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'Müşteri' : 'Depo'}
                            </span>
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
                            <div className="text-truncate">
                              {transferItemsPreview.length === 0 ? (
                                <small className="text-muted">Ürün bilgisi yok</small>
                              ) : (
                                <>
                                  {transferItemsPreview.slice(0, 3).map((item, idx) => (
                                    <div key={`${transfer.id}-${item.product?.id || idx}`} className="d-flex justify-content-between align-items-center mb-1">
                                      <div className="me-2 overflow-hidden">
                                        <div className="fw-bold small text-truncate">{item.product?.name || '-'}</div>
                                        <small className="text-muted text-truncate">{item.product?.sku || '-'}</small>
                                      </div>
                                      <span className="badge bg-light text-dark">{item.quantity}</span>
                                    </div>
                                  ))}
                                  {transferItemsPreview.length > 3 && (
                                    <small className="text-muted">+ {transferItemsPreview.length - 3} ürün daha</small>
                                  )}
                                </>
                              )}
                              {transfer.createdBy && (
                                <small className="text-muted d-block mt-1">
                                  <i className="fas fa-user me-1"></i>
                                  {transfer.createdBy}
                                </small>
                              )}
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
                              <div
                                className={`bg-${(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'info' : 'success'} bg-opacity-10 rounded-circle p-1 me-1 me-sm-2 flex-shrink-0 d-none d-sm-flex`}
                                style={{width: '30px', height: '30px', alignItems: 'center', justifyContent: 'center'}}
                              >
                                <i
                                  className={`fas ${
                                    (transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? 'fa-user-tag text-info' : 'fa-warehouse text-success'
                                  } fa-sm`}
                                ></i>
                              </div>
                              <div className="small overflow-hidden w-100">
                                {(transfer.transferType || 'WAREHOUSE') === 'CUSTOMER_DELIVERY' ? (
                                  <>
                                    <div className="fw-bold text-truncate" title={transfer.customerFullName}>
                                      <i className="fas fa-user-tag text-info fa-xs me-1 d-inline d-sm-none"></i>
                                      {transfer.customerFullName}
                                    </div>
                                    <small className="text-muted d-block text-truncate" title={transfer.customerPhone}>
                                      <i className="fas fa-phone me-1"></i>
                                      {transfer.customerPhone || '-'}
                                    </small>
                                    <small className="text-muted d-block text-truncate" title={transfer.customerAddress}>
                                      <i className="fas fa-map-marker-alt me-1"></i>
                                      {transfer.customerAddress || '-'}
                                    </small>
                                  </>
                                ) : (
                                  <>
                                    <div className="fw-bold text-truncate" title={transfer.destinationWarehouse?.name}>
                                      <i className="fas fa-warehouse text-success fa-xs me-1 d-inline d-sm-none"></i>
                                      {transfer.destinationWarehouse?.name}
                                    </div>
                                    <small className="text-muted d-block text-truncate" title={transfer.destinationWarehouse?.location}>
                                      {transfer.destinationWarehouse?.location}
                                    </small>
                                  </>
                                )}
                              </div>
                            </div>
                          </td>
                          <td className="text-center align-middle">
                            <span className="badge bg-primary rounded-pill">{totalQuantity}</span>
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
                            {awaitingApproval && (
                              <small className="d-block text-warning mt-1">
                                <i className="fas fa-hourglass-half me-1"></i>
                                Onay Bekleniyor
                              </small>
                            )}
                            {approvalRejected && (
                              <small
                                className="d-block text-danger mt-1 text-truncate"
                                title={transfer.approvalNote || 'Onay reddedildi'}
                              >
                                <i className="fas fa-times-circle me-1"></i>
                                Onay Reddedildi
                              </small>
                            )}
                            {(transfer.approvalStatus || '').toUpperCase() === 'APPROVED' && transfer.approvalDecisionBy && (
                              <small className="d-block text-muted mt-1">
                                Onaylayan: {transfer.approvalDecisionBy}
                              </small>
                            )}
                          </td>
                          <td className="text-center align-middle" style={{padding: '6px'}}>
                            <div className="d-flex flex-column gap-1">
                              {transfer.status === 'PENDING' && (
                                awaitingApproval ? (
                                  <div className="d-flex flex-column gap-2">
                                    <span className="badge bg-warning text-dark">
                                      <i className="fas fa-hourglass-half me-1"></i>
                                      Onay Bekleniyor
                                    </span>
                                    {isAdmin && (
                                      <button
                                        className="btn btn-sm btn-outline-primary"
                                        onClick={() => setShowApprovalModal(true)}
                                      >
                                        <i className="fas fa-tasks me-1"></i>
                                        Onayları Aç
                                      </button>
                                    )}
                                  </div>
                                ) : (
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
                                      onClick={() =>
                                        openCompletionFlow(
                                          transfer,
                                          'Transfer direkt tamamlanacak ve stok kaynak depodan düşülecek. Onaylıyor musunuz?'
                                        )
                                      }
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
                                )
                              )}
                              {transfer.status === 'IN_TRANSIT' && (
                                <>
                                  <button
                                    className="btn btn-sm btn-success w-100 py-1 px-2"
                                    style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                    onClick={() =>
                                      openCompletionFlow(
                                        transfer,
                                        'Transfer tamamlanacak ve stok rezervasyonu kapatılacak. Onaylıyor musunuz?'
                                      )
                                    }
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
                              {approvalRejected && transfer.approvalNote && (
                                <button
                                  className="btn btn-sm btn-outline-danger w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => setNotesModal({
                                    show: true,
                                    notes: transfer.approvalNote,
                                    transferId: transfer.id,
                                    title: 'Onay Red Notu'
                                  })}
                                  title="Onay reddetme notunu görüntüle"
                                >
                                  <i className="fas fa-exclamation-circle me-1"></i>
                                  Onay Notu
                                </button>
                              )}
                              {transfer.completionNote && (
                                <button
                                  className="btn btn-sm btn-outline-success w-100 py-1 px-2"
                                  style={{fontSize: 'clamp(0.7rem, 2vw, 0.8rem)', whiteSpace: 'nowrap'}}
                                  onClick={() => setNotesModal({
                                    show: true,
                                    notes: transfer.completionNote,
                                    transferId: transfer.id,
                                    title: 'Tamamlama Notu'
                                  })}
                                  title="Tamamlama notunu görüntüle"
                                >
                                  <i className="fas fa-clipboard-check me-1"></i>
                                  Tamamlama
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
              <div className="d-flex justify-content-between align-items-center px-3 py-3 flex-wrap gap-2">
                <small className="text-muted">
                  Gösterilen kayıt: {transfers.length}/{totalTransfers || transfers.length}
                </small>
                <PaginationControls
                  page={transferPage}
                  totalPages={transferTotalPages}
                  onPageChange={handleTransferPageChange}
                />
              </div>
              </>
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
                      cancellationModal.reason.trim()
                        ? { cancellationReason: cancellationModal.reason.trim() }
                        : undefined
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

      {completionModal.show && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-success text-white">
                <h5 className="modal-title">
                  <i className="fas fa-clipboard-check me-2"></i>
                  Tamamlama Notu
                </h5>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={() => setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null })}
                ></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-info">
                  <i className="fas fa-info-circle me-2"></i>
                  {completionModal.message || 'Sevk tamamlanmadan önce teslimat durumunu ve varsa özel notları ekleyebilirsiniz.'}
                </div>
                {completionModal.transfer?.transferType === 'CUSTOMER_DELIVERY' && (
                  <div className="mb-3">
                    <div className="fw-bold">
                      <i className="fas fa-user-tag me-2 text-info"></i>
                      {completionModal.transfer.customerFullName}
                    </div>
                    <small className="text-muted d-block">
                      <i className="fas fa-phone me-1"></i>
                      {completionModal.transfer.customerPhone}
                    </small>
                    <small className="text-muted d-block">
                      <i className="fas fa-map-marker-alt me-1"></i>
                      {completionModal.transfer.customerAddress}
                    </small>
                  </div>
                )}
                <div className="mb-3">
                  <label htmlFor="completionNote" className="form-label">
                    <i className="fas fa-comment-alt me-1"></i>
                    Tamamlama Notu <span className="text-muted">(Opsiyonel)</span>
                  </label>
                  <textarea
                    id="completionNote"
                    className="form-control"
                    rows="4"
                    value={completionModal.note}
                    onChange={(e) => setCompletionModal({ ...completionModal, note: e.target.value })}
                    placeholder="Teslim alan kişi, adres detayları veya önemli gözlemler..."
                    maxLength="500"
                  />
                  <small className="text-muted">{completionModal.note.length}/500 karakter</small>
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null })}
                >
                  <i className="fas fa-times me-2"></i>
                  Vazgeç
                </button>
                <button
                  type="button"
                  className="btn btn-success"
                  onClick={() => {
                    handleTransferStatusChange(
                      completionModal.transferId,
                      'complete',
                      completionModal.note.trim()
                        ? { completionNote: completionModal.note.trim() }
                        : undefined
                    );
                    setCompletionModal({ show: false, transferId: null, note: '', message: '', transfer: null });
                  }}
                >
                  <i className="fas fa-check me-2"></i>
                  Kaydet ve Tamamla
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

      {/* Stock Request Approval Modal */}
      {showApprovalModal && (
        <StockRequestApprovalModal
          onClose={() => setShowApprovalModal(false)}
          onApprove={() => {
            fetchAllData();
          }}
          initialTab={approvalModalTab}
        />
      )}
      {showMyRequestsModal && (
        <MyStockRequestsModal
          onClose={() => setShowMyRequestsModal(false)}
        />
      )}
    </div>
  );
};

export default Stock;
