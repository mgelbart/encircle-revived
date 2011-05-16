package aoi;

/*
  This class decides what to do based on (the
  history of) the game state.

  This implementation of Intelligence does
  not rely on any human input.

*/

import java.awt.Point;
import java.awt.geom.Point2D;

public class Nonhuman implements Intelligence {

    // The input options received from Client.
    ClientConfiguration config;

    Arena arena;

    // A queue of latest game states.
    GameState[] games;

    // The control parameters to be sent to the server.
    PlayerControlParameters ctrl = null;
    PlayerControlParameters prevCtrl = null;

    // Last time ctrl was changed.
    int lastAction = -999999;

    public Nonhuman(ClientConfiguration config, Arena arena) {

	this.config = config;
	this.arena = arena;

	System.out.println("Non-human intelligence for player " + config.playerNo + " created.");

	games = new GameState[1];

    }

    public void run() {
	think();
    }

    /* Store the latest game state in a queue. */
    public void processGameStateUpdate(GameState game) {
	games[0] = game; /* Our queue consists of one element! */
    }


    public PlayerControlParameters getNewControlParameters() {
	if (ctrl != prevCtrl) {
	    prevCtrl = ctrl;
	    return ctrl;
	}

	return null;
    }

    public void think() {

	int acc, dir, shots, boosts;
	int[] intPos;

	double bestV, V, currentV;
	PlayerControlParameters bestCtrl, testCtrl;

	acc = Simulation.INTMAX / 2;

	while (true) {
	    
	    try {
		Thread.sleep(1000); // Sleep when there's nothing better to do.
	    } catch (InterruptedException e) {

		if (Math.abs(games[0].time - lastAction) > 200/Simulation.TIMESTEP) {

		    shots = 1;
		    boosts = 0;

		    currentV = potentialFunction(games[0].states[config.playerNo].x,
						 games[0].states[config.playerNo].y);

		    bestV = 1e10;
		    bestCtrl = null;

		    // Try different directions.
		    for (int j=-1; j<=1; ++j) {

			dir = games[0].states[config.playerNo].direction
			    + j*(Simulation.INTMAX/3);

			testCtrl = new PlayerControlParameters(acc, dir, shots, boosts);

			intPos = SimulationUtils.interpolatePosition(games[0].states[config.playerNo],
								     testCtrl, 800/Simulation.TIMESTEP);

			V = potentialFunction(intPos[0], intPos[1]);

			// Prefer going straight
			V += Math.abs(j) * 0.05;

			if (V < bestV) {
			    bestCtrl = testCtrl;
			    bestV = V;
			}

		    }


		    if (bestV < currentV)
			acc = Simulation.INTMAX;
		    else {
			acc = (int) ((10. - Math.max(1., Math.min(5., bestV))) / 9.
				     * Simulation.INTMAX);

			System.out.println(bestV + "   " + (int) ((25. - Math.max(5., Math.min(20., bestV))) / 20));
		    }

		    System.out.println("acc: " + acc);

		    lastAction = games[0].time;

		    ctrl = bestCtrl;
		}

	    }

	}

    }

    /* Evaluate the desirability of a given position (x, y)
       on the arena.
     */
    private double potentialFunction(int x, int y) {
	double V = 0;

	Point2D loc = new Point(x, y);

	double range = 500 / Simulation.TIMESTEP
	    * Math.sqrt( Math.pow(games[0].states[config.playerNo].vx, 2)
			 + Math.pow(games[0].states[config.playerNo].vy, 2));

	range = Math.max(range, Simulation.MAPSIZE/20);

	V += -3*truncatedLog(Simulation.MAPSIZE/2 - Math.abs(x), 5*range);
	V += -3*truncatedLog(Simulation.MAPSIZE/2 - Math.abs(y), 5*range);

	double dist;
	for (Cluster clust : arena.clusters) {

	    V += -truncatedLog(loc.distance(clust.x, clust.y + clust.height/2), range);
	    V += -truncatedLog(loc.distance(clust.x, clust.y - clust.height/2), range);
	    V += -truncatedLog(loc.distance(clust.x + clust.width/2, clust.y), range);
	    V += -truncatedLog(loc.distance(clust.x + clust.width/2, clust.y), range);

	}

	return V;
    }

    private double truncatedExp(double x, double range) {
	if (Math.abs(x) > 3*range) return 0.0;

	return Math.exp(-Math.abs(x/range));
    }

    private double truncatedLog(double x, double range) {
	if (Math.abs(x) > range) return 0.0;

	return Math.log(Math.abs(x/range));
    }

}
