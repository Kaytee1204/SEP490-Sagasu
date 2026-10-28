import apiClient from '@/services/apiClient';

export async function getVisiblePosts(page = 1, size = 12) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const response = await apiClient.get(`/posts?${params}`);
  return response.data;
}

export async function getPostDetail(id) {
  const response = await apiClient.get(`/posts/${encodeURIComponent(id)}`);
  return response.data;
}

export async function getPostCategories() {
  const response = await apiClient.get('/posts/categories');
  return response.data;
}

export async function createLostPost(data, images) {
  return createPost('lost', data, images);
}

export async function createFoundPost(data, images) {
  return createPost('found', data, images);
}

async function createPost(type, data, images) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  images.forEach(file => form.append('images', file));
  const response = await apiClient.upload(`/posts/${type}`, form);
  return response.data;
}

export async function getPrivateIdentifiers(id) {
  const response = await apiClient.get(`/posts/${encodeURIComponent(id)}/private-identifiers`);
  return response.data;
}

export async function updatePrivateIdentifiers(id, privateIdentifiers) {
  return apiClient.put(`/posts/${encodeURIComponent(id)}/private-identifiers`, { privateIdentifiers });
}

export async function getMyPosts(page = 1, filter = 'ALL') {
  return (await apiClient.get(`/posts/mine?${new URLSearchParams({page: String(page), size: '12', filter})}`)).data;
}

export async function getPostForEdit(id) {
  return (await apiClient.get(`/posts/${encodeURIComponent(id)}/edit`)).data;
}

export async function updatePost(id, data, images) {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  images.forEach(file => form.append('images', file));
  return (await apiClient.request(`/posts/${encodeURIComponent(id)}`, { method: 'PUT', body: form })).data;
}

export async function deletePost(id, reason) {
  return apiClient.request(`/posts/${encodeURIComponent(id)}`, {
    method: 'DELETE', body: JSON.stringify({ reason }),
  });
}
