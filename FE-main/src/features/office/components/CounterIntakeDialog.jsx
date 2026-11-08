import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2, Search, Package } from "lucide-react";
import { workflowApi } from "../api/workflowApi";
import { officeApi } from "../api/officeApi";
import useLoad from "../hooks/useLoad";
import Dialog from "./Dialog";
import Notice from "./Notice";
import LoadState from "./LoadState";

export default function CounterIntakeDialog({ onClose, onDone }) {
  const [query, setQuery] = useState("");
  const [people, setPeople] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [person, setPerson] = useState(null);
  const [post, setPost] = useState(null);
  const [form, setForm] = useState({ locationId: "", condition: "", reconciliationNote: "", physicallyReceived: false });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [receiptId, setReceiptId] = useState(null);
  const posts = useLoad(() => person ? workflowApi.counterPosts(person.id) : Promise.resolve([]), person?.id);
  const locations = useLoad(() => officeApi.locations(), "counter-locations");
  useEffect(() => {
    let active = true;
    if (person || query.trim().length < 2) return;
    const timer = setTimeout(() => {
      workflowApi.counterPeople(query.trim()).then(result => { if (active) setPeople(result); })
        .catch(failure => { if (active) setSearchError(failure.message); })
        .finally(() => { if (active) setSearching(false); });
    }, 300);
    return () => { active = false; clearTimeout(timer); };
  }, [query, person]);
  function search(value) { setQuery(value); setPeople([]); setSearchError(""); setSearching(value.trim().length >= 2); }
  const change = (key, value) => setForm(old => ({ ...old, [key]: value }));
  async function submit(e) {
    e.preventDefault(); if (busy) return;
    setBusy(true); setError("");
    try {
      const id = await workflowApi.counterReceive({ ...form, locationId: Number(form.locationId), accountId: person.id, postId: post.id });
      setReceiptId(id); onDone();
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <Dialog title="Tiếp nhận tại quầy" busy={busy} onClose={onClose}>
    {receiptId ? <div className="of-counter-success"><CheckCircle2 size={36} /><h3>Đã tiếp nhận đồ</h3><p>Phiếu #{receiptId} đã được tạo. Văn phòng tiếp tục quản lý bài đăng và các yêu cầu nhận lại đồ liên quan.</p><div className="of-actions"><Link className="of-button of-primary" to={`/approver/receipts/${receiptId}`}>Xem phiếu tiếp nhận</Link><button className="of-button" onClick={onClose}>Đóng</button></div></div> : <>
      <p className="of-muted">Dành cho người mang đến đồ đã có bài đăng. Tìm người đăng, đối chiếu đồ và ghi nhận tiếp nhận tại đây.</p>
      {!person ? <div className="of-form">
        <label>Tên hoặc email người đăng<div className="of-counter-search"><Search size={18} /><input autoFocus value={query} onChange={e => search(e.target.value)} placeholder="Nhập ít nhất 2 ký tự…" autoComplete="off" /></div></label>
        <Notice error>{searchError}</Notice>
        {searching && <p role="status">Đang tìm người đăng…</p>}
        {!searching && query.trim().length >= 2 && !searchError && !people.length && <p className="of-muted">Không tìm thấy người có bài nhặt được đủ điều kiện tiếp nhận. Thử tìm bằng email để xác định đúng tài khoản.</p>}
        <div className="of-counter-options">{people.map(p => <button className="of-counter-option" key={p.id} onClick={() => { setPerson(p); setPost(null); }}><strong>{p.name}</strong><span>{p.email}</span></button>)}</div>
      </div> : <>
        <div className="of-counter-person"><div><strong>{person.name}</strong><p>{person.email}</p></div><button className="of-button" disabled={busy} onClick={() => { setPerson(null); setPost(null); search(query); }}>Đổi người</button></div>
        {!post ? <LoadState state={posts}><h3>Chọn bài đăng của người giao</h3>{!posts.loading && !posts.data?.length && <p>Người này không còn bài đăng đủ điều kiện tiếp nhận.</p>}<div className="of-counter-options">{posts.data?.map(p => <button className="of-counter-option of-counter-post" key={p.id} onClick={() => { setPost(p); setError(""); setForm({ locationId: "", condition: "", reconciliationNote: "", physicallyReceived: false }); }}>{p.images[0] ? <img src={p.images[0]} alt="" /> : <Package size={32} />}<span><strong>{p.title}</strong><small>Bài #{p.id}</small></span></button>)}</div></LoadState> : <form className="of-form" onSubmit={submit}>
          <div className="of-counter-selected"><h3>{post.title}</h3><small>Bài #{post.id}</small><p className="of-prose">{post.description}</p><div className="of-gallery">{post.images.map(url => <a href={url} target="_blank" rel="noreferrer" key={url}><img src={url} alt={post.title} /></a>)}</div><button type="button" className="of-button" disabled={busy} onClick={() => setPost(null)}>Chọn bài khác</button></div>
          <Notice error>{error || locations.error}</Notice>
          <label>Vị trí lưu kho *<select required disabled={busy || locations.loading} value={form.locationId} onChange={e => change("locationId", e.target.value)}><option value="">Chọn vị trí</option>{locations.data?.filter(l => l.active).map(l => <option key={l.id} value={l.id}>{l.code} · {l.name}</option>)}</select></label>
          <label>Tình trạng đồ khi nhận *<textarea required maxLength={2000} disabled={busy} value={form.condition} onChange={e => change("condition", e.target.value)} placeholder="Tình trạng thực tế, phụ kiện đi kèm…" /></label>
          {post.previousHandovers > 0 && <label>Đối chiếu lần giao đồ trước *<small>Bài đăng đã có biên bản giao đồ. Ghi rõ người giao lại và kết quả kiểm tra đồ thực tế.</small><textarea required maxLength={2000} disabled={busy} value={form.reconciliationNote} onChange={e => change("reconciliationNote", e.target.value)} /></label>}
          <label className="of-check"><input type="checkbox" required disabled={busy} checked={form.physicallyReceived} onChange={e => change("physicallyReceived", e.target.checked)} /><span>Tôi đã đối chiếu người giao với tài khoản trên và trực tiếp nhận, kiểm tra đúng đồ vật.</span></label>
          <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={onClose}>Để sau</button><button className="of-button of-primary" disabled={busy || !form.physicallyReceived}>{busy ? "Đang tiếp nhận…" : "Xác nhận đã nhận đồ"}</button></div>
        </form>}
      </>}
    </>}
  </Dialog>;
}
