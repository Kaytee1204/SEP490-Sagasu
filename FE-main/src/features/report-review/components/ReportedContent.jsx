import ContextPanel from './ContextPanel';

export default function ReportedContent({ context: data }) {
  if(!data) return null;
  return <section className="rr-card rr-reported-content"><h2>Nội dung bị phản ánh</h2>
    <p className="rr-muted">Đối chiếu nội dung này với lý do và bằng chứng của từng phản ánh bên cạnh.</p>
    <ContextPanel data={{post:data.post,postImages:data.postImages,message:data.message,messageAttachments:data.messageAttachments,account:data.account,claim:data.claim,answers:data.answers,claimEvidence:data.claimEvidence}}/>
    {!!data.claim&&<details><summary>Lịch sử xác minh và trao trả</summary><ContextPanel data={{appointments:data.appointments,verifications:data.verifications,p2pHandover:data.p2pHandover,officeHandover:data.officeHandover,handoverEvidence:data.handoverEvidence,claimHistory:data.claimHistory}}/></details>}
  </section>;
}
