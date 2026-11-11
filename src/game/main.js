// Local two-player game: both players share one keyboard.
// Runs the simulation at a fixed 25 Hz (40 ms ticks, as in 2011) and
// renders at the display's frame rate, interpolating in between.

import { TIMESTEP, NUMPLAYERS } from "../sim/constants.js";
import { generateArena } from "../sim/arena.js";
import { initialState, step } from "../sim/sim.js";
import { Renderer, PLAYERS } from "./render.js";
import { Sounds } from "./audio.js";

const KEYMAP = [
  { thrust: ["KeyW"], left: ["KeyA"], right: ["KeyD"], shoot: ["KeyS", "Space"] },
  { thrust: ["ArrowUp"], left: ["ArrowLeft"], right: ["ArrowRight"], shoot: ["ArrowDown", "Enter", "NumpadEnter"] },
];
const GAME_KEYS = new Set(KEYMAP.flatMap((m) => Object.values(m).flat()));
const COUNTDOWN_MS = 2000;

const canvas = document.getElementById("game");
const overlay = document.getElementById("overlay");
const held = new Set();

let database = null;
let renderer = null;
const sounds = new Sounds();

// Game state machine: "loading" | "title" | "countdown" | "playing" | "paused" | "over"
const game = { mode: "loading", arena: null, prev: null, cur: null, acc: 0, countdownEnd: 0, seed: 0 };

function inputsFor() {
  return KEYMAP.map((m) => {
    const any = (codes) => codes.some((c) => held.has(c));
    return { thrust: any(m.thrust), left: any(m.left), right: any(m.right), shoot: any(m.shoot) };
  });
}

function newMatch(seed) {
  game.seed = seed >>> 0;
  game.arena = generateArena(database, game.seed);
  game.cur = initialState(game.arena);
  game.prev = game.cur;
  game.acc = 0;
  renderer.clearEffects();
  const url = new URL(location.href);
  url.searchParams.set("seed", String(game.seed));
  history.replaceState(null, "", url);
}

function startCountdown() {
  game.mode = "countdown";
  game.countdownEnd = performance.now() + COUNTDOWN_MS;
  showOverlay(null);
}

function showOverlay(html) {
  if (html) {
    overlay.innerHTML = html;
    overlay.hidden = false;
  } else {
    overlay.hidden = true;
  }
}

function controlsHtml() {
  return `
    <div class="controls">
      <div class="p" style="--c:${PLAYERS[0].color}">
        <h3>${PLAYERS[0].name}</h3>
        <p><kbd>W</kbd> thrust</p>
        <p><kbd>A</kbd> <kbd>D</kbd> turn</p>
        <p><kbd>S</kbd> shoot</p>
      </div>
      <div class="p" style="--c:${PLAYERS[1].color}">
        <h3>${PLAYERS[1].name}</h3>
        <p><kbd>↑</kbd> thrust</p>
        <p><kbd>←</kbd> <kbd>→</kbd> turn</p>
        <p><kbd>↓</kbd> shoot</p>
      </div>
    </div>`;
}

function titleScreen() {
  game.mode = "title";
  showOverlay(`
    <h1>ENCIRCLE</h1>
    <p class="sub">2011 &rarr; 2026</p>
    <div class="rules">
      <p>Your ship leaves a tail behind it. Close a loop with your tail around a
      cluster of cells to collect it.</p>
      <p>Collect clusters in the order shown at the top (the numbers are how many cells to encircle).
      First to finish the sequence wins.</p>
      <p>Encircle your opponent's ship to reset their score to zero. Crossing their tail costs you
      part of yours. Shooting costs tail too; getting shot rewinds you to your last knot (✕).</p>
    </div>
    ${controlsHtml()}
    <p class="go">Press <kbd>Space</kbd> or <kbd>Enter</kbd> to start</p>
    <p class="small"><kbd>Esc</kbd> pause &middot; <kbd>M</kbd> mute &middot; <kbd>N</kbd> new map</p>
  `);
}

function gameOverScreen(winner) {
  game.mode = "over";
  game.overAt = performance.now();
  const p = PLAYERS[winner];
  showOverlay(`
    <h1 style="color:${p.color}">${p.name} wins!</h1>
    <p class="go">Press <kbd>Space</kbd> or <kbd>Enter</kbd> for a rematch on a new map</p>
    <p class="small"><kbd>R</kbd> replay this map</p>
  `);
}

function pauseScreen() {
  game.mode = "paused";
  showOverlay(`<h1>Paused</h1>${controlsHtml()}<p class="go">Press <kbd>Esc</kbd> to resume</p>
    <p class="small"><kbd>N</kbd> new map &middot; <kbd>M</kbd> mute</p>`);
}

function randomSeed() {
  return (Math.random() * 2 ** 32) >>> 0;
}

// ---------------------------------------------------------------- input --

window.addEventListener("keydown", (e) => {
  if (GAME_KEYS.has(e.code) || e.code === "Escape") e.preventDefault();
  held.add(e.code);
  if (e.repeat) return;
  sounds.unlock();

  // Space/Enter double as shoot keys, so ignore them briefly after a match ends.
  const start =
    (e.code === "Space" || e.code === "Enter" || e.code === "NumpadEnter") &&
    !(game.mode === "over" && performance.now() - game.overAt < 1200);
  if (game.mode === "title" && start) {
    startCountdown();
  } else if (game.mode === "over" && start) {
    newMatch(randomSeed());
    startCountdown();
  } else if (game.mode === "over" && e.code === "KeyR") {
    newMatch(game.seed);
    startCountdown();
  } else if (e.code === "Escape" && game.mode === "playing") {
    pauseScreen();
  } else if (e.code === "Escape" && game.mode === "paused") {
    game.mode = "playing";
    game.acc = 0;
    showOverlay(null);
  } else if (e.code === "KeyN" && (game.mode === "paused" || game.mode === "title")) {
    newMatch(randomSeed());
    if (game.mode === "paused") startCountdown();
  } else if (e.code === "KeyM") {
    sounds.toggleMute();
    renderer.say(sounds.muted ? "Sound off" : "Sound on", "#ccc", game.cur ? game.cur.time : 0, 40);
  }
});
window.addEventListener("keyup", (e) => held.delete(e.code));
window.addEventListener("blur", () => {
  held.clear();
  if (game.mode === "playing") pauseScreen();
});
window.addEventListener("resize", () => renderer && renderer.resize());

// ----------------------------------------------------------------- loop --

let last = performance.now();
function frame(now) {
  const dt = Math.min(250, now - last);
  last = now;

  if (game.mode === "countdown" && now >= game.countdownEnd) {
    game.mode = "playing";
    game.acc = 0;
  }

  if (game.mode === "playing") {
    game.acc += dt;
    while (game.acc >= TIMESTEP) {
      game.acc -= TIMESTEP;
      const { state, events } = step(game.arena, game.cur, inputsFor());
      game.prev = game.cur;
      game.cur = state;
      renderer.onEvents(events, state.time);
      sounds.onEvents(events);
      if (state.winner >= 0) {
        gameOverScreen(state.winner);
        break;
      }
    }
  }

  const alpha = game.mode === "playing" ? game.acc / TIMESTEP : 1;
  renderer.draw(game.arena, game.prev, game.cur, alpha);
  if (game.mode === "countdown") drawCountdown(now);
  requestAnimationFrame(frame);
}

function drawCountdown(now) {
  const ctx = renderer.ctx;
  const left = Math.max(0, game.countdownEnd - now);
  ctx.font = "bold 72px ui-monospace, Menlo, monospace";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle = "rgba(255,255,255,0.9)";
  ctx.fillText(left > COUNTDOWN_MS / 2 ? "Ready" : "Go!", renderer.w / 2, renderer.oy);
  ctx.font = "18px ui-monospace, Menlo, monospace";
  ctx.fillText(`First target: ${game.arena.sequence[0]} cell${game.arena.sequence[0] > 1 ? "s" : ""}`, renderer.w / 2, renderer.oy + 60);
}

// ---------------------------------------------------------------- setup --

async function loadImage(src) {
  const img = new Image();
  img.src = src;
  try {
    await img.decode();
  } catch {
    console.warn("failed to load", src);
  }
  return img;
}

async function main() {
  showOverlay(`<h1>ENCIRCLE</h1><p class="sub">loading…</p>`);
  database = await (await fetch("assets/clusters.json")).json();
  const clusterImages = new Map();
  const [bg] = await Promise.all([
    loadImage("assets/bg.jpg"),
    ...database.map(async (e) => clusterImages.set(e.name, await loadImage(`assets/clusters/${e.name}.png`))),
    sounds.load(),
  ]);
  renderer = new Renderer(canvas, { bg, clusterImages });
  const seedParam = new URL(location.href).searchParams.get("seed");
  newMatch(seedParam ? Number(seedParam) : randomSeed());
  titleScreen();
  // Handy for debugging from the browser console.
  window.encircle = { game, step, inputsFor, NUMPLAYERS };
  requestAnimationFrame(frame);
}

main().catch((err) => {
  console.error(err);
  showOverlay(`<h1>Oops</h1><p>${String(err)}</p><p class="small">Is this being served over http? See README.</p>`);
});
