import { useEffect, useRef, useId, useState } from "react";
import { X } from "lucide-react";

export default function Dialog({ title, onClose, busy, children }) {
  const ref = useRef(null);
  const titleId = useId();
  const [dirty, setDirty] = useState(false);
  const [discard, setDiscard] = useState(false);
  function requestClose() {
    if (busy) return;
    if (dirty) setDiscard(true); else onClose();
  }
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
      onInputCapture={() => setDirty(true)}
      onCancel={(e) => {
        e.preventDefault();
        requestClose();
      }}
    >
      <div className="of-dialog-head">
        <h2 id={titleId}>{title}</h2>
        <button
          type="button"
          className="of-icon"
          aria-label="Đóng"
          disabled={busy}
          onClick={requestClose}
        >
          <X size={22} />
        </button>
      </div>
      {discard && <section role="alert">
        <p>Bỏ nội dung đang nhập và đóng?</p>
        <div className="of-actions">
          <button type="button" className="of-button of-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="of-button of-danger" onClick={onClose}>Bỏ nội dung và đóng</button>
        </div>
      </section>}
      <div hidden={discard}>{children}</div>
    </dialog>
  );
}
