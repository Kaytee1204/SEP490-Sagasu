import { useCallback, useEffect, useRef, useState } from 'react';
import { prepareImageFiles } from '@/utils/imageUploads';

export default function useImageUploads() {
  const [processing, setProcessing] = useState(false);
  const active = useRef(true);
  const running = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);
  const prepare = useCallback(async (files, options) => {
    if (running.current || !files.length) return null;
    running.current = true;
    setProcessing(true);
    try {
      const result = await prepareImageFiles(files, options);
      return active.current ? result : null;
    } finally {
      running.current = false;
      if (active.current) setProcessing(false);
    }
  }, []);
  return { prepare, processing };
}
