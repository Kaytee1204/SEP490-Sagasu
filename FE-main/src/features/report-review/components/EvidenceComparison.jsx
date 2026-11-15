import ContextPanel from "./ContextPanel";
import { date, labels } from "../format";

const fields = [
  ["post", "title", "Tên đồ vật"], ["post", "description", "Mô tả bài đăng"],
  ["post", "status", "Trạng thái bài"], ["post", "custody_mode", "Bên giữ đồ"],
  ["post", "moderation_status", "Hiển thị bài"],
  ["claim", "status", "Trạng thái yêu cầu"], ["claim", "description", "Căn cứ nhận đồ"],
  ["receipt", "condition_at_receipt", "Tình trạng lúc tiếp nhận"],
  ["receipt", "status", "Trạng thái phiếu tiếp nhận"],
  ["officeHandover", "condition_note", "Tình trạng lúc bàn giao"],
  ["officeHandover", "status", "Kết quả bàn giao văn phòng"],
  ["p2pHandover", "status", "Kết quả bàn giao giữa người dùng"],
];
const imageGroups = [
  ["postImages", "Ảnh bài đăng"], ["receiptImages", "Ảnh phiếu tiếp nhận"],
  ["claimEvidence", "Bằng chứng người yêu cầu"], ["handoverEvidence", "Ảnh bàn giao"],
];
const text = value => value == null || value === "" ? "Chưa ghi nhận" : labels[value] || String(value);

export default function EvidenceComparison({ current, snapshot }) {
  const saved = snapshot?.payload;
  if (!saved) return <p className="rr-callout">Chưa có bản lưu để đối chiếu.</p>;
  const changes = fields.filter(([group, key]) =>
    (saved[group]?.[key] ?? null) !== (current[group]?.[key] ?? null));
  return <div className="rr-stack">
    <p className="rr-callout">Bản lưu {snapshot.capture_source === "SUBMISSION" ? "khi gửi báo cáo" : "từ lần đầu quản lý mở báo cáo cũ"}: {date(snapshot.captured_at)}.
      Dữ liệu hiện tại là thông tin ở lần tải gần nhất. Thay đổi nội dung chưa tự xác định có vi phạm.</p>
    <section className="rr-card">
      <h3>Thông tin đã thay đổi ({changes.length})</h3>
      {changes.length ? changes.map(([group, key, label]) => <article className="rr-comparison-row" key={`${group}.${key}`}>
        <h4>{label}</h4><dl className="rr-comparison-values">
          <div><dt>Trong bản lưu</dt><dd>{text(saved[group]?.[key])}</dd></div>
          <div><dt>Hiện tại</dt><dd>{text(current[group]?.[key])}</dd></div>
        </dl>
      </article>) : <p className="rr-muted">Các thông tin tóm tắt ở trên không thay đổi. Vẫn cần đối chiếu ảnh và lịch sử bên dưới.</p>}
    </section>
    <section className="rr-card"><h3>Các nhóm ảnh để đối chiếu</h3>
      <p className="rr-muted">Số lượng ảnh chỉ giúp định vị chứng cứ; số lượng bằng nhau không có nghĩa nội dung giống nhau.</p>
      {imageGroups.map(([key, label]) => <div className="rr-row rr-comparison-count" key={key}><strong>{label}</strong>
        <span>Bản lưu: {Array.isArray(saved[key]) ? `${saved[key].length} ảnh` : "chưa ghi nhận"} · Hiện tại: {current[key]?.length || 0} ảnh</span>
      </div>)}
    </section>
    <div className="rr-comparison-columns">
      <section><h3>Chứng cứ trong bản lưu</h3><ContextPanel snapshot data={saved} /></section>
      <section><h3>Chứng cứ hiện tại</h3><ContextPanel data={current} /></section>
    </div>
  </div>;
}
