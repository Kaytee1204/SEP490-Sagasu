export default function Notice({ children, error = false }) {
  return children ? (
    <div
      className={`of-notice ${error ? "of-error" : ""}`}
      role={error ? "alert" : "status"}
    >
      {children}
    </div>
  ) : null;
}
