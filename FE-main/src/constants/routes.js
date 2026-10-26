// src/constants/routes.js
// All application route paths in one place.
// Prevents magic string duplication across Link/navigate calls.

export const ROUTES = {
  HOME: '/',
  LOGIN: '/dang-nhap',
  REGISTER: '/dang-ky',
  POST_ITEM: '/dang-tin',
  POST_FOUND_ITEM: '/dang-tin-nhat-duoc',
  SEARCH: '/tim-kiem',
  ITEM_DETAIL: (id) => `/chi-tiet/${id}`,
  SMART_MATCH: '/smart-match',
  CLAIM_ITEM: (id) => `/xac-minh/${id}`,
  PROFILE: '/ho-so',
  MY_POSTS: '/tin-dang-cua-toi',

  // Admin
  ADMIN: '/admin',
  ADMIN_ITEMS: '/admin/items',
  ADMIN_REPORTS: '/admin/reports',
  ADMIN_USERS: '/admin/users',
  ADMIN_USER_DETAIL: (id) => `/admin/users/${id}`,
};
