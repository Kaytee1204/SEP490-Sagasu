import { useState } from 'react';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';

export default function IntakeConfirmDialog({ receipt, busy, error, onClose, onConfirm }) {
  const [acknowledged, setAcknowledged] = useState(false);

  return (
    <ConfirmActionPopup
      open
      variant="success"
      title="Xác nhận tiếp nhận & đăng bài?"
      description={`Bài đăng "${receipt.title}" sẽ được công khai với xác nhận của văn phòng. Nội dung phiếu được khóa sau khi tiếp nhận để bảo toàn hồ sơ.`}
      confirmLabel={busy ? 'Đang tiếp nhận…' : 'Xác nhận & đăng bài'}
      cancelLabel="Kiểm tra lại"
      busy={busy}
      error={error}
      onClose={() => !busy && onClose()}
      onConfirm={() => {
        if (!acknowledged || busy) return;
        onConfirm();
      }}
    >
      <div className="space-y-3">
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-[13px] text-slate-700">
          Lưu tại kho: <strong className="text-slate-900">{receipt.storageLocationName}</strong>
        </div>
        <label className="flex cursor-pointer items-start gap-2.5 rounded-xl border border-teal-200 bg-teal-50/70 p-3 text-[13px] font-medium leading-relaxed text-teal-950">
          <input
            type="checkbox"
            checked={acknowledged}
            disabled={busy}
            onChange={(event) => setAcknowledged(event.target.checked)}
            className="mt-0.5 h-4 w-4 shrink-0 accent-teal-700"
          />
          <span>Tôi đã nhận tài sản thực tế, kiểm tra thông tin và xác nhận đúng nơi lưu trữ.</span>
        </label>
      </div>
    </ConfirmActionPopup>
  );
}
