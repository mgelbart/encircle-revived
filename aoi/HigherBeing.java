package aoi;

/*
  This class decides what to do based on (the
  history of) the game state.

  This implementation of Intelligence relies
  on a combination of human input and machine
  decision making, i.e. it implements an
  "augmented intelligence".
*/

import java.awt.event.KeyEvent;

public class HigherBeing implements Intelligence {

    // The input options received from Client.
    ClientConfiguration config;

    Arena arena;

    // A queue of latest game states.
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


    public HigherBeing(ClientConfiguration config, Arena arena) {

	this.config = config;
	this.arena = arena;

	System.out.println("HigherBeing for player " + config.playerNo + " created.");

	games = new GameState[1];

	if (!config.nonhuman) {
	    gui = new GUI(arena, config);
	    guiThread = new Thread(gui);
	    guiThread.start();
	}

    }

    public void run() {
	think();
    }

    /* Store the latest game state in a queue. */
    public void processGameStateUpdate(GameState game) {
	games[0] = game; /* Our queue consists of one element! */
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
		    dir = games[0].states[config.playerNo].direction
			+ Simulation.INTMAX/2;
		    update = true;
		} else if (gui.keysPressed.contains(KeyEvent.VK_RIGHT)) {
		    dir = games[0].states[config.playerNo].direction
			- Simulation.INTMAX/2;
		    update = true;
		} else if (dir != games[0].states[config.playerNo].direction) {
		    dir = games[0].states[config.playerNo].direction;
		    update = true;
		}		    


		if (update)
		    ctrl = new PlayerControlParameters(acc, dir, shots, boosts);

	    }

	}

    }

}
