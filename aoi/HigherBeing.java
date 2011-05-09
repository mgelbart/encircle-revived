package aoi;

/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

import java.awt.event.KeyEvent;

public class HigherBeing implements Intelligence {

    int playerNo;

    // A ring buffer of latest game states.
    GameState[] games;

    // The control parameters to be sent to the server.
    PlayerControlParameters ctrl = null;
    PlayerControlParameters prevCtrl = null;

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

	System.out.println("HigherBeing for player " + playerNo + " created.");

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
	//System.out.println(game.toString());

	if (guiThread != null) {
	    gui.game = game;
	    guiThread.interrupt();
	}
    }


    public PlayerControlParameters getNewControlParameters() {
	if (ctrl != prevCtrl) {
	    prevCtrl = ctrl;
	    return ctrl;
	}

	return null;
    }

    public void think() {

	boolean update;
	int acc = 0;
	int dir = 0;
	int shots = 0;
	int boosts = 0;

	while (true) {
	    
	    try {
		Thread.sleep(1000); // Sleep when there's nothing better to do.
	    } catch (InterruptedException e) {

		update = false;

		if (gui != null) {

		    if (gui.keysPressed.contains(KeyEvent.VK_UP)) {
			if (acc != Simulation.INTMAX) {
			    acc = Simulation.INTMAX;
			    update = true;
			}
		    } else if (acc > 0) {
			acc = 0;
			update = true;
		    }

		    if (gui.keysPressed.contains(KeyEvent.VK_DOWN)) {
			boosts = 10;
			update = true;
		    } else if (boosts > 0) {
			boosts = 0;
			update = true;
		    }


		    if (gui.keysPressed.contains(KeyEvent.VK_SPACE)) {
			shots = 10;
			update = true;
		    } else if (shots > 0) {
			shots = 0;
			update = true;
		    }

		    if (gui.keysPressed.contains(KeyEvent.VK_LEFT)) {
			dir = games[0].states[playerNo].direction
			    + Simulation.INTMAX/2;
			update = true;
		    } else if (gui.keysPressed.contains(KeyEvent.VK_RIGHT)) {
			dir = games[0].states[playerNo].direction
			    - Simulation.INTMAX/2;
			update = true;
		    } else if (dir != games[0].states[playerNo].direction) {
			dir = games[0].states[playerNo].direction;
			update = true;
		    }		    

		} else {

		    // This is just some stupid automatic behavior for the
		    // other player for now
		    if (Math.abs(games[0].time - lastAction) > 1500/Simulation.TIMESTEP) {
			update = true;
			acc = Simulation.INTMAX;
			dir = games[0].states[playerNo].direction
			      + Simulation.INTMAX/3;
			shots = 1;
			boosts = 0;

			lastAction = games[0].time;
		    }

		}

		if (update)
		    ctrl = new PlayerControlParameters(acc, dir, shots, boosts);

	    }

	}

    }

}
