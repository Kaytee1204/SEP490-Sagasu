import { useState } from "react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";
import Dialog from "./Dialog";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

export default function ModerationDialog({ post, onClose, onDone }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");
  const hiding = post.moderationStatus !== "HIDDEN";
  function handleFormSubmit(e) {
    e.preventDefault();
    if (busy || !reason.trim()) return;
    setError("");
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await officeApi.moderate(post.id, {
        status: hiding ? "HIDDEN" : "VISIBLE",
        reason: reason.trim(),
        expectedUpdatedAt: post.updatedAt,
      });
      setConfirmOpen(false);
      onDone();
    } catch (err) {
      setError(err.message);
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={hiding ? "Ẩn bài đăng" : "Gỡ ẩn bài đăng"}
      busy={busy}
      onClose={onClose}
    >
      <form className="of-form" onSubmit={handleFormSubmit}>
        <p>
          {hiding
            ? "Bài sẽ không xuất hiện trong danh sách và kết quả tìm kiếm công khai."
            : "Bài sẽ hiển thị trở lại với trạng thái xử lý đồ hiện tại."}
        </p>
        <Notice error>{error}</Notice>
        <label>
          Lý do *
          <textarea
            autoFocus
            required
            maxLength={2000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={busy}
            placeholder="Ghi rõ căn cứ cho quyết định này…"
          />
        </label>
        <div className="of-actions">
          <button
            type="button"
            className="of-button"
            onClick={onClose}
            disabled={busy}
          >
            Quay lại
          </button>
          <button
            className={`of-button ${hiding ? "of-danger" : "of-primary"}`}
            disabled={busy || !reason.trim()}
          >
            {busy ? "Đang lưu…" : hiding ? "Xác nhận ẩn" : "Xác nhận gỡ ẩn"}
          </button>
        </div>
      </form>
      <ConfirmActionPopup
        open={confirmOpen}
        variant={hiding ? "danger" : "success"}
        title={hiding ? "Bạn có chắc chắn ẩn bài đăng?" : "Xác nhận gỡ ẩn bài đăng?"}
        description={hiding ? `Bài đăng "${post.title}" sẽ bị ẩn khỏi danh sách tìm kiếm công khai.` : `Bài đăng "${post.title}" sẽ hiển thị trở lại cho người dùng.`}
        confirmLabel={hiding ? "Đồng ý ẩn bài" : "Đồng ý gỡ ẩn"}
        cancelLabel="Quay lại"
        busy={busy}
        onConfirm={executeSubmit}
        onClose={() => setConfirmOpen(false)}
      />
    </Dialog>
  );
}
