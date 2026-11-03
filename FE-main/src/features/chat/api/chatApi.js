import apiClient from '@/services/apiClient';

export const openPostChat = async postId => (await apiClient.post(`/posts/${encodeURIComponent(postId)}/chat`, {})).data;
export const getChat = async id => (await apiClient.get(`/chats/${encodeURIComponent(id)}`)).data;
export const getChats = async cursor => (await apiClient.get(`/chats?${new URLSearchParams({ size: '20', ...(cursor ? { cursor } : {}) })}`)).data;
export const getMessages = async (id, query = {}) => (await apiClient.get(`/chats/${encodeURIComponent(id)}/messages?${new URLSearchParams({ limit: '50', ...query })}`)).data;
export const sendMessage = async (id, request) => (await apiClient.post(`/chats/${encodeURIComponent(id)}/messages`, request)).data;
export const getUnreadCount = async () => (await apiClient.get('/chats/unread-count')).data;
export const markChatRead = async (id, sequence) => (await apiClient.post(`/chats/${encodeURIComponent(id)}/read`, { lastReadSequence: sequence })).data;
