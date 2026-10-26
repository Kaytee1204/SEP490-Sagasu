// src/constants/status.js
// Standard statuses and roles used across the platform.

export const ITEM_TYPE = {
  LOST: 'lost',
  FOUND: 'found',
};

export const ITEM_STATUS = {
  ACTIVE: 'active',
  RETURNED: 'returned',
  PENDING: 'pending',
  HIDDEN: 'hidden',
  REJECTED: 'rejected',
};

export const REPORT_STATUS = {
  PENDING: 'pending',
  RESOLVED: 'resolved',
  DISMISSED: 'dismissed',
};

export const USER_STATUS = {
  ACTIVE: 'active',
  BANNED: 'banned',
  WARNING: 'warning',
};

export const USER_ROLES = {
  ADMIN: 'admin',
  USER: 'user',
};

export const STATUS_LABELS = {
  [ITEM_STATUS.ACTIVE]: 'Đang hiển thị',
  [ITEM_STATUS.RETURNED]: 'Đã trao trả',
  [ITEM_STATUS.PENDING]: 'Chờ duyệt',
  [ITEM_STATUS.HIDDEN]: 'Đã ẩn',
  [ITEM_STATUS.REJECTED]: 'Từ chối',
};
