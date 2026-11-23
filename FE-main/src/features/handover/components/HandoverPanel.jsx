import { useEffect,useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, ChevronDown, CircleCheck, Clock3, Images, PackageCheck, RotateCcw, ShieldAlert, TriangleAlert } from 'lucide-react';
import { useChat } from '@/features/chat/hooks/useChat';
import { useNotifications } from '@/features/notifications/NotificationProvider';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { handoverVerificationHint } from '@/features/claims/utils/reviewCriteria';
import { claimDate, P2P_CLAIM_STEPS } from '@/features/claims/utils/claimUtils';
import WorkflowSteps from '@/components/common/WorkflowSteps';
import { getHandover } from '../api/handoverApi';
import RecordHandoverDialog from './RecordHandoverDialog';
import ConfirmReceiptDialog from './ConfirmReceiptDialog';
import ReportHandoverIssueDialog from './ReportHandoverIssueDialog';
import '../styles/Handover.css';
// DaiHV integration: reflect Manager dispute outcomes without changing the P2P form.
const statusLabels={NOT_RECORDED:'Chưa ghi nhận giao đồ',WAITING_CONFIRMATION:'Chờ người nhận xác nhận',COMPLETED:'Đã nhận đồ',DISPUTED:'Có sự cố — chờ quản lý xử lý',CANCELLED:'Đã kết thúc bàn giao theo kết luận của quản lý'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});

function currentStep(data, canRevoke) {
  if (data.heldForReview && !['COMPLETED', 'CANCELLED'].includes(data.status)) return {
    Icon: ShieldAlert, title: 'Giao nhận đang tạm dừng', description: 'Quản lý đang đối chiếu báo cáo. Theo dõi kết quả trước khi tiếp tục bàn giao.',
  };
  if (data.issue && data.status === 'NOT_RECORDED') return {
    Icon: TriangleAlert, title: 'Sự cố đang được xem xét', description: 'Việc ghi nhận giao đồ đang tạm dừng trong lúc quản lý xem xét.',
  };
  const appeal = data.appealWindow;
  if (appeal?.state === 'OPEN') return {
    Icon: Clock3, title: 'Chưa thể bàn giao',
    description: `Những người gửi yêu cầu khác có thể phản hồi đến ${claimDate(appeal.deadline)}. Chat chỉ xem và bàn giao tạm dừng trong thời gian này.`,
  };
  if (appeal?.state === 'UNDER_REVIEW') return {
    Icon: ShieldAlert, title: 'Đang xem xét tranh chấp',
    description: 'Chat đang ở chế độ chỉ xem. Hãy chờ quản lý xử lý tranh chấp trước khi giao đồ.',
  };
  if (appeal?.state === 'REVIEW_REQUIRED') return {
    Icon: ShieldAlert, title: 'Bàn giao đang tạm dừng',
    description: canRevoke
      ? 'Quản lý yêu cầu xét lại người nhận. Nếu vẫn giữ đồ, bạn có thể mở lại xét duyệt để tự quyết định người nhận.'
      : 'Quản lý yêu cầu xét lại người nhận. Bàn giao tạm dừng trong lúc chờ người nhặt xử lý.',
  };
  switch (data.status) {
    case 'NOT_RECORDED': return data.canRecord
      ? { Icon: PackageCheck, title: 'Ghi nhận sau khi đã giao đồ', description: 'Hẹn người nhận và đối chiếu món đồ. Chỉ ghi nhận khi bạn đã giao đồ thực tế.' }
      : { Icon: Clock3, title: 'Chờ người nhặt ghi nhận giao đồ', description: 'Hai bên có thể nhắn tin để hẹn nhận đồ. Bạn sẽ xác nhận sau khi người nhặt ghi nhận đã giao.' };
    case 'WAITING_CONFIRMATION': return data.canConfirm
      ? { Icon: PackageCheck, title: 'Kiểm tra và xác nhận món đồ đã nhận', description: 'Kiểm tra đúng món đồ và phụ kiện trước khi xác nhận. Báo sự cố nếu chưa nhận được hoặc có vấn đề.' }
      : { Icon: Clock3, title: 'Chờ người nhận xác nhận', description: 'Bạn đã ghi nhận giao đồ. Quá trình hoàn tất khi người nhận xác nhận đã nhận đúng món đồ.' };
    case 'COMPLETED': return { Icon: CircleCheck, title: 'Đã hoàn tất trao trả', description: data.confirmedByManager
      ? 'Quản lý đã đối chiếu căn cứ và xác nhận đúng món đồ, đúng người nhận.'
      : 'Người nhận đã xác nhận nhận đúng đồ.' };
    case 'DISPUTED': return { Icon: TriangleAlert, title: 'Sự cố đang được xem xét', description: 'Quản lý đang xử lý báo cáo. Hai bên vẫn có thể nhắn tin; việc hoàn tất bàn giao đang tạm dừng.' };
    case 'CANCELLED': return { Icon: ShieldAlert, title: 'Bàn giao đã kết thúc', description: 'Bàn giao đã kết thúc theo kết luận của quản lý. Lịch sử và bằng chứng vẫn được lưu.' };
    default: return { Icon: Clock3, title: statusLabels[data.status] || data.status, description: 'Theo dõi kết quả bàn giao tại đây.' };
  }
}

function isPaused(data) {
  return data.heldForReview || (data.status === 'NOT_RECORDED' && Boolean(data.issue)) || ['DISPUTED', 'CANCELLED'].includes(data.status)
    || Boolean(data.appealWindow && data.appealWindow.state !== 'CLEARED');
}

function HandoverSteps({ data, showSubmission }) {
  const recorded = Boolean(data.recordedAt) || ['WAITING_CONFIRMATION', 'COMPLETED', 'DISPUTED'].includes(data.status);
  const completed = data.status === 'COMPLETED' ? 3 : recorded ? 2 : 1;
  const state = data.status === 'CANCELLED' ? 'stopped' : isPaused(data) ? 'paused' : 'active';
  const steps = showSubmission ? P2P_CLAIM_STEPS : ['Đã chấp thuận', ...P2P_CLAIM_STEPS.slice(2)];
  return <WorkflowSteps label="Các bước nhận đồ" steps={steps} completed={completed + Number(showSubmission)} state={state} />;
}

export default function HandoverPanel({claim,onChanged,onRevokeApproval,onContinueReview,showSubmission = false}) {
  const {revision}=useChat();
  const { items } = useNotifications();
  const latestNotification = items.find(item => item.targetUrl === `/bai-dang/${claim.postId}/claims/${claim.id}`)?.id;
  const [data,setData]=useState(null),[error,setError]=useState(''),[image,setImage]=useState(null),[refresh,setRefresh]=useState(0);
  const [recordOpen,setRecordOpen]=useState(false);
  const [confirmOpen,setConfirmOpen]=useState(false);
  const [issueOpen,setIssueOpen]=useState(false);
  const [submittedReportId,setSubmittedReportId]=useState(null);
  useEffect(()=>{const refetch=()=>setRefresh(n=>n+1);window.addEventListener('focus',refetch);return()=>window.removeEventListener('focus',refetch);},[]);
  useEffect(() => {
    let cancelled = false;
    getHandover(claim.id).then(value => {
      if (cancelled) return;
      setData(value);
      setError('');
    }).catch(problem => { if (!cancelled) setError(problem.message); });
    return () => { cancelled = true; };
  }, [claim.id, claim.version, revision, refresh, latestNotification]);
  const appealState = data?.appealWindow?.state;
  const deadline = data?.appealWindow?.deadline;
  useEffect(() => {
    const remaining = Date.parse(deadline) - Date.now();
    if (!['OPEN', 'UNDER_REVIEW', 'REVIEW_REQUIRED'].includes(appealState)) return;
    const refetch = () => { if (document.visibilityState === 'visible') setRefresh(value => value + 1); };
    const interval = window.setInterval(refetch, 30000);
    const timeout = appealState === 'OPEN' && remaining > 0 ? window.setTimeout(refetch, remaining + 100) : undefined;
    return () => { window.clearInterval(interval); window.clearTimeout(timeout); };
  }, [appealState, deadline]);
  useEffect(() => {
    if (appealState) onChanged();
  }, [appealState, onChanged]);
  const evidence=(data?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh giao đồ'}));
  const issueEvidence=(data?.issue?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh sự cố'}));
  const gallery=[...evidence.map(item=>({...item,id:`h:${item.id}`})),...issueEvidence.map(item=>({...item,id:`r:${item.id}`}))];
  const requiresReReview = appealState === 'REVIEW_REQUIRED';
  const canReportIssue = data?.canReportIssue;
  const canRevoke = Boolean(onRevokeApproval) && (!data || data.status === 'NOT_RECORDED')
    && !data?.heldForReview && !data?.issue && appealState !== 'UNDER_REVIEW';
  const canContinueReview = canRevoke && requiresReReview && Boolean(onContinueReview);
  const step = data ? currentStep(data, canRevoke) : null;
  const revokeButton = canRevoke && <button type="button" className={requiresReReview && !canContinueReview ? 'btn btn-accent' : 'btn btn-ghost'}
    onClick={onRevokeApproval}><RotateCcw size={16} aria-hidden="true" />{requiresReReview ? 'Thu hồi để xét lại' : 'Thu hồi chấp thuận'}</button>;
  const StepIcon = step?.Icon;
  return <section id="handover" className="claim-panel handover-panel" aria-labelledby="handover-title">
    <header className="handover-heading"><span className="handover-heading-icon"><PackageCheck size={22} aria-hidden="true" /></span><div><h2 id="handover-title">Tiến trình nhận đồ</h2><p>Hoàn tất sau khi người nhận xác nhận đúng món đồ.</p></div></header>
    {error&&<div role="alert" className="claim-alert"><p>{error}</p><div className="handover-next-actions">
      <button type="button" className="btn btn-ghost" onClick={()=>setRefresh(n=>n+1)}>Tải lại tiến trình</button>
      {!data && revokeButton}
    </div></div>}
    {!data&&!error&&<p role="status">Đang tải tiến trình bàn giao…</p>}
    {data&&<>
    <HandoverSteps data={data} showSubmission={showSubmission} />
    <div id={canRevoke ? 'review-approval' : undefined} className={`handover-current-step ${data.status === 'COMPLETED' ? 'is-completed' : isPaused(data) ? 'is-paused' : ''}`}>
      <StepIcon size={24} aria-hidden="true" /><div className="handover-current-content"><h3>{step.title}</h3><p>{step.description}</p>
      {(data.canRecord || data.canConfirm || canReportIssue || canRevoke) && <div className="handover-next-actions">
        {data.canRecord && <p className="handover-verification-hint">{handoverVerificationHint(claim.answers)}</p>}
        {data.canRecord&&<button type="button" className="btn btn-accent" onClick={()=>setRecordOpen(true)}><PackageCheck size={18} aria-hidden="true" />Ghi nhận đã giao đồ</button>}
        {data.canConfirm&&<button type="button" className="btn btn-accent" onClick={()=>setConfirmOpen(true)}><CircleCheck size={18} aria-hidden="true" />Xác nhận đã nhận đúng đồ</button>}
        {canReportIssue&&<button type="button" className="btn btn-ghost handover-issue-button" onClick={()=>setIssueOpen(true)}><TriangleAlert size={17} aria-hidden="true" />Báo sự cố trao trả</button>}
        {canContinueReview && <button type="button" className="btn btn-accent" onClick={onContinueReview}>
          Xét lại hồ sơ này <ArrowRight size={16} aria-hidden="true" />
        </button>}
        {revokeButton}
      </div>}
      </div>
    </div>
    {(data.recordedAt || data.confirmedAt) && <dl className="handover-dates">
      {data.recordedAt && <div><dt>Ghi nhận giao đồ</dt><dd>{date(data.recordedAt)}</dd></div>}
      {data.confirmedAt && <div><dt>{data.confirmedByManager ? 'Quản lý xác nhận' : 'Người nhận xác nhận'}</dt><dd>{date(data.confirmedAt)}</dd></div>}
    </dl>}
    {data.recordedAt&&<details className="handover-evidence-details"><summary><Images size={18} aria-hidden="true" /><span>Ghi chú và ảnh bàn giao</span><small>{evidence.length} ảnh</small><ChevronDown size={17} aria-hidden="true" /></summary>
      {data.note&&<p className="claim-prose">{data.note}</p>}
      {evidence.length ? <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} type="button" onClick={()=>setImage(i)} aria-label={`Xem ảnh giao đồ ${i+1}`}><img src={item.url} alt={item.name}/></button>)}</div> : <p className="claims-muted">Không đính kèm ảnh bàn giao.</p>}
    </details>}
    {submittedReportId && <p role="status">Đã gửi báo cáo sự cố #{submittedReportId}.</p>}
    {data.issue&&<section className="handover-issue-summary"><h3><TriangleAlert size={18} aria-hidden="true" />Báo cáo sự cố #{data.issue.id}</h3>{data.issue.reasonLabel&&<p><strong>{data.issue.reasonLabel}</strong></p>}<p className="claim-prose">{data.issue.description}</p><p>{data.status === 'COMPLETED'
      ? 'Quản lý đang xem xét sự cố phát sinh sau khi hoàn tất bàn giao.'
      : data.status === 'NOT_RECORDED'
        ? 'Quản lý đang xem xét báo cáo. Việc ghi nhận giao đồ đang tạm dừng.'
      : 'Quản lý đang xem xét báo cáo. Việc hoàn tất bàn giao đang tạm dừng.'}</p>
      <Link className="btn btn-ghost" to={`/bao-cao/${data.issue.id}`}>Theo dõi báo cáo</Link>
      <div className="report-evidence">{issueEvidence.map((item,i)=><button type="button" key={item.id} onClick={()=>setImage(evidence.length+i)} aria-label="Xem ảnh sự cố"><img src={item.url} alt={item.name}/></button>)}</div></section>}
    </>}
    {recordOpen&&data?.canRecord&&<RecordHandoverDialog claim={claim} onClose={()=>setRecordOpen(false)}
      onRecorded={result=>{setData(result);setRecordOpen(false);setError('');onChanged();}}/>}
    {confirmOpen&&data?.canConfirm&&<ConfirmReceiptDialog claim={claim} handover={data}
      onClose={()=>setConfirmOpen(false)} onConfirmed={result=>{setData(result);setConfirmOpen(false);setError('');onChanged();}}
      onReportIssue={canReportIssue?()=>{setConfirmOpen(false);setIssueOpen(true);}:undefined}/>}
    {issueOpen&&data&&<ReportHandoverIssueDialog claim={claim} handover={data}
      onClose={()=>setIssueOpen(false)} onReported={result=>{
        setData(result);setSubmittedReportId(result.submittedReportId);setIssueOpen(false);setError('');onChanged();
      }}/>}
    {image!==null&&<ClaimEvidencePreview images={gallery} initialIndex={image} onClose={()=>setImage(null)}/>}
  </section>;
}
