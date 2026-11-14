import { useRef } from 'react';
import { ImagePlus } from 'lucide-react';
import { IMAGE_ACCEPT } from '@/utils/imageUploads';
import './ImageUploadButton.css';

export default function ImageUploadButton({ label = 'Thêm ảnh', onChange, disabled = false, multiple = true, accept = IMAGE_ACCEPT, id, inputRef }) {
  const localInput = useRef(null);
  const fileInput = inputRef || localInput;

  return <div className="image-upload-control">
    <input id={id} ref={fileInput} type="file" accept={accept} multiple={multiple}
      disabled={disabled} onChange={onChange} aria-label={label} hidden style={{ display: 'none' }} />
    <button type="button" className="image-upload-button" disabled={disabled} onClick={() => fileInput.current?.click()}>
      <ImagePlus size={20} aria-hidden="true" /><span>{label}</span>
    </button>
  </div>;
}
