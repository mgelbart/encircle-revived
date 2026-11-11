// Sound effects via Web Audio, using the 2011 game's sound files.
// Browsers only allow audio after a user gesture, so unlock() is called on keydown.

const FILES = { shot: "shot.wav", score: "score.wav", win: "win.wav", hit: "hit.wav" };

export class Sounds {
  constructor() {
    this.ctx = null;
    this.buffers = {};
    this.raw = {};
    this.muted = false;
    try {
      this.muted = localStorage.getItem("encircle.muted") === "1";
    } catch {}
  }

  async load() {
    await Promise.all(
      Object.entries(FILES).map(async ([k, f]) => {
        try {
          this.raw[k] = await (await fetch(`assets/sound/${f}`)).arrayBuffer();
        } catch {}
      }),
    );
  }

  unlock() {
    if (this.ctx) {
      if (this.ctx.state === "suspended") this.ctx.resume();
      return;
    }
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    this.ctx = new AC();
    this.gain = this.ctx.createGain();
    this.gain.gain.value = 0.5;
    this.gain.connect(this.ctx.destination);
    for (const [k, data] of Object.entries(this.raw)) {
      this.ctx.decodeAudioData(data.slice(0)).then((b) => (this.buffers[k] = b), () => {});
    }
  }

  play(name, volume = 1) {
    if (this.muted || !this.ctx || !this.buffers[name]) return;
    const src = this.ctx.createBufferSource();
    src.buffer = this.buffers[name];
    const g = this.ctx.createGain();
    g.gain.value = volume;
    src.connect(g).connect(this.gain);
    src.start();
  }

  toggleMute() {
    this.muted = !this.muted;
    try {
      localStorage.setItem("encircle.muted", this.muted ? "1" : "0");
    } catch {}
  }

  onEvents(events) {
    for (const e of events) {
      if (e.type === "shot") this.play("shot", 0.6);
      else if (e.type === "score") this.play("score");
      else if (e.type === "hit") this.play("hit", 0.7);
      else if (e.type === "win") this.play("win");
    }
  }
}
