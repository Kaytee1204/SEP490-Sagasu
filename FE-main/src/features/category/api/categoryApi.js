import apiClient from '@/services/apiClient';

export const categoryApi = {
  async list({ page = 1, size = 10, keyword = '', active = '' } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword.trim()) params.set('keyword', keyword.trim());
    if (active !== '') params.set('active', String(active));
    return (await apiClient.get(`/categories?${params}`)).data;
  },
  async create(name) {
    return (await apiClient.post('/categories', { name })).data;
  },
  async rename(id, name) {
    return (await apiClient.put(`/categories/${id}`, { name })).data;
  },
  async setActive(id, active) {
    return (await apiClient.patch(`/categories/${id}/status`, { active })).data;
  },
};
