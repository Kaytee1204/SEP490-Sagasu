import api from "@/services/apiClient";
const root = "/report-review";
export const reviewApi = {
  batch: async (body) => {
    const result = (await api.post(`${root}/batch-conclusion`, body)).data;
    window.dispatchEvent(new Event("report-review-changed"));
    return result;
  },
  list: async (filters) => {
    try {
      const response = await api.request(`${root}?${new URLSearchParams(filters)}`, {
        method: "GET",
        signal: AbortSignal.timeout(15000),
      });
      return response.data;
    } catch (error) {
      if (error.name === "TimeoutError") {
        throw new Error("Danh sách phản hồi quá lâu. Bấm Làm mới để thử lại.");
      }
      throw error;
    }
  },
  detail: async (id) => (await api.get(`${root}/${id}`)).data,
  act: async (id, body) => {
    const result = (await api.post(`${root}/${id}/actions`, body)).data;
    window.dispatchEvent(new Event("report-review-changed"));
    return result;
  },
  ask: async (id, body) => {
    const result = (await api.post(`${root}/${id}/inquiries`, body)).data;
    window.dispatchEvent(new Event("report-review-changed"));
    return result;
  },
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
