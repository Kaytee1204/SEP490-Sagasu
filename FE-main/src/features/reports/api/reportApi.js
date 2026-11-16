import api from '@/services/apiClient';
export const getReport = async id => (await api.get(`/reports/${id}`)).data;
export const getMyReports = async (page,status,type='ALL') =>
  (await api.get(`/reports/mine?${new URLSearchParams({page,status,type})}`)).data;
export const getReportReasons = async (targetType,targetId,handoverIssue=false) =>
  (await api.get(`/reports/reasons?${new URLSearchParams({targetType,targetId,handoverIssue})}`)).data;
export async function createReport(request,images) {
  const form=new FormData(); form.append('data',new Blob([JSON.stringify(request)],{type:'application/json'}));
  images.forEach(image=>form.append('images',image)); return (await api.upload('/reports',form)).data;
}
