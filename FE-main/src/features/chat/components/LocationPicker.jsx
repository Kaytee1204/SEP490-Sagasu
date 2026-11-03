import { useEffect, useId, useRef, useState } from 'react';
import { CheckCircle, Crosshair, Info, LocateFixed, MapPin, X } from 'lucide-react';
import { getLocationAddress, sendLocation } from '../api/chatApi';
import { locationPoint } from '../locationUtils';

export default function LocationPicker({ chatId, onClose, onSent }) {
  const titleId = useId();
  const dialog = useRef(null), canvas = useRef(null), map = useRef(null);
  const selected = useRef(null), choose = useRef(null), disposed = useRef(false), busyRef = useRef(false);
  const gpsRequest = useRef(0), retry = useRef(null);
  const [point, setPoint] = useState(null), [label, setLabel] = useState(null);
  const [error, setError] = useState(''), [mapError, setMapError] = useState(''), [addressError, setAddressError] = useState('');
  const [ready, setReady] = useState(false), [locating, setLocating] = useState(false), [busy, setBusy] = useState(false);
  const [addressLoading, setAddressLoading] = useState(false), [accuracy, setAccuracy] = useState(null);
  const [mapAttempt, setMapAttempt] = useState(0), [addressAttempt, setAddressAttempt] = useState(0);
  selected.current = point;
  function select(value) {
    if (busyRef.current || disposed.current) return;
    const valid = value && locationPoint(value.latitude, value.longitude);
    if (!valid) return;
    gpsRequest.current++; setLocating(false); setAccuracy(null); retry.current = null;
    setPoint(valid); setLabel(null); setAddressLoading(true); setError(''); setAddressError('');
  }
  choose.current = select;

  useEffect(() => {
    disposed.current = false;
    const element = dialog.current, previousFocus = document.activeElement, previousOverflow = document.body.style.overflow;
    element.showModal(); document.body.style.overflow = 'hidden';
    return () => {
      disposed.current = true; gpsRequest.current++; element.close(); document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    let cancelled = false, instance;
    setReady(false); setMapError('');
    import('../chatMap').then(({ createChatMap }) => {
      if (cancelled) return;
      instance = createChatMap(canvas.current, {
        onPick: value => { if (!cancelled) choose.current(value); },
        onReady: () => { if (!cancelled) setReady(true); },
        onError: value => { if (!cancelled) setMapError(value); },
      });
      map.current = instance;
      if (selected.current) { instance.setPoint(selected.current); instance.focus(selected.current); }
    }).catch(problem => {
      console.error('Unable to initialize chat map', problem);
      if (!cancelled) { setReady(true); setMapError('Chưa tải được bản đồ. Hãy thử lại hoặc dùng nút Vị trí của tôi.'); }
    });
    return () => { cancelled = true; instance?.remove(); if (map.current === instance) map.current = null; };
  }, [mapAttempt]);
  useEffect(() => { map.current?.setPoint(point); }, [point]);

  useEffect(() => {
    if (!point) return;
    let cancelled = false;
    setAddressLoading(true); setAddressError('');
    const timer = setTimeout(async () => {
      try {
        const address = await getLocationAddress(chatId, point);
        if (!cancelled) {
          setLabel(address?.label || null); retry.current = null;
          if (!address?.label) setAddressError('Khu vực này chưa có tên địa chỉ. Bạn vẫn có thể gửi đúng tọa độ đã chọn.');
        }
      } catch (problem) {
        if (!cancelled) { setLabel(null); setAddressError(problem.message || 'Chưa lấy được địa chỉ.'); }
      } finally { if (!cancelled) setAddressLoading(false); }
    }, 650);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [point, chatId, addressAttempt]);

  function currentLocation() {
    if (busyRef.current || locating) return;
    if (!navigator.geolocation) { setError('Trình duyệt chưa hỗ trợ lấy vị trí tại đây. Hãy chọn điểm trên bản đồ.'); return; }
    const requestId = ++gpsRequest.current;
    setLocating(true); setError('');
    navigator.geolocation.getCurrentPosition(position => {
      if (disposed.current || gpsRequest.current !== requestId || busyRef.current) return;
      const coordinates = { latitude: position.coords.latitude, longitude: position.coords.longitude };
      choose.current(coordinates); setAccuracy(Math.round(position.coords.accuracy)); map.current?.focus(coordinates);
    }, problem => {
      if (disposed.current || gpsRequest.current !== requestId) return;
      setLocating(false);
      setError(problem.code === 1 ? 'Bạn chưa cho phép truy cập vị trí. Hãy cho phép hoặc chọn điểm trên bản đồ.'
        : 'Chưa lấy được vị trí hiện tại. Bạn có thể thử lại hoặc chọn điểm trên bản đồ.');
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
  }
  async function submit(event) {
    event.preventDefault();
    if (!point || busyRef.current || addressLoading || locating) return;
    retry.current ||= { ...point, label: label || null, clientMessageId: crypto.randomUUID() };
    busyRef.current = true; setBusy(true); setError('');
    try { const message = await sendLocation(chatId, retry.current); if (!disposed.current) onSent(message); }
    catch (problem) { if (!disposed.current) setError(problem.message); }
    finally { busyRef.current = false; if (!disposed.current) setBusy(false); }
  }
  function close() { if (!busyRef.current) onClose(); }
  return <dialog ref={dialog} aria-labelledby={titleId} className={`chat-location-dialog ${busy ? 'is-busy' : ''}`}
    onCancel={event => { event.preventDefault(); close(); }} onClick={event => { if (event.target === event.currentTarget) close(); }}>
    <header><h2 id={titleId}><MapPin size={23} /> Chọn vị trí trên bản đồ</h2><button type="button" disabled={busy} aria-label="Đóng bản đồ" onClick={close}><X size={23} /></button></header>
    <div className="chat-location-content">
      <div className="chat-map-wrap"><div ref={canvas} className="chat-location-map" aria-label="Bản đồ chọn vị trí" />
        <button type="button" className="chat-map-gps" disabled={busy || locating} onClick={currentLocation}><LocateFixed size={17} />{locating ? 'Đang lấy vị trí…' : 'Vị trí của tôi'}</button>
        <button type="button" className="chat-map-center" disabled={busy || !ready || !map.current} onClick={() => select(map.current?.getCenter())}><Crosshair size={16} />Chọn tâm bản đồ</button>
        {!ready && <p role="status" className="chat-map-loading">Đang tải bản đồ…</p>}
      </div>
      {mapError && <div className="chat-location-warning" role="status">{mapError} <button type="button" disabled={busy} onClick={() => setMapAttempt(value => value + 1)}>Thử tải lại</button></div>}
      <section className="chat-selected-location" aria-live="polite"><MapPin size={24} /><div>
        <span>Địa chỉ đang chọn:</span><strong>{!point ? 'Chưa chọn vị trí' : addressLoading ? 'Đang xác định địa chỉ…' : label || 'Vị trí đã chọn'}</strong>
        {point && <small>Tọa độ GPS: {point.latitude.toFixed(6)}, {point.longitude.toFixed(6)}</small>}
        {accuracy !== null && <small>Độ chính xác vị trí hiện tại khoảng {accuracy} m.</small>}
      </div></section>
      {addressError && <div className="chat-location-warning" role="status">{addressError} <button type="button" disabled={busy || addressLoading} onClick={() => setAddressAttempt(value => value + 1)}>Lấy lại địa chỉ</button></div>}
      <p className="chat-location-hint"><Info size={15} /> Chạm hoặc nhấp vào bản đồ, kéo ghim, hoặc lấy vị trí hiện tại để chọn điểm muốn gửi.</p>
      {error && <p className="chat-location-warning" role="alert">{error}</p>}
    </div>
    <form onSubmit={submit}><footer><button type="button" className="chat-location-cancel" disabled={busy} onClick={close}>Hủy bỏ</button>
      <button type="submit" className="chat-location-confirm" disabled={!point || busy || locating || addressLoading}><CheckCircle size={17} />{busy ? 'Đang gửi…' : 'Xác nhận vị trí này'}</button>
    </footer></form>
  </dialog>;
}
