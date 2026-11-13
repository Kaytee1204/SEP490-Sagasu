import { useEffect, useState } from "react";
import EvidenceImage from "./EvidenceImage";
import Notice from "./Notice";
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_ACCEPT, IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';

export default function EvidencePicker({ files, setFiles, disabled, onProcessing }) {
  const [error, setError] = useState("");
  const { prepare, processing } = useImageUploads();
  useEffect(() => {
    onProcessing?.(processing);
    return () => onProcessing?.(false);
  }, [processing, onProcessing]);
  const busy = disabled || processing;
  async function select(e) {
    const added = [...(e.target.files || [])];
    e.target.value = "";
    if (busy) return;
    if (files.length + added.length > 5) {
      setError("Chọn tối đa 5 ảnh.");
      return;
    }
    setError("");
    try {
      const prepared = await prepare(added);
      if (prepared) setFiles(current => [...current, ...prepared]);
    } catch (problem) { setError(problem.message); }
  }
  return (
    <>
      <label>
        Ảnh bằng chứng
        <input
          type="file"
          multiple
          accept={IMAGE_ACCEPT}
          disabled={busy}
          onChange={select}
        />
        <small>{IMAGE_FORMATS} · Tối đa 5 ảnh, 5 MB/ảnh. Không công khai trên bài đăng.</small>
      </label>
      <Notice error>{error}</Notice>
      <ImageProcessingStatus active={processing} />
      <div className="of-files">
        {files.map((file, i) => (
          <figure key={`${file.name}:${i}`}>
            <EvidenceImage file={file} index={i} />
            <figcaption>{file.name}</figcaption>
            <button
              type="button"
              className="of-button"
              disabled={busy}
              onClick={() => setFiles(files.filter((_, index) => index !== i))}
            >
              Xóa ảnh {i + 1}
            </button>
          </figure>
        ))}
      </div>
    </>
  );
}
