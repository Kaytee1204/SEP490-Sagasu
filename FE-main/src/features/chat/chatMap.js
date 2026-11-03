import { Map, Marker, NavigationControl, setWorkerUrl } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';

setWorkerUrl(workerUrl);

export function createChatMap(container, { onPick, onReady, onError }) {
  const map = new Map({
    container, style: import.meta.env.VITE_CHAT_MAP_STYLE_URL || 'https://tiles.openfreemap.org/styles/liberty',
    center: [105.85, 21.03], zoom: 12, maxZoom: 19,
    dragRotate: false, pitchWithRotate: false,
    attributionControl: { compact: false },
  });
  map.addControl(new NavigationControl({ showCompass: false }), 'top-left');
  let marker;
  const pick = coordinates => onPick({ latitude: coordinates.lat, longitude: coordinates.lng });
  map.on('click', event => pick(event.lngLat.wrap()));
  map.on('load', () => { onReady(); onError(''); });
  map.on('idle', () => { if (map.areTilesLoaded()) onError(''); });
  map.on('error', event => {
    console.error('Unable to load chat map resources', event.error);
    onReady(); onError('Chưa tải được bản đồ. Bạn có thể thử lại hoặc lấy vị trí hiện tại.');
  });
  let removed = false;
  const resize = new ResizeObserver(() => { if (!removed) map.resize(); }); resize.observe(container);
  return {
    setPoint(point) {
      if (!point) { marker?.remove(); marker = null; return; }
      if (!marker) {
        marker = new Marker({ color: '#ed3f5b', draggable: true }).setLngLat([point.longitude, point.latitude]).addTo(map);
        marker.getElement().setAttribute('aria-label', 'Ghim vị trí đã chọn');
        marker.on('dragend', () => pick(marker.getLngLat().wrap()));
      } else marker.setLngLat([point.longitude, point.latitude]);
      map.easeTo({ center: [point.longitude, point.latitude], duration: 180 });
    },
    focus(point) { map.jumpTo({ center: [point.longitude, point.latitude], zoom: 16 }); },
    getCenter() { const center = map.getCenter().wrap(); return { latitude: center.lat, longitude: center.lng }; },
    remove() { removed = true; resize.disconnect(); map.remove(); },
  };
}
