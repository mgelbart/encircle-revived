// One online match series between two machines, on top of a message link.
// Handles the protocol (hello, start, inputs, desync checks, rematches) and
// the timing; the page (main.js) handles screens and input devices.
//
// The host is player 0 (red) and picks the maps. The guest is player 1 (blue).
//
// A link is anything with send(obj) and close(), whose owner calls
// receive(msg) for each message and closed() when it goes away.
// `now` is a clock in milliseconds (performance.now in the browser).

import { TIMESTEP } from "../sim/constants.js";
import { generateArena } from "../sim/arena.js";
import { initialState } from "../sim/sim.js";
import { Rollback, DELAY, hashState } from "./rollback.js";

export const PROTOCOL = 1;
export const COUNTDOWN_MS = 2000;
const PING_MS = 500;
const SILENCE_MS = 10000; // no messages for this long = connection lost
const MAX_TICKS_PER_UPDATE = 50;

export class OnlineGame {
  // hooks: onBegin(), onOver(winner), onRematchWanted(), onLost(reason)
  constructor(link, { isHost, database, now, hooks = {} }) {
    this.link = link;
    this.isHost = isHost;
    this.me = isHost ? 0 : 1;
    this.database = database;
    this.now = now;
    this.hooks = hooks;
    this.phase = "connecting"; // connecting | countdown | playing | over | lost
    this.match = 0;
    this.sess = null;
    this.arena = null;
    this.seed = 0;
    this.startAt = 0;
    this.rtt = null; // smoothed round trip, ms
    this.gotHello = false;
    this.lastHeard = now();
    this.lastPing = -Infinity;
    this.wantRematch = [false, false];
    this.remoteHashes = new Map();
    this.remoteClock = null; // { tick, at }: the other side's tick when they last sent input
    this.desyncs = 0;
    this.stalledSince = null;
    this.nextSeed = null; // host: override for tests
    this.send({ type: "hello", v: PROTOCOL });
  }

  send(msg) {
    if (this.phase === "lost") return;
    try {
      this.link.send(msg);
    } catch (e) {
      this.lose("connection lost");
    }
  }

  lose(reason) {
    if (this.phase === "lost") return;
    this.phase = "lost";
    this.hooks.onLost?.(reason);
  }

  // The other side left or the link dropped.
  closed() {
    this.lose("Your opponent left");
  }

  leave() {
    this.send({ type: "bye" });
    this.phase = "lost";
    this.link.close();
  }

  get stalled() {
    return this.stalledSince !== null && this.now() - this.stalledSince > 300;
  }

  // ------------------------------------------------------------ messages --

  receive(msg) {
    const now = this.now();
    this.lastHeard = now;
    switch (msg.type) {
      case "hello":
        if (msg.v !== PROTOCOL) {
          this.lose("Your opponent is running a different version of the game. Both of you, reload the page.");
          return;
        }
        this.gotHello = true;
        this.maybeStartFirst();
        break;
      case "ping":
        this.send({ type: "pong", t: msg.t });
        break;
      case "pong": {
        const r = now - msg.t;
        this.rtt = this.rtt === null ? r : 0.8 * this.rtt + 0.2 * r;
        this.maybeStartFirst();
        break;
      }
      case "start":
        if (!this.isHost) this.begin(msg.seed, msg.match, now + COUNTDOWN_MS - (this.rtt ?? 100) / 2);
        break;
      case "in":
        if (msg.m !== this.match || !this.sess) return;
        this.sess.addRemote(msg.t, msg.i);
        this.remoteClock = { tick: msg.t - DELAY, at: now };
        break;
      case "hash":
        if (msg.m !== this.match) return;
        this.remoteHashes.set(msg.t, msg.h);
        break;
      case "desync":
        // Guest's state differs from ours: send ours (the host's copy wins).
        if (this.isHost && msg.m === this.match && this.sess) {
          const t = this.sess.confirmed;
          this.send({ type: "state", m: this.match, t, s: this.sess.states.get(t) });
        }
        break;
      case "state":
        if (msg.m === this.match && this.sess && this.sess.replaceState(msg.t, msg.s)) this.remoteHashes.clear();
        break;
      case "rematch":
        if (msg.m !== this.match) return;
        this.wantRematch[1 - this.me] = true;
        this.hooks.onRematchWanted?.();
        this.maybeRematch();
        break;
      case "bye":
        this.lose("Your opponent left");
        break;
    }
  }

  // Host: start the first match once both sides are talking and we know the lag.
  maybeStartFirst() {
    if (this.isHost && this.phase === "connecting" && this.gotHello && this.rtt !== null) this.hostStart();
  }

  hostStart() {
    const seed = this.nextSeed ?? (Math.random() * 2 ** 32) >>> 0;
    this.nextSeed = null;
    const match = this.match + 1;
    this.send({ type: "start", seed, match });
    this.begin(seed, match, this.now() + COUNTDOWN_MS);
  }

  begin(seed, match, startAt) {
    this.seed = seed >>> 0;
    this.match = match;
    this.arena = generateArena(this.database, this.seed);
    this.sess = new Rollback(this.arena, initialState(this.arena), this.me);
    this.startAt = startAt;
    this.phase = "countdown";
    this.wantRematch = [false, false];
    this.remoteHashes.clear();
    this.remoteClock = null;
    this.stalledSince = null;
    this.hooks.onBegin?.();
  }

  requestRematch() {
    if (this.phase !== "over" || this.wantRematch[this.me]) return;
    this.wantRematch[this.me] = true;
    this.send({ type: "rematch", m: this.match });
    this.maybeRematch();
  }

  maybeRematch() {
    if (this.isHost && this.phase === "over" && this.wantRematch[0] && this.wantRematch[1]) this.hostStart();
  }

  // --------------------------------------------------------------- ticks --

  // Call often (every frame and from a timer). sample() returns this
  // machine's input right now. Returns events from newly simulated ticks.
  update(sample) {
    const now = this.now();
    if (this.phase === "lost") return [];
    if (now - this.lastHeard > SILENCE_MS) {
      this.lose("Connection lost");
      return [];
    }
    if (now - this.lastPing >= PING_MS) {
      this.lastPing = now;
      this.send({ type: "ping", t: now });
    }
    if (!this.sess || this.phase === "over" || this.phase === "connecting") return [];
    if (now < this.startAt) return [];
    this.phase = "playing";

    const sess = this.sess;
    sess.reconcile();
    const events = [];
    const target = Math.floor((now - this.startAt) / TIMESTEP);
    let n = 0;
    while (sess.tick < target && n < MAX_TICKS_PER_UPDATE) {
      if (!sess.canAdvance()) break;
      const input = sample();
      const t = sess.addLocal(input);
      this.send({ type: "in", m: this.match, t, i: input });
      events.push(...sess.advance());
      n++;
    }
    const waiting = sess.tick < target && !sess.canAdvance() && sess.current.winner < 0;
    if (!waiting) this.stalledSince = null;
    else if (this.stalledSince === null) this.stalledSince = now;

    this.checkHashes();
    this.keepInStep(now);

    if (sess.winner >= 0) {
      this.phase = "over";
      this.hooks.onOver?.(sess.winner);
    }
    return events;
  }

  checkHashes() {
    for (const { t, h } of this.sess.newHashes()) {
      if (this.isHost) this.send({ type: "hash", m: this.match, t, h });
    }
    if (this.isHost) return;
    for (const [t, h] of this.remoteHashes) {
      const mine = this.sess.hashes.get(t);
      if (mine === undefined) continue;
      this.remoteHashes.delete(t);
      if (mine !== h) {
        this.desyncs++;
        console.warn(`encircle: out of sync at tick ${t}; taking the host's state`);
        this.send({ type: "desync", m: this.match, t });
        break;
      }
    }
  }

  // If we're running ahead of the other machine, slow down a little.
  keepInStep(now) {
    if (!this.remoteClock || this.rtt === null) return;
    const theirs = this.remoteClock.tick + (now - this.remoteClock.at + this.rtt / 2) / TIMESTEP;
    if (this.sess.tick - theirs > 1.5) this.startAt += 0.5;
  }

  // Where to draw: previous and current state plus how far between them we are.
  view() {
    const s = this.sess;
    if (!s) return null;
    const frac = (this.now() - this.startAt) / TIMESTEP - s.tick;
    return { prev: s.previous, cur: s.current, alpha: Math.max(0, Math.min(1, frac)) };
  }
}

export { hashState };
