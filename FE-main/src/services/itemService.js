// src/services/itemService.js
// Data layer — swap Promise.resolve() with fetch('/api/items') when backend is ready

import { INITIAL_ITEMS } from '../data/mockData';

/**
 * Get all items (paginated in future)
 * @returns {Promise<Array>}
 */
export async function getItems() {
  // TODO: return await fetch('/api/items').then(r => r.json());
  return Promise.resolve(INITIAL_ITEMS);
}

/**
 * Get a single item by ID
 * @param {string} id
 * @returns {Promise<Object|null>}
 */
export async function getItemById(id) {
  // TODO: return await fetch(`/api/items/${id}`).then(r => r.json());
  const item = INITIAL_ITEMS.find(i => i.id === id);
  return Promise.resolve(item ?? null);
}

/**
 * Hide / remove an item from public view
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function hideItem(id, reason) {
  // TODO: return await fetch(`/api/items/${id}/hide`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Restore a previously hidden item
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function restoreItem(id, reason) {
  // TODO: return await fetch(`/api/items/${id}/restore`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}
