// src/features/account/pages/AccountDashboardPage.jsx
import React, { useState } from 'react';
import { Plus, Upload, CheckCircle2, AlertCircle, Users, UserCheck, Ban, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';
import { useAccountDashboard } from '../hooks/useAccountDashboard';
import AccountFilterBar from '../components/AccountFilterBar';
import AccountTable from '../components/AccountTable';
import AccountCrudModal from '../components/AccountCrudModal';
import AccountImportModal from '../components/AccountImportModal';

export default function AccountDashboardPage() {
  const { user } = useAuth();
  const [confirmTarget, setConfirmTarget] = useState(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [lockReason, setLockReason] = useState('');
  const [lockReasonError, setLockReasonError] = useState('');
  const isLocking = confirmTarget?.type === 'lock' && confirmTarget?.account?.status !== 'SUSPENDED';
  const {
    accounts,
    loading,
    page,
    setPage,
    size,
    setSize,
    totalElements,
    totalPages,
    counts,
    keyword,
    setKeyword,
    roleFilter,
    setRoleFilter,
    statusFilter,
    setStatusFilter,
    includeDeleted,
    setIncludeDeleted,
    crudModal,
    setCrudModal,
    formData,
    setFormData,
    modalError,
    setModalError,
    submitting,
    openCrudModal,
    submitCrud,
    handleToggleLock,
    handleSoftDelete,
    importModalOpen,
    setImportModalOpen,
    importResult,
    setImportResult,
    importing,
    handleExcelImport,
    toast,
  } = useAccountDashboard();

  return (
    <div className="admin-page-container" style={{ padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Toast Notification */}
      {toast && (
        <div
          style={{
            position: 'fixed',
            top: '70px',
            right: '28px',
            zIndex: 1100,
            background: toast.type === 'error' ? '#991b1b' : '#090d12',
            color: 'white',
            padding: '12px 18px',
            borderRadius: '8px',
            fontSize: '0.85rem',
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.2)',
          }}
        >
          {toast.type === 'error' ? (
            <AlertCircle size={16} style={{ color: '#fca5a5' }} />
          ) : (
            <CheckCircle2 size={16} style={{ color: '#2dd4bf' }} />
          )}
          <span>{toast.message}</span>
        </div>
      )}

      {/* ── SUMMARY STATS CARDS ── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
        <div style={{ background: 'white', padding: '16px 20px', borderRadius: '12px', border: '1px solid rgba(0,0,0,0.08)', display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{ width: 42, height: 42, borderRadius: '10px', background: '#f0fdfa', color: '#0d9488', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Users size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Tổng kết quả lọc</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: 'var(--text-primary)' }}>{totalElements}</div>
          </div>
        </div>

        <div style={{ background: 'white', padding: '16px 20px', borderRadius: '12px', border: '1px solid rgba(0,0,0,0.08)', display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{ width: 42, height: 42, borderRadius: '10px', background: '#ecfdf5', color: '#059669', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <UserCheck size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Đang hoạt động</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#059669' }}>{counts.active}</div>
          </div>
        </div>

        <div style={{ background: 'white', padding: '16px 20px', borderRadius: '12px', border: '1px solid rgba(0,0,0,0.08)', display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{ width: 42, height: 42, borderRadius: '10px', background: '#fff7ed', color: '#ea580c', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Ban size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Đang tạm khóa</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#ea580c' }}>{counts.suspended}</div>
          </div>
        </div>

        <div style={{ background: 'white', padding: '16px 20px', borderRadius: '12px', border: '1px solid rgba(0,0,0,0.08)', display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{ width: 42, height: 42, borderRadius: '10px', background: '#fef2f2', color: '#dc2626', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Trash2 size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>Đã xóa</div>
            <div style={{ fontSize: '1.35rem', fontWeight: 800, color: '#dc2626' }}>{counts.deleted}</div>
          </div>
        </div>
      </div>

      {/* ── MAIN ACCOUNT MANAGEMENT WORKBENCH ── */}
      <div
        style={{
          background: 'white',
          borderRadius: '12px',
          border: '1px solid rgba(0,0,0,0.08)',
          boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          overflow: 'hidden',
        }}
      >
        {/* Header bar */}
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid rgba(0,0,0,0.06)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '16px',
          }}
        >
          <div>
            <h2
              style={{
                fontFamily: 'var(--font-display)',
                fontSize: '1.2rem',
                fontWeight: 700,
                margin: 0,
                color: 'var(--text-primary)',
              }}
            >
              Quản lý Tài khoản
            </h2>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
            <button
              type="button"
              onClick={() => {
                setImportResult(null);
                setImportModalOpen(true);
              }}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '9px 16px',
                background: 'white',
                color: 'var(--teal-700)',
                borderRadius: '8px',
                border: '1px solid var(--teal-600)',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              <Upload size={15} /> Import Excel
            </button>

            <button
              type="button"
              onClick={() => openCrudModal('create')}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '9px 16px',
                background: 'var(--teal-600)',
                color: 'white',
                borderRadius: '8px',
                border: 'none',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
                boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              }}
            >
              <Plus size={15} /> Thêm tài khoản
            </button>
          </div>
        </div>

        {/* Filter Bar */}
        <AccountFilterBar
          keyword={keyword}
          setKeyword={setKeyword}
          roleFilter={roleFilter}
          setRoleFilter={setRoleFilter}
          statusFilter={statusFilter}
          setStatusFilter={setStatusFilter}
          includeDeleted={includeDeleted}
          setIncludeDeleted={setIncludeDeleted}
          size={size}
          setSize={setSize}
        />

        {/* Table */}
        <AccountTable
          accounts={accounts}
          loading={loading}
          openCrudModal={openCrudModal}
          onToggleLock={(acc) => setConfirmTarget({ type: 'lock', account: acc })}
          onSoftDelete={(acc) => setConfirmTarget({ type: 'delete', account: acc })}
        />

        {/* Pagination Footer (Backend Paginated) */}
        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid rgba(0,0,0,0.06)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '12px',
            background: '#fafbfc',
          }}
        >
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            Trang <b>{page}</b> / <b>{totalPages}</b> (Tổng <b>{totalElements}</b> tài khoản)
          </span>

          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={(newPage) => setPage(newPage)}
          />
        </div>
      </div>

      {/* Create / Edit Modal */}
      <AccountCrudModal
        crudModal={crudModal}
        setCrudModal={setCrudModal}
        formData={formData}
        setFormData={setFormData}
        modalError={modalError}
        setModalError={setModalError}
        submitCrud={submitCrud}
        submitting={submitting}
      />

      {/* Import Excel Modal */}
      <AccountImportModal
        isOpen={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onImport={handleExcelImport}
        importing={importing}
        importResult={importResult}
      />

      {/* Confirm Lock / Soft Delete Popup */}
      <ConfirmActionPopup
        open={Boolean(confirmTarget)}
        variant={
          confirmTarget?.type === 'delete'
            ? 'danger'
            : confirmTarget?.account?.status === 'SUSPENDED'
              ? 'success'
              : 'warning'
        }
        title={
          confirmTarget?.type === 'delete'
            ? 'Xóa tài khoản?'
            : confirmTarget?.account?.status === 'SUSPENDED'
              ? 'Mở khóa tài khoản?'
              : 'Tạm khóa tài khoản?'
        }
        description={
          confirmTarget
            ? confirmTarget.type === 'delete'
              ? `Bạn có chắc chắn muốn xóa tài khoản ${confirmTarget.account.email}?`
              : confirmTarget.account.status === 'SUSPENDED'
                ? `Mở khóa để tài khoản ${confirmTarget.account.email} có thể đăng nhập và sử dụng hệ thống trở lại.`
                : `Tài khoản ${confirmTarget.account.email} sẽ bị tạm khóa truy cập hệ thống.`
            : ''
        }
        confirmLabel={
          confirmTarget?.type === 'delete'
            ? 'Xóa'
            : confirmTarget?.account?.status === 'SUSPENDED'
              ? 'Đồng ý mở khóa'
              : 'Đồng ý khóa'
        }
        cancelLabel="Hủy bỏ"
        busy={actionBusy}
        onConfirm={async () => {
          if (!confirmTarget || actionBusy) return;
          if (isLocking && !lockReason.trim()) {
            setLockReasonError('Vui lòng nhập lý do khóa tài khoản');
            return;
          }
          setActionBusy(true);
          try {
            if (confirmTarget.type === 'delete') {
              await handleSoftDelete(confirmTarget.account);
            } else {
              await handleToggleLock(confirmTarget.account, lockReason.trim());
            }
          } finally {
            setActionBusy(false);
            setConfirmTarget(null);
            setLockReason('');
            setLockReasonError('');
          }
        }}
        onClose={() => {
          if (actionBusy) return;
          setConfirmTarget(null);
          setLockReason('');
          setLockReasonError('');
        }}
      >
        {isLocking && (
          <div>
            <label htmlFor="lock-reason" style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px' }}>
              Lý do khóa <span style={{ color: '#dc2626' }}>*</span>
            </label>
            <textarea
              id="lock-reason"
              value={lockReason}
              maxLength={500}
              rows={3}
              onChange={(e) => {
                setLockReason(e.target.value);
                if (lockReasonError) setLockReasonError('');
              }}
              placeholder="VD: Vi phạm nội quy"
              style={{ width: '100%', padding: '8px 10px', borderRadius: '6px', border: '1px solid var(--border)', fontSize: '0.85rem', resize: 'vertical' }}
            />
            {lockReasonError && (
              <p role="alert" style={{ color: '#dc2626', fontSize: '0.8rem', marginTop: '4px' }}>{lockReasonError}</p>
            )}
          </div>
        )}
      </ConfirmActionPopup>
    </div>
  );
}
