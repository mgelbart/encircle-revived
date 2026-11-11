// Simulation tests. Open tests/index.html in the browser (served over http).
import * as T from "../src/sim/tail.js";
import { initialState, step, mouseControls } from "../src/sim/sim.js";
import { makeAi, aiThink } from "../src/sim/ai.js";
import { generateArena } from "../src/sim/arena.js";
import { makeRng } from "../src/sim/rng.js";
import { BULLETCOST, ARENA_HALF, SEQUENCE_LENGTH, CLUSTERS_PER_VALUE } from "../src/sim/constants.js";
import { bounds, polygonContains } from "../src/sim/geom.js";

export const tests = [];
const test = (name, fn) => tests.push({ name, fn });
function assert(cond, msg) {
  if (!cond) throw new Error(msg || "assertion failed");
}

const NOINPUT = { thrust: false, left: false, right: false, shoot: false };

function squareCluster(nCells, cx, cy, r, name = "test") {
  const xs = [cx - r, cx + r, cx + r, cx - r];
  const ys = [cy - r, cy - r, cy + r, cy + r];
  return { name, nCells, xs, ys, bounds: bounds(xs, ys), center: { x: cx, y: cy }, img: { cx, cy, w: 2 * r, h: 2 * r } };
}

function arenaWith(clusters, sequence = [1, 2, 3]) {
  return { seed: 0, clusters, sequence };
}

// Put a player somewhere with a fresh tail.
function place(state, p, x, y, dir, time = state.time) {
  const players = state.players.slice();
  players[p] = { ...players[p], ship: { x, y, vx: 0, vy: 0, dir }, tail: T.newTail(time, x, y) };
  return { ...state, players };
}

// Fly east, turn left ~270 degrees, then fly south: crosses the first leg
// and closes a loop around roughly (L, R).
function loopPilot(state) {
  const me = state.players[0];
  if (state.time < 25) return { ...NOINPUT, thrust: true };
  if (!loopPilot.turned) {
    if (state.time > 30 && Math.abs(me.ship.dir - -Math.PI / 2) < 0.15) loopPilot.turned = true;
    else return { ...NOINPUT, thrust: true, left: true };
  }
  return { ...NOINPUT, thrust: true };
}

function runLoopScenario(arena, setup = (s) => s, ticks = 160) {
  loopPilot.turned = false;
  let s = initialState(arena);
  s = place(s, 0, -10000, -20000, 0);
  s = place(s, 1, -45000, 45000, 0);
  s = setup(s);
  const events = [];
  for (let i = 0; i < ticks && s.winner < 0; i++) {
    const r = step(arena, s, [loopPilot(s), NOINPUT]);
    s = r.state;
    events.push(...r.events.map((e) => ({ ...e, time: s.time })));
  }
  return { state: s, events };
}

function interiorPoint(loop) {
  // average of vertices is inside for these simple loops; verify anyway
  const x = loop.xs.reduce((a, b) => a + b, 0) / loop.xs.length;
  const y = loop.ys.reduce((a, b) => a + b, 0) / loop.ys.length;
  return { x: Math.round(x), y: Math.round(y) };
}

// ------------------------------------------------------------------ tail --

test("tail: addPoint merges points closer than the minimum segment", () => {
  let t = T.newTail(0, 0, 0);
  t = T.addPoint(t, 1, 1000, 0);
  t = T.addPoint(t, 2, 1200, 0); // within 500 of... no: compares to point before head (0,0) -> 1200 > 500, kept
  assert(T.nPoints(t) === 3, "expected 3 points, got " + T.nPoints(t));
  t = T.addPoint(t, 3, 1300, 0); // within 500 of point before head (1000,0) -> replaces head
  assert(T.nPoints(t) === 3, "expected merge, got " + T.nPoints(t));
  assert(T.headTime(t) === 3 && t.x[2] === 1300);
});

test("tail: crop never empties the tail and keeps a knot at the base", () => {
  let t = T.newTail(0, 0, 0);
  for (let i = 1; i <= 10; i++) t = T.addPoint(t, i, i * 1000, 0);
  t = T.addKnot(t, 5);
  let c = T.crop(t, [[0, 100]]);
  assert(T.nPoints(c) === 1 && T.headTime(c) === 10, "head kept");
  assert(c.knots.length === 1 && c.knots[0] === 10, "base knot");
  c = T.crop(t, [[0, 5]]);
  assert(T.baseTime(c) === 5 && c.knots[0] === 5, "knot at 5 is the new base");
  assert(Math.abs(T.length(c) - 5000) < 1e-9);
});

test("tail: convertLengthToTime and lengthFromHeadTo", () => {
  let t = T.newTail(0, 0, 0);
  for (let i = 1; i <= 10; i++) t = T.addPoint(t, i, i * 1000, 0);
  assert(T.convertLengthToTime(t, 0, 2500, true) === 3);
  assert(T.convertLengthToTime(t, 0, 20000, true) === null);
  assert(T.lengthFromHeadTo(t, 4) === 6000);
});

// ----------------------------------------------------------------- loops --

test("loop: flying a loop crops the loop out of the tail", () => {
  const { events, state } = runLoopScenario(arenaWith([]));
  const loops = events.filter((e) => e.type === "loop" && e.player === 0);
  assert(loops.length >= 1, "expected a loop event");
  assert(!events.some((e) => e.type === "score"), "nothing to score");
  // After the loop is cut out, the tail should be much shorter than the path flown.
  assert(T.nPoints(state.players[0].tail) < state.time, "tail was cropped");
});

test("scoring: encircling a cluster with the right cell count scores and adds a knot", () => {
  const probe = runLoopScenario(arenaWith([]));
  const loop = probe.events.find((e) => e.type === "loop").loop;
  const c = interiorPoint(loop);
  assert(polygonContains(loop.xs, loop.ys, c.x, c.y, true), "probe point inside loop");
  const arena = arenaWith([squareCluster(1, c.x, c.y, 500)], [1, 2]);
  const { events, state } = runLoopScenario(arena);
  const score = events.find((e) => e.type === "score");
  assert(score && score.player === 0, "expected player 0 to score");
  assert(state.players[0].score === 1);
  assert(state.players[0].tail.knots.length >= 2, "scoring adds a knot");
});

test("scoring: wrong cell count does not score", () => {
  const probe = runLoopScenario(arenaWith([]));
  const c = interiorPoint(probe.events.find((e) => e.type === "loop").loop);
  const arena = arenaWith([squareCluster(2, c.x, c.y, 500)], [1, 2]);
  const { events } = runLoopScenario(arena);
  assert(!events.some((e) => e.type === "score"));
});

test("pwn: encircling the other ship resets their score", () => {
  const probe = runLoopScenario(arenaWith([]));
  const c = interiorPoint(probe.events.find((e) => e.type === "loop").loop);
  const { events, state } = runLoopScenario(arenaWith([]), (s) => {
    s = place(s, 1, c.x, c.y, 0);
    s.players[1] = { ...s.players[1], score: 2 };
    return s;
  });
  const pw = events.find((e) => e.type === "pwned");
  assert(pw && pw.player === 1 && pw.by === 0, "expected blue to be encircled by red");
  assert(state.players[1].score === 0);
});

test("winning: completing the sequence ends the match", () => {
  const probe = runLoopScenario(arenaWith([]));
  const c = interiorPoint(probe.events.find((e) => e.type === "loop").loop);
  const arena = arenaWith([squareCluster(1, c.x, c.y, 500)], [1]);
  const { events, state } = runLoopScenario(arena);
  assert(events.some((e) => e.type === "win" && e.player === 0));
  assert(state.winner === 0);
  const after = step(arena, state, [NOINPUT, NOINPUT]);
  assert(after.state === state, "no more steps after a win");
});

// -------------------------------------------------------------- crossing --

test("crossing: flying over the other player's tail cuts your own tail", () => {
  const arena = arenaWith([]);
  let s = initialState(arena);
  // Blue's tail: a vertical line at x = 15000.
  let bt = T.newTail(0, 15000, -20000);
  for (let i = 1; i <= 40; i++) bt = T.addPoint(bt, i, 15000, -20000 + i * 1000);
  s.players[1] = { ...s.players[1], tail: bt, ship: { x: 15000, y: 20000, vx: 0, vy: 0, dir: 0 } };
  s = { ...s, time: 40 };
  // Red: a long tail heading east toward the line.
  let rt = T.newTail(0, -30000, 0);
  for (let i = 1; i <= 40; i++) rt = T.addPoint(rt, i, -30000 + i * 1000, 0);
  s.players[0] = { ...s.players[0], tail: rt, ship: { x: 10000, y: 0, vx: 1000, vy: 0, dir: 0 } };
  const before = T.length(rt);
  let crossed = null;
  for (let i = 0; i < 20 && !crossed; i++) {
    const r = step(arena, s, [{ ...NOINPUT, thrust: true }, NOINPUT]);
    s = r.state;
    crossed = r.events.find((e) => e.type === "cross");
  }
  assert(crossed && crossed.player === 0, "expected red to cross blue's tail");
  assert(T.length(s.players[0].tail) < before / 4, "red lost most of its tail (only a base knot)");
});

// ------------------------------------------------------- shooting & hits --

test("shooting costs tail, and a hit rewinds the target to its last knot", () => {
  const arena = arenaWith([]);
  let s = initialState(arena);
  s = { ...s, time: 60 };
  // Red: long tail along y=0 ending at the origin, facing east.
  let rt = T.newTail(0, -50000 + 1000, 0);
  for (let i = 1; i <= 49; i++) rt = T.addPoint(rt, i, -49000 + i * 1000, 0);
  s.players[0] = { ...s.players[0], tail: rt, ship: { x: 0, y: 0, vx: 0, vy: 0, dir: 0 } };
  // Blue: sitting at x = 12000 with a tail that has a knot 10000 back.
  let bt = T.newTail(0, 12000, -20000);
  for (let i = 1; i <= 20; i++) bt = T.addPoint(bt, i, 12000, -20000 + i * 1000);
  bt = T.addKnot(bt, 10);
  s.players[1] = { ...s.players[1], tail: bt, ship: { x: 12000, y: 0, vx: 0, vy: 0, dir: Math.PI / 2 } };

  const lenBefore = T.length(s.players[0].tail);
  let r = step(arena, s, [{ ...NOINPUT, shoot: true }, NOINPUT]);
  assert(r.events.some((e) => e.type === "shot"), "shot fired");
  const lenAfter = T.length(r.state.players[0].tail);
  assert(Math.abs(lenBefore - lenAfter - BULLETCOST) < 2000, `shot cost ~${BULLETCOST}, was ${lenBefore - lenAfter}`);
  s = r.state;
  let hit = false;
  for (let i = 0; i < 10 && !hit; i++) {
    r = step(arena, s, [NOINPUT, NOINPUT]);
    s = r.state;
    hit = r.events.some((e) => e.type === "hit" && e.player === 1);
  }
  assert(hit, "blue got hit");
  assert(s.players[1].untilRespawn > 0, "blue is rewinding");
  for (let i = 0; i < 40 && s.players[1].untilRespawn > 0; i++) s = step(arena, s, [NOINPUT, NOINPUT]).state;
  const b = s.players[1];
  assert(b.untilRespawn === 0, "rewind finished");
  assert(Math.abs(b.ship.x - 12000) < 1 && Math.abs(b.ship.y - -10000) < 1500, `back near the knot, at ${b.ship.x},${b.ship.y}`);
  assert(b.tail.knots.includes(10), "kept the knot");
});

test("walls and clusters stop the ship", () => {
  const arena = arenaWith([squareCluster(1, 5000, 0, 2000)]);
  let s = place(initialState(arena), 0, 0, 0, 0);
  let crashed = false;
  for (let i = 0; i < 30; i++) {
    const r = step(arena, s, [{ ...NOINPUT, thrust: true }, NOINPUT]);
    s = r.state;
    if (r.events.some((e) => e.type === "crash")) crashed = true;
  }
  assert(crashed, "crashed into cluster");
  assert(s.players[0].ship.x < 3000, "never entered the cluster");
  s = place(s, 0, ARENA_HALF - 3000, -30000, 0);
  for (let i = 0; i < 30; i++) s = step(arena, s, [{ ...NOINPUT, thrust: true }, NOINPUT]).state;
  assert(s.players[0].ship.x <= ARENA_HALF, "stayed inside the wall");
});

// ----------------------------------------------------------------- mouse --

test("mouse: full thrust toward a far cursor ahead, none for a cursor behind, none while stopped", () => {
  const ship = { x: 0, y: 0, vx: 0, vy: 0, dir: 0 };
  const ahead = mouseControls(ship, { x: 40000, y: 0 }, false);
  assert(ahead.accel === 112 && Math.abs(ahead.targetDir) < 1e-12, "full thrust ahead");
  const near = mouseControls(ship, { x: 3000, y: 0 }, false);
  assert(near.accel > 0 && near.accel < 112, "partial thrust when the cursor is close");
  const behind = mouseControls(ship, { x: -20000, y: 1000 }, false);
  assert(behind.accel === 0 && behind.targetDir > 3, "turn around, no thrust");
  assert(mouseControls(ship, { x: 40000, y: 0 }, true).accel === 0, "stop key cuts thrust");
  assert(mouseControls(ship, null, false).accel === 0, "no cursor yet");
});

test("mouse: the ship flies to the cursor through step()", () => {
  const arena = arenaWith([]);
  let s = place(initialState(arena), 0, -30000, -30000, 0);
  for (let i = 0; i < 200; i++) s = step(arena, s, [{ target: { x: 20000, y: 25000 }, stop: false, shoot: false }, NOINPUT]).state;
  const sh = s.players[0].ship;
  assert(Math.hypot(sh.x - 20000, sh.y - 25000) < 15000, `ended near the cursor, at ${sh.x},${sh.y}`);
});

// ----------------------------------------------------- arena & determinism --

export function arenaTests(database) {
  test("arena: clusters don't overlap, sequence is valid, deterministic by seed", () => {
    for (const seed of [1, 2, 3, 42, 2011]) {
      const a = generateArena(database, seed);
      const total = CLUSTERS_PER_VALUE.reduce((x, y) => x + y, 0);
      assert(a.clusters.length === total, `seed ${seed}: ${a.clusters.length} clusters`);
      assert(a.sequence.length === SEQUENCE_LENGTH);
      for (let i = 1; i < a.sequence.length; i++) assert(a.sequence[i] !== a.sequence[i - 1], "no repeats");
      for (const n of a.sequence) assert(a.clusters.some((c) => c.nCells === n), `a ${n}-cell cluster exists`);
      for (const c of a.clusters) {
        assert(Math.abs(c.center.x) < ARENA_HALF && Math.abs(c.center.y) < ARENA_HALF, "inside arena");
      }
      const b = generateArena(database, seed);
      assert(JSON.stringify(a) === JSON.stringify(b), "same seed, same arena");
    }
  });

  test("ai: two computer players score, shoot and finish matches", () => {
    let scores = 0, shots = 0, wins = 0;
    for (const seed of [2, 6]) {
      const arena = generateArena(database, seed);
      let s = initialState(arena);
      const ais = [makeAi(0, seed), makeAi(1, seed + 1)];
      for (let i = 0; i < 25 * 300 && s.winner < 0; i++) {
        const r = step(arena, s, [aiThink(ais[0], arena, s), aiThink(ais[1], arena, s)]);
        s = r.state;
        for (const e of r.events) {
          if (e.type === "score") scores++;
          if (e.type === "shot") shots++;
        }
      }
      if (s.winner >= 0) wins++;
    }
    assert(scores >= 6, `scored ${scores}`);
    assert(shots >= 1, `shot ${shots}`);
    assert(wins >= 1, `won ${wins} of 2 matches within 5 minutes`);
  });

  test("determinism: same seed and inputs give identical games", () => {
    const run = () => {
      const arena = generateArena(database, 7);
      const rand = makeRng(99);
      let s = initialState(arena);
      for (let i = 0; i < 1500; i++) {
        const inp = () => ({ thrust: rand() < 0.7, left: rand() < 0.3, right: rand() < 0.2, shoot: rand() < 0.05 });
        s = step(arena, s, [inp(), inp()]).state;
      }
      return JSON.stringify(s);
    };
    assert(run() === run());
  });
}
