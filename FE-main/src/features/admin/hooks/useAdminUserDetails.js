import { useState, useEffect } from 'react';
import { USERS_LIST } from '../pages/mockData';

export function useAdminUserDetails(id) {
  const [user, setUser] = useState(null);
  const [viewTab, setViewTab] = useState('overview');
  const [toastMessage, setToastMessage] = useState(null);

  useEffect(() => {
    const foundUser = USERS_LIST.find(u => u.id === id);
    if (foundUser) {
      setUser(foundUser);
    }
  }, [id]);

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const handleSendWarning = () => {
    showToast(`Đã gửi thông báo cảnh báo đến ${user.email}`);
  };

  const handleToggleBan = () => {
    setUser(prev => ({
      ...prev,
      status: prev.status === 'Hoạt động' ? 'Đã tạm khóa' : 'Hoạt động'
    }));
    showToast(`Đã ${user.status === 'Hoạt động' ? 'khóa' : 'mở khóa'} tài khoản ${user.id}`);
  };

  return {
    user,
    viewTab,
    setViewTab,
    toastMessage,
    handleSendWarning,
    handleToggleBan
  };
}
