export default function Header({
  eyebrow = "Văn phòng · Sagasu",
  title,
  description,
  children,
}) {
  return (
    <header className="of-header">
      <div>
        <p className="of-eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="of-muted">{description}</p>
      </div>
      <div className="of-actions">{children}</div>
    </header>
  );
}
