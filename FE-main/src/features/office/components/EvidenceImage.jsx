import { useEffect, useRef } from "react";

export default function EvidenceImage({ file, index }) {
  const ref = useRef(null);
  useEffect(() => {
    const url = URL.createObjectURL(file);
    ref.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);
  return <img ref={ref} alt={`Bằng chứng ${index + 1}`} />;
}
