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

There's no build step. Any static file server works:

```bash
python3 -m http.server 8765
```

Then open <http://localhost:8765>. Opening `index.html` directly from disk won't work,
because browsers block ES modules on `file://`.

| | Red | Blue |
|---|---|---|
| thrust | `W` | `↑` |
| turn | `A` `D` | `←` `→` |
| shoot | `S` (or `Space`) | `↓` (or `Enter`) |

`Esc` pause · `M` mute · `N` new map (from the title or pause screen) · `R` replay the same map after a match.
The URL carries `?seed=…`, so a map can be shared or replayed.

## Tests

With the server running, open <http://localhost:8765/tests/>.

## Layout

```
index.html, style.css     page shell
src/sim/                  the game rules: pure, deterministic, no DOM
  constants.js            physics constants ported from SimulationUtils.java
  tail.js                 the tail: points, knots, cropping, loop detection (Tail.java)
  arena.js                map generation: cluster placement and the sequence (ClusterDatabase.java)
  sim.js                  step(arena, state, inputs) -> { state, events } (Simulation.java)
src/game/                 browser side: rendering, keyboard, sound, screens
assets/                   cluster images + outlines, sounds, background (from encircle-old/)
tools/build_assets.py     regenerates assets/ from encircle-old/
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
- No computer opponent yet (the original had one, `Nonhuman.java`).
