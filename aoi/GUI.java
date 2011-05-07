package aoi;

/*

  A class for interacting with a human, i.e. for
  drawing the game state and getting inputs.

  Designed to be ran in a separate thread such that
  frames are skipped in the visualization as needed.

  This class should be more or less tightly integrated
  with an instance of Intelligence.

 */

import stdlib.*;

public class GUI implements Runnable {

    /* Most recent game state. Updated by Client. */
    public GameState game;

    public GUI() {
	game = null;

	StdDraw.setXscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);
	StdDraw.setYscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);

	StdDraw.clear(StdDraw.BLACK);

	StdDraw.setPenColor(StdDraw.BLUE);
    }

    public void run() {

	// Sleep until interruption (triggered by Client
	// when game state is updated);
	while (true) {
	    
	    try {
		Thread.sleep(1000);
	    } catch (InterruptedException e) {

		drawGameState(game);

	    }

	}

    }


    private static void drawGameState(GameState g) {

	StdDraw.clear(StdDraw.BLACK);
	
	for (int j=0; j<2; ++j)
	    StdDraw.line(g.states[j].x,
			 g.states[j].y,
			 g.states[j].x + 1000 * Math.cos(Math.PI * (g.states[j].direction / (double) Simulation.INTMAX)),
			 g.states[j].y + 1000 * Math.sin(Math.PI * (g.states[j].direction / (double) Simulation.INTMAX)));

	for (Bullet b : g.bullets)
	    StdDraw.square(b.x, b.y, 100);

    }

}