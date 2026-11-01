import apiClient from '@/services/apiClient';

export const receiptApi = {
  async categories() { return (await apiClient.get('/posts/categories')).data.map(item => ({ categoryId: item.id, name: item.name })); },
  async locations() { return (await apiClient.get('/storage-locations')).data; },
};
