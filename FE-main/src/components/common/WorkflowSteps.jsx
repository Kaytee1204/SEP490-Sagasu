import { Check, Pause, X } from 'lucide-react';
import './WorkflowSteps.css';

const currentMarkers = {
  active: { note: 'Đang ở bước này' },
  paused: { Icon: Pause, note: 'Tạm dừng ở bước này' },
  stopped: { Icon: X, note: 'Kết thúc ở bước này' },
};

export default function WorkflowSteps({ label, steps, completed, state = 'active' }) {
  return <ol className="workflow-steps" aria-label={label}>
    {steps.map((step, index) => {
      const done = index < completed;
      const current = index === completed ? currentMarkers[state] : null;
      const Icon = done ? Check : current?.Icon;
      const tone = done ? 'is-done' : current ? `is-${state}` : undefined;
      return <li key={step} className={tone} aria-current={current && state !== 'stopped' ? 'step' : undefined}>
        <span className="workflow-step-marker" aria-hidden="true">{Icon ? <Icon size={15} /> : index + 1}</span>
        <span>{step}<span className="sr-only">{done ? ' (Đã hoàn tất)' : current ? ` (${current.note})` : ''}</span></span>
      </li>;
    })}
  </ol>;
}
