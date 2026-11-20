import api from '@/services/apiClient';

const workspace = '/reports/workspace';
export const getReportWorkspace = async ({ page, mine, status, type }) =>
  (await api.get(`${workspace}?${new URLSearchParams({ page, mine, status, type })}`)).data;
export const getReportActionCounts = async () => (await api.get(`${workspace}/action-counts`)).data;
export const getReportWorkspaceDetail = async id => (await api.get(`${workspace}/${id}`)).data;
export const getReportReasons = async (targetType,targetId,handoverIssue=false) =>
  (await api.get(`/reports/reasons?${new URLSearchParams({targetType,targetId,handoverIssue})}`)).data;
export async function createReport(request,images) {
  const form=new FormData(); form.append('data',new Blob([JSON.stringify(request)],{type:'application/json'}));
  images.forEach(image=>form.append('images',image)); return (await api.upload('/reports',form)).data;
}
