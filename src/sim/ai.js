// The computer opponent. Port of Nonhuman.java (2011).
//
// It steers by summing "forces": a pull toward the nearest cluster it needs
// next plus a swirl around it (so it ends up looping around it), a short
// swirl around and push away from nearby cluster outlines, and a push away
// from spots where it recently crashed or closed a loop. It re-thinks every
// third tick and, like the original, sometimes skips a turn at random.
//
// Like the original, it knows each cluster's real cell count.

import { ARENASIZE, ARENA_HALF, BULLETCOST, BULLETSPEED, MAXACC, NUMPLAYERS, TIMESTEP } from "./constants.js";
import { makeRng } from "./rng.js";
import { shipShape, isInArena } from "./sim.js";
import { segmentsIntersect } from "./geom.js";
import * as T from "./tail.js";

const REPULSION_LIFETIME = 4000 / TIMESTEP;
export const AI_TUNING = { wall: 60 };

export function makeAi(playerNo, seed) {
  return {
    playerNo,
    rand: makeRng(seed),
    target: null, // cluster currently being encircled
    targetFor: -1, // score the target was chosen for
    clockwise: true,
    repulsion: [], // { x, y, time }
    prevSinceCrashed: 0,
    prevSinceLooping: 0,
    last: { direct: { accel: 0, targetDir: 0 }, shoot: false },
  };
}

const dist = (ax, ay, bx, by) => Math.hypot(ax - bx, ay - by);

// Returns this tick's input for the AI's player. Mutates `ai` (its memory).
export function aiThink(ai, arena, state) {
  if (state.time % 3 !== 0) return ai.last;
  const me = state.players[ai.playerNo];
  const ship = me.ship;
  const nose = shipShape(ship)[1];
  let fax = 0, fay = 0, frx = 0, fry = 0, fox = 0, foy = 0;

  // Remember where we just crashed or looped and avoid it for a while.
  if (me.sinceCrashed === 0 || me.sinceLooping === 0 || me.sinceCrashed < ai.prevSinceCrashed || me.sinceLooping < ai.prevSinceLooping)
    ai.repulsion.push({ x: nose.x, y: nose.y, time: state.time });
  ai.prevSinceCrashed = me.sinceCrashed;
  ai.prevSinceLooping = me.sinceLooping;
  ai.repulsion = ai.repulsion.filter((p) => state.time - p.time < REPULSION_LIFETIME);
  for (const p of ai.repulsion) {
    if (dist(p.x, p.y, nose.x, nose.y) > ARENASIZE / 5) continue;
    const k = (100 * (REPULSION_LIFETIME - (state.time - p.time))) / REPULSION_LIFETIME * Math.exp(-dist(p.x, p.y, ship.x, ship.y) / (ARENASIZE / 50));
    const th = Math.atan2(ship.y - p.y, ship.x - p.x);
    frx += k * Math.cos(th);
    fry += k * Math.sin(th);
  }

  if (me.score >= arena.sequence.length || arena.clusters.length === 0) return ai.last;

  // (1) Pick the nearest cluster with the cell count we need next.
  const want = arena.sequence[me.score];
  if (!ai.target || ai.targetFor !== me.score) {
    let best = null, bestD = Infinity;
    for (const c of arena.clusters) {
      if (c.nCells !== want) continue;
      const d = dist(c.center.x, c.center.y, ship.x, ship.y);
      if (d < bestD) {
        bestD = d;
        best = c;
      }
    }
    if (!best) return ai.last;
    ai.target = best;
    ai.targetFor = me.score;
    ai.clockwise = ai.rand() < 0.5;
  }
  const tc = ai.target.center;
  const distToTarget = Math.max(1, dist(tc.x, tc.y, ship.x, ship.y));
  let th = Math.atan2(ship.y - tc.y, ship.x - tc.x);
  const spin = ai.clockwise ? -1 : 1;

  // (2) Swirl around and pull toward the target.
  let k = 200000 / distToTarget;
  fox += k * -Math.sin(th) * spin;
  foy += k * Math.cos(th) * spin;
  fax += -4 * Math.cos(th);
  fay += -4 * Math.sin(th);

  // Swirl around the outline of the closest cluster, in the direction we're already turning.
  let closest = null, cd = Infinity;
  for (const c of arena.clusters) {
    const d = dist(c.center.x, c.center.y, nose.x, nose.y);
    if (d < cd) {
      cd = d;
      closest = c;
    }
  }
  const b = Math.atan2(closest.center.y - ship.y, closest.center.x - ship.x) - ship.dir;
  const bw = Math.atan2(Math.sin(b), Math.cos(b));
  const rotSpin = bw < 0 ? -1 : 1;
  const scale = ARENASIZE / 30;
  for (let i = 0; i < closest.xs.length; i++) {
    k = (Math.exp(-dist(closest.xs[i], closest.ys[i], nose.x, nose.y) / scale) / closest.xs.length) * 100;
    th = Math.atan2(ship.y - closest.ys[i], ship.x - closest.xs[i]);
    fox += k * -Math.sin(th) * rotSpin;
    foy += k * Math.cos(th) * rotSpin;
  }

  // (3) Push away from nearby cluster outlines.
  for (const c of arena.clusters) {
    if (dist(c.center.x, c.center.y, nose.x, nose.y) > scale * 10) continue;
    for (let i = 0; i < c.xs.length; i++) {
      k = (Math.exp(-dist(c.xs[i], c.ys[i], nose.x, nose.y) / scale) / c.xs.length) * 130;
      th = Math.atan2(ship.y - c.ys[i], ship.x - c.xs[i]);
      frx += k * Math.cos(th);
      fry += k * Math.sin(th);
    }
  }

  // New in the remake: push away from the walls too (in 2011 they were far
  // off-screen). Kept out of the throttle rule below so it only steers.
  let fwx = 0, fwy = 0;
  for (const [d, ux, uy] of [
    [ARENA_HALF - nose.x, -1, 0],
    [ARENA_HALF + nose.x, 1, 0],
    [ARENA_HALF - nose.y, 0, -1],
    [ARENA_HALF + nose.y, 0, 1],
  ]) {
    k = AI_TUNING.wall * Math.exp(-Math.max(0, d) / scale);
    fwx += k * ux;
    fwy += k * uy;
  }

  // (4) Forces -> heading and thrust. Ease off when pushed toward a cluster ahead.
  const fx = fax + frx + fox + fwx, fy = fay + fry + foy + fwy;
  if (fx === 0 && fy === 0) return ai.last;
  let accel = MAXACC;
  if (Math.hypot(frx, fry) > 3 && Math.cos(bw) > 0) accel = Math.trunc(MAXACC * (1 - Math.cos(bw)));
  if (ai.rand() < 0.15) return ai.last; // hesitate now and then, as in 2011

  // (5) Shoot if a bullet fired now would likely hit someone.
  ai.last = { direct: { accel, targetDir: Math.atan2(fy, fx) }, shoot: wouldHit(arena, state, ai.playerNo) };
  return ai.last;
}

// Shooter.java: fly a bullet forward a third of the arena and see whether it
// meets any opponent, assuming they keep their current velocity.
function wouldHit(arena, state, playerNo) {
  const me = state.players[playerNo];
  if (me.untilReload !== 0 || me.untilRespawn !== 0 || T.length(me.tail) <= BULLETCOST) return false;
  const range = ARENASIZE / 3;
  const steps = Math.trunc(range / BULLETSPEED);
  const dx = Math.trunc(BULLETSPEED * Math.cos(me.ship.dir)), dy = Math.trunc(BULLETSPEED * Math.sin(me.ship.dir));
  for (let j = 0; j < NUMPLAYERS; j++) {
    if (j === playerNo) continue;
    const other = state.players[j];
    if (other.untilRespawn > 0) continue;
    let bx = me.ship.x, by = me.ship.y;
    for (let s = 1; s <= steps; s++) {
      const nx = bx + dx, ny = by + dy;
      if (!isInArena(arena, nx, ny)) break;
      const ghost = { ...other.ship, x: other.ship.x + other.ship.vx * s, y: other.ship.y + other.ship.vy * s };
      const pts = shipShape(ghost);
      for (let e = 0; e < 3; e++) {
        const p = pts[e], q = pts[(e + 1) % 3];
        if (segmentsIntersect(p.x, p.y, q.x, q.y, bx, by, nx, ny)) return true;
      }
      bx = nx;
      by = ny;
    }
  }
  return false;
}
