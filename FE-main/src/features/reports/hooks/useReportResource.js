import { useCallback, useEffect, useRef, useState } from 'react';

// Refresh an open case in place so an incoming notification never discards a reply draft.
export default function useReportResource(key, load) {
  const loader = useRef(load);
  useEffect(() => { loader.current = load; }, [load]);
  const sequence = useRef(0);
  const [state, setState] = useState({});
  const invalidate = useCallback(() => { sequence.current++; }, []);
  const reload = useCallback(async () => {
    const request = ++sequence.current;
    try {
      const data = await loader.current();
      if (sequence.current === request) setState({ key, data, loading: false, error: '' });
    } catch (error) {
      if (sequence.current === request) setState(old => ({
        key, data: old.key === key && ![401, 403, 404].includes(error.status) ? old.data : null,
        loading: false, error: error.message,
      }));
    }
  }, [key]);
  useEffect(() => {
    reload();
    const refreshVisible = () => { if (!document.hidden) reload(); };
    window.addEventListener('focus', refreshVisible);
    window.addEventListener('report-workspace-updated', refreshVisible);
    const timer = window.setInterval(refreshVisible, 60000);
    return () => {
      invalidate();
      window.removeEventListener('focus', refreshVisible);
      window.removeEventListener('report-workspace-updated', refreshVisible);
      window.clearInterval(timer);
    };
  }, [reload, invalidate]);
  function setData(data) {
    sequence.current++;
    setState({ key, data, loading: false, error: '' });
  }
  return { ...(state.key === key ? state : { loading: true }), reload, setData };
}
