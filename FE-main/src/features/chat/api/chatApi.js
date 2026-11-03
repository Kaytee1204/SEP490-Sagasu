import apiClient from '@/services/apiClient';

export const openPostChat = async postId => (await apiClient.post(`/posts/${encodeURIComponent(postId)}/chat`, {})).data;
export const getChat = async id => (await apiClient.get(`/chats/${encodeURIComponent(id)}`)).data;
export const getChats = async cursor => (await apiClient.get(`/chats?${new URLSearchParams({ size: '20', ...(cursor ? { cursor } : {}) })}`)).data;
