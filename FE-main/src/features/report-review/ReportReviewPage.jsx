import { Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import ReportQueue from "./components/ReportQueue";
import "./ReportReview.css";
export default function ReportReviewPage() {
  const navigate=useNavigate();
  const [params] = useSearchParams(),
    id = params.get("id");
  if(id){const next=new URLSearchParams(params);next.delete('id');return <Navigate replace to={`/admin/reports/${id}${next.size?`?${next}`:''}`}/>;}
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
      <ReportQueue onOpen={value=>navigate(`/admin/reports/${value}${params.size ? `?${params}` : ""}`)}/>
    </main>
  );
}
