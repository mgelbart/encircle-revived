package aoi;

/*
  A class for storing the history of the game
  state as well as predicting the future.
 */

import java.util.Vector;

public class TimeMachine {

    // Definitive game states that have been received from the server.
    Vector<GameState> states;

    // Commands by the player.
    Vector<PlayerCommands> ownCommands;

    // Number of game states to store in history.
    private final static int STATESSIZE = 1;
    private final static int COMMANDSSIZE = 1;

    public TimeMachine() {
	states = new Vector<GameState>(STATESSIZE + 1);
    }


    public void processStateUpdate(GameState g) {

	int i = 0;
	while (g.time - states[i].time < 0) i++; // TODO: Make sure this handles time wrapping...
	states.insertElementAt(g, i);

	if (states.size() > STATESSIZE)
	    states.removeElementAt(0);

    }


    // Get the GameState at the given time.
    public void getState(int time) {
	if (g.time - states[0].time < 0) return null; // TODO: Make sure this handles time wrapping...

	int i = 0;
	while (g.time - states[i].time < 0) i++; // TODO: Make sure this handles time wrapping...

	assert(i <= states.size());

	if (i < states.size() || states[i].time == time) {
	    // TODO: might want to interpolate in
	    // case there are holes in the history.
	    return states[i];

	} else {

	    // Need to predict the future.

	    g.time 

	}
    }

}