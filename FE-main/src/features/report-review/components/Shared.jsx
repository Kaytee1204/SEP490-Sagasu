import { useEffect, useRef, useId } from "react";
import { X } from "lucide-react";
import { labels } from "../format";
export function Badge({ value }) {
  return (
    <span className={`rr-badge rr-${value}`}>
      {labels[value] || value || "Chưa ghi nhận"}
    </span>
  );
}
export function ErrorBox({ children }) {
  return children ? (
    <p role="alert" className="rr-error">
      {children}
    </p>
  ) : null;
}
export function Empty({ children }) {
  return <div className="rr-empty">{children}</div>;
}
export function Gallery({ images = [] }) {
  return images.length ? (
    <div className="rr-gallery">
      {images.map((image, i) => (
        <a
          key={image.evidence_id || i}
          href={image.url}
          target="_blank"
          rel="noreferrer"
          title="Mở ảnh gốc"
        >
          <img
            src={image.url}
            alt={image.original_name || "Ảnh bằng chứng"}
            loading="lazy"
          />
        </a>
      ))}
    </div>
  ) : null;
}
export function Dialog({ title, children, onClose, busy = false }) {
  const ref = useRef(null),
    id = useId();
  useEffect(() => {
    const el = ref.current,
      previous = document.activeElement,
      overflow = document.body.style.overflow;
    el.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      el.close();
      document.body.style.overflow = overflow;
      if (previous?.isConnected) previous.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="rr-dialog rr"
      aria-labelledby={id}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <header className="rr-row">
        <h2 id={id}>{title}</h2>
        <button
          type="button"
          disabled={busy}
          onClick={onClose}
          aria-label="Đóng"
        >
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}
