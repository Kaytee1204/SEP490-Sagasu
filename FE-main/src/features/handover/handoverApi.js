import api from '@/services/apiClient';
export const getHandover=async id=>(await api.get(`/claims/${id}/handover`)).data;
export async function uploadHandover(id,request,images,path='') {
  const form=new FormData();form.append('data',new Blob([JSON.stringify(request)],{type:'application/json'}));
  images.forEach(image=>form.append('images',image));return (await api.upload(`/claims/${id}/handover${path}`,form)).data;
}
