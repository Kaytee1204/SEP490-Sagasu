import { MapPin, ExternalLink } from 'lucide-react';
import { googleMapsUrl } from '../locationUtils';

export default function ChatLocation({ location }) {
  const url = googleMapsUrl(location);
  if (!url) return null;
  return <a className="chat-location-card" href={url} target="_blank" rel="noopener noreferrer">
    <MapPin size={26} aria-hidden="true" /><strong>{location.label || 'Vị trí được chia sẻ'}</strong>
    <span>{location.latitude.toFixed(6)}, {location.longitude.toFixed(6)}</span>
    <span className="chat-location-link">Mở Google Maps <ExternalLink size={14} aria-hidden="true" /></span>
  </a>;
}
