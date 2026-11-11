// Game constants, ported from SimulationUtils.java (2011). Integer
// truncation mirrors the Java `(int)` casts so the physics feel the same.
// All distances are in arena units; the playing field spans
// -ARENA_HALF..ARENA_HALF in both x and y (y points up).

export const TIMESTEP = 40; // ms per simulation tick
export const ARENASIZE = 100000;
export const ARENA_HALF = ARENASIZE / 2; // walls (original only had walls at 2*ARENASIZE, off-screen)

export const OMEGA = (Math.PI / 650) * TIMESTEP; // max turn per tick (rad)
export const MAXACC = Math.trunc((ARENASIZE / 1e7) * TIMESTEP * TIMESTEP * 7); // 112
export const DRAG = TIMESTEP * 0.002; // fraction of velocity lost per tick
export const MAXSPEED = MAXACC / DRAG; // 1400 units/tick

export const BULLETSPEED = Math.trunc(ARENASIZE / 1000) * TIMESTEP * 0.8; // 3200
export const RELOADTIME = Math.trunc((1 / TIMESTEP) * 200); // 5 ticks
export const BULLETCOST = ARENASIZE * 0.2; // tail length spent per shot

export const UNCOILSPEED = Math.trunc(ARENASIZE * TIMESTEP * 0.00065); // 2600 units/tick

export const SHIPLENGTH = (ARENASIZE * 7) / 200;
export const SHIPWIDTH = (ARENASIZE * 4) / 200;

// Tail points closer together than this get merged (Tail.java).
export const MINTRAJECTORYSEGMENT = Math.trunc(ARENASIZE / 200);

// Control ramps from KeyboardPilot.java: thrust starts at 70% and turning at
// 40% of max, reaching 100% after RAMP_MS of holding the key.
export const RAMP_MS = 300;

export const NUMPLAYERS = 2;

// Arena generation (ClusterDatabase.java / gameserver.conf).
export const CLUSTER_SCALE = ARENASIZE / 700; // image pixels -> arena units
export const CLUSTERS_PER_VALUE = [5, 4, 3, 3, 1]; // how many clusters with 1,2,3,4,5 cells
export const SEQUENCE_LENGTH = 9; // clusters to encircle to win
export const MIN_ACCEPTED_LIKELIHOOD = 0.9;

// Byte/short counters from PlayerState.java.
export const BYTE_MAX = 0xff;
export const SHORT_MAX = 0x7fff;
