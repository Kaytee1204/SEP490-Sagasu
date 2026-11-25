import { ArrowDownToLine, PackageCheck } from "lucide-react";
import { date } from "../format";
import { Gallery } from "./Shared";

function AssetRecord({ icon: Icon, title, code, facts, images, imageTitle }) {
  return <section className="rr-office-record">
    <div className="rr-office-record-heading">
      <span className="rr-case-icon"><Icon size={19} aria-hidden="true" /></span>
      <div><h3>{title}</h3>{code && <small>{code}</small>}</div>
    </div>
    <dl className="rr-office-facts">
      {facts.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "Chưa ghi nhận"}</dd></div>)}
    </dl>
    <h4>{imageTitle}</h4>
    {images.length > 0 ? <Gallery images={images} /> : <p className="rr-muted">Chưa có ảnh trong hồ sơ.</p>}
  </section>;
}

export default function OfficeHandoverEvidence({ data }) {
  const snapshot = data.snapshot?.payload;
  const stored = snapshot?.handoverSource === "OFFICE" ? snapshot : data.current;
  const receipt = stored.receipt || {};
  const handover = stored.officeHandover || {};
  const intakeImages = (stored.receiptImages || []).filter(image => image.evidence_source === "INTAKE" && image.url);
  const handoverImages = (stored.handoverEvidence || []).filter(image => image.url);
  return <section className="rr-card">
    <h2>Đối chiếu tiếp nhận và bàn giao</h2>
    <p className="rr-muted">Yêu cầu nhận đồ #{data.report.claim_id}{stored === snapshot && " · Dữ liệu lưu khi gửi báo cáo"}</p>
    <div className="rr-office-evidence">
      <AssetRecord icon={ArrowDownToLine} title="Tiếp nhận tại văn phòng" code={receipt.receipt_code}
        facts={[["Tài sản và phụ kiện", receipt.item_summary], ["Tình trạng khi tiếp nhận", receipt.condition_at_receipt],
          ["Nhân viên tiếp nhận", receipt.received_by], ["Thời điểm tiếp nhận", receipt.received_at && date(receipt.received_at)],
          ["Vị trí lưu kho", receipt.storage_location]]} images={intakeImages} imageTitle="Ảnh khi tiếp nhận" />
      <AssetRecord icon={PackageCheck} title="Bàn giao cho người nhận" code={handover.handover_id && `Biên bản #${handover.handover_id}`}
        facts={[["Tài sản và phụ kiện", handover.item_summary], ["Tình trạng khi bàn giao", handover.condition_note],
          ["Nhân viên bàn giao", handover.recorded_by_name], ["Người nhận", handover.recipient_name],
          ["Thời điểm lập biên bản", handover.recorded_at && date(handover.recorded_at)],
          ["Người nhận xác nhận lúc", handover.completion_method === "RECIPIENT" && date(handover.confirmed_at)],
          ["Hạn tiếp nhận phản ánh", handover.report_deadline && date(handover.report_deadline)]]} images={handoverImages} imageTitle="Ảnh khi bàn giao" />
    </div>
    {handover.difference_note && <div className="rr-office-difference">
      <h3>Giải trình chênh lệch của văn phòng</h3>
      <p>{handover.difference_note}</p>
    </div>}
  </section>;
}
