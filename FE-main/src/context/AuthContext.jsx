// src/context/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '@/features/auth/api/authApi';
import { accountApi } from '@/features/account/api/accountApi';
import { apiClient } from '@/services/apiClient';

const AuthContext = createContext(null);

const LEGACY_STORAGE_KEYS = ['timdo_auth_user', 'timdo_auth_token'];

export function AuthProvider({ children }) {
  // Không lưu phiên đăng nhập vào localStorage — mỗi lần vào/tải lại web đều yêu cầu đăng nhập lại cho mọi role
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(false);

  // Xóa sạch dữ liệu đăng nhập cũ nếu trước đó từng được lưu trong localStorage
  useEffect(() => {
    try {
      LEGACY_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
    } catch {
      // ignore storage errors
    }
  }, []);

  useEffect(() => {
    apiClient.setToken(token);
  }, [token]);

  const applySession = (nextToken, nextAccount) => {
    apiClient.setToken(nextToken || null);
    setToken(nextToken || null);
    setUser(nextAccount || null);
  };

  const login = async (email, password) => {
    setLoading(true);
    try {
      const data = await authApi.login(email, password);
      if (data && !data.requiresOtp && data.accessToken && data.account) {
        applySession(data.accessToken, data.account);
      }
      return data;
    } finally {
      setLoading(false);
    }
  };

  const loginWithGoogle = async (payload) => {
    setLoading(true);
    try {
      const data = await authApi.loginWithGoogle(payload);
      if (data && !data.requiresOtp && data.accessToken && data.account) {
        applySession(data.accessToken, data.account);
      }
      return data;
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async (email, otp, purpose = 'LOGIN') => {
    setLoading(true);
    try {
      const data = await authApi.verifyOtp(email, otp, purpose);
      if (data && data.accessToken && data.account) {
        applySession(data.accessToken, data.account);
      }
      return data;
    } finally {
      setLoading(false);
    }
  };

  const requestChangePasswordOtp = async (email, currentPassword) => {
    return await authApi.requestChangePasswordOtp(email, currentPassword);
  };

  const confirmChangePassword = async (email, otp, newPassword) => {
    const result = await authApi.confirmChangePassword(email, otp, newPassword);
    await logout();
    return result;
  };

  const forgotPassword = async (email) => {
    return await authApi.forgotPassword(email);
  };

  const resetPassword = async (email, otp, newPassword) => {
    return await authApi.resetPassword(email, otp, newPassword);
  };

  const logout = async () => {
    apiClient.setToken(null);
    setUser(null);
    setToken(null);
    try {
      LEGACY_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
    } catch {
      // ignore storage errors
    }
  };

  const refreshProfile = async () => {
    try {
      const latestAccount = await accountApi.getMyProfile();
      if (latestAccount) {
        setUser(latestAccount);
        return latestAccount;
      }
    } catch (err) {
      console.warn('Could not refresh profile from DB:', err);
    }
    return user;
  };

  const updateUser = async (updatedFields) => {
    const updatedAccount = await accountApi.updateMyProfile(updatedFields);
    if (updatedAccount) {
      setUser((prev) => (prev ? { ...prev, ...updatedAccount } : updatedAccount));
      return updatedAccount;
    }
    setUser((prev) => (prev ? { ...prev, ...updatedFields } : updatedFields));
    return updatedFields;
  };

  const role = user?.role ? String(user.role).toUpperCase() : '';
  // Chỉ ADMIN và MANAGER được vào trang Dashboard (/admin)
  const isAdmin = role === 'ADMIN' || role === 'MANAGER';
  const isPureAdmin = role === 'ADMIN';
  const isManager = role === 'MANAGER';
  const isApprover = role === 'APPROVER';

  const value = {
    user,
    token,
    loading,
    isAuthenticated: Boolean(user && token),
    isAdmin,
    isPureAdmin,
    isManager,
    isApprover,
    login,
    loginWithGoogle,
    verifyOtp,
    requestChangePasswordOtp,
    confirmChangePassword,
    logout,
    forgotPassword,
    resetPassword,
    refreshProfile,
    updateUser,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export default AuthContext;
