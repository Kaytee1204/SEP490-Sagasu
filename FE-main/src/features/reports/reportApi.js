import api from '@/services/apiClient';
export const getReport = async id => (await api.get(`/reports/${id}`)).data;
export const getMyReports = async (page,status) => (await api.get(`/reports/mine?page=${page}&status=${status}`)).data;
export async function createReport(request,images) {
  const form=new FormData(); form.append('data',new Blob([JSON.stringify(request)],{type:'application/json'}));
  images.forEach(image=>form.append('images',image)); return (await api.upload('/reports',form)).data;
}
