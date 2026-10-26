import React from 'react';
import { CheckCircle2 } from 'lucide-react';
import { useAdminReports } from '../hooks/useAdminReports';
import AdminPageHeader from '../components/common/AdminPageHeader';
import ReportListTable from '../components/reports/ReportListTable';
import ReportDetailView from '../components/reports/ReportDetailView';

export default function AdminReportsPage() {
  const {
    reports,
    filteredReports,
    selectedReport,
    reportFilter,
    setReportFilter,
    resolutionReason,
    setResolutionReason,
    counts,
    toastMessage,
    openReport,
    closeReport,
    handleAction,
    handleReopen,
  } = useAdminReports();

  return (
    <div style={{ padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Toast Alert */}
      {toastMessage && (
        <div style={{
          position: 'fixed', top: '70px', right: '28px', zIndex: 100,
          background: '#090d12', color: 'white', padding: '10px 16px',
          borderRadius: '8px', fontSize: '0.85rem', fontWeight: 500,
          display: 'flex', alignItems: 'center', gap: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.2)'
        }}>
          <CheckCircle2 size={16} style={{ color: '#2dd4bf' }} />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── MAIN CONTAINER ── */}
      <div style={{
        background: 'white', borderRadius: '12px',
        border: '1px solid rgba(0,0,0,0.08)',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        overflow: 'hidden'
      }}>
        <AdminPageHeader 
          title="Báo cáo vi phạm & Khiếu nại"
          description="Kiểm tra tố cáo mạo nhận tài sản, tống tiền hoặc tranh chấp quyền sở hữu đồ thất lạc"
          counts={counts}
        />

        {selectedReport ? (
          <ReportDetailView 
            report={selectedReport}
            onClose={closeReport}
            resolutionReason={resolutionReason}
            setResolutionReason={setResolutionReason}
            onAction={handleAction}
            onReopen={handleReopen}
          />
        ) : (
          <ReportListTable 
            reports={reports}
            filteredReports={filteredReports}
            currentFilter={reportFilter}
            onFilterChange={setReportFilter}
            onSelectReport={openReport}
          />
        )}
      </div>
    </div>
  );
}
