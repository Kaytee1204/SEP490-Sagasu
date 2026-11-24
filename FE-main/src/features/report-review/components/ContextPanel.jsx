import { Link } from "react-router-dom";
import { Badge, Gallery, Empty } from "./Shared";
import { date, labels } from "../format";
const names = {
  title: "Đồ vật",
  description: "Mô tả",
  post_type: "Loại bài",
  status: "Trạng thái",
  custody_mode: "Bên giữ đồ",
  moderation_status: "Hiển thị",
  moderation_note: "Lý do kiểm duyệt",
  location_text: "Địa điểm",
  event_at: "Thời điểm mất/nhặt",
  created_at: "Thời điểm tạo",
  updated_at: "Cập nhật gần nhất",
  deleted_at: "Thời điểm xóa",
  claimant_name: "Người khai",
  claimant_email: "Email người khai",
  evidence_note: "Thông tin bổ sung",
  claim_type: "Nơi xử lý",
  submission_channel: "Kênh tiếp nhận",
  close_reason: "Lý do đóng",
  receipt_code: "Mã phiếu",
  source: "Nguồn tiếp nhận",
  condition_at_receipt: "Tình trạng khi nhận",
  recorded_at: "Thời điểm ghi nhận",
  received_at: "Thời điểm nhận đồ",
  finder_name: "Người giao",
  finder_contact: "Liên hệ người giao",
  received_by: "Nhân viên nhận",
  storage_location: "Vị trí lưu kho",
  note: "Ghi chú",
  scheduled_at: "Lịch hẹn",
  location: "Địa điểm",
  response_status: "Phản hồi lịch hẹn",
  email_sent_at: "Gửi thư lúc",
  response_deadline: "Hạn phản hồi",
  responded_at: "Phản hồi lúc",
  cancellation_reason: "Lý do hủy",
  result: "Kết quả",
  verified_at: "Xác minh lúc",
  officer: "Nhân viên xác minh",
  condition_note: "Tình trạng bàn giao",
  confirmed_at: "Hoàn tất lúc",
  issue_note: "Phản ánh",
  full_name: "Họ tên",
  email: "Email",
  role: "Vai trò",
  requested_at: "Gửi yêu cầu lúc",
  processed_at: "Xử lý lúc",
  reason: "Lý do",
  reconciliation_note: "Kết quả đối chiếu",
  body: "Nội dung tin nhắn",
  location_label: "Địa điểm chia sẻ",
};
Object.assign(names, {
  private_identifiers: "Đặc điểm đối chiếu nội bộ",
  claim_id: "Mã yêu cầu",
  recorded_by_name: "Người ghi nhận",
  confirmed_by_name: "Người xác nhận",
  recipient_name: "Người nhận đồ",
});
function Facts({ data }) {
  return (
    <dl className="rr-facts">
      {Object.entries(data || {})
        .filter(([key, value]) => names[key] && value != null && value !== "")
        .map(([key, value]) => (
          <div key={key}>
            <dt>{names[key]}</dt>
            <dd>
              {key.endsWith("_at") || key === "response_deadline"
                ? date(value)
                : labels[value] || String(value)}
            </dd>
          </div>
        ))}
    </dl>
  );
}
function Records({ title, rows }) {
  return rows?.length ? (
    <section className="rr-card">
      <h3>
        {title} <span className="rr-muted">({rows.length})</span>
      </h3>
      {rows.map((row, i) => (
        <div className="rr-record" key={i}>
          <Facts data={row} />
        </div>
      ))}
    </section>
  ) : null;
}
function ClaimEvidence({ data }) {
  const { description, evidence_note, claim_id, claimant_name, ...facts } = data.claim;
  const images = data.claimEvidence || [];
  return <section className="rr-card rr-claim-proof">
    <div className="rr-row">
      <h3>Căn cứ nhận đồ</h3><small>Yêu cầu #{claim_id}{claimant_name && ` · ${claimant_name}`}</small>
    </div>
    <p className="rr-proof-note">{description || "Chưa cung cấp căn cứ nhận đồ."}</p>
    {evidence_note && <p className="rr-description"><strong>Thông tin bổ sung</strong> · {evidence_note}</p>}
    <h4>Ảnh bằng chứng <small>({images.length})</small></h4>
    {images.length > 0 ? <Gallery images={images} /> : <p className="rr-muted">Hồ sơ không đính kèm ảnh.</p>}
    {!!data.answers?.length && <>
      <h4>Câu trả lời xác minh <small>({data.answers.length})</small></h4>
      <dl className="rr-proof-answers">
        {data.answers.map((answer, index) => <div key={index}>
          <dt>
            {answer.section_title && answer.section_title !== data.answers[index - 1]?.section_title &&
              <strong className="rr-proof-group">{answer.section_title}</strong>}
            {answer.question_label}
          </dt>
          <dd>{answer.answer_text || "Chưa trả lời"}</dd>
        </div>)}
      </dl>
    </>}
    <Facts data={facts} />
  </section>;
}
export default function ContextPanel({ data, snapshot = false, violation = false }) {
  if (!data) return <Empty>Chưa có dữ liệu đối chiếu.</Empty>;
  return (
    <div className="rr-stack rr-evidence-sections">
      {data.claim && <ClaimEvidence data={data} />}
      {!violation && data.post && (
        <section className="rr-card">
          <div className="rr-row">
            <h3>Bài đăng #{data.post.post_id}</h3>
            {!snapshot && (
              <Link to={`/admin/items/${data.post.post_id}`} target="_blank">
                Mở bài đăng ↗
              </Link>
            )}
          </div>
          <Facts data={data.post} />
          <Gallery images={data.postImages} />
        </section>
      )}
      {!!data.claimSupplements?.length && <section className="rr-card"><h3>Thông tin bổ sung cho yêu cầu nhận đồ</h3>{data.claimSupplements.map(entry => <div className="rr-record" key={entry.claim_activity_id}><strong>{entry.actor} · {date(entry.created_at)}</strong><p className="rr-description">{entry.reason}</p><Gallery images={entry.images} /></div>)}</section>}
      {data.message && (
        <section className="rr-card">
          <h3>Tin nhắn bị báo cáo #{data.message.message_id}</h3>
          <Facts data={data.message} />
          <Gallery images={data.messageAttachments} />
        </section>
      )}
      {data.account && (
        <section className="rr-card">
          <h3>Tài khoản bị báo cáo</h3>
          <Facts data={data.account} />
        </section>
      )}
      {!!Object.keys(data.receipt || {}).length && (
        <section className="rr-card">
          <h3>Phiếu tiếp nhận</h3>
          <Facts data={data.receipt} />
          <h4>Ảnh ghi nhận khi tiếp nhận</h4>
          {data.receiptImages === undefined ? (
            <p className="rr-muted">{snapshot
              ? "Bản lưu này chưa ghi nhận nhóm ảnh tiếp nhận. Xem dữ liệu hiện tại để đối chiếu thêm."
              : "Chưa có dữ liệu ảnh tiếp nhận."}</p>
          ) : <>
            <Gallery images={data.receiptImages.filter(image => image.evidence_source === "INTAKE")} />
            {!data.receiptImages.some(image => image.evidence_source === "INTAKE") &&
              <p className="rr-muted">Phiếu chưa có ảnh tiếp nhận riêng.</p>}
            {data.receiptImages.some(image => image.evidence_source === "INHERITED_POST") && <details>
              <summary>Ảnh bài đăng được lưu kèm phiếu cũ</summary>
              <p className="rr-muted">Nhóm ảnh này được kế thừa từ bài đăng, không xác nhận tình trạng đồ tại thời điểm văn phòng nhận.</p>
              <Gallery images={data.receiptImages.filter(image => image.evidence_source === "INHERITED_POST")} />
            </details>}
          </>}
        </section>
      )}
      <Records
        title="Lịch mang đồ đến văn phòng"
        rows={data.intakeAppointments}
      />
      <Records title="Lịch nhận lại đồ" rows={data.appointments} />
      <Records title="Xác minh tại quầy" rows={data.verifications} />
      {[
        ["p2pHandover", "Bàn giao giữa người dùng"],
        ["officeHandover", "Bàn giao tại văn phòng"],
      ].map(
        ([key, title]) =>
          Object.keys(data[key] || {}).length > 0 && (
            <section className="rr-card" key={key}>
              <h3>{title}</h3>
              <Facts data={data[key]} />
              <Gallery images={data.handoverEvidence} />
            </section>
          ),
      )}
      {!violation && <Records title="Chuyển trách nhiệm tiếp nhận" rows={data.transfers} />}
      {!violation && <Records title="Các yêu cầu nhận cùng món đồ" rows={data.relatedClaims} />}
      {!violation && !!data.claimHistory?.length && (
        <section className="rr-card">
          <h3>Diễn biến yêu cầu nhận đồ</h3>
          <ol className="rr-timeline">
            {data.claimHistory.map((x) => (
              <li key={x.claim_activity_id}>
                <strong>{labels[x.action] || x.action}</strong>
                <small>
                  {date(x.created_at)} · {x.actor}
                </small>
                <p>{x.reason}</p>
                <Badge value={x.from_status} /> → <Badge value={x.to_status} />
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}
