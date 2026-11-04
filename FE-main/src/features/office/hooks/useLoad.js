import { useEffect, useRef, useState } from "react";

export default function useLoad(loader, key) {
  const fn = useRef(loader);
  useEffect(() => {
    fn.current = loader;
  });
  const [attempt, setAttempt] = useState(0);
  const [snapshot, setSnapshot] = useState({});
  const requestKey = `${key}:${attempt}`;
  useEffect(() => {
    let cancelled = false;
    Promise.resolve()
      .then(() => fn.current())
      .then((data) => {
        if (!cancelled) setSnapshot({ key: requestKey, data });
      })
      .catch((error) => {
        if (!cancelled) setSnapshot({ key: requestKey, error: error.message });
      });
    return () => {
      cancelled = true;
    };
  }, [requestKey]);
  return {
    data: snapshot.key === requestKey ? snapshot.data : null,
    error: snapshot.key === requestKey ? snapshot.error : "",
    loading: snapshot.key !== requestKey,
    reload: () => setAttempt((a) => a + 1),
  };
}
