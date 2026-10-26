// src/services/userService.js
// Data layer — swap Promise.resolve() with fetch('/api/users') when backend is ready

import { USERS_LIST, CURRENT_USER } from '@/data/mockData';

/**
 * Get all users (admin only)
 * @returns {Promise<Array>}
 */
export async function getUsers() {
  // TODO: return await fetch('/api/admin/users').then(r => r.json());
  return Promise.resolve(USERS_LIST);
}

/**
 * Get a single user by ID
 * @param {string} id
 * @returns {Promise<Object|null>}
 */
export async function getUserById(id) {
  // TODO: return await fetch(`/api/users/${id}`).then(r => r.json());
  const user = USERS_LIST.find(u => u.id === id);
  return Promise.resolve(user ?? null);
}

/**
 * Get the currently logged-in user's profile
 * @returns {Promise<Object>}
 */
export async function getCurrentUser() {
  // TODO: return await fetch('/api/auth/me').then(r => r.json());
  return Promise.resolve(CURRENT_USER);
}

/**
 * Ban (temporarily lock) a user account
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function banUser(id, reason) {
  // TODO: return await fetch(`/api/admin/users/${id}/ban`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Unban a user account
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function unbanUser(id, reason) {
  // TODO: return await fetch(`/api/admin/users/${id}/unban`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Delete a user account permanently
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function deleteUser(id, reason) {
  // TODO: return await fetch(`/api/admin/users/${id}`, { method: 'DELETE', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Create a new user (admin)
 * @param {Object} userData
 * @returns {Promise<Object>} - Created user object
 */
export async function createUser(userData) {
  // TODO: return await fetch('/api/admin/users', { method: 'POST', body: JSON.stringify(userData) }).then(r => r.json());
  const newUser = { ...userData, id: `USR-${Date.now()}` };
  return Promise.resolve(newUser);
}

/**
 * Update a user's data (admin)
 * @param {string} id
 * @param {Object} updates
 * @returns {Promise<Object>} - Updated user object
 */
export async function updateUser(id, updates) {
  // TODO: return await fetch(`/api/admin/users/${id}`, { method: 'PATCH', body: JSON.stringify(updates) }).then(r => r.json());
  const user = USERS_LIST.find(u => u.id === id);
  return Promise.resolve({ ...user, ...updates });
}
