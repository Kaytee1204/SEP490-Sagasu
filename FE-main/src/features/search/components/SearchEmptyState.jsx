import React from 'react';
import { Search } from 'lucide-react';

export default function SearchEmptyState({ query }) {
  return (
    <div style={{
      background: 'white', borderRadius: 'var(--radius-xl)', padding: '48px 24px',
      textAlign: 'center', border: '1px solid var(--border)', color: 'var(--text-muted)'
    }}>
      <Search size={32} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
      <h3 style={{ fontSize: '1.05rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '6px' }}>
        Không tìm thấy kết quả nào
      </h3>
      <p style={{ fontSize: '0.875rem', margin: 0 }}>
        {query
          ? `Không có vật phẩm nào khớp với từ khóa "${query}". Thử tìm với từ khóa khác hoặc chọn "Tất cả danh mục".`
          : 'Không có vật phẩm nào phù hợp với bộ lọc hiện tại. Thử chọn "Tất cả danh mục".'}
      </p>
    </div>
  );
}
