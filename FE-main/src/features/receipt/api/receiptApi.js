import apiClient from '@/services/apiClient';

export const receiptApi = {
  async locations() { return (await apiClient.get('/storage-locations')).data; },
};
