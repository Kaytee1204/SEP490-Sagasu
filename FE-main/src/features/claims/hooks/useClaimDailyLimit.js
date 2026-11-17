import { useEffect, useState } from 'react';
import api from '@/services/apiClient';

export default function useClaimDailyLimit(accountId) {
  const [quota, setQuota] = useState(null);
  useEffect(() => {
    if (!accountId) return;
    let cancelled = false;
    let sequence = 0;
    const refresh = async () => {
      const request = ++sequence;
      try {
        const response = await api.get('/claims/daily-limit');
        if (!cancelled && request === sequence) setQuota({ ...response.data, accountId });
      } catch {
        if (!cancelled && request === sequence) setQuota({ accountId, error: true });
      }
    };
    refresh();
    window.addEventListener('focus', refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => { cancelled = true; window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [accountId]);
  return quota?.accountId === accountId ? quota : null;
}
