import { labels } from "../officeUtils";

export default function Badge({ value }) {
  return (
    <span className={`of-badge of-${value}`}>{labels[value] || value}</span>
  );
}
