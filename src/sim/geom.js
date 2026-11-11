// Geometry helpers matching java.awt.geom semantics closely enough.

function orient(ax, ay, bx, by, cx, cy) {
  const v = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
  return v > 0 ? 1 : v < 0 ? -1 : 0;
}

function onSeg(ax, ay, bx, by, cx, cy) {
  // c is collinear with a-b; is it within the bounding box?
  return Math.min(ax, bx) <= cx && cx <= Math.max(ax, bx) && Math.min(ay, by) <= cy && cy <= Math.max(ay, by);
}

// Like Line2D.linesIntersect: true if the closed segments touch or cross.
export function segmentsIntersect(x1, y1, x2, y2, x3, y3, x4, y4) {
  const o1 = orient(x1, y1, x2, y2, x3, y3);
  const o2 = orient(x1, y1, x2, y2, x4, y4);
  const o3 = orient(x3, y3, x4, y4, x1, y1);
  const o4 = orient(x3, y3, x4, y4, x2, y2);
  if (o1 !== o2 && o3 !== o4) return true;
  if (o1 === 0 && onSeg(x1, y1, x2, y2, x3, y3)) return true;
  if (o2 === 0 && onSeg(x1, y1, x2, y2, x4, y4)) return true;
  if (o3 === 0 && onSeg(x3, y3, x4, y4, x1, y1)) return true;
  if (o4 === 0 && onSeg(x3, y3, x4, y4, x2, y2)) return true;
  return false;
}

// Point in polygon. xs/ys are vertex arrays; the polygon is implicitly closed.
// nonZero=true matches Path2D's default WIND_NON_ZERO (used for loops);
// nonZero=false matches java.awt.Polygon's even-odd rule (used for clusters).
export function polygonContains(xs, ys, px, py, nonZero) {
  const n = xs.length;
  if (n < 3) return false;
  let winding = 0;
  let inside = false;
  for (let i = 0, j = n - 1; i < n; j = i++) {
    const xi = xs[i], yi = ys[i], xj = xs[j], yj = ys[j];
    if ((yi > py) !== (yj > py)) {
      const xCross = xj + ((py - yj) * (xi - xj)) / (yi - yj);
      if (px < xCross) {
        inside = !inside;
        winding += yi > yj ? 1 : -1;
      }
    }
  }
  return nonZero ? winding !== 0 : inside;
}

export function bounds(xs, ys) {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (let i = 0; i < xs.length; i++) {
    if (xs[i] < minX) minX = xs[i];
    if (xs[i] > maxX) maxX = xs[i];
    if (ys[i] < minY) minY = ys[i];
    if (ys[i] > maxY) maxY = ys[i];
  }
  return { minX, minY, maxX, maxY };
}

export function boxesOverlap(a, b, margin = 0) {
  return a.minX - margin <= b.maxX && b.minX - margin <= a.maxX && a.minY - margin <= b.maxY && b.minY - margin <= a.maxY;
}

// Does the closed polygon's outline cross the segment?
export function polygonEdgeHitsSegment(xs, ys, x1, y1, x2, y2) {
  const n = xs.length;
  for (let i = 0, j = n - 1; i < n; j = i++) {
    if (segmentsIntersect(xs[j], ys[j], xs[i], ys[i], x1, y1, x2, y2)) return true;
  }
  return false;
}

// Wrap an angle into [-PI, PI).
export function wrapAngle(a) {
  const TWO_PI = 2 * Math.PI;
  a = (a + Math.PI) % TWO_PI;
  if (a < 0) a += TWO_PI;
  return a - Math.PI;
}
