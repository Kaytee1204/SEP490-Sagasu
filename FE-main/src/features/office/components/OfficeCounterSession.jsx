import { QrCode } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { counterConfirmationPath } from "../officeRoutes";
import { date } from "../officeUtils";
import CounterConfirmationNotice from "./CounterConfirmationNotice";

export default function OfficeCounterSession({ claimId, deadline, expired, onRefresh }) {
  const url = new URL(counterConfirmationPath(claimId), window.location.origin).href;
  return <section className="of-counter-session" aria-label="Xác nhận tại quầy">
    <CounterConfirmationNotice />
    {!expired && <>
      <div className="of-counter-qr">
        <QRCodeSVG value={url} size={144} marginSize={4} title="Mã QR mở biên bản của người nhận" />
        <div>
          <h3><QrCode size={18} aria-hidden="true" /> Quét mã để xác nhận</h3>
          <p>Người nhận cũng có thể mở <strong>Nhận đồ tại văn phòng</strong> và chọn yêu cầu <strong>#{claimId}</strong>.</p>
          <p className="of-counter-deadline">Hạn xác nhận <time dateTime={deadline}>{date(deadline)}</time></p>
        </div>
      </div>
      <button type="button" className="of-button" onClick={onRefresh}>Tải lại trạng thái</button>
    </>}
  </section>;
}
