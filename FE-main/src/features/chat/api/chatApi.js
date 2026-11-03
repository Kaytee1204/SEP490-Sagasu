import apiClient from '@/services/apiClient';

export const openPostChat = async postId => (await apiClient.post(`/posts/${encodeURIComponent(postId)}/chat`, {})).data;
export const getChat = async id => (await apiClient.get(`/chats/${encodeURIComponent(id)}`)).data;
export const getChats = async cursor => (await apiClient.get(`/chats?${new URLSearchParams({ size: '20', ...(cursor ? { cursor } : {}) })}`)).data;
export const getMessages = async (id, query = {}) => (await apiClient.get(`/chats/${encodeURIComponent(id)}/messages?${new URLSearchParams({ limit: '50', ...query })}`)).data;
export const sendMessage = async (id, request) => (await apiClient.post(`/chats/${encodeURIComponent(id)}/messages`, request)).data;
export const getUnreadCount = async () => (await apiClient.get('/chats/unread-count')).data;
export const markChatRead = async (id, sequence) => (await apiClient.post(`/chats/${encodeURIComponent(id)}/read`, { lastReadSequence: sequence })).data;
export const getChatLimits = async () => (await apiClient.get('/chats/limits')).data;
export const sendLocation = async (id, request) => (await apiClient.post(`/chats/${encodeURIComponent(id)}/locations`, request)).data;
export const getReadReceipts = async (id, after, through) => (await apiClient.get(`/chats/${encodeURIComponent(id)}/read-receipts?${new URLSearchParams({ afterSequence: String(after), throughSequence: String(through) })}`)).data;
export async function sendAttachments(id, request, files) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  files.forEach(file => form.append('files', file));
  return (await apiClient.upload(`/chats/${encodeURIComponent(id)}/messages`, form)).data;
}
export async function downloadChatFile(id, attachment) {
  const response = await fetch(`${apiClient.getBaseUrl()}/chats/${encodeURIComponent(id)}/attachments/${attachment.id}/download`, { headers: apiClient.getHeaders({}, true) });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.message || 'Không tải được tệp.');
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a'); link.href = url; link.download = attachment.name || 'attachment';
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
