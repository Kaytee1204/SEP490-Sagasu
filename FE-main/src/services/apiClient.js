// src/services/apiClient.js
import { prepareImageFormData } from '@/utils/imageUploads';
const BASE_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api';

const TITLE_FIELDS = new Set(['title', 'postTitle', 'itemTitle', 'post_title']);

export function stripPostCode(text) {
  if (typeof text !== 'string') return text;
  const cleaned = text.replace(/\s*#\d+\b/g, '').trim();
  return cleaned || text.trim();
}

function sanitizeTitlesInPayload(node) {
  if (!node || typeof node !== 'object') return node;
  if (Array.isArray(node)) {
    for (let i = 0; i < node.length; i += 1) {
      node[i] = sanitizeTitlesInPayload(node[i]);
    }
    return node;
  }
  for (const key of Object.keys(node)) {
    const val = node[key];
    if (TITLE_FIELDS.has(key) && typeof val === 'string') {
      node[key] = stripPostCode(val);
    } else if (val && typeof val === 'object') {
      node[key] = sanitizeTitlesInPayload(val);
    }
  }
  return node;
}

class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.token = null;
  }

  setToken(token) {
    this.token = token || null;
  }

  getToken() {
    if (this.token) {
      return this.token;
    }
    try {
      return localStorage.getItem('timdo_auth_token') || null;
    } catch {
      return null;
    }
  }

  getHeaders(customHeaders = {}, isMultipart = false) {
    const headers = isMultipart
      ? { ...customHeaders }
      : {
          'Content-Type': 'application/json',
          ...customHeaders,
        };
    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
    const isMultipart = options.body instanceof FormData;
    const config = {
      ...options,
      headers: this.getHeaders(options.headers, isMultipart),
    };

    try {
      if (isMultipart) config.body = await prepareImageFormData(options.body);
      const response = await fetch(url, config);
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        if (
          response.status === 401 &&
          this.getToken() &&
          !endpoint.includes('/auth/login') &&
          typeof window !== 'undefined'
        ) {
          window.dispatchEvent(
            new CustomEvent('sagasu:auth-expired', {
              detail: { message: errorData.message || 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' },
            })
          );
        }
        const err = new Error(errorData.message || `Lỗi HTTP ${response.status}`);
        err.code = errorData.code;
        err.status = response.status;
        err.data = errorData;
        throw err;
      }
      const json = await response.json();
      return sanitizeTitlesInPayload(json);
    } catch (error) {
      console.warn(`[ApiClient] Request to ${url} failed:`, error.message);
      throw error;
    }
  }

  get(endpoint, headers = {}) {
    return this.request(endpoint, { method: 'GET', headers });
  }

  post(endpoint, body, headers = {}) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body),
      headers,
    });
  }

  put(endpoint, body, headers = {}) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers,
    });
  }

  patch(endpoint, body, headers = {}) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers,
    });
  }

  delete(endpoint, headers = {}) {
    return this.request(endpoint, { method: 'DELETE', headers });
  }

  upload(endpoint, formData, headers = {}) {
    return this.request(endpoint, {
      method: 'POST',
      body: formData,
      headers,
    });
  }

  getBaseUrl() {
    return this.baseUrl;
  }
}

export const apiClient = new ApiClient(BASE_URL);
export default apiClient;
