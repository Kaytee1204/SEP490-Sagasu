// src/context/AuthContext.jsx
import React, { createContext, useContext, useState, useEffect } from 'react';
import { authApi } from '@/features/auth/api/authApi';
import { accountApi } from '@/features/account/api/accountApi';

const AuthContext = createContext(null);

const STORAGE_KEY_USER = 'timdo_auth_user';
const STORAGE_KEY_TOKEN = 'timdo_auth_token';

function enforceGroupRoleConstraint(account, userGroup) {
  if (!account || !account.role || !userGroup) return;
  const role = String(account.role).toUpperCase();
  if (userGroup === 'student' && role !== 'USER') {
    throw new Error(
      `Tài khoản này thuộc nhóm Cán bộ & Phòng ban (${role}). Vui lòng chọn mục 'Cán bộ & Phòng ban' để đăng nhập!`
    );
  }
  if (userGroup === 'staff' && role === 'USER') {
    throw new Error(
      "Tài khoản này là tài khoản Sinh viên. Vui lòng chọn mục 'Sinh viên' để đăng nhập!"
    );
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const savedUser = localStorage.getItem(STORAGE_KEY_USER);
      if (!savedUser) return null;
      const parsed = JSON.parse(savedUser);
      if (parsed && parsed.fullName) {
        parsed.fullName = String(parsed.fullName).replace(/\s*\((Manager|Admin)\)/gi, '');
      }
      return parsed;
    } catch {
      return null;
    }
  });

  const [token, setToken] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY_TOKEN) || null;
    } catch {
      return null;
    }
  });

  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (user) {
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(user));
    } else {
      localStorage.removeItem(STORAGE_KEY_USER);
    }
  }, [user]);

  useEffect(() => {
    if (token) {
      localStorage.setItem(STORAGE_KEY_TOKEN, token);
    } else {
      localStorage.removeItem(STORAGE_KEY_TOKEN);
    }
  }, [token]);

  const login = async (email, password, userGroup) => {
    setLoading(true);
    try {
      const data = await authApi.login(email, password, userGroup);
      if (data && data.account) {
        enforceGroupRoleConstraint(data.account, userGroup);
      }
      if (data && !data.requiresOtp && data.accessToken && data.account) {
        setToken(data.accessToken);
        setUser(data.account);
      }
      return data;
    } finally {
      setLoading(false);
    }
  };

  const loginWithGoogle = async (payload, userGroup = 'student') => {
    setLoading(true);
    try {
      const data = await authApi.loginWithGoogle(payload);
      if (data && data.account) {
        enforceGroupRoleConstraint(data.account, userGroup);
      }
      if (data && !data.requiresOtp && data.accessToken && data.account) {
        setToken(data.accessToken);
        setUser(data.account);
      }
      return data;
    } finally {
      setLoading(false);
    }
  };

  const verifyOtp = async (email, otp, purpose = 'LOGIN', userGroup) => {
    setLoading(true);
    try {
      const data = await authApi.verifyOtp(email, otp, purpose);
      if (data && data.account) {
        enforceGroupRoleConstraint(data.account, userGroup);
      }
      if (data && data.accessToken && data.account) {
        setToken(data.accessToken);
        setUser(data.account);
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
    return await authApi.confirmChangePassword(email, otp, newPassword);
  };

  const forgotPassword = async (email) => {
    return await authApi.forgotPassword(email);
  };

  const resetPassword = async (email, otp, newPassword) => {
    return await authApi.resetPassword(email, otp, newPassword);
  };

  const logout = async () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem(STORAGE_KEY_USER);
    localStorage.removeItem(STORAGE_KEY_TOKEN);
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
  const isChecker = role === 'CHECKER';
  const isApprover = role === 'APPROVER';

  const value = {
    user,
    token,
    loading,
    isAuthenticated: Boolean(user && token),
    isAdmin,
    isPureAdmin,
    isManager,
    isChecker,
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
