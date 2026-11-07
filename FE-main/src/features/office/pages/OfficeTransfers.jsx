import { useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Package } from "lucide-react";
import { workflowApi } from "../api/workflowApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import TransferDialog from "../components/TransferDialog";
import "../Office.css";

const statuses = [["PENDING", "Chờ tiếp nhận"], ["RECEIVED", "Đã nhận đồ"], ["REJECTED", "Đã từ chối"], ["CANCELLED", "Đã rút yêu cầu"], ["ALL", "Tất cả"]];
export default function OfficeTransfers({ mine = false }) {
  const [filters, setFilters] = useState({ page: 1, status: mine ? "ALL" : "PENDING", q: "", mine });
  const state = useLoad(() => workflowApi.transfers(filters), JSON.stringify(filters));
  const [selected, setSelected] = useState(null);
  const [message, setMessage] = useState("");
  const change = (key, value) => setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return <div className="office-page">
    {mine && <Link className="of-back" to="/">← Trang chủ</Link>}
    <Header title={mine ? "Yêu cầu giao đồ của tôi" : "Tiếp nhận chuyển giao"} description={mine ? "Theo dõi việc giao đồ cho văn phòng. Bạn vẫn giữ đồ cho đến khi nhân viên xác nhận tiếp nhận." : "Kiểm tra đồ thực tế, lập phiếu và chuyển các hồ sơ liên quan về văn phòng."} />
    <Notice>{message}</Notice>
    <section className="of-panel">
      <div className="of-toolbar"><SearchBox value={filters.q} onChange={(q) => change("q", q)} placeholder="Tìm theo tên đồ vật…" /><Select label="Trạng thái tiếp nhận" value={filters.status} onChange={(s) => change("status", s)} options={statuses} /></div>
      <LoadState state={state}>{state.data && <>
        {!state.data.content.length ? <Empty text="Chưa có yêu cầu phù hợp" description={mine ? "Mở bài đăng đồ nhặt được và chọn giao cho văn phòng để gửi yêu cầu." : "Yêu cầu mới từ người giữ đồ sẽ xuất hiện tại đây."} /> : <div className="of-transfer-grid">{state.data.content.map((t) => <article className="of-transfer-card" key={t.id}>
          {t.images[0] ? <img className="of-transfer-image" src={t.images[0]} alt={t.title} /> : <div className="of-transfer-image of-transfer-placeholder"><Package size={28} /></div>}
          <div className="of-transfer-body"><span className={`of-transfer-status ${t.status === "RECEIVED" ? "is-complete" : ""}`}>{statuses.find(([s]) => s === t.status)?.[1]}</span><h2>{t.title}</h2><p className="of-muted">{t.finder} · {date(t.requestedAt)}</p><p>{t.activeClaims} hồ sơ đang xử lý</p><button className="of-button" onClick={() => setSelected(t.id)}>Xem yêu cầu <ArrowRight size={16} /></button></div>
        </article>)}</div>}
        <Pager data={state.data} onChange={(page) => setFilters((f) => ({ ...f, page }))} />
      </>}</LoadState>
    </section>
    {selected && <TransferDialog id={selected} mine={mine} onClose={() => setSelected(null)} onDone={() => { setSelected(null); setMessage("Đã cập nhật yêu cầu chuyển giao."); state.reload(); }} />}
  </div>;
}
