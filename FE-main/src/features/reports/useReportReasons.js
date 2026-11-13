import { useEffect, useState } from 'react';
import { getReportReasons } from './reportApi';

export const reportTypes = {
  POST: 'Bài đăng',
  USER: 'Tài khoản',
  CLAIM: 'Yêu cầu nhận đồ',
  MESSAGE: 'Tin nhắn',
  HANDOVER_ISSUE: 'Sự cố bàn giao',
};

export const reportTitles = {
  POST: 'Báo cáo bài đăng',
  USER: 'Báo cáo tài khoản',
  CLAIM: 'Báo cáo yêu cầu nhận đồ',
  MESSAGE: 'Báo cáo tin nhắn',
  HANDOVER_ISSUE: 'Báo sự cố bàn giao',
};

export default function useReportReasons(type, id, handoverIssue = false) {
  const [options, setOptions] = useState(null);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setOptions(null);
    setError('');
    getReportReasons(type, id, handoverIssue).then(result => {
      if (cancelled) return;
      if (!result.reasons?.length) setError('Chưa có lý do báo cáo khả dụng. Vui lòng thử lại sau.');
      else setOptions(result);
    }).catch(problem => {
      if (!cancelled) setError(problem.message || 'Chưa tải được lý do báo cáo.');
    });
    return () => { cancelled = true; };
  }, [type, id, handoverIssue, revision]);

  return { options, error, reload: () => setRevision(value => value + 1) };
}
