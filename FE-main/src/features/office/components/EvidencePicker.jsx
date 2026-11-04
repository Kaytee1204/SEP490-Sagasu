import { useState } from "react";
import EvidenceImage from "./EvidenceImage";
import Notice from "./Notice";

export default function EvidencePicker({ files, setFiles, disabled }) {
  const [error, setError] = useState("");
  function select(e) {
    const added = [...(e.target.files || [])];
    e.target.value = "";
    if (
      files.length + added.length > 5 ||
      added.some(
        (f) =>
          !["image/jpeg", "image/png"].includes(f.type) ||
          !f.size ||
          f.size > 5 * 1024 * 1024,
      )
    ) {
      setError("Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.");
      return;
    }
    setError("");
    setFiles([...files, ...added]);
  }
  return (
    <>
      <label>
        Ảnh bằng chứng
        <input
          type="file"
          multiple
          accept="image/jpeg,image/png"
          disabled={disabled}
          onChange={select}
        />
        <small>Tối đa 5 ảnh, 5 MB/ảnh. Không công khai trên bài đăng.</small>
      </label>
      <Notice error>{error}</Notice>
      <div className="of-files">
        {files.map((file, i) => (
          <figure key={`${file.name}:${i}`}>
            <EvidenceImage file={file} index={i} />
            <figcaption>{file.name}</figcaption>
            <button
              type="button"
              className="of-button"
              disabled={disabled}
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
