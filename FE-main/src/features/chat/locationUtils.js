export function locationPoint(latitude, longitude) {
  if (String(latitude ?? '').trim() === '' || String(longitude ?? '').trim() === '') return null;
  const lat = Number(latitude), lng = Number(longitude);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return null;
  return { latitude: Math.round(lat * 1000000) / 1000000, longitude: Math.round(lng * 1000000) / 1000000 };
}

export function googleMapsUrl(point) {
  const valid = point && locationPoint(point.latitude, point.longitude);
  return valid ? `https://www.google.com/maps/search/?${new URLSearchParams({ api: '1', query: `${valid.latitude},${valid.longitude}` })}` : null;
}
