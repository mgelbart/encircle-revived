// Rollback netcode for two players. Pure logic: no DOM, no networking.
//
// Both machines run the same deterministic simulation. Each one applies its
// own input a few ticks late (DELAY) and sends it to the other side right away.
// When the other player's input for a tick hasn't arrived yet, we guess that
// they're still doing what they last did. When their real input arrives and
// the guess was wrong, we rewind to that tick and re-run the ticks since.
//
// state(t) means the game state at the start of tick t; step() with the
// inputs for tick t turns it into state(t+1).

import { step } from "../sim/sim.js";

export const DELAY = 2; // ticks of input delay (80 ms): hides most of the lag
export const MAX_AHEAD = 10; // stop and wait if this many ticks ahead of the last real remote input
export const HASH_EVERY = 25; // ticks between desync checks (1 s)
const KEEP_STATES = 2 * MAX_AHEAD + 4;

export const NEUTRAL = { thrust: false, left: false, right: false, shoot: false };

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

// FNV-1a over the JSON text of a state. JSON prints numbers in their shortest
// exact form, so two states hash alike only if every number matches exactly.
export function hashState(state) {
  const s = JSON.stringify(state);
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

export class Rollback {
  // me: this machine's player number (0 or 1).
  constructor(arena, initial, me) {
    this.arena = arena;
    this.me = me;
    this.them = 1 - me;
    this.tick = initial.time; // the tick about to be simulated; state(tick) is current
    this.states = new Map([[this.tick, initial]]);
    this.inputs = [new Map(), new Map()]; // real inputs, by tick
    this.used = new Map(); // remote input actually used for each simulated tick
    this.remoteUpTo = this.tick + DELAY - 1; // all remote inputs up to here are known
    this.redoFrom = Infinity; // earliest tick that was simulated with a wrong guess
    this.hashes = new Map(); // tick -> hash of confirmed state(tick)
    this.hashedUpTo = this.tick;
    this.rollbacks = 0; // stats
    this.resimulated = 0;
    for (let t = this.tick; t < this.tick + DELAY; t++) {
      this.inputs[0].set(t, NEUTRAL);
      this.inputs[1].set(t, NEUTRAL);
    }
  }

  get current() {
    return this.states.get(this.tick);
  }
  get previous() {
    return this.states.get(this.tick - 1) || this.current;
  }
  // state(confirmed) depends only on real inputs.
  get confirmed() {
    return Math.min(this.tick, this.remoteUpTo + 1);
  }
  get winner() {
    const s = this.current;
    return s.winner >= 0 && this.confirmed >= this.tick ? s.winner : -1;
  }

  // Can we simulate another tick now, or are we too far ahead of the other player?
  canAdvance() {
    return this.tick - this.remoteUpTo <= MAX_AHEAD && this.current.winner < 0;
  }

  // Records this machine's input. It applies at tick + DELAY; returns that tick
  // so the caller can send { t, input } to the other side.
  addLocal(input) {
    const t = this.tick + DELAY;
    if (!this.inputs[this.me].has(t)) this.inputs[this.me].set(t, input);
    return t;
  }

  // An input from the other machine (they arrive in order).
  addRemote(t, input) {
    if (this.inputs[this.them].has(t)) return;
    this.inputs[this.them].set(t, input);
    while (this.inputs[this.them].has(this.remoteUpTo + 1)) this.remoteUpTo++;
    if (t < this.tick && !same(this.used.get(t), input)) this.redoFrom = Math.min(this.redoFrom, t);
  }

  // The other player's input for tick t: the real one, or a guess.
  remoteInput(t) {
    const real = this.inputs[this.them].get(t);
    if (real) return real;
    return this.inputs[this.them].get(this.remoteUpTo) || NEUTRAL;
  }

  simulate(t) {
    const remote = this.remoteInput(t);
    const local = this.inputs[this.me].get(t) || NEUTRAL;
    const inputs = this.me === 0 ? [local, remote] : [remote, local];
    this.used.set(t, remote);
    const { state, events } = step(this.arena, this.states.get(t), inputs);
    this.states.set(t + 1, state);
    return events;
  }

  // Re-runs ticks that used a wrong guess. Call before advance().
  // Returns true if anything changed.
  reconcile() {
    if (this.redoFrom === Infinity) return false;
    const from = this.redoFrom;
    this.redoFrom = Infinity;
    if (!this.states.has(from)) throw new Error(`rollback to ${from}: state no longer kept`);
    this.rollbacks++;
    for (let t = from; t < this.tick; t++) {
      this.simulate(t);
      this.resimulated++;
    }
    return true;
  }

  // Simulates one new tick. Returns its events (sounds, effects).
  advance() {
    const events = this.simulate(this.tick);
    this.tick++;
    this.states.delete(this.tick - KEEP_STATES);
    this.used.delete(this.tick - KEEP_STATES);
    return events;
  }

  // Hashes of newly confirmed states, every HASH_EVERY ticks: [{ t, h }].
  newHashes() {
    const out = [];
    const c = this.confirmed;
    while (this.hashedUpTo + HASH_EVERY <= c) {
      this.hashedUpTo += HASH_EVERY;
      const t = this.hashedUpTo;
      const h = hashState(this.states.get(t));
      this.hashes.set(t, h);
      this.hashes.delete(t - 40 * HASH_EVERY);
      out.push({ t, h });
    }
    return out;
  }

  // Replaces state(t) with the other machine's copy (after a desync) and
  // re-runs everything since.
  // Returns false if t is out of reach (then wait for the next check).
  replaceState(t, state) {
    if (t > this.tick || !this.states.has(t)) return false;
    this.states.set(t, state);
    for (let u = t; u < this.tick; u++) this.simulate(u);
    // Hashes after t came from the wrong states; recompute them.
    for (const u of this.hashes.keys()) if (u > t) this.hashes.delete(u);
    this.hashedUpTo = Math.min(this.hashedUpTo, Math.floor(t / HASH_EVERY) * HASH_EVERY);
    this.redoFrom = Infinity;
    return true;
  }
}
