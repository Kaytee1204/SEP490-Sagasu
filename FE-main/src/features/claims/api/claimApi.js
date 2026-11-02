import apiClient from '@/services/apiClient';

const data = async response => (await response).data;
const query = (page, status) => new URLSearchParams({ page: String(page), size: '12', status });
export const getMyClaims = (page = 1, status = 'ALL') => data(apiClient.get(`/claims/mine?${query(page, status)}`));
export const getReceivedClaims = (page = 1, status = 'ALL') => data(apiClient.get(`/claims/received?${query(page, status)}`));
export const getClaimsOnPost = (postId, page = 1, status = 'ALL') => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims?${query(page, status)}`));
export const getClaim = id => data(apiClient.get(`/claims/${encodeURIComponent(id)}`));
export const getClaimOnPost = (postId, id) => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims/${encodeURIComponent(id)}`));
export const getLatestClaimOnPost = postId => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims/mine`));
export const reviewClaim = (id, request) => data(apiClient.post(`/claims/${encodeURIComponent(id)}/review`, request));
export const cancelClaim = (id, request) => data(apiClient.post(`/claims/${encodeURIComponent(id)}/cancel`, request));
export async function submitClaim(postId, request, images, retryId) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  images.forEach(image => form.append('images', image));
  const path = retryId ? `/claims/${encodeURIComponent(retryId)}/retry` : `/posts/${encodeURIComponent(postId)}/claims`;
  return data(apiClient.upload(path, form));
}
