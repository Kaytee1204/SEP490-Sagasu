import '@/styles/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { claimDetailPath, postClaimsPath } from '@/features/claims/utils/claimUtils';
import useReportResource from '../hooks/useReportResource';
import { getReportWorkspaceDetail } from '../api/reportApi';
import FinderAppealAction from '../components/FinderAppealAction';
import ReportInquiryResponse from '../components/ReportInquiryResponse';
import ReportProgress from '../components/ReportProgress';
import { reportStatuses, reportTitles } from '../constants/reportConstants';
import { appealInstruction, appealSteps, formatReportDate, reportsPath, reportSubject } from '../utils/reportUtils';
import '../styles/Reports.css';

const outcomeLabels = {
  VIOLATION: 'Có vi phạm',
  NO_VIOLATION: 'Không phát hiện vi phạm',
  INSUFFICIENT_EVIDENCE: 'Chưa đủ căn cứ',
  APPEAL_KEEP: 'Giữ kết quả nhận đồ',
  APPEAL_ROUND_CLOSED: "Đã mở lại xét duyệt người nhận",
  APPEAL_WITHDRAWN: "Người gửi đã rút tranh chấp",
  APPEAL_INSUFFICIENT: 'Giữ người đã duyệt vì chưa đủ căn cứ thay đổi',
  APPEAL_REJECT_CLAIM: 'Yêu cầu bị bác và không thể gửi lại trên bài đăng này',
  APPEAL_REQUIRE_REVIEW: 'Quản lý đã yêu cầu xét lại người nhận',
  APPEAL_WRONG_RECIPIENT: 'Quản lý xác nhận đã duyệt nhầm người nhận',
  HANDOVER_CORRECTION: 'Đang khắc phục bàn giao',
  HANDOVER_UNVERIFIED: 'Đã đóng sự cố · Chưa xác minh được',
  HANDOVER_VERIFY_HOLDER: 'Quản lý đang xác minh người giữ đồ',
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
  const { data, error, loading, reload } = useReportResource(id, () => getReportWorkspaceDetail(id));
  const [image, setImage] = useState(null);
  const [replySaved, setReplySaved] = useState(false);

  const lastTarget = useRef(null);
  useEffect(() => {
    const hash = location.hash;
    if (lastTarget.current === hash || !data) return;
    if (!hash) { lastTarget.current = null; return; }
    const frame = requestAnimationFrame(() => {
      const section = hash === '#phan-hoi'
        ? document.querySelector('.report-inquiry[data-pending="true"]') || document.getElementById('phan-hoi')
        : ['#ket-qua', '#buoc-tiep-theo'].includes(hash) ? document.getElementById(hash.slice(1)) : null;
      if (section) {
        lastTarget.current = hash;
        if (hash === '#phan-hoi' && !section.matches('[data-pending="true"]')) {
          const previous = section.querySelector('details');
          if (previous) previous.open = true;
        }
      }
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
  const awaitingReply = inquiries.filter(item => pending && !item.responded_at && !item.closed_at);
  const previousExchanges = inquiries.filter(item => !pending || item.responded_at || item.closed_at);
  const linkedClaim = report?.finder_approved_claim_id || report?.participant_claim_id;
  const office = report?.claim_type === 'OFFICE';
  const claimPath = linkedClaim && (office ? `/van-phong/claims/${linkedClaim}`
    : report?.report_subject_id ? claimDetailPath(report.report_subject_id, linkedClaim) : null);
  const correction = report?.post_measure === 'REQUEST_POST_CORRECTION' && report.post_owner;
  const recoveryPending = Boolean(report?.recovery_note && !report.recovery_confirmed_at && (!concluded || report.appeal_step));
  const handover = isAppeal || report?.report_type === 'HANDOVER_ISSUE';
  const overdue = awaitingReply.some(item => item.overdue);
  const reviewClaims = isAppeal && !office && report.approval_reopened && report.post_owner && report.report_subject_id;
  const followUpPath = reviewClaims ? postClaimsPath(report.report_subject_id, 'PENDING')
    : claimPath && !report.needs_reopen && (handover ? `${claimPath}#handover` : claimPath);
  const followUpLabel = reviewClaims ? 'Xem yêu cầu chờ xét duyệt' : handover ? 'Theo dõi nhận đồ' : 'Xem yêu cầu nhận đồ';

  function afterReply() {
    setReplySaved(true);
    window.dispatchEvent(new Event('sagasu-counts-changed'));
    reload();
  }

  return (
    <main className="container reports-page report-detail-page">
      <BackLink to={returnTo} />
      {loading && !data && <p className="reports-count" role="status">Đang tải chi tiết báo cáo…</p>}
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
            <span className={`report-state report-state-${report.status}`}>{report.appeal_step ? "Tranh chấp đã có kết luận" : reportStatuses[report.status]}</span>
          </div>
          {subject.value && <p className="report-detail-subject">{subject.label} <span className="report-meta-dot">·</span> <strong>{subject.value}</strong></p>}
          <time dateTime={report.created_at}>Gửi lúc {formatReportDate(report.created_at)}</time>
          {office && report.mine && report.report_type === 'HANDOVER_ISSUE' && !concluded &&
            <p className="report-detail-subject">Báo cáo đã gửi vẫn được xem xét sau khi hết hạn gửi sự cố.</p>}

        </header>
        <section id="buoc-tiep-theo" tabIndex={-1} className={`report-card report-detail-section report-next-step${pending ? ' report-next-step-pending' : ''}`}>
          <div className="report-next-copy">
            <h2>{pending ? (overdue ? 'Đã quá hạn phản hồi' : 'Bạn cần phản hồi') : recoveryPending ? 'Chờ xác nhận thu hồi món đồ' : appealSteps[report.appeal_step] || (concluded ? 'Kết quả xử lý' : 'Bước tiếp theo')}</h2>
            {pending && <p>{overdue
              ? 'Bạn vẫn có thể gửi thông tin trước khi quản lý kết luận.'
              : 'Quản lý cần thêm thông tin để xử lý báo cáo này.'}</p>}
            {recoveryPending && !pending && <p>Người giữ đồ phản hồi trong hồ sơ của mình. Quản lý đối chiếu bằng chứng và xác nhận việc thu hồi.</p>}
            {report.appeal_step && !report.needs_reopen && <p>{appealInstruction(report.appeal_step, office)}</p>}
            {report.needs_reopen && claimPath && <FinderAppealAction report={report} claimPath={claimPath} />}
            {!pending && !recoveryPending && !report.appeal_step && !concluded && <p>{report.status === 'OPEN'
              ? 'Chờ quản lý nhận hồ sơ. Bạn sẽ nhận thông báo khi cần bổ sung thông tin hoặc khi có kết quả.'
              : 'Quản lý đang đối chiếu hồ sơ. Bạn sẽ nhận thông báo nếu cần bổ sung thông tin.'}</p>}
            {concluded && <div id="ket-qua" tabIndex={-1} className="report-note">
              <strong>{outcomeLabels[report.outcome] || reportStatuses[report.status]}</strong>
              <p>{report.resolution_note || submission?.resolutionNote || 'Chưa có ghi chú kết luận.'}</p>
              {report.resolved_at && <time dateTime={report.resolved_at}>Kết luận lúc {formatReportDate(report.resolved_at)}</time>}
            </div>}
            {concluded && report.outcome === 'HANDOVER_RETRY' && <p>Người nhặt hẹn giao lại với người đã được chấp thuận, sau đó ghi nhận lượt giao mới trong yêu cầu nhận đồ.</p>}
            {concluded && report.outcome === 'APPEAL_KEEP' && report.mine && !office && <p>Yêu cầu của bạn đã kết thúc. Bạn không thể gửi lại yêu cầu hoặc tranh chấp trên bài đăng này.</p>}
            {concluded && report.outcome === 'APPEAL_INSUFFICIENT' && report.mine && <p>Tranh chấp này đã kết thúc. Trạng thái yêu cầu nhận đồ của bạn được giữ nguyên.</p>}
            {concluded && (report.outcome === 'HANDOVER_REOPEN' || report.approval_reopened) &&
              <p>{office ? 'Văn phòng xét duyệt lại' : 'Người nhặt xét lại'} các yêu cầu của bài đăng. Theo dõi kết quả trong hồ sơ của bạn.</p>}
            {correction && <div className="report-appeal-next">
              <p>Quản lý đã yêu cầu bạn chỉnh sửa bài đăng theo kết luận trên.</p>
              <Link className="btn btn-ghost" to={`/chi-tiet/${report.report_subject_id}`}>Mở bài đăng <ArrowRight size={15} aria-hidden="true" /></Link>
            </div>}
          </div>
          {(pending || followUpPath) && <div className="report-next-actions">
            {pending && <a className="btn btn-accent" href="#phan-hoi">Trả lời quản lý <ArrowDown size={15} aria-hidden="true" /></a>}
            {followUpPath && <Link className={pending ? 'btn btn-ghost' : 'report-back'} to={followUpPath}>
              {followUpLabel} <ArrowRight size={15} aria-hidden="true" />
            </Link>}
          </div>}
        </section>
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
          {awaitingReply.map(inquiry => <ReportInquiryResponse key={inquiry.inquiry_id} inquiry={inquiry} canReply onSaved={afterReply} />)}
          {previousExchanges.length > 0 && <details className="report-exchange-archive">
            <summary>Trao đổi trước đây ({previousExchanges.length})</summary>
            {previousExchanges.map(inquiry => <ReportInquiryResponse key={inquiry.inquiry_id} inquiry={inquiry} canReply={false} onSaved={afterReply} />)}
          </details>}
        </section>}
        {(submission || report.report_reason) && <section className="report-card report-detail-section">
          <h2>{isAppeal ? 'Nội dung tranh chấp' : 'Nội dung báo cáo'}{evidence.length > 0 && ` · ${evidence.length} ảnh`}</h2>
          <dl className="report-detail-fields">
            <div><dt>{isAppeal ? 'Lý do tranh chấp' : 'Lý do báo cáo'}</dt><dd>{report.report_reason || 'Chưa phân loại (báo cáo cũ)'}</dd></div>
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
          <ReportProgress status={report.status} appeal={isAppeal} />
          {submission.activities.map((activity, index) => <p key={index}>
            {formatReportDate(activity.at)} · {reportStatuses[activity.status]}{activity.note && ` · ${activity.note}`}
          </p>)}
        </details>}
        {image !== null && <ClaimEvidencePreview images={evidence} initialIndex={image} onClose={() => setImage(null)} />}
      </>}
    </main>
  );
}
