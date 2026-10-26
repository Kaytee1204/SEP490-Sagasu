import React from 'react';

export default function ItemCharacteristics({ description, characteristics = [] }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div>
        <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '12px' }}>Mô tả chi tiết</h3>
        <p className="text-body" style={{ color: 'var(--text-secondary)', whiteSpace: 'pre-line' }}>
          {description}
        </p>
      </div>

      {characteristics.length > 0 && (
        <div>
          <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '12px' }}>Đặc điểm nhận dạng</h3>
          <ul style={{
            listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '8px',
            color: 'var(--text-secondary)', fontSize: '0.9375rem', padding: 0
          }}>
            {characteristics.map((char, i) => (
              <li key={i} style={{ display: 'flex', gap: '8px', alignItems: 'flex-start' }}>
                <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--accent)', marginTop: 8, flexShrink: 0 }} />
                {char}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
