import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import ReportDetail from '../components/ReportDetail';
import { reportReviewListPath, reportReviewRoot } from '../routes';
import '../ReportReview.css';
import '../ReportWorkspace.css';
export default function ReportReviewDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hash } = useLocation();
  const showConversation = hash === '#trao-doi';
  const [params] = useSearchParams();
  const suffix = params.size ? `?${params}` : '';
  function back(kind) {
    const filters = new URLSearchParams(params);
    const path = reportReviewListPath(filters.get('kind') || kind);
    filters.delete('kind');
    navigate(`${path}${filters.size ? `?${filters}` : ''}`);
  }
  return <section className="rr rr-detail-shell" aria-label="Chi tiết báo cáo">
    <ReportDetail key={showConversation ? `${id}:conversation` : id} id={id} showConversation={showConversation}
      onBack={back}
      onRelated={value => navigate(`${reportReviewRoot}/${value}${suffix}`)} />
  </section>;
}
