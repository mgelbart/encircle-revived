// Connecting two browsers with a room code, using PeerJS (WebRTC).
//
// PeerJS's free public server only introduces the two browsers; after that
// they talk directly (or through PeerJS's relay if a firewall blocks that).
// The host registers under a name built from the room code; the guest
// connects to that name.

const PEERJS_URL = "https://cdn.jsdelivr.net/npm/peerjs@1.5.5/dist/peerjs.min.js";
const PEERJS_SRI = "sha384-x0YgkOr/3UOZP2CRDxGW9e0Q+2Qjyr3uJrm4xU32Y7ZCNAo7Cc7bjhrZMi/dwczu";
const ID_PREFIX = "encircle-revived-room-";
const CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I/L
export const CODE_LENGTH = 4;

let loading = null;
function loadPeerJs() {
  if (globalThis.Peer) return Promise.resolve();
  loading ??= new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = PEERJS_URL;
    s.integrity = PEERJS_SRI;
    s.crossOrigin = "anonymous";
    s.onload = () => resolve();
    s.onerror = () => {
      loading = null;
      reject(new Error("Couldn't load the networking library. Check your internet connection."));
    };
    document.head.appendChild(s);
  });
  return loading;
}

export function normalizeCode(text) {
  return text.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, CODE_LENGTH);
}

function randomCode() {
  const a = new Uint32Array(CODE_LENGTH);
  crypto.getRandomValues(a);
  return Array.from(a, (n) => CODE_CHARS[n % CODE_CHARS.length]).join("");
}

function friendly(err) {
  switch (err && err.type) {
    case "peer-unavailable":
      return "No room with that code. Check the code, and that the other player is still waiting.";
    case "network":
    case "server-error":
    case "socket-error":
    case "socket-closed":
      return "Couldn't reach the matchmaking server. Check your internet connection and try again.";
    case "browser-incompatible":
      return "This browser can't do peer-to-peer connections.";
    default:
      return `Connection problem (${(err && (err.type || err.message)) || "unknown"}).`;
  }
}

// Wraps a PeerJS DataConnection as a link for OnlineGame.
function wrap(conn, peer) {
  const link = {
    onmessage: null,
    onclose: null,
    send: (msg) => conn.send(msg),
    close() {
      try {
        conn.close();
      } catch {}
      setTimeout(() => peer.destroy(), 200);
    },
  };
  conn.on("data", (msg) => link.onmessage && link.onmessage(msg));
  const closed = () => {
    if (link.onclose) {
      const f = link.onclose;
      link.onclose = null;
      f();
    }
  };
  conn.on("close", closed);
  conn.on("error", closed);
  peer.on("error", (e) => {
    if (e.type !== "network" && e.type !== "server-error") closed(); // the broker going away doesn't matter now
  });
  return link;
}

const CONN_OPTIONS = { reliable: true, serialization: "json" };

// Creates a room. onCode(code) is called once it's registered; resolves with
// a link when someone joins. cancel() via the returned handle.
export function hostRoom(onCode) {
  let peer = null, cancelled = false;
  const promise = (async () => {
    await loadPeerJs();
    for (let attempt = 0; attempt < 5; attempt++) {
      if (cancelled) throw new Error("cancelled");
      const code = randomCode();
      peer = new globalThis.Peer(ID_PREFIX + code, { debug: 1 });
      const result = await new Promise((resolve) => {
        peer.on("open", () => resolve("open"));
        peer.on("error", (e) => resolve(e));
      });
      if (result === "open") {
        onCode(code);
        return await new Promise((resolve, reject) => {
          let taken = false;
          peer.on("connection", (conn) => {
            if (taken || conn.metadata?.app !== "encircle") return conn.close(); // room's full
            taken = true;
            conn.on("open", () => resolve(wrap(conn, peer)));
          });
          peer.on("error", (e) => reject(new Error(friendly(e))));
          peer.on("disconnected", () => {
            // Lost the matchmaking server while waiting: try to reconnect.
            if (!cancelled && !peer.destroyed) peer.reconnect();
          });
        });
      }
      peer.destroy();
      if (result.type !== "unavailable-id") throw new Error(friendly(result));
    }
    throw new Error("Couldn't get a room code. Try again.");
  })();
  return {
    promise,
    cancel() {
      cancelled = true;
      if (peer) peer.destroy();
    },
  };
}

// Joins a room by code. Resolves with a link.
export function joinRoom(code) {
  let peer = null;
  const promise = (async () => {
    await loadPeerJs();
    peer = new globalThis.Peer({ debug: 1 });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Timed out connecting. One of you may be behind a strict firewall.")), 20000);
      peer.on("error", (e) => {
        clearTimeout(timer);
        reject(new Error(friendly(e)));
      });
      peer.on("open", () => {
        const conn = peer.connect(ID_PREFIX + code, { ...CONN_OPTIONS, metadata: { app: "encircle" } });
        conn.on("open", () => {
          clearTimeout(timer);
          resolve(wrap(conn, peer));
        });
      });
    });
  })();
  return {
    promise,
    cancel() {
      if (peer) peer.destroy();
    },
  };
}
