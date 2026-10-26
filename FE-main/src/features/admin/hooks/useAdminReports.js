import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { INITIAL_REPORTS } from '../pages/mockData';

export function useAdminReports() {
  const [reports, setReports] = useState(INITIAL_REPORTS);
  const [reportFilter, setReportFilter] = useState('pending');
  const [selectedReportId, setSelectedReportId] = useState(null);
  const [resolutionReason, setResolutionReason] = useState('');
  const [toastMessage, setToastMessage] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();

  useEffect(() => {
    const id = searchParams.get('id');
    if (id && reports.some(r => r.id === id)) {
      setSelectedReportId(id);
      const target = reports.find(r => r.id === id);
      if (target?.adminNote) {
        setResolutionReason(target.adminNote);
      }
    }
  }, [searchParams, reports]);

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const selectedReport = useMemo(() => {
    return reports.find(r => r.id === selectedReportId) || null;
  }, [reports, selectedReportId]);

  const openReport = (report) => {
    setSelectedReportId(report.id);
    setResolutionReason(report.adminNote || '');
  };

  const closeReport = () => {
    setSelectedReportId(null);
    setResolutionReason('');
    if (searchParams.get('id')) {
      searchParams.delete('id');
      setSearchParams(searchParams);
    }
  };

  const handleAction = (actionType) => {
    if (!resolutionReason.trim()) {
      showToast('Vui lòng nhập lý do / căn cứ xử lý vào ô bên dưới');
      return;
    }

    if (actionType === 'DISMISS_REPORT') {
      setReports(prev => prev.map(r => r.id === selectedReportId ? { ...r, status: 'dismissed', adminNote: resolutionReason } : r));
      showToast(`Đã bác bỏ khiếu nại ${selectedReportId}`);
    } else if (actionType === 'RESOLVE_REPORT') {
      setReports(prev => prev.map(r => r.id === selectedReportId ? { ...r, status: 'resolved', adminNote: resolutionReason } : r));
      showToast(`Đã xử lý hồ sơ vi phạm ${selectedReportId}`);
    } else if (actionType === 'BAN_AND_RESOLVE') {
      setReports(prev => prev.map(r => r.id === selectedReportId ? { ...r, status: 'resolved', adminNote: resolutionReason } : r));
      showToast(`Đã khóa tài khoản vi phạm và giải quyết ca ${selectedReportId}`);
    }
  };

  const handleReopen = (id) => {
    setReports(prev => prev.map(r => r.id === id ? { ...r, status: 'pending' } : r));
    showToast(`Đã mở lại hồ sơ ${id} để xử lý lại`);
  };

  const filteredReports = useMemo(() => {
    return reports.filter(r => {
      if (reportFilter === 'all') return true;
      if (reportFilter === 'pending') return r.status === 'pending';
      if (reportFilter === 'resolved') return r.status === 'resolved';
      if (reportFilter === 'dismissed') return r.status === 'dismissed';
      return r.reportType === reportFilter;
    });
  }, [reports, reportFilter]);

  const pendingCount = reports.filter(r => r.status === 'pending').length;
  const resolvedCount = reports.filter(r => r.status === 'resolved').length;
  const dismissedCount = reports.filter(r => r.status === 'dismissed').length;

  return {
    reports,
    filteredReports,
    selectedReport,
    reportFilter,
    setReportFilter,
    resolutionReason,
    setResolutionReason,
    counts: {
      pending: pendingCount,
      resolved: resolvedCount,
      dismissed: dismissedCount,
    },
    toastMessage,
    openReport,
    closeReport,
    handleAction,
    handleReopen,
  };
}
