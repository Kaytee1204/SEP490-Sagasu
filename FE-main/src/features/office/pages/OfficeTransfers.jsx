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
import CounterIntakeDialog from "../components/CounterIntakeDialog";
import { useAuth } from "@/context/AuthContext";
import PersonalOfficeHeader from '../components/PersonalOfficeHeader';
import "../Office.css";
import '../PersonalOffice.css';

const statuses = [["PENDING", "Đang chờ tiếp nhận"], ["UNSCHEDULED", "Cần hẹn lịch"], ["AWAITING_REPLY", "Chờ phản hồi lịch"], ["CONFIRMED", "Đã đồng ý lịch"], ["RECEIVED", "Đã nhận đồ"], ["REJECTED", "Đã từ chối"], ["CANCELLED", "Đã hủy yêu cầu"], ["ALL", "Tất cả"]];
function progress(t) {
  if (t.status !== "PENDING") return statuses.find(([s]) => s === t.status)?.[1];
  if (!t.appointment || ["DECLINED", "EXPIRED"].includes(t.appointment.responseStatus)) return "Cần hẹn lịch";
  return t.appointment.responseStatus === "CONFIRMED" ? "Đã đồng ý lịch" : t.appointment.responseStatus === "UNSENT" ? "Chưa gửi được lịch" : "Chờ phản hồi lịch";
}
export default function OfficeTransfers({ mine = false }) {
  const { user } = useAuth();
  const [counterOpen, setCounterOpen] = useState(false);
  const [filters, setFilters] = useState({ page: 1, status: mine ? "ALL" : "PENDING", q: "", mine });
  const state = useLoad(() => workflowApi.transfers(filters), JSON.stringify(filters));
  const [selected, setSelected] = useState(null);
  const [message, setMessage] = useState("");
  const change = (key, value) => setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return <div className={`office-page ${mine?'office-personal':''}`}>
    {mine?<PersonalOfficeHeader active="transfers"/>:<Header title="Yêu cầu tiếp nhận đồ thất lạc" description="Hẹn lịch với người giao, kiểm tra và tiếp nhận đồ tại quầy.">{user?.role === "APPROVER" && <button className="of-button of-primary" onClick={() => setCounterOpen(true)}>Tiếp nhận tại quầy</button>}</Header>}
    <Notice>{message}</Notice>
    <section className="of-panel">
      <div className="of-toolbar"><SearchBox value={filters.q} onChange={(q) => change("q", q)} placeholder="Tìm theo tên đồ vật…" /><Select label="Trạng thái tiếp nhận" value={filters.status} onChange={(s) => change("status", s)} options={statuses} /></div>
      <LoadState state={state}>{state.data && <>
        {!state.data.content.length ? <Empty text={filters.q.trim()||filters.status!==(mine?'ALL':'PENDING')?'Không tìm thấy yêu cầu phù hợp':mine?'Bạn chưa gửi yêu cầu tiếp nhận':'Chưa có yêu cầu tiếp nhận'} description={filters.q.trim()||filters.status!==(mine?'ALL':'PENDING')?'Thử thay đổi từ khóa hoặc bỏ bộ lọc để xem lại danh sách.':mine ? "Nếu đang giữ đồ nhặt được, mở bài đăng của bạn để nhờ văn phòng tiếp nhận và hỗ trợ trao trả." : "Yêu cầu mới từ người giữ đồ sẽ xuất hiện tại đây."}>{filters.q.trim()||filters.status!==(mine?'ALL':'PENDING')?<button className="of-button" onClick={()=>setFilters(f=>({...f,page:1,status:mine?'ALL':'PENDING',q:''}))}>Xóa bộ lọc</button>:mine?<Link className="of-button" to="/">Về trang chủ</Link>:null}</Empty> : <div className="of-transfer-grid of-transfer-list">{state.data.content.map((t) => <article className="of-transfer-card" key={t.id}>
          {t.images[0] ? <img className="of-transfer-image" src={t.images[0]} alt={t.title} /> : <div className="of-transfer-image of-transfer-placeholder"><Package size={28} /></div>}
          <div className="of-transfer-body"><span className={`of-transfer-status ${t.status === "RECEIVED" ? "is-complete" : t.status === "CANCELLED" || t.status === "REJECTED" ? "is-cancelled" : ""}`}>{progress(t)}</span><h2>{t.title}</h2><p className="of-muted">{t.finder} · {date(t.requestedAt)}</p>{t.status === "PENDING" && t.appointment && <p><strong>{date(t.appointment.scheduledAt)}</strong><br />{t.appointment.location}</p>}{t.activeClaims > 0 && <p>{t.activeClaims} yêu cầu nhận lại đồ</p>}{t.reason && <p className="of-prose of-reason-preview"><strong>Lý do:</strong> {t.reason}</p>}</div><div className="of-transfer-row-action"><button className={`of-button ${mine && t.status === "PENDING" && t.appointment?.responseStatus === "PENDING" ? "of-primary" : ""}`} onClick={() => setSelected(t.id)}>{mine && t.status === "PENDING" && t.appointment?.responseStatus === "PENDING" ? "Phản hồi lịch hẹn" : !mine && t.status === "PENDING" && (!t.appointment || ["EXPIRED", "DECLINED"].includes(t.appointment.responseStatus)) ? "Sắp xếp lịch" : "Xem yêu cầu"} <ArrowRight size={16} /></button></div>
        </article>)}</div>}
        <Pager data={state.data} onChange={(page) => setFilters((f) => ({ ...f, page }))} />
      </>}</LoadState>
    </section>
    {selected && <TransferDialog id={selected} mine={mine} onClose={() => setSelected(null)} onDone={() => { setMessage("Đã cập nhật yêu cầu tiếp nhận."); state.reload(); }} />}
    {counterOpen && <CounterIntakeDialog onClose={() => setCounterOpen(false)} onDone={() => state.reload()} />}
  </div>;
}
