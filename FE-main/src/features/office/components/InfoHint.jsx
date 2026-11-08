import { CircleHelp } from "lucide-react";
import { useId } from "react";
import "./InfoHint.css";
export default function InfoHint({ children, label = "Thông tin thêm" }) {
  const id = useId();
  return <span className="of-info-hint"><button type="button" className="of-info-hint-button" aria-label={label} aria-describedby={id}><CircleHelp size={18} /></button><span id={id} role="tooltip" className="of-info-hint-text">{children}</span></span>;
}
