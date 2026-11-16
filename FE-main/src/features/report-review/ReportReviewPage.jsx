import { useSearchParams } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import ReportDetail from "./components/ReportDetail";
import ReportWorkbench from "./components/ReportWorkbench";
import "./ReportReview.css";
export default function ReportReviewPage() {
  const [params, setParams] = useSearchParams(),
    id = params.get("id"), target=params.get("target");
  return (
    <main className="rr">
      <header className="rr-header">
        <div>
          <span className="rr-eyebrow">
            <ShieldCheck size={17} /> QUẢN LÝ · SAGASU
          </span>
          <h1>Báo cáo & phản ánh</h1>
          <p>
            Đối chiếu thông tin từ các bên, ghi nhận căn cứ và theo dõi kết quả
            xử lý.
          </p>
        </div>
      </header>
      {id ? (
        <ReportDetail
          key={id}
          id={id}
          onBack={() => setParams(target?{target}:{})}
          onRelated={(value) => setParams({ id: value,...(target?{target}:{}) })}
        />
      ) : (
        <ReportWorkbench target={target} onTarget={value=>setParams(value?{target:value}:{})} onOpen={value=>setParams({id:value,...(target?{target}:{})})}/>
      )}
    </main>
  );
}
