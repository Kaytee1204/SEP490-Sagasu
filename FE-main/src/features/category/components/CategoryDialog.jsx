import { useEffect, useRef } from 'react';
import Modal from '@/components/common/Modal';

// Keep focus within the category dialog and restore it to the invoking button.
export default function CategoryDialog({ title, busy, onClose, children }) {
  const content = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const dialog = content.current.closest('[role="dialog"]');
    const focusable = () => [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]')];
    const firstInput = dialog.querySelector('input');
    (firstInput || focusable()[0])?.focus();
    const trapFocus = (event) => {
      if (event.key !== 'Tab') return;
      const elements = focusable();
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    dialog.setAttribute('aria-label', title);
    dialog.addEventListener('keydown', trapFocus);
    return () => { dialog.removeEventListener('keydown', trapFocus); previous?.focus(); };
  }, [title]);
  return <Modal isOpen title={title} onClose={() => { if (!busy) onClose(); }}>
    <div ref={content}>{children}</div>
  </Modal>;
}
