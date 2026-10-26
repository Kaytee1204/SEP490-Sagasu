import React from 'react';
import { Link } from 'react-router-dom';
import { Package, Plus } from 'lucide-react';

export default function MyPostsEmptyState() {
  return (
    <div style={{
      textAlign: 'center', padding: '80px 24px',
      display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px',
    }}>
      <div style={{
        width: 80, height: 80, borderRadius: '50%',
        background: 'rgba(30,107,107,0.06)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--teal-300)',
      }}>
        <Package size={36} strokeWidth={1.4} />
      </div>
      <div>
        <div style={{ fontWeight: 700, fontSize: '1.0625rem', color: 'var(--text-primary)', marginBottom: '6px' }}>
          Không tìm thấy bài đăng nào
        </div>
        <div style={{ fontSize: '0.875rem', color: 'var(--text-muted)', maxWidth: '32ch', margin: '0 auto' }}>
          Thử đổi bộ lọc hoặc đăng tin mới để bắt đầu.
        </div>
      </div>
      <Link to="/dang-tin" className="btn btn-accent" style={{ marginTop: '8px' }}>
        <Plus size={16} /> Đăng tin mới
      </Link>
    </div>
  );
}
