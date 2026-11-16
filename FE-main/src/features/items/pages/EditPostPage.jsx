import { useState } from 'react';
import { useParams } from 'react-router-dom';
import PostEditor from '../components/post/PostEditor';

export default function EditPostPage() {
  const { id }=useParams();
  const [attempt,setAttempt]=useState(0);
  return <PostEditor key={`${id}:${attempt}`} id={id} onReload={()=>setAttempt(n=>n+1)} />;
}
