import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { officeApi } from "../api/officeApi";
import { date } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import ModerationDialog from "../components/ModerationDialog";
import Badge from "../components/Badge";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import ReceiptInfo from "../components/ReceiptInfo";
import "../Office.css";

export default function StaffPostDetail() {
  const { id } = useParams();
  const state = useLoad(() => officeApi.post(id), id);
  const [dialog, setDialog] = useState(false);
  const [message, setMessage] = useState("");
  const post = state.data;
  return (
    <div className="office-page">
      <Link className="of-back" to="/admin/items">
        ← Danh sách bài đăng
      </Link>
      <Notice>{message}</Notice>
      <LoadState state={state}>
        {post && (
          <>
            <Header
              title={post.title}
              description={`Bài #${post.id} · ${post.category} · ${date(post.createdAt)}`}
            >
              {["VISIBLE", "HIDDEN"].includes(post.moderationStatus) && (
                <button
                  className={`of-button ${post.moderationStatus === "HIDDEN" ? "of-primary" : "of-danger"}`}
                  onClick={() => setDialog(true)}
                >
                  {post.moderationStatus === "HIDDEN" ? (
                    <Eye size={17} />
                  ) : (
                    <EyeOff size={17} />
                  )}
                  {post.moderationStatus === "HIDDEN"
                    ? "Gỡ ẩn bài"
                    : "Ẩn bài đăng"}
                </button>
              )}
            </Header>
            <div className="of-detail-grid">
              <div>
                <section className="of-panel">
                  <div className="of-gallery">
                    {post.images.map((src, i) => (
                      <a key={src} href={src} target="_blank" rel="noreferrer">
                        <img src={src} alt={`Ảnh đồ vật ${i + 1}`} />
                      </a>
                    ))}
                  </div>
                  <h2>Nội dung bài đăng</h2>
                  <p className="of-prose">{post.description}</p>
                </section>
                <ReceiptInfo receipt={post.receipt} />
              </div>
              <aside>
                <section className="of-panel">
                  <h2>Thông tin bài đăng</h2>
                  <div className="of-badges">
                    <Badge value={post.postType} />
                    <Badge value={post.status} />
                    <Badge value={post.moderationStatus} />
                    <Badge value={post.custodyMode} />
                  </div>
                  <dl className="of-facts" style={{ marginTop: 20 }}>
                    <div>
                      <dt>Người đăng</dt>
                      <dd>{post.author}</dd>
                    </div>
                    <div>
                      <dt>Thư điện tử</dt>
                      <dd>{post.authorEmail}</dd>
                    </div>
                    <div>
                      <dt>Địa điểm</dt>
                      <dd>{post.location}</dd>
                    </div>
                    <div>
                      <dt>Cập nhật</dt>
                      <dd>{date(post.updatedAt)}</dd>
                    </div>
                  </dl>
                </section>
                {post.moderationNote && (
                  <section className="of-panel">
                    <h2>Quyết định kiểm duyệt gần nhất</h2>
                    <p className="of-prose">{post.moderationNote}</p>
                    <p className="of-muted">
                      {post.moderatedBy} · {date(post.moderatedAt)}
                    </p>
                  </section>
                )}
              </aside>
            </div>
            {dialog && (
              <ModerationDialog
                post={post}
                onClose={() => setDialog(false)}
                onDone={() => {
                  setDialog(false);
                  setMessage("Đã cập nhật trạng thái hiển thị bài đăng.");
                  state.reload();
                }}
              />
            )}
          </>
        )}
      </LoadState>
    </div>
  );
}
