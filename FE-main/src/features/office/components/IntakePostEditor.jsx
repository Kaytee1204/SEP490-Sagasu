import { receiptApi } from "@/features/receipt/api/receiptApi";
import useLoad from "../hooks/useLoad";
import EvidencePicker from "./EvidencePicker";
import Notice from "./Notice";

function OriginalPost({ post, original }) {
  if (!original) return <p className="of-muted">Yêu cầu cũ chưa có bản lưu nội dung lúc gửi.</p>;
  const same = post.title === original.title && post.description === original.description
    && post.categoryId === original.categoryId
    && post.images.map(image => image.id).join() === original.images.map(image => image.id).join();
  return <details className="of-intake-original" open={!same || undefined}>
    <summary>Bài đăng lúc gửi yêu cầu {same ? "· Trùng với nội dung hiện tại" : "· Đã có thay đổi"}</summary>
    <dl className="of-facts">
      <div><dt>Tiêu đề</dt><dd>{original.title}</dd></div>
      <div><dt>Danh mục</dt><dd>{original.categoryName}</dd></div>
    </dl>
    <dl className="of-facts">
      <div><dt>Mô tả lúc gửi</dt><dd className="of-prose">{original.description}</dd></div>
    </dl>
    {original.images.some(image => image.url) && <div>
      <h4>Ảnh lúc gửi yêu cầu</h4>
      <div className="of-gallery">{original.images.filter(image => image.url).map((image, index) =>
        <a key={image.id} href={image.url} target="_blank" rel="noreferrer"><img src={image.url} alt={`Ảnh lúc gửi yêu cầu ${index + 1}`} /></a>)}</div>
    </div>}
  </details>;
}

export default function IntakePostEditor({ post, original, draft, setDraft, files, setFiles, disabled, onProcessing }) {
  const categories = useLoad(receiptApi.categories, "intake-post-categories");
  const change = (field, value) => setDraft(current => ({ ...current, [field]: value }));
  const tooMany = draft.keptImageIds.length + files.length > 5;
  return <section className="of-form of-intake-post-editor">
    <div><h3>Đối chiếu và cập nhật bài đăng</h3>
      <p className="of-muted">Vui lòng đối chiếu nội dung với tài sản thực tế. Các thay đổi sẽ hiển thị trên bài đăng sau khi văn phòng xác nhận tiếp nhận.</p></div>
    {original !== undefined && <OriginalPost post={post} original={original} />}
    <div className="of-intake-post-fields">
      <label>Tiêu đề công khai *<input required maxLength={200} disabled={disabled} value={draft.title}
        onChange={event => change("title", event.target.value)} /></label>
      <label>Danh mục *<select required disabled={disabled || categories.loading} value={draft.categoryId}
        onChange={event => change("categoryId", Number(event.target.value))}>
        {!(categories.data || []).some(option => option.categoryId === post.categoryId) && <option value={post.categoryId}>{post.categoryName}</option>}
        {(categories.data || []).map(option => <option key={option.categoryId} value={option.categoryId}>{option.name}</option>)}
      </select></label>
    </div>
    {categories.error && <div><Notice error>{categories.error}</Notice><button type="button" className="of-button" onClick={categories.reload}>Tải lại danh mục</button></div>}
    <label>Mô tả công khai *<textarea rows={4} required maxLength={10000} disabled={disabled} value={draft.description}
      onChange={event => change("description", event.target.value)} /></label>
    {!!post.images.length && <div><h4>Ảnh trên bài đăng hiện tại</h4>
      <div className="of-intake-post-photos">{post.images.map((image, index) => <div key={image.id}>
        {image.url && <a href={image.url} target="_blank" rel="noreferrer"><img src={image.url} alt={`Ảnh công khai ${index + 1}`} /></a>}
        <label className="of-check"><input type="checkbox" checked={draft.keptImageIds.includes(image.id)} disabled={disabled}
          onChange={event => change("keptImageIds", post.images.filter(photo => photo.id === image.id ? event.target.checked : draft.keptImageIds.includes(photo.id)).map(photo => photo.id))} />
          <span>Giữ ảnh {index + 1}</span></label>
      </div>)}</div>
    </div>}
    <EvidencePicker files={files} setFiles={setFiles} disabled={disabled} onProcessing={onProcessing}
      label="Thêm ảnh công khai" hint="Ảnh này hiển thị trên bài đăng. Vui lòng tránh để lộ giấy tờ, thông tin liên hệ hoặc dấu hiệu dùng để xác minh chủ sở hữu." />
    <Notice error>{tooMany ? "Bài đăng chỉ được lưu tối đa 5 ảnh. Vui lòng bỏ bớt ảnh trước khi tiếp nhận." : null}</Notice>
  </section>;
}
