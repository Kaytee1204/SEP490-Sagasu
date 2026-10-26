// src/services/authService.js
import { authApi } from '@/features/auth/api/authApi';

export const authService = {
  ...authApi,
  async logout() {
    return Promise.resolve();
  },
};

export default authService;
