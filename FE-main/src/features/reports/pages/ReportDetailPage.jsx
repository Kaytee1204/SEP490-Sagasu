import '@/styles/Workspace.css';
import { useEffect, useState } from 'react';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { claimDetailPath } from '@/features/claims/utils/claimUtils';
import useLoad from '@/features/office/hooks/useLoad';
import { getReportWorkspaceDetail } from '../api/reportApi';
import FinderAppealAction from '../components/FinderAppealAction';
import ReportInquiryResponse from '../components/ReportInquiryResponse';
import ReportProgress from '../components/ReportProgress';
import { reportStatuses, reportTitles } from '../constants/reportConstants';
import { formatReportDate, reportsPath, reportSubject } from '../utils/reportUtils';
import '../styles/Reports.css';

const outcomeLabels = {
  VIOLATION: 'Có vi phạm',
  NO_VIOLATION: 'Không phát hiện vi phạm',
  INSUFFICIENT_EVIDENCE: 'Chưa đủ căn cứ',
  APPEAL_KEEP: 'Giữ kết quả nhận đồ',
  APPEAL_REQUIRE_REVIEW: 'Cần xét lại người nhận',
  HANDOVER_COMPLETED: 'Xác nhận đã trả đồ',
  HANDOVER_CONTINUE: 'Cho tiếp tục bàn giao',
  HANDOVER_RETRY: 'Hủy lượt giao và hẹn giao lại',
  HANDOVER_REOPEN: 'Thu hồi chấp thuận để xét lại',
  CLAIM_REJECTED: 'Hủy bàn giao và mở lại xét duyệt',
};

export default function ReportDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  return /^[1-9]\d*$/.test(id)
    ? <ReportDetail key={`${user.id}:${id}`} id={id} />
    : <main className="container reports-page"><BackLink to={reportsPath} /><p role="alert">Mã báo cáo không hợp lệ.</p></main>;
}

function BackLink({ to }) {
  return <Link className="report-back" to={to}><ArrowLeft size={17} aria-hidden="true" /> Về danh sách báo cáo</Link>;
}

function ReportDetail({ id }) {
  const location = useLocation();
  const { data, error, loading, reload } = useLoad(() => getReportWorkspaceDetail(id), id);
  const [image, setImage] = useState(null);
  const [replySaved, setReplySaved] = useState(false);

  useEffect(() => {
    const hash = location.hash;
    if (!data || (hash !== '#phan-hoi' && hash !== '#ket-qua')) return;
    const frame = requestAnimationFrame(() => {
      const section = document.getElementById(hash.slice(1));
      section?.focus({ preventScroll: true });
      section?.scrollIntoView({ block: 'start' });
    });
    return () => cancelAnimationFrame(frame);
  }, [data, location.hash]);

  const { report, submission, inquiries = [] } = data ?? {};
  const returnTo = location.state?.returnTo || (report?.mine === false ? `${reportsPath}?tab=related` : reportsPath);
  const evidence = (submission?.evidence ?? []).map(item => ({ ...item, name: item.name || 'Ảnh bằng chứng' }));
  const subject = report && reportSubject(report);
  const isAppeal = report?.report_type === 'CLAIM_APPEAL';
  const concluded = report && ['RESOLVED', 'REJECTED'].includes(report.status);
  const pending = report?.needs_reply === true;
  const claimPath = isAppeal && report.finder_approved_claim_id && report.report_subject_id
    ? claimDetailPath(report.report_subject_id, report.finder_approved_claim_id) : null;

  function afterReply() {
    setReplySaved(true);
    window.dispatchEvent(new Event('sagasu-counts-changed'));
    reload();
  }

  return (
    <main className="container reports-page report-detail-page">
      <BackLink to={returnTo} />
      {loading && <p className="reports-count" role="status">Đang tải chi tiết báo cáo…</p>}
      {error && <div className="report-card">
        <p className="report-error" role="alert">{error}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>Thử tải lại</button>
      </div>}
      {report && <>
        <header className="report-card report-detail-overview">
          <div className="report-detail-heading">
            <div>
              <p className="report-detail-reference">Báo cáo #{report.report_id} · {report.mine ? 'Bạn đã gửi' : 'Liên quan đến bạn'}</p>
              <h1>{reportTitles[report.report_type] || 'Chi tiết báo cáo'}</h1>
            </div>
            <span className={`report-state report-state-${report.status}`}>{reportStatuses[report.status]}</span>
          </div>
          {subject.value && <p className="report-detail-subject">{subject.label} <span className="report-meta-dot">·</span> <strong>{subject.value}</strong></p>}
          <time dateTime={report.created_at}>Gửi lúc {formatReportDate(report.created_at)}</time>
          <ReportProgress status={report.status} appeal={isAppeal} />
        </header>
        {report.recovery_note && <section className="report-card report-detail-section">
          <h2>{report.recovery_confirmed_at ? 'Đã xác nhận thu hồi món đồ' : 'Yêu cầu thu hồi món đồ'}</h2>
          <p>{report.recovery_note}</p>
          {report.recovery_holder_name && <p>Người được yêu cầu phản hồi · {report.recovery_holder_name}</p>}
          {report.recovery_due_at && !report.recovery_confirmed_at && <p>Hạn phản hồi · {formatReportDate(report.recovery_due_at)}</p>}
        </section>}
        {inquiries.length > 0 && <section id="phan-hoi" className="report-card report-detail-section" tabIndex={-1}>
          <div className="report-detail-heading">
            <h2>Trao đổi với quản lý</h2>
            {pending && <span className="report-state report-state-ACTION">Cần bạn phản hồi</span>}
          </div>
          {replySaved && <p className="report-reply-success" role="status">Đã gửi thông tin bổ sung.</p>}
          {inquiries.map(inquiry => <ReportInquiryResponse key={inquiry.inquiry_id} inquiry={inquiry} canReply={pending} onSaved={afterReply} />)}
        </section>}
        {concluded && <section id="ket-qua" className="report-card report-detail-section" tabIndex={-1}>
          <div className="report-detail-heading">
            <h2>Kết quả xử lý</h2>
            {report.resolved_at && <time dateTime={report.resolved_at}>Xử lý lúc {formatReportDate(report.resolved_at)}</time>}
          </div>
          <div className="report-note">
            {outcomeLabels[report.outcome] && <strong>{outcomeLabels[report.outcome]}</strong>}
            <p>{report.resolution_note || submission?.resolutionNote || 'Chưa có ghi chú kết luận.'}</p>
          </div>
          {claimPath && report.needs_reopen && <FinderAppealAction report={report} claimPath={claimPath} />}
          {claimPath && !report.needs_reopen && <HandoverLink claimPath={claimPath} status={report.status} />}
        </section>}
        {!concluded && claimPath && <section className="report-card report-detail-section">
          <h2>Bàn giao món đồ</h2>
          <HandoverLink claimPath={claimPath} status={report.status} />
        </section>}
        {(submission || report.report_reason) && <section className="report-card report-detail-section">
          <h2>{isAppeal ? 'Nội dung kháng cáo' : 'Nội dung báo cáo'}</h2>
          <dl className="report-detail-fields">
            <div><dt>{isAppeal ? 'Lý do kháng cáo' : 'Lý do báo cáo'}</dt><dd>{report.report_reason || 'Chưa phân loại (báo cáo cũ)'}</dd></div>
            {submission?.description && <div><dt>Mô tả</dt><dd className="report-description">{submission.description}</dd></div>}
          </dl>
          {evidence.length > 0 && <div className="report-evidence">
            {evidence.map((item, index) => <button type="button" key={item.id} onClick={() => setImage(index)} aria-label={`Xem ${item.name}`}>
              <img src={item.url} alt={item.name} loading="lazy" />
            </button>)}
          </div>}
        </section>}
        {submission?.activities?.length > 0 && <details className="report-card report-history">
          <summary>Lịch sử xử lý</summary>
          {submission.activities.map((activity, index) => <p key={index}>
            {formatReportDate(activity.at)} · {reportStatuses[activity.status]}{activity.note && ` · ${activity.note}`}
          </p>)}
        </details>}
        {image !== null && <ClaimEvidencePreview images={evidence} initialIndex={image} onClose={() => setImage(null)} />}
      </>}
    </main>
  );
}

function HandoverLink({ claimPath, status }) {
  const guide = status === 'RESOLVED' ? 'Kiểm tra tiến trình để biết khi nào có thể xét lại người nhận.'
    : status === 'REJECTED' ? 'Kiểm tra tiến trình để biết khi nào có thể bàn giao.'
      : 'Giữ món đồ trong khi quản lý xử lý kháng cáo.';
  return <div className="report-appeal-next">
    <p>{guide}</p>
    <Link className="btn btn-ghost" to={`${claimPath}#handover`}>Theo dõi bàn giao <ArrowRight size={15} aria-hidden="true" /></Link>
  </div>;
}
