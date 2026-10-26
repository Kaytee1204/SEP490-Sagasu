// src/features/account/components/AccountImportModal.jsx
import React, { useState } from 'react';
import { Upload, Download, CheckCircle2, AlertTriangle, X } from 'lucide-react';
import { accountApi } from '../api/accountApi';

export default function AccountImportModal({
  isOpen,
  onClose,
  onImport,
  importing,
  importResult,
}) {
  const [selectedFile, setSelectedFile] = useState(null);

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (selectedFile) {
      onImport(selectedFile);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1050,
        background: 'rgba(0,0,0,0.45)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
    >
      <div
        style={{
          background: 'white',
          borderRadius: '12px',
          width: '100%',
          maxWidth: '560px',
          padding: '24px',
          boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
          border: '1px solid rgba(0,0,0,0.08)',
          maxHeight: '90vh',
          overflowY: 'auto',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <h3
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.15rem',
              fontWeight: 700,
              margin: 0,
              color: 'var(--text-primary)',
            }}
          >
            Import tài khoản từ file Excel (.xlsx / .xls)
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }}
          >
            <X size={18} />
          </button>
        </div>

        <p style={{ fontSize: '0.825rem', color: 'var(--text-secondary)', marginBottom: '14px', lineHeight: 1.5 }}>
          Hỗ trợ file Excel chứa <b>Cột A = email (Gmail)</b>, <b>Cột B = password (Mật khẩu)</b>, tùy chọn{' '}
          <b>Cột C = full_name (Tên)</b>, <b>Cột D = role</b>.
        </p>

        {/* Download template */}
        <div
          style={{
            padding: '10px 14px',
            borderRadius: '8px',
            background: '#f0fdfa',
            border: '1px solid #ccfbf1',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '16px',
          }}
        >
          <span style={{ fontSize: '0.8rem', color: '#0f766e', fontWeight: 500 }}>
            Chưa có file mẫu? Tải file Excel mẫu có sẵn cột email & password:
          </span>
          <a
            href={accountApi.getTemplateDownloadUrl()}
            download="accounts_import_template.xlsx"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '6px 12px',
              borderRadius: '6px',
              background: 'var(--teal-600)',
              color: 'white',
              textDecoration: 'none',
              fontSize: '0.78rem',
              fontWeight: 600,
            }}
          >
            <Download size={14} /> Tải file mẫu
          </a>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <label
            style={{
              border: '2px dashed rgba(0,0,0,0.15)',
              borderRadius: '10px',
              padding: '24px',
              textAlign: 'center',
              cursor: 'pointer',
              background: '#f8fafc',
            }}
          >
            <Upload size={26} style={{ margin: '0 auto 8px', color: 'var(--teal-600)' }} />
            <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
              {selectedFile ? selectedFile.name : 'Bấm để chọn file Excel (.xlsx hoặc .xls)'}
            </div>
            <input
              type="file"
              accept=".xlsx,.xls"
              onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
              style={{ display: 'none' }}
            />
          </label>

          {/* Import result summary */}
          {importResult && (
            <div
              style={{
                padding: '14px',
                borderRadius: '8px',
                background: '#f8fafc',
                border: '1px solid rgba(0,0,0,0.08)',
                fontSize: '0.82rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 700, marginBottom: '8px' }}>
                <CheckCircle2 size={16} style={{ color: '#059669' }} />
                <span>
                  Kết quả: Thành công {importResult.successCount}/{importResult.totalRows} dòng (Lỗi:{' '}
                  {importResult.failedCount})
                </span>
              </div>

              {importResult.errors && importResult.errors.length > 0 && (
                <div style={{ marginTop: '8px', color: '#dc2626' }}>
                  <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '4px' }}>
                    <AlertTriangle size={14} /> Chi tiết dòng lỗi:
                  </div>
                  <ul style={{ margin: 0, paddingLeft: '18px' }}>
                    {importResult.errors.map((err, i) => (
                      <li key={i}>{err}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 14px',
                borderRadius: '6px',
                background: '#f1f5f9',
                border: '1px solid rgba(0,0,0,0.06)',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              Đóng
            </button>
            <button
              type="submit"
              disabled={!selectedFile || importing}
              style={{
                padding: '8px 16px',
                borderRadius: '6px',
                background: !selectedFile ? '#94a3b8' : 'var(--teal-600)',
                color: 'white',
                border: 'none',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: selectedFile ? 'pointer' : 'not-allowed',
              }}
            >
              {importing ? 'Đang Import...' : 'Bắt đầu Import vào DB'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
