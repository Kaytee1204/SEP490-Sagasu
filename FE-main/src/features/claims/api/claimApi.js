import apiClient from '@/services/apiClient';

const data = async response => (await response).data;
async function updateClaim(response) {
  const result = await data(response);
  window.dispatchEvent(new Event('sagasu-counts-changed'));
  return result;
}
const query = (page, status) => new URLSearchParams({ page: String(page), size: '12', status });
export const getMyClaims = (page = 1, status = 'ALL') => data(apiClient.get(`/claims/mine?${query(page, status)}`));
export const getReceivedClaims = (page = 1, status = 'ALL') => data(apiClient.get(`/claims/received?${query(page, status)}`));
export const getPendingClaimReviewCount = () => data(apiClient.get('/claims/pending-review-count'));
export const getOfficeClaims = (page = 1, status = 'ALL') => data(apiClient.get(`/claims/office?${query(page, status)}`));
export const getClaimsOnPost = (postId, page = 1, status = 'ALL') => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims?${query(page, status)}`));
export const getClaim = id => data(apiClient.get(`/claims/${encodeURIComponent(id)}`));
export const getClaimOnPost = (postId, id) => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims/${encodeURIComponent(id)}`));
export const getLatestClaimOnPost = postId => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims/mine`));
export const getClaimQuestionnaire = postId => data(apiClient.get(`/posts/${encodeURIComponent(postId)}/claims/questions`));
export const reviewClaim = (id, request) => updateClaim(apiClient.post(`/claims/${encodeURIComponent(id)}/review`, request));
export const cancelClaim = (id, request) => updateClaim(apiClient.post(`/claims/${encodeURIComponent(id)}/cancel`, request));
export async function submitClaim(postId, request, images, retryId) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  images.forEach(image => form.append('images', image));
  const path = retryId ? `/claims/${encodeURIComponent(retryId)}/retry` : `/posts/${encodeURIComponent(postId)}/claims`;
  return updateClaim(apiClient.upload(path, form));
}

export const getGroupedClaims = (page = 1, status = 'ALL', sent = false, postId = null, size = 8) =>
  data(apiClient.get(`/claims/grouped?${new URLSearchParams({ page: String(page), size: String(size), status, sent: String(sent), ...(postId ? { postId } : {}) })}`));

export const revokeClaimApproval = (id, request) => updateClaim(apiClient.post(`/claims/${encodeURIComponent(id)}/revoke-approval`, request));

export const getClaimAppeal = id => data(apiClient.get(`/claims/${encodeURIComponent(id)}/appeal`));

export function submitClaimAppeal(id, request, images) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  images.forEach(image => form.append('images', image));
  return data(apiClient.upload(`/claims/${encodeURIComponent(id)}/appeal`, form));
}
