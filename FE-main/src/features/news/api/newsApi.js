import apiClient from '@/services/apiClient';

function buildNewsFormData(data, coverImageFile) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  if (coverImageFile) {
    form.append('coverImage', coverImageFile);
  }
  return form;
}

export const newsApi = {
  async listPublished(page = 1, size = 8) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    return (await apiClient.get(`/news?${params}`)).data;
  },

  async getPublished(id) {
    return (await apiClient.get(`/news/${encodeURIComponent(id)}`)).data;
  },

  async listForApprover({ page = 1, size = 10, keyword = '', category = 'ALL', status = 'ALL' } = {}) {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      category,
      status,
    });
    if (keyword && keyword.trim()) {
      params.set('keyword', keyword.trim());
    }
    return (await apiClient.get(`/approver/news?${params}`)).data;
  },

  async create(data, coverImageFile = null) {
    return (await apiClient.upload('/approver/news', buildNewsFormData(data, coverImageFile))).data;
  },

  async update(id, data, coverImageFile = null) {
    return (
      await apiClient.request(`/approver/news/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: buildNewsFormData(data, coverImageFile),
      })
    ).data;
  },

  async remove(id) {
    return (await apiClient.request(`/approver/news/${encodeURIComponent(id)}`, { method: 'DELETE' })).data;
  },
};
