import { useState, useMemo } from 'react';
import { INITIAL_ITEMS } from '../pages/mockData';

export function useAdminItems() {
  const [items, setItems] = useState(INITIAL_ITEMS);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState('all');
  const [filterLocation, setFilterLocation] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');
  const [selectedItemDetail, setSelectedItemDetail] = useState(null);

  const [actionModal, setActionModal] = useState({ isOpen: false, type: '', payload: null, title: '', placeholder: '' });
  const [actionReason, setActionReason] = useState('');
  const [toastMessage, setToastMessage] = useState(null);

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const executeAction = () => {
    if (!actionReason.trim()) {
      showToast('Vui lòng nhập lý do thực hiện');
      return;
    }

    const { type, payload } = actionModal;

    if (type === 'HIDE_ITEM') {
      setItems(prev => prev.map(item => item.id === payload ? { ...item, status: 'hidden' } : item));
      showToast(`Đã gỡ bài đăng ${payload}`);
      if (selectedItemDetail?.id === payload) {
        setSelectedItemDetail(prev => ({ ...prev, status: 'hidden' }));
      }
    } else if (type === 'RESTORE_ITEM') {
      setItems(prev => prev.map(item => item.id === payload ? { ...item, status: 'active' } : item));
      showToast(`Đã khôi phục bài đăng ${payload}`);
      if (selectedItemDetail?.id === payload) {
        setSelectedItemDetail(prev => ({ ...prev, status: 'active' }));
      }
    }

    setActionModal({ isOpen: false, type: '', payload: null, title: '', placeholder: '' });
    setActionReason('');
  };

  const filteredItems = useMemo(() => {
    return items.filter(item => {
      const query = searchQuery.toLowerCase().trim();
      const matchesSearch = !query ||
        item.title.toLowerCase().includes(query) ||
        item.id.toLowerCase().includes(query) ||
        item.location.toLowerCase().includes(query) ||
        (item.reporter && item.reporter.toLowerCase().includes(query)) ||
        (item.reporterPhone && item.reporterPhone.includes(query));

      const matchesType = filterType === 'all' || item.type === filterType;
      const matchesLocation = filterLocation === 'all' || item.location.toLowerCase().includes(filterLocation.toLowerCase());
      const matchesStatus = filterStatus === 'all' || item.status === filterStatus;

      return matchesSearch && matchesType && matchesLocation && matchesStatus;
    });
  }, [items, searchQuery, filterType, filterLocation, filterStatus]);

  const resetFilters = () => {
    setSearchQuery('');
    setFilterType('all');
    setFilterLocation('all');
    setFilterStatus('all');
  };

  const lostCount = items.filter(i => i.type === 'lost').length;
  const foundCount = items.filter(i => i.type === 'found').length;
  const hiddenCount = items.filter(i => i.status === 'hidden').length;
  const resolvedCount = items.filter(i => i.status === 'resolved').length;

  return {
    items,
    filteredItems,
    searchQuery,
    setSearchQuery,
    filterType,
    setFilterType,
    filterLocation,
    setFilterLocation,
    filterStatus,
    setFilterStatus,
    selectedItemDetail,
    setSelectedItemDetail,
    actionModal,
    setActionModal,
    actionReason,
    setActionReason,
    toastMessage,
    executeAction,
    resetFilters,
    counts: {
      total: items.length,
      lost: lostCount,
      found: foundCount,
      hidden: hiddenCount,
      resolved: resolvedCount,
    },
    hasActiveFilters: searchQuery || filterType !== 'all' || filterLocation !== 'all' || filterStatus !== 'all'
  };
}
