// Capture the pointer on the map itself instead of relying on document mouse events.
export function bindMapGestures(map, marker, onPick) {
  const canvas = map.getCanvas(), pin = marker.getElement();
  const events = new AbortController(), pointers = new Set();
  let gesture = null;

  function position(event) {
    const bounds = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - bounds.left) * canvas.clientWidth / bounds.width,
      y: (event.clientY - bounds.top) * canvas.clientHeight / bounds.height,
    };
  }
  function release(cancelled) {
    const current = gesture;
    if (!current) return;
    gesture = null;
    canvas.classList.remove('chat-map-dragging'); pin.classList.remove('chat-map-dragging');
    if (current.element.hasPointerCapture(current.id)) current.element.releasePointerCapture(current.id);
    if (cancelled) { marker.setLngLat(current.originalLocation); return; }
    const coordinates = current.isPin ? marker.getLngLat() : current.moved ? map.getCenter() : map.unproject([current.start.x, current.start.y]);
    marker.setLngLat(coordinates);
    onPick(coordinates.wrap());
  }
  function down(event) {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    pointers.add(event.pointerId);
    // Leave two-finger zoom to MapLibre; it must not submit a single-finger selection.
    if (pointers.size !== 1) { release(true); return; }
    map.stop();
    const start = position(event);
    gesture = {
      id: event.pointerId, element: event.currentTarget, isPin: event.currentTarget === pin,
      start, previous: start, originalLocation: marker.getLngLat(),
      originalPixel: map.project(marker.getLngLat()), moved: false,
    };
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function move(event) {
    if (!gesture || gesture.id !== event.pointerId || pointers.size !== 1) return;
    const point = position(event);
    if (!gesture.moved && Math.hypot(point.x - gesture.start.x, point.y - gesture.start.y) < 4) return;
    gesture.moved = true;
    event.preventDefault();
    gesture.element.classList.add('chat-map-dragging');
    if (gesture.isPin) {
      marker.setLngLat(map.unproject([
        gesture.originalPixel.x + point.x - gesture.start.x,
        gesture.originalPixel.y + point.y - gesture.start.y,
      ]));
    } else {
      map.panBy([gesture.previous.x - point.x, gesture.previous.y - point.y], { duration: 0 });
      marker.setLngLat(map.getCenter());
    }
    gesture.previous = point;
  }
  function up(event) {
    if (gesture?.id === event.pointerId) { move(event); release(false); }
    pointers.delete(event.pointerId);
  }
  function cancel(event) {
    if (gesture?.id === event.pointerId) release(true);
    pointers.delete(event.pointerId);
  }
  for (const element of [canvas, pin]) {
    element.draggable = false;
    element.addEventListener('pointerdown', down, { signal: events.signal });
    element.addEventListener('pointermove', move, { signal: events.signal, passive: false });
    element.addEventListener('pointerup', up, { signal: events.signal });
    element.addEventListener('pointercancel', cancel, { signal: events.signal });
    element.addEventListener('lostpointercapture', cancel, { signal: events.signal });
    element.addEventListener('dragstart', event => event.preventDefault(), { signal: events.signal });
  }
  pin.tabIndex = 0; pin.setAttribute('role', 'button');
  pin.addEventListener('keydown', event => {
    const offset = { ArrowLeft: [-10, 0], ArrowRight: [10, 0], ArrowUp: [0, -10], ArrowDown: [0, 10] }[event.key];
    if (!offset && event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault(); event.stopPropagation();
    if (offset) {
      const point = map.project(marker.getLngLat());
      marker.setLngLat(map.unproject([point.x + offset[0], point.y + offset[1]]));
    }
    onPick(marker.getLngLat().wrap());
  }, { signal: events.signal });
  return () => { release(true); pointers.clear(); events.abort(); };
}
