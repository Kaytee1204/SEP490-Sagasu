export default function ImageProcessingStatus({ active }) {
  return active ? <p className="py-2 text-sm text-teal-700" role="status">Đang xử lý ảnh, vui lòng chờ…</p> : null;
}
