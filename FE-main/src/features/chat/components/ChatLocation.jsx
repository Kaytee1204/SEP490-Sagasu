import { MapPin, Map } from 'lucide-react';
import { googleMapsUrl, googleMapsDirectionsUrl } from '../locationUtils';

export default function ChatLocation({ location }) {
  const url = googleMapsUrl(location);
  if (!url) return null;
  return <div className="chat-location-card">
    <div className="chat-location-title"><MapPin size={19} aria-hidden="true" /><span>Vị trí: {location.label || `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`}</span></div>
    <a className="chat-location-directions" href={googleMapsDirectionsUrl(location)} target="_blank" rel="noopener noreferrer">
      <Map size={27} aria-hidden="true" /><span><strong>Mở chỉ đường Google Maps</strong><small>Nhấn để xem vị trí chính xác</small></span>
    </a>
  </div>;
}
