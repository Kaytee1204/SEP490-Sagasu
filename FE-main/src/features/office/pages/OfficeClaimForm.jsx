import { useState } from "react";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { getPostDetail } from "@/features/items/api/postApi";
import { officeApi } from "../api/officeApi";
import useLoad from "../hooks/useLoad";
import EvidencePicker from "../components/EvidencePicker";
import ClaimantPicker from "../components/ClaimantPicker";
import Badge from "../components/Badge";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import SearchBox from "../components/SearchBox";
import "../Office.css";

export default function OfficeClaimForm({ counter = false }) {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [postId, setPostId] = useState(
    counter ? params.get("postId") || "" : id,
  );
  const [query, setQuery] = useState("");
  const stored = useLoad(
    () =>
      counter ? officeApi.stored({ q: query, page: 1 }) : Promise.resolve(null),
    `stored:${query}:${counter}`,
  );
  const selected = useLoad(
    () =>
      postId
        ? getPostDetail(postId)
        : Promise.resolve(null),
    `post:${postId}:${counter}`,
  );
  const [form, setForm] = useState({
    claimantName: counter ? "" : user?.fullName || "",
    claimantEmail: counter ? "" : user?.email || "",
    claimantPhone: "",
    description: "",
    evidenceNote: "",
  });
  const [files, setFiles] = useState([]);
  const [claimant, setClaimant] = useState(null);
  function selectClaimant(account) {
    setClaimant(account);
    setForm((current) => ({
      ...current,
      claimantName: account?.name || "",
      claimantEmail: account?.email || "",
    }));
  }
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  const back = counter ? "/approver/office-claims" : "/van-phong/claims";
  const valid =
    selected.data &&
    ["OPEN", "RESERVED"].includes(selected.data.status) &&
    selected.data.moderationStatus === "VISIBLE" &&
    selected.data.custodyMode === "OFFICE";
  async function submit(e) {
    e.preventDefault();
    if (busy || !valid) return;
    if (counter && !claimant) {
      setError("Chọn tài khoản người yêu cầu nhận đồ từ kết quả tìm kiếm.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const claimId = await officeApi.submit(
        {
          ...form,
          postId: Number(postId),
          claimantId: counter ? claimant?.id : null,
          claimantName: form.claimantName.trim() || null,
          claimantEmail: form.claimantEmail.trim().toLowerCase() || null,
          claimantPhone: form.claimantPhone.trim() || null,
          description: form.description.trim(),
          evidenceNote: form.evidenceNote.trim() || null,
        },
        files,
        counter,
      );
      navigate(`${back}/${claimId}`, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="office-page">
      <Link className="of-back" to={back}>
        ← Danh sách yêu cầu
      </Link>
      <Header
        title={
          counter ? "Lập yêu cầu tại quầy" : "Yêu cầu nhận đồ tại văn phòng"
        }
        description={
          counter
            ? "Nhập hộ thông tin do người đến nhận đồ cung cấp. Hệ thống ghi nhận bạn là người lập hồ sơ."
            : "Cung cấp căn cứ sở hữu để văn phòng xét duyệt và gửi lịch hẹn nhận đồ."
        }
      />
      <div className="of-detail-grid">
        <form className="of-form" onSubmit={submit}>
          <Notice error>{error}</Notice>
          <section className="of-panel">
            <div className="of-step">
              <span>1</span>Đồ vật cần nhận
            </div>
            {counter && (
              <>
                <SearchBox
                  value={query}
                  onChange={setQuery}
                  placeholder="Tìm tên đồ vật hoặc mã phiếu tiếp nhận…"
                />
                <LoadState state={stored}>
                  {stored.data && (
                    <div className="of-list-choice" style={{ marginTop: 12 }}>
                      {stored.data.content.map((r) => (
                        <button
                          className={`of-button ${String(r.postId) === String(postId) ? "of-primary" : ""}`}
                          type="button"
                          disabled={busy}
                          key={r.id}
                          onClick={() => setPostId(r.postId)}
                        >
                          {r.code} · {r.title}
                        </button>
                      ))}
                      {!stored.data.content.length && (
                        <p className="of-muted">Không tìm thấy đồ trong kho.</p>
                      )}
                    </div>
                  )}
                </LoadState>
              </>
            )}
            <LoadState state={selected}>
              {selected.data && (
                <div className="of-callout">
                  <strong>{selected.data.title}</strong>
                  <p className="of-muted">
                    Bài #{postId} · <Badge value={selected.data.status} />
                  </p>
                  {!valid && (
                    <Notice error>
                      Bài này không còn nhận thêm yêu cầu. Vui lòng chọn đồ khác
                      hoặc kiểm tra hồ sơ đã có.
                    </Notice>
                  )}
                </div>
              )}
            </LoadState>
          </section>
          <section className="of-panel of-form">
            <div className="of-step">
              <span>2</span>Người yêu cầu nhận đồ
            </div>
            {counter ? (
              <ClaimantPicker
                value={claimant}
                query={form.claimantEmail}
                onQueryChange={(email) => {
                  setClaimant(null);
                  setForm((current) => ({
                    ...current,
                    claimantEmail: email,
                    claimantName: "",
                  }));
                }}
                onChange={selectClaimant}
                disabled={busy}
              />
            ) : (
              <label>
                Địa chỉ thư điện tử
                <input
                  type="email"
                  maxLength={255}
                  disabled={busy}
                  value={form.claimantEmail}
                  onChange={(e) => change("claimantEmail", e.target.value)}
                  autoComplete="email"
                />
                <small>Dùng để nhận thư lịch hẹn sau khi duyệt.</small>
              </label>
            )}
            <div className="of-row">
              <label>
                Họ và tên
                <input
                  maxLength={150}
                  disabled
                  value={form.claimantName}
                  onChange={(e) => change("claimantName", e.target.value)}
                  autoComplete="name"
                  placeholder={
                    counter ? "Tự điền khi chọn tài khoản từ email" : undefined
                  }
                />
              </label>

              <label>
                Số điện thoại
                <input
                  type="tel"
                  maxLength={40}
                  disabled={busy}
                  value={form.claimantPhone}
                  onChange={(e) => change("claimantPhone", e.target.value)}
                  autoComplete="tel"
                />
              </label>
            </div>
          </section>
          <section className="of-panel of-form">
            <div className="of-step">
              <span>3</span>Căn cứ sở hữu
            </div>
            <label>
              Mô tả xác minh *
              <textarea
                required
                maxLength={5000}
                disabled={busy}
                value={form.description}
                onChange={(e) => change("description", e.target.value)}
                placeholder="Đặc điểm riêng, thời gian làm mất, thông tin chỉ người sở hữu biết…"
              />
            </label>
            <label>
              Ghi chú bằng chứng
              <textarea
                maxLength={5000}
                disabled={busy}
                value={form.evidenceNote}
                onChange={(e) => change("evidenceNote", e.target.value)}
              />
            </label>
            <EvidencePicker files={files} setFiles={setFiles} disabled={busy} />
            <div className="of-actions">
              <button
                className="of-button of-primary"
                disabled={busy || !valid || !form.description.trim()}
              >
                {busy
                  ? "Đang lưu hồ sơ…"
                  : counter
                    ? "Tạo yêu cầu nhận đồ"
                    : "Gửi yêu cầu"}
              </button>
              <Link className="of-button" to={back}>
                Quay lại
              </Link>
            </div>
          </section>
        </form>
        <aside className="of-panel of-sticky">
          <ShieldCheck size={28} color="#0f766e" />
          <h2 style={{ marginTop: 12 }}>Xác minh trước khi trao trả</h2>
          <p>
            Nội dung và bằng chứng chỉ được xem bởi người khai có tài khoản liên
            kết và nhân sự quản lý hồ sơ.
          </p>
          <div className="of-callout">
            <strong>
              {counter ? "Người nhập hồ sơ" : "Người gửi yêu cầu"}
            </strong>
            <p>{user?.fullName || user?.email}</p>
          </div>
          <p className="of-muted">
            Hồ sơ sau khi tạo cần được xét duyệt. Khi được duyệt, lịch hẹn sẽ
            được gửi đến hộp thư người nhận đồ.
          </p>
        </aside>
      </div>
    </div>
  );
}
