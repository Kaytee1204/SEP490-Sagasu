// src/services/reportService.js
// Data layer — swap Promise.resolve() with fetch('/api/reports') when backend is ready

import { INITIAL_REPORTS } from '@/data/mockData';

/**
 * Get all violation reports
 * @returns {Promise<Array>}
 */
export async function getReports() {
  // TODO: return await fetch('/api/reports').then(r => r.json());
  return Promise.resolve(INITIAL_REPORTS);
}

/**
 * Get a single report by ID
 * @param {string} id
 * @returns {Promise<Object|null>}
 */
export async function getReportById(id) {
  // TODO: return await fetch(`/api/reports/${id}`).then(r => r.json());
  const report = INITIAL_REPORTS.find(r => r.id === id);
  return Promise.resolve(report ?? null);
}

/**
 * Resolve a report (mark as handled)
 * @param {string} id
 * @param {string} reason - Admin's resolution note
 * @returns {Promise<void>}
 */
export async function resolveReport(id, reason) {
  // TODO: return await fetch(`/api/reports/${id}/resolve`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Dismiss a report (reject the claim)
 * @param {string} id
 * @param {string} reason
 * @returns {Promise<void>}
 */
export async function dismissReport(id, reason) {
  // TODO: return await fetch(`/api/reports/${id}/dismiss`, { method: 'PATCH', body: JSON.stringify({ reason }) });
  return Promise.resolve();
}

/**
 * Reopen a previously closed report
 * @param {string} id
 * @returns {Promise<void>}
 */
export async function reopenReport(id) {
  // TODO: return await fetch(`/api/reports/${id}/reopen`, { method: 'PATCH' });
  return Promise.resolve();
}
