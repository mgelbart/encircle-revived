package aoi;

/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

public class HigherBeing implements Intelligence {

    int playerNo;

    // A ring buffer of latest game states.
    GameState[] games;

    // The control parameters to be sent to the server.
    PlayerControlParameters ctrl;

    // The GUI is ran in a separate thread so that drawing
    // doesn't block thinking.
    GUI gui = null;
    Thread guiThread = null;

    // Last time ctrl was changed.
    int lastAction = -999999;


    public HigherBeing(int playerNo) {

	assert(playerNo < 2);
	assert(playerNo >= 0);

	this.playerNo = playerNo;

	games = new GameState[1];

	if (Client.draw) {
	    gui = new GUI();
	    guiThread = new Thread(gui);
	    guiThread.start();
	}

    }

    public void run() {
	think();
    }

    /* Store the latest game state in a ring buffer. */
    public void processGameStateUpdate(GameState game) {
	games[0] = game; /* Our ring buffer consists of one element! */
	System.out.println(game.toString());

	if (guiThread != null) {
	    gui.game = game;
	    guiThread.interrupt();
	}
    }


    public PlayerControlParameters getNewControlParameters() {
	return ctrl;
    }

    public void think() {

	while (true) {
	    
	    try {
		Thread.sleep(1000); // Sleep when there's nothing better to do.
	    } catch (InterruptedException e) {

		if (Math.abs(games[0].time - lastAction) > 1500/Simulation.TIMESTEP) {
		    ctrl = new PlayerControlParameters(Simulation.INTMAX,
						       games[0].states[playerNo].direction + Simulation.INTMAX/3,
						       2, 0);

		    lastAction = games[0].time;
		}
	    }

	}

    }

}
