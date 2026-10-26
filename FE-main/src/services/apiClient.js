// src/services/apiClient.js
const BASE_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api';

class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
  }

  getToken() {
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
      const response = await fetch(url, config);
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `Lỗi HTTP ${response.status}`);
      }
      return await response.json();
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
