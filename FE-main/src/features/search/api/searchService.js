// src/services/searchService.js
// Data layer — swap Promise.resolve() with fetch('/api/search') when backend is ready

import { TOP_MATCHES_MOCK, MORE_RESULTS_MOCK } from '@/data/mockData';

/**
 * Search items by query and optional category
 * @param {string} query - Search keyword
 * @param {string} category - Category filter ('all' for no filter)
 * @returns {Promise<{ topMatches: Array, moreResults: Array }>}
 */
export async function searchItems(query, category = 'all') {
  // TODO:
  // const params = new URLSearchParams({ q: query, cat: category });
  // return await fetch(`/api/search?${params}`).then(r => r.json());

  const keyword = query.toLowerCase().trim();
  const filterItem = (item) => {
    const matchesQ = !keyword ||
      item.title?.toLowerCase().includes(keyword) ||
      item.category?.toLowerCase().includes(keyword) ||
      item.location?.toLowerCase().includes(keyword);
    const matchesCat = category === 'all' || item.category === category;
    return matchesQ && matchesCat;
  };

  return Promise.resolve({
    topMatches: TOP_MATCHES_MOCK.filter(filterItem),
    moreResults: MORE_RESULTS_MOCK.filter(filterItem),
  });
}

/**
 * Get AI match results for a given item ID
 * @param {string} itemId
 * @returns {Promise<Array>}
 */
export async function getAiMatches(itemId) {
  // TODO: return await fetch(`/api/items/${itemId}/matches`).then(r => r.json());
  return Promise.resolve([]);
}
