import apiClient from '@/services/apiClient';

export const approverApi = {
  async list({ page = 1, keyword = '', filter = 'PENDING', source = 'ALL' }) {
    return (await apiClient.get(`/approver/receipts?${new URLSearchParams({ page, size: 10, keyword, filter, source })}`)).data;
  },
  async detail(id) { return (await apiClient.get(`/approver/receipts/${encodeURIComponent(id)}`)).data; },
  async review(id, data) { return (await apiClient.post(`/approver/receipts/${id}/review`, data)).data; },
};
