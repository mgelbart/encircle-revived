// The core simulation: step(arena, state, inputs) -> { state, events }.
// Port of the server loop in Simulation.java (2011).
//
// This module is pure and deterministic: the same arena, state and inputs
// always give the same result. The renderer, the local keyboard game and
// (later) online play all drive it the same way.
//
// inputs[playerNo] = { thrust, left, right, shoot } (booleans)

import {
  ARENA_HALF,
  BULLETCOST,
  BULLETSPEED,
  BYTE_MAX,
  DRAG,
  MAXACC,
  MAXSPEED,
  NUMPLAYERS,
  OMEGA,
  RAMP_MS,
  RELOADTIME,
  SHIPLENGTH,
  SHIPWIDTH,
  SHORT_MAX,
  TIMESTEP,
  UNCOILSPEED,
} from "./constants.js";
import { polygonContains, polygonEdgeHitsSegment, segmentsIntersect, wrapAngle } from "./geom.js";
import { spawnPoint } from "./arena.js";
import * as T from "./tail.js";

const clampByte = (v) => Math.max(0, Math.min(BYTE_MAX, v));
const clampShort = (v) => Math.max(0, Math.min(SHORT_MAX, v));

export function initialState(arena) {
  const players = [];
  for (let p = 0; p < NUMPLAYERS; p++) {
    const { x, y } = spawnPoint(p);
    players.push({
      ship: { x, y, vx: 0, vy: 0, dir: Math.atan2(-y, -x) }, // face the center
      tail: T.newTail(0, x, y),
      score: 0,
      untilReload: 0,
      untilRespawn: 0,
      sinceScoring: BYTE_MAX,
      sinceLooping: BYTE_MAX,
      sinceCrossing: BYTE_MAX,
      sinceHitByBullet: SHORT_MAX,
      sincePwned: BYTE_MAX,
      sinceCrashed: BYTE_MAX,
      // Rewinding to the last knot after being shot.
      respawn: { toTime: 0, length: 0 },
      // How long thrust / turning have been held, for the control ramps.
      thrustTicks: 0,
      turnTicks: 0,
    });
  }
  return { time: 0, players, bullets: [], winner: -1 };
}

// ---------------------------------------------------------------- ship --

// The ship's triangle: [tail-left, nose, tail-right] (Ship.shape()).
export function shipShape(ship) {
  const cos = Math.cos(ship.dir), sin = Math.sin(ship.dir);
  const pts = [];
  for (let j = 0; j < 3; j++) {
    const dx = ((j === 1 ? 1 : -1) * SHIPLENGTH) / 2;
    const dy = ((j - 1) * SHIPWIDTH) / 2;
    pts.push({ x: ship.x + Math.trunc(cos * dx - sin * dy), y: ship.y + Math.trunc(sin * dx + cos * dy) });
  }
  return pts;
}

function inCluster(arena, x, y) {
  for (const c of arena.clusters) {
    const b = c.bounds;
    if (x < b.minX || x > b.maxX || y < b.minY || y > b.maxY) continue;
    if (polygonContains(c.xs, c.ys, x, y, false)) return true;
  }
  return false;
}

export function isInArena(arena, x, y) {
  if (Math.abs(x) > ARENA_HALF || Math.abs(y) > ARENA_HALF) return false;
  return !inCluster(arena, x, y);
}

// How far ahead the cursor must be for full thrust (Tracker.DAMPINGDIST).
const DAMPINGDIST = MAXSPEED / DRAG;

// Turn an input into thrust and a target heading. Three kinds of input:
//   keys:   { thrust, left, right, shoot }          (KeyboardPilot.java)
//   mouse:  { target: {x, y} | null, stop, shoot }  (MousePilot.java + Tracker.paramsToTarget)
//   direct: { direct: {accel, targetDir}, shoot }   (the computer opponent)
function controls(player, input) {
  if (input.direct) return input.direct;
  if ("target" in input) return mouseControls(player.ship, input.target, input.stop);
  return keyControls(player, input);
}

// Steer toward the cursor. Thrust grows with the square of how far ahead of
// the ship (along its heading) the cursor is; no thrust if it's behind.
export function mouseControls(ship, target, stop) {
  if (!target) return { accel: 0, targetDir: ship.dir };
  const speed = Math.hypot(ship.vx, ship.vy);
  const l = DAMPINGDIST * Math.max(0.5, speed / MAXSPEED);
  const ahead = (target.x - ship.x) * Math.cos(ship.dir) + (target.y - ship.y) * Math.sin(ship.dir);
  let accel = ahead > 0 ? Math.trunc(MAXACC * Math.min(1, (ahead / l) ** 2)) : 0;
  if (stop) accel = 0;
  const dx = target.x - ship.x, dy = target.y - ship.y;
  const targetDir = dx === 0 && dy === 0 ? ship.dir : Math.atan2(dy, dx);
  return { accel, targetDir };
}

function keyControls(player, input) {
  const accel = input.thrust
    ? Math.trunc(Math.min(MAXACC, MAXACC * (0.7 + (0.3 * player.thrustTicks * TIMESTEP) / RAMP_MS)))
    : 0;
  const omega = Math.min(OMEGA, OMEGA * (0.4 + (0.6 * player.turnTicks * TIMESTEP) / RAMP_MS));
  const turn = (input.left ? 1 : 0) - (input.right ? 1 : 0);
  return { accel, targetDir: player.ship.dir + omega * turn };
}

// One tick of ship physics (SimulationUtils.nextPosition). Hitting a wall
// or a cluster stops the ship dead.
function moveShip(arena, ship, accel, targetDir) {
  const a = Math.max(0, Math.min(MAXACC, accel));
  let vx = ship.vx + Math.trunc(a * Math.cos(ship.dir));
  let vy = ship.vy + Math.trunc(a * Math.sin(ship.dir));
  vx = Math.trunc(vx - DRAG * vx);
  vy = Math.trunc(vy - DRAG * vy);
  let x = ship.x + vx;
  let y = ship.y + vy;
  const crashed = !isInArena(arena, x, y);
  if (crashed) {
    x = ship.x;
    y = ship.y;
    vx = 0;
    vy = 0;
  }
  let dir;
  const diff = wrapAngle(ship.dir - targetDir);
  if (Math.abs(diff) <= OMEGA) dir = targetDir;
  else dir = ship.dir + (diff < 0 ? OMEGA : -OMEGA);
  return { ship: { x, y, vx, vy, dir: wrapAngle(dir) }, crashed };
}

// ------------------------------------------------------------- bullets --

function moveBullet(b) {
  return {
    owner: b.owner,
    x: b.x + Math.trunc(BULLETSPEED * Math.cos(b.dir)),
    y: b.y + Math.trunc(BULLETSPEED * Math.sin(b.dir)),
    dir: b.dir,
  };
}

// The segment a bullet covered in its last step (Bullet.getLine()).
function bulletLine(b) {
  const n = moveBullet(b);
  return [b.x, b.y, 2 * b.x - n.x, 2 * b.y - n.y];
}

function bulletAlive(arena, b) {
  if (Math.abs(b.x) > ARENA_HALF || Math.abs(b.y) > ARENA_HALF) return false;
  const [x1, y1, x2, y2] = bulletLine(b);
  for (const c of arena.clusters) {
    const bb = c.bounds;
    if (Math.max(x1, x2) < bb.minX || Math.min(x1, x2) > bb.maxX || Math.max(y1, y2) < bb.minY || Math.min(y1, y2) > bb.maxY)
      continue;
    if (polygonEdgeHitsSegment(c.xs, c.ys, x1, y1, x2, y2) || polygonContains(c.xs, c.ys, b.x, b.y, false)) return false;
  }
  return true;
}

function bulletHitsShip(b, ship) {
  const [x1, y1, x2, y2] = bulletLine(b);
  const s = shipShape(ship);
  for (let i = 0; i < 3; i++) {
    const p = s[i], q = s[(i + 1) % 3];
    if (segmentsIntersect(p.x, p.y, q.x, q.y, x1, y1, x2, y2)) return true;
  }
  return false;
}

// ---------------------------------------------------------------- step --

export function step(arena, state, inputs) {
  const events = [];
  if (state.winner >= 0) return { state, events };

  const newTime = state.time + 1;
  const players = state.players.slice(); // updated in place, player by player (as in Java)
  let bullets = [];

  // Move bullets; drop those that left the arena or hit a cluster.
  for (const b of state.bullets) {
    const nb = moveBullet(b);
    if (bulletAlive(arena, nb)) bullets.push(nb);
  }

  const gotPwned = new Array(NUMPLAYERS).fill(-1);

  for (let p = 0; p < NUMPLAYERS; p++) {
    const prev = players[p];
    const input = inputs[p] || {};
    let ship = prev.ship;
    let score = prev.score;
    let untilReload = clampByte(prev.untilReload - 1);
    let untilRespawn = clampShort(prev.untilRespawn - 1);
    let sinceScoring = clampByte(prev.sinceScoring + 1);
    let sinceLooping = clampByte(prev.sinceLooping + 1);
    let sinceCrossing = clampByte(prev.sinceCrossing + 1);
    let sinceHitByBullet = clampShort(prev.sinceHitByBullet + 1);
    let sincePwned = clampByte(prev.sincePwned + 1);
    let sinceCrashed = clampByte(prev.sinceCrashed + 1);
    let respawn = prev.respawn;
    const tail = prev.tail;
    const crops = []; // [from, to) time ranges to cut from the tail this tick

    // (1) Hit by a bullet? Then rewind along the tail to the last knot.
    if (untilRespawn === 0) {
      const survivors = [];
      for (const b of bullets) {
        if (b.owner !== p && bulletHitsShip(b, ship)) sinceHitByBullet = 0;
        else survivors.push(b);
      }
      bullets = survivors;
      if (sinceHitByBullet === 0) {
        const toTime = T.lastKnotTime(tail);
        const len = T.lengthFromHeadTo(tail, toTime);
        respawn = { toTime, length: len };
        untilRespawn = Math.ceil(len / UNCOILSPEED);
        events.push({ type: "hit", player: p });
      }
    }

    if (untilRespawn > 0) {
      // Rewinding: slide back along the tail, eating it as we go.
      respawn = { toTime: respawn.toTime, length: respawn.length - UNCOILSPEED };
      let cropFrom = T.convertLengthToTime(tail, respawn.toTime, respawn.length, true);
      if (cropFrom === null) cropFrom = T.headTime(tail);
      const loc = T.getPoint(tail, cropFrom) || { x: ship.x, y: ship.y };
      ship = { x: loc.x, y: loc.y, vx: 0, vy: 0, dir: wrapAngle(state.time * 0.2) }; // spin
      // +1 so you never lose the knot you're rewinding to.
      crops.push([cropFrom + 1, newTime]);
    } else {
      // (3) Shoot. Each shot costs BULLETCOST of tail, taken from the base.
      const canShoot = prev.untilReload === 0 && prev.untilRespawn === 0 && T.length(tail) > BULLETCOST;
      if (input.shoot && canShoot) {
        bullets.push(moveBullet({ owner: p, x: ship.x, y: ship.y, dir: ship.dir }));
        untilReload = RELOADTIME;
        let cropTo = T.convertLengthToTime(tail, T.baseTime(tail), BULLETCOST, true);
        if (cropTo === null) cropTo = T.headTime(tail);
        crops.push([T.baseTime(tail), cropTo]);
        events.push({ type: "shot", player: p });
      }

      // (4) Crossings and loops.
      for (let q = 0; q < NUMPLAYERS; q++) {
        const other = players[q].tail;
        const same = q === p;
        const crossedAt = T.findCrossing(other, tail, same);
        if (crossedAt === null) continue;
        if (!same) {
          // Crossed the other player's tail: lose the oldest part of yours.
          sinceCrossing = 0;
          crops.push([T.baseTime(tail), T.cropToForCrossing(tail)]);
          events.push({ type: "cross", player: p, other: q });
        } else {
          // Closed a loop with your own tail.
          sinceLooping = 0;
          const loop = T.loopPolygon(tail, crossedAt);
          crops.push([crossedAt + 1, state.time]);
          let scored = null;
          if (score < arena.sequence.length && sinceHitByBullet > 0) {
            const want = arena.sequence[score];
            for (const c of arena.clusters) {
              if (c.nCells === want && polygonContains(loop.xs, loop.ys, c.center.x, c.center.y, true)) {
                scored = c;
                break;
              }
            }
          }
          if (scored) {
            score++;
            sinceScoring = 0;
          }
          for (let j = 0; j < NUMPLAYERS; j++) {
            const s = players[j].ship;
            if (j !== p && polygonContains(loop.xs, loop.ys, s.x, s.y, true)) gotPwned[j] = p;
          }
          events.push({ type: "loop", player: p, loop, scored: scored ? scored.name : null });
          if (scored) events.push({ type: "score", player: p, score });
        }
      }

      // (5) Move.
      const c = controls(prev, input);
      const moved = moveShip(arena, ship, c.accel, c.targetDir);
      ship = moved.ship;
      if (moved.crashed) {
        if (prev.sinceCrashed > 0) events.push({ type: "crash", player: p });
        sinceCrashed = 0;
      }
    }

    // (7) Update the tail: add the new point, mark a knot if we scored, crop.
    let newTailV = T.addPoint(tail, newTime, ship.x, ship.y);
    if (sinceScoring === 0) {
      const knotAt = T.indexOf(newTailV, T.headTime(tail)) >= 0 ? T.headTime(tail) : newTime;
      newTailV = T.addKnot(newTailV, knotAt);
    }
    newTailV = T.crop(newTailV, crops);

    players[p] = {
      ship,
      tail: newTailV,
      score,
      untilReload,
      untilRespawn,
      sinceScoring,
      sinceLooping,
      sinceCrossing,
      sinceHitByBullet,
      sincePwned,
      sinceCrashed,
      respawn,
      thrustTicks: input.thrust ? prev.thrustTicks + 1 : 0,
      turnTicks: input.left || input.right ? prev.turnTicks + 1 : 0,
    };
  }

  // Encircling the other player's ship resets their score.
  for (let j = 0; j < NUMPLAYERS; j++) {
    if (gotPwned[j] >= 0) {
      players[j] = { ...players[j], score: 0, sincePwned: 0 };
      events.push({ type: "pwned", player: j, by: gotPwned[j] });
    }
  }

  let winner = -1;
  for (let p = 0; p < NUMPLAYERS; p++) {
    if (players[p].score >= arena.sequence.length) {
      winner = p;
      events.push({ type: "win", player: p });
      break;
    }
  }

  return { state: { time: newTime, players, bullets, winner }, events };
}
