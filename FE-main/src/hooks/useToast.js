// src/hooks/useToast.js
// Reusable toast notification hook.
// Usage: const { toast, showToast } = useToast();

import { useState } from 'react';

/**
 * @returns {{ toast: string|null, showToast: (msg: string, duration?: number) => void }}
 */
export function useToast(duration = 3500) {
  const [toast, setToast] = useState(null);

  const showToast = (msg, customDuration) => {
    setToast(msg);
    setTimeout(() => setToast(null), customDuration ?? duration);
  };

  return { toast, showToast };
}
