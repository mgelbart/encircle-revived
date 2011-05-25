package aoi;

/*
  A class for storing the history of the game
  state as well as predicting the future.
 */

import java.util.Vector;

public class TimeMachine {

    private final ClientConfiguration config;

    // Definitive game states that have been received from the server.
    private final Vector<GameState> states;

    // Commands by the player.
    private final Vector<PlayerControlParameters> ownParams;

    // Number of game states to store in history.
    private final static int STATESSIZE = 5;

    public TimeMachine(ClientConfiguration config) {
	this.config = config;
	states = new Vector<GameState>(STATESSIZE + 1);
	ownParams = new Vector<GameState>();
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


    // Call this whenever sending new control
    // parameters to the server.
    public void processParamsUpdate(PlayerControlParameters p) {

	int i = 0;
	while (ownParams.size() > i
	       && p.effectiveFrom - ownParams.get(i).effectiveFrom < 0) i++; // TODO: Make sure this handles time wrapping...
	ownParams.insertElementAt(p, i+1);

	// Prune old control params.
	while (ownParams.size() > 1 && ownParams.get(0).effectiveFrom < states.get(states.size()-1).time)
	    ownParams.removeElementAt(0);

    }


    // Get the GameState at the given time.
    public void getState(int time, MindReader reader) {
	if (time - states.get(0).time < 0) return null; // TODO: Make sure this handles time wrapping...

	int i = 0;
	while (time - states(i).time < 0) i++; // TODO: Make sure this handles time wrapping...

	assert(i <= states.size());

	if (i < states.size() || states.get(i).time == time) {
	    // TODO: might want to interpolate in
	    // case there are holes in the history.
	    return states[i];

	} else {

	    // 1st dim: player no.
	    // 2nd dim: position data returned by nextPos()
	    int[][] pos = new [Simulation.NUMPLAYERS][SimulationUtils.nextPos(null, null).size()];
	    GameState last = states.get(states.size() - 1);
	    PlayerState[] players = new PlayerState[Simulation.NUMPLAYERS]();

	    // Need to predict the future.
	    // Note: this _only_ interpolates position. Doesn't check for crashing into stuff
	    //       or scoring. If we want those, we should include them in SimulationUtils.
	    for (int k=0; k < Simulation.NUMPLAYERS; ++k) {

		pos[k] = SimulationUtils.nextPosition(last.states.get(k), getParams(k, last.time+1, reader));

		for (int j=1; j < states(i).time - time; ++j) { // TODO: Make sure this handles time wrapping...
		    pos[k] = SimulationUtils.nextPosition(new PlayerState(k, pos[k][0], pos[k][1], pos[k][2],
									  pos[k][3], pos[k][4], 0, 0, 0),
							  getParams(k, last.time+1+j));
		}

		players[k] = new PlayerState(k, pos[k][0], pos[k][1], pos[k][2], pos[k][3], pos[k][4],
					     last.states.get(k).energy, last.states.get(k).loopStarted,
					     last.states.get(k).score);

	    }


	    Vector<Bullet> bullets = (Vector<Bullet>) (last.bullets.clone());
	    int[] bulPos = new int[SimulationUtils.nextBulletPosition(null)];
	    Bullet b;

	    for (int k=0; k < bullets.size(); ++k) {
		b = bullets.get(k);

		bulPos = SimulationUtil.nextBulletPosition(b);
		dir = b.direction;

		for (int j=1; j < states(i).time - time; ++j) { // TODO: Make sure this handles time wrapping...
		    bulPos = SimulationUtils.nextBulletPosition(new bullet(b.playerNo, bulPos[0], bulPos[1], b.direction));
		}

		bullets.set(k, new Bullet(b.playerNo, bulPos[0], bulPos[1], b.direction));
	    }


	    return new GameState(time, players, bullets);

	}
    }


    // Get the control parameters of player number playerNo at the given time.
    private PlayerControlParameters getOwnParamsAt(int playerNo, int time, MindReader reader) {
	if (playerNo == config.playerNo) return getOwnParamsAt(time);

	if (reader != null) return reader.paramsAt(time);

	if (states.size() == 0) return null;

	// Fall back to assuming motion in current direction.
	// TODO: could at least calculate the acceleration
	//       from the last three steps...
	return new PlayerControlParameters(Simulation.INTMAX, states.get(states.size()-1).states[config.playerNo].direction, 0, 0);
    }


    // Get the control parameters of the local player at the given time.
    private PlayerControlParameters getOwnParamsAt(int time) {

	int i = 0;
	while (ownParams.size() > i && ownParams.get(i).effectiveFrom - time < 0) i++;

	if (i == 0) return null;

	return ownParams.get(i-1);
    }

}