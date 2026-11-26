import { useEffect, useState } from 'react';

const MINUTE_MS = 60_000;

export default function useDeadlineCountdown(deadline, { showDays = false } = {}) {
  const end = Date.parse(deadline);
  const [now, setNow] = useState(Date.now);

  useEffect(() => {
    if (!Number.isFinite(end)) return;
    let timer;

    function tick() {
      window.clearTimeout(timer);
      const current = Date.now();
      setNow(current);
      const remaining = end - current;
      if (remaining > 0) timer = window.setTimeout(tick, remaining % MINUTE_MS || MINUTE_MS);
    }

    tick();
    document.addEventListener('visibilitychange', tick);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', tick);
    };
  }, [end]);

  if (!(end > now)) return null;
  const totalMinutes = Math.ceil((end - now) / MINUTE_MS);
  const totalHours = Math.floor(totalMinutes / 60);
  const days = showDays ? Math.floor(totalHours / 24) : 0;
  const hours = totalHours - days * 24;
  const minutes = totalMinutes % 60;
  return [days && `${days} ngày`, hours && `${hours} giờ`, minutes && `${minutes} phút`]
    .filter(Boolean)
    .join(' ');
}
