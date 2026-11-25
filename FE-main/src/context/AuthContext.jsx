// src/context/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { authApi } from '@/features/auth/api/authApi';
import { accountApi } from '@/features/account/api/accountApi';
import { apiClient } from '@/services/apiClient';

const AuthContext = createContext(null);

const STORAGE_KEY_USER = 'timdo_auth_user';
const STORAGE_KEY_TOKEN = 'timdo_auth_token';

function getJwtExpirationMs(jwt) {
  if (!jwt || typeof jwt !== 'string') return null;
  try {
    const parts = jwt.split('.');
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const payload = JSON.parse(atob(padded));
    return typeof payload?.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

function isTokenExpired(jwt) {
  const expMs = getJwtExpirationMs(jwt);
  if (expMs === null) return false;
  return expMs <= Date.now();
}

function readStoredSession() {
  try {
    const savedToken = localStorage.getItem(STORAGE_KEY_TOKEN);
    const savedUser = localStorage.getItem(STORAGE_KEY_USER);
    if (!savedToken || !savedUser || isTokenExpired(savedToken)) {
      localStorage.removeItem(STORAGE_KEY_USER);
      localStorage.removeItem(STORAGE_KEY_TOKEN);
      return { user: null, token: null };
    }
    const parsedUser = JSON.parse(savedUser);
    if (parsedUser && parsedUser.fullName) {
      parsedUser.fullName = String(parsedUser.fullName).replace(/\s*\((Manager|Admin)\)/gi, '');
    }
    apiClient.setToken(savedToken);
    return { user: parsedUser, token: savedToken };
  } catch {
    return { user: null, token: null };
  }
}

export function AuthProvider({ children }) {
  // Giữ phiên đăng nhập qua F5 & mở lại web bằng localStorage, tự hết hạn theo JWT (24h)
  const [initialSession] = useState(readStoredSession);
  const [user, setUser] = useState(initialSession.user);
  const [token, setToken] = useState(initialSession.token);
  const [loading, setLoading] = useState(false);

  const logout = useCallback(async () => {
    apiClient.setToken(null);
    setUser(null);
    setToken(null);
    try {
      localStorage.removeItem(STORAGE_KEY_USER);
      localStorage.removeItem(STORAGE_KEY_TOKEN);
    } catch {
      // ignore storage errors
    }
  }, []);

  useEffect(() => {
    apiClient.setToken(token);
    try {
      if (token) {
        localStorage.setItem(STORAGE_KEY_TOKEN, token);
      } else {
        localStorage.removeItem(STORAGE_KEY_TOKEN);
      }
    } catch {
      // ignore storage errors
    }
  }, [token]);

  useEffect(() => {
    try {
      if (user) {
        localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(user));
      } else {
        localStorage.removeItem(STORAGE_KEY_USER);
      }
    } catch {
      // ignore storage errors
    }
  }, [user]);

  // Tự động đăng xuất đúng lúc JWT (24h) hết hạn ngay cả khi đang mở tab
  useEffect(() => {
    if (!token) return undefined;
    const expMs = getJwtExpirationMs(token);
    if (expMs === null) return undefined;
    const remainingMs = expMs - Date.now();
    if (remainingMs <= 0) {
      logout();
      return undefined;
    }
    const safeDelay = Math.min(remainingMs, 2147483647);
    const timer = setTimeout(() => {
      logout();
    }, safeDelay);
    return () => clearTimeout(timer);
  }, [token, logout]);

  // Tự động đăng xuất khi Backend trả 401 (hết hạn JWT, đổi mật khẩu ở thiết bị khác, hoặc bị khóa)
  useEffect(() => {
    const handleAuthExpired = () => {
      logout();
    };
    window.addEventListener('sagasu:auth-expired', handleAuthExpired);
    return () => window.removeEventListener('sagasu:auth-expired', handleAuthExpired);
  }, [logout]);

  // Khi mở lại web / F5 có sẵn token, đồng bộ kiểm tra ngầm với Backend
  useEffect(() => {
    if (!initialSession.token) return;
    let cancelled = false;
    accountApi
      .getMyProfile()
      .then((latestAccount) => {
        if (!cancelled && latestAccount) {
          setUser(latestAccount);
        }
      })
      .catch((err) => {
        if (!cancelled && err?.status === 401) {
          logout();
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
