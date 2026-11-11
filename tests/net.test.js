// Netcode tests: two OnlineGames talking over a fake link with lag, jitter
// and a fake clock. Checks that both machines end up with exactly the same
// game as a plain run of the simulation with the same inputs.

import { TIMESTEP } from "../src/sim/constants.js";
import { generateArena } from "../src/sim/arena.js";
import { initialState, step } from "../src/sim/sim.js";
import { makeRng } from "../src/sim/rng.js";
import { OnlineGame, COUNTDOWN_MS } from "../src/net/online.js";
import { Rollback, NEUTRAL, DELAY, hashState } from "../src/net/rollback.js";

export const netTests = [];
const test = (name, fn) => netTests.push({ name, fn });
function assert(cond, msg) {
  if (!cond) throw new Error(msg || "assertion failed");
}

// A world with a clock and two links that deliver in order after a delay.
function makeWorld({ latency = 60, jitter = 0, seed = 7 }) {
  const rand = makeRng(seed);
  const world = { time: 0, queue: [], games: [] };
  const clock = () => world.time;
  const linkFrom = (from) => {
    let lastArrival = 0;
    return {
      send(msg) {
        // Reliable, ordered: never arrive before an earlier message.
        const arrival = Math.max(lastArrival, world.time + latency + rand() * jitter);
        lastArrival = arrival;
        world.queue.push({ at: arrival, to: 1 - from, msg: JSON.parse(JSON.stringify(msg)) });
      },
      close() {},
    };
  };
  world.clock = clock;
  world.linkFrom = linkFrom;
  world.deliver = () => {
    world.queue.sort((a, b) => a.at - b.at);
    while (world.queue.length && world.queue[0].at <= world.time) {
      const { to, msg } = world.queue.shift();
      world.games[to].receive(msg);
    }
  };
  return world;
}

// Scripted pilots: a changing mix of keyboard and mouse inputs.
function pilot(p, seed) {
  const rand = makeRng(seed);
  let cur = NEUTRAL, left = 0;
  return () => {
    if (left-- <= 0) {
      left = Math.floor(rand() * 20);
      if (p === 0) cur = { thrust: rand() < 0.7, left: rand() < 0.3, right: rand() < 0.3, shoot: rand() < 0.1 };
      else cur = { target: { x: Math.round((rand() - 0.5) * 80000), y: Math.round((rand() - 0.5) * 80000) }, stop: rand() < 0.1, shoot: rand() < 0.1 };
    }
    return cur;
  };
}

async function runOnline(database, { latency, jitter, ms, hostSeed = 12345, frameMs = 16, pauseGuest = null }) {
  const world = makeWorld({ latency, jitter });
  const pilots = [pilot(0, 1), pilot(1, 2)];
  const log = [[], []]; // inputs each side actually generated, by tick
  const mk = (isHost) =>
    new OnlineGame(world.linkFrom(isHost ? 0 : 1), { isHost, database, now: world.clock });
  const host = mk(true);
  host.nextSeed = hostSeed;
  world.games = [host, null];
  const guest = mk(false);
  world.games[1] = guest;
  const games = [host, guest];
  for (; world.time < ms; world.time += frameMs) {
    world.deliver();
    for (const p of [0, 1]) {
      if (p === 1 && pauseGuest && world.time >= pauseGuest[0] && world.time < pauseGuest[1]) continue;
      const g = games[p];
      g.update(() => {
        const i = pilots[p]();
        log[p][g.sess.tick + DELAY] = i;
        return i;
      });
    }
  }
  return { host, guest, log };
}

// The game as it should be: the plain simulation with the real inputs.
function replay(arena, log, ticks) {
  let s = initialState(arena);
  for (let t = 0; t < ticks; t++) {
    const inp = [log[0][t] || NEUTRAL, log[1][t] || NEUTRAL];
    s = step(arena, s, inp).state;
  }
  return s;
}

function checkRun(r, label) {
  const { host, guest, log } = r;
  assert(host.sess && guest.sess, `${label}: match never started`);
  assert(host.seed === guest.seed, `${label}: different maps`);
  const t = Math.min(host.sess.confirmed, guest.sess.confirmed);
  assert(t > 100, `${label}: only ${t} ticks confirmed`);
  const hs = host.sess.states.get(t), gs = guest.sess.states.get(t);
  assert(hs && gs, `${label}: state ${t} not kept`);
  assert(hashState(hs) === hashState(gs), `${label}: host and guest differ at tick ${t}`);
  const truth = replay(host.arena, log, t);
  assert(hashState(truth) === hashState(hs), `${label}: differs from a plain replay at tick ${t}`);
  assert(guest.desyncs === 0, `${label}: ${guest.desyncs} desyncs`);
  return t;
}

export function netTestsWith(database) {
  test("step() does not modify the state it is given", () => {
    const arena = generateArena(database, 3);
    let s = initialState(arena);
    const pl = [pilot(0, 5), pilot(1, 6)];
    for (let t = 0; t < 400; t++) {
      const before = JSON.stringify(s);
      const next = step(arena, s, [pl[0](), pl[1]()]).state;
      assert(JSON.stringify(s) === before, `tick ${t} modified its input state`);
      s = next;
    }
  });

  test("rollback: a late input rewinds and replays to the right state", () => {
    const arena = generateArena(database, 4);
    const r = new Rollback(arena, initialState(arena), 0);
    const mine = [], theirs = [];
    const pl = [pilot(0, 8), pilot(1, 9)];
    for (let t = 0; t < 20; t++) {
      const i = pl[0]();
      mine[r.addLocal(i)] = i;
      r.advance();
    }
    // Now all of the other side's inputs arrive at once.
    for (let t = DELAY; t < 20; t++) {
      theirs[t] = pl[1]();
      r.addRemote(t, theirs[t]);
    }
    assert(r.reconcile(), "expected a rollback");
    const truth = replay(arena, [mine, theirs], 20);
    assert(hashState(truth) === hashState(r.current), "replayed state differs");
  });

  test("online: 60 ms each way, both sides agree with a plain replay", async () => {
    const r = await runOnline(database, { latency: 60, jitter: 0, ms: 20000 });
    checkRun(r, "60ms");
  });

  test("online: 150 ms each way with 80 ms jitter", async () => {
    const r = await runOnline(database, { latency: 150, jitter: 80, ms: 20000, frameMs: 17 });
    const t = checkRun(r, "150ms");
    assert(r.host.sess.rollbacks > 0, "expected some rollbacks");
    // Neither side should run far ahead of the other.
    const gap = Math.abs(r.host.sess.tick - r.guest.sess.tick);
    assert(gap < 12, `sides drifted ${gap} ticks apart (confirmed ${t})`);
  });

  test("online: guest's tab freezes for 3 s, then both catch up", async () => {
    const r = await runOnline(database, { latency: 40, jitter: 10, ms: 20000, pauseGuest: [6000, 9000] });
    checkRun(r, "freeze");
    const expected = Math.floor((20000 - COUNTDOWN_MS - 200) / TIMESTEP);
    assert(r.host.sess.tick > expected - 30, `host only reached tick ${r.host.sess.tick}`);
  });

  test("online: a desync is detected and repaired from the host", async () => {
    const world = makeWorld({ latency: 50 });
    const pilots = [pilot(0, 3), pilot(1, 4)];
    const host = new OnlineGame(world.linkFrom(0), { isHost: true, database, now: world.clock });
    world.games = [host, null];
    const guest = new OnlineGame(world.linkFrom(1), { isHost: false, database, now: world.clock });
    world.games[1] = guest;
    let broke = false;
    for (; world.time < 15000; world.time += 16) {
      world.deliver();
      host.update(pilots[0]);
      guest.update(pilots[1]);
      if (!broke && guest.sess && guest.sess.tick === 100) {
        // Simulate a floating-point difference between browsers.
        const s = guest.sess.states.get(100);
        s.players[0] = { ...s.players[0], ship: { ...s.players[0].ship, x: s.players[0].ship.x + 1e-9 } };
        broke = true;
      }
    }
    assert(broke, "never reached tick 100");
    assert(guest.desyncs >= 1, "desync not detected");
    const t = Math.min(host.sess.confirmed, guest.sess.confirmed);
    assert(hashState(host.sess.states.get(t)) === hashState(guest.sess.states.get(t)), "still out of sync");
  });

  test("online: rematch needs both players, then a new map starts", async () => {
    const world = makeWorld({ latency: 30 });
    const host = new OnlineGame(world.linkFrom(0), { isHost: true, database, now: world.clock });
    world.games = [host, null];
    const guest = new OnlineGame(world.linkFrom(1), { isHost: false, database, now: world.clock });
    world.games[1] = guest;
    const run = (ms) => {
      for (const end = world.time + ms; world.time < end; world.time += 16) {
        world.deliver();
        host.update(() => NEUTRAL);
        guest.update(() => NEUTRAL);
      }
    };
    run(3000);
    assert(host.phase === "playing" && guest.phase === "playing", `phases ${host.phase}/${guest.phase}`);
    // Pretend the match ended.
    for (const g of [host, guest]) g.phase = "over";
    const firstSeed = host.seed;
    guest.requestRematch();
    run(500);
    assert(host.phase === "over", "host restarted without its own player agreeing");
    host.requestRematch();
    run(500);
    assert(host.match === 2 && guest.match === 2, `matches ${host.match}/${guest.match}`);
    assert(guest.seed === host.seed && host.seed !== firstSeed, "rematch map wrong");
  });

  test("online: the other side leaving is noticed", async () => {
    const world = makeWorld({ latency: 30 });
    let lost = null;
    const host = new OnlineGame(world.linkFrom(0), { isHost: true, database, now: world.clock, hooks: { onLost: (r) => (lost = r) } });
    world.games = [host, null];
    const guest = new OnlineGame(world.linkFrom(1), { isHost: false, database, now: world.clock });
    world.games[1] = guest;
    for (; world.time < 3000; world.time += 16) {
      world.deliver();
      host.update(() => NEUTRAL);
      guest.update(() => NEUTRAL);
    }
    guest.leave();
    for (; world.time < 3500; world.time += 16) world.deliver();
    assert(lost && host.phase === "lost", "host did not notice");
  });
}
