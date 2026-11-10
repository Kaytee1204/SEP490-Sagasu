import { useSearchParams } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import ReportQueue from "./components/ReportQueue";
import ReportDetail from "./components/ReportDetail";
import "./ReportReview.css";
export default function ReportReviewPage() {
  const [params, setParams] = useSearchParams(),
    id = params.get("id");
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
          onBack={() => setParams({})}
          onRelated={(value) => setParams({ id: value })}
        />
      ) : (
        <ReportQueue onOpen={(value) => setParams({ id: value })} />
      )}
    </main>
  );
}
