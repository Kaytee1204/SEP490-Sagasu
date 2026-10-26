// src/features/auth/api/authApi.js
import apiClient from '@/services/apiClient';

export const authApi = {
  async login(email, password) {
    const res = await apiClient.post('/auth/login', { email, password });
    return res.data;
  },

  async requestChangePasswordOtp(email, currentPassword) {
    const res = await apiClient.post('/auth/change-password/request-otp', {
      email,
      currentPassword,
    });
    return res.data;
  },

  async confirmChangePassword(email, otp, newPassword) {
    const res = await apiClient.post('/auth/change-password/confirm', {
      email,
      otp,
      newPassword,
    });
    return res.data;
  },

  async sendOtp(email, purpose = 'LOGIN') {
    const res = await apiClient.post('/auth/send-otp', { email, purpose });
    return res.data;
  },

  async verifyOtp(email, otp, purpose = 'LOGIN') {
    const res = await apiClient.post('/auth/verify-otp', { email, otp, purpose });
    return res.data;
  },

  async forgotPassword(email) {
    const res = await apiClient.post('/auth/forgot-password', { email });
    return res.data;
  },

  async resetPassword(email, otp, newPassword) {
    const res = await apiClient.post('/auth/reset-password', {
      email,
      otp,
      newPassword,
    });
    return res.data;
  },
};

export default authApi;
