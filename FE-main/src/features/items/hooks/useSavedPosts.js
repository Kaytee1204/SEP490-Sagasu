import { useContext } from 'react';
import { SavedPostsContext } from '../context/SavedPostsContext';

export function useSavedPosts() {
  return useContext(SavedPostsContext);
}
