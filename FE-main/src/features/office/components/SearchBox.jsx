import { Search } from "lucide-react";

export default function SearchBox({
  value,
  onChange,
  placeholder = "Tìm kiếm…",
}) {
  return (
    <label className="of-search">
      <Search size={18} />
      <input
        aria-label={placeholder}
        type="search"
        maxLength={200}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}
