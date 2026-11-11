# Encircle (revived)

A browser remake of **Encircle**, the 2011 Java game by Mike Gelbart and Joonas Govenius.
The original code is in [`encircle-old/`](encircle-old/).

Each player flies a ship that leaves a tail behind it. Close a loop with your tail
around a cluster of cells to collect it. Clusters must be collected in the order
shown at the top of the screen, where each number is how many cells the cluster has. The first
player to finish the sequence wins.

- **Encircle the other ship** and their score resets to zero.
- **Cross the other player's tail** and you lose the oldest part of yours (back to your second knot ✕).
- **Shooting** costs tail length. **Getting shot** rewinds you along your tail to your last knot.
- Each knot ✕ marks a place where you scored. Flying into a wall or a cluster stops you dead.

(The cluster images are fruit-fly embryo cells. In 2011 the game doubled as a crowdsourcing experiment
for image segmentation, recording which cell count players thought each cluster had.)

## Play

There's no build step. Start the local server:

```bash
python3 tools/serve.py
```

Then open <http://localhost:8765>. Any static file server works, but `tools/serve.py` turns off
browser caching so edits show up on reload. Opening `index.html` directly from disk won't work,
because browsers block ES modules on `file://`.

### Modes

Pick a mode on the title screen with `1` `2` `3`, or by clicking it.

1. **Two players, one keyboard.** Keyboard only:

   | | Red | Blue |
   |---|---|---|
   | thrust | `W` | `↑` |
   | turn | `A` `D` | `←` `→` |
   | shoot | `S` (or `Space`) | `↓` (or `Enter`) |

2. **Vs computer, mouse.** This is how the 2011 site played. Your ship turns toward the cursor and
   thrusts harder the farther ahead of it the cursor is; it coasts if the cursor is behind it.
   Click or `Space` shoots; hold `A` to cut the engine.
3. **Vs computer, keyboard.** `W`/`↑` thrust, `A` `D` / `←` `→` turn, `Space` shoots.

The computer opponent is a port of the 2011 AI (`Nonhuman.java`).

`Esc` pause (then `Q` quits to the title) · `M` mute · `N` new map · `R` replay the same map after a match.
The URL carries `?seed=…` (and optionally `&mode=local2|aiMouse|aiKeys`), so a map can be shared or replayed.

## Tests

With the server running, open <http://localhost:8765/tests/>.

## Running the 2011 original

The old game needs Java 8 (it uses APIs removed in Java 11). This repo expects it unpacked at
`.tools/jdk8` (git ignores `.tools/`), e.g. Azul Zulu 8 for macOS from <https://www.azul.com/downloads/>:

```bash
tools/run-old-game.sh            # you vs the 2011 AI, steering with the mouse
tools/run-old-game.sh keyboard   # arrows + space instead
```

This compiles `encircle-old/src` into `.tools/oldbuild`, then starts the game server, matchmaker,
one AI client and your client window. Click in the window to start a match.

`tools/old-traces/` has two small Java programs that run scripted inputs through the original
physics and mouse steering. They were used to check the remake against the original: keyboard
flying matched exactly on every tick, and mouse steering matched to within rounding (Java stored
angles as integers), drifting by at most 0.05% of the arena over 40 seconds.

## Layout

```
index.html, style.css     page shell
src/sim/                  the game rules: pure, deterministic, no DOM
  constants.js            physics constants ported from SimulationUtils.java
  tail.js                 the tail: points, knots, cropping, loop detection (Tail.java)
  arena.js                map generation: cluster placement and the sequence (ClusterDatabase.java)
  sim.js                  step(arena, state, inputs) -> { state, events } (Simulation.java)
  ai.js                   the computer opponent (Nonhuman.java)
src/game/                 browser side: rendering, keyboard/mouse, sound, screens
assets/                   cluster images + outlines, sounds, background (from encircle-old/)
tools/build_assets.py     regenerates assets/ from encircle-old/
tools/serve.py            local dev server (no caching)
tools/run-old-game.sh     runs the original 2011 Java game
tests/                    browser test page for the simulation
```

The simulation runs at a fixed 25 Hz (40 ms ticks, as in 2011) and the renderer interpolates
between ticks. `step()` depends only on its inputs, and maps come from a seeded RNG, so two
machines given the same seed and the same key presses get the same game. That's the basis for
online play.

## Differences from the 2011 version

- Two players on one keyboard instead of client/server over the network.
- The walls are at the edge of the visible arena. The original only had walls far off-screen.
- Ships start in opposite corners facing the center, and clusters are never placed on top of a start point.
- The computer opponent also steers away from the walls, which the 2011 AI never had to do.
  Its aim-ahead when shooting assumes the target keeps its current velocity.
