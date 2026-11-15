import { useEffect, useRef } from "react";

export default function Notice({ children, error = false }) {
  const ref = useRef(null);
  useEffect(() => {
    if (error && children) ref.current?.focus({ preventScroll: false });
  }, [error, children]);
  return children ? (
    <div
      ref={ref}
      tabIndex={error ? -1 : undefined}
      className={`of-notice ${error ? "of-error" : ""}`}
      role={error ? "alert" : "status"}
    >
      {children}
    </div>
  ) : null;
}
