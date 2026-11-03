import { useState } from 'react';
import ImageViewer from '@/components/common/ImageViewer';
import { downloadChatFile, getMessages } from '../api/chatApi';

export default function ChatAttachments({ chatId, message }) {
  const [viewer, setViewer] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function open(file) {
    if (busy) return;
    setBusy(true); setError('');
    try {
      if (file.mimeType.startsWith('image/')) {
        // Refresh expiring R2 URLs when opening the gallery, including old messages.
        const page = await getMessages(chatId, { beforeSequence: String(message.sequence + 1), limit: '1' });
        const images = (page.items[0]?.attachments || []).filter(item => item.mimeType.startsWith('image/') && item.url)
          .map(item => ({ id: item.id, name: item.name, url: item.url }));
        const index = images.findIndex(image => image.id === file.id);
        if (index < 0) throw new Error('Ảnh tạm thời chưa sẵn sàng.');
        setViewer({ images, index });
      } else await downloadChatFile(chatId, file);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <><div className="chat-files">{(message.attachments || []).map(file => <button key={file.id} className="chat-file" type="button" disabled={busy} onClick={() => open(file)}>
    {file.mimeType.startsWith('image/') && file.url && <img src={file.url} alt={file.name || 'Ảnh đính kèm'} loading="lazy" />}
    <span>{file.name || 'Tệp đính kèm'}</span><small>{(file.sizeBytes / 1024).toFixed(0)} KB{file.mimeType === 'application/pdf' ? ' · Tải PDF' : ''}</small>
  </button>)}</div>{error && <p role="alert">{error}</p>}
    {viewer && <ImageViewer images={viewer.images} initialIndex={viewer.index} title="Ảnh trong tin nhắn" onClose={() => setViewer(null)} />}
  </>;
}
