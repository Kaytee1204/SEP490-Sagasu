import { useEffect, useMemo, useState } from 'react';
import { approverApi } from '../api/approverApi';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ArrowUpDown,
  Calendar,
  Filter,
  Inbox,
  MapPin,
  Plus,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Tags,
} from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import { useIntakeReceipts } from '@/features/receipt/hooks/useIntakeReceipts';
import ReceiptStatusBadge from '@/features/receipt/components/ReceiptStatusBadge';
import { ReceiptImage } from '@/features/receipt/components/ReceiptImagePicker';
import { ReceiptError, ReceiptLoading } from '@/features/receipt/components/ReceiptFeedback';
import { receiptDate } from '@/features/receipt/receiptUtils';
import { TIME_RANGE_OPTIONS, matchesTimeFilter } from '@/features/claims/utils/claimUtils';
import '@/features/office/StaffWorkspace.css';

const RECEIPT_FILTER_TABS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'DRAFT', label: 'Bản nháp', tone: 'warning' },
  { value: 'IN_STORAGE', label: 'Đã tiếp nhận' },
  { value: 'RETURNED', label: 'Đã trao trả' },
  { value: 'REJECTED', label: 'Từ chối' },
];

export default function ReceiptQueuePage() {
  const data = useIntakeReceipts(approverApi);
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [storageFilter, setStorageFilter] = useState('ALL');
  const [timeRange, setTimeRange] = useState('ALL');
  const [sortOrder, setSortOrder] = useState('NEWEST');
  const [tabCounts, setTabCounts] = useState({});

  useEffect(() => {
    let active = true;
    Promise.allSettled(
      RECEIPT_FILTER_TABS.map(({ value }) =>
        approverApi.list({ page: 1, keyword: data.filters.keyword, filter: value }),
      ),
    ).then((settled) => {
      if (!active) return;
      const next = {};
      RECEIPT_FILTER_TABS.forEach(({ value }, idx) => {
        const item = settled[idx];
        next[value] = item?.status === 'fulfilled' ? Number(item.value?.totalElements ?? 0) : 0;
      });
      setTabCounts(next);
    });
    return () => {
      active = false;
    };
  }, [data.filters.keyword, data.totalElements]);

  const categoryOptions = useMemo(() => {
    const names = new Set();
    (data.content || []).forEach((r) => {
      if (r.categoryName) names.add(r.categoryName);
    });
    return Array.from(names).sort((a, b) => a.localeCompare(b, 'vi'));
  }, [data.content]);

  const displayedContent = useMemo(() => {
    const filtered = (data.content || []).filter((r) => {
      if (categoryFilter !== 'ALL' && r.categoryName !== categoryFilter) return false;
      if (storageFilter === 'ASSIGNED' && !r.storageLocationName) return false;
      if (storageFilter === 'UNASSIGNED' && Boolean(r.storageLocationName)) return false;
      if (timeRange !== 'ALL' && !matchesTimeFilter(r.recordedAt || r.foundAt, timeRange, '', '')) {
        return false;
      }
      return true;
    });
    const sorted = [...filtered];
    sorted.sort((a, b) => {
      const diff =
        new Date(a.recordedAt || a.foundAt || 0).getTime() -
        new Date(b.recordedAt || b.foundAt || 0).getTime();
      return sortOrder === 'OLDEST' ? diff : -diff;
    });
    return sorted;
  }, [data.content, categoryFilter, storageFilter, timeRange, sortOrder]);

  const hasActiveFilter =
    Boolean(data.filters?.keyword?.trim?.()) ||
    data.filters.filter !== 'ALL' ||
    categoryFilter !== 'ALL' ||
    storageFilter !== 'ALL' ||
    timeRange !== 'ALL' ||
    sortOrder !== 'NEWEST';

  const [showExtraFilters, setShowExtraFilters] = useState(false);
  const extraActiveCount = (categoryFilter !== 'ALL' ? 1 : 0) + (storageFilter !== 'ALL' ? 1 : 0);
  const isExtraOpen = showExtraFilters || extraActiveCount > 0;

  const resetAllFilters = () => {
    data.reset();
    setCategoryFilter('ALL');
    setStorageFilter('ALL');
    setTimeRange('ALL');
    setSortOrder('NEWEST');
    setShowExtraFilters(false);
  };

  return (
    <div className="sw-page">
      <header className="sw-heading">
        <h1>Phiếu tiếp nhận tài sản</h1>
        <p>Tra cứu phiếu tiếp nhận và lập phiếu tại quầy cho tài sản chưa có bài đăng.</p>
      </header>

      {/* Card 1: Filter Controls */}
      <section className="sw-controls" aria-label="Bộ lọc hồ sơ tiếp nhận">
        <div className="sw-controls-top">
          <label className="sw-search">
            <Search size={17} aria-hidden="true" />
            <input
              type="search"
              value={data.filters.keyword}
              onChange={(e) => data.change('keyword', e.target.value)}
              maxLength={200}
              aria-label="Tìm hồ sơ tiếp nhận"
              placeholder="Tìm tên đồ vật, mã hồ sơ hoặc địa điểm…"
            />
          </label>

          <Link to="/approver/receipts/new" className="sw-primary-btn">
            <Plus size={16} aria-hidden="true" />
            Tạo phiếu tiếp nhận
          </Link>
        </div>

        <div className="sw-filter-row">
          <div className="sw-status-group">
            <span className="sw-filter-label">Trạng thái</span>
            <div className="sw-status-pills" role="group" aria-label="Lọc trạng thái hồ sơ">
              {RECEIPT_FILTER_TABS.map((tab) => {
                const count = tabCounts[tab.value];
                const activeTab = data.filters.filter === tab.value;
                const isWarn = tab.tone === 'warning' && typeof count === 'number' && count > 0;
                return (
                  <button
                    key={tab.value}
                    type="button"
                    aria-pressed={activeTab}
                    onClick={() => data.change('filter', tab.value)}
                    className={`${activeTab ? 'is-active' : ''} ${isWarn ? 'is-warning' : ''}`.trim()}
                  >
                    {tab.label}
                    {typeof count === 'number' ? ` (${count})` : ''}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="sw-dropdowns">
            <div className="sw-field">
              <label htmlFor="sw-receipt-time">
                <Calendar size={13} aria-hidden="true" />
                Thời gian
              </label>
              <select
                id="sw-receipt-time"
                value={timeRange}
                onChange={(e) => setTimeRange(e.target.value)}
              >
                {TIME_RANGE_OPTIONS.filter((o) => o.value !== 'CUSTOM').map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="sw-field">
              <label htmlFor="sw-receipt-sort">
                <ArrowUpDown size={13} aria-hidden="true" />
                Sắp xếp
              </label>
              <select
                id="sw-receipt-sort"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
              >
                <option value="NEWEST">Mới nhất trước</option>
                <option value="OLDEST">Cũ nhất trước</option>
              </select>
            </div>

            <button
              type="button"
              className={`sw-extra-toggle-btn ${isExtraOpen ? 'is-active' : ''}`}
              onClick={() => setShowExtraFilters((v) => !v)}
              title="Bộ lọc nâng cao"
              aria-expanded={isExtraOpen}
            >
              <SlidersHorizontal size={13} aria-hidden="true" />
              Lọc thêm {extraActiveCount > 0 ? `(${extraActiveCount})` : ''}
            </button>

            {hasActiveFilter && (
              <button type="button" className="sw-reset-btn" onClick={resetAllFilters}>
                <RotateCcw size={13} aria-hidden="true" />
                Đặt lại
              </button>
            )}
          </div>
        </div>

        {isExtraOpen && (
          <div className="sw-extra-filter-bar" role="group" aria-label="Bộ lọc nâng cao">
            <div className="sw-field">
              <label htmlFor="sw-receipt-cat">
                <Tags size={13} aria-hidden="true" />
                Danh mục
              </label>
              <select
                id="sw-receipt-cat"
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
              >
                <option value="ALL">Tất cả danh mục</option>
                {categoryOptions.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            <div className="sw-field">
              <label htmlFor="sw-receipt-shelf">
                <Filter size={13} aria-hidden="true" />
                Vị trí kho
              </label>
              <select
                id="sw-receipt-shelf"
                value={storageFilter}
                onChange={(e) => setStorageFilter(e.target.value)}
              >
                <option value="ALL">Mọi tình trạng kệ</option>
                <option value="ASSIGNED">Đã gán vị trí</option>
                <option value="UNASSIGNED">Chưa gán vị trí</option>
              </select>
            </div>
          </div>
        )}
      </section>

      {/* Card 2: Table / Empty */}
      {data.loading ? (
        <div className="sw-empty-card">
          <ReceiptLoading />
        </div>
      ) : data.error ? (
        <div className="sw-empty-card">
          <ReceiptError message={data.error} onRetry={data.reload} />
        </div>
      ) : !displayedContent.length ? (
        <div className="sw-empty-card">
          <Inbox size={32} strokeWidth={1.6} aria-hidden="true" />
          <h3>{hasActiveFilter ? 'Không tìm thấy hồ sơ phù hợp' : 'Chưa có hồ sơ tiếp nhận nào'}</h3>
          <p>
            {hasActiveFilter
              ? 'Thử đổi từ khóa hoặc đặt lại bộ lọc để xem toàn bộ danh sách.'
              : 'Khi lập hồ sơ tiếp nhận tài sản mang đến văn phòng, hồ sơ sẽ hiển thị tại đây.'}
          </p>
        </div>
      ) : (
        <>
          <div className="sw-results-bar">
            <h2>Danh sách biên bản tiếp nhận</h2>
            <span className="sw-results-count">
              {displayedContent.length} / {data.totalElements} phiếu
            </span>
          </div>

          <div className="sw-table-card">
            <div className="sw-table-head sw-grid-receipts" aria-hidden="true">
              <span>Hồ sơ đồ vật</span>
              <span>Nơi & thời gian nhặt</span>
              <span>Vị trí lưu kho</span>
              <span>Trạng thái</span>
              <span style={{ textAlign: 'right' }}>Thao tác</span>
            </div>

            <div className="sw-table-body">
              {displayedContent.map((receipt) => (
                <article key={receipt.id} className="sw-table-row sw-grid-receipts">
                  <div className="sw-item-cell">
                    <div className="sw-thumb">
                      <ReceiptImage
                        key={receipt.thumbnailUrl}
                        src={receipt.thumbnailUrl}
                        alt=""
                        className="h-full w-full object-cover"
                      />
                    </div>
                    <div className="sw-item-text">
                      <Link className="sw-item-title" to={`/approver/receipts/${receipt.id}`}>
                        {receipt.title}
                      </Link>
                      <div className="sw-item-sub">
                        <span className="sw-code-tag">
                          {receipt.receiptCode || `#${receipt.id}`}
                        </span>
                        <span>{receipt.categoryName}</span>
                      </div>
                    </div>
                  </div>

                  <div className="sw-meta-cell">
                    <strong title={receipt.foundLocation}>{receipt.foundLocation}</strong>
                    <small>{receiptDate(receipt.foundAt)}</small>
                  </div>

                  <div>
                    <span
                      className={`sw-shelf-tag ${!receipt.storageLocationName ? 'is-missing' : ''}`}
                      title={receipt.storageLocationName || 'Chưa gán vị trí kho'}
                    >
                      <MapPin size={13} aria-hidden="true" />
                      <span>{receipt.storageLocationName || 'Chưa gán kệ'}</span>
                    </span>
                  </div>

                  <div>
                    <ReceiptStatusBadge receipt={receipt} />
                  </div>

                  <div className="sw-action-cell">
                    <Link
                      to={`/approver/receipts/${receipt.id}`}
                      className={`sw-action-pill ${receipt.canProcess ? 'is-solid' : 'is-default'}`}
                    >
                      {receipt.canProcess ? 'Xem & xác nhận' : 'Xem phiếu'}
                      <ArrowRight size={14} aria-hidden="true" />
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          </div>

          <div className="sw-pagination">
            <span>
              Trang {data.filters.page} / {data.totalPages} · Tổng {data.totalElements} phiếu
            </span>
            <Pagination
              currentPage={data.filters.page}
              totalPages={data.totalPages}
              onPageChange={data.setPage}
            />
          </div>
        </>
      )}
    </div>
  );
}
