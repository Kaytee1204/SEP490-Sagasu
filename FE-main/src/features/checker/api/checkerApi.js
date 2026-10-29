import apiClient from '@/services/apiClient';

function multipart(data, files) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  files.forEach((file) => form.append('files', file));
  return form;
}

export const checkerApi = {
  async list({ page = 1, keyword = '', filter = 'ALL' }) {
    return (await apiClient.get(`/checker/receipts?${new URLSearchParams({ page, size: 10, keyword, filter })}`)).data;
  },
  async detail(id) { return (await apiClient.get(`/checker/receipts/${encodeURIComponent(id)}`)).data; },
  async categories() {
    return (await apiClient.get('/posts/categories')).data.map((item) => ({ categoryId: item.id, name: item.name }));
  },
  async create(data, files) { return (await apiClient.upload('/checker/receipts/draft', multipart(data, files))).data; },
  async update(id, data, files) {
    return (await apiClient.request(`/checker/receipts/${id}`, { method: 'PUT', body: multipart(data, files) })).data;
  },
  async submit(id, expectedUpdatedAt) {
    return (await apiClient.post(`/checker/receipts/${id}/submit`, { expectedUpdatedAt })).data;
  },
  async updateLocation(id, storageLocationId, expectedUpdatedAt) {
    return (await apiClient.patch(`/checker/receipts/${id}/location`, { storageLocationId, expectedUpdatedAt })).data;
  },
};
