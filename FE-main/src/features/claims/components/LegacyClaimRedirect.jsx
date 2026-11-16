import '@/styles/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getClaim } from '../api/claimApi';
import { validClaimId, claimDetailPath } from '../utils/claimUtils';
import '../styles/Claims.css';

export default function LegacyClaimRedirect() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!validClaimId(id)) return <main className="container claims-page"><p role="alert">Mã claim không hợp lệ.</p></main>;
  return <LegacyClaimLookup key={`${user?.id}:${id}`} id={id} />;
}
function LegacyClaimLookup({ id }) {
  const location = useLocation();
  const [result, setResult] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getClaim(id).then(claim => { if (!cancelled) setResult({ claim }); })
      .catch(error => { if (!cancelled) setResult({ error: error.message }); });
    return () => { cancelled = true; };
  }, [id]);
  if (result?.claim) return <Navigate to={claimDetailPath(result.claim.postId, result.claim.id)} replace state={location.state} />;
  return <main className="container claims-page">
    {result?.error ? <p role="alert">{result.error}</p> : <p role="status">Đang mở hồ sơ claim…</p>}
    <Link to="/claim-cua-toi">Về danh sách claim</Link>
  </main>;
}
