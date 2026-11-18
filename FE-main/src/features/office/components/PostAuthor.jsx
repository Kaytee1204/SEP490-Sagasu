export default function PostAuthor({ post }) {
  return (
    <div className="of-post-author">
      <strong>{post.author || "Chưa có tên"}</strong>
      {post.authorEmail && post.authorEmail !== post.author && <small>{post.authorEmail}</small>}
      {post.authorId != null && <small>Tài khoản #{post.authorId}</small>}
      {post.ownerId != null && post.ownerId !== post.authorId && (
        <small>Đăng thay cho {post.owner} · #{post.ownerId}</small>
      )}
    </div>
  );
}
