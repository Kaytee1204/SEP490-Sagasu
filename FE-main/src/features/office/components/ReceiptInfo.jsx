import { date } from "../officeUtils";
import Badge from "./Badge";

export default function ReceiptInfo({ receipt }) {
  return (
    receipt && (
      <section className="of-panel">
        <h2>Hồ sơ tiếp nhận · {receipt.code}</h2>
        <dl className="of-facts">
          <div>
            <dt>Vị trí lưu giữ</dt>
            <dd>{receipt.location || "Chưa bố trí"}</dd>
          </div>
          <div>
            <dt>Trạng thái</dt>
            <dd>
              <Badge value={receipt.status} />
            </dd>
          </div>
          <div>
            <dt>Người tiếp nhận</dt>
            <dd>{receipt.receivedBy}</dd>
          </div>
          <div>
            <dt>Thời điểm tiếp nhận</dt>
            <dd>{date(receipt.receivedAt)}</dd>
          </div>
          <div>
            <dt>Tài sản và phụ kiện tiếp nhận</dt>
            <dd className="of-prose">{receipt.items || "Chưa ghi nhận riêng"}</dd>
          </div>
          <div>
            <dt>Tình trạng tài sản khi tiếp nhận</dt>
            <dd className="of-prose">{receipt.condition || "—"}</dd>
          </div>
          <div>
            <dt>Người giao đồ</dt>
            <dd>
              {receipt.finderName || "—"} {receipt.finderContact || ""}
            </dd>
          </div>
        </dl>
      </section>
    )
  );
}
