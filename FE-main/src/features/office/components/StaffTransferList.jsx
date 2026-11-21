import { ArrowRight, CalendarDays, Package } from "lucide-react";
import { date } from "../officeUtils";
import TransferStage from "./TransferStage";
import { transferStage } from "../transferStage";

export default function StaffTransferList({ transfers, officer, actorId, onOpen }) {
  return (
    <div className="of-worklist of-transfer-worklist">
      <div className="of-worklist-header" aria-hidden="true">
        <span>Đồ vật & Người giao</span>
        <span>Tiến trình tiếp nhận</span>
        <span>Lịch hẹn & Kết quả</span>
        <span>Thao tác</span>
      </div>
      {transfers.map((transfer) => {
        const stage = transferStage(transfer);
        const appointment = transfer.appointment;
        const canSchedule = officer && transfer.requestedBy !== actorId && stage === "UNSCHEDULED";
        const active = transfer.status === "PENDING";
        return (
          <article className="of-worklist-row" key={transfer.id}>
            <div className="of-transfer-item">
              {transfer.images?.[0] ? (
                <img src={transfer.images[0]} alt="" loading="lazy" />
              ) : (
                <span>
                  <Package size={22} aria-hidden="true" />
                </span>
              )}
              <div>
                <div className="of-claim-meta-row">
                  <span className="of-code-pill">#{transfer.id}</span>
                  <small>{date(transfer.requestedAt)}</small>
                </div>
                <h2>{transfer.title}</h2>
                <p>Người giao: {transfer.finder || "Chưa ghi nhận"}</p>
              </div>
            </div>
            <div className="of-worklist-stage">
              <TransferStage stage={stage} />
              {active && transfer.activeClaims > 0 && (
                <small>{transfer.activeClaims} yêu cầu nhận lại đồ đi kèm</small>
              )}
            </div>
            <div className="of-transfer-event">
              {active && appointment ? (
                <>
                  <small>
                    <CalendarDays size={15} aria-hidden="true" />
                    {stage === "UNSCHEDULED" ? "Lịch cũ" : "Lịch mang đồ đến"}
                  </small>
                  <strong>{date(appointment.scheduledAt)}</strong>
                  {appointment.location && <p>{appointment.location}</p>}
                </>
              ) : active ? (
                <p>Chưa xếp lịch hẹn</p>
              ) : (
                <>
                  {transfer.processedAt && <small>Xử lý lúc {date(transfer.processedAt)}</small>}
                  {transfer.reason && <p className="of-reason-preview">{transfer.reason}</p>}
                  {transfer.receiptId && <p>Hồ sơ tiếp nhận #{transfer.receiptId}</p>}
                </>
              )}
            </div>
            <button
              type="button"
              className={`of-button of-worklist-action${canSchedule ? " of-primary" : ""}`}
              onClick={() => onOpen(transfer.id, canSchedule)}
            >
              {canSchedule ? "Sắp xếp lịch" : "Xem & xử lý"}{" "}
              <ArrowRight size={15} aria-hidden="true" />
            </button>
          </article>
        );
      })}
    </div>
  );
}
