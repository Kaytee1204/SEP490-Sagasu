import apiClient from '@/services/apiClient';

function multipart(data, files) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  files.forEach((file) => form.append('files', file));
  return form;
}

export const approverApi = {
  async list({ page = 1, keyword = '', filter = 'ALL' }) {
    return (await apiClient.get(`/approver/receipts?${new URLSearchParams({ page, size: 10, keyword, filter })}`)).data;
  },
  async detail(id) { return (await apiClient.get(`/approver/receipts/${encodeURIComponent(id)}`)).data; },
  async create(data, files) { return (await apiClient.upload('/approver/receipts/draft', multipart(data, files))).data; },
  async update(id, data, files) {
    return (await apiClient.request(`/approver/receipts/${id}`, { method: 'PUT', body: multipart(data, files) })).data;
  },
  async submit(id, expectedUpdatedAt) {
    return (await apiClient.post(`/approver/receipts/${id}/submit`, { expectedUpdatedAt })).data;
  },
};
