import { useEffect,useState } from 'react';
import { Check, ChevronDown, CircleCheck, Clock3, Images, PackageCheck, ShieldAlert, TriangleAlert } from 'lucide-react';
import { useChat } from '@/features/chat/ChatProvider';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getHandover } from './handoverApi';
import RecordHandoverDialog from './RecordHandoverDialog';
import ConfirmReceiptDialog from './ConfirmReceiptDialog';
import ReportHandoverIssueDialog from './ReportHandoverIssueDialog';
import './Handover.css';
// DaiHV integration: reflect Manager dispute outcomes without changing the P2P form.
const statusLabels={NOT_RECORDED:'Chưa ghi nhận giao đồ',WAITING_CONFIRMATION:'Chờ người nhận xác nhận',COMPLETED:'Đã nhận đồ',DISPUTED:'Có sự cố — chờ quản lý xử lý',CANCELLED:'Đã kết thúc bàn giao theo kết luận của quản lý'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});

function currentStep(data) {
  if (data.heldForReview && !['COMPLETED', 'CANCELLED'].includes(data.status)) return {
    Icon: ShieldAlert, title: 'Giao nhận đang tạm dừng', description: 'Quản lý đang đối chiếu báo cáo. Theo dõi kết quả trước khi tiếp tục bàn giao.',
  };
  switch (data.status) {
    case 'NOT_RECORDED': return data.canRecord
      ? { Icon: PackageCheck, title: 'Ghi nhận sau khi đã giao đồ', description: 'Hẹn người nhận và đối chiếu món đồ. Chỉ ghi nhận khi bạn đã giao đồ thực tế.' }
      : { Icon: Clock3, title: 'Chờ người nhặt ghi nhận giao đồ', description: 'Hai bên có thể nhắn tin để hẹn nhận đồ. Bạn sẽ xác nhận sau khi người nhặt ghi nhận đã giao.' };
    case 'WAITING_CONFIRMATION': return data.canConfirm
      ? { Icon: PackageCheck, title: 'Kiểm tra và xác nhận món đồ đã nhận', description: 'Kiểm tra đúng món đồ và phụ kiện trước khi xác nhận. Báo sự cố nếu chưa nhận được hoặc có vấn đề.' }
      : { Icon: Clock3, title: 'Chờ người nhận xác nhận', description: 'Bạn đã ghi nhận giao đồ. Quá trình hoàn tất khi người nhận xác nhận đã nhận đúng món đồ.' };
    case 'COMPLETED': return { Icon: CircleCheck, title: 'Đã hoàn tất trao trả', description: 'Người nhận đã xác nhận nhận đúng đồ. Hồ sơ và lịch sử trao đổi được giữ lại để đối chiếu.' };
    case 'DISPUTED': return { Icon: TriangleAlert, title: 'Sự cố đang được xem xét', description: 'Quản lý đang xử lý báo cáo. Hai bên vẫn có thể nhắn tin; việc hoàn tất bàn giao đang tạm dừng.' };
    case 'CANCELLED': return { Icon: ShieldAlert, title: 'Bàn giao đã kết thúc', description: 'Bàn giao đã kết thúc theo kết luận của quản lý. Lịch sử và bằng chứng vẫn được lưu.' };
    default: return { Icon: Clock3, title: statusLabels[data.status] || data.status, description: 'Theo dõi kết quả bàn giao tại đây.' };
  }
}

function HandoverSteps({ data }) {
  const recorded = Boolean(data.recordedAt) || ['WAITING_CONFIRMATION', 'COMPLETED', 'DISPUTED'].includes(data.status);
  const completed = data.status === 'COMPLETED';
  const stopped = ['DISPUTED', 'CANCELLED'].includes(data.status) || data.heldForReview;
  const steps = [
    { label: 'Đã chấp thuận', done: true },
    { label: 'Ghi nhận giao đồ', done: recorded, current: !recorded && !stopped },
    { label: 'Xác nhận nhận đồ', done: completed, current: recorded && !completed && !stopped },
  ];
  return <ol className="handover-steps" aria-label="Các bước nhận đồ">
    {steps.map((step, index) => <li key={step.label} className={step.done ? 'is-done' : step.current ? 'is-current' : ''} aria-current={step.current ? 'step' : undefined}>
      <span className="handover-step-marker">{step.done ? <Check size={16} aria-hidden="true" /> : index + 1}</span><span>{step.label}</span>
    </li>)}
  </ol>;
}

export default function HandoverPanel({claim,onChanged}) {
  const {revision}=useChat();
  const [data,setData]=useState(null),[error,setError]=useState(''),[image,setImage]=useState(null),[refresh,setRefresh]=useState(0);
  const [recordOpen,setRecordOpen]=useState(false);
  const [confirmOpen,setConfirmOpen]=useState(false);
  const [issueOpen,setIssueOpen]=useState(false);
  useEffect(()=>{const refetch=()=>setRefresh(n=>n+1);window.addEventListener('focus',refetch);return()=>window.removeEventListener('focus',refetch);},[]);
  useEffect(()=>{let cancelled=false;getHandover(claim.id).then(value=>{if(!cancelled){setData(value);setError('');}}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[claim.id,claim.version,revision,refresh]);
  const evidence=(data?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh giao đồ'}));
  const issueEvidence=(data?.issue?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh sự cố'}));
  const gallery=[...evidence.map(item=>({...item,id:`h:${item.id}`})),...issueEvidence.map(item=>({...item,id:`r:${item.id}`}))];
  const step = data ? currentStep(data) : null;
  const StepIcon = step?.Icon;
  return <section id="handover" className="claim-panel handover-panel" aria-labelledby="handover-title">
    <header className="handover-heading"><span className="handover-heading-icon"><PackageCheck size={22} aria-hidden="true" /></span><div><h2 id="handover-title">Tiến trình nhận đồ</h2><p>Hoàn tất sau khi người nhận xác nhận đúng món đồ.</p></div></header>
    {error&&<div role="alert" className="claim-alert"><p>{error}</p><button type="button" className="btn btn-ghost" onClick={()=>setRefresh(n=>n+1)}>Tải lại tiến trình</button></div>}
    {!data&&!error&&<p role="status">Đang tải tiến trình bàn giao…</p>}
    {data&&<>
    <HandoverSteps data={data} />
    <div className={`handover-current-step ${data.status === 'COMPLETED' ? 'is-completed' : data.heldForReview || ['DISPUTED','CANCELLED'].includes(data.status) ? 'is-paused' : ''}`}>
      <StepIcon size={24} aria-hidden="true" /><div className="handover-current-content"><h3>{step.title}</h3><p>{step.description}</p>
      {(data.canRecord || data.canConfirm || data.canReportIssue) && <div className="handover-next-actions">
        {data.canRecord&&<button type="button" className="btn btn-accent" onClick={()=>setRecordOpen(true)}><PackageCheck size={18} aria-hidden="true" />Ghi nhận đã giao đồ</button>}
        {data.canConfirm&&<button type="button" className="btn btn-accent" onClick={()=>setConfirmOpen(true)}><CircleCheck size={18} aria-hidden="true" />Xác nhận đã nhận đúng đồ</button>}
        {data.canReportIssue&&<button type="button" className="btn btn-ghost handover-issue-button" onClick={()=>setIssueOpen(true)}><TriangleAlert size={17} aria-hidden="true" />Báo sự cố bàn giao</button>}
      </div>}
      </div>
    </div>
    {(data.recordedAt || data.confirmedAt) && <dl className="handover-dates">
      {data.recordedAt && <div><dt>Ghi nhận giao đồ</dt><dd>{date(data.recordedAt)}</dd></div>}
      {data.confirmedAt && <div><dt>Người nhận xác nhận</dt><dd>{date(data.confirmedAt)}</dd></div>}
    </dl>}
    {data.recordedAt&&<details className="handover-evidence-details"><summary><Images size={18} aria-hidden="true" /><span>Ghi chú và ảnh bàn giao</span><small>{evidence.length} ảnh</small><ChevronDown size={17} aria-hidden="true" /></summary>
      {data.note&&<p className="claim-prose">{data.note}</p>}
      {evidence.length ? <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} type="button" onClick={()=>setImage(i)} aria-label={`Xem ảnh giao đồ ${i+1}`}><img src={item.url} alt={item.name}/></button>)}</div> : <p className="claims-muted">Không đính kèm ảnh bàn giao.</p>}
    </details>}
    {data.issue&&<section className="handover-issue-summary"><h3><TriangleAlert size={18} aria-hidden="true" />Báo cáo sự cố #{data.issue.id}</h3>{data.issue.reasonLabel&&<p><strong>{data.issue.reasonLabel}</strong></p>}<p className="claim-prose">{data.issue.description}</p><p>Quản lý đang xem xét báo cáo. Việc hoàn tất bàn giao đang tạm dừng.</p>
      <div className="report-evidence">{issueEvidence.map((item,i)=><button type="button" key={item.id} onClick={()=>setImage(evidence.length+i)} aria-label="Xem ảnh sự cố"><img src={item.url} alt={item.name}/></button>)}</div></section>}
    </>}
    {recordOpen&&data?.canRecord&&<RecordHandoverDialog claim={claim} onClose={()=>setRecordOpen(false)}
      onRecorded={result=>{setData(result);setRecordOpen(false);setError('');onChanged();}}/>}
    {confirmOpen&&data?.canConfirm&&<ConfirmReceiptDialog claim={claim} handover={data}
      onClose={()=>setConfirmOpen(false)} onConfirmed={result=>{setData(result);setConfirmOpen(false);setError('');onChanged();}}
      onReportIssue={data.canReportIssue?()=>{setConfirmOpen(false);setIssueOpen(true);}:undefined}/>}
    {issueOpen&&data?.canReportIssue&&<ReportHandoverIssueDialog claim={claim} handover={data}
      onClose={()=>setIssueOpen(false)} onReported={result=>{setData(result);setIssueOpen(false);setError('');onChanged();}}/>}
    {image!==null&&<ClaimEvidencePreview images={gallery} initialIndex={image} onClose={()=>setImage(null)}/>}
  </section>;
}
