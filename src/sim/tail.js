// The tail ("slime trail") behind each ship. Port of Tail.java.
//
// A tail is a plain object { t, x, y, knots }: parallel arrays of
// trajectory points sorted by time t (in ticks), plus a sorted list of
// "knot" times. Knots mark where you scored; getting shot rewinds you to
// your last knot, and crossing the other player's tail cuts your tail back
// to your second knot. There is always a knot at the base (oldest point).
//
// Tails are never empty and are treated as immutable: every operation
// returns a new tail.

import { MINTRAJECTORYSEGMENT, MAXSPEED } from "./constants.js";
import { segmentsIntersect } from "./geom.js";

export function newTail(time, x, y) {
  return { t: [time], x: [x], y: [y], knots: [time] };
}

export function cloneTail(tail) {
  return { t: tail.t.slice(), x: tail.x.slice(), y: tail.y.slice(), knots: tail.knots.slice() };
}

export const nPoints = (tail) => tail.t.length;
export const headTime = (tail) => tail.t[tail.t.length - 1];
export const baseTime = (tail) => tail.t[0];
export const lastKnotTime = (tail) => tail.knots[tail.knots.length - 1];

// Index of the point at exactly `time`, or -1.
export function indexOf(tail, time) {
  let lo = 0, hi = tail.t.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const v = tail.t[mid];
    if (v === time) return mid;
    if (v < time) lo = mid + 1;
    else hi = mid - 1;
  }
  return -1;
}

export function getPoint(tail, time) {
  const i = indexOf(tail, time);
  return i < 0 ? null : { x: tail.x[i], y: tail.y[i] };
}

function dist(tail, i, j) {
  return Math.hypot(tail.x[i] - tail.x[j], tail.y[i] - tail.y[j]);
}

export function length(tail) {
  let len = 0;
  for (let i = 0; i + 1 < tail.t.length; i++) len += dist(tail, i, i + 1);
  return len;
}

// The first time (walking from startTime forward or backward) at which the
// path from startTime has at least minLength. null if the tail is too short.
export function convertLengthToTime(tail, startTime, minLength, forward) {
  const start = indexOf(tail, startTime);
  if (start < 0) return null;
  if (minLength <= 0) return startTime;
  if (tail.t.length === 1) return startTime;
  let len = 0;
  const step = forward ? 1 : -1;
  for (let i = start, j = start + step; j >= 0 && j < tail.t.length; i = j, j += step) {
    len += dist(tail, i, j);
    if (len >= minLength) return tail.t[j];
  }
  return null;
}

// Length along the tail from the head back to the last point at or before desiredTime.
export function lengthFromHeadTo(tail, desiredTime) {
  const n = tail.t.length;
  if (desiredTime >= tail.t[n - 1]) return 0;
  if (desiredTime <= tail.t[0]) return length(tail);
  let len = 0;
  for (let i = n - 1; i > 0 && tail.t[i] > desiredTime; i--) len += dist(tail, i, i - 1);
  return len;
}

// Direction of travel at `time` (angle in radians), or null for a 1-point tail.
export function tangentAngle(tail, time) {
  const n = tail.t.length;
  if (n === 1) return null;
  const i = indexOf(tail, time);
  if (i < 0) return null;
  const a = i === n - 1 ? i - 1 : i;
  return Math.atan2(tail.y[a + 1] - tail.y[a], tail.x[a + 1] - tail.x[a]);
}

// Append the ship's new position. If the new point is very close to the
// point before the head, the head is replaced instead (keeps the point
// count down when moving slowly). A knot on the replaced head moves along.
export function addPoint(tail, time, x, y) {
  const out = cloneTail(tail);
  const n = out.t.length;
  const hx = out.x[n - 1], hy = out.y[n - 1];
  const tooClose =
    (n >= 2 && Math.hypot(x - out.x[n - 2], y - out.y[n - 2]) < MINTRAJECTORYSEGMENT) || (x === hx && y === hy);
  if (tooClose) {
    const ht = out.t[n - 1];
    const k = out.knots.indexOf(ht);
    out.t.pop();
    out.x.pop();
    out.y.pop();
    if (k >= 0) out.knots.splice(k, 1);
    if (k >= 0 || out.t.length === 0) insertKnot(out, time);
  }
  out.t.push(time);
  out.x.push(x);
  out.y.push(y);
  ensureBaseKnot(out);
  return out;
}

function insertKnot(tail, time) {
  if (tail.knots.includes(time)) return;
  tail.knots.push(time);
  tail.knots.sort((a, b) => a - b);
}

export function addKnot(tail, time) {
  const out = { ...tail, knots: tail.knots.slice() };
  insertKnot(out, time);
  return out;
}

function ensureBaseKnot(tail) {
  // Drop knots no longer in the trajectory, and keep one at the base.
  const base = tail.t[0];
  tail.knots = tail.knots.filter((k) => k >= base && indexOf(tail, k) >= 0);
  if (tail.knots[0] !== base) tail.knots.unshift(base);
}

// Remove every point whose time is in any [from, to) range. Never returns
// an empty tail: if everything is cropped, the head point is kept.
export function crop(tail, ranges) {
  if (ranges.length === 0) return tail;
  const inRange = (time) => ranges.some(([from, to]) => time >= from && time < to);
  const out = { t: [], x: [], y: [], knots: [] };
  for (let i = 0; i < tail.t.length; i++) {
    if (!inRange(tail.t[i])) {
      out.t.push(tail.t[i]);
      out.x.push(tail.x[i]);
      out.y.push(tail.y[i]);
    }
  }
  if (out.t.length === 0) {
    const n = tail.t.length;
    out.t.push(tail.t[n - 1]);
    out.x.push(tail.x[n - 1]);
    out.y.push(tail.y[n - 1]);
  }
  out.knots = tail.knots.slice();
  ensureBaseKnot(out);
  return out;
}

// After crossing someone else's tail you lose everything up to (not
// including) your second knot, or everything but the head if you have
// only the base knot.
export function cropToForCrossing(tail) {
  if (tail.knots.length === 1) return headTime(tail);
  return tail.knots[1];
}

// Does the last segment of `mover` (its newest move) cross `other`?
// When other === mover (checking for a self-loop) the newest two segments of
// the tail are skipped so a ship doesn't "cross" the segment it just drew.
// Returns the time of the first (oldest) point of the crossed segment, or
// null. For a self-loop, the points from that time to the head form the loop.
export function findCrossing(other, mover, same) {
  const exclude = same ? 2 : 0;
  const n = other.t.length;
  if (n < 2 + exclude) return null;
  const m = mover.t.length;
  if (m < 2) return null;
  const ax = mover.x[m - 2], ay = mover.y[m - 2], bx = mover.x[m - 1], by = mover.y[m - 1];
  const minX = Math.min(ax, bx), maxX = Math.max(ax, bx), minY = Math.min(ay, by), maxY = Math.max(ay, by);
  // Like the Java version, the other tail's own newest segment (into its
  // head) is not considered, plus `exclude` more points.
  const lastIdx = n - 1 - exclude;
  for (let i = 0; i + 1 < lastIdx; i++) {
    const x1 = other.x[i], y1 = other.y[i], x2 = other.x[i + 1], y2 = other.y[i + 1];
    if (Math.max(x1, x2) < minX || Math.min(x1, x2) > maxX || Math.max(y1, y2) < minY || Math.min(y1, y2) > maxY)
      continue;
    if (segmentsIntersect(x1, y1, x2, y2, ax, ay, bx, by)) return other.t[i];
  }
  return null;
}

// The loop polygon (points from `fromTime` to the head) as coordinate arrays.
export function loopPolygon(tail, fromTime) {
  const i = indexOf(tail, fromTime);
  return { xs: tail.x.slice(i), ys: tail.y.slice(i) };
}

// Upper bound on a segment length; exported for tests.
export const MAX_SEGMENT = Math.max(MAXSPEED, MINTRAJECTORYSEGMENT);
