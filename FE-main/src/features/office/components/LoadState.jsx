import { RefreshCw } from "lucide-react";
import Notice from "./Notice";

export default function LoadState({ state, children }) {
  if (state.loading)
    return (
      <div className="of-loading" role="status">
        <RefreshCw className="of-spin" size={22} />
        Đang tải dữ liệu…
      </div>
    );
  if (state.error)
    return (
      <Notice error>
        {state.error}{" "}
        <button className="of-button" onClick={state.reload}>
          Thử lại
        </button>
      </Notice>
    );
  return children;
}
