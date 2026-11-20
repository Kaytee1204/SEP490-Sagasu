import { useEffect, useRef, useState } from 'react';
import {
  Plus,
  Search,
  RefreshCw,
  Newspaper,
  Pencil,
  Trash2,
  Eye,
  X,
  Upload,
  CheckCircle2,
  AlertCircle,
  Globe,
  FileEdit,
  Clock3,
  User,
} from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import { newsApi } from '@/features/news/api/newsApi';
import {
  NEWS_CATEGORIES,
  formatNewsFullDate,
  getNewsCategoryMeta,
} from '@/features/news/utils/newsUtils';

const STATUS_TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PUBLISHED', label: 'Đang hiển thị' },
  { key: 'DRAFT', label: 'Bản nháp' },
];

const EMPTY_FORM = {
  id: null,
  title: '',
  summary: '',
  content: '',
  category: 'UPDATE',
  published: true,
  existingCoverUrl: '',
  removeCoverImage: false,
};

export default function ApproverNewsPage() {
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [category, setCategory] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 1 });
  const [tabCounts, setTabCounts] = useState({});
  const [notice, setNotice] = useState(null);
  const [reloadTick, setReloadTick] = useState(0);
  const triggerReload = () => setReloadTick((n) => n + 1);

  // Modal editor state
  const [editorOpen, setEditorOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [coverFile, setCoverFile] = useState(null);
  const [coverPreviewUrl, setCoverPreviewUrl] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const fileInputRef = useRef(null);

  // Preview & delete confirm state
  const [previewArticle, setPreviewArticle] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Debounce keyword search
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    let active = true;
    Promise.all([
      newsApi.listForApprover({ page, size: 10, keyword, category, status }),
      ...STATUS_TABS.map((tab) =>
        newsApi.listForApprover({ page: 1, size: 1, keyword, category, status: tab.key }).catch(() => null),
      ),
    ])
      .then(([mainRes, ...countSettled]) => {
        if (!active) return;
        setData({
          content: mainRes?.content || [],
          totalElements: Number(mainRes?.totalElements ?? 0) || 0,
          totalPages: Math.max(1, Number(mainRes?.totalPages ?? 1) || 1),
        });
        const nextCounts = {};
        STATUS_TABS.forEach((tab, idx) => {
          const res = countSettled[idx];
          nextCounts[tab.key] = Number(res?.totalElements ?? 0) || 0;
        });
        setTabCounts(nextCounts);
        setError('');
        setLoading(false);
      })
      .catch((err) => {
        if (!active) return;
        setError(err?.message || 'Không tải được danh sách bài tin tức.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, keyword, category, status, reloadTick]);

  useEffect(() => {
    return () => {
      if (coverPreviewUrl && coverPreviewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(coverPreviewUrl);
      }
    };
  }, [coverPreviewUrl]);

  const openCreateModal = () => {
    setForm(EMPTY_FORM);
    setCoverFile(null);
    setCoverPreviewUrl('');
    setFormError('');
    setEditorOpen(true);
  };

  const openEditModal = (article) => {
    setForm({
      id: article.id,
      title: article.title || '',
      summary: article.summary || '',
      content: article.content || '',
      category: article.category || 'UPDATE',
      published: Boolean(article.published),
      existingCoverUrl: article.coverImageUrl || '',
      removeCoverImage: false,
    });
    setCoverFile(null);
    setCoverPreviewUrl('');
    setFormError('');
    setEditorOpen(true);
  };

  const closeEditor = () => {
    if (saving) return;
    setEditorOpen(false);
    setFormError('');
  };

  const handleFileChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setFormError('Vui lòng chọn tệp hình ảnh hợp lệ (JPG, PNG, WEBP).');
      return;
    }
    if (coverPreviewUrl && coverPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(coverPreviewUrl);
    }
    setFormError('');
    setCoverFile(file);
    setCoverPreviewUrl(URL.createObjectURL(file));
    setForm((prev) => ({ ...prev, removeCoverImage: false }));
  };

  const handleClearCover = () => {
    if (coverPreviewUrl && coverPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(coverPreviewUrl);
    }
    setCoverFile(null);
    setCoverPreviewUrl('');
    if (fileInputRef.current) fileInputRef.current.value = '';
    setForm((prev) => ({ ...prev, existingCoverUrl: '', removeCoverImage: true }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setFormError('');

    const title = form.title.trim();
    const summary = form.summary.trim();
    const content = form.content.trim();
    if (!title) {
      setFormError('Vui lòng nhập tiêu đề bài viết.');
      return;
    }
    if (!summary) {
      setFormError('Vui lòng nhập tóm tắt ngắn.');
      return;
    }
    if (!content) {
      setFormError('Vui lòng nhập nội dung chi tiết.');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        title,
        summary,
        content,
        category: form.category,
        published: form.published,
        removeCoverImage: form.removeCoverImage,
      };
      if (form.id) {
        await newsApi.update(form.id, payload, coverFile);
        setNotice({ type: 'success', text: 'Đã cập nhật bài tin tức thành công.' });
      } else {
        await newsApi.create(payload, coverFile);
        setNotice({ type: 'success', text: 'Đã tạo bài tin tức mới thành công.' });
      }
      setEditorOpen(false);
      window.dispatchEvent(new Event('sagasu-counts-changed'));
      triggerReload();
    } catch (err) {
      setFormError(err?.message || 'Lưu bài tin tức thất bại. Vui lòng thử lại.');
    } finally {
      setSaving(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await newsApi.remove(deleteTarget.id);
      setDeleteTarget(null);
      setNotice({ type: 'success', text: 'Đã xóa bài tin tức.' });
      window.dispatchEvent(new Event('sagasu-counts-changed'));
      triggerReload();
    } catch (err) {
      setNotice({ type: 'error', text: err?.message || 'Xóa bài tin tức thất bại.' });
    } finally {
      setDeleting(false);
    }
  };

  const activePreviewImage = coverPreviewUrl || form.existingCoverUrl;

  return (
    <div className="mx-auto w-full max-w-7xl space-y-6 p-4 sm:p-6 lg:p-8">
      {/* Header */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mb-1.5 text-sm font-bold tracking-wide text-teal-700">
            Văn phòng · Truyền thông hệ thống
          </p>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Tin tức hệ thống
          </h1>
          <p className="mt-1.5 max-w-2xl text-sm leading-6 text-slate-600">
            Soạn thảo và đăng tải thông báo, cập nhật tính năng hoặc hướng dẫn sử dụng hiển thị trực tiếp trên Trang chủ Sagasu.
          </p>
        </div>

        <button
          type="button"
          onClick={openCreateModal}
          className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-800"
        >
          <Plus size={18} />
          Tạo bài tin tức
        </button>
      </div>

      {/* Feedback Notice */}
      {notice && (
        <div
          role="status"
          className={`flex items-center justify-between gap-3 rounded-xl border px-4 py-3 text-sm font-medium ${
            notice.type === 'error'
              ? 'border-rose-200 bg-rose-50 text-rose-700'
              : 'border-emerald-200 bg-emerald-50 text-emerald-800'
          }`}
        >
          <span className="inline-flex items-center gap-2">
            {notice.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
            {notice.text}
          </span>
          <button
            type="button"
            onClick={() => setNotice(null)}
            className="rounded-md p-1 hover:bg-black/5"
            aria-label="Đóng thông báo"
          >
            <X size={15} />
          </button>
        </div>
      )}

      {/* Main Panel */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {/* Toolbar */}
        <div className="space-y-4 border-b border-slate-100 p-4 sm:p-5">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[240px] sm:max-w-md">
              <Search size={17} className="absolute left-3.5 top-3 text-slate-500" />
              <input
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                maxLength={200}
                aria-label="Tìm bài tin tức"
                placeholder="Tìm tiêu đề hoặc nội dung tóm tắt…"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
              />
            </div>

            <select
              value={category}
              onChange={(e) => {
                setCategory(e.target.value);
                setPage(1);
              }}
              aria-label="Lọc danh mục"
              className="rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm font-medium text-slate-700 outline-none focus:border-teal-500"
            >
              <option value="ALL">Tất cả danh mục</option>
              {NEWS_CATEGORIES.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </select>

            <button
              type="button"
              disabled={loading}
              onClick={() => {
                setLoading(true);
                triggerReload();
              }}
              aria-label="Tải lại danh sách"
              className="rounded-xl border border-slate-200 p-2.5 text-slate-600 hover:bg-slate-50 disabled:opacity-40"
            >
              <RefreshCw size={17} className={loading ? 'animate-spin' : ''} />
            </button>
          </div>

          <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc trạng thái xuất bản">
            {STATUS_TABS.map((tab) => {
              const active = status === tab.key;
              const count = tabCounts[tab.key];
              return (
                <button
                  key={tab.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => {
                    setStatus(tab.key);
                    setPage(1);
                  }}
                  className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold transition ${
                    active
                      ? 'bg-teal-700 text-white'
                      : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  <span>{tab.label}</span>
                  {typeof count === 'number' && (
                    <span
                      className={`inline-flex min-w-5 items-center justify-center rounded-full px-1.5 py-0.5 text-xs font-bold leading-none ${
                        active
                          ? 'bg-white/20 text-white'
                          : tab.key === 'DRAFT' && count > 0
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-slate-200/80 text-slate-700'
                      }`}
                    >
                      {count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* Table / Empty / Loading */}
        {loading ? (
          <div className="p-12 text-center text-sm text-slate-500">Đang tải danh sách bài tin tức…</div>
        ) : error ? (
          <div className="p-8 text-center text-sm text-rose-600">
            <p>{error}</p>
            <button
              type="button"
              onClick={() => {
                setLoading(true);
                triggerReload();
              }}
              className="mt-3 rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Thử lại
            </button>
          </div>
        ) : data.content.length === 0 ? (
          <div className="flex flex-col items-center px-6 py-16 text-center">
            <div className="mb-4 rounded-2xl bg-teal-50 p-4 text-teal-600">
              <Newspaper size={32} strokeWidth={1.6} />
            </div>
            <h2 className="text-base font-bold text-slate-800">Chưa có bài tin tức phù hợp</h2>
            <p className="mt-1.5 max-w-sm text-sm text-slate-500">
              Tạo bài viết mới để chia sẻ cập nhật tính năng, quy trình hoặc thông báo mới trên Trang chủ.
            </p>
            <button
              type="button"
              onClick={openCreateModal}
              className="mt-4 inline-flex items-center gap-1.5 text-sm font-bold text-teal-700 hover:underline"
            >
              <Plus size={16} /> Tạo bài tin tức mới
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-100 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3.5">Bài viết</th>
                  <th className="px-4 py-3.5">Danh mục</th>
                  <th className="px-4 py-3.5">Trạng thái</th>
                  <th className="px-4 py-3.5">Cập nhật</th>
                  <th className="px-5 py-3.5 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((article) => {
                  const meta = getNewsCategoryMeta(article.category);
                  return (
                    <tr key={article.id} className="hover:bg-slate-50/60">
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3.5">
                          {article.coverImageUrl ? (
                            <img
                              src={article.coverImageUrl}
                              alt=""
                              className="h-14 w-20 shrink-0 rounded-xl border border-slate-200 object-cover"
                            />
                          ) : (
                            <div
                              className="flex h-14 w-20 shrink-0 items-center justify-center rounded-xl border border-slate-200/70"
                              style={{ background: meta.gradient, color: meta.accent }}
                            >
                              <Newspaper size={20} />
                            </div>
                          )}
                          <div className="min-w-0 max-w-md">
                            <p className="truncate font-semibold text-slate-900">{article.title}</p>
                            <p className="mt-0.5 line-clamp-2 text-xs leading-5 text-slate-500">
                              {article.summary}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-4">
                        <span
                          className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold"
                          style={{ background: meta.softBg, color: meta.accent }}
                        >
                          {article.categoryLabel || meta.label}
                        </span>
                      </td>
                      <td className="px-4 py-4">
                        {article.published ? (
                          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                            <Globe size={12} /> Đang hiển thị
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
                            <FileEdit size={12} /> Bản nháp
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-4 text-xs text-slate-500">
                        <div>{formatNewsFullDate(article.updatedAt || article.createdAt)}</div>
                        <div className="mt-0.5 text-slate-400">{article.authorName}</div>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            type="button"
                            onClick={() => setPreviewArticle(article)}
                            title="Xem trước"
                            className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-900"
                          >
                            <Eye size={16} />
                          </button>
                          <button
                            type="button"
                            onClick={() => openEditModal(article)}
                            title="Chỉnh sửa"
                            className="rounded-lg p-2 text-slate-500 hover:bg-teal-50 hover:text-teal-700"
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeleteTarget(article)}
                            title="Xóa bài"
                            className="rounded-lg p-2 text-slate-500 hover:bg-rose-50 hover:text-rose-600"
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Pagination */}
      {!loading && !error && data.totalElements > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-4 px-1">
          <p className="text-sm text-slate-500">
            Hiển thị {(page - 1) * 10 + 1}–{Math.min(page * 10, data.totalElements)} trong {data.totalElements} bài viết
          </p>
          <Pagination currentPage={page} totalPages={data.totalPages} onPageChange={setPage} />
        </div>
      )}

      {/* Modal: Create / Edit News */}
      {editorOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="news-editor-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeEditor();
          }}
        >
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl">
            <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-4">
              <div>
                <h2 id="news-editor-title" className="text-lg font-bold text-slate-900">
                  {form.id ? 'Chỉnh sửa bài tin tức' : 'Tạo bài tin tức mới'}
                </h2>
                <p className="mt-0.5 text-xs text-slate-500">
                  Bài viết khi bật hiển thị công khai sẽ xuất hiện ngay trong mục Tin tức mới nhất trên Trang chủ.
                </p>
              </div>
              <button
                type="button"
                onClick={closeEditor}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                aria-label="Đóng"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4 p-6">
              {formError && (
                <div
                  role="alert"
                  className="flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2.5 text-sm text-rose-700"
                >
                  <AlertCircle size={16} />
                  <span>{formError}</span>
                </div>
              )}

              <div>
                <label htmlFor="news-title" className="mb-1.5 block text-xs font-bold text-slate-700">
                  Tiêu đề bài viết <span className="text-rose-500">*</span>
                </label>
                <input
                  id="news-title"
                  type="text"
                  maxLength={200}
                  value={form.title}
                  onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                  placeholder="VD: Nâng cấp tính năng AI so khớp đồ thất lạc"
                  className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
                />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label htmlFor="news-category" className="mb-1.5 block text-xs font-bold text-slate-700">
                    Danh mục <span className="text-rose-500">*</span>
                  </label>
                  <select
                    id="news-category"
                    value={form.category}
                    onChange={(e) => setForm((p) => ({ ...p, category: e.target.value }))}
                    className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
                  >
                    {NEWS_CATEGORIES.map((c) => (
                      <option key={c.key} value={c.key}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="flex items-end pb-1">
                  <label className="inline-flex cursor-pointer items-center gap-2.5 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm font-medium text-slate-700">
                    <input
                      type="checkbox"
                      checked={form.published}
                      onChange={(e) => setForm((p) => ({ ...p, published: e.target.checked }))}
                      className="h-4 w-4 accent-teal-700"
                    />
                    <span>Hiển thị công khai trên Trang chủ</span>
                  </label>
                </div>
              </div>

              <div>
                <label htmlFor="news-summary" className="mb-1.5 block text-xs font-bold text-slate-700">
                  Tóm tắt ngắn <span className="text-rose-500">*</span>
                </label>
                <textarea
                  id="news-summary"
                  rows={2}
                  maxLength={500}
                  value={form.summary}
                  onChange={(e) => setForm((p) => ({ ...p, summary: e.target.value }))}
                  placeholder="Mô tả ngắn gọn nội dung chính hiển thị trên thẻ bài viết…"
                  className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
                />
              </div>

              <div>
                <label htmlFor="news-content" className="mb-1.5 block text-xs font-bold text-slate-700">
                  Nội dung chi tiết <span className="text-rose-500">*</span>
                </label>
                <textarea
                  id="news-content"
                  rows={6}
                  value={form.content}
                  onChange={(e) => setForm((p) => ({ ...p, content: e.target.value }))}
                  placeholder="Nhập nội dung đầy đủ của bài viết. Bạn có thể xuống dòng để chia đoạn rõ ràng…"
                  className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm leading-6 outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100"
                />
              </div>

              <div>
                <span className="mb-1.5 block text-xs font-bold text-slate-700">
                  Ảnh bìa bài viết (tùy chọn)
                </span>
                {activePreviewImage ? (
                  <div className="relative overflow-hidden rounded-xl border border-slate-200">
                    <img
                      src={activePreviewImage}
                      alt="Xem trước ảnh bìa"
                      className="h-44 w-full object-cover"
                    />
                    <button
                      type="button"
                      onClick={handleClearCover}
                      className="absolute right-2.5 top-2.5 inline-flex items-center gap-1 rounded-full bg-slate-900/75 px-3 py-1 text-xs font-semibold text-white hover:bg-slate-900"
                    >
                      <X size={13} /> Gỡ ảnh
                    </button>
                  </div>
                ) : (
                  <label className="flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center transition hover:border-teal-500 hover:bg-teal-50/40">
                    <Upload size={20} className="text-slate-500" />
                    <span className="text-sm font-semibold text-slate-700">Chọn ảnh bìa từ máy tính</span>
                    <span className="text-xs text-slate-500">
                      JPG, PNG, WEBP. Nếu bỏ trống, hệ thống sẽ dùng nền đồ họa theo danh mục.
                    </span>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      onChange={handleFileChange}
                      className="hidden"
                    />
                  </label>
                )}
              </div>

              <div className="flex items-center justify-end gap-2.5 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  onClick={closeEditor}
                  disabled={saving}
                  className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
                >
                  {saving ? 'Đang lưu…' : form.id ? 'Lưu thay đổi' : 'Đăng bài tin tức'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Preview */}
      {previewArticle && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          onClick={(e) => {
            if (e.target === e.currentTarget) setPreviewArticle(null);
          }}
        >
          <article className="relative max-h-[88vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <button
              type="button"
              onClick={() => setPreviewArticle(null)}
              aria-label="Đóng xem trước"
              className="absolute right-3.5 top-3.5 z-10 rounded-full bg-white/90 p-2 text-slate-700 shadow hover:bg-white"
            >
              <X size={18} />
            </button>
            {previewArticle.coverImageUrl ? (
              <img
                src={previewArticle.coverImageUrl}
                alt={previewArticle.title}
                className="h-56 w-full object-cover"
              />
            ) : (
              <div
                className="flex h-44 w-full items-center justify-center"
                style={{
                  background: getNewsCategoryMeta(previewArticle.category).gradient,
                  color: getNewsCategoryMeta(previewArticle.category).accent,
                }}
              >
                <Newspaper size={44} />
              </div>
            )}
            <div className="space-y-3 p-6">
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
                <span
                  className="rounded-full px-2.5 py-1 font-bold"
                  style={{
                    background: getNewsCategoryMeta(previewArticle.category).softBg,
                    color: getNewsCategoryMeta(previewArticle.category).accent,
                  }}
                >
                  {previewArticle.categoryLabel || getNewsCategoryMeta(previewArticle.category).label}
                </span>
                <span className="inline-flex items-center gap-1">
                  <User size={13} /> {previewArticle.authorName}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Clock3 size={13} /> {formatNewsFullDate(previewArticle.createdAt)}
                </span>
              </div>
              <h2 className="text-xl font-extrabold text-slate-900">{previewArticle.title}</h2>
              <p className="rounded-xl border-l-4 border-teal-600 bg-slate-50 px-3.5 py-2.5 text-sm font-medium text-slate-700">
                {previewArticle.summary}
              </p>
              <div className="space-y-2.5 text-sm leading-7 text-slate-700">
                {String(previewArticle.content || '')
                  .split(/\n+/)
                  .filter((p) => p.trim())
                  .map((paragraph, idx) => (
                    <p key={idx}>{paragraph}</p>
                  ))}
              </div>
            </div>
          </article>
        </div>
      )}

      {/* Modal: Delete Confirm */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          onClick={(e) => {
            if (e.target === e.currentTarget && !deleting) setDeleteTarget(null);
          }}
        >
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-slate-900">Xóa bài tin tức?</h3>
            <p className="mt-2 text-sm text-slate-600">
              Bài viết <strong>{deleteTarget.title}</strong> sẽ bị xóa vĩnh viễn và không còn hiển thị trên Trang chủ.
            </p>
            <div className="mt-5 flex justify-end gap-2.5">
              <button
                type="button"
                disabled={deleting}
                onClick={() => setDeleteTarget(null)}
                className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                Hủy
              </button>
              <button
                type="button"
                disabled={deleting}
                onClick={handleConfirmDelete}
                className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-50"
              >
                {deleting ? 'Đang xóa…' : 'Xác nhận xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
