// src/hooks/useActionModal.js
// Reusable hook for admin confirmation modals that require a written reason.
// Used in AdminItemsPage and AdminUsersPage.

import { useState } from 'react';

const INITIAL_STATE = { isOpen: false, type: '', payload: null, title: '', placeholder: '' };

/**
 * @returns {{
 *   actionModal: Object,
 *   actionReason: string,
 *   openActionModal: (config: Object) => void,
 *   closeActionModal: () => void,
 *   setActionReason: (reason: string) => void,
 * }}
 */
export function useActionModal() {
  const [actionModal, setActionModal] = useState(INITIAL_STATE);
  const [actionReason, setActionReason] = useState('');

  const openActionModal = ({ type, payload, title, placeholder = 'Nhập lý do...' }) => {
    setActionModal({ isOpen: true, type, payload, title, placeholder });
    setActionReason('');
  };

  const closeActionModal = () => {
    setActionModal(INITIAL_STATE);
    setActionReason('');
  };

  return { actionModal, actionReason, openActionModal, closeActionModal, setActionReason };
}
