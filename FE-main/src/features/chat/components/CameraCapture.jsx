import { useEffect, useId, useRef, useState } from 'react';
import { Camera, Check, RotateCcw, X } from 'lucide-react';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_ACCEPT, IMAGE_MAX_BYTES } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';

export default function CameraCapture({ onClose, onAttach, maxImageBytes = IMAGE_MAX_BYTES }) {
  const titleId = useId();
  const dialog = useRef(null), video = useRef(null), stream = useRef(null), fileInput = useRef(null);
  const disposed = useRef(false), capturing = useRef(false);
  const cameraEpoch = useRef(0);
  const previewUrl = useRef('');
  const [ready, setReady] = useState(false), [working, setBusy] = useState(false);
  const { prepare, processing } = useImageUploads();
  const busy = working || processing;
  const [error, setError] = useState(''), [attempt, setAttempt] = useState(0);
  const [photo, setPhoto] = useState(null);

  function stopCamera() {
    stream.current?.getTracks().forEach(track => track.stop());
    stream.current = null;
    if (video.current) video.current.srcObject = null;
  }
  function showPhoto(file) {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = file ? URL.createObjectURL(file) : '';
    setPhoto(file ? { file, url: previewUrl.current } : null);
  }
  useEffect(() => {
    disposed.current = false;
    const element = dialog.current, previousFocus = document.activeElement, previousOverflow = document.body.style.overflow;
    element.showModal(); document.body.style.overflow = 'hidden';
    return () => {
      disposed.current = true; stopCamera(); element.close(); document.body.style.overflow = previousOverflow;
      if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);
  useEffect(() => {
    if (photo) { stopCamera(); return; }
    const epoch = ++cameraEpoch.current;
    let cancelled = false;
    async function startCamera() {
      setReady(false); setError('');
      if (!navigator.mediaDevices?.getUserMedia) {
        setError('Không mở được camera tại đây. Bạn có thể dùng nút Chọn ảnh từ thiết bị.');
        return;
      }
      try {
        const media = await navigator.mediaDevices.getUserMedia({ audio: false, video: {
          facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 },
        } });
        if (cancelled || epoch !== cameraEpoch.current) { media.getTracks().forEach(track => track.stop()); return; }
        stream.current = media; video.current.srcObject = media;
        await video.current.play();
      } catch (problem) {
        if (cancelled || epoch !== cameraEpoch.current) return;
        stopCamera(); setReady(false);
        setError(problem.name === 'NotAllowedError'
          ? 'Bạn chưa cho phép dùng camera. Hãy cấp quyền trong trình duyệt hoặc chọn ảnh từ thiết bị.'
          : 'Không mở được camera. Hãy kiểm tra camera có đang được ứng dụng khác sử dụng hoặc chọn ảnh từ thiết bị.');
      }
    }
    startCamera();
    return () => { cancelled = true; stopCamera(); };
  }, [attempt, photo]);
  async function capture() {
    const source = video.current;
    if (!ready || !source?.videoWidth || capturing.current) return;
    capturing.current = true; setBusy(true); setError('');
    try {
      const canvas = document.createElement('canvas');
      const scale = Math.min(1, 1920 / Math.max(source.videoWidth, source.videoHeight));
      canvas.width = Math.round(source.videoWidth * scale); canvas.height = Math.round(source.videoHeight * scale);
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Không tạo được ảnh. Hãy thử chụp lại.');
      context.drawImage(source, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .85));
      if (!blob) throw new Error('Không tạo được ảnh. Hãy thử chụp lại.');
      if (disposed.current) return;
      showPhoto(new File([blob], `chat-photo-${Date.now()}.jpg`, { type: 'image/jpeg' }));
      stopCamera(); setReady(false);
    } catch (problem) { if (!disposed.current) setError(problem.message); }
    finally { capturing.current = false; if (!disposed.current) setBusy(false); }
  }
  function retake() { showPhoto(null); setAttempt(value => value + 1); }
  async function attach() {
    if (!photo || busy || capturing.current) return;
    capturing.current = true;
    setBusy(true);
    try {
      if (await onAttach(photo.file) === false && !disposed.current) setError('Ảnh vượt giới hạn đính kèm. Hãy đóng camera và kiểm tra số lượng hoặc dung lượng tệp đang chọn.');
    } catch (problem) {
      if (!disposed.current) setError(problem.message || 'Chưa đính kèm được ảnh. Vui lòng thử lại.');
    } finally {
      capturing.current = false;
      if (!disposed.current) setBusy(false);
    }
  }
  async function pickPhoto(event) {
    const selected = [...(event.target.files || [])]; event.target.value = '';
    if (busy || !selected.length) return;
    cameraEpoch.current++;
    stopCamera(); setReady(false); setError('');
    try {
      const prepared = await prepare(selected, { maxBytes: maxImageBytes });
      if (prepared) showPhoto(prepared[0]);
    } catch (problem) { if (!disposed.current) setError(problem.message); }
  }
  function close() {
    if (!busy && !capturing.current) onClose();
  }
  return <dialog ref={dialog} aria-labelledby={titleId} className="chat-location-dialog chat-camera-dialog"
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}><Camera size={23} /> Chụp ảnh đính kèm</h2><button type="button" aria-label="Đóng camera" disabled={busy} onClick={close}><X size={23} /></button></header>
    <div className="chat-location-content">
      <div className="chat-camera-preview">
        <video ref={video} autoPlay playsInline muted style={{ display: photo ? 'none' : undefined }} onLoadedData={() => setReady(true)} aria-label="Hình ảnh từ camera" />
        {photo && <img src={photo.url} alt="Ảnh vừa chụp, chưa gửi" />}
        {!photo && !ready && !error && <p role="status">Đang mở camera…</p>}
      </div>
      <p className="chat-location-hint">Ảnh chỉ được gửi khi bạn đính kèm và bấm Gửi trong cuộc trò chuyện.</p>
      {error && <p className="chat-location-warning" role="alert">{error}</p>}
      <ImageProcessingStatus active={processing} />
      {!photo && <div className="chat-camera-fallback">
        {error && <button type="button" disabled={busy} onClick={() => setAttempt(value => value + 1)}>Thử mở lại camera</button>}
        <button type="button" disabled={busy} onClick={() => fileInput.current?.click()}>Chọn ảnh từ thiết bị</button>
      </div>}
      <input ref={fileInput} className="chat-file-input" type="file" accept={IMAGE_ACCEPT} disabled={busy} capture="environment" aria-label="Chọn ảnh chụp từ thiết bị" onChange={pickPhoto} />
    </div>
    <form onSubmit={event => { event.preventDefault(); if (photo) attach(); else capture(); }}><footer>
      {photo ? <button type="button" className="chat-location-cancel" disabled={busy} onClick={retake}><RotateCcw size={17} />Chụp lại</button>
        : <button type="button" className="chat-location-cancel" disabled={busy} onClick={close}>Hủy bỏ</button>}
      <button type="submit" className="chat-location-confirm" disabled={busy || (!photo && !ready)}>
        {photo ? <Check size={17} /> : <Camera size={17} />}{photo ? 'Đính kèm ảnh' : busy ? 'Đang chụp…' : 'Chụp ảnh'}
      </button>
    </footer></form>
  </dialog>;
}
