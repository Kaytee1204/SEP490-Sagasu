// IndexedDB preserves File blobs as well as text without uploading draft content.
let database;
function openDatabase() {
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open('sagasu-post-drafts', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('drafts');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => { database = null; reject(request.error); };
    request.onblocked = () => { database = null; reject(new Error('Draft storage blocked')); };
  });
  return database;
}
async function transaction(mode, operation) {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('drafts', mode);
    const request = operation(tx.objectStore('drafts'));
    tx.oncomplete = () => resolve(request.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}
export const readPostDraft = key => transaction('readonly', store => store.get(key));
export const savePostDraft = (key, draft) => transaction('readwrite', store => store.put(draft, key));
export const deletePostDraft = key => transaction('readwrite', store => store.delete(key));
