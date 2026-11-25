export default function IntakeAssetFields({ items, condition, onChange, disabled }) {
  return <>
    <label>Tài sản và phụ kiện tiếp nhận *
      <textarea required rows={3} maxLength={2000} disabled={disabled} value={items}
        onChange={(event) => onChange("items", event.target.value)} placeholder="Ví dụ: 01 bình giữ nhiệt, 01 nắp bình, 01 túi đựng" />
    </label>
    <label>Tình trạng tài sản khi tiếp nhận *
      <textarea required rows={3} maxLength={2000} disabled={disabled} value={condition}
        onChange={(event) => onChange("condition", event.target.value)} placeholder="Ví dụ: Thân bình có vết xước nhẹ ở đáy" />
    </label>
  </>;
}
