// Canvas renderer. Draws a simulation state; never changes it.
// The look follows the 2011 Java client: galaxy background, cluster images
// with green outlines, thin tails with X-shaped knots, triangle ships,
// green bullets with a fading blue streak, and a sequence scoreboard on top.

import { ARENA_HALF, ARENASIZE, NUMPLAYERS, TIMESTEP } from "../sim/constants.js";
import { shipShape } from "../sim/sim.js";
import { wrapAngle } from "../sim/geom.js";

export const PLAYERS = [
  { name: "Red", color: "#ff3b30", dark: "#5c1410", glow: "rgba(255,59,48,0.35)" },
  { name: "Blue", color: "#3d7bff", dark: "#122a5c", glow: "rgba(61,123,255,0.35)" },
];
const GREEN = "#2bff5a";
const KNOTSIZE = ARENASIZE * 0.01;
const BULLETSIZE = ARENASIZE * 0.005;
const BULLET_STREAK = ARENASIZE / 15;
const EFFECT_TICKS = 1500 / TIMESTEP; // scoring/pwned effects last 1.5 s
const HUD_H = 64;

export class Renderer {
  constructor(canvas, assets) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.assets = assets; // { bg: Image, clusterImages: Map<name, Image> }
    this.effects = []; // { kind, player, loop?, startTick }
    this.messages = []; // { text, color, until (tick) }
    this.resize();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const w = this.canvas.clientWidth, h = this.canvas.clientHeight;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.dpr = dpr;
    this.w = w;
    this.h = h;
    const pad = 12;
    const size = Math.max(100, Math.min(w - 2 * pad, h - HUD_H - 2 * pad));
    this.scale = size / (2 * ARENA_HALF);
    this.ox = w / 2;
    this.oy = HUD_H + pad + size / 2;
    this.size = size;
  }

  // arena -> screen (CSS pixels); arena y points up.
  sx(x) { return this.ox + x * this.scale; }
  sy(y) { return this.oy - y * this.scale; }

  // Feed simulation events so effects and messages can be shown.
  onEvents(events, tick) {
    for (const e of events) {
      const who = PLAYERS[e.player];
      if (e.type === "loop" && e.scored) this.effects.push({ kind: "score", player: e.player, loop: e.loop, startTick: tick });
      else if (e.type === "loop") this.effects.push({ kind: "loop", player: e.player, loop: e.loop, startTick: tick });
      if (e.type === "pwned") {
        this.effects.push({ kind: "pwned", player: e.player, startTick: tick });
        this.say(`${PLAYERS[e.by].name} encircled ${who.name}! ${who.name} starts over.`, PLAYERS[e.by].color, tick);
      }
      if (e.type === "hit") this.say(`${who.name} got shot: rewinding to last knot`, who.color, tick);
      if (e.type === "cross") this.say(`${who.name} crossed ${PLAYERS[e.other].name}'s tail and lost some of it`, who.color, tick, 60);
    }
  }

  say(text, color, tick, ticks = 90) {
    this.messages = this.messages.filter((m) => m.text !== text);
    this.messages.push({ text, color, until: tick + ticks });
    if (this.messages.length > 3) this.messages.shift();
  }

  clearEffects() {
    this.effects = [];
    this.messages = [];
  }

  // prev/cur: consecutive states; alpha in [0,1] interpolates ship motion.
  draw(arena, prev, cur, alpha) {
    const ctx = this.ctx;
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, this.w, this.h);
    if (!arena || !cur) return;
    const tick = cur.time;
    this.effects = this.effects.filter((e) => tick - e.startTick < EFFECT_TICKS);
    this.messages = this.messages.filter((m) => m.until > tick);

    this.drawBackground();
    this.drawClusters(arena);
    const ships = cur.players.map((p, i) => this.lerpShip(prev ? prev.players[i] : p, p, alpha));
    for (let i = 0; i < NUMPLAYERS; i++) this.drawTail(i, cur.players[i], ships[i]);
    this.drawEffects(tick);
    this.drawBullets(prev, cur, alpha);
    for (let i = 0; i < NUMPLAYERS; i++) this.drawShip(i, ships[i], tick);
    this.drawHud(arena, cur);
    this.drawMessages();
  }

  lerpShip(a, b, alpha) {
    // Don't interpolate across a rewind jump.
    if (b.untilRespawn > 0 || Math.hypot(b.ship.x - a.ship.x, b.ship.y - a.ship.y) > ARENASIZE * 0.05) return { ...b.ship };
    return {
      x: a.ship.x + (b.ship.x - a.ship.x) * alpha,
      y: a.ship.y + (b.ship.y - a.ship.y) * alpha,
      dir: a.ship.dir + wrapAngle(b.ship.dir - a.ship.dir) * alpha,
    };
  }

  drawBackground() {
    const ctx = this.ctx;
    const x0 = this.sx(-ARENA_HALF), y0 = this.sy(ARENA_HALF), s = this.size;
    ctx.save();
    ctx.beginPath();
    ctx.rect(x0, y0, s, s);
    ctx.clip();
    const bg = this.assets.bg;
    if (bg && bg.complete && bg.naturalWidth) {
      // Cover the arena square, keeping the image's aspect ratio.
      const k = Math.max(s / bg.naturalWidth, s / bg.naturalHeight);
      const w = bg.naturalWidth * k, h = bg.naturalHeight * k;
      ctx.globalAlpha = 0.85;
      ctx.drawImage(bg, x0 + (s - w) / 2, y0 + (s - h) / 2, w, h);
      ctx.globalAlpha = 1;
    }
    ctx.restore();
    ctx.strokeStyle = "rgba(255,255,255,0.25)";
    ctx.lineWidth = 1;
    ctx.strokeRect(x0 + 0.5, y0 + 0.5, s - 1, s - 1);
  }

  drawClusters(arena) {
    const ctx = this.ctx;
    for (const c of arena.clusters) {
      const img = this.assets.clusterImages.get(c.name);
      const w = c.img.w * this.scale, h = c.img.h * this.scale;
      if (img && img.complete) ctx.drawImage(img, this.sx(c.img.cx) - w / 2, this.sy(c.img.cy) - h / 2, w, h);
      ctx.beginPath();
      for (let i = 0; i < c.xs.length; i++) {
        const X = this.sx(c.xs[i]), Y = this.sy(c.ys[i]);
        if (i === 0) ctx.moveTo(X, Y);
        else ctx.lineTo(X, Y);
      }
      ctx.closePath();
      ctx.strokeStyle = GREEN;
      ctx.lineWidth = 1;
      ctx.stroke();
    }
  }

  drawTail(i, player, ship) {
    const ctx = this.ctx;
    const t = player.tail;
    const col = PLAYERS[i].color;
    ctx.beginPath();
    for (let k = 0; k < t.t.length; k++) {
      const X = this.sx(t.x[k]), Y = this.sy(t.y[k]);
      if (k === 0) ctx.moveTo(X, Y);
      else ctx.lineTo(X, Y);
    }
    if (player.untilRespawn === 0) ctx.lineTo(this.sx(ship.x), this.sy(ship.y));
    ctx.strokeStyle = col;
    ctx.lineWidth = 1.5;
    ctx.lineJoin = "round";
    ctx.stroke();

    // Knots: small X marks along the tail.
    const r = (KNOTSIZE / 2) * this.scale;
    ctx.lineWidth = 2;
    for (const kt of t.knots) {
      const idx = t.t.indexOf(kt);
      if (idx < 0) continue;
      const X = this.sx(t.x[idx]), Y = this.sy(t.y[idx]);
      ctx.beginPath();
      ctx.moveTo(X - r, Y - r);
      ctx.lineTo(X + r, Y + r);
      ctx.moveTo(X - r, Y + r);
      ctx.lineTo(X + r, Y - r);
      ctx.stroke();
    }
  }

  drawEffects(tick) {
    const ctx = this.ctx;
    for (const e of this.effects) {
      if (!e.loop) continue;
      const f = (tick - e.startTick) / EFFECT_TICKS;
      ctx.beginPath();
      for (let k = 0; k < e.loop.xs.length; k++) {
        const X = this.sx(e.loop.xs[k]), Y = this.sy(e.loop.ys[k]);
        if (k === 0) ctx.moveTo(X, Y);
        else ctx.lineTo(X, Y);
      }
      ctx.closePath();
      if (e.kind === "score") {
        // The loop thickens as it fades out (ScoringEffect.java).
        ctx.globalAlpha = 1 - f;
        ctx.fillStyle = PLAYERS[e.player].glow;
        ctx.fill();
        ctx.lineWidth = 1 + 10 * f;
        ctx.strokeStyle = PLAYERS[e.player].color;
        ctx.stroke();
      } else {
        // A loop that didn't score: brief faint flash.
        ctx.globalAlpha = Math.max(0, 0.6 - 2 * f);
        ctx.lineWidth = 1;
        ctx.strokeStyle = PLAYERS[e.player].color;
        ctx.setLineDash([4, 4]);
        ctx.stroke();
        ctx.setLineDash([]);
      }
      ctx.globalAlpha = 1;
    }
  }

  drawBullets(prev, cur, alpha) {
    const ctx = this.ctx;
    for (const b of cur.bullets) {
      const X = this.sx(b.x), Y = this.sy(b.y);
      const ex = this.sx(b.x - BULLET_STREAK * Math.cos(b.dir)), ey = this.sy(b.y - BULLET_STREAK * Math.sin(b.dir));
      const nx = -Math.sin(b.dir) * BULLETSIZE * this.scale, ny = -Math.cos(b.dir) * BULLETSIZE * this.scale;
      const g = ctx.createLinearGradient(X, Y, ex, ey);
      g.addColorStop(0, "rgba(61,123,255,0.9)");
      g.addColorStop(1, "rgba(0,0,0,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.moveTo(X, Y);
      ctx.lineTo(ex + nx, ey + ny);
      ctx.lineTo(ex - nx, ey - ny);
      ctx.closePath();
      ctx.fill();
      const s = Math.max(2, BULLETSIZE * this.scale);
      ctx.fillStyle = GREEN;
      ctx.fillRect(X - s / 2, Y - s / 2, s, s);
    }
  }

  drawShip(i, ship, tick) {
    const ctx = this.ctx;
    const pwned = this.effects.some((e) => e.kind === "pwned" && e.player === i);
    const pts = shipShape(ship);
    ctx.beginPath();
    pts.forEach((p, k) => (k === 0 ? ctx.moveTo(this.sx(p.x), this.sy(p.y)) : ctx.lineTo(this.sx(p.x), this.sy(p.y))));
    ctx.closePath();
    ctx.fillStyle = pwned ? GREEN : PLAYERS[i].dark;
    ctx.fill();
    ctx.strokeStyle = pwned ? GREEN : PLAYERS[i].color;
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }

  drawHud(arena, cur) {
    const ctx = this.ctx;
    const seq = arena.sequence;
    const cell = 34;
    const items = seq.length + 1; // numbers then "*"
    const x0 = this.w / 2 - (items * cell) / 2;
    const y = 30;
    ctx.font = "bold 22px ui-monospace, Menlo, monospace";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    for (let k = 0; k < items; k++) {
      const done = cur.players.some((p) => p.score > k);
      ctx.fillStyle = done ? "rgba(255,255,255,0.45)" : "#fff";
      ctx.fillText(k < seq.length ? String(seq[k]) : "★", x0 + cell * (k + 0.5), y);
    }
    // Each player's position in the sequence, underlined in their color.
    const stack = new Array(items).fill(0);
    cur.players.forEach((p, i) => {
      const k = Math.min(p.score, seq.length);
      const off = stack[k]++ * 5;
      ctx.strokeStyle = PLAYERS[i].color;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(x0 + cell * k + 5, y + 16 + off);
      ctx.lineTo(x0 + cell * (k + 1) - 5, y + 16 + off);
      ctx.stroke();
    });

    // Per-player "next target" on the sides.
    ctx.font = "15px ui-monospace, Menlo, monospace";
    cur.players.forEach((p, i) => {
      const next = p.score < seq.length ? `next: ${seq[p.score]} cell${seq[p.score] > 1 ? "s" : ""}` : "done!";
      ctx.fillStyle = PLAYERS[i].color;
      ctx.textAlign = i === 0 ? "left" : "right";
      const x = i === 0 ? 16 : this.w - 16;
      ctx.font = "bold 16px ui-monospace, Menlo, monospace";
      ctx.fillText(`${PLAYERS[i].name}  ${p.score}/${seq.length}`, x, 20);
      ctx.font = "14px ui-monospace, Menlo, monospace";
      ctx.fillText(next, x, 42);
    });
    ctx.textAlign = "center";
  }

  drawMessages() {
    const ctx = this.ctx;
    ctx.font = "16px ui-monospace, Menlo, monospace";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    this.messages.forEach((m, k) => {
      const y = this.oy + this.size / 2 - 20 - (this.messages.length - 1 - k) * 24;
      ctx.fillStyle = "rgba(0,0,0,0.6)";
      const w = ctx.measureText(m.text).width + 20;
      ctx.fillRect(this.w / 2 - w / 2, y - 11, w, 22);
      ctx.fillStyle = m.color;
      ctx.fillText(m.text, this.w / 2, y);
    });
  }
}
