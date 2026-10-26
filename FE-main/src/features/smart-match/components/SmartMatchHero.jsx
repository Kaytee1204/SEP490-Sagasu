import React from 'react';
import { CheckCircle } from 'lucide-react';

export default function SmartMatchHero({ postedItem }) {
  return (
    <div style={{ background: 'var(--accent)', color: 'white', padding: '60px 0 40px' }}>
      <div className="container" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{
          width: 64, height: 64, borderRadius: '50%', background: 'rgba(255,255,255,0.1)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '24px'
        }}>
          <CheckCircle size={32} color="var(--status-found)" />
        </div>
        <h1 className="text-display-lg" style={{ marginBottom: '16px', color: 'white' }}>Đăng tin thành công!</h1>
        <p className="text-body" style={{ color: 'rgba(255,255,255,0.8)', maxWidth: '600px', margin: '0 auto 32px' }}>
          Tin tìm đồ của bạn đã được ghi nhận. Ngay lập tức, hệ thống AI Smart Match đã chạy ngầm và đối chiếu với hàng ngàn món đồ được nhặt trên hệ thống.
        </p>

        {postedItem && (
          <div style={{
            display: 'flex', alignItems: 'center', gap: '16px',
            background: 'rgba(0,0,0,0.2)', padding: '16px 24px', borderRadius: 'var(--radius-xl)'
          }}>
            <img
              src={postedItem.img}
              alt="My Item"
              style={{ width: 60, height: 60, borderRadius: 'var(--radius-md)', objectFit: 'cover' }}
            />
            <div style={{ textAlign: 'left' }}>
              <div style={{ fontSize: '0.8125rem', color: 'rgba(255,255,255,0.6)', marginBottom: '4px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Tin vừa đăng
              </div>
              <div style={{ fontWeight: 600, fontSize: '1rem', color: 'white' }}>{postedItem.title}</div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
