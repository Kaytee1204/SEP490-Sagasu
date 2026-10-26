import React from 'react';
import { CheckCircle } from 'lucide-react';

export default function StepIndicator({ currentStep, steps }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '40px' }}>
      {steps.map((step, index) => (
        <div key={index} style={{ display: 'flex', alignItems: 'center' }}>
          {/* Node */}
          <div style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px',
            position: 'relative', width: '80px'
          }}>
            <div style={{
              width: '32px', height: '32px', borderRadius: '50%',
              background: index < currentStep ? 'var(--accent)' : (index === currentStep ? 'var(--accent)' : 'var(--bg-canvas)'),
              border: `2px solid ${index <= currentStep ? 'var(--accent)' : 'var(--border-strong)'}`,
              color: index <= currentStep ? 'white' : 'var(--text-muted)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: '0.875rem', zIndex: 2,
              transition: 'all 300ms ease'
            }}>
              {index < currentStep ? <CheckCircle size={16} strokeWidth={2.5} /> : index + 1}
            </div>
            <div style={{
              fontSize: '0.75rem', fontWeight: 600,
              color: index <= currentStep ? 'var(--text-primary)' : 'var(--text-muted)',
              textAlign: 'center', whiteSpace: 'nowrap'
            }}>
              {step}
            </div>
          </div>
          {/* Connector */}
          {index < steps.length - 1 && (
            <div style={{
              width: '64px', height: '2px',
              background: index < currentStep ? 'var(--accent)' : 'var(--border-strong)',
              margin: '0 -20px 24px -20px', zIndex: 1,
              transition: 'all 300ms ease'
            }} />
          )}
        </div>
      ))}
    </div>
  );
}
