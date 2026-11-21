import { useEffect, useRef } from 'react';

export default function LocalImagePreview({ file }) {
  const image = useRef(null);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    image.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  return <img ref={image} alt={`Xem trước ảnh ${file.name}`} />;
}
