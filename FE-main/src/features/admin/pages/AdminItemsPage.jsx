import React from 'react';
import { CheckCircle } from 'lucide-react';
import { useAdminItems } from '../hooks/useAdminItems';
import AdminPageHeader from '../components/common/AdminPageHeader';
import ItemFilterBar from '../components/items/ItemFilterBar';
import ItemTable from '../components/items/ItemTable';
import ItemDetailModal from '../components/items/ItemDetailModal';
import ItemActionModal from '../components/items/ItemActionModal';

export default function AdminItemsPage() {
  const {
    filteredItems,
    searchQuery, setSearchQuery,
    filterType, setFilterType,
    filterLocation, setFilterLocation,
    filterStatus, setFilterStatus,
    selectedItemDetail, setSelectedItemDetail,
    actionModal, setActionModal,
    actionReason, setActionReason,
    toastMessage,
    executeAction,
    resetFilters,
    counts,
    hasActiveFilters
  } = useAdminItems();

  return (
    <div style={{ padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Toast */}
      {toastMessage && (
        <div style={{
          position: 'fixed', top: '70px', right: '28px', zIndex: 100,
          background: '#090d12', color: 'white', padding: '10px 16px',
          borderRadius: '8px', fontSize: '0.85rem', fontWeight: 500,
          display: 'flex', alignItems: 'center', gap: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.2)'
        }}>
          <CheckCircle size={16} style={{ color: '#2dd4bf' }} />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── UNIFIED WORKBENCH ── */}
      <div style={{
        background: 'white', borderRadius: '12px',
        border: '1px solid rgba(0,0,0,0.08)',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        overflow: 'hidden'
      }}>
        <div style={{
          padding: '20px 24px', borderBottom: '1px solid rgba(0,0,0,0.06)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h2 style={{ fontFamily: 'var(--font-display)', fontSize: '1.2rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                Quản lý tin đăng
              </h2>
              <span style={{ fontSize: '0.75rem', fontWeight: 600, background: 'rgba(30,107,107,0.08)', color: 'var(--teal-700)', padding: '2px 8px', borderRadius: '12px' }}>
                {filteredItems.length} tin hiển thị
              </span>
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '4px 0 0' }}>
              Kiểm duyệt nội dung, xử lý tin vi phạm và theo dõi trạng thái đồ thất lạc
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.775rem', color: 'var(--text-muted)' }}>
            <span style={{ fontWeight: 600, color: '#dc2626' }}>{counts.lost} Cần tìm</span>
            <span>·</span>
            <span style={{ fontWeight: 600, color: '#059669' }}>{counts.found} Nhặt được</span>
            <span>·</span>
            <span style={{ fontWeight: 600, color: '#6366f1' }}>{counts.resolved} Đã trao trả</span>
          </div>
        </div>

        <ItemFilterBar
          filterType={filterType} setFilterType={setFilterType}
          searchQuery={searchQuery} setSearchQuery={setSearchQuery}
          filterLocation={filterLocation} setFilterLocation={setFilterLocation}
          filterStatus={filterStatus} setFilterStatus={setFilterStatus}
          counts={counts}
          hasActiveFilters={hasActiveFilters}
          resetFilters={resetFilters}
        />

        <ItemTable
          filteredItems={filteredItems}
          setSelectedItemDetail={setSelectedItemDetail}
          setActionModal={setActionModal}
        />
      </div>

      <ItemDetailModal
        selectedItemDetail={selectedItemDetail}
        setSelectedItemDetail={setSelectedItemDetail}
        setActionModal={setActionModal}
      />

      <ItemActionModal
        actionModal={actionModal}
        setActionModal={setActionModal}
        actionReason={actionReason}
        setActionReason={setActionReason}
        executeAction={executeAction}
      />
    </div>
  );
}
