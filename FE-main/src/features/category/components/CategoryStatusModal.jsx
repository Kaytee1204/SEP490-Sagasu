import { useState } from 'react';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';

export default function CategoryStatusModal({ category, busy, onClose, onConfirm }) {
  const [error, setError] = useState('');
  const label = category.active ? 'Ngừng hoạt động' : 'Kích hoạt';
  const confirm = async () => {
    setError('');
    try {
      if (await onConfirm(category)) onClose();
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <ConfirmActionPopup
      open
      variant={category.active ? 'warning' : 'success'}
      title={`${label} danh mục?`}
      description={
        category.active
          ? `Danh mục "${category.name}" sẽ không còn được chọn cho bài đăng mới. Các bài đăng cũ vẫn được giữ nguyên.`
          : `Danh mục "${category.name}" sẽ có thể được chọn lại cho bài đăng mới.`
      }
      confirmLabel={busy ? 'Đang lưu…' : label}
      cancelLabel="Hủy bỏ"
      busy={busy}
      error={error}
      onConfirm={confirm}
      onClose={() => !busy && onClose()}
    />
  );
}
