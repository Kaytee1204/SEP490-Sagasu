import { AlignLeft } from 'lucide-react';

export default function ItemCharacteristics({ description, characteristics = [] }) {
  return (
    <section className="post-detail-card post-detail-description" aria-label="Mô tả món đồ">
      <h2><AlignLeft size={18} aria-hidden="true" />Mô tả món đồ</h2>
      <p>{description || 'Chưa có mô tả.'}</p>

      {characteristics.length > 0 && (
        <div>
          <h3>Đặc điểm nhận dạng</h3>
          <ul>
            {characteristics.map((char, i) => (
              <li key={i}>{char}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
