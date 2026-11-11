// The browser game: title screen, mode selection, input, and the main loop.
// Runs the simulation at a fixed 25 Hz (40 ms ticks, as in 2011) and
// renders at the display's frame rate, interpolating in between.
//
// Modes:
//   local2   two players sharing one keyboard (keyboard only)
//   aiMouse  you vs the computer, steering with the mouse (as on the 2011 site)
//   aiKeys   you vs the computer, steering with the keyboard
//   online   against someone on another computer, joined by room code
//            (mouse or keyboard, each player's own choice)

import { TIMESTEP, ARENA_HALF } from "../sim/constants.js";
import { generateArena } from "../sim/arena.js";
import { initialState, step } from "../sim/sim.js";
import { makeAi, aiThink } from "../sim/ai.js";
import { Renderer, PLAYERS } from "./render.js";
import { Sounds } from "./audio.js";
import { OnlineGame } from "../net/online.js";
import { hostRoom, joinRoom, normalizeCode, CODE_LENGTH } from "../net/peer.js";

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
  online: {
    title: "Online",
    sub: "room code",
    pilots: [{ kind: "remote" }, { kind: "remote" }],
    names: ["Red", "Blue"],
  },
};
const MODE_ORDER = ["local2", "aiMouse", "aiKeys", "online"];

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
//   online only: "lobby" | "hosting" | "joining" | "net" (a match: see game.online.phase) | "lost"
const game = { screen: "loading", modeId: loadMode(), arena: null, prev: null, cur: null, acc: 0, countdownEnd: 0, seed: 0, ais: [] };
// Online: which controls this player uses, the connection attempt in progress, the match.
const net = { controls: loadSetting("encircle.netControls", ["mouse", "keys"]), attempt: null, online: null, menu: false, error: "", code: "" };
const mode = () => MODES[game.modeId];

function loadMode() {
  try {
    const m = localStorage.getItem("encircle.mode");
    if (m && MODES[m]) return m;
  } catch {}
  return "local2";
}
function saveMode() {
  saveSetting("encircle.mode", game.modeId);
}
function loadSetting(key, allowed) {
  try {
    const v = localStorage.getItem(key);
    if (allowed.includes(v)) return v;
  } catch {}
  return allowed[0];
}
function saveSetting(key, value) {
  try {
    localStorage.setItem(key, value);
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

function keysInput(m) {
  return { thrust: anyHeld(m.thrust), left: anyHeld(m.left), right: anyHeld(m.right), shoot: anyHeld(m.shoot) };
}
function mouseInput() {
  return { target: mouseTarget(), stop: anyHeld(MOUSE_KEYS.stop), shoot: mouse.down || anyHeld(MOUSE_KEYS.shoot) };
}

function inputsFor(state) {
  return mode().pilots.map((pilot, i) => {
    if (pilot.kind === "keys") return keysInput(pilot.map);
    if (pilot.kind === "mouse") return mouseInput();
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
  if (id === "online")
    return `<div class="controls">
      ${card(1, "Online", ["play someone on another computer:", "one of you creates a room,", "the other joins with its code", "mouse or keyboard, your choice"])}
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
    <p class="small"><kbd>1</kbd> <kbd>2</kbd> <kbd>3</kbd> <kbd>4</kbd> pick mode &middot; <kbd>Esc</kbd> pause &middot; <kbd>M</kbd> mute &middot; <kbd>N</kbd> new map</p>
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

// --------------------------------------------------------------- online --

const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function controlsPickerHtml() {
  const b = (id, title, sub) =>
    `<button class="mode${net.controls === id ? " on" : ""}" data-ctl="${id}"><b>${title}</b><span>${sub}</span></button>`;
  return `<p class="label">Your controls</p><div class="modes tight">${b("mouse", "Mouse", "steer toward the cursor")}${b("keys", "Keyboard", "arrows or WASD")}</div>`;
}

function setNetControls(id) {
  net.controls = id;
  saveSetting("encircle.netControls", id);
  if (game.screen === "lobby") lobbyScreen();
  if (game.screen === "net") canvas.classList.toggle("mouse", id === "mouse");
  if (game.screen === "net" && net.online.phase === "over") onlineOverScreen();
}

function lobbyScreen() {
  game.screen = "lobby";
  showOverlay(`
    <h1>Online</h1>
    <p class="sub">play someone on another computer</p>
    ${controlsPickerHtml()}
    <div class="modes">
      <button class="mode" data-act="host"><b>Create a room</b><span>and send the code to a friend</span></button>
      <form class="mode join">
        <b>Join a room</b>
        <span><input id="code" maxlength="${CODE_LENGTH}" placeholder="CODE" autocomplete="off" spellcheck="false" value="${esc(net.code)}" /><button type="submit">Join</button></span>
      </form>
    </div>
    ${net.error ? `<p class="error">${esc(net.error)}</p>` : ""}
    <p class="small"><kbd>Esc</kbd> back</p>
  `);
}

function cancelAttempt() {
  net.attempt?.cancel();
  net.attempt = null;
}

function inviteLink(code) {
  const u = new URL(location.href);
  u.search = `?room=${code}`;
  return u.href;
}

async function hostOnline() {
  cancelAttempt();
  net.error = "";
  game.screen = "hosting";
  showOverlay(`<h1>Online</h1><p class="sub">getting a room code…</p><p class="small"><kbd>Esc</kbd> cancel</p>`);
  const attempt = hostRoom((code) => {
    if (game.screen !== "hosting") return;
    showOverlay(`
      <p class="sub">Room code</p>
      <div class="code">${code}</div>
      <p>Send the code to your opponent, or this link:</p>
      <p><a class="invite" href="${inviteLink(code)}" target="_blank" rel="noopener">${esc(inviteLink(code))}</a>
        <button class="copy" data-act="copy" data-text="${esc(inviteLink(code))}">Copy link</button></p>
      <p class="go">Waiting for them to join…</p>
      <p class="small"><kbd>Esc</kbd> cancel</p>
    `);
  });
  net.attempt = attempt;
  try {
    const link = await attempt.promise;
    if (net.attempt === attempt) startOnline(link, true);
    else link.close();
  } catch (err) {
    if (net.attempt === attempt) failOnline(err);
  }
}

async function joinOnline(text) {
  const code = normalizeCode(text);
  net.code = code;
  if (code.length !== CODE_LENGTH) {
    net.error = `Room codes are ${CODE_LENGTH} letters.`;
    lobbyScreen();
    return;
  }
  cancelAttempt();
  net.error = "";
  game.screen = "joining";
  showOverlay(`<h1>Online</h1><p class="sub">joining room ${code}…</p><p class="small"><kbd>Esc</kbd> cancel</p>`);
  const attempt = joinRoom(code);
  net.attempt = attempt;
  try {
    const link = await attempt.promise;
    if (net.attempt === attempt) startOnline(link, false);
    else link.close();
  } catch (err) {
    if (net.attempt === attempt) failOnline(err);
  }
}

function failOnline(err) {
  net.attempt = null;
  net.error = err.message;
  lobbyScreen();
}

function copyInvite(button) {
  navigator.clipboard?.writeText(button.dataset.text).then(
    () => (button.textContent = "Copied"),
    () => (button.textContent = "Copy failed: select the link instead"),
  );
}

function startOnline(link, isHost) {
  net.attempt = null;
  net.code = "";
  net.menu = false;
  const online = new OnlineGame(link, {
    isHost,
    database,
    now: () => performance.now(),
    hooks: {
      onBegin() {
        game.arena = online.arena;
        game.seed = online.seed;
        renderer.clearEffects();
        renderer.names = online.me === 0 ? ["You", "Opponent"] : ["Opponent", "You"];
        canvas.classList.toggle("mouse", net.controls === "mouse");
        net.menu = false;
        showOverlay(null);
      },
      onOver: () => onlineOverScreen(),
      onRematchWanted: () => onlineOverScreen(),
      onLost(reason) {
        game.screen = "lost";
        canvas.classList.remove("mouse");
        showOverlay(`<h1>Disconnected</h1><p>${esc(reason)}</p>
          <p class="go">Press <kbd>Space</kbd> or <kbd>Esc</kbd> to go back</p>`);
      },
    },
  });
  link.onmessage = (msg) => online.receive(msg);
  link.onclose = () => online.closed();
  net.online = online;
  game.screen = "net";
  showOverlay(`<h1>Connected</h1><p class="sub">starting…</p>`);
}

function onlineOverScreen() {
  const o = net.online;
  const winner = o.sess.winner;
  const won = winner === o.me;
  const mine = o.wantRematch[o.me], theirs = o.wantRematch[1 - o.me];
  const status = mine
    ? "Waiting for your opponent…"
    : theirs
      ? "Your opponent wants a rematch. Press <kbd>Space</kbd> or <kbd>Enter</kbd>"
      : "Press <kbd>Space</kbd> or <kbd>Enter</kbd> for a rematch on a new map";
  showOverlay(`
    <h1 style="color:${PLAYERS[winner].color}">${won ? "You win!" : "Your opponent wins"}</h1>
    <p class="go">${status}</p>
    ${controlsPickerHtml()}
    <p class="small"><kbd>Esc</kbd> leave</p>
  `);
}

function leaveOnline() {
  cancelAttempt();
  net.online?.leave();
  net.online = null;
  net.menu = false;
  canvas.classList.remove("mouse");
  newMatch(randomSeed());
  titleScreen();
}

// Keys on the online screens. Returns true if handled.
function onlineKey(e, start) {
  const s = game.screen;
  if (s === "lobby" || s === "hosting" || s === "joining") {
    if (e.code === "Escape") {
      cancelAttempt();
      if (s === "lobby") titleScreen();
      else lobbyScreen();
    }
    return e.code !== "KeyM";
  }
  if (s === "lost") {
    if (start || e.code === "Escape") leaveOnline();
    return e.code !== "KeyM";
  }
  if (s !== "net") return false;
  const o = net.online;
  if (o.phase === "over") {
    if (start) {
      o.requestRematch();
      if (o.phase === "over") onlineOverScreen(); // else the rematch already started
    } else if (e.code === "Escape") leaveOnline();
  } else if (e.code === "Escape") {
    net.menu = !net.menu;
    if (net.menu)
      showOverlay(`<h1>Leave?</h1><p>The match keeps running (online games can't pause).</p>
        <p class="go"><kbd>Q</kbd> leave the match &middot; <kbd>Esc</kbd> keep playing</p>`);
    else showOverlay(null);
  } else if (e.code === "KeyQ" && net.menu) leaveOnline();
  return e.code !== "KeyM";
}

function netInput() {
  return net.controls === "mouse" ? mouseInput() : keysInput(KEYS_SOLO);
}

// Runs the online match forward. Called every frame and from a timer, so it
// keeps going (slowly) even when the tab is in the background.
function pumpOnline() {
  const o = net.online;
  if (game.screen !== "net" || !o) return;
  const events = o.update(netInput);
  if (events.length) {
    renderer.onEvents(events, o.sess.tick);
    sounds.onEvents(events);
  }
}
setInterval(pumpOnline, 30);

function drawOnline(now) {
  pumpOnline();
  const o = net.online;
  const v = o?.view();
  if (!v) return;
  renderer.draw(o.arena, v.prev, v.cur, o.phase === "playing" ? v.alpha : 1);
  if (o.phase === "countdown") drawCountdown(now, o.startAt);
  if (o.stalled) {
    const ctx = renderer.ctx;
    ctx.font = "bold 28px ui-monospace, Menlo, monospace";
    ctx.textAlign = "center";
    ctx.fillStyle = "rgba(255,255,255,0.85)";
    ctx.fillText("Waiting for your opponent…", renderer.w / 2, renderer.oy);
  }
}

// --------------------------------------------------------------- events --

overlay.addEventListener("click", (e) => {
  const b = e.target.closest("[data-mode]");
  if (b) setMode(b.dataset.mode);
  const c = e.target.closest("[data-ctl]");
  if (c) setNetControls(c.dataset.ctl);
  const a = e.target.closest("[data-act]");
  if (a?.dataset.act === "host") hostOnline();
  if (a?.dataset.act === "copy") copyInvite(a);
});
overlay.addEventListener("submit", (e) => {
  e.preventDefault();
  joinOnline(document.getElementById("code").value);
});

window.addEventListener("keydown", (e) => {
  if (e.target.tagName === "INPUT") {
    if (e.code === "Escape") e.target.blur();
    return; // typing a room code
  }
  if (GAME_KEYS.has(e.code) || e.code === "Escape") e.preventDefault();
  held.add(e.code);
  if (e.repeat) return;
  sounds.unlock();

  // Space/Enter double as shoot keys, so ignore them briefly after a match ends.
  const start =
    (e.code === "Space" || e.code === "Enter" || e.code === "NumpadEnter") &&
    !(game.screen === "over" && performance.now() - game.overAt < 1200);
  const digit = { Digit1: 0, Digit2: 1, Digit3: 2, Digit4: 3, Numpad1: 0, Numpad2: 1, Numpad3: 2, Numpad4: 3 }[e.code];

  if (onlineKey(e, start)) {
    // handled
  } else if (game.screen === "title" && digit !== undefined) {
    setMode(MODE_ORDER[digit]);
  } else if (game.screen === "title" && start && game.modeId === "online") {
    lobbyScreen();
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
  if (game.screen === "playing") pauseScreen(); // (online matches can't pause)
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

  if (game.screen === "net") {
    drawOnline(now);
  } else if (game.arena) {
    const alpha = game.screen === "playing" ? game.acc / TIMESTEP : 1;
    renderer.draw(game.arena, game.prev, game.cur, alpha);
    if (game.screen === "countdown") drawCountdown(now, game.countdownEnd);
  }
  requestAnimationFrame(frame);
}

function drawCountdown(now, end) {
  const ctx = renderer.ctx;
  const left = Math.max(0, end - now);
  ctx.font = "bold 72px ui-monospace, Menlo, monospace";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillStyle = "rgba(255,255,255,0.9)";
  ctx.fillText(left > COUNTDOWN_MS / 2 ? "Ready" : "Go!", renderer.w / 2, renderer.oy);
  ctx.font = "18px ui-monospace, Menlo, monospace";
  const n = game.arena.sequence[0];
  if (game.screen === "net") {
    const me = net.online.me;
    ctx.fillStyle = PLAYERS[me].color;
    ctx.fillText(`You are ${me === 0 ? "red" : "blue"}`, renderer.w / 2, renderer.oy + 95);
    ctx.fillStyle = "rgba(255,255,255,0.9)";
  }
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
  const url = new URL(location.href);
  const params = url.searchParams;
  if (MODES[params.get("mode")]) game.modeId = params.get("mode");
  const seedParam = params.get("seed");
  newMatch(seedParam ? Number(seedParam) : randomSeed());
  titleScreen();
  // Handy for debugging from the browser console.
  window.encircle = { game, net, step, inputsFor, MODES };
  const room = normalizeCode(params.get("room") || "");
  if (room.length === CODE_LENGTH) {
    // Opened from an invite link: join straight away.
    params.delete("room");
    history.replaceState(null, "", url.pathname + (params.size ? `?${params}` : ""));
    game.modeId = "online";
    joinOnline(room);
  }
  requestAnimationFrame(frame);
}

main().catch((err) => {
  console.error(err);
  showOverlay(`<h1>Oops</h1><p>${String(err)}</p><p class="small">Is this being served over http? See README.</p>`);
});
