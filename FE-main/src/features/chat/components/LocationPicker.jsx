import { useEffect, useId, useRef, useState } from 'react';
import { LocateFixed, MapPin, X } from 'lucide-react';
import { sendLocation } from '../api/chatApi';
import { googleMapsUrl, locationPoint } from '../locationUtils';
import 'leaflet/dist/leaflet.css';

export default function LocationPicker({ chatId, onClose, onSent }) {
  const titleId = useId();
  const dialog = useRef(null), canvas = useRef(null), map = useRef(null), marker = useRef(null);
  const selected = useRef(null), choose = useRef(null), disposed = useRef(false), busyRef = useRef(false);
  const gpsRequest = useRef(0), retry = useRef(null);
  const [point, setPoint] = useState(null);
  const [latitude, setLatitude] = useState(''), [longitude, setLongitude] = useState('');
  const [label, setLabel] = useState('');
  const [error, setError] = useState(''), [mapError, setMapError] = useState('');
  const [ready, setReady] = useState(false), [locating, setLocating] = useState(false), [busy, setBusy] = useState(false);
  const [accuracy, setAccuracy] = useState(null);
  selected.current = point;
  function select(value) {
    if (busyRef.current || disposed.current) return;
    const valid = value && locationPoint(value.latitude, value.longitude);
    if (!valid) return;
    gpsRequest.current++; setLocating(false); setAccuracy(null); retry.current = null;
    setPoint(valid); setLatitude(valid.latitude.toFixed(6)); setLongitude(valid.longitude.toFixed(6)); setError('');
  }
  choose.current = select;
  useEffect(() => {
    disposed.current = false;
    const element = dialog.current, previousFocus = document.activeElement, previousOverflow = document.body.style.overflow;
    element.showModal(); document.body.style.overflow = 'hidden';
    let resize, leaflet, cancelled = false;
    import('leaflet').then(module => {
      if (cancelled) return;
      leaflet = module.default;
      const instance = leaflet.map(canvas.current).setView([21.03, 105.85], 12);
      map.current = instance;
      leaflet.tileLayer(import.meta.env.VITE_CHAT_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        referrerPolicy: 'strict-origin-when-cross-origin',
        attribution: import.meta.env.VITE_CHAT_MAP_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      }).on('tileerror', () => { if (!disposed.current) setMapError('Không tải được một phần bản đồ. Bạn vẫn có thể dùng vị trí hiện tại hoặc nhập tọa độ.'); }).addTo(instance);
      const icon = leaflet.divIcon({ className: 'chat-map-pin', html: '<span aria-hidden="true">📍</span>', iconSize: [32, 40], iconAnchor: [16, 37] });
      function updateMarker(value) {
        if (!value) { if (marker.current) { marker.current.remove(); marker.current = null; } return; }
        const coordinates = [value.latitude, value.longitude];
        if (!marker.current) {
          marker.current = leaflet.marker(coordinates, { draggable: true, icon, title: 'Kéo ghim để chọn vị trí', keyboard: true }).addTo(instance);
          marker.current.on('dragend', () => {
            const position = marker.current.getLatLng().wrap();
            choose.current({ latitude: position.lat, longitude: position.lng });
          });
        } else marker.current.setLatLng(coordinates);
        instance.panTo(coordinates);
      }
      instance.chatUpdateMarker = updateMarker;
      instance.on('click', event => {
        const position = event.latlng.wrap(); choose.current({ latitude: position.lat, longitude: position.lng });
      });
      updateMarker(selected.current);
      if (selected.current) instance.setView([selected.current.latitude, selected.current.longitude], 16);
      resize = new ResizeObserver(() => instance.invalidateSize()); resize.observe(canvas.current);
      instance.invalidateSize(); setReady(true);
    }).catch(() => { if (!cancelled) { setReady(true); setMapError('Chưa tải được bản đồ. Bạn có thể lấy vị trí hiện tại hoặc nhập tọa độ.'); } });
    return () => {
      cancelled = true; disposed.current = true; gpsRequest.current++; resize?.disconnect(); map.current?.remove(); map.current = null; marker.current = null;
      element.close(); document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);
  useEffect(() => { map.current?.chatUpdateMarker?.(point); }, [point]);

  function currentLocation() {
    if (busyRef.current || locating) return;
    if (!navigator.geolocation) { setError('Trình duyệt chưa hỗ trợ lấy vị trí tại đây. Hãy chọn điểm trên bản đồ hoặc nhập tọa độ.'); return; }
    const requestId = ++gpsRequest.current;
    setLocating(true); setError('');
    navigator.geolocation.getCurrentPosition(position => {
      if (disposed.current || gpsRequest.current !== requestId || busyRef.current) return;
      choose.current({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      setAccuracy(Math.round(position.coords.accuracy));
      map.current?.setView([position.coords.latitude, position.coords.longitude], 16);
    }, problem => {
      if (disposed.current || gpsRequest.current !== requestId) return;
      setLocating(false);
      setError(problem.code === 1 ? 'Bạn chưa cho phép truy cập vị trí. Hãy chọn điểm trên bản đồ hoặc nhập tọa độ.'
        : 'Chưa lấy được vị trí hiện tại. Bạn có thể thử lại hoặc chọn điểm trên bản đồ.');
    }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
  }
  function editCoordinate(setter, value) {
    setter(value); setPoint(null); setAccuracy(null); setLocating(false); gpsRequest.current++; retry.current = null;
  }
  async function submit(event) {
    event.preventDefault();
    if (!point || busyRef.current) return;
    retry.current ||= { ...point, label: label.trim() || null, clientMessageId: crypto.randomUUID() };
    busyRef.current = true; setBusy(true); setError('');
    try {
      const message = await sendLocation(chatId, retry.current);
      if (!disposed.current) onSent(message);
    } catch (problem) { if (!disposed.current) setError(problem.message); }
    finally { busyRef.current = false; if (!disposed.current) setBusy(false); }
  }
  function close() { if (!busyRef.current) onClose(); }
  return <dialog ref={dialog} aria-labelledby={titleId} className={`chat-location-dialog ${busy ? 'is-busy' : ''}`}
    onCancel={event => { event.preventDefault(); close(); }} onClick={event => { if (event.target === event.currentTarget) close(); }}>
    <header><h2 id={titleId}><MapPin size={22} /> Chia sẻ vị trí</h2><button type="button" disabled={busy} aria-label="Đóng bản đồ" onClick={close}><X size={23} /></button></header>
    <p className="chat-location-hint">Bấm lên bản đồ hoặc kéo ghim đến điểm muốn gửi. Vị trí chỉ được gửi khi bạn bấm “Gửi vị trí”.</p>
    <div ref={canvas} className="chat-location-map" aria-label="Bản đồ chọn vị trí" />
    {!ready && <p role="status" className="chat-location-hint">Đang tải bản đồ…</p>}
    {mapError && <p role="status" className="chat-location-hint">{mapError}</p>}
    <form onSubmit={submit}><fieldset disabled={busy}>
      <button type="button" className="btn btn-ghost" disabled={locating} onClick={currentLocation}><LocateFixed size={17} />{locating ? 'Đang lấy vị trí…' : 'Vị trí hiện tại'}</button>
      {accuracy !== null && <small className="chat-muted">Độ chính xác khoảng {accuracy} m. Bạn có thể kéo ghim để chỉnh lại.</small>}
      <div className="chat-coordinate-inputs">
        <label>Vĩ độ<input type="number" step="any" min="-90" max="90" value={latitude} onChange={event => editCoordinate(setLatitude, event.target.value)} /></label>
        <label>Kinh độ<input type="number" step="any" min="-180" max="180" value={longitude} onChange={event => editCoordinate(setLongitude, event.target.value)} /></label>
        <button type="button" className="btn btn-ghost" disabled={!locationPoint(latitude, longitude)} onClick={() => select(locationPoint(latitude, longitude))}>Đặt ghim</button>
      </div>
      <label>Tên địa điểm (không bắt buộc)<input type="text" maxLength={200} placeholder="Ví dụ: Cổng trường, sảnh tòa nhà…" value={label} onChange={event => { setLabel(event.target.value); retry.current = null; }} /></label>
      {point && <a href={googleMapsUrl(point)} target="_blank" rel="noopener noreferrer">Mở điểm đã chọn trên Google Maps ↗</a>}
      {error && <p className="chat-error" role="alert">{error}</p>}
      <footer><button type="button" className="btn btn-ghost" onClick={close}>Hủy</button><button type="submit" className="btn btn-accent" disabled={!point || locating}>{busy ? 'Đang gửi…' : 'Gửi vị trí'}</button></footer>
    </fieldset></form>
  </dialog>;
}
