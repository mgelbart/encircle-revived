package aoi;

/*
  A class that figures out what the other
  player is going to do.

  This could very much benefit from human
  input!
 */

public class MindReader {

    // Player to follow.
    private GameState playerNo;

    // Definitive game states that have been received from the server.
    private final Vector<GameState> states;

    // Number of game states to store in history.
    private final static int STATESSIZE = 5;

    public MindReader(playerNo) {
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

	final int N = state.size();

	if (N == 0) return null;

	if (N < 3) return new PlayerControlParameters(Simulation.INTMAX, states.get(N-1).states[playerNo].direction, 0, 0);

	int dvx = states.get(N-1).states[playerNo].x - 2*states.get(N-2).states[playerNo].x + states.get(N-3).states[playerNo].x;
	int dvy = states.get(N-1).states[playerNo].y - 2*states.get(N-2).states[playerNo].y + states.get(N-3).states[playerNo].y;

	double a = Math.sqrt(Math.pow(dvx, 2), Math.pow(dvy, 2));

	return new PlayerControlParameters((int) (Math.min(Simulation.INTMAX, ((long) Simulation.INTMAX) * (a / Simulation.MAXACC))),
					   states.get(N-1).states[playerNo].direction, 0, 0);
    }

}