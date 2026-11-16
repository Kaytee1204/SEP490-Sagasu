import '@/styles/Workspace.css';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { validClaimId } from '../utils/claimUtils';
import '../styles/Claims.css';

export default function PostClaimsRedirect() {
  const { id } = useParams();
  const [params] = useSearchParams();
  if (!validClaimId(id)) return <main className="container claims-page"><p role="alert">Mã bài đăng không hợp lệ.</p></main>;
  const target = new URLSearchParams({ view: 'received', postId: id, claimStatus: params.get('claimStatus') || 'ALL' });
  return <Navigate replace to={`/claim-cua-toi?${target}`} />;
}
