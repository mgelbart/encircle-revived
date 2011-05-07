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

	StdDraw.setPenColor(StdDraw.BLUE);

	StdDraw.clear(StdDraw.BLACK);

	StdDraw.show(0);
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

	int xy[][];
	double xydbl[][] = new double[2][4];
	
	for (PlayerState p : g.states) {

	    xy = p.shape();

	    // Have to cast these things by hand because you can't cast
	    // a whole array at once (I think).
	    for (int k=0; k<2; ++k) {
		for (int l=0; l<4; ++l)
		    xydbl[k][l] = xy[k][l];
	    }

	    StdDraw.polygon(xydbl[0], xydbl[1]);

	}

	for (Bullet b : g.bullets)
	    StdDraw.square(b.x, b.y, Simulation.MAPSIZE / 500);

	StdDraw.show(0);

    }

}