import api from "@/services/apiClient";
const data = async (promise) => (await promise).data;
const uploadReceipt = (url,body,files=[],publicImages=[]) => {
  const form=new FormData();
  form.append("data",new Blob([JSON.stringify(body)],{type:"application/json"}));
  files.forEach(file=>form.append("images",file));
  publicImages.forEach(file=>form.append("publicImages",file));
  return data(api.upload(url,form));
};
export const workflowApi = {
  counterPeople: (q) => data(api.get(`/office-workflow/counter/people?${new URLSearchParams({ q })}`)),
  counterPosts: (id) => data(api.get(`/office-workflow/counter/people/${id}/posts`)),
  counterReceive: (body,files,publicImages) => uploadReceipt('/office-workflow/counter/receive',body,files,publicImages),
  transfers: (filters) => data(api.get(`/office-workflow/transfers?${new URLSearchParams(filters)}`)),
  myTransferId: (postId) => data(api.get(`/office-workflow/transfers/my-id?${new URLSearchParams({ postId })}`)),
  transfer: (id) => data(api.get(`/office-workflow/transfers/${id}`)),
  decide: (id, body, files, publicImages) => body.decision === "RECEIVED" ? uploadReceipt(`/office-workflow/transfers/${id}/receive`,body,files,publicImages) : data(api.post(`/office-workflow/transfers/${id}`, body)),
  schedule: (id, body) => data(api.post(`/office-workflow/transfers/${id}/schedule`, body)),
  resendIntake: (id) => data(api.post(`/office-workflow/transfers/${id}/appointment-email`, {})),
  detail: (id) => data(api.get(`/office-workflow/claims/${id}`)),
  action: (id, action, body) => data(api.post(`/office-workflow/claims/${id}/${action}`, body)),
  record: (id, body, files) => {
    const form = new FormData();
    form.append("data", new Blob([JSON.stringify(body)], { type: "application/json" }));
    files.forEach((file) => form.append("images", file));
    return data(api.upload(`/office-workflow/claims/${id}/handover`, form));
  },
};
