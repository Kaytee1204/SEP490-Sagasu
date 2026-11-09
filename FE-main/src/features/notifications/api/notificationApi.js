import apiClient from '@/services/apiClient';

export const getNotifications = async ({ unreadOnly = false, page = 1, size = 15 } = {}) => {
  const params = new URLSearchParams({
    unreadOnly: String(unreadOnly),
    page: String(page),
    size: String(size),
  });
  const res = await apiClient.get(`/notifications?${params.toString()}`);
  return res.data;
};

export const getUnreadNotificationCount = async () => {
  const res = await apiClient.get('/notifications/unread-count');
  return res.data;
};

export const markNotificationAsRead = async (id) => {
  const res = await apiClient.patch(`/notifications/${encodeURIComponent(id)}/read`, {});
  return res.data;
};

export const markAllNotificationsAsRead = async () => {
  const res = await apiClient.patch('/notifications/read-all', {});
  return res.data;
};

export const getNotificationPreferences = async () => {
  const res = await apiClient.get('/notifications/preferences');
  return res.data;
};

export const updateNotificationPreferences = async (payload) => {
  const res = await apiClient.put('/notifications/preferences', payload);
  return res.data;
};
