import apiClient from '@/services/apiClient';

export const adminGovernanceApi = {
  async getDashboardOverview() {
    const res = await apiClient.get('/admin/dashboard');
    return res?.data || {};
  },

  async getAuditLogs({
    page = 1,
    size = 15,
    source = 'ALL',
    eventType = 'ALL',
    role = 'ALL',
    success = '',
    keyword = '',
    fromDate = '',
    toDate = '',
  } = {}) {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      source: source || 'ALL',
      eventType: eventType || 'ALL',
      role: role || 'ALL',
    });
    if (success !== '' && success !== null && success !== undefined) {
      params.set('success', String(success));
    }
    if (keyword && keyword.trim()) {
      params.set('keyword', keyword.trim());
    }
    if (fromDate) {
      params.set('fromDate', fromDate);
    }
    if (toDate) {
      params.set('toDate', toDate);
    }
    const res = await apiClient.get(`/admin/audit-logs?${params.toString()}`);
    return res?.data || { content: [], page: 1, size: 15, totalElements: 0, totalPages: 1, summary: {}, eventTypes: [] };
  },

  async listReportReasons({ targetGroup = 'ALL', active = '', keyword = '' } = {}) {
    const params = new URLSearchParams({ targetGroup: targetGroup || 'ALL' });
    if (active !== '' && active !== null && active !== undefined) {
      params.set('active', String(active));
    }
    if (keyword && keyword.trim()) {
      params.set('keyword', keyword.trim());
    }
    const res = await apiClient.get(`/report-reasons?${params.toString()}`);
    return Array.isArray(res?.data) ? res.data : [];
  },

  async createReportReason(payload) {
    const res = await apiClient.post('/report-reasons', payload);
    return res?.data;
  },

  async updateReportReason(code, payload) {
    const res = await apiClient.put(`/report-reasons/${encodeURIComponent(code)}`, payload);
    return res?.data;
  },

  async toggleReportReasonStatus(code, active) {
    const res = await apiClient.patch(`/report-reasons/${encodeURIComponent(code)}/status`, { active });
    return res?.data;
  },

  async deleteReportReason(code) {
    const res = await apiClient.delete(`/report-reasons/${encodeURIComponent(code)}`);
    return res?.data;
  },
};

export default adminGovernanceApi;
