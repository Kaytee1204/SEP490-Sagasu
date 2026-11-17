import apiClient from '@/services/apiClient';

export async function getSavedPosts(page = 1, postType = '') {
  return (await apiClient.get(`/saved-posts?${new URLSearchParams({ page: String(page), size: '12', postType })}`)).data;
}

export async function getSavedPostIds(token) {
  return (await apiClient.get('/saved-posts/ids', { Authorization: `Bearer ${token}` })).data;
}

export function savePost(id) {
  return apiClient.put(`/saved-posts/${encodeURIComponent(id)}`, {});
}

export function unsavePost(id) {
  return apiClient.delete(`/saved-posts/${encodeURIComponent(id)}`);
}
