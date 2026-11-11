// Arena generation: place cluster images around the map and pick the
// sequence of cell counts players must encircle. Port of
// ClusterDatabase.generateArena / ClusterServerSide.randomizeLocationSpread.

import {
  ARENASIZE,
  ARENA_HALF,
  CLUSTER_SCALE,
  CLUSTERS_PER_VALUE,
  SEQUENCE_LENGTH,
  MIN_ACCEPTED_LIKELIHOOD,
  NUMPLAYERS,
} from "./constants.js";
import { makeRng } from "./rng.js";
import { bounds, boxesOverlap, polygonContains, segmentsIntersect } from "./geom.js";

// Where ships start: opposite corners, a little in from the walls.
export function spawnPoint(playerNo) {
  const c = ARENA_HALF * 0.85;
  const corners = [
    [c, -c],
    [-c, c],
    [-c, -c],
    [c, c],
  ];
  const [x, y] = corners[playerNo % corners.length];
  return { x, y };
}

export function isAcceptable(cluster, nCells) {
  const p = cluster.likelihoods[nCells];
  return p !== undefined && p >= MIN_ACCEPTED_LIKELIHOOD;
}

// Make an arena cluster from a database entry, centered at (cx, cy).
// The outline data is already y-up (like arena coordinates), relative to the
// image center, so it lines up with the image drawn upright on screen.
function placeCluster(entry, nCells, cx, cy) {
  const xs = entry.xs.map((v) => Math.trunc(v * CLUSTER_SCALE) + cx);
  const ys = entry.ys.map((v) => Math.trunc(v * CLUSTER_SCALE) + cy);
  const b = bounds(xs, ys);
  return {
    name: entry.name,
    nCells,
    xs,
    ys,
    bounds: b,
    // The point that must be inside a loop to count (bounding-box center).
    center: { x: Math.trunc((b.minX + b.maxX) / 2), y: Math.trunc((b.minY + b.maxY) / 2) },
    img: {
      cx,
      cy,
      w: Math.trunc(entry.w * CLUSTER_SCALE),
      h: Math.trunc(entry.h * CLUSTER_SCALE),
    },
  };
}

function clustersOverlap(a, b) {
  if (!boxesOverlap(a.bounds, b.bounds, ARENASIZE * 0.01)) return false;
  for (let i = 0, j = a.xs.length - 1; i < a.xs.length; j = i++) {
    for (let k = 0, l = b.xs.length - 1; k < b.xs.length; l = k++) {
      if (segmentsIntersect(a.xs[j], a.ys[j], a.xs[i], a.ys[i], b.xs[l], b.ys[l], b.xs[k], b.ys[k])) return true;
    }
  }
  return (
    polygonContains(a.xs, a.ys, b.xs[0], b.ys[0], false) || polygonContains(b.xs, b.ys, a.xs[0], a.ys[0], false)
  );
}

function nearSpawn(c) {
  const margin = ARENASIZE * 0.06;
  for (let p = 0; p < NUMPLAYERS; p++) {
    const s = spawnPoint(p);
    if (s.x >= c.bounds.minX - margin && s.x <= c.bounds.maxX + margin && s.y >= c.bounds.minY - margin && s.y <= c.bounds.maxY + margin)
      return true;
  }
  return false;
}

function roulette(weights, rand) {
  const sum = weights.reduce((a, b) => a + b, 0);
  const r = rand() * sum;
  let acc = 0;
  for (let i = 0; i < weights.length; i++) {
    acc += weights[i];
    if (r <= acc) return i;
  }
  return weights.length - 1;
}

// database: array of entries from assets/clusters.json.
export function generateArena(database, seed) {
  const rand = makeRng(seed);
  const clusters = [];
  const LIMIT = ARENASIZE * 0.5 * 0.85;

  for (let nCells = 1; nCells <= CLUSTERS_PER_VALUE.length; nCells++) {
    const candidates = database.filter((e) => isAcceptable(e, nCells));
    if (candidates.length === 0) throw new Error(`no clusters with ${nCells} cells`);
    for (let k = 0; k < CLUSTERS_PER_VALUE[nCells - 1]; k++) {
      const entry = candidates[Math.floor(rand() * candidates.length)];
      let placed = null;
      for (let attempt = 0; attempt < 500 && !placed; attempt++) {
        // Random spot, pushed away from clusters already placed.
        let x, y;
        let tries = 0;
        do {
          x = ARENASIZE * 0.85 * (0.5 - rand());
          y = ARENASIZE * 0.85 * (0.5 - rand());
          for (const c of clusters) {
            x += Math.sign(x - c.center.x) * (ARENASIZE / 10) * Math.exp(-(Math.abs(x - c.center.x) / ARENASIZE) * 5);
            y += Math.sign(y - c.center.y) * (ARENASIZE / 10) * Math.exp(-(Math.abs(y - c.center.y) / ARENASIZE) * 5);
          }
          tries++;
        } while ((Math.abs(x) > LIMIT || Math.abs(y) > LIMIT) && tries < 1000);
        const candidate = placeCluster(entry, nCells, Math.trunc(x), Math.trunc(y));
        if (nearSpawn(candidate)) continue;
        if (clusters.some((c) => clustersOverlap(c, candidate))) continue;
        placed = candidate;
      }
      if (placed) clusters.push(placed);
    }
  }

  // The sequence of cell counts to encircle. More common counts come up
  // more often (sqrt weighting), and the same count never repeats twice in a row.
  const weights = CLUSTERS_PER_VALUE.map((n) => Math.sqrt(n));
  const sequence = [];
  for (let i = 0; i < SEQUENCE_LENGTH; i++) {
    let r = roulette(weights, rand) + 1;
    while (i > 0 && r === sequence[i - 1]) r = roulette(weights, rand) + 1;
    sequence.push(r);
  }

  return { seed, clusters, sequence };
}
