import { useEffect, useRef, useId, useState } from "react";
import { X } from "lucide-react";

export default function Dialog({ title, onClose, busy, children, resetKey }) {
  const ref = useRef(null);
  const titleId = useId();
  const [edited, setEdited] = useState(null);
  const [pending, setPending] = useState(null);
  const dirty = edited?.key === resetKey && edited?.dirty;
  const discard = pending?.key === resetKey && Boolean(pending?.action);
  function guard(action = onClose) {
    if (busy) return;
    if (dirty) setPending({ key:resetKey, action }); else action();
  }
  const content = typeof children === "function" ? children({ guard }) : children;
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
      onInputCapture={() => setEdited({ key:resetKey, dirty:true })}
      onCancel={(e) => {
        e.preventDefault();
        guard();
      }}
    >
      <div className="of-dialog-head">
        <h2 id={titleId}>{title}</h2>
        <button
          type="button"
          className="of-icon"
          aria-label="Đóng"
          disabled={busy}
          onClick={() => guard()}
        >
          <X size={22} />
        </button>
      </div>
      {discard && <section role="alert">
        <p>Bỏ những thay đổi chưa lưu?</p>
        <div className="of-actions">
          <button type="button" className="of-button of-primary" onClick={() => setPending(null)}>Tiếp tục nhập</button>
          <button type="button" className="of-button of-danger" onClick={() => { setEdited(null); setPending(null); pending.action(); }}>Bỏ thay đổi và tiếp tục</button>
        </div>
      </section>}
      <div hidden={discard}>{content}</div>
    </dialog>
  );
}
