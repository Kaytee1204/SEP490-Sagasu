import { PackageOpen } from "lucide-react";

export default function Empty({
  text = "Không có kết quả phù hợp",
  description = "Thử thay đổi từ khóa hoặc bộ lọc.",
  children,
}) {
  return (
    <div className="of-empty">
      <PackageOpen size={38} strokeWidth={1.4} />
      <h2>{text}</h2>
      <p className="of-muted">{description}</p>
      {children}
    </div>
  );
}
