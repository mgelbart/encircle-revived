// The browser game: title screen, mode selection, input, and the main loop.
// Runs the simulation at a fixed 25 Hz (40 ms ticks, as in 2011) and
// renders at the display's frame rate, interpolating in between.
//
// Modes:
//   local2   two players sharing one keyboard (keyboard only)
//   aiMouse  you vs the computer, steering with the mouse (as on the 2011 site)
//   aiKeys   you vs the computer, steering with the keyboard

import { TIMESTEP, ARENA_HALF } from "../sim/constants.js";
import { generateArena } from "../sim/arena.js";
import { initialState, step } from "../sim/sim.js";
import { makeAi, aiThink } from "../sim/ai.js";
import { Renderer, PLAYERS } from "./render.js";
import { Sounds } from "./audio.js";

const KEYS_RED = { thrust: ["KeyW"], left: ["KeyA"], right: ["KeyD"], shoot: ["KeyS", "Space"] };
const KEYS_BLUE = { thrust: ["ArrowUp"], left: ["ArrowLeft"], right: ["ArrowRight"], shoot: ["ArrowDown", "Enter", "NumpadEnter"] };
const KEYS_SOLO = {
  thrust: ["KeyW", "ArrowUp"],
  left: ["KeyA", "ArrowLeft"],
  right: ["KeyD", "ArrowRight"],
  shoot: ["Space", "KeyS", "ArrowDown"],
};
// Mouse mode: steer toward the cursor; click or Space shoots; hold A to cut the engine.
const MOUSE_KEYS = { shoot: ["Space"], stop: ["KeyA"] };

const MODES = {
  local2: {
    title: "Two players",
    sub: "one keyboard",
    pilots: [{ kind: "keys", map: KEYS_RED }, { kind: "keys", map: KEYS_BLUE }],
    names: ["Red", "Blue"],
  },
  aiMouse: {
    title: "Vs computer",
    sub: "mouse",
    pilots: [{ kind: "mouse" }, { kind: "ai" }],
    names: ["You", "Computer"],
  },
  aiKeys: {
    title: "Vs computer",
    sub: "keyboard",
    pilots: [{ kind: "keys", map: KEYS_SOLO }, { kind: "ai" }],
    names: ["You", "Computer"],
  },
};
const MODE_ORDER = ["local2", "aiMouse", "aiKeys"];

const GAME_KEYS = new Set(
  [KEYS_RED, KEYS_BLUE, KEYS_SOLO, MOUSE_KEYS].flatMap((m) => Object.values(m).flat()),
);
const COUNTDOWN_MS = 2000;

const canvas = document.getElementById("game");
const overlay = document.getElementById("overlay");
const held = new Set();
const mouse = { x: null, y: null, down: false }; // screen (CSS px) coords

let database = null;
let renderer = null;
const sounds = new Sounds();

// Screen state: "loading" | "title" | "countdown" | "playing" | "paused" | "over"
const game = { screen: "loading", modeId: loadMode(), arena: null, prev: null, cur: null, acc: 0, countdownEnd: 0, seed: 0, ais: [] };
const mode = () => MODES[game.modeId];

function loadMode() {
  try {
    const m = localStorage.getItem("encircle.mode");
    if (m && MODES[m]) return m;
  } catch {}
  return "local2";
}
function saveMode() {
  try {
    localStorage.setItem("encircle.mode", game.modeId);
  } catch {}
}

// ---------------------------------------------------------------- input --

const anyHeld = (codes) => codes.some((c) => held.has(c));

function mouseTarget() {
  if (mouse.x === null) return null;
  const p = renderer.toArena(mouse.x, mouse.y);
  const clamp = (v) => Math.round(Math.max(-ARENA_HALF * 1.5, Math.min(ARENA_HALF * 1.5, v)));
  return { x: clamp(p.x), y: clamp(p.y) };
}

function inputsFor(state) {
  return mode().pilots.map((pilot, i) => {
    if (pilot.kind === "keys") {
      const m = pilot.map;
      return { thrust: anyHeld(m.thrust), left: anyHeld(m.left), right: anyHeld(m.right), shoot: anyHeld(m.shoot) };
    }
    if (pilot.kind === "mouse") {
      return { target: mouseTarget(), stop: anyHeld(MOUSE_KEYS.stop), shoot: mouse.down || anyHeld(MOUSE_KEYS.shoot) };
    }
    return aiThink(game.ais[i], game.arena, state);
  });
}

// ---------------------------------------------------------------- flow --

function newMatch(seed) {
  game.seed = seed >>> 0;
  game.arena = generateArena(database, game.seed);
  game.cur = initialState(game.arena);
  game.prev = game.cur;
  game.acc = 0;
  game.ais = mode().pilots.map((p, i) => (p.kind === "ai" ? makeAi(i, game.seed ^ 0x9e3779b9) : null));
  renderer.clearEffects();
  renderer.names = mode().names;
  canvas.classList.toggle("mouse", mode().pilots.some((p) => p.kind === "mouse"));
  const url = new URL(location.href);
  url.searchParams.set("seed", String(game.seed));
  history.replaceState(null, "", url);
}

function setMode(id) {
  if (!MODES[id] || id === game.modeId) return;
  game.modeId = id;
  saveMode();
  newMatch(game.seed); // same map, new pilots
  if (game.screen === "title") titleScreen();
}

function startCountdown() {
  game.screen = "countdown";
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

function controlsHtml(id = game.modeId) {
  const card = (i, name, lines) =>
    `<div class="p" style="--c:${PLAYERS[i].color}"><h3>${name}</h3>${lines.map((l) => `<p>${l}</p>`).join("")}</div>`;
  if (id === "local2")
    return `<div class="controls">
      ${card(0, "Red", ["<kbd>W</kbd> thrust", "<kbd>A</kbd> <kbd>D</kbd> turn", "<kbd>S</kbd> shoot"])}
      ${card(1, "Blue", ["<kbd>↑</kbd> thrust", "<kbd>←</kbd> <kbd>→</kbd> turn", "<kbd>↓</kbd> shoot"])}
    </div>`;
  if (id === "aiMouse")
    return `<div class="controls">
      ${card(0, "You (Red)", [
        "your ship flies toward the mouse:",
        "farther ahead = faster",
        "<kbd>click</kbd> or <kbd>Space</kbd> shoot",
        "hold <kbd>A</kbd> to cut the engine",
      ])}
    </div>`;
  return `<div class="controls">
    ${card(0, "You (Red)", ["<kbd>W</kbd>/<kbd>↑</kbd> thrust", "<kbd>A</kbd> <kbd>D</kbd> / <kbd>←</kbd> <kbd>→</kbd> turn", "<kbd>Space</kbd> shoot"])}
  </div>`;
}

function modePickerHtml() {
  return `<div class="modes">${MODE_ORDER.map(
    (id, k) =>
      `<button class="mode${id === game.modeId ? " on" : ""}" data-mode="${id}">
        <span class="num">${k + 1}</span><b>${MODES[id].title}</b><span>${MODES[id].sub}</span></button>`,
  ).join("")}</div>`;
}

function titleScreen() {
  game.screen = "title";
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
    ${modePickerHtml()}
    ${controlsHtml()}
    <p class="go">Press <kbd>Space</kbd> or <kbd>Enter</kbd> to start</p>
    <p class="small"><kbd>1</kbd> <kbd>2</kbd> <kbd>3</kbd> pick mode &middot; <kbd>Esc</kbd> pause &middot; <kbd>M</kbd> mute &middot; <kbd>N</kbd> new map</p>
  `);
}

function gameOverScreen(winner) {
  game.screen = "over";
  game.overAt = performance.now();
  const p = PLAYERS[winner];
  const name = mode().names[winner];
  showOverlay(`
    <h1 style="color:${p.color}">${name === "You" ? "You win!" : `${name} wins!`}</h1>
    <p class="go">Press <kbd>Space</kbd> or <kbd>Enter</kbd> for a rematch on a new map</p>
    <p class="small"><kbd>R</kbd> replay this map &middot; <kbd>Esc</kbd> back to the title screen</p>
  `);
}

function pauseScreen() {
  game.screen = "paused";
  showOverlay(`<h1>Paused</h1>${controlsHtml()}<p class="go">Press <kbd>Esc</kbd> to resume</p>
    <p class="small"><kbd>Q</kbd> quit to title &middot; <kbd>N</kbd> new map &middot; <kbd>M</kbd> mute</p>`);
}

function randomSeed() {
  return (Math.random() * 2 ** 32) >>> 0;
}

// --------------------------------------------------------------- events --

overlay.addEventListener("click", (e) => {
  const b = e.target.closest("[data-mode]");
  if (b) setMode(b.dataset.mode);
});

window.addEventListener("keydown", (e) => {
  if (GAME_KEYS.has(e.code) || e.code === "Escape") e.preventDefault();
  held.add(e.code);
  if (e.repeat) return;
  sounds.unlock();

  // Space/Enter double as shoot keys, so ignore them briefly after a match ends.
  const start =
    (e.code === "Space" || e.code === "Enter" || e.code === "NumpadEnter") &&
    !(game.screen === "over" && performance.now() - game.overAt < 1200);
  const digit = { Digit1: 0, Digit2: 1, Digit3: 2, Numpad1: 0, Numpad2: 1, Numpad3: 2 }[e.code];

  if (game.screen === "title" && digit !== undefined) {
    setMode(MODE_ORDER[digit]);
  } else if (game.screen === "title" && start) {
    startCountdown();
  } else if (game.screen === "over" && start) {
    newMatch(randomSeed());
    startCountdown();
  } else if (game.screen === "over" && e.code === "KeyR") {
    newMatch(game.seed);
    startCountdown();
  } else if (game.screen === "over" && e.code === "Escape") {
    newMatch(randomSeed());
    titleScreen();
  } else if (e.code === "Escape" && game.screen === "playing") {
    pauseScreen();
  } else if (e.code === "Escape" && game.screen === "paused") {
    game.screen = "playing";
    game.acc = 0;
    showOverlay(null);
  } else if (e.code === "KeyQ" && game.screen === "paused") {
    newMatch(game.seed);
    titleScreen();
  } else if (e.code === "KeyN" && (game.screen === "paused" || game.screen === "title")) {
    newMatch(randomSeed());
    if (game.screen === "paused") startCountdown();
  } else if (e.code === "KeyM") {
    sounds.toggleMute();
    renderer.say(sounds.muted ? "Sound off" : "Sound on", "#ccc", game.cur ? game.cur.time : 0, 40);
  }
});
window.addEventListener("keyup", (e) => held.delete(e.code));
window.addEventListener("blur", () => {
  held.clear();
  mouse.down = false;
  if (game.screen === "playing") pauseScreen();
});
window.addEventListener("resize", () => renderer && renderer.resize());

window.addEventListener("pointermove", (e) => {
  mouse.x = e.clientX;
  mouse.y = e.clientY;
});
canvas.addEventListener("pointerdown", (e) => {
  sounds.unlock();
  mouse.x = e.clientX;
  mouse.y = e.clientY;
  mouse.down = true;
});
window.addEventListener("pointerup", () => (mouse.down = false));
canvas.addEventListener("contextmenu", (e) => e.preventDefault());

// ----------------------------------------------------------------- loop --

let last = performance.now();
function frame(now) {
  const dt = Math.min(250, now - last);
  last = now;

  if (game.screen === "countdown" && now >= game.countdownEnd) {
    game.screen = "playing";
    game.acc = 0;
  }

  if (game.screen === "playing") {
    game.acc += dt;
    while (game.acc >= TIMESTEP) {
      game.acc -= TIMESTEP;
      const { state, events } = step(game.arena, game.cur, inputsFor(game.cur));
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

  const alpha = game.screen === "playing" ? game.acc / TIMESTEP : 1;
  renderer.draw(game.arena, game.prev, game.cur, alpha);
  if (game.screen === "countdown") drawCountdown(now);
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
  const n = game.arena.sequence[0];
  ctx.fillText(`First target: ${n} cell${n > 1 ? "s" : ""}`, renderer.w / 2, renderer.oy + 60);
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
  const params = new URL(location.href).searchParams;
  if (MODES[params.get("mode")]) game.modeId = params.get("mode");
  const seedParam = params.get("seed");
  newMatch(seedParam ? Number(seedParam) : randomSeed());
  titleScreen();
  // Handy for debugging from the browser console.
  window.encircle = { game, step, inputsFor, MODES };
  requestAnimationFrame(frame);
}

main().catch((err) => {
  console.error(err);
  showOverlay(`<h1>Oops</h1><p>${String(err)}</p><p class="small">Is this being served over http? See README.</p>`);
});
