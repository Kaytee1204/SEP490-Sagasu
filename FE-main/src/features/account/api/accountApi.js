// src/features/account/api/accountApi.js
import apiClient from '@/services/apiClient';

export const accountApi = {
  async getMyProfile() {
    const res = await apiClient.get('/accounts/me');
    return res.data;
  },

  async getMyAuditLogs() {
    const res = await apiClient.get('/accounts/me/audit-logs');
    return res.data;
  },

  async updateMyProfile(payload) {
    const res = await apiClient.put('/accounts/me', payload);
    return res.data;
  },

  async getAccountsPage({
    page = 1,
    size = 10,
    keyword = '',
    role = '',
    status = '',
    includeDeleted = false,
  } = {}) {
    const params = new URLSearchParams();
    params.set('page', String(page));
    params.set('size', String(size));
    params.set('includeDeleted', String(includeDeleted));
    if (keyword && keyword.trim()) params.set('keyword', keyword.trim());
    if (role && role !== 'ALL') params.set('role', role);
    if (status && status !== 'ALL') params.set('status', status);

    const res = await apiClient.get(`/accounts?${params.toString()}`);
    return res.data;
  },

  async getAccountById(id) {
    const res = await apiClient.get(`/accounts/${id}`);
    return res.data;
  },

  async createAccount(payload) {
    const res = await apiClient.post('/accounts', payload);
    return res.data;
  },

  async updateAccount(id, payload) {
    const res = await apiClient.put(`/accounts/${id}`, payload);
    return res.data;
  },

  async softDeleteAccount(id) {
    const res = await apiClient.delete(`/accounts/${id}`);
    return res.data;
  },

  async importAccountsFromExcel(file) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.upload('/accounts/import', formData);
    return res.data;
  },

  getTemplateDownloadUrl() {
    return `${apiClient.getBaseUrl()}/accounts/import/template`;
  },
};

export default accountApi;
