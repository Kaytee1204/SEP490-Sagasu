import api from "@/services/apiClient";
const data = async (promise) => (await promise).data;
export const workflowApi = {
  counterPeople: (q) => data(api.get(`/office-workflow/counter/people?${new URLSearchParams({ q })}`)),
  counterPosts: (id) => data(api.get(`/office-workflow/counter/people/${id}/posts`)),
  counterReceive: (body) => data(api.post('/office-workflow/counter/receive', body)),
  reply: (id, intake, body) => data(api.post(`/office-workflow/${intake ? "transfers" : "claims"}/${id}/appointment-response`, body)),
  transfers: (filters) => data(api.get(`/office-workflow/transfers?${new URLSearchParams(filters)}`)),
  transfer: (id) => data(api.get(`/office-workflow/transfers/${id}`)),
  decide: (id, body) => data(api.post(`/office-workflow/transfers/${id}`, body)),
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
