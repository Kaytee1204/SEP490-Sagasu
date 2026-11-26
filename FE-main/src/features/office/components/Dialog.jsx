import { useEffect, useRef, useId } from "react";
import { X } from "lucide-react";

export default function Dialog({ title, onClose, busy, children }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="of-dialog"
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="of-dialog-head">
        <h2 id={titleId}>{title}</h2>
        <button
          type="button"
          className="of-icon"
          aria-label="Đóng"
          disabled={busy}
          onClick={onClose}
        >
          <X size={22} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
