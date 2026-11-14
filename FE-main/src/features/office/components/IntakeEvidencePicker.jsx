import EvidencePicker from "./EvidencePicker";

export default function IntakeEvidencePicker(props) {
  return <section className="of-form">
    <h3>Ảnh khi tiếp nhận đồ *</h3>
    <p className="of-muted">Thêm ít nhất một ảnh chụp đồ thực tế khi nhận, thể hiện tình trạng và phụ kiện đi kèm. Ảnh được lưu trong phiếu tiếp nhận, riêng với ảnh bài đăng của người giao.</p>
    <EvidencePicker {...props} />
  </section>;
}
