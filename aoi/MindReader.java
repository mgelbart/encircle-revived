package aoi;

/*
  A class that figures out what the other
  player is going to do.

  This could very much benefit from human
  input!
 */

import java.util.Vector;

public class MindReader {

    private ClientConfiguration config;

    // Definitive game states that have been received from the server.
    private final Vector<GameState> states;

    // Number of game states to store in history.
    private final static int STATESSIZE = 5;

    public MindReader(ClientConfiguration config) {
	this.config = config;
	states = new Vector<GameState>(STATESSIZE + 1);
    }


    // Call this whenever a new game state is
    // received from the server.
    public void processStateUpdate(GameState g) {

	int i = 0;
	while (states.size() > i
	       && g.time - states.get(i).time < 0) i++; // TODO: Make sure this handles time wrapping...
	states.insertElementAt(g, i+1);

	if (states.size() > STATESSIZE)
	    states.removeElementAt(0);

    }


    /*
      Return the best guess at the parameters
      of the player at the given time.
     */
    public PlayerControlParameters paramsAt(int time) {

	final int N = states.size();
	final int P = config.playerNo;

	if (N == 0) return null;

	if (N < 3) return new PlayerControlParameters(time, Simulation.INTMAX, states.get(N-1).states[P].direction, 0, 0);

	int dvx = states.get(N-1).states[P].vx - states.get(N-2).states[P].vx;
	int dvy = states.get(N-1).states[P].vy - states.get(N-2).states[P].vy;

	// What we'd expect with zero thrust:
	double dragdvx = -Simulation.DRAG * states.get(N-1).states[P].vx;
	double dragdvy = -Simulation.DRAG * states.get(N-1).states[P].vy;

	double a = Math.sqrt(Math.pow(dvx-dragdvx, 2) + Math.pow(dvy-dragdvy, 2));

	return new PlayerControlParameters(time,
					   (int) (Math.min(Simulation.INTMAX, ((long) Simulation.INTMAX) * (a / Simulation.MAXACC))),
					   states.get(N-1).states[P].direction,
					   0, 0);
    }

}