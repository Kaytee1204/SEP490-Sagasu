import { CalendarDays, Clock3, MapPin } from "lucide-react";
import { date } from "../officeUtils";

export default function OfficePickupAppointment({ appointment, verificationDeadline }) {
  return <section className="of-appointment-reply" aria-label="Lịch xác minh nhận tài sản">
    <div className="of-appointment-heading"><CalendarDays size={22} aria-hidden="true" />
      <div><small>LỊCH XÁC MINH NHẬN TÀI SẢN</small><h3>{date(appointment.scheduledAt)}</h3></div>
    </div>
    <p className="of-appointment-location"><MapPin size={17} aria-hidden="true" />
      <span><strong>Địa điểm</strong> {appointment.location}</span>
    </p>
    {appointment.note && <p className="of-prose"><strong>Hướng dẫn</strong> {appointment.note}</p>}
    {verificationDeadline && <>
      <p className="of-reply-deadline"><Clock3 size={16} aria-hidden="true" />
        <span><strong>Hạn đến quầy</strong> {date(verificationDeadline)}</span>
      </p>
      <p>Bạn vui lòng mang theo giấy tờ tùy thân và căn cứ sở hữu để xác minh tại quầy.</p>
      <p>Nếu hết hạn mà bạn chưa đến xác minh, yêu cầu nhận tài sản sẽ tự động bị hủy.</p>
      <p>Nếu cần đổi giờ hẹn, bạn vui lòng liên hệ văn phòng. Thời hạn hiện tại vẫn có hiệu lực cho đến khi văn phòng cập nhật lịch mới.</p>
    </>}
  </section>;
}
