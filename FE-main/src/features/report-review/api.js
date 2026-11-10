import api from "@/services/apiClient";
const root = "/report-review";
export const reviewApi = {
  list: async (filters) =>
    (await api.get(`${root}?${new URLSearchParams(filters)}`)).data,
  detail: async (id) => (await api.get(`${root}/${id}`)).data,
  act: async (id, body) => {
    const result = (await api.post(`${root}/${id}/actions`, body)).data;
    window.dispatchEvent(new Event("report-review-changed"));
    return result;
  },
  ask: async (id, body) =>
    (await api.post(`${root}/${id}/inquiries`, body)).data,
  chat: async (id, before) =>
    (await api.get(`${root}/${id}/chat${before ? `?before=${before}` : ""}`))
      .data,
  mine: async () => (await api.get(`${root}/inquiries/mine`)).data,
  results: async () => (await api.get(`${root}/results/mine`)).data,
  reply: async (id, response, files) => {
    const form = new FormData();
    form.append(
      "data",
      new Blob([JSON.stringify({ response })], { type: "application/json" }),
    );
    files.forEach((file) => form.append("images", file));
    return (await api.upload(`${root}/inquiries/${id}/reply`, form)).data;
  },
};
